# KogSense

**KogSense** is an extension for the Hammerhead Karoo that beeps when your electronic drivetrain hits its mechanical limits. It automatically supports both **SRAM AXS** and **Shimano Di2** (Note: Di2 support requires the [Ki2 extension](https://github.com/valterc/ki2) to be installed).

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/jcro001)

## Features

- **End-of-Range Alerts**: Automatic beeps when you reach the first or last available gear in your cassette.
- **Cross-Chain Blocked Gear Support**: Correctly handles systems that block certain gear combinations to prevent chain rasp. For example, on SRAM 2x systems where the small-small combination is permanently locked out, KogSense identifies the actual reachable gear as the limit and beeps accordingly.
- **Proactive Alerts**: Beeps the moment you shift into your limit gear, so you know instantly when you're out of shifts.
- **Reminder Alerts**: Provides a secondary beep if you attempt to shift again while already at the limit.
- **Tested & Verified**: Fully tested and verified on **SRAM AXS 2x12** and **Shimano Di2 2x12** drivetrains.
- **Smart Detection**: Automatically detects your drivetrain brand and configuration to adjust beeping logic without manual setup.

## Installation

1. Download the latest release from the [Releases](https://github.com/jcro001/KogSense/releases) page.
2. Sideload the file onto your Hammerhead Karoo.
3. Open the **KogSense** app on your Karoo to configure your alerts.
4. Ensure the extension is enabled in your Karoo settings.

## Configuration

Within the KogSense app, you can adjust:
- **Alert Toggles**: Enable or disable Low Gear and High Gear alerts independently.
- **Cassette Size**: Manually set your cassette size (10S-13S) or let the app detect it automatically.
- **Min Retry Delay**: Adjust the delay between "reminder" beeps if you continue clicking the shifter at a limit.
- **Drivetrain Mode**: Force SRAM/Shimano modes or use the "Auto" detection.

## Support

If you find this extension useful, consider supporting the development:

[![Support me on Ko-fi](https://storage.ko-fi.com/cdn/cup-border.png)](https://ko-fi.com/jcro001)

## License

This project is licensed under the Apache License 2.0.
