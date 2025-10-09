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


  val instrStallReg = RegInit(Instruction.NOP)

  val stallReg = RegInit(false.B)

  stallReg := io.stall

  val pcReg = RegInit(0.U(32.W))
  io.ID_PC := pcReg
  when(!io.stall) {
    pcReg := io.IF_PC
  }

  when(stallReg) {
    io.ID_instruction := instrStallReg
  }.otherwise {
    instrStallReg := io.IF_instruction
    io.ID_instruction := io.IF_instruction
  }
}
