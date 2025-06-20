package pwm

import spinal.core._
import spinal.core.sim._
import spinal.lib._

import audio.pwm._
import pwm.util.TestUtils

import scala.collection.mutable.ListBuffer
import scala.math._

object TopLevelSim {
  def apply(audioData: List[Int]): Unit = {
    val config = TopLevelConfig()
    SimConfig.withWave.workspacePath("target/sim").doSim(new TopLevel(config)) { dut =>
      // Clock setup
      val mclkThread = fork {
        dut.pwmClockDomain.forkStimulus(20) // 50 MHz MCLK
      }
      val audioClockDomain = ClockDomain.external("audio", frequency = FixedFrequency(96 kHz))
      val audioClockThread = fork {
        audioClockDomain.forkStimulus(10416) // 96 kHz
      }

      // Audio input stream
      val audioStream = StreamSource(Fragment(SInt(24 bits)), audioClockDomain) { payload =>
        payload.payload #= audioData(payload.fragmentIndex)
        payload.last := (payload.fragmentIndex == audioData.length - 1)
        if (payload.fragmentIndex % 64 == 0) sleep(10)
      }
      dut.io.audioIn << audioStream.toStream

      // Simulate ADC feedback with noise
      val adcSamples = ListBuffer[Long]()
      val volumeLevels = ListBuffer[Long]()
      fork {
        dut.io.adcValid #= false
        dut.io.adcData #= 0
        dut.pwmClockDomain.waitSampling(100)
        while (true) {
          val pwmOut = dut.io.pwmOut(0).toBoolean
          val volume = dut.io.volumeLevel.toLong
          val sample = (if (pwmOut) 1.0 else -1.0) * (1 << (config.adcWidth - 1)) +
                       Random.nextGaussian() * 0.01 // Add noise
          dut.io.adcData #= sample.toInt
          dut.io.adcValid #= true
          dut.pwmClockDomain.waitSampling()
          dut.io.adcValid #= false
          adcSamples += sample.toLong
          volumeLevels += volume
        }
      }

      // Collect PWM samples
      val pwmSamples = ListBuffer[Boolean]()
      fork {
        dut.pwmClockDomain.waitSampling(1000)
        for (_ <- 0 until 48000) {
          pwmSamples += dut.io.pwmOut(0).toBoolean
          dut.pwmClockDomain.waitSampling()
        }
      }

      // Wait for simulation
      sleep(audioData.length / 96 * 1e9.toLong)

      // Calculate metrics
      val pwmData = pwmSamples.map(if (_) 1.0 else -1.0).toArray
      val thd = TestUtils.calculateTHD(pwmData, 1000.0, 50000000)
      val snr = TestUtils.calculateSNR(pwmData, 1000.0, 50000000)
      val dcOffset = pwmData.sum / pwmData.length
      val volumeAvg = volumeLevels.sum.toDouble / volumeLevels.length
      println(f"THD: $thd%.2f dB, SNR: $snr%.2f dB, DC Offset: $dcOffset%.6f, Avg Volume: $volumeAvg%.2f")

      mclkThread.join()
      audioClockThread.join()
    }
  }

  def main(args: Array[String]): Unit = {
    val audioData = (0 until 96000).map { i =>
      val t = i.toDouble / 96000
      val signal = sin(2 * Pi * 1000 * t) + 0.5 * sin(2 * Pi * 5000 * t) + 0.25 * sin(2 * Pi * 10000 * t)
      (signal * ((1 << 23) - 1)).toInt
    }.toList
    TopLevelSim(audioData)
  }
}
