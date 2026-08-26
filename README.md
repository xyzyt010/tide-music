# 🎵 Tide Music (Android)

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="Tide Music Logo" width="120" height="120" style="border-radius: 24px;" />
</p>

<p align="center">
  <strong>A modern, high-performance, open-source Android music player and downloader built with Jetpack Compose, Media3 ExoPlayer, and Room DB.</strong>
</p>

<p align="center">
  <a href="https://github.com/xyzyt010/tide-music/actions"><img src="https://img.shields.io/badge/Build-Passing-brightgreen?style=flat-square&logo=github-actions" alt="Build Status" /></a>
  <a href="https://developer.android.com/about/versions/oreo"><img src="https://img.shields.io/badge/Android-8.0%2B%20(API%2026%2B)-blue?style=flat-square&logo=android" alt="Android Version" /></a>
  <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/Kotlin-2.1.0-purple?style=flat-square&logo=kotlin" alt="Kotlin" /></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-blueviolet?style=flat-square&logo=jetpackcompose" alt="Jetpack Compose" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-orange?style=flat-square" alt="License" /></a>
</p>

---

## 🌟 Key Features

### 🎧 Audio Engine & Media Session
- **Media3 ExoPlayer Core**: Ultra-low-latency, gapless playback with full background service lifecycle integration.
- **System Notification & Lock Screen Controls**: Rich metadata broadcasting, artwork caching, and responsive media actions.
- **Hardware Equalizer**: Built-in 5-band equalizer with Bass Boost, Virtualizer, preset profiles, and customizable gains.
- **Sleep Timer**: Customizable countdown timer that gently fades out and stops playback.

### 📜 Dual-Mode Synchronized Lyrics Engine
- **Auto-Scroll Mode**: Real-time LRC lyric synchronizer that seamlessly tracks playback timestamps with spring physics.
- **Interactive Gesture Interception**: User scroll drags pause auto-scroll without fighting the user, keeping active lines highlighted, with a floating `"Resume Auto-Scroll"` chip.
- **Manual Mode**: Clean text rendering with persisted scroll position.
- **Embedded & External LRC Support**: Automatically loads embedded lyrics from audio metadata and adjacent `.lrc` files, with an integrated LRC picker and tag editor.

### 📥 YouTube / yt-dlp Audio Downloader
- **Native Audio Extraction**: Seamlessly download high-bitrate audio directly from YouTube URLs into standard MP3/M4A.
- **Subtitle & Lyrics Fetching**: Automatic downloading and conversion of YouTube creator subtitles and auto-generated captions into `.lrc` / `.srt` synced lyrics files.
- **Batch Processing & Quality Selection**: Select audio bitrates, custom folders, and monitor real-time download progress.

### 🎨 Fluid, Gesture-Driven UI / UX
- **Expanding Mini-Player**: Bottom mini-player stretches and slides upward into the full player screen with fluid spring animations.
- **Frosted Glass / Tint Separation**: Frosted artwork backdrops, monochromatic dark styling, and translucent division lines.
- **Multi-Selection & Batch Operations**: Select multiple songs for queuing, playlist insertion, sharing, or batch deletion.
- **3-Dot Context Menus**: Universal options across all sections (Songs, Albums, Folders, Playlists, Downloads, Queue).

### 📁 Smart Library & Playlist Management
- **Automatic Audio Scanning**: High-speed filesystem & MediaStore indexing for FLAC, MP3, WAV, M4A, OGG, and AAC files.
- **100% Functional Built-in Playlists**:
  - ⭐ **Favorites**: Instantly heart tracks from the player or list.
  - 🕒 **Recently Played**: Real-time timestamp tracking on track transition.
  - 🆕 **Recently Added**: Ordered strictly by file addition date.
  - 🔥 **Most Played**: Complete play count tracking with per-song play metrics.
  - 💤 **Not Played**: Discover untouched music in your library.
- **Custom Playlists**: Create, reorder, rename, and manage custom playlists.
- **Folder Navigation**: Tree-based directory browser with fast child folder navigation.

---

## 📱 Screenshots & Visual Flow

```
┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
│   Full Player   │       │ Synchronized    │       │ Smart Playlists │
│  Vinyl / Art    │ <───> │  Lyrics View    │       │ & Audio Engine  │
│  Seek & Volume  │       │ Auto / Manual   │       │ Equalizer / DSP │
└────────┬────────┘       └─────────────────┘       └─────────────────┘
         │
         │ (Smooth Vertical Expand / Collapse)
         ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 🎵 Song Title - Artist           ⏮️  ▶️  ⏭️  🔀  [ Mini Player Bar ] │
├─────────────────────────────────────────────────────────────────────┤
│ ────────── Translucent Highlight Division Line ──────────────────── │
│ 🏠 Player  |  📜 Queue  |  💿 Albums  |  📁 Folders  |  🔍 Search   │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 🛠️ Tech Stack & Architecture

| Component | Technology | Description |
|---|---|---|
| **Language** | Kotlin 2.1.0 | 100% Kotlin codebase |
| **UI Framework** | Jetpack Compose + Material 3 | Declarative, modern UI toolkit |
| **Audio Engine** | AndroidX Media3 ExoPlayer | Robust media playback and notification service |
| **Local Database** | Room DB + KSP | SQLite ORM for songs, playlists, tags, and metrics |
| **Image Loading** | Coil 3 Compose | Fast asynchronous image & embedded artwork loading |
| **Downloader** | `youtubedl-android` (yt-dlp + ffmpeg) | On-device audio extraction and subtitle parsing |
| **Architecture** | Clean Architecture + MVVM + UDF | Unidirectional Data Flow with Kotlin StateFlow/Coroutines |

---

## 🚀 Getting Started & Building from Source

### Prerequisites
- **Android Studio Ladybug (2024.2+)** or newer
- **JDK 17** (Temurin, Zulu, or Android Studio bundled JDK)
- **Android SDK Platform 36** (API 36)
- **NDK** (for yt-dlp native ABI libraries)

### Clone & Build
```bash
# Clone the repository
git clone https://github.com/xyzyt010/tide-music.git
cd tide-music

# Build Debug APK
./gradlew assembleDebug

# Build Release APK
./gradlew assembleRelease
```

The compiled APK will be available in:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## 📂 Project Structure

```
app/src/main/java/com/example/tidemusic/
├── data/
│   ├── db/                 # Room database, DAOs (SongDao, PlaylistDao), Entities
│   └── SettingsManager.kt  # User preferences & persistent settings (Flow)
├── domain/
│   ├── LibraryRepository.kt# Single source of truth for audio files and database
│   └── Models.kt           # Domain models (Song, Album, Playlist, Folder)
├── playback/
│   ├── PlaybackController.kt # Media3 controller interface & queue management
│   ├── PlaybackService.kt    # Android MediaSessionService background runner
│   ├── AudioEffectsManager.kt# Equalizer, Bass Boost & Virtualizer engine
│   └── SleepTimerManager.kt  # Fading sleep timer
├── ui/
│   ├── AppShell.kt         # Navigation graph, bottom bar & animated mini player
│   ├── common/             # Reusable UI components, 3-dot menus, SongRow, MiniPlayer
│   ├── player/             # Fullscreen player, vinyl animator, synchronized lyrics
│   ├── playlists/          # Custom & built-in playlist screens with play stats
│   ├── albums/             # Album grid & detail view
│   ├── folders/            # Hierarchical directory explorer
│   ├── search/             # Instant search filter across songs, artists, albums
│   ├── queue/              # Live play queue with drag-to-reorder
│   └── download/           # YouTube audio & subtitle downloader interface
└── util/
    ├── DurationFormatter.kt# Formatting helpers
    └── SongArtworkRequest.kt # Coil custom fetcher for embedded album art
```

---

## 🤝 Contributing

Contributions are warmly welcomed! Please read our [CONTRIBUTING.md](CONTRIBUTING.md) for details on submitting pull requests, reporting issues, and setting up your development environment.

---

## 📄 License

This project is licensed under the **Apache License 2.0** - see the [LICENSE](LICENSE) file for details.

```
Copyright 2026 Tide Music Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
