package org.jruby.compiler.ir;

public class Reference extends Operand
{
   final public String _refName;

   public Reference(String n) { _refName = n; }
}
