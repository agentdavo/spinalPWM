package pwm.util

import spinal.core.sim._
import scala.math._

object TestUtils {
  // Generate a multi-tone signal for testing
  def generateMultiToneSignal(sampleRate: Int, numSamples: Int, freqs: Seq[Double], amplitudes: Seq[Double] = Nil): Seq[Long] = {
    val amps = if (amplitudes.isEmpty) Seq.fill(freqs.length)(1.0) else amplitudes
    (0 until numSamples).map { i =>
      val t = i.toDouble / sampleRate
      val signal = freqs.zip(amps).map { case (f, a) => a * sin(2 * Pi * f * t) }.sum
      (signal * ((1L << 23) - 1)).toLong // Scale to 24-bit signed
    }
  }

  // Compute THD from a signal
  def calculateTHD(signal: Array[Double], fundamentalFreq: Double, sampleRate: Int): Double = {
    case class Complex(real: Double, imag: Double) {
      def magnitude: Double = sqrt(real * real + imag * imag)
    }
    // Simplified FFT implementation (for simulation purposes)
    def computeComplex(signal: Array[Double]): Array[Complex] = {
      val n = signal.length
      Array.tabulate(n) { k =>
        var real = 0.0
        var imag = 0.0
        for (i <- 0 until n) {
          val angle = 2 * Pi * i * k / n
          real += signal(i) * cos(angle)
          imag -= signal(i) * sin(angle)
        }
        Complex(real / n, imag / n)
      }
    }

    val fftResult = computeComplex(signal)
    val fftMagnitude = fftResult.map(_.magnitude)
    val fundamentalIndex = (fundamentalFreq * signal.length / sampleRate).toInt
    val fundamental = fftMagnitude(fundamentalIndex)
    val harmonics = fftMagnitude.indices.filter(i => i % fundamentalIndex == 0 && i != fundamentalIndex && i != 0)
      .map(fftMagnitude(_)).sum
    if (fundamental > 0) 20 * log10(harmonics / fundamental) else -100.0
  }

  // Compute SNR from a signal
  def calculateSNR(signal: Array[Double], fundamentalFreq: Double, sampleRate: Int): Double = {
    case class Complex(real: Double, imag: Double) {
      def magnitude: Double = sqrt(real * real + imag * imag)
    }
    def computeComplex(signal: Array[Double]): Array[Complex] = {
      val n = signal.length
      Array.tabulate(n) { k =>
        var real = 0.0
        var imag = 0.0
        for (i <- 0 until n) {
          val angle = 2 * Pi * i * k / n
          real += signal(i) * cos(angle)
          imag -= signal(i) * sin(angle)
        }
        Complex(real / n, imag / n)
      }
    }

    val fftResult = computeComplex(signal)
    val fftMagnitude = fftResult.map(_.magnitude)
    val fundamentalIndex = (fundamentalFreq * signal.length / sampleRate).toInt
    val fundamental = fftMagnitude(fundamentalIndex)
    val noisePower = signal.map(d => d * d).sum / signal.length - (fundamental * fundamental)
    val noise = if (noisePower > 0) sqrt(noisePower) else 1e-10
    if (fundamental > 0) 20 * log10(fundamental / noise) else 0.0
  }
}
