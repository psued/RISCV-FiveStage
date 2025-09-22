package FiveStage
import chisel3._
import chisel3.experimental.MultiIOModule

class InstructionFetch extends MultiIOModule {
  val testHarness = IO(new Bundle {
    val IMEMsetup = Input(new IMEMsetupSignals)
    val PC        = Output(UInt(32.W))
  })
  val io = IO(new Bundle {
    val branchTaken  = Input(Bool())
    val branchTarget = Input(UInt(32.W))
    val PC           = Output(UInt(32.W))           // <-- give it a width
    val instruction  = Output(new Instruction)
  })

  val IMEM = Module(new IMEM)
  val PCreg = RegInit(0.U(32.W))

  // IMEM hookup
  IMEM.testHarness.setupSignals := testHarness.IMEMsetup
  IMEM.io.instructionAddress    := PCreg
  testHarness.PC                := IMEM.testHarness.requestedAddress

  // Next PC: branch redirect or PC+4
  val pcPlus4 = PCreg + 4.U(32.W)
  val pcNext  = Mux(io.branchTaken, io.branchTarget, pcPlus4)
  PCreg := pcNext

  // *** KEY: align the PC that travels with the instruction ***
  val pcForID = RegNext(PCreg, 0.U(32.W))
  io.PC := pcForID

  // Instruction out
  val instrW = WireInit(IMEM.io.instruction.asTypeOf(new Instruction))
  when (testHarness.IMEMsetup.setup) {
    PCreg  := 0.U(32.W)
    instrW := Instruction.NOP
  }
  io.instruction := instrW
}
