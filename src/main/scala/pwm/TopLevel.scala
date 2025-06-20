package pwm

import spinal.core._
import spinal.lib._

import pwm.core._

case class TopLevelConfig(
  audioWidth: Int = 24,
  pwmWidth: Int = 20,
  pwmChannels: Int = 4,
  adcWidth: Int = 14
)

class TopLevel(config: TopLevelConfig) extends Component {
  val io = new Bundle {
    val audioIn = slave Stream(Fragment(SInt(config.audioWidth bits)))
    val adcData = in Bits(config.adcWidth bits)
    val adcValid = in Bool
    val mclk = in Bool
    val pwmOut = out Vec(Analog(Bool), config.pwmChannels)
    val volumeLevel = out UInt(10 bits) // Volume monitoring
  }

  val pwmClockDomain = ClockDomain(
    clock = io.mclk,
    frequency = FixedFrequency(50 MHz)
  )

  // ADC Interface
  val adc = new ADCInterface(ADCInterfaceConfig(
    adcWidth = config.adcWidth,
    sampleRate = 50 MHz
  ))
  adc.io.adcData := io.adcData
  adc.io.adcValid := io.adcValid
  adc.io.mclk := io.mclk

  // FIFO for audio input
  val audioToFifo = new Area {
    val fifo = StreamFifo(Fragment(SInt(config.audioWidth bits)), depth = 32)
    fifo.io.push << io.audioIn.toStream
    fifo.io.pop.clockDomain := pwmClockDomain
    fifo.io.pop.halt := False
  }

  // HighResTIPWM with feedback and volume monitoring
  val tipwm = new HighResTIPWM(HighResTIPWMConfig(
    pwmWidth = config.pwmWidth,
    pwmChannels = config.pwmChannels,
    oversampleRate = 512,
    pwmClockFrequency = 50 MHz
  ))
  tipwm.io.audioIn << audioToFifo.fifo.io.pop.toFlow
  tipwm.io.adcSample := adc.io.sampleOut
  io.volumeLevel := tipwm.io.volumeLevel

  io.pwmOut := tipwm.io.pwmOut.map(p => Analog(Bool(p.msb)))
}

object TopLevel {
  def main(args: Array[String]): Unit = {
    SpinalConfig(targetDirectory = "target/verilog").generateVerilog(new TopLevel(TopLevelConfig()))
  }
}
