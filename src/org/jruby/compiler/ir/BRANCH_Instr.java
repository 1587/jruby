package org.jruby.compiler.ir;

public abstract class BRANCH_Instr extends TwoOperandInstr
{
    Label _target;

    public BRANCH_Instr(Operation op, Operand v1, Operand v2, Label jmpTarget)
    {
        super(op, null, v1, v2);
        _target = jmpTarget;
    }

    public Label getJumpTarget() { return _target; }

    public String toString() {
        return super.toString() +
                " target = " + _target;
    }
}
