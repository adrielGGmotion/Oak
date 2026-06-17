<p align="center">
  <img src="oak-animated.svg" width="120" alt="Oak logo">
</p>

<h1 align="center">Oak</h1>

<p align="center">
  <em>Open-source AI assistant with persistent memory for Android and Desktop.</em>
</p>

<p align="center">
  <a href="https://github.com/adrielGGmotion/Oak/blob/main/LICENSE.txt"><img src="https://img.shields.io/badge/License-GPLv3-blue.svg" alt="GPLv3"></a>
  <a href="#"><img src="https://img.shields.io/badge/Platform-Android%20%7C%20Desktop-34a853.svg" alt="Platform"></a>
</p>

---

## Features

<table>
  <tr>
    <td><strong>Persistent Memory</strong></td>
    <td>Stores facts and preferences, auto-promotes useful memories into context</td>
  </tr>
  <tr>
    <td><strong>Multi-Provider</strong></td>
    <td>25+ LLM providers with automatic failover</td>
  </tr>
  <tr>
    <td><strong>Tool Execution</strong></td>
    <td>Sandboxed shell commands, file I/O, SSH, and more</td>
  </tr>
  <tr>
    <td><strong>On-Device Inference</strong></td>
    <td>Runs locally via LiteRT — no internet required</td>
  </tr>
  <tr>
    <td><strong>Heartbeat</strong></td>
    <td>Autonomous background scheduling and monitoring</td>
  </tr>
</table>

## Build

```bash
git clone https://github.com/adrielGGmotion/Oak
cd Oak
./gradlew :androidApp:assembleDebug
```

<p align="center">
  <em>Requires Android SDK. Open in Android Studio for the easiest setup.</em>
</p>

## License

Oak is GPLv3. It started as a fork of [Kai](https://github.com/kai-ai-org/Kai) (Apache 2.0) — see [NOTICE.md](NOTICE.md) for attribution.
