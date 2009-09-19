package org.jruby.compiler.ir;

// Rather than building a zillion instructions that capture calls to ruby implementation internals,
// we are building one that will serve as a placeholder for internals-specific call optimizations.
public class RUBY_IMPL_CALL_Instr extends CALL_Instr
{
   public RUBY_IMPL_CALL_Instr(Variable result, Operand methAddr, Operand[] args)
   {
      super(result, methAddr, args, null);
   }

   public RUBY_IMPL_CALL_Instr(Variable result, Operand methAddr, Operand[] args, Operand closure)
   {
      super(result, methAddr, args, closure);
   }

   public boolean isRubyImplementationCall() { return true; }
}
