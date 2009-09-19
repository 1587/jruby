package org.jruby.compiler.ir;

public class RECV_ARG_Instr extends IR_Instr
{
    int     _argIndex;
    boolean _restOfArgArray;

    public RECV_ARG_Instr(Variable dest, int index, boolean restOfArgArray)
    {
        super(Operation.RECV_ARG, dest);
        _argIndex = index;
        _restOfArgArray = restOfArgArray;
    }

    public RECV_ARG_Instr(Variable dest, int index)
    {
        this(dest, index, false);
    }

    public String toString() { return super.toString() + "(" + _argIndex + (_restOfArgArray ? ", ALL" : "") + ")"; }
}
