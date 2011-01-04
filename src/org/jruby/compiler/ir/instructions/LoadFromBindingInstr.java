package org.jruby.compiler.ir.instructions;

import org.jruby.compiler.ir.Operation;
import org.jruby.compiler.ir.operands.Variable;
import org.jruby.compiler.ir.operands.MetaObject;
import org.jruby.compiler.ir.IRExecutionScope;
import org.jruby.compiler.ir.IRMethod;
import org.jruby.compiler.ir.IRScope;
import org.jruby.compiler.ir.Interp;
import org.jruby.compiler.ir.operands.Label;
import org.jruby.compiler.ir.operands.LocalVariable;
import org.jruby.compiler.ir.operands.Operand;
import org.jruby.compiler.ir.representations.InlinerInfo;
import org.jruby.interpreter.InterpreterContext;
import org.jruby.runtime.builtin.IRubyObject;

/*
 * All variables are allocated in the binding of the nearest method ancestor.  Additionally, all variables 
 * with the same name in all closures (however deeply nested) get a single shared slot in the method's binding.
 *
 * So, when we encounter a load from binding instruction in some execution scope, we traverse the scope
 * tree till we find a method.  We are guaranteed to find one since closures dont float free --
 * they are always tethered to a surrounding scope!  This also means that we can find the neareast
 * method ancestor by simply traversing lexical scopes -- no need to traverse the dynamic scopes
 *
 * SSS FIXME: except perhaps when we use class_eval, module_eval, or instance_eval??
 */

public class LoadFromBindingInstr extends GetInstr {
    private int bindingSlot;
    public LoadFromBindingInstr(Variable v, IRExecutionScope scope, String slotName) {
        super(Operation.BINDING_LOAD, v, MetaObject.create(scope.getClosestMethodAncestor()), slotName);

		  MetaObject mo = (MetaObject)getSource();
		  IRMethod m = (IRMethod)mo.scope;
        bindingSlot = m.assignBindingSlot(slotName);
    }

    @Override
    public String toString() {
        return "\t" + result + " = BINDING(" + getSource() + ")." + getName();
    }

    public Instr cloneForInlining(InlinerInfo ii) {
        return new LoadFromBindingInstr(ii.getRenamedVariable(result), (IRExecutionScope)((MetaObject)getSource()).scope, getName());
    }

    private IRScope getIRScope(Operand scopeHolder) {
        assert scopeHolder instanceof MetaObject : "Target should be a MetaObject";

        return ((MetaObject) scopeHolder).getScope();
    }

    @Interp
    @Override
    public Label interpret(InterpreterContext interp, IRubyObject self) {
        IRMethod m = (IRMethod)getIRScope(getSource());
		  LocalVariable v = (LocalVariable)getResult();
        if (bindingSlot == -1)
            bindingSlot = m.getBindingSlot(v.getName());
        interp.setLocalVariable(v.getLocation(), interp.getSharedBindingVariable(bindingSlot));
        return null;
    }
}
