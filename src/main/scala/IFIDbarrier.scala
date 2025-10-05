package FiveStage
import chisel3._
import chisel3.experimental.MultiIOModule

class IFIDbarrier extends MultiIOModule {
  val io = IO(new Bundle {
    val IF_PC = Input(UInt(32.W))
    val IF_instruction = Input(new Instruction)

    val stall = Input(Bool())

    val ID_PC = Output(UInt(32.W))
    val ID_instruction = Output(new Instruction)
  })

  // Register the IF outputs so ID sees them one cycle later
  val pcReg = RegInit(0.U(32.W))

  val instrReg = RegInit(0.U.asTypeOf(new Instruction))

  // Only update when NOT stalling
  when(!io.stall) {
    pcReg := io.IF_PC
    instrReg := io.IF_instruction
  }
  // When stall = true, pcReg/instrReg keep their previous values automatically

  // Drive outputs from regs (always)
  io.ID_PC := pcReg
  io.ID_instruction := instrReg
}
