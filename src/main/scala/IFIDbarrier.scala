package FiveStage
import chisel3._
import chisel3.experimental.MultiIOModule

class IFIDbarrier extends MultiIOModule {
  val io = IO(new Bundle {
    // From IF
    val IF_PC          = Input(UInt(32.W))
    val IF_instruction = Input(new Instruction)

    // Control
    val stall = Input(Bool())
    val flush = Input(Bool())   // flush > stall

    // To ID
    val ID_PC          = Output(UInt(32.W))
    val ID_instruction = Output(new Instruction)
  })

  // ----------------- regs -----------------
  val pcReg        = RegInit(0.U(32.W))
  val instrHoldReg = RegInit(Instruction.NOP) // for 1-cycle replay
  val stallReg     = RegInit(false.B)         // 1-cycle shadow of stall

  // Shadow stall (captures previous cycle's stall)
  stallReg := io.stall

  // ----------------- 2-cycle flush (band-aid) -----------------
  // Arm a sticky flush for exactly one extra cycle whenever flush pulses.
  val flushSticky = RegInit(false.B)
  when (io.flush) {
    flushSticky := true.B
  } .elsewhen (flushSticky) {
    flushSticky := false.B
  }

  // Instruction is killed on both cycles; PC should only be zeroed on the 1st.
  val effFlushInstr = io.flush || flushSticky   // controls instruction kill
  val flushFirstPC  = io.flush                  // only first cycle zeroes PC

  // ----------------- PC path -----------------
  // - On the *first* flush cycle: zero PC (legacy behavior).
  // - On the sticky (second) flush cycle: DO NOT zero PC — track IF_PC so ID sees the correct target PC.
  // - Otherwise: update from IF_PC when not stalled.
  when (flushFirstPC) {
    pcReg := 0.U
  } .elsewhen (!io.stall) {
    // This covers both normal operation and the sticky flush cycle.
    pcReg := io.IF_PC
  } // else: hold pcReg on stall

  io.ID_PC := pcReg

  // ----------------- Instruction path -----------------
  // Kill instruction on both first and sticky flush cycles.
  // Shadow-stall behavior unchanged: replay held instruction when stallReg==true.
  when (effFlushInstr) {
    instrHoldReg := Instruction.NOP
  } .elsewhen (!stallReg) {
    instrHoldReg := io.IF_instruction
  }

  io.ID_instruction :=
    Mux(effFlushInstr, Instruction.NOP,
      Mux(stallReg, instrHoldReg, io.IF_instruction))
}
