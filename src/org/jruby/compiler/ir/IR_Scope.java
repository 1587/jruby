package org.jruby.compiler.ir;

// SSS FIXME: Probably should rename this to IR_Scope?
// Easier to understand and it is in any case a scope, not just a IR builder context!
public interface IR_Scope
{
        // scripts
    public void addClass(IR_Class c);

        // scripts, and modules
    public void addModule(IR_Module m);

        // scripts, classes, and modules
    public void addMethod(IR_Method m);

        // scripts, classes, modules, methods, and closures
    public void addInstr(IR_Instr i);

        // create a new variable
    public Variable getNewVariable();

        // create a new variable using the prefix
    public Variable getNewVariable(String prefix);

        // SSS FIXME: Is this a leaked implementation detail??
    public int getAndIncrementMethodIndex();

        // get "self"
    public Variable getSelf();

        // SSS FIXME: BUGGY IMPLEMENTATION!  Remove or fix!  
        //
        // Right now, this is not being used anywhere since filenames are always constants strings
        // So, and can be removed unless it is required for some unforeseen dynamic case!
        //
        // Delegation-to-parent based lookup of filenames will not work!  For example, a method might
        // be defined in file F1 and its parent class might be defined in file F2.  File name lookup
        // on the method should always return F1.  But, delegation-to-parent based lookup will return F2!
    public Operand getFileName();

        // Tries to load at compile-time the constant referred to by 'constRef'.
        // This might be possible if the constant is defined and is not a forward reference
        // to a value that will be defined later in the class.
    public Operand getConstantValue(String constRef);

        // Tries to load at compile-time the constant referred to by 'constRef'.
        // This might be possible if the constant is defined and is not a forward reference
        // to a value that will be defined later in the class.
    public Operand setConstantValue(String constRef, Operand value);

        // While processing loops, this returns the loop that we are processing.
    public IR_Loop getCurrentLoop();

        // Record the loop we are beginning to process
    public void startLoop(IR_Loop l);

        // Indicate that we are done processing the loop
    public void endLoop(IR_Loop l);
}
