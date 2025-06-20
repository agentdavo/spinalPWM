package pwm

import spinal.core._
import spinal.core.sim._
import spinal.lib._

import pwm.core.HighResTIPWM
import pwm.core.HighResTIPWMConfig
import pwm.util.TestUtils

import scala.collection.mutable.ListBuffer
import scala.math._

object HighResTIPWMSim {
  def apply(): Unit = {
    val config = HighResTIPWMConfig()
    SimConfig.withWave.workspacePath("target/sim").doSim(new HighResTIPWM(config)) { dut =>
      // Clock setup
      dut.clockDomain.forkStimulus(20) // 50 MHz

      // Input stream
      val sampleRate = 96000
      val numSamples = 10000
      val inputSignal = (0 until numSamples).map { i =>
        val t = i.toDouble / sampleRate
        val signal = sin(2 * Pi * 1000 * t) + 0.5 * sin(2 * Pi * 5000 * t)
        (signal * ((1 << 23) - 1)).toInt
      }

      val pwmSamples = ListBuffer[Boolean]()
      val volumeLevels = ListBuffer[Long]()

      // Drive input stream
      fork {
        dut.io.audioIn.valid #= false
        dut.io.audioIn.payload #= 0
        dut.io.adcSample #= 0
        dut.clockDomain.waitSampling(100)
        for (sample <- inputSignal) {
          dut.io.audioIn.valid #= true
          dut.io.audioIn.payload #= sample
          val adcSample = sample / 4 + Random.nextGaussian() * 100 // Simplified ADC feedback
          dut.io.adcSample #= adcSample.toInt
          dut.clockDomain.waitSampling()
          dut.io.audioIn.valid #= false
          dut.clockDomain.waitSampling(511) // 512x oversampling
          pwmSamples += dut.io.pwmOut(0).msb.toBoolean
          volumeLevels += dut.io.volumeLevel.toLong
        }
      }.join()

      // Calculate THD and SNR
      val pwmData = pwmSamples.map(if (_) 1.0 else -1.0).toArray
      val thd = TestUtils.calculateTHD(pwmData, 1000.0, 50000000)
      val snr = TestUtils.calculateSNR(pwmData, 1000.0, 50000000)
      val volumeAvg = volumeLevels.sum.toDouble / volumeLevels.length
      println(f"THD: $thd%.2f dB, SNR: $snr%.2f dB, Avg Volume: $volumeAvg%.2f")
    }
  }

  def main(args: Array[String]): Unit = {
    HighResTIPWMSim()
  }
}