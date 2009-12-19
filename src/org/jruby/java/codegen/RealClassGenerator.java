/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jruby.java.codegen;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jruby.Ruby;
import org.jruby.RubyBasicObject;
import org.jruby.RubyClass;
import org.jruby.RubyModule;
import org.jruby.compiler.impl.SkinnyMethodAdapter;
import org.jruby.compiler.util.BasicObjectStubGenerator;
import org.jruby.internal.runtime.methods.DynamicMethod;
import org.jruby.internal.runtime.methods.UndefinedMethod;
import org.jruby.javasupport.JavaUtil;
import org.jruby.javasupport.util.RuntimeHelpers;
import org.jruby.runtime.Block;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.callsite.CacheEntry;
import static org.jruby.util.CodegenUtils.*;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import static org.objectweb.asm.Opcodes.*;

/**
 *
 * @author headius
 */
public class RealClassGenerator {
    private static final boolean DEBUG = false;

    private static Map<String, List<Method>> buildSimpleToAllMap(Class[] interfaces, String[] superTypeNames) throws SecurityException {
        Map<String, List<Method>> simpleToAll = new HashMap<String, List<Method>>();
        for (int i = 0; i < interfaces.length; i++) {
            superTypeNames[i] = p(interfaces[i]);
            for (Method method : interfaces[i].getMethods()) {
                List<Method> methods = simpleToAll.get(method.getName());
                if (methods == null) {
                    simpleToAll.put(method.getName(), methods = new ArrayList<Method>());
                }
                methods.add(method);
            }
        }
        return simpleToAll;
    }

    public static Class createOldStyleImplClass(Class[] superTypes, RubyClass rubyClass, Ruby ruby, String name) {
        String[] superTypeNames = new String[superTypes.length];
        Map<String, List<Method>> simpleToAll = buildSimpleToAllMap(superTypes, superTypeNames);
        
        Class newClass = defineOldStyleImplClass(ruby, name, superTypeNames, simpleToAll);
        
        return newClass;
    }

    public static Class createRealImplClass(Class superClass, Class[] interfaces, RubyClass rubyClass, Ruby ruby, String name) {
        String[] superTypeNames = new String[interfaces.length];
        Map<String, List<Method>> simpleToAll = buildSimpleToAllMap(interfaces, superTypeNames);

        Class newClass = defineRealImplClass(ruby, name, superClass, superTypeNames, simpleToAll);

        return newClass;
    }
    
    /**
     * This variation on defineImplClass uses all the classic type coercion logic
     * for passing args and returning results.
     * 
     * @param ruby
     * @param name
     * @param superTypeNames
     * @param simpleToAll
     * @return
     */
    public static Class defineOldStyleImplClass(Ruby ruby, String name, String[] superTypeNames, Map<String, List<Method>> simpleToAll) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        String pathName = name.replace('.', '/');
        
        // construct the class, implementing all supertypes
        cw.visit(V1_5, ACC_PUBLIC | ACC_SUPER, pathName, null, p(Object.class), superTypeNames);
        cw.visitSource(pathName + ".gen", null);
        
        // fields needed for dispatch and such
        cw.visitField(ACC_STATIC | ACC_FINAL | ACC_PRIVATE, "$monitor", ci(Object.class), null, null).visitEnd();
        cw.visitField(ACC_PRIVATE | ACC_FINAL, "$self", ci(IRubyObject.class), null, null).visitEnd();

        // create static init, for a monitor object
        SkinnyMethodAdapter clinitMethod = new SkinnyMethodAdapter(cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "<clinit>", sig(void.class), null, null));
        clinitMethod.newobj(p(Object.class));
        clinitMethod.dup();
        clinitMethod.invokespecial(p(Object.class), "<init>", sig(void.class));
        clinitMethod.putstatic(pathName, "$monitor", ci(Object.class));
        
        // create constructor
        SkinnyMethodAdapter initMethod = new SkinnyMethodAdapter(cw.visitMethod(ACC_PUBLIC, "<init>", sig(void.class, IRubyObject.class), null, null));
        initMethod.aload(0);
        initMethod.invokespecial(p(Object.class), "<init>", sig(void.class));
        
        // store the wrapper
        initMethod.aload(0);
        initMethod.aload(1);
        initMethod.putfield(pathName, "$self", ci(IRubyObject.class));
        
        // end constructor
        initMethod.voidreturn();
        initMethod.end();
        
        // for each simple method name, implement the complex methods, calling the simple version
        for (Map.Entry<String, List<Method>> entry : simpleToAll.entrySet()) {
            String simpleName = entry.getKey();
            Set<String> nameSet = JavaUtil.getRubyNamesForJavaName(simpleName, entry.getValue());

            // set up a field for the CacheEntry
            // TODO: make this an array so it's not as much class metadata; similar to AbstractScript stuff
            cw.visitField(ACC_STATIC | ACC_PUBLIC | ACC_VOLATILE, simpleName, ci(CacheEntry.class), null, null).visitEnd();
            clinitMethod.getstatic(p(CacheEntry.class), "NULL_CACHE", ci(CacheEntry.class));
            clinitMethod.putstatic(pathName, simpleName, ci(CacheEntry.class));

            Set<String> implementedNames = new HashSet<String>();
            
            for (Method method : entry.getValue()) {
                Class[] paramTypes = method.getParameterTypes();
                Class returnType = method.getReturnType();

                String fullName = simpleName + prettyParams(paramTypes);
                if (implementedNames.contains(fullName)) continue;
                implementedNames.add(fullName);

                // indices for temp values
                int baseIndex = 1;
                for (Class paramType : paramTypes) {
                    if (paramType == double.class || paramType == long.class) {
                        baseIndex += 2;
                    } else {
                        baseIndex += 1;
                    }
                }
                int selfIndex = baseIndex;
                int rubyIndex = selfIndex + 1;
                
                SkinnyMethodAdapter mv = new SkinnyMethodAdapter(
                        cw.visitMethod(ACC_PUBLIC, simpleName, sig(returnType, paramTypes), null, null));
                mv.start();
                mv.line(1);
                
                // TODO: this code should really check if a Ruby equals method is implemented or not.
                if(simpleName.equals("equals") && paramTypes.length == 1 && paramTypes[0] == Object.class && returnType == Boolean.TYPE) {
                    mv.line(2);
                    mv.aload(0);
                    mv.aload(1);
                    mv.invokespecial(p(Object.class), "equals", sig(Boolean.TYPE, params(Object.class)));
                    mv.ireturn();
                } else if(simpleName.equals("hashCode") && paramTypes.length == 0 && returnType == Integer.TYPE) {
                    mv.line(3);
                    mv.aload(0);
                    mv.invokespecial(p(Object.class), "hashCode", sig(Integer.TYPE));
                    mv.ireturn();
                } else if(simpleName.equals("toString") && paramTypes.length == 0 && returnType == String.class) {
                    mv.line(4);
                    mv.aload(0);
                    mv.invokespecial(p(Object.class), "toString", sig(String.class));
                    mv.areturn();
                } else {
                    mv.line(5);
                    
                    Label dispatch = new Label();
                    Label end = new Label();
                    Label recheckMethod = new Label();

                    // prepare temp locals
                    mv.aload(0);
                    mv.getfield(pathName, "$self", ci(IRubyObject.class));
                    mv.astore(selfIndex);
                    mv.aload(selfIndex);
                    mv.invokeinterface(p(IRubyObject.class), "getRuntime", sig(Ruby.class));
                    mv.astore(rubyIndex);

                    // Try to look up field for simple name
                    // get field; if nonnull, go straight to dispatch
                    mv.getstatic(pathName, simpleName, ci(CacheEntry.class));
                    mv.dup();
                    mv.aload(selfIndex);
                    mv.invokestatic(p(RealClassGenerator.class), "isCacheOk", sig(boolean.class, params(CacheEntry.class, IRubyObject.class)));
                    mv.iftrue(dispatch);

                    // field is null, lock class and try to populate
                    mv.line(6);
                    mv.pop();
                    mv.getstatic(pathName, "$monitor", ci(Object.class));
                    mv.monitorenter();

                    // try/finally block to ensure unlock
                    Label tryStart = new Label();
                    Label tryEnd = new Label();
                    Label finallyStart = new Label();
                    Label finallyEnd = new Label();
                    mv.line(7);
                    mv.label(tryStart);

                    mv.aload(selfIndex);
                    for (String eachName : nameSet) {
                        mv.ldc(eachName);
                    }
                    mv.invokestatic(p(RealClassGenerator.class), "searchWithCache", sig(CacheEntry.class, params(IRubyObject.class, String.class, nameSet.size())));

                    // store it
                    mv.putstatic(pathName, simpleName, ci(CacheEntry.class));

                    // all done with lookup attempts, release monitor
                    mv.getstatic(pathName, "$monitor", ci(Object.class));
                    mv.monitorexit();
                    mv.go_to(recheckMethod);

                    // end of try block
                    mv.label(tryEnd);

                    // finally block to release monitor
                    mv.label(finallyStart);
                    mv.line(9);
                    mv.getstatic(pathName, "$monitor", ci(Object.class));
                    mv.monitorexit();
                    mv.label(finallyEnd);
                    mv.athrow();

                    // exception handling for monitor release
                    mv.trycatch(tryStart, tryEnd, finallyStart, null);
                    mv.trycatch(finallyStart, finallyEnd, finallyStart, null);

                    // re-get, re-check method; if not null now, go to dispatch
                    mv.label(recheckMethod);
                    mv.line(10);
                    mv.getstatic(pathName, simpleName, ci(CacheEntry.class));
                    mv.dup();
                    mv.getfield(p(CacheEntry.class), "method", ci(DynamicMethod.class));
                    mv.invokevirtual(p(DynamicMethod.class), "isUndefined", sig(boolean.class));
                    mv.iffalse(dispatch);

                    // method still not available, call method_missing
                    mv.line(11);
                    mv.pop();
                    // exit monitor before making call
                    // FIXME: this not being in a finally is a little worrisome
                    mv.aload(selfIndex);
                    mv.ldc(simpleName);
                    coerceArgumentsToRuby(mv, paramTypes, rubyIndex);
                    mv.invokestatic(p(RuntimeHelpers.class), "invokeMethodMissing", sig(IRubyObject.class, IRubyObject.class, String.class, IRubyObject[].class));
                    mv.go_to(end);
                
                    // perform the dispatch
                    mv.label(dispatch);
                    mv.line(12, dispatch);
                    // get current context
                    mv.getfield(p(CacheEntry.class), "method", ci(DynamicMethod.class));
                    mv.aload(rubyIndex);
                    mv.invokevirtual(p(Ruby.class), "getCurrentContext", sig(ThreadContext.class));
                
                    // load self, class, and name
                    mv.aload(selfIndex);
                    mv.aload(selfIndex);
                    mv.invokeinterface(p(IRubyObject.class), "getMetaClass", sig(RubyClass.class));
                    mv.ldc(simpleName);
                
                    // coerce arguments
                    coerceArgumentsToRuby(mv, paramTypes, rubyIndex);
                
                    // load null block
                    mv.getstatic(p(Block.class), "NULL_BLOCK", ci(Block.class));
                
                    // invoke method
                    mv.line(13);
                    mv.invokevirtual(p(DynamicMethod.class), "call", sig(IRubyObject.class, ThreadContext.class, IRubyObject.class, RubyModule.class, String.class, IRubyObject[].class, Block.class));
                
                    mv.label(end);
                    coerceResultAndReturn(mv, returnType);
                }                
                mv.end();
            }
        }
        
        // end setup method
        clinitMethod.voidreturn();
        clinitMethod.end();
        
        // end class
        cw.visitEnd();
        
        // create the class
        byte[] bytes = cw.toByteArray();
        Class newClass;
        synchronized (ruby.getJRubyClassLoader()) {
            // try to load the specified name; only if that fails, try to define the class
            try {
                newClass = ruby.getJRubyClassLoader().loadClass(name);
            } catch (ClassNotFoundException cnfe) {
                newClass = ruby.getJRubyClassLoader().defineClass(name, cw.toByteArray());
            }
        }
        
        if (DEBUG) {
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(name + ".class");
                fos.write(bytes);
            } catch (IOException ioe) {
                ioe.printStackTrace();
            } finally {
                try {fos.close();} catch (Exception e) {}
            }
        }
        
        return newClass;
    }

    /**
     * This variation on defineImplClass uses all the classic type coercion logic
     * for passing args and returning results.
     *
     * @param ruby
     * @param name
     * @param superTypeNames
     * @param simpleToAll
     * @return
     */
    public static Class defineRealImplClass(Ruby ruby, String name, Class superClass, String[] superTypeNames, Map<String, List<Method>> simpleToAll) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        String pathName = name.replace('.', '/');

        boolean isRubyHierarchy = RubyBasicObject.class.isAssignableFrom(superClass);

        // construct the class, implementing all supertypes
        if (isRubyHierarchy) {
            // Ruby hierarchy...just extend it
            cw.visit(V1_5, ACC_PUBLIC | ACC_SUPER, pathName, null, p(superClass), superTypeNames);
        } else {
            // Non-Ruby hierarchy; add IRubyObject
            String[] plusIRubyObject = new String[superTypeNames.length + 1];
            plusIRubyObject[0] = p(IRubyObject.class);
            System.arraycopy(superTypeNames, 0, plusIRubyObject, 1, superTypeNames.length);
            
            cw.visit(V1_5, ACC_PUBLIC | ACC_SUPER, pathName, null, p(superClass), plusIRubyObject);
        }
        cw.visitSource(pathName + ".gen", null);

        // fields needed for dispatch and such
        cw.visitField(ACC_STATIC | ACC_FINAL | ACC_PRIVATE, "$monitor", ci(Object.class), null, null).visitEnd();

        // create static init, for a monitor object
        SkinnyMethodAdapter clinitMethod = new SkinnyMethodAdapter(cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "<clinit>", sig(void.class), null, null));
        clinitMethod.newobj(p(Object.class));
        clinitMethod.dup();
        clinitMethod.invokespecial(p(Object.class), "<init>", sig(void.class));
        clinitMethod.putstatic(pathName, "$monitor", ci(Object.class));

        // create constructor
        SkinnyMethodAdapter initMethod = new SkinnyMethodAdapter(cw.visitMethod(ACC_PUBLIC, "<init>", sig(void.class, Ruby.class, RubyClass.class), null, null));

        if (isRubyHierarchy) {
            // superclass is in the Ruby object hierarchy; invoke typical Ruby superclass constructor
            initMethod.aloadMany(0, 1, 2);
            initMethod.invokespecial(p(superClass), "<init>", sig(void.class, Ruby.class, RubyClass.class));
        } else {
            // superclass is not in Ruby hierarchy; store objects and call no-arg super constructor
            cw.visitField(ACC_FINAL | ACC_PRIVATE, "$ruby", ci(Ruby.class), null, null).visitEnd();
            cw.visitField(ACC_FINAL | ACC_PRIVATE, "$rubyClass", ci(RubyClass.class), null, null).visitEnd();

            initMethod.aloadMany(0, 1);
            initMethod.putfield(pathName, "$ruby", ci(Ruby.class));
            initMethod.aloadMany(0, 2);
            initMethod.putfield(pathName, "$rubyClass", ci(RubyClass.class));

            // only no-arg super constructor supported right now
            initMethod.aload(0);
            initMethod.invokespecial(p(superClass), "<init>", sig(void.class));
        }
        initMethod.voidreturn();
        initMethod.end();

        if (isRubyHierarchy) {
            // override toJava
            SkinnyMethodAdapter toJavaMethod = new SkinnyMethodAdapter(cw.visitMethod(ACC_PUBLIC, "toJava", sig(Object.class, Class.class), null, null));
            toJavaMethod.aload(0);
            toJavaMethod.areturn();
            toJavaMethod.end();
        } else {
            // decorate with stubbed IRubyObject methods
            BasicObjectStubGenerator.addBasicObjectStubsToClass(cw);

            // add getRuntime and getMetaClass impls based on captured fields
            SkinnyMethodAdapter getRuntimeMethod = new SkinnyMethodAdapter(cw.visitMethod(ACC_PUBLIC, "getRuntime", sig(Ruby.class), null, null));
            getRuntimeMethod.aload(0);
            getRuntimeMethod.getfield(pathName, "$ruby", ci(Ruby.class));
            getRuntimeMethod.areturn();
            getRuntimeMethod.end();

            SkinnyMethodAdapter getMetaClassMethod = new SkinnyMethodAdapter(cw.visitMethod(ACC_PUBLIC, "getMetaClass", sig(RubyClass.class), null, null));
            getMetaClassMethod.aload(0);
            getMetaClassMethod.getfield(pathName, "$rubyClass", ci(RubyClass.class));
            getMetaClassMethod.areturn();
            getMetaClassMethod.end();
        }

        // for each simple method name, implement the complex methods, calling the simple version
        for (Map.Entry<String, List<Method>> entry : simpleToAll.entrySet()) {
            String simpleName = entry.getKey();
            Set<String> nameSet = JavaUtil.getRubyNamesForJavaName(simpleName, entry.getValue());

            // set up a field for the CacheEntry
            // TODO: make this an array so it's not as much class metadata; similar to AbstractScript stuff
            cw.visitField(ACC_STATIC | ACC_PUBLIC | ACC_VOLATILE, simpleName, ci(CacheEntry.class), null, null).visitEnd();
            clinitMethod.getstatic(p(CacheEntry.class), "NULL_CACHE", ci(CacheEntry.class));
            clinitMethod.putstatic(pathName, simpleName, ci(CacheEntry.class));

            Set<String> implementedNames = new HashSet<String>();

            for (Method method : entry.getValue()) {
                Class[] paramTypes = method.getParameterTypes();
                Class returnType = method.getReturnType();

                String fullName = simpleName + prettyParams(paramTypes);
                if (implementedNames.contains(fullName)) continue;
                implementedNames.add(fullName);

                // indices for temp values
                int baseIndex = 1;
                for (Class paramType : paramTypes) {
                    if (paramType == double.class || paramType == long.class) {
                        baseIndex += 2;
                    } else {
                        baseIndex += 1;
                    }
                }
                int rubyIndex = baseIndex + 1;

                SkinnyMethodAdapter mv = new SkinnyMethodAdapter(
                        cw.visitMethod(ACC_PUBLIC, simpleName, sig(returnType, paramTypes), null, null));
                mv.start();
                mv.line(1);

                // TODO: this code should really check if a Ruby equals method is implemented or not.
                if(simpleName.equals("equals") && paramTypes.length == 1 && paramTypes[0] == Object.class && returnType == Boolean.TYPE) {
                    mv.line(2);
                    mv.aload(0);
                    mv.aload(1);
                    mv.invokespecial(p(Object.class), "equals", sig(Boolean.TYPE, params(Object.class)));
                    mv.ireturn();
                } else if(simpleName.equals("hashCode") && paramTypes.length == 0 && returnType == Integer.TYPE) {
                    mv.line(3);
                    mv.aload(0);
                    mv.invokespecial(p(Object.class), "hashCode", sig(Integer.TYPE));
                    mv.ireturn();
                } else if(simpleName.equals("toString") && paramTypes.length == 0 && returnType == String.class) {
                    mv.line(4);
                    mv.aload(0);
                    mv.invokespecial(p(Object.class), "toString", sig(String.class));
                    mv.areturn();
                } else {
                    mv.line(5);

                    Label dispatch = new Label();
                    Label end = new Label();
                    Label recheckMethod = new Label();

                    // prepare temp locals
                    mv.aload(0);
                    mv.invokeinterface(p(IRubyObject.class), "getRuntime", sig(Ruby.class));
                    mv.astore(rubyIndex);

                    // Try to look up field for simple name
                    // get field; if nonnull, go straight to dispatch
                    mv.getstatic(pathName, simpleName, ci(CacheEntry.class));
                    mv.dup();
                    mv.aload(0);
                    mv.invokestatic(p(RealClassGenerator.class), "isCacheOk", sig(boolean.class, params(CacheEntry.class, IRubyObject.class)));
                    mv.iftrue(dispatch);

                    // field is null, lock class and try to populate
                    mv.line(6);
                    mv.pop();
                    mv.getstatic(pathName, "$monitor", ci(Object.class));
                    mv.monitorenter();

                    // try/finally block to ensure unlock
                    Label tryStart = new Label();
                    Label tryEnd = new Label();
                    Label finallyStart = new Label();
                    Label finallyEnd = new Label();
                    mv.line(7);
                    mv.label(tryStart);

                    mv.aload(0);
                    for (String eachName : nameSet) {
                        mv.ldc(eachName);
                    }
                    mv.invokestatic(p(RealClassGenerator.class), "searchWithCache", sig(CacheEntry.class, params(IRubyObject.class, String.class, nameSet.size())));

                    // store it
                    mv.putstatic(pathName, simpleName, ci(CacheEntry.class));

                    // all done with lookup attempts, release monitor
                    mv.getstatic(pathName, "$monitor", ci(Object.class));
                    mv.monitorexit();
                    mv.go_to(recheckMethod);

                    // end of try block
                    mv.label(tryEnd);

                    // finally block to release monitor
                    mv.label(finallyStart);
                    mv.line(9);
                    mv.getstatic(pathName, "$monitor", ci(Object.class));
                    mv.monitorexit();
                    mv.label(finallyEnd);
                    mv.athrow();

                    // exception handling for monitor release
                    mv.trycatch(tryStart, tryEnd, finallyStart, null);
                    mv.trycatch(finallyStart, finallyEnd, finallyStart, null);

                    // re-get, re-check method; if not null now, go to dispatch
                    mv.label(recheckMethod);
                    mv.line(10);
                    mv.getstatic(pathName, simpleName, ci(CacheEntry.class));
                    mv.dup();
                    mv.getfield(p(CacheEntry.class), "method", ci(DynamicMethod.class));
                    mv.invokevirtual(p(DynamicMethod.class), "isUndefined", sig(boolean.class));
                    mv.iffalse(dispatch);

                    // method still not available, call method_missing
                    mv.line(11);
                    mv.pop();
                    // exit monitor before making call
                    // FIXME: this not being in a finally is a little worrisome
                    mv.aload(0);
                    mv.ldc(simpleName);
                    coerceArgumentsToRuby(mv, paramTypes, rubyIndex);
                    mv.invokestatic(p(RuntimeHelpers.class), "invokeMethodMissing", sig(IRubyObject.class, IRubyObject.class, String.class, IRubyObject[].class));
                    mv.go_to(end);

                    // perform the dispatch
                    mv.label(dispatch);
                    mv.line(12, dispatch);
                    // get current context
                    mv.getfield(p(CacheEntry.class), "method", ci(DynamicMethod.class));
                    mv.aload(rubyIndex);
                    mv.invokevirtual(p(Ruby.class), "getCurrentContext", sig(ThreadContext.class));

                    // load self, class, and name
                    mv.aloadMany(0, 0);
                    mv.invokeinterface(p(IRubyObject.class), "getMetaClass", sig(RubyClass.class));
                    mv.ldc(simpleName);

                    // coerce arguments
                    coerceArgumentsToRuby(mv, paramTypes, rubyIndex);

                    // load null block
                    mv.getstatic(p(Block.class), "NULL_BLOCK", ci(Block.class));

                    // invoke method
                    mv.line(13);
                    mv.invokevirtual(p(DynamicMethod.class), "call", sig(IRubyObject.class, ThreadContext.class, IRubyObject.class, RubyModule.class, String.class, IRubyObject[].class, Block.class));

                    mv.label(end);
                    coerceResultAndReturn(mv, returnType);
                }
                mv.end();
            }
        }

        // end setup method
        clinitMethod.voidreturn();
        clinitMethod.end();

        // end class
        cw.visitEnd();

        // create the class
        byte[] bytes = cw.toByteArray();
        Class newClass;
        synchronized (ruby.getJRubyClassLoader()) {
            // try to load the specified name; only if that fails, try to define the class
            try {
                newClass = ruby.getJRubyClassLoader().loadClass(name);
            } catch (ClassNotFoundException cnfe) {
                newClass = ruby.getJRubyClassLoader().defineClass(name, cw.toByteArray());
            }
        }

        if (DEBUG) {
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(name + ".class");
                fos.write(bytes);
            } catch (IOException ioe) {
                ioe.printStackTrace();
            } finally {
                try {fos.close();} catch (Exception e) {}
            }
        }

        return newClass;
    }

    public static void coerceArgumentsToRuby(SkinnyMethodAdapter mv, Class[] paramTypes, int rubyIndex) {
        // load arguments into IRubyObject[] for dispatch
        if (paramTypes.length != 0) {
            mv.pushInt(paramTypes.length);
            mv.anewarray(p(IRubyObject.class));

            // TODO: make this do specific-arity calling
            for (int i = 0, argIndex = 1; i < paramTypes.length; i++) {
                Class paramType = paramTypes[i];
                mv.dup();
                mv.pushInt(i);
                // convert to IRubyObject
                mv.aload(rubyIndex);
                if (paramTypes[i].isPrimitive()) {
                    if (paramType == byte.class || paramType == short.class || paramType == char.class || paramType == int.class) {
                        mv.iload(argIndex++);
                        mv.invokestatic(p(JavaUtil.class), "convertJavaToRuby", sig(IRubyObject.class, Ruby.class, int.class));
                    } else if (paramType == long.class) {
                        mv.lload(argIndex);
                        argIndex += 2; // up two slots, for long's two halves
                        mv.invokestatic(p(JavaUtil.class), "convertJavaToRuby", sig(IRubyObject.class, Ruby.class, long.class));
                    } else if (paramType == float.class) {
                        mv.fload(argIndex++);
                        mv.invokestatic(p(JavaUtil.class), "convertJavaToRuby", sig(IRubyObject.class, Ruby.class, float.class));
                    } else if (paramType == double.class) {
                        mv.dload(argIndex);
                        argIndex += 2; // up two slots, for long's two halves
                        mv.invokestatic(p(JavaUtil.class), "convertJavaToRuby", sig(IRubyObject.class, Ruby.class, double.class));
                    } else if (paramType == boolean.class) {
                        mv.iload(argIndex++);
                        mv.invokestatic(p(JavaUtil.class), "convertJavaToRuby", sig(IRubyObject.class, Ruby.class, boolean.class));
                    }
                } else {
                    mv.aload(argIndex++);
                    mv.invokestatic(p(JavaUtil.class), "convertJavaToUsableRubyObject", sig(IRubyObject.class, Ruby.class, Object.class));
                }
                mv.aastore();
            }
        } else {
            mv.getstatic(p(IRubyObject.class), "NULL_ARRAY", ci(IRubyObject[].class));
        }
    }

    public static void coerceResultAndReturn(SkinnyMethodAdapter mv, Class returnType) {
        // if we expect a return value, unwrap it
        if (returnType != void.class) {
            // TODO: move the bulk of this logic to utility methods
            if (returnType.isPrimitive()) {
                if (returnType == boolean.class) {
                    mv.ldc(Type.getType(Boolean.class));
                    mv.invokeinterface(p(IRubyObject.class), "toJava", sig(Object.class, Class.class));
                    mv.checkcast(p(Boolean.class));
                    mv.invokevirtual(p(Boolean.class), "booleanValue", sig(boolean.class));
                    mv.ireturn();
                } else {
                    if (returnType == byte.class) {
                        mv.ldc(Type.getType(Byte.class));
                        mv.invokeinterface(p(IRubyObject.class), "toJava", sig(Object.class, Class.class));
                        mv.checkcast(p(Number.class));
                        mv.invokevirtual(p(Number.class), "byteValue", sig(byte.class));
                        mv.ireturn();
                    } else if (returnType == short.class) {
                        mv.ldc(Type.getType(Short.class));
                        mv.invokeinterface(p(IRubyObject.class), "toJava", sig(Object.class, Class.class));
                        mv.checkcast(p(Number.class));
                        mv.invokevirtual(p(Number.class), "shortValue", sig(short.class));
                        mv.ireturn();
                    } else if (returnType == char.class) {
                        mv.ldc(Type.getType(Character.class));
                        mv.invokeinterface(p(IRubyObject.class), "toJava", sig(Object.class, Class.class));
                        mv.checkcast(p(Number.class));
                        mv.invokevirtual(p(Number.class), "charValue", sig(char.class));
                        mv.ireturn();
                    } else if (returnType == int.class) {
                        mv.ldc(Type.getType(Integer.class));
                        mv.invokeinterface(p(IRubyObject.class), "toJava", sig(Object.class, Class.class));
                        mv.checkcast(p(Number.class));
                        mv.invokevirtual(p(Number.class), "intValue", sig(int.class));
                        mv.ireturn();
                    } else if (returnType == long.class) {
                        mv.ldc(Type.getType(Long.class));
                        mv.invokeinterface(p(IRubyObject.class), "toJava", sig(Object.class, Class.class));
                        mv.checkcast(p(Number.class));
                        mv.invokevirtual(p(Number.class), "longValue", sig(long.class));
                        mv.lreturn();
                    } else if (returnType == float.class) {
                        mv.ldc(Type.getType(Float.class));
                        mv.invokeinterface(p(IRubyObject.class), "toJava", sig(Object.class, Class.class));
                        mv.checkcast(p(Number.class));
                        mv.invokevirtual(p(Number.class), "floatValue", sig(float.class));
                        mv.freturn();
                    } else if (returnType == double.class) {
                        mv.ldc(Type.getType(Double.class));
                        mv.invokeinterface(p(IRubyObject.class), "toJava", sig(Object.class, Class.class));
                        mv.checkcast(p(Number.class));
                        mv.invokevirtual(p(Number.class), "doubleValue", sig(double.class));
                        mv.dreturn();
                    }
                }
            } else {
                mv.ldc(Type.getType(returnType));
                mv.invokeinterface(p(IRubyObject.class), "toJava", sig(Object.class, Class.class));
                mv.checkcast(p(returnType));
                mv.areturn();
            }
        } else {
            mv.voidreturn();
        }
    }
    
    public static CacheEntry searchWithCache(RubyClass clazz, String name1) {
        return clazz.searchWithCache(name1);
    }
    
    public static CacheEntry searchWithCache(RubyClass clazz, String name1, String name2) {
        CacheEntry entry = clazz.searchWithCache(name1);
        if (entry.method == UndefinedMethod.INSTANCE) {
            return searchWithCache(clazz, name2);
        }
        return entry;
    }
    
    public static CacheEntry searchWithCache(RubyClass clazz, String name1, String name2, String name3) {
        CacheEntry entry = clazz.searchWithCache(name1);
        if (entry.method == UndefinedMethod.INSTANCE) {
            return searchWithCache(clazz, name2, name3);
        }
        return entry;
    }
    
    public static CacheEntry searchWithCache(RubyClass clazz, String name1, String name2, String name3, String name4) {
        CacheEntry entry = clazz.searchWithCache(name1);
        if (entry.method == UndefinedMethod.INSTANCE) {
            return searchWithCache(clazz, name2, name3, name4);
        }
        return entry;
    }
    
    public static CacheEntry searchWithCache(RubyClass clazz, String name1, String name2, String name3, String name4, String name5) {
        CacheEntry entry = clazz.searchWithCache(name1);
        if (entry.method == UndefinedMethod.INSTANCE) {
            return searchWithCache(clazz, name2, name3, name4, name5);
        }
        return entry;
    }
    
    public static CacheEntry searchWithCache(RubyClass clazz, String name1, String name2, String name3, String name4, String name5, String name6) {
        CacheEntry entry = clazz.searchWithCache(name1);
        if (entry.method == UndefinedMethod.INSTANCE) {
            return searchWithCache(clazz, name2, name3, name4, name5, name6);
        }
        return entry;
    }
    
    public static CacheEntry searchWithCache(RubyClass clazz, String name1, String name2, String name3, String name4, String name5, String name6, String name7) {
        CacheEntry entry = clazz.searchWithCache(name1);
        if (entry.method == UndefinedMethod.INSTANCE) {
            return searchWithCache(clazz, name2, name3, name4, name5, name6, name7);
        }
        return entry;
    }
    
    public static CacheEntry searchWithCache(RubyClass clazz, String name1, String name2, String name3, String name4, String name5, String name6, String name7, String name8) {
        CacheEntry entry = clazz.searchWithCache(name1);
        if (entry.method == UndefinedMethod.INSTANCE) {
            return searchWithCache(clazz, name2, name3, name4, name5, name6, name7, name8);
        }
        return entry;
    }

    public static CacheEntry searchWithCache(IRubyObject obj, String name1) {
        return searchWithCache(obj.getMetaClass(), name1);
    }
    
    public static CacheEntry searchWithCache(IRubyObject obj, String name1, String name2) {
        return searchWithCache(obj.getMetaClass(), name1, name2);
    }
    
    public static CacheEntry searchWithCache(IRubyObject obj, String name1, String name2, String name3) {
        return searchWithCache(obj.getMetaClass(), name1, name2, name3);
    }
    
    public static CacheEntry searchWithCache(IRubyObject obj, String name1, String name2, String name3, String name4) {
        return searchWithCache(obj.getMetaClass(), name1, name2, name3, name4);
    }
    
    public static CacheEntry searchWithCache(IRubyObject obj, String name1, String name2, String name3, String name4, String name5) {
        return searchWithCache(obj.getMetaClass(), name1, name2, name3, name4, name5);
    }
    
    public static CacheEntry searchWithCache(IRubyObject obj, String name1, String name2, String name3, String name4, String name5, String name6) {
        return searchWithCache(obj.getMetaClass(), name1, name2, name3, name4, name5, name6);
    }
    
    public static CacheEntry searchWithCache(IRubyObject obj, String name1, String name2, String name3, String name4, String name5, String name6, String name7) {
        return searchWithCache(obj.getMetaClass(), name1, name2, name3, name4, name5, name6, name7);
    }
    
    public static CacheEntry searchWithCache(IRubyObject obj, String name1, String name2, String name3, String name4, String name5, String name6, String name7, String name8) {
        return searchWithCache(obj.getMetaClass(), name1, name2, name3, name4, name5, name6, name7, name8);
    }

    public static boolean isCacheOk(CacheEntry entry, IRubyObject self) {
        return CacheEntry.typeOk(entry, self.getMetaClass()) && entry.method != UndefinedMethod.INSTANCE;
    }
}
