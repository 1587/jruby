package org.jruby.runtime.callsite;

import org.jruby.RubyFixnum;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

public class DivCallSite extends NormalCachingCallSite {

    public DivCallSite() {
        super("/");
    }

    public IRubyObject call(ThreadContext context, IRubyObject caller, IRubyObject self, long fixnum) {
        if (self instanceof RubyFixnum) {
            return ((RubyFixnum) self).op_div(context, fixnum);
        }
        return super.call(context, caller, self, fixnum);
    }

    @Override
    public IRubyObject call(ThreadContext context, IRubyObject caller, IRubyObject self, IRubyObject arg) {
        if (self instanceof RubyFixnum) {
            return ((RubyFixnum) self).op_div(context, arg);
        }
        return super.call(context, caller, self, arg);
    }
}
