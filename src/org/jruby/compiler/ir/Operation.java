package org.jruby.compiler.ir;

enum OpType { dont_care, obj_op, alu_op, call_op, eval_op, branch_op, load_op, store_op };

public enum Operation
{
// ------ Define the operations below ----
    NOP(OpType.dont_care),

// value copy and type conversion operations
    COPY(OpType.dont_care), TYPE_CVT(OpType.dont_care), BOX_VAL(OpType.dont_care), UNBOX_OBJ(OpType.dont_care),

// alu operations
    ADD(OpType.alu_op), SUB(OpType.alu_op), MUL(OpType.alu_op), DIV(OpType.alu_op),
    OR(OpType.alu_op), AND(OpType.alu_op), XOR(OpType.alu_op), NOT(OpType.alu_op),
    LSHIFT(OpType.alu_op), RSHIFT(OpType.alu_op),

// method handle, arg receive, return value, and  call instructions
    GET_METHOD(OpType.dont_care),
    RETURN(OpType.dont_care), CLOSURE_RETURN(OpType.dont_care),
	 RECV_ARG(OpType.dont_care), RECV_BLOCK(OpType.dont_care), RECV_OPT_ARG(OpType.dont_care), RECV_CLOSURE_ARG(OpType.dont_care),
    CALL(OpType.call_op),

// closure instructions
    YIELD(OpType.dont_care),

// eval instructions
    EVAL_OP(OpType.eval_op), CLASS_EVAL(OpType.eval_op), 
    
// def instructions
    DEF_INST_METH(OpType.dont_care), DEF_CLASS_METH(OpType.dont_care),

// exception instructions
    THROW(OpType.dont_care), RESCUE(OpType.dont_care), RETRY(OpType.dont_care),

// Loads
	 GET_CONST(OpType.load_op), GET_GLOBAL_VAR(OpType.load_op), GET_FIELD(OpType.load_op), GET_CVAR(OpType.dont_care), GET_ARRAY(OpType.load_op), 

// Stores
	 PUT_CONST(OpType.store_op), PUT_GLOBAL_VAR(OpType.store_op), PUT_FIELD(OpType.store_op), PUT_ARRAY(OpType.store_op), PUT_CVAR(OpType.store_op),

// jump and branch operations
    JUMP(OpType.branch_op), BEQ(OpType.branch_op), BNE(OpType.branch_op), BLE(OpType.branch_op), BLT(OpType.branch_op), BGE(OpType.branch_op), BGT(OpType.branch_op),

// others
    LABEL(OpType.dont_care), BREAK(OpType.dont_care), THREAD_POLL(OpType.dont_care),

// comparisons & checks
    IS_TRUE(OpType.dont_care), // checks if the operand is non-null and non-false
    EQQ(OpType.call_op), // EQQ a === call used only for its conditional results, as in case/when, begin/rescue, ...

// a case/when branch
    CASE(OpType.branch_op);

    private OpType _type;

    Operation(OpType t) { _type = t; }

    public boolean isALU()    { return _type == OpType.alu_op; }
    public boolean isBranch() { return _type == OpType.branch_op; }
    public boolean isLoad()   { return _type == OpType.load_op; }
    public boolean isStore()  { return _type == OpType.store_op; }
    public boolean isCall()   { return _type == OpType.call_op; }
    public boolean isEval()   { return _type == OpType.eval_op; }

}