package org.jruby.compiler.ir.instructions;

import org.jruby.compiler.ir.Operation;
import org.jruby.compiler.ir.operands.Operand;
import org.jruby.compiler.ir.operands.Variable;

// Represents result = source.ref or result = source where source is not a stack variable
public abstract class GET_Instr extends IR_Instr
{
    public final Operand _source;
    public final String  _ref;

    public GET_Instr(Operation op, Variable dest, Operand src, String ref)
    {
        super(op, dest);
        _source = src;
        _ref = ref;
    }

    public Operand[] getOperands() { return new Operand[] { _source }; }

    public String toString() { return super.toString() + "(" + _source + (_ref == null ? "" : ", " + _ref) + ")"; }
}
