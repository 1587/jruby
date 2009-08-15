
package org.jruby.ext.ffi;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyModule;
import org.jruby.RubyNumeric;
import org.jruby.RubyObject;
import org.jruby.RubyString;
import org.jruby.anno.JRubyClass;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

/**
 *
 */
@JRubyClass(name = "FFI::Type", parent = "Object")
public abstract class Type extends RubyObject {

    protected final NativeType nativeType;

    /** Size of this type in bytes */
    protected final int size;

    /** Minimum alignment of this type in bytes */
    protected final int alignment;

    public static RubyClass createTypeClass(Ruby runtime, RubyModule ffiModule) {
        RubyClass typeClass = ffiModule.defineClassUnder("Type", runtime.getObject(),
                ObjectAllocator.NOT_ALLOCATABLE_ALLOCATOR);
        typeClass.defineAnnotatedMethods(Type.class);
        typeClass.defineAnnotatedConstants(Type.class);

        RubyClass builtinClass = typeClass.defineClassUnder("Builtin", typeClass,
                ObjectAllocator.NOT_ALLOCATABLE_ALLOCATOR);
        builtinClass.defineAnnotatedMethods(Builtin.class);
        
        RubyModule nativeType = ffiModule.defineModuleUnder("NativeType");

        for (NativeType t : NativeType.values()) {
            try {
                Type b = new Builtin(runtime, builtinClass, t);
                typeClass.fastSetConstant(t.name(), b);
                nativeType.fastSetConstant(t.name(), b);
                ffiModule.fastSetConstant("TYPE_" + t.name(), b);
            } catch (UnsupportedOperationException ex) { }
        }

        RubyClass arrayTypeClass = typeClass.defineClassUnder("Array", typeClass,
                ObjectAllocator.NOT_ALLOCATABLE_ALLOCATOR);
        arrayTypeClass.defineAnnotatedMethods(Type.Array.class);

        return typeClass;
    }
    
    public static final RubyClass getTypeClass(Ruby runtime) {
        return runtime.fastGetModule("FFI").fastGetClass("Type");
    }

    /**
     * Initializes a new <tt>Type</tt> instance.
     */
    protected Type(Ruby runtime, RubyClass klass, NativeType type, int size, int alignment) {
        super(runtime, klass);
        this.nativeType = type;
        this.size = size;
        this.alignment = alignment;
    }

    /**
     * Initializes a new <tt>Type</tt> instance.
     */
    protected Type(Ruby runtime, RubyClass klass, NativeType type) {
        super(runtime, klass);
        this.nativeType = type;
        this.size = getNativeSize(type);
        this.alignment = getNativeAlignment(type);
    }

    /**
     * Gets the native type of this <tt>Type</tt> when passed as a parameter
     *
     * @return The native type of this Type.
     */
    public final NativeType getNativeType() {
        return nativeType;
    }

    /**
     * Gets the native size of this <tt>Type</tt> in bytes
     *
     * @return The native size of this Type.
     */
    public final int getNativeSize() {
        return size;
    }

    /**
     * Gets the native alignment of this <tt>Type</tt> in bytes
     *
     * @return The native alignment of this Type.
     */
    public final int getNativeAlignment() {
        return alignment;
    }

    /**
     * Gets the native size of this <tt>Type</tt> in bytes
     *
     * @param context The Ruby thread context.
     * @return The native size of this Type.
     */
    @JRubyMethod(name = "size")
    public IRubyObject size(ThreadContext context) {
        return context.getRuntime().newFixnum(getNativeSize());
    }

    /**
     * Gets the native alignment of this <tt>Type</tt> in bytes
     *
     * @param context The Ruby thread context.
     * @return The native alignment of this Type.
     */
    @JRubyMethod(name = "alignment")
    public IRubyObject alignment(ThreadContext context) {
        return context.getRuntime().newFixnum(getNativeAlignment());
    }

    @JRubyClass(name = "FFI::Type::Builtin", parent = "FFI::Type")
    public final static class Builtin extends Type {
        
        /**
         * Initializes a new <tt>BuiltinType</tt> instance.
         */
        private Builtin(Ruby runtime, RubyClass klass, NativeType nativeType) {
            super(runtime, klass, nativeType, Type.getNativeSize(nativeType), Type.getNativeAlignment(nativeType));
        }

        @JRubyMethod(name = "to_s")
        public final IRubyObject to_s(ThreadContext context) {
            return RubyString.newString(context.getRuntime(),
                    String.format("#<FFI::Type::Builtin:%s size=%d alignment=%d>",
                    nativeType.name(), size, alignment));
        }
        
        @Override
        public final String toString() {
            return nativeType.name();
        }

        @Override
        public boolean equals(Object obj) {
            return (obj instanceof Builtin) && ((Builtin) obj).nativeType.equals(nativeType);
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 23 * hash + nativeType.hashCode();
            return hash;
        }
    }

    @JRubyClass(name = "FFI::Type::Array", parent = "FFI::Type")
    public final static class Array extends Type {
        private final Type componentType;
        private final int length;

        /**
         * Initializes a new <tt>BuiltinType</tt> instance.
         */
        public Array(Ruby runtime, RubyClass klass, Type componentType, int length) {
            super(runtime, klass, NativeType.ARRAY, componentType.getNativeSize() * length, componentType.getNativeAlignment());
            this.componentType = componentType;
            this.length = length;
        }


        public final Type getComponentType() {
            return componentType;
        }

        public final int length() {
            return length;
        }

        @JRubyMethod(name = "new", required = 2, meta = true)
        public static final IRubyObject newInstance(ThreadContext context, IRubyObject klass, IRubyObject componentType, IRubyObject length) {
            if (!(componentType instanceof Type)) {
                throw context.getRuntime().newTypeError(componentType, getTypeClass(context.getRuntime()));
            }

            return new Array(context.getRuntime(), (RubyClass) klass, (Type) componentType, RubyNumeric.fix2int(length));
        }

        @JRubyMethod
        public final IRubyObject length(ThreadContext context) {
            return context.getRuntime().newFixnum(length);
        }

        @JRubyMethod
        public final IRubyObject elem_type(ThreadContext context) {
            return componentType;
        }

    }

    private static final boolean isPrimitive(NativeType type) {
        switch (type) {
            case VOID:
            case BOOL:
            case INT8:
            case UINT8:
            case INT16:
            case UINT16:
            case INT32:
            case UINT32:
            case INT64:
            case UINT64:
            case LONG:
            case ULONG:
            case FLOAT32:
            case FLOAT64:
            case BUFFER_IN:
            case BUFFER_INOUT:
            case BUFFER_OUT:
            case POINTER:
            case STRING:
            case RBXSTRING:
                return true;
            default:
                return false;
        }

    }
    private static final int getNativeAlignment(NativeType type) {
        return isPrimitive(type) ? Factory.getInstance().alignmentOf(type) : 1;
    }
    private static final int getNativeSize(NativeType type) {
        return isPrimitive(type) ? Factory.getInstance().sizeOf(type) : 0;
    }

}
