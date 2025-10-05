package FiveStage

import chisel3._
import chisel3.util._
import chisel3.experimental.MultiIOModule
import Op1Select._
import Op2Select._
import branchType._

class Forwarder extends MultiIOModule{
  val io = IO(new Bundle {
    val i_ra1      = Input(UInt(5.W)) // Input register address 1
    val i_ra1_data = Input(UInt(32.W)) // Input register address 1 data
    val i_ra2      = Input(UInt(5.W)) // Input register address 2
    val i_ra2_data = Input(UInt(32.W)) // Input register address 2 data

    val i_ALUMEM = Input(UInt(5.W)) // EX/MEM barrier destination register
    val i_ALUMEM_data = Input(UInt(32.W)) // EX/MEM barrier destination register data
    val i_is_MEM_load = Input(Bool()) // EX/MEM barrier is load instruction
    val i_write_register = Input(Bool()) // Is the MEM instruction writing to a register

    val i_is_WB_writing = Input(Bool()) // Is the WB instruction writing to a register
    val i_WBdestination = Input(UInt(5.W)) // MEM/WB barrier destination register
    val i_WBdata = Input(UInt(32.W)) // MEM/WB barrier destination register data

    val o_rs1 = Output(UInt(32.W)) // Output address 1
    val o_rs2 = Output(UInt(32.W)) // Output address 2
    val stall = Output(Bool()) // stall signal
  })

  //Default values
  io.o_rs1 := io.i_ra1_data
  io.o_rs2 := io.i_ra2_data
  io.stall := false.B

  //Forwarding logic for rs1
  when(io.i_ra1 =/= 0.U && (io.i_ra1 =/= io.i_ALUMEM) && (io.i_ra1 =/= io.i_WBdestination)){
    io.o_rs1 := io.i_ra1_data
  }.elsewhen(io.i_ra1 =/= 0.U && (io.i_ra1 =/= io.i_ALUMEM) && (io.i_ra1 === io.i_WBdestination) && (io.i_is_WB_writing)){
    io.o_rs1 := io.i_WBdata
  }.elsewhen((io.i_ra1 =/= 0.U) && (io.i_is_MEM_load) && io.i_ra1 =/= io.i_WBdestination){
    io.stall := true.B
  }.elsewhen(io.i_ra1 === io.i_ALUMEM && (io.i_ra1 =/= io.i_WBdestination) && (io.i_write_register)){
    io.o_rs1 := io.i_ALUMEM_data
  }

  when(io.i_ra2 =/= 0.U && (io.i_ra2 =/= io.i_ALUMEM) && (io.i_ra2 =/= io.i_WBdestination)) {
    io.o_rs2 := io.i_ra2_data
  }.elsewhen(io.i_ra2 =/= 0.U && (io.i_ra2 =/= io.i_ALUMEM) && (io.i_ra2 === io.i_WBdestination) && (io.i_is_WB_writing)) {
    io.o_rs2 := io.i_WBdata
  }.elsewhen((io.i_ra2 =/= 0.U) && (io.i_is_MEM_load)) {
    io.stall := true.B
  }.elsewhen(io.i_ra2 === io.i_ALUMEM && (io.i_ra2 =/= io.i_WBdestination) && (io.i_write_register)) {
    io.o_rs2 := io.i_ALUMEM_data
  }


}