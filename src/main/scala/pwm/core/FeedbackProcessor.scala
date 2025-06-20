package pwm.core

import spinal.core._
import spinal.lib._

case class FeedbackProcessorConfig(
  inputWidth: Int = 24, // PCM input width
  adcWidth: Int = 14, // ADC resolution
  pwmWidth: Int = 20, // PWM output width
  volumeBits: Int = 10 // Bits for volume estimation
)

class FeedbackProcessor(config: FeedbackProcessorConfig) extends Component {
  val io = new Bundle {
    val pcmInput = in SInt(config.inputWidth bits) // Expected audio input
    val pwmExpected = in SInt(config.pwmWidth bits) // Expected PWM duty cycle
    val adcSample = in SInt(config.adcWidth bits) // ADC feedback from gate
    val correction = out SInt(config.pwmWidth bits) // Correction term for PWM
    val dcOffset = out SInt(config.inputWidth bits) // Estimated DC noise
    val volumeLevel = out UInt(config.volumeBits bits) // Estimated volume
  }

  // Scale ADC sample to match PWM width
  val adcScaled = (io.adcSample @@ S(0, config.pwmWidth - config.adcWidth bits)).asSInt

  // Volume estimation (average absolute amplitude over a window)
  val volumeEstimator = new Area {
    val windowSize = 1024 // Samples for averaging (~20 µs at 50 MHz)
    val accum = Reg(UInt(config.pwmWidth + log2Up(windowSize) bits)) init(0)
    val counter = Reg(UInt(log2Up(windowSize) bits)) init(0)
    val absAdc = io.adcSample.abs.asUInt

    when(counter < windowSize - 1) {
      accum := accum + absAdc.resize(accum.getWidth)
      counter := counter + 1
    } otherwise {
      accum := absAdc
      counter := 0
      io.volumeLevel := (accum >> log2Up(windowSize)).resize(config.volumeBits)
    }
  }

  // Calculate discrepancy
  val discrepancy = io.pwmExpected - adcScaled

  // DC offset estimation (low-pass filter)
  val dcFilter = new Area {
    val alpha = SFix(0.0001, -16 exp) // Cutoff ~10 Hz at 50 MHz
    val dcAccum = Reg(SInt(config.inputWidth bits)) init(0)
    dcAccum := dcAccum + (discrepancy.resize(config.inputWidth bits) * alpha).toSInt
    io.dcOffset := dcAccum
  }

  // Volume-dependent compensation
  val compensation = new Area {
    val beta = SFix(0.01, -16 exp) // High-pass filter cutoff ~100 Hz
    val correctionAccum = Reg(SInt(config.pwmWidth bits)) init(0)
    val error = discrepancy - dcFilter.dcAccum.resize(config.pwmWidth bits)

    // Volume-based gain adjustment (reduce gain at high volumes)
    val volumeGain = Mux(
      volumeEstimator.io.volumeLevel > U((1 << config.volumeBits) * 0.8),
      SFix(0.9, -4 exp), // Reduce gain by 10% at high volumes
      SFix(1.0, -4 exp)  // Unity gain otherwise
    )

    // GaN FET non-linearity compensation (quadratic correction)
    val nonLinearCorr = Mux(
      volumeEstimator.io.volumeLevel > U((1 << config.volumeBits) * 0.5),
      (error * SFix(0.02, -8 exp) * volumeEstimator.io.volumeLevel).toSInt,
      S(0, config.pwmWidth bits)
    )

    correctionAccum := correctionAccum + (error * beta * volumeGain + nonLinearCorr).toSInt
    io.correction := correctionAccum
  }
}