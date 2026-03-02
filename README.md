# Life Architect 2

**Gamify your goals, build your ideal self.**

---

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT) [![Kotlin Version](https://img.shields.io/badge/Kotlin-2.0.21-blue.svg)](https://kotlinlang.org) [![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202026-blue)](https://developer.android.com/jetpack/compose)

**Life Architect 2** is a native Android application designed to transform personal development into an engaging and rewarding journey. By applying principles of gamification, the app helps users define their goals, break them down into actionable tasks (or "quests"), and track their progress through a system of experience points (XP), levels, and ranks. It serves as a powerful tool for anyone looking to build habits, learn new skills, and systematically construct a better life.

This repository contains the complete source code for the application, written in 100% Kotlin and built with modern Android development practices.

---

## Features

The application is in active development. The current feature set provides the core engine for the gamified task management system.

| Feature | Status | Description |
| --- | --- | --- |
| **Task Management** | ✅ Complete | Create, track, and complete tasks ("quests"). |
| **Gamification Engine** | ✅ Complete | Earn XP and coins for completing tasks. |
| **Leveling System** | ✅ Complete | Progress through ranks from *Fragment Seeker* to *System Architect*. |
| **MVI Architecture** | ✅ Complete | A robust, scalable, and testable Model-View-Intent architecture. |
| **Offline-First Storage** | ✅ Complete | All data is stored locally using RoomDB for instant access. |
| **Goal Management** | 🚧 Planned | Group tasks under larger goals or projects. |
| **Analytics & Insights** | ✅ Complete | Visualize progress with charts and historical data. |
| **Trending Feed** | ✅ Complete | A daily feed of trending topics . |
| **Google Sign-In** | 🚧 Planned | Sync progress across devices with cloud backup. |

---

## Tech Stack & Architecture

This project is a case study in modern, native Android development, emphasizing best practices and a clean, scalable architecture.

- **100% **[**Kotlin**](https://kotlinlang.org/) — The entire codebase is written in Kotlin, leveraging coroutines and Flow for asynchronous operations.

- [**Jetpack Compose**](https://developer.android.com/jetpack/compose) — The UI is built entirely with Compose, enabling a declarative, reactive, and maintainable user interface.

- [**Room Database**](https://developer.android.com/training/data-storage/room) — A robust, local SQLite database for offline-first data persistence, with KSP code generation.

- **Model-View-Intent (MVI) Architecture** — A unidirectional data flow pattern that ensures a predictable and debuggable application state.

- **ViewModel & StateFlow** — Manages UI-related data and business logic, surviving configuration changes.

- **Repository Pattern** — Acts as a single source of truth for all application data, abstracting data sources from the rest of the app.

---

## Contributing

This is an open-source project and contributions are welcome. Whether you are fixing a bug, implementing a planned feature, or improving the documentation, feel free to open an issue or submit a pull request.

---

## License

This project is licensed under the **MIT License**. 

