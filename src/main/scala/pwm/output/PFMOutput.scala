package pwm.output

import spinal.core._
import spinal.lib._

case class PFMOutput(inputWidth: BitCount, thresholdWidth: BitCount, step: UInt) extends Component {
  val io = new Bundle {
    val input = in UInt(inputWidth)
    val output = out Bool
  }

  val comparator = Reg(UInt(thresholdWidth)) init(0)
  io.output := io.input > comparator
  comparator := comparator + step
}