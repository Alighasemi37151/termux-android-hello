# termux-android-hello

Build and install Android APKs entirely from Termux -- no Android Studio, no computer, just your phone.

This is a minimal Hello World app that demonstrates the complete build pipeline: resource compilation, Java compilation, DEX conversion, APK packaging, and signing.

## Prerequisites

- Android device with [Termux](https://f-droid.org/en/packages/com.termux/) installed
- Java (JDK) installed in Termux: `pkg install openjdk-21`

## Quick Start

```bash
# Clone the repo
git clone https://github.com/azmaveth/termux-android-hello.git
cd termux-android-hello

# Install build tools and download android.jar
bash setup.sh

# Grant storage access (needed once for installing APKs)
termux-setup-storage

# Build the APK
bash build.sh

# Install on device
cp build/apk/app.apk ~/storage/shared/ && termux-open ~/storage/shared/app.apk
```

## What setup.sh installs

| Package | Purpose |
|---------|---------|
| `aapt2` | Android resource compiler and linker |
| `apksigner` | APK signing tool |
| `dx` | Java bytecode to DEX converter |
| `zip` | APK packaging |

It also downloads the Android SDK platform JAR (`android.jar` from API 33) which provides the Android framework classes, and generates a debug signing keystore.

## Build Pipeline

The build follows the standard Android build process, just without Gradle:

```
res/*.xml ──> aapt2 compile ──> *.flat
                                  │
AndroidManifest.xml ──> aapt2 link ──> unsigned.apk + R.java
                                                        │
*.java + R.java ──> javac ──> *.class ──> dx ──> classes.dex
                                                      │
unsigned.apk + classes.dex ──> zip ──> apksigner ──> signed.apk
```

## Project Structure

```
.
├── AndroidManifest.xml          # App manifest
├── build.sh                     # Build script
├── setup.sh                     # One-time setup (installs tools)
├── res/
│   ├── layout/activity_main.xml # UI layout
│   └── values/strings.xml       # String resources
└── src/
    └── com/example/hello/
        └── MainActivity.java    # App code
```

## Limitations

- Java 8 bytecode only (uses `dx` instead of `d8` due to a JDK 21 compatibility issue)
- No dependency management (no Gradle/Maven)
- No AndroidX/Jetpack libraries (framework APIs only)
- No ProGuard/R8 minification
- No Compose -- XML layouts only

These limitations aside, this approach works well for utility apps, prototypes, personal tools, and learning Android fundamentals.

## Adapting for Your Own App

1. Change the package name in `AndroidManifest.xml` and the directory structure under `src/`
2. Update `build.sh` to point to your new package/class paths
3. Add your layouts in `res/layout/` and strings in `res/values/`
4. Write your Java code -- any Android framework API is available

## License

MIT
