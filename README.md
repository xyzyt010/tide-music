# 🎵 Tide Music

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="Tide Music Logo" width="110" height="110" style="border-radius: 24px;" />
</p>

<p align="center">
  <strong>A modern, lightweight, high-fidelity Android music player crafted with Jetpack Compose, Media3 ExoPlayer, and Room DB.</strong>
</p>

<p align="center">
  <a href="https://github.com/xyzyt010/tide-music/releases/latest"><img src="https://img.shields.io/badge/Release-Latest%20APK-blue?style=flat-square&logo=android" alt="Download APK" /></a>
  <a href="https://developer.android.com/about/versions/oreo"><img src="https://img.shields.io/badge/Android-8.0%2B%20(API%2026%2B)-brightgreen?style=flat-square&logo=android" alt="Android Version" /></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/UI-Material%203%20Compose-purple?style=flat-square&logo=jetpackcompose" alt="Jetpack Compose" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-orange?style=flat-square" alt="License: MIT" /></a>
</p>

---

## ✨ Overview

**Tide Music** is an elegant, open-source music player engineered for seamless performance and studio-quality sound. Built entirely in modern Kotlin with Jetpack Compose and AndroidX Media3, it combines minimalist Material 3 design with powerful DSP audio enhancements, intelligent non-repeating shuffle, and an integrated offline media downloader.

---

## 🚀 Key Features

### 🎧 High-Fidelity Audio & Smart Volume
* **Media3 ExoPlayer Core**: Zero-lag gapless playback, comprehensive format decoding (FLAC, MP3, WAV, M4A, OGG, AAC), and low battery consumption.
* **🔊 Smart Volume Extra**: Hardware-accelerated DSP loudness enhancer (+3.0 dB clean makeup gain) delivering richer, louder output across phone speakers, Bluetooth earbuds, and car audio without digital clipping.
* **🎛️ Graphic Equalizer**: 10-band equalizer with Bass Boost, 3D Virtualizer, preamp gain controls, and custom device profiles.
* **Notification & Lock Screen Controls**: System media controls with real-time seek bar, album art rendering, and dynamic shuffle toggles.

### 🎲 Hardware-Entropy Smart Shuffle
* **Dynamic Hardware Entropy**: Utilizes real-time device hardware signals (thermal sensors, battery voltage fluctuations, and nanosecond monotonic clocks) mixed with cryptographic SHA-256 to ensure every shuffle sequence is truly unpredictable.
* **Anti-Repeat History Protection**: Remembers recently played tracks and partitions them away from upcoming cycles, completely eliminating repetitive playback.
* **Arbitrary Random Start**: Tapping "Shuffle All" begins on an unpredictable track from your library rather than defaulting to track 1.

### 📜 Synced Dual-Mode Lyrics
* **Fluid Auto-Scroll**: Time-synced LRC lyric engine that smoothly follows audio playback with spring physics.
* **Interactive Gesture Interception**: Scroll manually without disrupting tracking, featuring an instant "Resume" chip.
* **Embedded & Local LRC**: Reads embedded metadata lyrics and auto-detects adjacent `.lrc` files.

### 📥 On-Device Media Downloader
* **High-Bitrate Extraction**: Download offline audio directly into standard MP3/M4A formats.
* **Automatic Lyrics Sync**: Automatically extracts captions and converts them into time-synced `.lrc` lyric files.

### 🎨 Clean Material 3 Interface
* **Spring-Animated Mini Player**: Floating player stretches and expands smoothly into the full player screen.
* **Dynamic Backdrop Glass**: Frosted artwork backgrounds that adapt colors to match the currently playing song.
* **Smart Library**: Fast local storage indexing, tree folder navigation, multi-selection batch actions, and dynamic smart playlists (*Favorites*, *Most Played*, *Recently Added*, *Not Played*).
* **Sleep Timer**: Gentle fade-out countdown timer for evening listening.

---

## 📲 Download & Installation

1. Head to the **[Latest Release](https://github.com/xyzyt010/tide-music/releases/latest)** page.
2. Download the latest `.apk` file onto your Android device.
3. Open the downloaded file and tap **Install** *(if prompted, enable "Install unknown apps" for your browser or file manager)*.
4. Launch **Tide Music**, grant audio storage permissions when requested, and enjoy your music!

---

## 🛠️ Tech Stack

* **UI**: Jetpack Compose & Material Design 3
* **Audio Engine**: AndroidX Media3 (ExoPlayer, MediaSession, AudioFX LoudnessEnhancer)
* **Local Storage**: Room Database & DataStore Preferences
* **Image Loading**: Coil 3 Compose
* **Downloader**: `youtubedl-android` (yt-dlp & FFmpeg)
* **Architecture**: Clean Architecture with MVVM and Unidirectional Data Flow

---

## 📄 License

This project is licensed under the **[MIT License](LICENSE)** — a permissive open-source license allowing free personal and commercial use, modification, and distribution.

```
Copyright (c) 2026 Tide Music Contributors
```
