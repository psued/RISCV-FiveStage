package FiveStage
import chisel3._
import chisel3.experimental.MultiIOModule

class WriteBack extends MultiIOModule {
  val io = IO(new Bundle {
    val wbDataIn = Input(UInt(32.W))
    val wbRdIn   = Input(UInt(5.W))
    val wbWeIn   = Input(Bool())

    // To ID/register file
    val wbDataOut = Output(UInt(32.W))
    val wbRdOut   = Output(UInt(5.W))
    val wbWeOut   = Output(Bool())
  })
  io.wbDataOut := io.wbDataIn
  io.wbRdOut   := io.wbRdIn
  io.wbWeOut   := io.wbWeIn
}
