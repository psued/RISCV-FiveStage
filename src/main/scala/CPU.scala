package FiveStage

import chisel3._
import chisel3.core.Input
import chisel3.experimental.MultiIOModule
import chisel3.experimental._
import chisel3.util.MuxCase
import chisel3.util.MuxLookup
import Op1Select._
import Op2Select._
import branchType._



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

  IFIDbarrier.IF_PC := IF.io.pc_out
  IFIDbarrier.IF_instruction := IF.io.instr_out
  //IFIDbarrier.stall := false.B

  ID.io.instruction := IFIDbarrier.ID_instruction
  ID.io.pcIn := IFIDbarrier.ID_PC

  IDEXbarrier.ID_PC := ID.io.pcOut
  IDEXbarrier.rs1Data_in := ID.io.rs1Data
  IDEXbarrier.rs2Data_in := ID.io.rs2Data
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
  //EXMEMbarrier.flush := false.B
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
  Forwarder.io.i_use_rs1 := IDEXbarrier.usesR1
  Forwarder.io.i_use_rs2 := IDEXbarrier.usesR2

  Forwarder.io.i_ex_isStore := IDEXbarrier.ctrl_out.memWrite
  Forwarder.io.i_ALUMEM := EXMEMbarrier.out_rd
  Forwarder.io.i_is_MEM_load := EXMEMbarrier.out_ctrl.memRead
  Forwarder.io.i_write_register := EXMEMbarrier.out_ctrl.regWrite

  Forwarder.io.i_is_WB_writing := MEMWBbarrier.out_wbWe
  Forwarder.io.i_WBdestination := MEMWBbarrier.out_wbRd

  EX.io.rs1Data := MuxCase(IDEXbarrier.rs1Data_out, Array(
    (Forwarder.io.o_sel_rs1 === 0.U) -> IDEXbarrier.rs1Data_out,
    (Forwarder.io.o_sel_rs1 === 1.U) -> MEMWBbarrier.out_wbData,
     (Forwarder.io.o_sel_rs1 === 2.U) -> EXMEMbarrier.out_aluResult
  ))
  EX.io.rs2Data := MuxCase(IDEXbarrier.rs2Data_out, Array(
    (Forwarder.io.o_sel_rs2 === 0.U) -> IDEXbarrier.rs2Data_out,
    (Forwarder.io.o_sel_rs2 === 1.U) -> MEMWBbarrier.out_wbData,
    (Forwarder.io.o_sel_rs2 === 2.U) -> EXMEMbarrier.out_aluResult
    ))
  val storeData = MuxLookup(Forwarder.io.o_sel_store, IDEXbarrier.rs2Data_out, Seq(
    FwdSel.WB -> MEMWBbarrier.out_wbData,
    FwdSel.MEM -> EXMEMbarrier.out_aluResult
  ))
  EXMEMbarrier.in_rs2Data := storeData


/*  IDEXbarrier.rs1Data_in := Forwarder.io.o_rs1
  IDEXbarrier.rs2Data_in := Forwarder.io.o_rs2*/

  val id_rs1 = IFIDbarrier.ID_instruction.registerRs1
  val id_rs2 = IFIDbarrier.ID_instruction.registerRs2

  // Kinds from ID decoder
  val id_isBranch = ID.io.ctrl.branch && (ID.io.branchType =/= branchType.DC)
  val id_isJump   = ID.io.ctrl.jump
  val id_isJalr   = id_isJump && (ID.io.op1Select === Op1Select.rs1)  // JALR reads rs1
  val id_isStore  = ID.io.ctrl.memWrite

  // “Does the *ID* instruction read rs1/rs2?”
  val id_use_rs1 = (ID.io.op1Select =/= Op1Select.PC) || id_isBranch || id_isJalr
  val id_use_rs2 = (ID.io.op2Select =/= Op2Select.imm) || id_isBranch || id_isStore

  val ex_rd = IDEXbarrier.rd_out
  val ex_memRead = IDEXbarrier.ctrl_out.memRead
  val ex_isLoad = IDEXbarrier.ctrl_out.memRead

  val loadUseHazard =
    ex_memRead && (ex_rd =/= 0.U) &&
      ((id_use_rs1 && (id_rs1 === ex_rd)) || (id_use_rs2 && (id_rs2 === ex_rd)))

  val loadStoreHazard =
    ex_isLoad && id_isStore && (ex_rd =/= 0.U) && (id_rs2 === ex_rd)


  val hazard = loadUseHazard || loadStoreHazard
  val branchTaken = MEM.io.branchTakenOut


  IFIDbarrier.stall := hazard // freeze IF & ID
  IF.io.stallF := hazard // freeze IF
  IDEXbarrier.flush := hazard || branchTaken //|| branchTaken // inject one bubble into EX


  //IFIDbarrier.flush := false.B //branchTaken
  IFIDbarrier.flush := branchTaken
  EXMEMbarrier.flush := branchTaken
  //IDEXbarrier.flush := branchTaken

 /* when(true.B) {
    printf(p"[HZ ] ID.rs1=$id_rs1 use1=$id_use_rs1 ID.rs2=$id_rs2 use2=$id_use_rs2 | " +
      p"EX.rd=$ex_rd EX.memRead=$ex_memRead | stall=$loadUseHazard\n")
  }


  // ---------- IF / IFID ----------
  {
    // IF: what address are we fetching and are we frozen?
    printf(p"[IF ] pc=${Hexadecimal(IF.io.pc_out)} stallF=${IF.io.stallF} " +
      p"br=${MEM.io.branchTakenOut} tgt=${Hexadecimal(MEM.io.pcTargetOut)}\n")

    // IFID: what did we actually latch into ID this cycle?
    printf(p"[IFID] hold=${IFIDbarrier.stall} " +
      p"pc=${Hexadecimal(IFIDbarrier.ID_PC)} " +
      p"instr=${Hexadecimal(IFIDbarrier.ID_instruction.instruction)}\n")
  }

  // ---------- IDEX (what EX will see) ----------
  {
    printf(p"[IDEX] flush=${IDEXbarrier.flush} " +
      p"pc=${Hexadecimal(IDEXbarrier.EX_PC)} " +
      p"rd=${IDEXbarrier.rd_out} " +
      p"memR=${IDEXbarrier.ctrl_out.memRead} memW=${IDEXbarrier.ctrl_out.memWrite} " +
      p"regW=${IDEXbarrier.ctrl_out.regWrite} " +
      p"op1Sel=${IDEXbarrier.op1Select_out} op2Sel=${IDEXbarrier.op2Select_out} " +
      p"usesR1=${IDEXbarrier.usesR1} usesR2=${IDEXbarrier.usesR2}\n")
  }

  // ---------- IDEX (what EX will see) ----------
  {
    printf(p"[IDEX] flush=${IDEXbarrier.flush} " +
      p"pc=${Hexadecimal(IDEXbarrier.EX_PC)} " +
      p"rd=${IDEXbarrier.rd_out} " +
      p"memR=${IDEXbarrier.ctrl_out.memRead} memW=${IDEXbarrier.ctrl_out.memWrite} " +
      p"regW=${IDEXbarrier.ctrl_out.regWrite} " +
      p"op1Sel=${IDEXbarrier.op1Select_out} op2Sel=${IDEXbarrier.op2Select_out} " +
      p"usesR1=${IDEXbarrier.usesR1} usesR2=${IDEXbarrier.usesR2}\n")
  }

  // ---------- FORWARDER (mux selects) ----------
  {
    printf(p"[FWD] rs1=${IDEXbarrier.rs1_out} rs2=${IDEXbarrier.rs2_out} | " +
      p"MEM.rd=${EXMEMbarrier.out_rd} MEM.memR=${EXMEMbarrier.out_ctrl.memRead} MEM.regW=${EXMEMbarrier.out_ctrl.regWrite} | " +
      p"WB.rd=${MEMWBbarrier.out_wbRd} WB.we=${MEMWBbarrier.out_wbWe} | " +
      p"sel1=${Forwarder.io.o_sel_rs1} sel2=${Forwarder.io.o_sel_rs2}\n")
  }

  // ---------- EX/MEM ----------
  {
    printf(p"[EXM] rd=${EXMEMbarrier.out_rd} " +
      p"regW=${EXMEMbarrier.out_ctrl.regWrite} memR=${EXMEMbarrier.out_ctrl.memRead} memW=${EXMEMbarrier.out_ctrl.memWrite} " +
      p"alu=${Hexadecimal(EXMEMbarrier.out_aluResult)} " +
      p"br=${EXMEMbarrier.out_branchTaken} " +
      p"pcTgt=${Hexadecimal(EXMEMbarrier.out_pcTarget)}\n")
  }

  // ---------- MEM (current vs commit) ----------
  {
    // current MEM inputs (the instruction *issuing* address to DMEM this cycle)
    printf(p"[MEM cur ] we=${MEM.io.ctrl.regWrite} memRd=${MEM.io.ctrl.memRead} rd=${MEM.io.rd} " +
      p"alu=${Hexadecimal(MEM.io.aluResult)}\n")

    // what you are actually sending to WB this cycle (with your sync DMEM and RegNext alignment)
    printf(p"[MEM prev] WB.we=${MEM.io.wbWe} rd=${MEM.io.wbRd} " +
      p"data=${Hexadecimal(MEM.io.wbData)}\n")
  }

  // ---------- MEM/WB & WB (commit) ----------
  {
    // if MEMWB is pass-through this equals MEM.wb*
    printf(p"[WB ] we=${WB.io.wbWeOut} rd=${WB.io.wbRdOut} " +
      p"data=${Hexadecimal(WB.io.wbDataOut)}\n")
  }*/

}