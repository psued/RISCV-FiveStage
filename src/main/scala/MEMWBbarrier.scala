package FiveStage
import chisel3._
import chisel3.experimental.MultiIOModule

class MEMWBbarrier extends MultiIOModule {
  val io = IO(new Bundle {
    // From MEM
    val in_wbData = Input(UInt(32.W))
    val in_wbRd   = Input(UInt(5.W))
    val in_wbWe   = Input(Bool())

    // To WB (or CPU top)
    val out_wbData = Output(UInt(32.W))
    val out_wbRd   = Output(UInt(5.W))
    val out_wbWe   = Output(Bool())

    val stall = Input(Bool())
    val flush = Input(Bool())
  })

/*  val r_data = RegInit(0.U(32.W))
  val r_rd   = RegInit(0.U(5.W))
  val r_we   = RegInit(false.B)*/

  when (io.flush) {
/*    r_data := 0.U
    r_rd   := 0.U
    r_we   := false.B*/
    io.out_wbData := 0.U
    io.out_wbRd   := 0.U
    io.out_wbWe   := false.B
  } .elsewhen (!io.stall) {
    io.out_wbData := io.in_wbData
    io.out_wbRd   := io.in_wbRd
    io.out_wbWe   := io.in_wbWe
/*    r_data := io.in_wbData
    r_rd   := io.in_wbRd
    r_we   := io.in_wbWe*/
  }
/*
  io.out_wbData := r_data
  io.out_wbRd   := r_rd
  io.out_wbWe   := r_we*/
  io.out_wbData := io.in_wbData
  io.out_wbRd   := io.in_wbRd
  io.out_wbWe   := io.in_wbWe
}
