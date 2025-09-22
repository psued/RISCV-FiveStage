package FiveStage

import chisel3._
import chisel3.util._
import chisel3.experimental.MultiIOModule
import Op1Select._   // PC / rs1
import Op2Select._   // imm / rs2
import branchType._  // beq/neq/gte/lt/gteu/ltu/jump

class Execute extends MultiIOModule {
  val io = IO(new Bundle {
    // from IDEX barrier
    val pc          = Input(UInt(32.W))
    val rs1Data     = Input(UInt(32.W))
    val rs2Data     = Input(UInt(32.W))
    val rd          = Input(UInt(5.W))
    val imm         = Input(UInt(32.W))
    val ctrl        = Input(new ControlSignals)
    val branchType  = Input(UInt(3.W))
    val op1Select   = Input(UInt(1.W))
    val op2Select   = Input(UInt(1.W))
    val ALUop       = Input(UInt(4.W))

    // to EX/MEM barrier (or top, for now)
    val aluResult   = Output(UInt(32.W)) // ALU output (also address for loads/stores)
    val rs2Pass     = Output(UInt(32.W)) // store data
    val rdOut       = Output(UInt(5.W))  // destination reg id
    val ctrlOut     = Output(new ControlSignals)

    // branch/jump outcome
    val linkPC = Output(UInt(32.W))
    val branchTaken = Output(Bool())
    val pcTarget    = Output(UInt(32.W)) // target PC (branch/jump)
  })

  // ---- link PC (for JAL/JALR) ----
  io.linkPC := io.pc + 4.U
  // ---- operand select (for ALU & target calculation) ----
  val op1 = Mux(io.op1Select === PC,  io.pc,  io.rs1Data)  // 0=rs1, 1=PC
  val op2 = Mux(io.op2Select === imm, io.imm, io.rs2Data)  // 0=rs2, 1=imm

  // ---- ALU ----
  val alu = Module(new ALU)
  alu.io.op1   := op1
  alu.io.op2   := op2
  alu.io.aluOp := io.ALUop
  io.aluResult := alu.io.result

  // ---- branch/jump decision ----
  // Compare on register operands (NOT on ALU operands, which might be PC/imm)
  val cmpEq  = io.rs1Data === io.rs2Data
  val cmpLt  = (io.rs1Data.asSInt < io.rs2Data.asSInt)
  val cmpLtu = (io.rs1Data        < io.rs2Data)

  val brCond = MuxLookup(io.branchType, false.B, Seq(
    beq  -> cmpEq,
    neq  -> !cmpEq,
    gte  -> !cmpLt,    // signed >=
    lt   -> cmpLt,     // signed <
    gteu -> !cmpLtu,   // unsigned >=
    ltu  -> cmpLtu,    // unsigned <
    jump -> true.B     // unconditional jump (JAL/JALR)
  ))

  val taken = (io.ctrl.branch && brCond) || io.ctrl.jump
  io.branchTaken := taken

  // Target: op1 + op2 (PC+imm for branches/JAL, rs1+imm for JALR)
  val targetRaw   = op1 + op2
  val isJalrLike  = io.ctrl.jump && (io.op1Select =/= PC) // JALR uses rs1+imm
  val targetMasked= Mux(isJalrLike, targetRaw & ~1.U(32.W), targetRaw) // clear bit0 for JALR
  io.pcTarget := targetMasked

  // ---- pass-throughs to next stage ----
  io.rs2Pass := io.rs2Data
  io.rdOut   := io.rd
  io.ctrlOut := io.ctrl
}
