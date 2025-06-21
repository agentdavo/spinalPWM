# PWM Audio Amplifier

A SpinalHDL project for a high-performance PWM audio amplifier, converting 96 kHz/24-bit PCM to PWM for GaN FET-driven power stages, targeting 118 dB THD/SNR.

## Structure
- `src/main/scala/pwm/`: Core components, output stages, and utilities.
- `src/test/scala/pwm/`: Testbenches for simulation.
- `src/main/resources/config/`: Configuration files (e.g., coefficients).
- `target/verilog/`: Generated Verilog files.

## Setup
1. Install SBT (version 1.10.0).
2. Clone the repository.
3. Run `sbt compile` to build.
4. Run `sbt test` to execute simulations.
5. Run `sbt run` to generate Verilog (`TopLevel.v`).

## Dependencies
- SpinalHDL dev (core, lib, sim) from `lib/spinalHDL/`
- ScalaTest 3.2.18

## Usage
- Simulate: `sbt "testOnly pwm.test.TopLevelSim"`
- Generate Verilog: `sbt "runMain pwm.TopLevel"`

## Notes
- Configurable coefficients in `src/main/resources/config/coefficients.json`.
- Test signals in `src/test/resources/test_signals/`.
