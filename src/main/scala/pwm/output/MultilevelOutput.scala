package pwm.output

import spinal.core._
import spinal.lib._

case class MultilevelOutputConfig(
  inputWidth: BitCount = 24 bits,
  resolution: BitCount = 12 bits,
  levels: Int = 9,
  thresholdWidth: BitCount = 12 bits,
  step: UInt = 1,
  shift: Int = 3,
  deadtime: UInt = 1
)

class MultilevelOutput(config: MultilevelOutputConfig) extends Component {
  val io = new Bundle {
    val input = in SInt(config.inputWidth)
    val output = out Bool
  }

  val pwmOutputsPos = List.tabulate(config.levels) { i =>
    val pwm = new PWMOutput(config.resolution, polarity = True, config.deadtime)
    pwm.io.dutyCycle := (io.input.abs() * (i + 1)) >> config.shift
    pwm
  }

  val pwmOutputsNeg = List.tabulate(config.levels) { i =>
    val pwm = new PWMOutput(config.resolution, polarity = False, config.deadtime)
    pwm.io.dutyCycle := (io.input.abs() * (i + 1)) >> config.shift
    pwm
  }

  val pfmOutputs = List.tabulate(config.levels) { i =>
    val pfm = new PFMOutput(config.inputWidth, config.thresholdWidth, config.step)
    pfm.io.input := io.input.abs()
    pfm
  }

  val levelOutputs = pwmOutputsPos.zip(pwmOutputsNeg).zip(pfmOutputs).map { case ((pwmPos, pwmNeg), pfm) =>
    val pwmDiff = pwmPos.io.output.asSInt - pwmNeg.io.output.asSInt
    Mux(pwmDiff < 0, pfm.io.output || pwmPos.io.output, pfm.io.output || pwmNeg.io.output)
  }

  io.output := levelOutputs.reduceLeft(_ || _)
}