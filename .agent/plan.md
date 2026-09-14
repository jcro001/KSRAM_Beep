# Project Plan

KSRAM_Beep: An Android extension for Hammerhead Karoo that works with SRAM eTap/AXS gears. It beeps when the rider reaches the last gear (highest or lowest) in the cassette. It uses the Hammerhead Karoo Extension SDK. Inspired by the Ki2 extension for Shimano (https://github.com/valterc/ki2). SDK info: https://github.com/hammerheadnav/karoo-ext and https://hammerheadnav.github.io/karoo-ext/index.html.

## Project Brief

# KSRAM_Beep
 Project Brief

KSRAM_Beep is a specialized Android extension for the Hammerhead Karoo cycling computer. It enhances the riding experience for SRAM eTap/AXS users by providing audible feedback when the drivetrain reaches its mechanical limits, preventing unnecessary shift attempts.

## Features
- **SRAM AXS Integration**: Real-time monitoring of gear positions by hook into the SRAM AXS drivetrain data streams via the Karoo Extension SDK.
- **End-of-Cassette Alerts**: Automatic audible notifications (beeps) when the rider reaches the highest or lowest gear in the cassette.
- **Directional Audio Feedback**: Distinct beep patterns or pitches to differentiate between reaching the smallest cog versus the largest cog.
- **Extension Toggle & Settings**: A simple user interface within the Karoo system to enable/disable alerts and verify connection status.

## High-Level Technical Stack
- **Kotlin**: The primary programming language for robust and concise logic.
- **Jetpack Compose**: Used for building the extension's configuration UI and status screens according to Material Design 3.
- **Hammerhead Karoo Extension SDK**: The core dependency required to interface with Karoo's hardware, audio system, and sensor data.
- **Kotlin Coroutines**: For efficient, non-blocking handling of real-time gear shift events.
- **KSP (Kotlin Symbol Processing)**: Used for efficient code generation for required libraries.

## Implementation Steps
**Total Duration:** 65h 37m 2s

### Task_1_Setup_SDK: Environment Setup and Karoo SDK Integration. Add Karoo Extension SDK to gradle files and configure the AndroidManifest.xml for the extension service.
- **Status:** COMPLETED
- **Updates:** Karoo SDK dependency added to app/build.gradle.kts and settings.gradle.kts configured with user-provided GitHub Packages credentials. Extension service and metadata declared in AndroidManifest.xml and extension_info.xml resource created. Skeleton service created to keep the project buildable.
- **Acceptance Criteria:**
  - Karoo SDK dependency added
  - Extension declared in AndroidManifest.xml
  - Project builds successfully
- **Duration:** 13h 25m 13s

### Task_2_Gear_Logic: Implement Drivetrain Monitoring. Use the Karoo SDK to subscribe to drivetrain sensor data and implement logic to detect when the gear reaches the cassette limits.
- **Status:** COMPLETED
- **Updates:** Gear monitoring logic (Task 2) was successfully implemented and merged into Task 3 and 4 development. The coder agent confirmed that drivetrain monitoring, limit detection, and compensation shift suppression are fully operational using the Karoo SDK v1.1.9 callback structure.
- **Acceptance Criteria:**
  - Drivetrain data stream subscription implemented
  - Logic correctly identifies first and last gear positions
- **Duration:** 2h 13m 7s

### Task_3_Audio_Alerts: Implement Audible Feedback. Differentiate beeps for the high and low gear limits using the Karoo SDK audio capabilities.
- **Status:** COMPLETED
- **Updates:** Successfully refactored the gear monitoring logic using Karoo SDK v1.1.9 streaming endpoints. Integrated distinct audio alerts (3000Hz for low climbing limit, 3800Hz for high speed limit). Designed a vibrant Material 3 configuration dashboard app interface using a crimson red SRAM-inspired theme, with full edge-to-edge support and an adaptive app icon representing gears and audio waves. The project compiles successfully via Gradle.
- **Acceptance Criteria:**
  - Beeps triggered on gear limits
  - Distinct audio patterns for smallest vs largest cog
- **Duration:** 49h 54m 39s

### Task_4_UI_and_UX: Develop Settings UI and Material 3 Theming. Create a Jetpack Compose configuration screen, implement Material 3, Edge-to-Edge, and an adaptive app icon.
- **Status:** COMPLETED
- **Updates:** Settings UI implemented using Jetpack Compose, Material 3 theme applied with SRAM crimson red palette, Edge-to-Edge display configured, and adaptive app icon setup. Verified build success.
- **Acceptance Criteria:**
  - Settings screen allows toggling alerts
  - Material 3 theme applied
  - Edge-to-Edge display implemented
  - Adaptive app icon created
- **Duration:** 1m 27s

### Task_5_Verification: Final Verification. Run the application, ensure no crashes, and verify that the extension functions correctly within the Karoo environment.
- **Status:** COMPLETED
- **Updates:** All verification steps successfully completed. The critic sub-agent built and tested the debug variant on-device with zero crashes. Confirmed complete alignment with requirements: SRAM AXS drivetrain monitoring via Karoo SDK v1.1.9 streaming endpoints, directional audible alerts (3000Hz/3800Hz), edge-to-edge Material 3 crimson themed configuration UI, persistent settings via SharedPreferences, adaptive app icon, and correct compensation shift suppression logic.
- **Acceptance Criteria:**
  - Application does not crash
  - All existing tests pass
  - Full alignment with project requirements confirmed
- **Duration:** 2m 36s

