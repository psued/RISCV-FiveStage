package FiveStage
import chisel3._
import chisel3.util.{ BitPat, MuxCase }
import chisel3.experimental.MultiIOModule
import ImmFormat._
import chisel3.util.MuxLookup
import chisel3.util._  // <-- brings in MuxLookup, Cat, Fill, etc.



class InstructionDecode extends MultiIOModule {

  // Don't touch the test harness
  val testHarness = IO(
    new Bundle {
      val registerSetup = Input(new RegisterSetupSignals)
      val registerPeek  = Output(UInt(32.W))

      val testUpdates   = Output(new RegisterUpdates)
    })


  val io = IO(
    new Bundle {
      /**
        * TODO: Your code here.
        */
      val instruction = Input(new Instruction)
      val pcIn   = Input(UInt(32.W))
      val pcOut  = Output(UInt(32.W))

      val rs1Data = Output(UInt(32.W))
      val rs2Data = Output(UInt(32.W))
      val rs1 = Output(UInt(5.W))
      val rs2 = Output(UInt(5.W))
      val rd = Output(UInt(5.W))
      val imm = Output(UInt(32.W))
      val ctrl = Output(new ControlSignals)
      val branchType = Output(UInt(3.W))
      val op1Select = Output(UInt(1.W))
      val op2Select = Output(UInt(1.W))
      val ALUop = Output(UInt(4.W))

      //Input from writeback, used for register writing
      val wbWriteEnable = Input(Bool())
      val wbWriteAddr = Input(UInt(5.W))
      val wbWriteData = Input(UInt(32.W))


    }
  )

  val registers = Module(new Registers)
  val decoder   = Module(new Decoder).io


  /**
    * Setup. You should not change this code
    */
  registers.testHarness.setup := testHarness.registerSetup
  testHarness.registerPeek    := registers.io.readData1
  testHarness.testUpdates     := registers.testHarness.testUpdates


  /**
    * TODO: Your code here.
    */

  decoder.instruction := io.instruction
  registers.io.readAddress1 := io.instruction.registerRs1
  registers.io.readAddress2 := io.instruction.registerRs2
  // ID must NOT write during decode; WB drives the write port:
  registers.io.writeEnable := io.wbWriteEnable
  registers.io.writeAddress := io.wbWriteAddr
  registers.io.writeData := io.wbWriteData


  io.pcOut := io.pcIn

  // Expose register data and indices
  io.rs1Data := registers.io.readData1
  io.rs2Data := registers.io.readData2
  io.rs1 := io.instruction.registerRs1
  io.rs2 := io.instruction.registerRs2
  io.rd := io.instruction.registerRd

  // Decode immediate

  val inst = io.instruction.instruction
  val s = inst(31)

  val immI = Cat(Fill(20, s), inst(31, 20)) // I-type
  val immS = Cat(Fill(20, s), inst(31, 25), inst(11, 7)) // S-type
  val immB = Cat(Fill(19, s), inst(31), inst(7), inst(30, 25), inst(11, 8), 0.U(1.W)) // B-type (<<1)
  val immU = Cat(inst(31, 12), 0.U(12.W)) // U-type (<<12)
  val immJ = Cat(Fill(11, s), inst(31), inst(19, 12), inst(20), inst(30, 21), 0.U(1.W)) // J-type (<<1)
  val shamt = Cat(0.U(27.W), inst(24, 20)) // 5-bit shamt

  val imm = MuxLookup(decoder.immType, 0.U(32.W), Seq(
    ITYPE -> immI,
    STYPE -> immS,
    BTYPE -> immB,
    UTYPE -> immU,
    JTYPE -> immJ,
    SHAMT -> shamt
  ))
  io.imm := imm


  // Forward decoded control signals
  io.ctrl := decoder.controlSignals
  io.branchType := decoder.branchType
  io.op1Select := decoder.op1Select
  io.op2Select := decoder.op2Select
  io.ALUop := decoder.ALUop


}
