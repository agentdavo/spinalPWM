package pwm.util

import spinal.core._
import spinal.lib._

case class StreamResampler(from: HertzNumber, to: HertzNumber) extends Component {
  val io = new Bundle {
    val pcmInput = slave Stream(SInt(24 bits))
    val pcmOutput = master Stream(SInt(24 bits))
  }

  val sourceClockDomain = ClockDomain(
    clock = io.pcmInput.clock,
    reset = io.pcmInput.reset
  )

  val targetClockDomain = ClockDomain(
    clock = io.pcmOutput.clock,
    reset = io.pcmOutput.reset
  )

  val samplingRatio = from.toBigDecimal / to.toBigDecimal
  val bufferDepth = 2

  val buffer = Mem(SInt(24 bits), depth = bufferDepth)
  val bufferWriteAddress = Reg(UInt(log2Up(bufferDepth) bits)) init(0)
  val bufferReadAddress = Reg(UInt(log2Up(bufferDepth) bits)) init(0)
  val bufferEmpty = RegInit(True)

  val inputSample = Reg(SInt(24 bits))
  val outputSample = Reg(SInt(24 bits))
  val counter = Reg(UInt(log2Up(samplingRatio.toInt) bits)) init(0)

  val interpolate = counter === samplingRatio.toInt - 1

  val enableWrite = io.pcmInput.valid && bufferEmpty
  val enableRead = io.pcmOutput.ready && !bufferEmpty

  when(enableWrite) {
    buffer.write(bufferWriteAddress, io.pcmInput.payload)
    bufferWriteAddress := (bufferWriteAddress + 1) % bufferDepth
    bufferEmpty := False
  }

  when(enableRead) {
    inputSample := buffer.readSync(bufferReadAddress, clockCrossing = bufferReadAddress =/= bufferWriteAddress)
    bufferReadAddress := (bufferReadAddress + 1) % bufferDepth
    bufferEmpty := bufferReadAddress === bufferWriteAddress
  }

  when(interpolate) {
    outputSample := ((inputSample * (samplingRatio.toInt - counter) + buffer.readSync(bufferReadAddress, clockCrossing = bufferReadAddress =/= bufferWriteAddress) * counter) / samplingRatio.toInt).round
    bufferReadAddress := (bufferReadAddress + 1) % bufferDepth
  } otherwise {
    outputSample := inputSample
  }

  when(interpolate) {
    counter := 0
  } otherwise {
    counter := counter + 1
  }

  io.pcmInput.ready := enableWrite
  io.pcmOutput.valid := enableRead
  io.pcmOutput.payload := outputSample
}