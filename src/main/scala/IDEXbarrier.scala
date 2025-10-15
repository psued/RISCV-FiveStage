package FiveStage
import chisel3._
import chisel3.experimental.MultiIOModule
import Op1Select._      // must contain PC, rs1 (your encoding)
import Op2Select._      // must contain imm, rs2
import branchType._
import ALUOps._

class IDEXbarrier extends MultiIOModule {
  val io = IO(new Bundle {
    // inputs from ID
    val ID_PC         = Input(UInt(32.W))
    val rs1Data_in    = Input(UInt(32.W))
    val rs2Data_in    = Input(UInt(32.W))
    val rs1_in        = Input(UInt(5.W))
    val rs2_in        = Input(UInt(5.W))
    val rd_in         = Input(UInt(5.W))
    val imm_in        = Input(UInt(32.W))
    val ctrl_in       = Input(new ControlSignals)
    val branchType_in = Input(UInt(3.W))
    val op1Select_in  = Input(UInt(1.W))   // widen if your enum needs it
    val op2Select_in  = Input(UInt(1.W))   // widen if your enum needs it
    val ALUop_in      = Input(UInt(4.W))

    // outputs to EX
    val EX_PC         = Output(UInt(32.W))
    val rs1Data_out   = Output(UInt(32.W))
    val rs2Data_out   = Output(UInt(32.W))
    val rs1_out       = Output(UInt(5.W))
    val rs2_out       = Output(UInt(5.W))
    val rd_out        = Output(UInt(5.W))
    val imm_out       = Output(UInt(32.W))
    val ctrl_out      = Output(new ControlSignals)
    val branchType_out= Output(UInt(3.W))
    val op1Select_out = Output(UInt(1.W))
    val op2Select_out = Output(UInt(1.W))
    val ALUop_out     = Output(UInt(4.W))

    // NEW: pre-selected operands (before forwarding)
    val opA_out       = Output(UInt(32.W)) // from PC or rs1
    val opB_out       = Output(UInt(32.W)) // from imm or rs2

    // NEW: whether EX actually reads rs1/rs2 (use for forwarding/stall gating)
    val usesR1        = Output(Bool())
    val usesR2        = Output(Bool())

    val isLoad        = Output(Bool()) // EX is a load (for forwarding/stall gating)


    val stall         = Input(Bool())
    val flush         = Input(Bool())
  })

  // ----------------- pipeline registers -----------------
  val pcReg         = RegInit(0.U(32.W))
  val rs1DataReg    = RegInit(0.U(32.W))
  val rs2DataReg    = RegInit(0.U(32.W))
  val rs1Reg        = RegInit(0.U(5.W))
  val rs2Reg        = RegInit(0.U(5.W))
  val rdReg         = RegInit(0.U(5.W))
  val immReg        = RegInit(0.U(32.W))
  val ctrlReg       = RegInit(0.U.asTypeOf(new ControlSignals))
  val branchTypeReg = RegInit(0.U(3.W))
  val op1SelReg     = RegInit(0.U(1.W))
  val op2SelReg     = RegInit(0.U(1.W))
  val aluopReg      = RegInit(0.U(4.W))

  when (io.flush) {
    pcReg := 0.U; rs1DataReg := 0.U; rs2DataReg := 0.U
    rs1Reg := 0.U; rs2Reg := 0.U; rdReg := 0.U
    immReg := 0.U; ctrlReg := 0.U.asTypeOf(new ControlSignals)
    branchTypeReg := 0.U; op1SelReg := 0.U; op2SelReg := 0.U; aluopReg := 0.U
  } .elsewhen (!io.stall) {
    pcReg         := io.ID_PC
    rs1DataReg    := io.rs1Data_in
    rs2DataReg    := io.rs2Data_in
    rs1Reg        := io.rs1_in
    rs2Reg        := io.rs2_in
    rdReg         := io.rd_in
    immReg        := io.imm_in
    ctrlReg       := io.ctrl_in
    branchTypeReg := io.branchType_in
    op1SelReg     := io.op1Select_in
    op2SelReg     := io.op2Select_in
    aluopReg      := io.ALUop_in
  }

  // ----------------- pass-throughs -----------------
  io.EX_PC          := pcReg
  io.rs1Data_out    := rs1DataReg
  io.rs2Data_out    := rs2DataReg
  io.rs1_out        := rs1Reg
  io.rs2_out        := rs2Reg
  io.rd_out         := rdReg
  io.imm_out        := immReg
  io.ctrl_out       := ctrlReg
  io.branchType_out := branchTypeReg
  io.op1Select_out  := op1SelReg
  io.op2Select_out  := op2SelReg
  io.ALUop_out      := aluopReg

  // ----------------- your request: select operands here -----------------
  // With your encoding (commented): op1: 0=rs1, 1=PC ; op2: 0=rs2, 1=imm
  // If using enums: compare to Op1Select.PC / Op2Select.imm
  val opA = Mux(op1SelReg === PC,  pcReg,  rs1DataReg)
  val opB = Mux(op2SelReg === imm, immReg, rs2DataReg)

  // Registering is not necessary: these are derived from registered fields.
  // They naturally "hold" on stall and zero on flush.
  io.opA_out := opA
  io.opB_out := opB

  // ----------------- derived usage booleans (for fwd/stall gating) -----------------
  // True if EX actually reads rs1/rs2 (i.e., selects those paths).
  // These also respect stall/flush because they’re derived from *latched* selects.
  val isBranch = ctrlReg.branch && (branchTypeReg =/= branchType.DC)
  val isJump = ctrlReg.jump
  // Your opcodeMap uses: JAL  -> (jump=true,  op1Select=PC)
  //                      JALR -> (jump=true,  op1Select=rs1)
  val isJalr = isJump && (op1SelReg === rs1)
  val isStore = ctrlReg.memWrite

  // Raw “selected operand” usage (ALU side)
  val usesR1_raw = (op1SelReg =/= PC) // ALU opA from rs1 (vs PC)
  val usesR2_raw = (op2SelReg =/= imm) // ALU opB from rs2 (vs imm)

  // Some ALU ops (e.g., COPY_B for LUI) do not actually consume opA/rs1
  val aluConsumesR1 = (aluopReg =/= COPY_B) // COPY_B ignores opA
  // If you have ops that ignore opB, you can add a similar mask for R2.

  // Final usage seen by forwarding/hazard unit:
  // - Branches: comparator needs rs1 *and* rs2
  // - JALR:     needs rs1
  // - Stores:   need rs2 (store-data)
  // - Otherwise: ALU reads whichever side is selected and actually consumed
  io.usesR1 := isBranch || isJalr || (aluConsumesR1 && usesR1_raw)
  io.usesR2 := isBranch || isStore || usesR2_raw


  io.isLoad  := io.ctrl_in.memRead

}
