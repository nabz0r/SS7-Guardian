# Contributing to SS7 Guardian

First off, thank you for considering contributing to SS7 Guardian! 🛡️

This document provides guidelines for contributing to the project.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [How Can I Contribute?](#how-can-i-contribute)
- [Development Setup](#development-setup)
- [Coding Standards](#coding-standards)
- [Pull Request Process](#pull-request-process)
- [Reporting Bugs](#reporting-bugs)
- [Suggesting Features](#suggesting-features)

## Code of Conduct

This project is dedicated to providing a harassment-free experience for everyone. We expect all contributors to:

- Be respectful and inclusive
- Accept constructive criticism gracefully
- Focus on what is best for the community
- Show empathy towards other community members

## How Can I Contribute?

### Good First Issues

Look for issues labeled `good first issue` or `help wanted`. These are great starting points!

### Areas We Need Help

1. **Testing** - We need help testing on different Android devices and versions
2. **Documentation** - Improve README, add code comments, write tutorials
3. **Translations** - Help translate the app to other languages
4. **UI/UX** - Improve the user interface design
5. **Detection Algorithms** - Improve anomaly detection accuracy

## Development Setup

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 11 or newer
- Android SDK 34
- An Android device or emulator (API 26+)

### Getting Started

1. **Fork the repository**
   ```bash
   # Click 'Fork' on GitHub, then clone your fork
   git clone https://github.com/YOUR_USERNAME/SS7-Guardian.git
   ```

2. **Open in Android Studio**
   - File -> Open -> Select the project directory
   - Wait for Gradle sync to complete

3. **Run the app**
   - Connect a device or start an emulator
   - Click Run or press Shift+F10

### Project Structure

```
app/src/main/java/com/ss7guardian/
├── data/                 # Database (Room)
│   ├── dao/              # Data Access Objects
│   ├── entity/           # Entity classes
│   └── repository/       # Repository pattern
├── monitor/              # Monitoring modules
│   ├── CellMonitor.kt    # Cell tower monitoring
│   ├── NetworkMonitor.kt # Network type monitoring
│   └── SmsMonitor.kt     # SMS monitoring
├── receiver/             # Broadcast receivers
├── service/              # Background services
├── ui/                   # UI components
│   ├── adapter/          # RecyclerView adapters
│   └── MainActivity.kt   # Main screen
└── util/                 # Utility classes
```

## Coding Standards

### Kotlin Style

- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Maximum line length: 100 characters
- Use 4 spaces for indentation (no tabs)

### Documentation

- Add KDoc comments to all public classes and functions
- Include `@author` and `@since` tags in class documentation
- Document complex algorithms with inline comments

### Example

```kotlin
/**
 * Analyzes cell tower signal strength for anomalies.
 * 
 * IMSI catchers typically exhibit unusually strong signals
 * to overpower legitimate cell towers.
 *
 * @param currentStrength Current signal strength in dBm
 * @param previousStrength Previous signal strength in dBm
 * @return true if signal change is suspicious
 * @author SS7 Guardian Team
 * @since 0.1.0
 */
fun isSignalAnomaly(currentStrength: Int, previousStrength: Int): Boolean {
    // Signal jump > 20 dBm is considered suspicious
    return kotlin.math.abs(currentStrength - previousStrength) > SIGNAL_THRESHOLD
}
```

### Commit Messages

Use conventional commit format:

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

Types:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation only
- `style`: Code style changes
- `refactor`: Code refactoring
- `test`: Adding tests
- `chore`: Maintenance tasks

Example:
```
feat(monitor): Add rapid cell change detection

Detects when device connects to >10 different towers
within 60 seconds, which may indicate IMSI catcher.

Closes #42
```

## Pull Request Process

1. **Create a branch**
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Make your changes**
   - Write clean, documented code
   - Add tests if applicable
   - Update documentation if needed

3. **Test your changes**
   - Run the app on a device/emulator
   - Verify no regressions
   - Run any existing tests

4. **Submit the PR**
   - Push your branch to your fork
   - Create a Pull Request to `main`
   - Fill out the PR template
   - Link any related issues

5. **Review process**
   - A maintainer will review your PR
   - Address any feedback
   - Once approved, it will be merged

## Reporting Bugs

### Before Submitting

1. Check existing issues to avoid duplicates
2. Verify the bug on the latest version
3. Collect relevant information:
   - Android version
   - Device model
   - Steps to reproduce
   - Expected vs actual behavior

### Bug Report Template

```markdown
**Describe the bug**
A clear and concise description.

**To Reproduce**
1. Go to '...'
2. Click on '...'
3. See error

**Expected behavior**
What should happen.

**Device info:**
- Device: [e.g., Pixel 6]
- Android: [e.g., Android 13]
- App Version: [e.g., 0.1.0]

**Screenshots**
If applicable.
```

## Suggesting Features

### Feature Request Template

```markdown
**Is your feature request related to a problem?**
A clear description of the problem.

**Describe the solution you'd like**
What you want to happen.

**Describe alternatives you've considered**
Other solutions you've thought about.

**Additional context**
Any other information or screenshots.
```

## Questions?

Feel free to open an issue with the `question` label if you have any questions!

---

Thank you for contributing to SS7 Guardian! Together, we can make mobile security accessible to everyone. 🛡️
