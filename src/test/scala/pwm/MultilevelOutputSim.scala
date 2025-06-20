package pwm

import spinal.core._
import spinal.core.sim._
import spinal.lib._

import pwm.output.MultilevelOutput
import pwm.output.MultilevelOutputConfig
import pwm.util.TestUtils

import scala.collection.mutable.ListBuffer
import scala.math._

object MultilevelOutputSim {
  def apply(): Unit = {
    val config = MultilevelOutputConfig()
    SimConfig.withWave.workspacePath("target/sim").doSim(new MultilevelOutput(config)) { dut =>
      // Clock setup
      dut.clockDomain.forkStimulus(10) // 100 MHz for PWM resolution

      // Input signal: 1 kHz sine wave
      val sampleRate = 96000
      val numSamples = 9600
      val inputSignal = (0 until numSamples).map { i =>
        val t = i.toDouble / sampleRate
        val signal = sin(2 * Pi * 1000 * t)
        (signal * ((1 << 23) - 1)).toInt
      }

      val outputSamples = ListBuffer[Boolean]()

      // Drive input
      fork {
        for (sample <- inputSignal) {
          dut.io.input #= sample
          dut.clockDomain.waitSampling(10) // Simulate at 100 MHz
          outputSamples += dut.io.output.toBoolean
        }
      }.join()

      // Calculate THD and SNR
      val outputData = outputSamples.map(if (_) 1.0 else -1.0).toArray
      val thd = TestUtils.calculateTHD(outputData, 1000.0, 100000000)
      val snr = TestUtils.calculateSNR(outputData, 1000.0, 100000000)
      println(f"THD: $thd%.2f dB, SNR: $snr%.2f dB")
    }
  }

  def main(args: Array[String]): Unit = {
    MultilevelOutputSim()
  }
}
