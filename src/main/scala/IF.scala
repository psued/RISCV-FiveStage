package FiveStage
import chisel3._
import chisel3.util._
import chisel3.experimental.MultiIOModule

class InstructionFetch extends MultiIOModule {
  val testHarness = IO(new Bundle {
    val IMEMsetup = Input(new IMEMsetupSignals)
    val PC        = Output(UInt(32.W))
  })
  val io = IO(new Bundle {
    // control from hazard / branch unit
    val stallF       = Input(Bool())        // freeze PC (Fetch)
    val branchTaken  = Input(Bool())
    val branchTarget = Input(UInt(32.W))

    // raw outputs to IF/ID
    val pc_out       = Output(UInt(32.W))
    val instr_out    = Output(new Instruction)
  })

  val IMEM  = Module(new IMEM)
  val PCreg = RegInit(0.U(32.W))

  // IMEM hookup
  IMEM.testHarness.setupSignals := testHarness.IMEMsetup
  IMEM.io.instructionAddress    := PCreg
  testHarness.PC                := IMEM.testHarness.requestedAddress

  val pcPlus4 = PCreg + 4.U
  val pcNext  = Mux(io.branchTaken, io.branchTarget, pcPlus4)

  // Priority: redirect > stall > normal increment
  when (testHarness.IMEMsetup.setup) {
    PCreg := 0.U
  } .elsewhen (io.branchTaken) {
    PCreg := io.branchTarget
  } .elsewhen (!io.stallF) {
    PCreg := pcNext
  } // else: hold PC

  // raw instruction/pc out (to IF/ID)
  io.pc_out    := PCreg
  val instrRaw = WireInit(IMEM.io.instruction.asTypeOf(new Instruction))
  when (testHarness.IMEMsetup.setup) { instrRaw := Instruction.NOP }
  io.instr_out := instrRaw


}
