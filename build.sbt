name := "pwm"

version := "0.1"

scalaVersion := "2.13.12"

libraryDependencies ++= Seq(
  "com.github.spinalhdl" %% "spinalhdl-core" % "1.12.2",
  "com.github.spinalhdl" %% "spinalhdl-lib" % "1.12.2",
  "com.github.spinalhdl" %% "spinalhdl-sim" % "1.12.2",
  "org.scalatest" %% "scalatest" % "3.2.18" % Test
)

fork := true