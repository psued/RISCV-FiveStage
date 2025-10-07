package FiveStage

import chisel3._
import chisel3.core.Input
import chisel3.experimental.MultiIOModule
import chisel3.experimental._
import Op1Select._
import Op2Select._
import branchType._

object FwdSel {
  val Reg = 0.U(2.W)
  val WB  = 1.U(2.W)
  val MEM = 2.U(2.W)
}
class Forwarder extends MultiIOModule{
  val io = IO(new Bundle {
    val i_ra1      = Input(UInt(5.W)) // Input register address 1
    val i_ra2      = Input(UInt(5.W)) // Input register address 2

    val i_use_rs1 = Input(Bool())
    val i_use_rs2 = Input(Bool())


    val i_ALUMEM = Input(UInt(5.W)) // EX/MEM barrier destination register
    val i_is_MEM_load = Input(Bool()) // EX/MEM barrier is load instruction
    val i_write_register = Input(Bool()) // Is the MEM instruction writing to a register

    val i_is_WB_writing = Input(Bool()) // Is the WB instruction writing to a register
    val i_WBdestination = Input(UInt(5.W)) // MEM/WB barrier destination register
    //val i_is_WB_valid = Input(Bool())

    val o_sel_rs1 = Output(UInt(2.W))
    val o_sel_rs2 = Output(UInt(2.W))
    val stall = Output(Bool()) // stall signal
  })

  import FwdSel._

  io.stall := false.B


  val memHit1 = io.i_use_rs1 && io.i_write_register && (io.i_ra1 === io.i_ALUMEM) && (io.i_ALUMEM =/= 0.U)
  val memHit2 = io.i_use_rs2 && io.i_write_register && (io.i_ra2 === io.i_ALUMEM) && (io.i_ALUMEM =/= 0.U)

  val wbHit1 = io.i_use_rs1 && (io.i_ra1 === io.i_WBdestination) && (io.i_is_WB_writing) && (io.i_WBdestination =/= 0.U) //&& i_is_WB_valid
  val wbHit2 = io.i_use_rs2 && (io.i_ra2 === io.i_WBdestination) && (io.i_is_WB_writing) && (io.i_WBdestination =/= 0.U) //&& i_is_WB_valid

  io.o_sel_rs1 := Reg
  io.o_sel_rs2 := Reg

  // rs1: MEM > WB > Reg
  when(memHit1 && !io.i_is_MEM_load) {
    io.o_sel_rs1 := MEM
  }.elsewhen(wbHit1) {
    io.o_sel_rs1 := WB
  }

  // rs2: MEM > WB > Reg
  when(memHit2 && !io.i_is_MEM_load) {
    io.o_sel_rs2 := MEM
  }.elsewhen(wbHit2) {
    io.o_sel_rs2 := WB
  }

  when((memHit1 || memHit2) && io.i_is_MEM_load){
    io.stall := true.B
  }
}