package pwm

import spinal.core._
import spinal.core.sim._
import spinal.lib._

import pwm.core.FeedbackProcessor
import pwm.core.FeedbackProcessorConfig

import scala.collection.mutable.ListBuffer
import scala.math._

object FeedbackProcessorSim {
  def apply(): Unit = {
    val config = FeedbackProcessorConfig()
    SimConfig.withWave.workspacePath("target/sim").doSim(new FeedbackProcessor(config)) { dut =>
      // Clock setup
      dut.clockDomain.forkStimulus(20) // 50 MHz

      // Input signals
      val pcmSamples = ListBuffer[Long]()
      val adcSamples = ListBuffer[Long]()
      val volumeLevels = ListBuffer[Long]()
      val corrections = ListBuffer[Long]()
      val dcOffsets = ListBuffer[Long]()

      // Generate test input: 1 kHz sine wave with DC offset
      val sampleRate = 50000000
      val numSamples = 10000
      val inputSignal = (0 until numSamples).map { i =>
        val t = i.toDouble / sampleRate
        val signal = sin(2 * Pi * 1000 * t) * ((1 << 19) - 1) + (1 << 15) // Add DC offset
        signal.toLong
      }

      // Simulate
      fork {
        for (sample <- inputSignal) {
          dut.io.pcmInput #= sample
          dut.io.pwmExpected #= sample // Simplified assumption
          val adcSample = sample + Random.nextGaussian() * 100 // Add noise
          dut.io.adcSample #= adcSample.toInt
          dut.clockDomain.waitSampling()
          pcmSamples += sample
          adcSamples += adcSample.toLong
          volumeLevels += dut.io.volumeLevel.toLong
          corrections += dut.io.correction.toLong
          dcOffsets += dut.io.dcOffset.toLong
        }
      }.join()

      // Verify volume estimation
      val expectedVolume = inputSignal.map(_.abs.toDouble).sum / numSamples
      val avgVolume = volumeLevels.sum.toDouble / volumeLevels.length
      println(f"Expected Volume: $expectedVolume%.2f, Actual Volume: $avgVolume%.2f")

      // Verify DC offset
      val expectedDC = (1 << 15)
      val avgDC = dcOffsets.sum.toDouble / dcOffsets.length
      println(f"Expected DC Offset: $expectedDC%.2f, Actual DC Offset: $avgDC%.2f")

      // Verify correction
      val avgCorrection = corrections.sum.toDouble / corrections.length
      println(f"Average Correction: $avgCorrection%.2f")
    }
  }

  def main(args: Array[String]): Unit = {
    FeedbackProcessorSim()
  }
}
