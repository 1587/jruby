package org.jruby.compiler.ir.operands;

public class DynamicReference extends Operand
{
        // SSS FIXME: Should this be Operand or CompoundString?
        // Can it happen that symbols are built out of other than compound strings?  
        // Or can it happen during optimizations that this becomes a generic operand?
    final public CompoundString _refName;

    public DynamicReference(CompoundString n) { _refName = n; }
}
