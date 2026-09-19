# KogSense

**KogSense** is a specialized Android extension for the Hammerhead Karoo cycling computer. It provides audible feedback when your electronic drivetrain reaches its mechanical limits (the first or last cog), allowing you to focus on the road instead of your gear display.

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/jcro001)

## Features

- **End-of-Cassette Alerts**: Automatic beeps when you reach the highest or lowest gear.
- **SRAM & Shimano Support**: Specialized logic for both brands, including:
  - **SRAM Road 2x**: Correctly handles soft-lockouts (e.g., beeping at gear 11 when in the small ring).
  - **Shimano Di2**: Full range support and detection of manual-mode blocks.
- **Proactive & Reactive Alerts**: 
  - **Proactive**: Beeps the moment you shift *into* the limit gear.
  - **Reactive**: Reminder beep if you attempt to shift again while already at the limit.
- **Context-Aware Muting**: Automatically suppresses beeps during front shifts (compensation shifts) to prevent audio clutter.
- **Thread-Safe Logic**: Built for reliability on the Karoo platform with multi-threaded event processing.

## Installation

1. Download the latest APK from the [Releases](https://github.com/jcro001/KogSense/releases) page.
2. Sideload the APK onto your Hammerhead Karoo.
3. Open the **KogSense** app on your Karoo to configure your preferences.
4. Ensure the extension is enabled in your Karoo settings.

## Configuration

Within the KogSense app, you can adjust:
- **Alert Toggles**: Enable or disable Low Gear and High Gear alerts independently.
- **Cassette Size**: Manually set your cassette size (10S-13S) or let the app detect it automatically.
- **Min Retry Delay**: Adjust how long to wait before repeating a "reminder" beep if you keep clicking the shifter.
- **Drivetrain Mode**: Force SRAM/Shimano modes or use "Auto" detection.

## Development

This project uses the [Hammerhead Karoo Extension SDK](https://github.com/hammerheadnav/karoo-ext).

### Prerequisites
- Android Studio
- Hammerhead Karoo SDK v1.1.9+

## Support

If you find this extension useful, consider supporting the development:

[![Support me on Ko-fi](https://storage.ko-fi.com/cdn/cup-border.png)](https://ko-fi.com/jcro001)

## License

This project is licensed under the Apache License 2.0.
