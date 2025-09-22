package FiveStage
import chisel3._
import chisel3.experimental.MultiIOModule

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
    val op1Select_in  = Input(UInt(1.W))
    val op2Select_in  = Input(UInt(1.W))
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

    val stall         = Input(Bool())
    val flush         = Input(Bool())
  })

  // registers (snapshot)
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


}
