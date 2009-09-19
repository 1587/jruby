package org.jruby.compiler.ir.operands;

import org.jruby.compiler.ir.IR_Class;
import org.jruby.compiler.ir.IR_Closure;
import org.jruby.compiler.ir.IR_Method;
import org.jruby.compiler.ir.IR_Scope;
import org.jruby.compiler.ir.IR_Script;

public class MetaObject extends Operand
{
   public final IR_Scope _scope;

   public MetaObject(IR_Scope s) { _scope = s; }

   public String toString() { 
      if (_scope instanceof IR_Class)
         return "Class " + ((IR_Class)_scope)._className;
      else if (_scope instanceof IR_Method)
         return "Method " + ((IR_Method)_scope)._name;
      else if (_scope instanceof IR_Script)
         return "Script " + ((IR_Script)_scope)._fileName;
      else
         return ((IR_Closure)_scope).toString().replace("\t", "\t\t");
   }
}
