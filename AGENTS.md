# AGENTS.md

## Project Overview

The `pwm-audio-amplifier` is a SpinalHDL-based project that implements a high-performance audio amplifier, converting 96 kHz/24-bit PCM audio to a PWM signal for driving GaN FET power stages. The design targets 110 dB Total Harmonic Distortion (THD) and Signal-to-Noise Ratio (SNR), leveraging oversampling, noise shaping, interpolation, and real-time feedback via an ADC to monitor the GaN FET gate voltage. Key components include `HighResTIPWM`, `FeedbackProcessor`, `MultilevelOutput`, and `TopLevel`, integrated with a 50 MHz master clock (MCLK) for precise timing. The project uses Scala Build Tool (SBT) for build management, SpinalHDL for hardware description, and SpinalSim for simulation and verification.

This document outlines strict guidelines for AI programmers contributing to the project, ensuring consistent code quality, adherence to SpinalHDL best practices, and robust simulation workflows.

## Development Environment

### SBT Setup
- **Version**: Use SBT 1.12.2, as specified in `project/build.properties`.
- **Dependencies**: Defined in `build.sbt`, including:
  - `com.github.spinalhdl %% spinalhdl-core % 1.12.2`
  - `com.github.spinalhdl %% spinalhdl-lib % 1.12.2`
  - `com.github.spinalhdl %% spinalhdl-sim % 1.12.2`
  - `org.scalatest %% scalatest % 3.2.18 % Test`
- **Commands**:
  - Compile: `sbt compile`
  - Run tests: `sbt test`
  - Generate Verilog: `sbt "runMain pwm.TopLevel"`
  - Run specific test: `sbt "testOnly pwm.test.<TestName>"`
- **Directory Structure**:
  - `src/main/scala/audio/pwm/`: Main components (e.g., `TopLevel.scala`).
  - `src/main/scala/audio/pwm/core/`: Core components (e.g., `HighResTIPWM.scala`).
  - `src/main/scala/audio/pwm/output/`: Output stages (e.g., `MultilevelOutput.scala`).
  - `src/main/scala/audio/pwm/util/`: Utilities (e.g., `StreamResampler.scala`).
  - `src/test/scala/audio/pwm/test/`: Testbenches (e.g., `TopLevelSim.scala`).
  - `src/test/scala/audio/pwm/util/`: Test utilities (e.g., `TestUtils.scala`).
  - `target/verilog/`: Generated Verilog files.
  - `src/main/resources/config/`: Configuration files (e.g., `coefficients.json`).

### SpinalHDL Requirements
- **Version**: Use SpinalHDL 1.10.2 exclusively.
- **Language**: Write all hardware descriptions in Scala using SpinalHDL constructs.
- **Components**:
  - Define components as `case class` for configurations (e.g., `HighResTIPWMConfig`) and `class` for implementation (e.g., `HighResTIPWM extends Component`).
  - Use `Bundle` for I/O definitions with clear naming (e.g., `io.audioIn`, `io.pwmOut`).
- **Clock Domains**: Explicitly define clock domains, especially for the 50 MHz MCLK (`pwmClockDomain`) and 96 kHz audio clock (`audioClockDomain`).
- **Simulation**: Use SpinalSim for verification, with waveform generation enabled (`SimConfig.withWave`).

## Coding Standards

### SpinalHDL Strict Coding Guidelines
1. **Naming Conventions**:
   - Components: Use `CamelCase` (e.g., `HighResTIPWM`, `FeedbackProcessor`).
   - Signals: Use `camelCase` with descriptive names (e.g., `io.audioIn`, `pwmCounter`).
   - Configurations: Append `Config` to configuration classes (e.g., `TopLevelConfig`).
   - Packages: Use `com.example.audio.pwm` for main components, `com.example.audio.pwm.core` for core logic, `com.example.audio.pwm.output` for output stages, and `com.example.audio.pwm.util` for utilities.
2. **Type Safety**:
   - Use SpinalHDL types (`SInt`, `UInt`, `SFix`, `Bool`) with explicit bit widths (e.g., `SInt(24 bits)`).
   - Avoid implicit conversions; explicitly cast signals (e.g., `signal.asSInt`, `resize(24 bits)`).
3. **Modularity**:
   - Break complex logic into `Area` blocks with descriptive names (e.g., `interpolator`, `noiseShaper`).
   - Reuse components (e.g., `Interpolator` from `core`) across the design.
4. **Clock and Reset**:
   - Always define clock domains explicitly (e.g., `ClockDomain(clock = io.mclk, frequency = FixedFrequency(50 MHz))`).
   - Use synchronous resets unless asynchronous is required, and document the choice.
5. **Documentation**:
   - Add Scaladoc comments for all components, configurations, and major `Area` blocks.
   - Example:
     ```scala
     /**
      * Generates high-resolution PWM with feedback and noise shaping.
      * @param config Configuration for PWM width, channels, and oversampling.
      */
     class HighResTIPWM(config: HighResTIPWMConfig) extends Component { ... }
     ```
   - Include inline comments for complex logic (e.g., filter coefficients, feedback loops).
6. **Error Handling**:
   - Validate input signals (e.g., `when(io.audioIn.valid) { ... }`).
   - Initialize registers with sensible defaults (e.g., `Reg(UInt()) init(0)`).
7. **Performance**:
   - Optimize DSP usage by minimizing multiplications and using fixed-point arithmetic (`SFix`).
   - Pipeline critical paths (e.g., interpolation, feedback processing) to meet 50 MHz timing.

### File Structure
- **Source Files**:
  - Place new components in the appropriate package (e.g., `core`, `output`, `util`).
  - Example: A new filter component goes in `src/main/scala/audio/pwm/core/NewFilter.scala`.
- **Testbenches**:
  - Place testbenches in `src/test/scala/audio/pwm/test/`.
  - Name testbenches with `Sim` suffix (e.g., `NewFilterSim.scala`).
- **Configuration**:
  - Store coefficients and parameters in `src/main/resources/config/coefficients.json`.
  - Example:
    ```json
    {
      "noise_shaper": [-0.6234, -0.1542, -0.0345, -0.0123],
      "volume_gain": { "high_volume_threshold": 0.8, "high_volume_gain": 0.9 }
    }
    ```

### Simulation with SpinalSim
1. **Setup**:
   - Use `SimConfig.withWave.workspacePath("target/sim")` for all testbenches.
   - Enable waveform generation for debugging.
2. **Testbench Structure**:
   - Define a main simulation function (e.g., `def apply(): Unit`) and a `main` method.
   - Example:
     ```scala
     object MyComponentSim {
       def apply(): Unit = {
         SimConfig.withWave.doSim(new MyComponent) { dut => ... }
       }
       def main(args: Array[String]): Unit = { MyComponentSim() }
     }
     ```
3. **Input Signals**:
   - Generate multi-tone signals using `TestUtils.generateMultiToneSignal`.
   - Example: 1 kHz, 5 kHz, 10 kHz tones at 96 kHz sample rate.
4. **Verification**:
   - Calculate THD and SNR using `TestUtils.calculateTHD` and `TestUtils.calculateSNR`.
   - Verify DC offset and volume levels (where applicable) with explicit checks.
   - Add assertions (e.g., `assert(thd < -110.0, "THD too high")`) for automated validation.
5. **ADC Feedback**:
   - Simulate ADC feedback with noise (e.g., `Random.nextGaussian() * 0.01`).
   - Example:
     ```scala
     val sample = (if (pwmOut) 1.0 else -1.0) * (1 << 13) + Random.nextGaussian() * 0.01
     dut.io.adcData #= sample.toInt
     ```
6. **Clock Domains**:
   - Simulate MCLK at 50 MHz (`forkStimulus(20)`) and audio clock at 96 kHz (`forkStimulus(10416)`).
   - Use separate threads for clock domains (e.g., `mclkThread`, `audioClockThread`).

## Contribution Guidelines

1. **Code Reviews**:
   - All code must adhere to the above standards.
   - Submit changes via pull requests with detailed descriptions.
2. **Testing**:
   - Write a testbench for every new component, verifying THD, SNR, and other metrics.
   - Ensure all existing tests pass (`sbt test`) before submitting changes.
3. **Verilog Generation**:
   - Test Verilog output for `TopLevel` using `sbt "runMain com.example.audio.pwm.TopLevel"`.
   - Verify synthesis compatibility with FPGA tools (e.g., Xilinx Vivado).
4. **Performance Targets**:
   - Aim for THD <-110 dB and SNR >115 dB in simulations.
   - Optimize components to meet 50 MHz timing constraints.
5. **Documentation Updates**:
   - Update `README.md` with new components or features.
   - Maintain `AGENTS.md` for any changes to coding standards.

## Specific Tasks for AI Programmers

1. **Component Development**:
   - Implement new components in `core` or `output` packages, following the modular structure.
   - Example: Add a new predistortion filter in `core/PredistortionFilter.scala`.
2. **Testbench Creation**:
   - Write testbenches in `test` package, using `TestUtils` for signal generation and metrics.
   - Example: `test/NewFilterSim.scala` for a new filter component.
3. **Optimization**:
   - Profile DSP usage and optimize critical paths (e.g., noise shaper, interpolator).
   - Tune coefficients in `coefficients.json` based on simulation results.
4. **Feedback Enhancements**:
   - Improve `FeedbackProcessor` with adaptive algorithms for GaN FET non-linearities.
   - Example: Add a lookup table for volume-dependent compensation.
5. **Simulation Robustness**:
   - Add edge cases to testbenches (e.g., low-volume signals, transient inputs).
   - Implement automated regression tests for all components.

## Resources
- **SpinalHDL Documentation**: [https://spinalhdl.github.io/SpinalDoc-RTD/](https://spinalhdl.github.io/SpinalDoc-RTD/)
- **SBT Documentation**: [https://www.scala-sbt.org/1.x/docs/](https://www.scala-sbt.org/1.x/docs/)
- **Project Repository**: Assumed to be local; update with actual repository URL if hosted.
- **Configuration**: Refer to `src/main/resources/config/coefficients.json` for tunable parameters.

## Contact
For questions or clarifications, consult the project owner or refer to the `README.md` for additional context.
