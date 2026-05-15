# 🌳 Oak

<img src="https://img.shields.io/badge/License-GPLv3-blue.svg" alt="GPLv3">
<img src="https://img.shields.io/badge/Platform-Android-34a853.svg?logo=android" alt="Android">

<p align="center">
  <img src="oak-animated.svg" width="120" alt="Oak logo">
</p>

**Oak is an open-source AI assistant with persistent memory.** Android-first, BYOK (bring your own API key). Work in progress 🌱

## Status

Oak is early. Core features work on Android — chat, memory, tool execution, heartbeat. Desktop builds are planned but not available yet.

## Quick Start

1. Download the latest APK from [Releases](https://github.com/adrielGGmotion/Oak/releases)
2. Open Oak and go to **Settings → Services** to add your API key
3. Start a conversation — Oak remembers what matters across chats

## Features

- **🧠 Persistent memory** — Stores facts, preferences, and learnings. Useful memories auto-promote into the system prompt
- **🔁 Multi-service fallback** — 20+ LLM providers with automatic failover
- **🛠️ Tool execution** — Web search, notifications, calendar, shell commands, MCP servers
- **📱 On-device inference** — LiteRT on Android, no internet needed
- **⏰ Autonomous heartbeat** — Periodic background checks that surface what needs attention
- **🎨 Interactive UI** — The AI generates live screens, forms, dashboards inline

## Build

```bash
git clone https://github.com/adrielGGmotion/Oak
cd Oak
./gradlew assembleDebug
```

Requires Android SDK. Open in Android Studio for the easiest setup.

## License

Oak is GPLv3. It started as a fork of [Kai](https://github.com/kai-ai-org/Kai) (Apache 2.0) — see [NOTICE.md](NOTICE.md) for attribution.
