# Contributing to Tide Music

Thank you for your interest in contributing to **Tide Music**! Open-source thrives on community contributions, bug reports, feature requests, and code improvements.

---

## 🌟 Ways to Contribute

1. **Reporting Bugs**: Check existing issues first. If not reported, open a new issue with steps to reproduce, device model, Android version, and logs if possible.
2. **Suggesting Enhancements**: Open an issue describing the proposed feature, the use case, and any relevant UI mockups.
3. **Submitting Pull Requests**:
   - Fork the repository.
   - Create a feature branch (`git checkout -b feature/awesome-feature`).
   - Implement your changes following Kotlin and Android best practices.
   - Verify that `./gradlew assembleDebug` builds cleanly with 0 errors.
   - Commit your changes using conventional commit messages.
   - Push to your branch and open a Pull Request.

---

## 🛠️ Development Guidelines

- **Code Style**: Follow the official [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html) and [Android Compose guidelines](https://developer.android.com/jetpack/compose/architecture).
- **Architecture**: Keep business logic in `LibraryRepository` / `PlaybackController` and maintain unidirectional data flow (UDF) in Compose UI.
- **Testing**: Ensure any changes to database queries, lyrics parsers, or duration formatters are covered by unit tests where applicable.

---

## 📜 Code of Conduct

Please note that this project is released with a [Code of Conduct](CODE_OF_CONDUCT.md). By participating in this project, you agree to abide by its terms.
