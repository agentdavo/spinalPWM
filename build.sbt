name := "pwm"

version := "0.1"

scalaVersion := "2.12.19"

Compile / unmanagedClasspath ++= (baseDirectory.value / "lib" / "spinalHDL" * "*.jar").classpath
Test / unmanagedClasspath ++= (baseDirectory.value / "lib" / "spinalHDL" * "*.jar").classpath

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.2.18" % Test
)

fork := true
