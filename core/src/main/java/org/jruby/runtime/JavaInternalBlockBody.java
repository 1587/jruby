/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jruby.runtime;

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyModule;
import org.jruby.parser.StaticScope;
import org.jruby.runtime.Block.Type;
import org.jruby.runtime.builtin.IRubyObject;

/**
 * Represents a special Java implementation of a block.
 */
public abstract class JavaInternalBlockBody extends BlockBody {
    private final Arity arity;
    private final ThreadContext originalContext;
    private final String methodName;
    private final StaticScope dummyScope;
    
    /**
     * For blocks which can be executed in any thread concurrently.
     */
    public JavaInternalBlockBody(Ruby runtime, Arity arity) {
        this(runtime, null, null, arity);
    }

    /**
     * For blocks which cannot be executed in parallel.
     * @param methodName
     * @param arity 
     */
    public JavaInternalBlockBody(Ruby runtime, ThreadContext originalContext, String methodName, Arity arity) {
        super(BlockBody.SINGLE_RESTARG);
        
        this.arity = arity;
        this.originalContext = originalContext;
        this.methodName = methodName;
        this.dummyScope = runtime.getStaticScopeFactory().getDummyScope();
    }
    
    // Make sure we are still on the same thread as originator if we care
    private void threadCheck(ThreadContext yieldingContext) {
        if (originalContext != null && yieldingContext != originalContext) {
            throw yieldingContext.runtime.newThreadError("" + methodName + " cannot be parallelized");
        }
    }

    @Override
    public IRubyObject call(ThreadContext context, IRubyObject[] args, Binding binding, Block.Type type) {
        IRubyObject value;
        if (args.length == 1) {
            value = args[0];
        } else {
            value = RubyArray.newArrayNoCopy(context.runtime, args);
        }
        return yield(context, value, null, null, true, binding, type);
    }

    @Override
    public IRubyObject call(ThreadContext context, IRubyObject[] args, Binding binding,
                            Block.Type type, Block block) {
        IRubyObject value;
        if (args.length == 1) {
            value = args[0];
        } else {
            value = RubyArray.newArrayNoCopy(context.runtime, args);
        }
        return yield(context, value, null, null, true, binding, type, block);
    }

    @Override
    public IRubyObject yield(ThreadContext context, IRubyObject value, Binding binding, Type type) {
        threadCheck(context);
        
        return yield(context, value);        
    }

    @Override
    public IRubyObject yield(ThreadContext context, IRubyObject value, IRubyObject self, RubyModule klass, boolean aValue, Binding binding, Type type) {
        threadCheck(context);
        
        
        return yield(context, value);
    }
    
    public abstract IRubyObject yield(ThreadContext context, IRubyObject value);

    @Override
    public StaticScope getStaticScope() {
        return dummyScope;
    }

    @Override
    public void setStaticScope(StaticScope newScope) {
    }

    @Override
    public Arity arity() {
        return arity;
    }

    @Override
    public String getFile() {
        return "(internal)";
    }

    @Override
    public int getLine() {
        return -1;
    }
    
}
