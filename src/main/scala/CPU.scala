package FiveStage

import chisel3._
import chisel3.experimental.MultiIOModule
import chisel3.util.{MuxCase, MuxLookup}
import Op1Select._
import Op2Select._
import branchType._

class CPU extends MultiIOModule {

  val testHarness = IO(new Bundle {
    val setupSignals = Input(new SetupSignals)
    val testReadouts = Output(new TestReadouts)
    val regUpdates   = Output(new RegisterUpdates)
    val memUpdates   = Output(new MemUpdates)
    val currentPC    = Output(UInt(32.W))
  })

  // ---- Pipeline blocks ----
  val IFIDbarrier  = Module(new IFIDbarrier).io
  val IDEXbarrier  = Module(new IDEXbarrier).io
  val EXMEMbarrier = Module(new EXMEMbarrier).io
  val MEMWBbarrier = Module(new MEMWBbarrier).io

  val Forwarder = Module(new Forwarder)

  val IF  = Module(new InstructionFetch)
  val ID  = Module(new InstructionDecode)
  val EX  = Module(new Execute)
  val MEM = Module(new MemoryFetch)
  val WB  = Module(new WriteBack)

  // ---- Harness plumbing (unchanged) ----
  IF.testHarness.IMEMsetup     := testHarness.setupSignals.IMEMsignals
  ID.testHarness.registerSetup := testHarness.setupSignals.registerSignals
  MEM.testHarness.DMEMsetup    := testHarness.setupSignals.DMEMsignals

  testHarness.testReadouts.registerRead := ID.testHarness.registerPeek
  testHarness.testReadouts.DMEMread     := MEM.testHarness.DMEMpeek
  testHarness.regUpdates                := ID.testHarness.testUpdates
  testHarness.memUpdates                := MEM.testHarness.testUpdates
  testHarness.currentPC                 := IF.testHarness.PC

  // ===========================================================================
  //  IF -> ID
  // ===========================================================================
  // *** FAST redirect: drive IF directly from EX (combinational) ***
  val redirectValidEX  = EX.io.branchTaken
  val redirectTargetEX = EX.io.pcTarget

  IF.io.branchTaken  := redirectValidEX
  IF.io.branchTarget := redirectTargetEX

  // IF outputs into IF/ID
  IFIDbarrier.IF_PC          := IF.io.pc_out
  IFIDbarrier.IF_instruction := IF.io.instr_out

  // ===========================================================================
  //  ID
  // ===========================================================================
  ID.io.instruction := IFIDbarrier.ID_instruction
  ID.io.pcIn        := IFIDbarrier.ID_PC

  // ===========================================================================
  //  ID -> EX barrier (IDEX)
  // ===========================================================================
  IDEXbarrier.ID_PC         := ID.io.pcOut
  IDEXbarrier.rs1Data_in    := ID.io.rs1Data
  IDEXbarrier.rs2Data_in    := ID.io.rs2Data
  IDEXbarrier.rs1_in        := ID.io.rs1
  IDEXbarrier.rs2_in        := ID.io.rs2
  IDEXbarrier.rd_in         := ID.io.rd
  IDEXbarrier.imm_in        := ID.io.imm
  IDEXbarrier.ctrl_in       := ID.io.ctrl
  IDEXbarrier.branchType_in := ID.io.branchType
  IDEXbarrier.op1Select_in  := ID.io.op1Select
  IDEXbarrier.op2Select_in  := ID.io.op2Select
  IDEXbarrier.ALUop_in      := ID.io.ALUop
  IDEXbarrier.stall         := false.B // stall is handled via IFID/IDEX flush logic below
  IDEXbarrier.flush         := false.B // will be driven below

  // ===========================================================================
  //  EX (with forwarded operands)
  // ===========================================================================
  // Forwarder inputs (from ID/EX and producers)
  Forwarder.io.i_ra1            := IDEXbarrier.rs1_out
  Forwarder.io.i_ra2            := IDEXbarrier.rs2_out
  Forwarder.io.i_use_rs1        := IDEXbarrier.usesR1
  Forwarder.io.i_use_rs2        := IDEXbarrier.usesR2
  Forwarder.io.i_ex_isStore     := IDEXbarrier.ctrl_out.memWrite
  Forwarder.io.i_ALUMEM         := EXMEMbarrier.out_rd
  Forwarder.io.i_is_MEM_load    := EXMEMbarrier.out_ctrl.memRead
  Forwarder.io.i_write_register := EXMEMbarrier.out_ctrl.regWrite
  Forwarder.io.i_is_WB_writing  := MEMWBbarrier.out_wbWe
  Forwarder.io.i_WBdestination  := MEMWBbarrier.out_wbRd



  // Forwarded operands into EX:
  // NOTE: when selecting EX/MEM (sel==2) and that producer is a jump,
  //       use link (PC+4) instead of ALU result.
  val exmemFwdDataForRegs = Mux(EXMEMbarrier.out_ctrl.jump,
    EXMEMbarrier.out_linkPC,        // JAL/JALR forward = link
    EXMEMbarrier.out_aluResult)     // normal ALU forward

  EX.io.rs1Data := MuxCase(IDEXbarrier.rs1Data_out, Array(
    (Forwarder.io.o_sel_rs1 === 1.U) -> MEMWBbarrier.out_wbData,
    (Forwarder.io.o_sel_rs1 === 2.U) -> exmemFwdDataForRegs
  ))
  EX.io.rs2Data := MuxCase(IDEXbarrier.rs2Data_out, Array(
    (Forwarder.io.o_sel_rs2 === 1.U) -> MEMWBbarrier.out_wbData,
    (Forwarder.io.o_sel_rs2 === 2.U) -> exmemFwdDataForRegs
  ))

  // Store-data forwarding mirrors rs2 path
  val storeData = MuxLookup(Forwarder.io.o_sel_store, IDEXbarrier.rs2Data_out, Seq(
    FwdSel.WB  -> MEMWBbarrier.out_wbData,
    FwdSel.MEM -> exmemFwdDataForRegs
  ))

  // EX gets the rest from ID/EX barrier
  EX.io.pc         := IDEXbarrier.EX_PC
  EX.io.rd         := IDEXbarrier.rd_out
  EX.io.imm        := IDEXbarrier.imm_out
  EX.io.ctrl       := IDEXbarrier.ctrl_out
  EX.io.branchType := IDEXbarrier.branchType_out
  EX.io.op1Select  := IDEXbarrier.op1Select_out
  EX.io.op2Select  := IDEXbarrier.op2Select_out
  EX.io.ALUop      := IDEXbarrier.ALUop_out

  // ===========================================================================
  //  EX -> MEM barrier (EXMEM)
  // ===========================================================================
  EXMEMbarrier.stall         := false.B
  // IMPORTANT: we resolve branches/jumps in EX now, so we do NOT need to flush EX/MEM
  // for a late-kill here. If you keep a late MEM resolve path, you can wire a late flush.
  EXMEMbarrier.flush         := false.B

  EXMEMbarrier.in_aluResult  := EX.io.aluResult
  EXMEMbarrier.in_rs2Data    := storeData
  EXMEMbarrier.in_rd         := EX.io.rdOut
  EXMEMbarrier.in_ctrl       := EX.io.ctrlOut
  EXMEMbarrier.in_branchTaken:= EX.io.branchTaken
  EXMEMbarrier.in_pcTarget   := EX.io.pcTarget
  EXMEMbarrier.in_linkPC     := EX.io.linkPC

  // ===========================================================================
  //  MEM
  // ===========================================================================
  MEM.io.aluResult  := EXMEMbarrier.out_aluResult
  MEM.io.rs2Data    := EXMEMbarrier.out_rs2Data
  MEM.io.rd         := EXMEMbarrier.out_rd
  MEM.io.ctrl       := EXMEMbarrier.out_ctrl
  MEM.io.branchTaken:= EXMEMbarrier.out_branchTaken   // for tracing/visibility
  MEM.io.pcTarget   := EXMEMbarrier.out_pcTarget      // for tracing/visibility
  MEM.io.linkPC     := EXMEMbarrier.out_linkPC

  // ===========================================================================
  //  MEM -> WB barrier & WB
  // ===========================================================================
  MEMWBbarrier.in_wbData := MEM.io.wbData
  MEMWBbarrier.in_wbRd   := MEM.io.wbRd
  MEMWBbarrier.in_wbWe   := MEM.io.wbWe
  MEMWBbarrier.stall     := false.B
  MEMWBbarrier.flush     := false.B

  WB.io.wbDataIn := MEMWBbarrier.out_wbData
  WB.io.wbRdIn   := MEMWBbarrier.out_wbRd
  WB.io.wbWeIn   := MEMWBbarrier.out_wbWe

  // Regfile writeback into ID
  ID.io.wbWriteEnable := WB.io.wbWeOut
  ID.io.wbWriteAddr   := WB.io.wbRdOut
  ID.io.wbWriteData   := WB.io.wbDataOut

  // ===========================================================================
  //  Hazard detection (your logic, with JALR accounted as rs1 user)
  // ===========================================================================
  val id_rs1 = IFIDbarrier.ID_instruction.registerRs1
  val id_rs2 = IFIDbarrier.ID_instruction.registerRs2

  val id_isBranch = ID.io.ctrl.branch && (ID.io.branchType =/= branchType.DC)
  val id_isJump   = ID.io.ctrl.jump
  val id_isJalr   = id_isJump && (ID.io.op1Select === Op1Select.rs1)
  val id_isStore  = ID.io.ctrl.memWrite

  val id_use_rs1 = (ID.io.op1Select =/= Op1Select.PC) || id_isBranch || id_isJalr
  val id_use_rs2 = (ID.io.op2Select =/= Op2Select.imm) || id_isBranch || id_isStore

  val ex_rd       = IDEXbarrier.rd_out
  val ex_memRead  = IDEXbarrier.ctrl_out.memRead
  val ex_isLoad   = ex_memRead

  val loadUseHazard =
    ex_memRead && (ex_rd =/= 0.U) &&
      ((id_use_rs1 && (id_rs1 === ex_rd)) || (id_use_rs2 && (id_rs2 === ex_rd)))

  val loadStoreHazard =
    ex_isLoad && id_isStore && (ex_rd =/= 0.U) && (id_rs2 === ex_rd)

  val hazard = loadUseHazard || loadStoreHazard

  // ===========================================================================
  //  Control: redirect/flush/stall (EX is source of truth)
  // ===========================================================================
  // Redirect must beat stall everywhere.
  IFIDbarrier.stall := hazard && !redirectValidEX
  IF.io.stallF      := hazard && !redirectValidEX

  IDEXbarrier.flush := hazard || redirectValidEX // bubble into EX next cycle
  IFIDbarrier.flush := redirectValidEX           // kill younger instr in ID immediately

  // NOTE: EXMEMbarrier.flush := false (set above). If you keep a path where branch resolves in MEM,
  // wire EXMEMbarrier.flush := MEM.io.branchTakenOut for that *late* case only.
}
