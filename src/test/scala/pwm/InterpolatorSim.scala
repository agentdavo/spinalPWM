package pwm.test

import spinal.core._
import spinal.core.sim._
import pwm.core._
import scala.util.Random

object InterpolatorSim {
  def main(args: Array[String]): Unit = {
    SimConfig.withWave.doSim(new Interpolator) { dut =>
      dut.clockDomain.forkStimulus(10)

      // Test input: 1 kHz sine wave
      val sampleRate = 96000
      val numSamples = 1000
      val inputSignal = (0 until numSamples).map { i =>
        val t = i.toDouble / sampleRate
        (math.sin(2 * math.Pi * 1000 * t) * ((1 << 31) - 1)).toLong
      }

      // Buffer for 6 samples
      val buffer = Array.fill(6)(0L)
      var bufferIndex = 0

      // Simulate
      for (sample <- inputSignal) {
        buffer(bufferIndex % 6) = sample
        bufferIndex += 1

        // Set inputs
        dut.io.x #= SFix(2.0, 4 exp).raw
        for (i <- 0 until 6) {
          dut.io.y(i) #= SFix.fromBits(buffer((bufferIndex - i - 1) % 6), 4 exp, 32 bits)
        }

        dut.clockDomain.waitSampling()
        val result = dut.io.result.toLong
        println(s"Output: $result")
      }
    }
  }
}