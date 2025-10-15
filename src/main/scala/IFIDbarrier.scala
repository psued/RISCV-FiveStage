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
  val instrHoldReg = RegInit(Instruction.NOP) // instruction replay payload
  val stallReg     = RegInit(false.B)         // 1-cycle shadow of stall

  // NEW: sticky flush to "stretch" flush one extra cycle (brittle bandaid)
  val flushSticky  = RegInit(false.B)
  when (io.flush) {
    flushSticky := true.B           // arm for exactly one extra cycle
  } .elsewhen (flushSticky) {
    flushSticky := false.B          // auto-clear after one cycle
  }
  val effFlush = io.flush || flushSticky   // effective 2-cycle flush

  // 1-cycle shadow stall
  stallReg := io.stall

  // ----------------- PC path (flush > stall > update) -----------------
  when (effFlush) {
    pcReg := 0.U
  } .elsewhen (!io.stall) {
    pcReg := io.IF_PC
  }
  io.ID_PC := pcReg

  // ----------------- Instruction path (shadow-stall + flush) -----------------
  // Capture new instruction unless we are about to replay (stallReg=true).
  when (effFlush) {
    instrHoldReg := Instruction.NOP   // kill any payload while flushing
    // (We deliberately do NOT touch stallReg here; this is the quick fix)
  } .elsewhen (!stallReg) {
    instrHoldReg := io.IF_instruction
  }

  // Output priority: flush > replay > pass-through
  io.ID_instruction :=
    Mux(effFlush, Instruction.NOP,
      Mux(stallReg, instrHoldReg, io.IF_instruction))
}
