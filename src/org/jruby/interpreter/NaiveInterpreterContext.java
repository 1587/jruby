/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jruby.interpreter;

import java.util.HashMap;
import java.util.Map;
import org.jruby.Ruby;
import org.jruby.parser.StaticScope;
import org.jruby.runtime.Block;
import org.jruby.runtime.DynamicScope;
import org.jruby.runtime.Frame;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

import org.jruby.compiler.ir.IRMethod;

/**
 *
 * @author enebo
 */
public class NaiveInterpreterContext implements InterpreterContext {
    private final Ruby runtime;
    private final ThreadContext context;
    protected Object returnValue;
    protected Object self;
    protected IRubyObject[] parameters;
    protected Object[] temporaryVariables;
    protected Object[] renamedVariables;
    protected Map localVariables;
    protected Frame frame;
    protected Block block;
    protected DynamicScope currDynScope = null;
    protected boolean allocatedDynScope = false;

    public NaiveInterpreterContext(ThreadContext context, IRubyObject self, int temporaryVariableSize, int renamedVariableSize, IRubyObject[] parameters, StaticScope staticScope, Block block) {
        // context.preMethodScopeOnly(self.getMetaClass(), staticScope);
        context.preMethodFrameOnly(self.getMetaClass(), null, self, block);

        this.context = context;
        this.runtime = context.getRuntime();
        this.self = self;
        this.parameters = parameters;
        this.temporaryVariables = new Object[temporaryVariableSize];
        this.renamedVariables = new Object[renamedVariableSize];
        this.localVariables = new HashMap();
        this.block = block;
    }

    public Ruby getRuntime() {
        return runtime;
    }

    public Block getBlock() {
        return block;
    }

    public void setBlock(Block block) {
        this.block = block;
    }

    public void setDynamicScope(DynamicScope s) {
        this.currDynScope = s;
    }

    public void allocateSharedBindingScope(IRMethod method) {
        this.allocatedDynScope = true;
        this.currDynScope = new org.jruby.runtime.scope.SharedBindingDynamicScope(method.getStaticScope(), method);
        context.pushScope(this.currDynScope);
    }

    public DynamicScope getSharedBindingScope() {
        return this.currDynScope;
    }

    // SSS: Should get rid of this and add a FreeBinding instruction
    public boolean hasAllocatedDynamicScope() {
        return this.allocatedDynScope;
    }

    public Object getReturnValue() {
        // FIXME: Maybe returnValue is a sure thing and we don't need this check.  Should be this way.
        return returnValue == null ? context.getRuntime().getNil() : returnValue;
    }

    public void setReturnValue(Object returnValue) {
        this.returnValue = returnValue;
    }

    public Object getTemporaryVariable(int offset) {
        return temporaryVariables[offset];
    }

    public Object setTemporaryVariable(int offset, Object value) {
        Object oldValue = temporaryVariables[offset];

        temporaryVariables[offset] = value;

        return oldValue;
    }

    public void updateRenamedVariablesCount(int n) {
        // SSS FIXME: use System.arraycopy
        Object[] oldRenamedVars = this.renamedVariables;
        this.renamedVariables = new Object[n];
        for (int i = 0; i < oldRenamedVars.length; i++) this.renamedVariables[i] = oldRenamedVars[i];
    }

    public Object getRenamedVariable(int offset) {
        return renamedVariables[offset];
    }

    public Object setRenamedVariable(int offset, Object value) {
        Object oldValue = renamedVariables[offset];
        renamedVariables[offset] = value;
        return oldValue;
    }

    public Object getSharedBindingVariable(IRMethod irMethod, String varName) {
        // SSS: This is actually dynamic scope variable -- badly named in the IR
        // Object value = getFrameVariableMap(irMethod).get(varNameame);
        int slot = irMethod.getBindingSlot(varName);
//        System.out.println("LOAD: location for " + varName + " is " + slot);
        Object value = currDynScope.getValue(slot, 0);
        if (value == null) value = getRuntime().getNil();
        return value;
    }

    public void setSharedBindingVariable(IRMethod irMethod, String varName, Object value) {
        // SSS: This is actually dynamic scope variable -- badly named in the IR
        // getFrameVariableMap(irMethod).put(varNameame, value);
        int slot = irMethod.getBindingSlot(varName);
//        System.out.println("STORE: location for " + varName + " is " + slot);
        currDynScope.setValue(slot, (IRubyObject)value, 0);
    }

    public Object getLocalVariable(String name) {
        Object value = localVariables.get(name);

        if (value == null) value = getRuntime().getNil();

        return value;
    }

    public ThreadContext getContext() {
        return context;
    }

    public Object getParameter(int offset) {
        return parameters[offset];
    }

    public int getParameterCount() {
        return parameters.length;
    }

    public Object setLocalVariable(String name, Object value) {
        localVariables.put(name, value);
        return value;
    }

    public Object getSelf() {
        return self;
    }

    public Frame getFrame() {
        return frame;
    }

    public void setFrame(Frame frame) {
        this.frame = frame;
    }
    // FIXME: We have this as a var somewhere else
    private IRubyObject[] NO_PARAMS = new IRubyObject[0];

    public IRubyObject[] getParametersFrom(int argIndex) {
        argIndex -= 1;

        int length = parameters.length - argIndex;
        if (length < 0) {
            return NO_PARAMS;
        }

        IRubyObject[] args = new IRubyObject[length];
        System.arraycopy(parameters, argIndex, args, 0, length);

        return args;
    }
}
