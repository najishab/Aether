# NajiAether

> A dedicated, secure, and high-speed network management tool for Android, powered by the Aether engine.

🇬🇧 English | 🇮🇷 [فارسی](README.fa.md)

---

## What's new in v1.1.1

We are excited to announce the v1.1.1 update for NajiAether! This release introduces powerful new monitoring features, home screen widgets, stability fixes, and significant UI/UX enhancements.

### ✨ Features & Highlights

- **New Advanced Home Screen Widget (4x2)** — feature-packed widget displaying live download/upload speeds, toggle connection button, current IP with country flag & name, and an active connection timer.
- **Endpoint History & Quick Reconnect** — automatically saves endpoint parameters after each connection, displaying ping metrics and network type (Cellular/Wi-Fi). Users can now reconnect instantly with a single tap.
- **Redesigned Usage Calendar** — complete overhaul of the usage calendar tracking live data consumption with dual Jalali (Persian) and Gregorian support, filtered by daily, weekly, and monthly views as well as download, upload, and session breakdowns.
- **Background Keep-Alive & Battery Prompt** — added a smart prompt during the initial connection to guide users toward disabling battery optimization, preventing system kills when the screen locks.
- **Redesigned Connection Info Card** — modernized and restructured the connection status card on the home screen for a cleaner layout.
- **Bug Fixes & Core Stability** — resolved the crash issue when interacting with the 3x1 home screen widget and fixed missing serialization fields in `ProfileCodec` (`AetherController.kt`), ensuring options pass properly to `VpnService`.

## v1.1.0

This release introduced new data monitoring tools, theme support, and overall user experience improvements.

### ✨ Features & Highlights

- **Bandwidth Usage Graph** — a real-time graph component for monitoring live download and upload speeds.
- **Usage Calendar** — a visual daily usage calendar allowing users to track their internet data consumption per day over a monthly view.
- **Theme Support** — expanded theme options with new Light Mode and System Default theme matching (alongside Dark Mode).
- **In-App Changelog** — an embedded release notes / changelog section so users can easily review version updates and feature additions directly in the app.
- **Bug Fixes** — resolved an issue that prevented scrolling in the Debug and Diagnostics section.

## v1.0.0 — Initial Public Release

NajiAether is a dedicated, secure, and high-speed network management tool. This was the first official release of the application.

### ✨ Features & Highlights

- **Powerful Core** — powered by the Aether core (v1.7.0) for maximum stability and speed.
- **Optimized UI** — simple, lightweight, and user-friendly design for easy configuration and use.
- **Initial Release** — provides all core and foundational features under version 1.0.0.

## Requirements

- Android 8.0 (API 26) or newer.
- The app asks for VPN permission (standard Android consent) and notification permission (to show the ongoing status).

## Installation

Download the latest APK from the [Releases](https://github.com/najishab/aether/releases) page and install it on your Android device.

## Credits & license

- Powered by the **Aether** engine (v1.7.0).

Released under **AGPL-3.0**. See [LICENSE](LICENSE).