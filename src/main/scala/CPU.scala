package FiveStage

import chisel3._
import chisel3.core.Input
import chisel3.experimental.MultiIOModule
import chisel3.experimental._


class CPU extends MultiIOModule {

  val testHarness = IO(
    new Bundle {
      val setupSignals = Input(new SetupSignals)
      val testReadouts = Output(new TestReadouts)
      val regUpdates   = Output(new RegisterUpdates)
      val memUpdates   = Output(new MemUpdates)
      val currentPC    = Output(UInt(32.W))
    }
  )

  /**
    You need to create the classes for these yourself
    */
  // val IFBarrier  = Module(new IFBarrier).io
  // val IDBarrier  = Module(new IDBarrier).io
  // val EXBarrier  = Module(new EXBarrier).io
  // val MEMBarrier = Module(new MEMBarrier).io

  val IFIDbarrier = Module(new IFIDbarrier).io
  val IDEXbarrier = Module(new IDEXbarrier).io
  val EXMEMbarrier = Module(new EXMEMbarrier).io
  val MEMWBbarrier = Module(new MEMWBbarrier).io

  val Forwarder = Module(new Forwarder)

  val ID  = Module(new InstructionDecode)
  val IF  = Module(new InstructionFetch)
  val EX  = Module(new Execute)
  val MEM = Module(new MemoryFetch)
  val WB  = Module(new WriteBack)


  /**
    * Setup. You should not change this code
    */
  IF.testHarness.IMEMsetup     := testHarness.setupSignals.IMEMsignals
  ID.testHarness.registerSetup := testHarness.setupSignals.registerSignals
  MEM.testHarness.DMEMsetup    := testHarness.setupSignals.DMEMsignals

  testHarness.testReadouts.registerRead := ID.testHarness.registerPeek
  testHarness.testReadouts.DMEMread     := MEM.testHarness.DMEMpeek

  /**
    spying stuff
    */
  testHarness.regUpdates := ID.testHarness.testUpdates
  testHarness.memUpdates := MEM.testHarness.testUpdates
  testHarness.currentPC  := IF.testHarness.PC


  /**
    TODO: Your code here
    */
  //Send over values from IF to ID through IFID barrier
  IF.io.branchTaken := MEM.io.branchTakenOut
  IF.io.branchTarget := MEM.io.pcTargetOut

  IFIDbarrier.IF_PC := IF.io.PC
  IFIDbarrier.IF_instruction := IF.io.instruction
  //IFIDbarrier.stall := false.B

  ID.io.instruction := IFIDbarrier.ID_instruction
  ID.io.pcIn := IFIDbarrier.ID_PC

  IDEXbarrier.ID_PC := ID.io.pcOut
/*  IDEXbarrier.rs1Data_in := ID.io.rs1Data
  IDEXbarrier.rs2Data_in := ID.io.rs2Data*/
  IDEXbarrier.rs1_in := ID.io.rs1
  IDEXbarrier.rs2_in := ID.io.rs2
  IDEXbarrier.rd_in := ID.io.rd
  IDEXbarrier.imm_in := ID.io.imm
  IDEXbarrier.ctrl_in := ID.io.ctrl
  IDEXbarrier.branchType_in := ID.io.branchType
  IDEXbarrier.op1Select_in := ID.io.op1Select
  IDEXbarrier.op2Select_in := ID.io.op2Select
  IDEXbarrier.ALUop_in := ID.io.ALUop
  IDEXbarrier.stall := false.B
  IDEXbarrier.flush := false.B



  EX.io.pc := IDEXbarrier.EX_PC
  EX.io.rs1Data := IDEXbarrier.rs1Data_out
  EX.io.rs2Data := IDEXbarrier.rs2Data_out
  EX.io.rd := IDEXbarrier.rd_out
  EX.io.imm := IDEXbarrier.imm_out
  EX.io.ctrl := IDEXbarrier.ctrl_out
  EX.io.branchType := IDEXbarrier.branchType_out
  EX.io.op1Select := IDEXbarrier.op1Select_out
  EX.io.op2Select := IDEXbarrier.op2Select_out
  EX.io.ALUop := IDEXbarrier.ALUop_out


  EXMEMbarrier.stall := false.B
  EXMEMbarrier.flush := false.B
  EXMEMbarrier.in_aluResult := EX.io.aluResult
  EXMEMbarrier.in_rs2Data := EX.io.rs2Pass // store data (unused for ADDI)
  EXMEMbarrier.in_rd := EX.io.rdOut
  EXMEMbarrier.in_ctrl := EX.io.ctrlOut
  EXMEMbarrier.in_branchTaken := EX.io.branchTaken
  EXMEMbarrier.in_pcTarget    := EX.io.pcTarget
  EXMEMbarrier.in_linkPC := EX.io.linkPC


  MEM.io.aluResult := EXMEMbarrier.out_aluResult
  MEM.io.rs2Data := EXMEMbarrier.out_rs2Data
  MEM.io.rd := EXMEMbarrier.out_rd
  MEM.io.ctrl := EXMEMbarrier.out_ctrl
  MEM.io.branchTaken := EXMEMbarrier.out_branchTaken
  MEM.io.pcTarget    := EXMEMbarrier.out_pcTarget
  MEM.io.linkPC          := EXMEMbarrier.out_linkPC


  MEMWBbarrier.in_wbData := MEM.io.wbData
  MEMWBbarrier.in_wbRd := MEM.io.wbRd
  MEMWBbarrier.in_wbWe := MEM.io.wbWe
  MEMWBbarrier.stall := false.B
  MEMWBbarrier.flush := false.B


  WB.io.wbDataIn := MEMWBbarrier.out_wbData
  WB.io.wbRdIn := MEMWBbarrier.out_wbRd
  WB.io.wbWeIn := MEMWBbarrier.out_wbWe

  // Wire WB -> ID
  ID.io.wbWriteEnable := WB.io.wbWeOut
  ID.io.wbWriteAddr := WB.io.wbRdOut
  ID.io.wbWriteData := WB.io.wbDataOut

  // ------FORWARDER--------
  Forwarder.io.i_ra1 := IDEXbarrier.rs1_out
  Forwarder.io.i_ra2 := IDEXbarrier.rs2_out
  Forwarder.io.i_ra1_data := ID.io.rs1Data
  Forwarder.io.i_ra2_data := ID.io.rs2Data

  Forwarder.io.i_ALUMEM := EXMEMbarrier.out_rd
  Forwarder.io.i_ALUMEM_data := EXMEMbarrier.out_aluResult
  Forwarder.io.i_is_MEM_load := EXMEMbarrier.out_ctrl.memRead
  Forwarder.io.i_write_register := EXMEMbarrier.out_ctrl.regWrite

  Forwarder.io.i_is_WB_writing := MEMWBbarrier.out_wbWe
  Forwarder.io.i_WBdestination := MEMWBbarrier.out_wbRd
  Forwarder.io.i_WBdata := MEMWBbarrier.out_wbData

  IDEXbarrier.rs1Data_in := Forwarder.io.o_rs1
  IDEXbarrier.rs2Data_in := Forwarder.io.o_rs2

  IDEXbarrier.stall := Forwarder.io.stall
  IFIDbarrier.stall := Forwarder.io.stall
}
