package FiveStage
import chisel3._
import chisel3.util._
import chisel3.experimental.MultiIOModule

class MemoryFetch() extends MultiIOModule {

  // Don't touch the test harness
  val testHarness = IO(new Bundle {
    val DMEMsetup = Input(new DMEMsetupSignals)
    val DMEMpeek = Output(UInt(32.W))
    val testUpdates = Output(new MemUpdates)
  })

  // MEM <-> pipeline
  val io = IO(new Bundle {
    // From EX stage (or EX/MEM barrier)
    val aluResult = Input(UInt(32.W)) // ALU output (addr for mem ops, arithmetic result otherwise)
    val rs2Data = Input(UInt(32.W)) // store data (unused for ADDI)
    val rd = Input(UInt(5.W)) // destination reg
    val ctrl = Input(new ControlSignals)
    val branchTaken = Input(Bool())
    val pcTarget = Input(UInt(32.W))
    val linkPC = Input(UInt(32.W))


    // To IF (for branch/jump)
    val branchTakenOut = Output(Bool())
    val pcTargetOut = Output(UInt(32.W))
    // To WB (or MEM/WB barrier)
    val wbData = Output(UInt(32.W))
    val wbRd = Output(UInt(5.W))
    val wbWe = Output(Bool())
  })

  val DMEM = Module(new DMEM)

  // Setup. You should not change this code
  DMEM.testHarness.setup := testHarness.DMEMsetup
  testHarness.DMEMpeek := DMEM.io.dataOut
  testHarness.testUpdates := DMEM.testHarness.testUpdates

  // ---------------- MEM logic (aligned with 1-cycle DMEM read latency) ----------------
  // Drive DMEM for this instruction
  DMEM.io.dataAddress := io.aluResult
  DMEM.io.dataIn := io.rs2Data
  DMEM.io.writeEnable := io.ctrl.memWrite

  // Align control & metadata to match when DMEM.io.dataOut is valid
  val memRead_d = RegNext(io.ctrl.memRead, init = false.B)
  val regWrite_d = RegNext(io.ctrl.regWrite, init = false.B)
  val jump_d = RegNext(io.ctrl.jump, init = false.B)
  val rd_d = RegNext(io.rd)
  val alu_d = RegNext(io.aluResult) // ALU result for non-loads
  val link_d = RegNext(io.linkPC, 0.U)



  // NOTE: DMEM.io.dataOut is the read data corresponding to the *previous* cycle's address.
  // With the above RegNexts, memRead_d/rd_d/regWrite_d now "travel" with that data.
  val wbData_d = Mux(memRead_d, DMEM.io.dataOut,
    Mux(jump_d,   link_d,          alu_d))

  // Drive branch/jump outputs
  val branchTaken_d = RegNext(io.branchTaken, init = false.B)
  val pcTarget_d = RegNext(io.pcTarget, init = 0.U(32.W))

  io.branchTakenOut := false.B
  io.pcTargetOut := 0.U

  // Pass-through from EX/MEM barrier inputs
  io.branchTakenOut := io.branchTaken
  io.pcTargetOut := io.pcTarget


  // Drive WB bundle (now correctly aligned)
  io.wbData := wbData_d
  io.wbRd := rd_d
  io.wbWe := regWrite_d


}

