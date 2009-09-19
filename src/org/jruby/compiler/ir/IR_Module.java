package org.jruby.compiler.ir;

public class IR_Module extends IR_ScopeImpl
{
    public final String _moduleName;

    public IR_Module(IR_Scope parent, String name) 
    { 
        super(parent); 
        _moduleName = name;
    }
}
