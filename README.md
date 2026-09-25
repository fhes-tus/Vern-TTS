<div align="center">

<img src="docs/icon.png" width="96" height="96" alt="Veritas Reader Icon" />

# Veritas Reader

### *Your intelligent, private reading companion, neural audiobook studio, and active-recall study suite for Android.*

[![Latest Release](https://img.shields.io/badge/Release-v2.5.1-orange.svg)](https://github.com/fhes-tus/Vern-TTS/releases)
[![Target Platform](https://img.shields.io/badge/Platform-Android%2016%20(API%2036)-3DDC84.svg?logo=android&logoColor=white)](https://developer.android.com/)
[![Minimum SDK](https://img.shields.io/badge/Min%20SDK-Android%209.0%20(API%2028)-blue.svg)](https://developer.android.com/)
[![Language](https://img.shields.io/badge/Kotlin-2.4.0-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![UI Toolkit](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Privacy](https://img.shields.io/badge/Privacy-100%25%20On--Device-success.svg)](#privacy--offline-first)

<br/>

<p align="center">
  <img width="30%" alt="Home Dashboard" src="docs/screenshots/home.png" />
  <img width="30%" alt="Central Library" src="docs/screenshots/library.png" />
  <img width="30%" alt="Reading Mode" src="docs/screenshots/current_reader.png" />
</p>

</div>

---

## 🌟 Overview

**Veritas Reader** transforms any document into an immersive, multi-sensory reading experience. Whether you are studying dense academic papers, reading digital books on your commute, or listening to long-form articles hands-free, Veritas Reader combines state-of-the-art on-device neural voice synthesis, active-recall study tools, dyslexia-friendly typography, and reading analytics into a single, cohesive experience.

Designed with an **offline-first, privacy-respecting philosophy**, your documents, reading history, highlights, and voice notes never leave your device. No subscriptions, no mandatory accounts, and no data tracking.

---

## 📸 App Preview

<div align="center">

| 📚 Reading & TTS | 🎧 Immersive Audio | 📄 Original PDF View |
| :---: | :---: | :---: |
| <img src="docs/screenshots/current_reader.png" width="280" alt="Reading Mode & Highlighting" /> | <img src="docs/screenshots/audio_player.png" width="280" alt="Audio Player & Speeds" /> | <img src="docs/screenshots/pdf_viewer.png" width="280" alt="Original PDF Mode" /> |

| ✍️ Notes & Voice Clips | 🧠 Study Hub & Flashcards | 📊 Habit & Reading Insights |
| :---: | :---: | :---: |
| <img src="docs/screenshots/notes.png" width="280" alt="Multi-Media Notes" /> | <img src="docs/screenshots/study.png" width="280" alt="Study Hub Flashcards" /> | <img src="docs/screenshots/insights.png" width="280" alt="Analytics Heatmap" /> |

</div>

<p align="center">
  <b>Android 16 Material You Home Screen Widgets</b><br/>
  <img src="docs/screenshots/widgets.png" width="420" alt="Interactive Home Widgets" />
</p>

---

## 🚀 Key Features

### 📖 1. Universal Multi-Format Reading
* **Broad Format Support**: Import and read **PDF**, **EPUB**, **DOCX**, **PPTX**, and plain text (**TXT**, **MD**, **LOG**, etc.), or scrape clean distraction-free articles directly from web URLs.
* **Dual Reading Engines**:
  * **Text Flow View**: Clean, continuous, customizable typographic view with sentence-by-sentence reading tracking and instant word lookups.
  * **Original Document View**: High-fidelity page renderer preserving original fonts, layouts, column structures, and embedded illustrations.
* **Optical Character Recognition (OCR)**: Scanned pages, multi-column research papers, and image-based PDFs are automatically parsed using on-device Google ML Kit OCR.
* **Interactive Table of Contents**: Jump effortlessly between sections, parts, and chapters with the interactive TOC navigation drawer.
* **Dyslexia & Comfort Typography**: Includes variable fonts designed for high legibility (*Atkinson Hyperlegible*, *Literata*, *Lora*, *Bitter*), configurable font scaling, line height, and Night Mode PDF color inversion.

### 🎙️ 2. On-Device Neural Audiobook Player
* **Studio-Quality Neural Voices**: Experience lifelike, expressive offline narration powered by **Piper** and **Kokoro** via Sherpa-ONNX runtime—generating natural speech completely offline.
* **System TTS Fallback**: Full compatibility with Android's built-in text-to-speech engines and third-party voice providers.
* **Full Audio Playback Controls**: Real-time slider adjustments for speech rate (0.5x–2.5x), pitch tuning, and voice accents.
* **Uninterrupted Background Playback**: MediaSession-powered background audio with lock-screen notification controls, headset button mapping, and auto-pause on phone calls or headphone disconnection.
* **Smart Sleep Timer**: Set timed audio shutoff with automatic sentence-boundary detection so playback never abruptly cuts off mid-thought.
* **Audio Exporter**: Convert any document chapter or excerpt directly into a `.wav` audio file for offline listening on any device.

### 🧠 3. Active-Recall Study Suite & Flashcards
* **Tactile Spaced-Repetition Flashcards**: Automatically generate or manually build flashcard decks with tactile flip animations, rating intervals, and mastery tracking.
* **Rich Markdown Notes**: Create study notes directly anchored to document sentences. Attach photos, document snapshots, and recorded voice memos to your cards.
* **Study Guide PDF Exporter**: Compile highlights, notes, definitions, and LaTeX mathematical equations into a beautifully styled, printable PDF study guide.
* **Extracted Sentence Editor**: Fix typographic quirks or customize sentences directly within the app without altering your source document.
* **Phonetic Pronunciation Rules**: Create custom pronunciation dictionaries (e.g., expanding medical acronyms or correcting foreign names) applied across both live TTS and exported audio.

### 🌐 4. In-App Digital Book Catalog
* **Direct Repository Access**: Browse, search, and download thousands of public-domain books directly from 5 major digital libraries:
  * *Project Gutenberg*
  * *Standard Ebooks*
  * *Open Library*
  * *ManyBooks*
  * *Ocean of PDF*
* **Automatic Download Interception**: Downloaded EPUBs, PDFs, and TXT files are automatically sandboxed and imported into your personal library with generated book covers and chapter outlines.

### 📊 5. Reading Insights & Habit Tracker
* **Daily Reading Streaks**: Stay motivated with continuous streak counters and milestones.
* **Annual Habit Heatmap**: Visual GitHub-style contribution heatmap tracking every reading session throughout the year.
* **Visual Analytics**: Interactive spring-animated charts showing reading speed (words per minute), format distribution, and rolling 8-week history.
* **Local Progress Backup**: Export, import, and transfer your full reading library, annotations, flashcard progress, and settings with zero cloud dependency.

### 📱 6. Android 16 Material You Widgets
* **Interactive Flashcards Widget**: Review due cards with interactive tap-to-flip and grading directly from your launcher screen.
* **Study Dashboard Widget**: Quick overview of daily review goals, active streaks, and deck mastery.
* **Mini Audio Player Widget**: Instant play/pause, track seeking, and sentence position counter right on your home screen.

---

## 🔒 Privacy & Offline-First

* **Zero Cloud Tracking**: All text extraction, OCR, AI study aids, and neural speech synthesis run 100% on-device.
* **No Mandatory Account**: Open the app and start reading immediately. No login, tracking analytics, or telemetry.
* **Sandboxed Security**: In-app web browsing enforces strict sandbox restrictions, ensuring untrusted web content cannot access local storage or device permissions.

---

## 📥 Installation

Download the latest release APK from the official repository releases:

👉 **[Download Latest Veritas Reader Release](https://github.com/fhes-tus/Vern-TTS/releases/latest)**

| Recommended For | Architecture |
| :--- | :--- |
| **99% of Modern Android Phones & Tablets** | `arm64-v8a` *(Recommended)* |
| **Universal Package (All Supported Devices)** | `arm64-v8a`, `armeabi-v7a` |
| **Legacy 32-bit Devices** | `armeabi-v7a` |

> **Requirements**: Android 9.0 (Pie / API 28) or higher. Optimized for Android 15 & Android 16.

---

## 🛠️ Architecture & Tech Stack

Veritas Reader is built adhering strictly to modern Android development standards and Unidirectional Data Flow (UDF) architecture:

* **UI Layer**: 100% declarative UI built with **Jetpack Compose**, **Material 3**, and Compose Spring Animations.
* **Architecture**: MVVM with `StateFlow` and immutable UI state representations.
* **Background Audio & Media**: **AndroidX Media3** with foreground service lifecycle management and battery optimization guardrails.
* **Home Screen Widgets**: **AndroidX Glance** for responsive, interactive Material You home screen widgets.
* **Neural Speech Synthesis**: C++/JNI bindings to **Sherpa-ONNX**, running quantized **Piper** and **Kokoro** models with low-latency streaming PCM buffers.
* **OCR & Vision**: **Google ML Kit** Latin Text Recognition and Language Identification.
* **Document Parsing**: Hybrid pipeline combining **PdfBox-Android**, Android native `PdfRenderer`, and lightweight XML/EPUB streaming parsers.
* **Data Reliability**: Atomic file writing and automatic JSON serialization backup (`__bak`) to ensure zero document loss during abrupt process death.

---

## 🤝 Contributing & Feedback

Suggestions, bug reports, and pull requests are welcome! Feel free to open an issue on the [Issues page](https://github.com/fhes-tus/Vern-TTS/issues).

---

<div align="center">
  <sub>Crafted with passion for readers, students, and lifelong learners.</sub>
</div>

