package org.jruby.compiler.ir;

public class DEFINE_CLASS_METHOD_Instr extends NoOperandInstr
{
    public final IR_Class  _class;
    public final IR_Method _method;

    public DEFINE_CLASS_METHOD_Instr(IR_Class c, IR_Method m)
    {
        super(Operation.DEF_CLASS_METH);
        _class = c;
        _method = m;
    }

	 public String toString() { return super.toString() + "(" + _class._className + ", " + _method._name + ")"; }
}
