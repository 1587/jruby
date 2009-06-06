
package org.jruby.ext.ffi;


import java.nio.ByteBuffer;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyModule;
import org.jruby.anno.JRubyClass;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.threading.DaemonThreadFactory;

@JRubyClass(name = "FFI::" + AutoPointer.CLASS_NAME, parent = "FFI::Pointer")
public final class AutoPointer extends Pointer {
    public static final String CLASS_NAME = "AutoPointer";
    private Pointer pointer;
    private PointerHolder holder;

    public static RubyClass createAutoPointerClass(Ruby runtime, RubyModule module) {
        RubyClass result = module.defineClassUnder(CLASS_NAME,
                module.getClass("Pointer"),
                AutoPointerAllocator.INSTANCE);
        result.defineAnnotatedMethods(AutoPointer.class);
        result.defineAnnotatedConstants(AutoPointer.class);

        return result;
    }

    private static final class AutoPointerAllocator implements ObjectAllocator {
        static final ObjectAllocator INSTANCE = new AutoPointerAllocator();

        public IRubyObject allocate(Ruby runtime, RubyClass klazz) {
            return new AutoPointer(runtime, klazz);
        }

    }

    private AutoPointer(Ruby runtime, RubyClass klazz) {
        super(runtime, klazz, new NullMemoryIO(runtime));
    }
    
    private static final void checkPointer(Ruby runtime, IRubyObject ptr) {
        if (!(ptr instanceof Pointer)) {
            throw runtime.newTypeError(ptr, runtime.fastGetModule("FFI").fastGetClass("Pointer"));
        }
        if (ptr instanceof MemoryPointer || ptr instanceof AutoPointer) {
            throw runtime.newTypeError("Cannot use AutoPointer with MemoryPointer or AutoPointer instances");
        }
    }

    @JRubyMethod(name = "new", meta = true)
    public static final IRubyObject newAutoPointer(ThreadContext context, IRubyObject klazz, IRubyObject ptr) {
        AutoPointer p = new AutoPointer(context.getRuntime(), (RubyClass) klazz);
        p.callMethod(context, "initialize", ptr);

        return p;
    }

    @JRubyMethod(name = "new", meta = true)
    public static final IRubyObject newAutoPointer(ThreadContext context, IRubyObject klazz, IRubyObject ptr, IRubyObject proc) {
        AutoPointer p = new AutoPointer(context.getRuntime(), (RubyClass) klazz);
        p.callMethod(context, "initialize", new IRubyObject[] { ptr, proc });

        return p;
    }

    @JRubyMethod(name = "initialize")
    public final IRubyObject initialize(ThreadContext context, IRubyObject pointerArg) {

        checkPointer(context.getRuntime(), pointerArg);

        this.io = ((Pointer) pointerArg).getMemoryIO();
        this.pointer = (Pointer) pointerArg;
        this.holder = new PointerHolder(pointer, new ReleaseMethodReaper(pointer, getMetaClass()));

        return this;
    }

    @JRubyMethod(name = "initialize")
    public final IRubyObject initialize(ThreadContext context, IRubyObject self,
            IRubyObject pointerArg, IRubyObject releaser) {

        checkPointer(context.getRuntime(), pointerArg);

        this.io = ((Pointer) pointerArg).getMemoryIO();
        this.pointer = (Pointer) pointerArg;
        this.holder = new PointerHolder(pointer, new ProcReaper(pointer, releaser));

        return this;
    }    
    
    private static final class PointerHolder {
        static final Executor executor = Executors.newSingleThreadExecutor(new DaemonThreadFactory());
        
        protected final Pointer pointer;
        protected final Runnable reaper;
        public PointerHolder(Pointer pointer, Runnable reaper) {
            this.pointer = pointer;
            this.reaper = reaper;
        }
        @Override
        protected void finalize() throws Exception {
            executor.execute(reaper);
        }
    }
    
    private static final class ProcReaper implements Runnable {
        private final Pointer pointer;
        private final IRubyObject proc;
        private ProcReaper(Pointer pointer, IRubyObject proc) {
            this.pointer = pointer;
            this.proc = proc;
        }
        public void run() {
            try {
                proc.callMethod(pointer.getRuntime().getCurrentContext(), "call", pointer);
            } catch (Exception ex) {}
        }
    }

    private static final class ReleaseMethodReaper implements Runnable {
        private final Pointer pointer;
        private final IRubyObject releaser;
        private ReleaseMethodReaper(Pointer pointer, IRubyObject releaser) {
            this.pointer = pointer;
            this.releaser = releaser;
        }
        public void run() {
            try {
                releaser.callMethod(pointer.getRuntime().getCurrentContext(), "release", pointer);
            } catch (Exception ex) {}
        }
    }
}
