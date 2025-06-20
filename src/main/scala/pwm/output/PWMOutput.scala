package pwm.output

import spinal.core._
import spinal.lib._

case class PWMOutput(resolution: BitCount, polarity: Bool, deadtime: UInt) extends Component {
  val io = new Bundle {
    val dutyCycle = in UInt(resolution)
    val output = out Bool
  }

  val counter = Counter(resolution)
  val threshold = Reg(UInt(resolution)) init(0)

  when(polarity) {
    io.output := counter < threshold
  } otherwise {
    io.output := counter >= threshold
  }

  counter.increment()

  when(counter.willOverflow) {
    counter.clearAll()
    counter.increment().clearAll()
    counter.increment()
  }

  threshold := io.dutyCycle + deadtime
}