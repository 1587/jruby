package org.jruby.compiler.ir.instructions;

import org.jruby.compiler.ir.Operation;
import org.jruby.compiler.ir.operands.Operand;
import org.jruby.compiler.ir.operands.Variable;

// This is of the form:
//   v = OP(arg, attribute_array); Ex: v = NOT(v1)

public class OneOperandInstr extends IR_Instr
{
    public final Operand _arg;

    public OneOperandInstr(Operation op, Variable dest, Operand arg)
    {
        super(op, dest);
        _arg = arg;
    }

    public String toString() { return super.toString() + "(" + _arg + ")"; }

    public Operand[] getOperands() {
        return new Operand[] {_arg};
    }
}
