package org.jruby.compiler.ir;

// Closures are contexts/scopes for the purpose of IR building.  They are self-contained and accummulate instructions
// that don't merge into the flow of the containing scope.  They are manipulated as an unit.
public class IR_Closure extends IR_ScopeImpl
{
    public final Label _startLabel; // Label for the start of the closure (used to implement redo)
    public final Label _endLabel;   // Label for the end of the closure (used to implement retry)

    public IR_Closure(IR_Scope parent) 
    { 
        super(parent); 
        _startLabel = getNewLabel("_CLOSURE_START_");
        _endLabel   = getNewLabel("_CLOSURE_END_");
    }

    public Operand getConstantValue(String constRef)
    {
            // Constants are defined in classes & modules, not in closures!
            // So, this reference is actually defined in the containing class/module
        if (_parent instanceof MetaObject) {
            return ((MetaObject)_parent)._scope.getConstantValue(constRef);  
        }
        else {
            Variable cv = getNewVariable();
            addInstr(new GET_CONST_Instr(cv, _parent, constRef));
            return cv;
        }
    }

    public void setConstantValue(String constRef, Operand val) 
    { 
        // SSS FIXME: Throw an exception here?
    }

    public String toString() { return "Closure: {" + super.toString() + "}"; }
}