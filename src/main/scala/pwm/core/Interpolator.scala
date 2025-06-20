package pwm.core

import spinal.core._
import spinal.lib._

object LagIntOpt {
  val coeffs = Seq[SFix](
    SFix(0.07571827673995030, 4 exp), SFix(-0.87079480370960549, 4 exp),
    SFix(0.18688371835645290, 4 exp), SFix(1.09174419992174300, 4 exp),
    SFix(0.03401038103941584, 4 exp), SFix(0.39809419102537769, 4 exp),
    SFix(0.41706012247048818, 4 exp), SFix(-0.40535151498252686, 4 exp),
    SFix(-0.62917625718809478, 4 exp), SFix(-0.05090907029392906, 4 exp),
    SFix(0.02618753167558019, 4 exp), SFix(0.12392296259397995, 4 exp),
    SFix(0.21846781431808182, 4 exp), SFix(0.15915674384870970, 4 exp),
    SFix(0.01689861603514873, 4 exp)
  )
}

class Interpolator extends Component {
  val io = new Bundle {
    val x = in SFix(4 exp, 32 bits)
    val y = in Vec(SFix(4 exp, 32 bits), 6)
    val result = out SFix(4 exp, 32 bits)
  }

  val pipeline = Pipeline(
    List(
      new Stage {
        val x = input(io.x)
        val y = input(io.y)
        val e, o = Vec(SFix(4 exp, 32 bits), 5)
        for (i <- 0 until 5) {
          e(i) := y(i + 1) + y(i)
          o(i) := y(i + 1) - y(i)
        }
        output(e ++ o)
        output(x)
      },
      new Stage {
        val eo = input(Vec(SFix(4 exp, 32 bits), 10))
        val c = Vec(SFix(4 exp, 32 bits), 6)
        for (i <- 0 until 6) {
          c(i) := eo(i * 2) * LagIntOpt.coeffs(i * 3) +
            eo(i * 2 + 1) * LagIntOpt.coeffs(i * 3 + 1) +
            eo(i * 2 + 2) * LagIntOpt.coeffs(i * 3 + 2)
        }
        output(c)
      },
      new Stage {
        val c = input(Vec(SFix(4 exp, 32 bits), 6))
        val result = c.reduce(_ + _)
        output(result)
      }
    )
  )

  pipeline.input(0) := io.x
  pipeline.input(1) := io.y
  io.result := pipeline.output(0)
}