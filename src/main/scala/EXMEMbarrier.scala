package FiveStage
import chisel3._
import chisel3.experimental.MultiIOModule

class EXMEMbarrier extends MultiIOModule {
  val io = IO(new Bundle {
    // From EX
    val in_aluResult   = Input(UInt(32.W))
    val in_rs2Data     = Input(UInt(32.W))
    val in_rd          = Input(UInt(5.W))
    val in_ctrl        = Input(new ControlSignals)
    val in_branchTaken = Input(Bool())
    val in_pcTarget    = Input(UInt(32.W))

    val stall = Input(Bool())
    val flush = Input(Bool())

    val in_linkPC = Input(UInt(32.W))
    val out_linkPC = Output(UInt(32.W))

    // To MEM
    val out_aluResult   = Output(UInt(32.W))
    val out_rs2Data     = Output(UInt(32.W))
    val out_rd          = Output(UInt(5.W))
    val out_ctrl        = Output(new ControlSignals)
    val out_branchTaken = Output(Bool())
    val out_pcTarget    = Output(UInt(32.W))
  })


  val r_linkPC = RegInit(0.U(32.W))

  // Pipeline registers
  val r_alu         = RegInit(0.U(32.W))
  val r_rs2         = RegInit(0.U(32.W))
  val r_rd          = RegInit(0.U(5.W))
  val r_ctrl        = RegInit(0.U.asTypeOf(new ControlSignals))
  val r_branchTaken = RegInit(false.B)
  val r_pcTarget    = RegInit(0.U(32.W))

  when (io.flush) {
    r_alu         := 0.U
    r_rs2         := 0.U
    r_rd          := 0.U
    r_ctrl        := 0.U.asTypeOf(new ControlSignals)
    r_branchTaken := false.B
    r_pcTarget    := 0.U
    r_linkPC      := 0.U
  } .elsewhen (!io.stall) {
    r_alu         := io.in_aluResult
    r_rs2         := io.in_rs2Data
    r_rd          := io.in_rd
    r_ctrl        := io.in_ctrl
    r_branchTaken := io.in_branchTaken
    r_pcTarget    := io.in_pcTarget
    r_linkPC      := io.in_linkPC
  }

  io.out_aluResult   := r_alu
  io.out_rs2Data     := r_rs2
  io.out_rd          := r_rd
  io.out_ctrl        := r_ctrl
  io.out_branchTaken := r_branchTaken
  io.out_pcTarget    := r_pcTarget
  io.out_linkPC := r_linkPC

}
