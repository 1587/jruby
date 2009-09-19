package org.jruby.compiler.ir;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class IR_BaseContext implements IR_BuilderContext
{
    IR_BuilderContext _container;   // The container for this context
    List<IR_Instr>    _instrs;      // List of ir instructions for this method

    private Map<String, Integer> _nextVarIndex;

	 public IR_BaseContext()
	 {
        _instrs = new ArrayList<IR_Instr>();
        _nextVarIndex = new HashMap<String, Integer>();
	 }

    public Variable getNewVariable(prefix)
    {
        if (prefix == null)
            prefix = "tmp";

        Integer idx = _nextVarIndex.get(prefix);
        if (idx == null)
            idx = 0;
        _nextVarIndex.put(prefix, idx+1);
        return new Variable(prefix + idx);
    }

    public Variable getNewVariable()
    {
       return getNewVariable("tmp");
    }

    public Label getNewLabel()
    {
        Integer idx = _nextVarIndex.get("LBL_");
        if (idx == null)
            idx = 0;
        _nextVarIndex.put(prefix, idx+1);
        return new Label(prefix + idx);
    }

      // Delegate method to the containing script/module/class
    public StringLiteral getFileName() { return _container.getFileName(); }

    public void addInstr(IR_Instr i) { _instrs.append(i); }
}
