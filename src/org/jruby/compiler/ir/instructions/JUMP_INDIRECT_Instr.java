package org.jruby.compiler.ir.instructions;

import org.jruby.compiler.ir.Operation;
import org.jruby.compiler.ir.operands.Variable;

public class JUMP_INDIRECT_Instr extends NoOperandInstr
{
    public final Variable _target; 

    public JUMP_INDIRECT_Instr(Variable tgt)
    {
        super(Operation.JUMP_INDIRECT);
        _target = tgt;
    }

    public String toString() {
        return super.toString() + " " + _target;
    }

    public Variable getJumpTarget() { return _target; }
}
