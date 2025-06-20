package pwm.util

import spinal.core._
import spinal.lib._

case class FFTConfig(
  fftSize: Int = 512,
  dataWidth: Int = 24,
  phaseWidth: Int = 16
) {
  val twiddles = Vec(ComplexNumber(dataWidth bits, phaseWidth bits), fftSize / 2)
  def getTwiddle(n: Int): ComplexNumber = twiddles(n)
}

class FFT(config: FFTConfig) extends Component {
  val io = new Bundle {
    val in = Vec(ComplexNumber(config.dataWidth bits, config.phaseWidth bits), config.fftSize)
    val out = Vec(ComplexNumber(config.dataWidth bits, config.phaseWidth bits), config.fftSize)
  }

  val stages = log2Up(config.fftSize)
  val butterflyData = Reg(Vec(Vec(ComplexNumber(config.dataWidth bits, config.phaseWidth bits), config.fftSize / 2), stages))
  val butterflyTwiddles = Vec(Vec(ComplexNumber(config.dataWidth bits, config.phaseWidth bits), config.fftSize / 2), stages)
  val reversedIndex = Vec(UInt(stages bits), config.fftSize)

  reversedIndex(0) := 0
  for (i <- 1 until stages) {
    reversedIndex(i) := (reversedIndex(i - 1) | (1 << (i - 1))).resize(stages bits)
  }

  for (i <- 0 until config.fftSize / 2) {
    val twiddle = config.getTwiddle(i)
    butterflyTwiddles(0)(i) := twiddle
    butterflyTwiddles(1)(i) := twiddle
    butterflyData(0)(i) := io.in(i) + io.in(i + config.fftSize / 2)
    butterflyData(0)(i + config.fftSize / 2) := (io.in(i) - io.in(i + config.fftSize / 2)) * twiddle
  }

  for (stage <- 1 until stages) {
    val halfSize = 1 << stage
    for (i <- 0 until config.fftSize by halfSize) {
      for (j <- 0 until halfSize / 2) {
        val index = reversedIndex(stage - 1) | (i + j)
        val twiddle = butterflyTwiddles(stage)(j)
        butterflyData(stage)(i + j) := butterflyData(stage - 1)(index) + butterflyData(stage - 1)(index + halfSize / 2)
        butterflyData(stage)(i + j + halfSize / 2) := (butterflyData(stage - 1)(index) - butterflyData(stage - 1)(index + halfSize / 2)) * twiddle
      }
    }
    butterflyTwiddles(stage + 1) := butterflyTwiddles(stage)
  }

  io.out := butterflyData.last
}