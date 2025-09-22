package FiveStage
import chisel3._
import chisel3.util._
import ALUOps._


class ALU extends Module {
  val io = IO(new Bundle {
    val op1    = Input(UInt(32.W))
    val op2    = Input(UInt(32.W))
    val aluOp  = Input(UInt(4.W))
    val result = Output(UInt(32.W))
    // optional flags (handy for branches)
    val eq     = Output(Bool())
    val lt     = Output(Bool())   // signed <
    val ltu    = Output(Bool())   // unsigned <
  })

  val shamt = io.op2(4,0) // for shifts (I-type shamt or rs2 lower 5 bits)

  val add   = io.op1 + io.op2
  val sub   = io.op1 - io.op2
  val and_  = io.op1 & io.op2
  val or_   = io.op1 | io.op2
  val xor_  = io.op1 ^ io.op2
  val sll   = io.op1 << shamt
  val srl   = io.op1 >> shamt
  val sra   = (io.op1.asSInt >> shamt).asUInt
  val slt   = (io.op1.asSInt < io.op2.asSInt).asUInt  // 0/1
  val sltu  = (io.op1 < io.op2).asUInt                // 0/1

  val opMap: Seq[(UInt, UInt)] = Seq(
    ADD    -> add,
    SUB    -> sub,
    AND    -> and_,
    OR     -> or_,
    XOR    -> xor_,
    SLT    -> slt,
    SLL    -> sll,
    SLTU   -> sltu,
    SRL    -> srl,
    SRA    -> sra,
    COPY_A -> io.op1,
    COPY_B -> io.op2
  )

  io.result := MuxLookup(io.aluOp, 0.U, opMap)

  // flags
  io.eq  := io.op1 === io.op2
  io.lt  := io.op1.asSInt < io.op2.asSInt
  io.ltu := io.op1 < io.op2
}
