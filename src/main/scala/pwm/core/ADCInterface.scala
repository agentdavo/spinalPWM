package pwm.core

import spinal.core._
import spinal.lib._

case class ADCInterfaceConfig(
  adcWidth: Int = 14,
  sampleRate: HertzNumber = 50 MHz
)

class ADCInterface(config: ADCInterfaceConfig) extends Component {
  val io = new Bundle {
    val adcData = in Bits(config.adcWidth bits)
    val adcValid = in Bool
    val mclk = in Bool
    val sampleOut = out SInt(config.adcWidth bits)
  }

  val mclkDomain = ClockDomain(
    clock = io.mclk,
    reset = ClockDomain.current.reset,
    frequency = config.sampleRate
  )

  val adcArea = mclkDomain on new Area {
    val sampleReg = Reg(SInt(config.adcWidth bits)) init(0)
    when(io.adcValid) {
      sampleReg := io.adcData.asSInt
    }
    io.sampleOut := sampleReg
  }
}