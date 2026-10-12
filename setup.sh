#!/bin/bash
# Setup script for building Android apps in Termux
# Installs all required packages and downloads the Android SDK platform jar
set -e

echo "=== Installing build tools ==="
pkg install -y aapt aapt2 apksigner dx zip

echo ""
echo "=== Downloading Android SDK platform (android.jar) ==="
PLATFORM_ZIP="$HOME/platform-33.zip"
ANDROID_JAR_DIR="$HOME/android-sdk"
mkdir -p "$ANDROID_JAR_DIR"

if [ ! -f "$ANDROID_JAR_DIR/android.jar" ]; then
    curl -L -o "$PLATFORM_ZIP" \
        "https://dl.google.com/android/repository/platform-33_r02.zip"
    unzip -j "$PLATFORM_ZIP" "android-13/android.jar" -d "$ANDROID_JAR_DIR/"
    rm -f "$PLATFORM_ZIP"
    echo "   android.jar saved to $ANDROID_JAR_DIR/android.jar"
else
    echo "   android.jar already exists, skipping download"
fi

echo ""
echo "=== Generating debug keystore ==="
if [ ! -f "$HOME/.debug.keystore" ]; then
    keytool -genkeypair \
        -keystore "$HOME/.debug.keystore" \
        -alias debug \
        -keyalg RSA -keysize 2048 \
        -validity 10000 \
        -storepass android \
        -keypass android \
        -dname "CN=Debug, OU=Debug, O=Debug, L=Debug, ST=Debug, C=US"
    echo "   Debug keystore created."
else
    echo "   Debug keystore already exists, skipping"
fi

echo ""
echo "=== Setup complete ==="
echo "You can now build with: bash build.sh"
