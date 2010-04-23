package org.jruby.compiler.ir.representations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jruby.compiler.ir.Tuple;
import org.jruby.compiler.ir.operands.Operand;
import org.jruby.compiler.ir.operands.Label;
import org.jruby.compiler.ir.operands.Array;
import org.jruby.compiler.ir.operands.Variable;
import org.jruby.compiler.ir.instructions.CallInstruction;
import org.jruby.compiler.ir.instructions.YIELD_Instr;

public class InlinerInfo {
    public final CFG callerCFG;
    public final CallInstruction call;

    private Operand[] callArgs;
    private Map<Label, Label> lblRenameMap;
    private Map<Variable, Variable> varRenameMap;
    private List yieldSites;

    public InlinerInfo(CallInstruction call, CFG c) {
        this.call = call;
        this.callArgs = call.getCallArgs();
        this.callerCFG = c;
        this.varRenameMap = new HashMap<Variable, Variable>();
        this.lblRenameMap = new HashMap<Label, Label>();
        this.yieldSites = new ArrayList();
    }

    public Label getRenamedLabel(Label l) {
        Label newLbl = this.lblRenameMap.get(l);
        if (newLbl == null) {
           newLbl = this.callerCFG.getScope().getNewLabel();
           this.lblRenameMap.put(l, newLbl);
        }
        return newLbl;
    }

    public Variable getRenamedVariable(Variable v) {
        Variable newVar = this.varRenameMap.get(v);
        if (newVar == null) {
           newVar = this.callerCFG.getScope().getNewInlineVariable();
           this.varRenameMap.put(v, newVar);
        }
        return newVar;
    }

    public Operand getCallArg(int index) {
        return index < callArgs.length ? callArgs[index] : null;
    }

    public Operand getCallArg(int index, boolean restOfArgArray) {
        if (restOfArgArray == false) {
            return getCallArg(index);
        }
        else {
            if (index >= callArgs.length) {
                return new Array();
            }
            else {
                Operand[] args = new Operand[callArgs.length - index];
                for (int i = index; i < callArgs.length; i++)
                    args[i-index] = callArgs[i];

                return new Array(args);
            }
        }
    }

    public Operand getCallReceiver() {
        return call.getReceiver();
    }

    public Operand getCallClosure() {
        return call.getClosureArg();
    }

    public Variable getCallResultVariable() {
        return call._result;
    }

    public void recordYieldSite(BasicBlock bb, YIELD_Instr i) {
        yieldSites.add(new Tuple<BasicBlock, YIELD_Instr>(bb, i));
    }

    public List getYieldSites() {
        return yieldSites;
    }
}
