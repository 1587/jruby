package org.jruby.compiler.ir.instructions;

import java.util.Map;
import org.jruby.RubyModule;
import org.jruby.compiler.ir.IRModule;
import org.jruby.compiler.ir.IRMethod;
import org.jruby.compiler.ir.operands.Label;
import org.jruby.compiler.ir.operands.Operand;
import org.jruby.compiler.ir.Operation;
import org.jruby.compiler.ir.representations.InlinerInfo;
import org.jruby.internal.runtime.methods.InterpretedIRMethod;
import org.jruby.interpreter.InterpreterContext;
import org.jruby.runtime.builtin.IRubyObject;

public class DefineClassMethodInstr extends OneOperandInstr {
    public final IRModule module; // Can be a class or module
    public final IRMethod method;

    public DefineClassMethodInstr(IRModule module, IRMethod method) {
		  // SSS FIXME: I have to explicitly record method.getContainer() as an operand because it can be an unresolved value and thus a Variable
		  // We dont want live variable analysis to forget about it!
        super(Operation.DEF_CLASS_METH, null, method.getContainer());
        this.module = module;
        this.method = method;
    }

    @Override
    public String toString() {
        return super.toString() + "(" + module.getName() + ", " + method.getName() + "): container operand for method is: " + method.getContainer();
    }

    public Instr cloneForInlining(InlinerInfo ii) {
        return this;
    }

    public void simplifyOperands(Map<Operand, Operand> valueMap) {
		  super.simplifyOperands(valueMap);
        Operand o = method.getContainer();
        Operand v = valueMap.get(o);
        if (v != null)
            method.setContainer(v);
    }

    @Override
    public Label interpret(InterpreterContext interp, IRubyObject self) {
        RubyModule clazz = self.getMetaClass();
		  // SSS FIXME: Used to be this
        // method.setContainerModule((RubyModule) method.getContainer().retrieve(interp));
        method.setContainerModule(clazz);
        clazz.addMethod(method.getName(), new InterpretedIRMethod(method, clazz));
        return null;
    }
}
