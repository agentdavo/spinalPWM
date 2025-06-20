package pwm.core

import spinal.core._
import spinal.lib._

case class HighResTIPWMConfig(
  pwmWidth: Int = 20,
  pwmChannels: Int = 4,
  oversampleRate: Int = 512,
  threshold: Int = 1,
  dutyCycleCorrection: Int = 0,
  pwmClockFrequency: HertzNumber = 50 MHz
)

class HighResTIPWM(config: HighResTIPWMConfig) extends Component {
  import config._

  val io = new Bundle {
    val audioIn = in Stream(SInt(24 bits))
    val adcSample = in SInt(14 bits) // ADC feedback
    val pwmOut = out Vec(UInt(pwmWidth bits), pwmChannels)
    val volumeLevel = out UInt(10 bits) // Volume monitoring
  }

  val pwmCounter = CounterFreeRun(pwmClockFrequency / (oversampleRate * pwmChannels))
  val phaseAccumulator = Reg(UInt(log2Up(oversampleRate) bits)) init(0)

  // Feedback processor with volume monitoring
  val feedback = new FeedbackProcessor(FeedbackProcessorConfig(
    inputWidth = 24,
    adcWidth = 14,
    pwmWidth = pwmWidth,
    volumeBits = 10
  ))
  feedback.io.pcmInput := io.audioIn.payload
  feedback.io.adcSample := io.adcSample
  feedback.io.pwmExpected := noiseShaper.output(0) // Use first channel for feedback
  io.volumeLevel := feedback.io.volumeLevel

  // Interpolator (6-point Lagrange)
  val interpolator = new Area {
    val input = io.audioIn
    val output = Reg(Vec(SInt(pwmWidth bits), pwmChannels))
    val phase = Reg(UInt(log2Up(oversampleRate) bits)) init(0)
    val lagInt = new Interpolator
    lagInt.io.x := phase.asSFix / oversampleRate
    val buffer = Mem(SInt(pwmWidth bits), 8)
    val writePtr = Counter(8)
    when(input.valid) {
      buffer(writePtr) := input.payload.resized
      writePtr.increment()
    }
    lagInt.io.y := (0 until 6).map(i => buffer((writePtr - i) % 8))
    for (i <- 0 until pwmChannels) {
      output(i) := lagInt.io.result
    }
    when(phase =/= (oversampleRate - 1)) {
      phase := phase + 1
    } otherwise {
      phase := 0
    }
  }

  // 4th-order noise shaper
  val noiseShaper = new Area {
    val input = interpolator.output
    val output = Reg(Vec(SInt(pwmWidth bits), pwmChannels))
    val error = Reg(Vec(SInt(4 bits), pwmChannels))
    val coefficients = Seq(
      S(-0.6234).toDouble,
      S(-0.1542).toDouble,
      S(-0.0345).toDouble,
      S(-0.0123).toDouble
    )
    for (i <- 0 until pwmChannels) {
      val quantizedError = error(i).resized
      val dcCorrected = input(i) - feedback.io.dcOffset.resize(pwmWidth bits)
      val x = (dcCorrected - quantizedError).asSInt
      val acc = coefficients.zipWithIndex.map { case (c, j) => c * x(j).toDouble }.reduce(_ + _)
      val y = (acc / 1.0) +: output(i).init
      val newX = Vec(quantizedError, x.dropRight(1))
      val newY = Vec(y.zip(Seq(1, 0, 0, 0)).map { case (y, b) => (y * b.toDouble).truncated })
      val newError = newX - newY
      output(i) := newY.reduce(_ + _)
      error(i) := newError.asSInt.resize(4)
    }
  }

  // PWM corrector with feedback and volume compensation
  val pwmCorrector = new Area {
    val input = noiseShaper.output
    val output = Reg(Vec(UInt(pwmWidth bits), pwmChannels))
    val correction = Reg(Vec(SInt(pwmWidth bits), pwmChannels))
    for (i <- 0 until pwmChannels) {
      val feedbackCorr = correction(i) + feedback.io.correction
      val corrected = (input(i).asSInt + feedbackCorr).max(0).min(U((1 << pwmWidth) - 1))
      val thresholded = corrected >= (U(1 << (pwmWidth - 1)) + dutyCycleCorrection).asUInt
      val dutyCycle = (thresholded.mux(
        True -> ((U(1) << pwmWidth) - 1).asUInt,
        False -> U(0)
      ) * pwmCounter.value).resized
      val thresholdedValue = thresholded.mux(
        True -> ((U(1) << pwmWidth) - 1).asUInt,
        False -> U(0)
      )
      correction(i) := (thresholdedValue.asSInt - input(i).asSInt - dutyCycle.asSInt).max(-threshold).min(threshold)
      output(i) := corrected
    }
  }

  io.pwmOut := pwmCorrector.output
}