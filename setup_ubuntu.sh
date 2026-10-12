#!/bin/bash
set -e

echo "=== 1. Installing system dependencies (Java, Zip, Wget) ==="
sudo apt-get update -y
sudo apt-get install -y wget unzip zip openjdk-17-jdk

echo "=== 2. Downloading Android Command Line Tools ==="
mkdir -p "$HOME/android-sdk"
cd "$HOME/android-sdk"
if [ ! -f commandlinetools.zip ]; then
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O commandlinetools.zip
fi
unzip -q commandlinetools.zip -d cmdline-tools-temp
mkdir -p cmdline-tools/latest
mv cmdline-tools-temp/cmdline-tools/* cmdline-tools/latest/ 2>/dev/null || true
rm -rf cmdline-tools-temp commandlinetools.zip

export ANDROID_HOME="$HOME/android-sdk"
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin"

echo "=== 3. Installing Android Platform and Build Tools ==="
yes | sdkmanager --licenses > /dev/null 2>&1 || true
sdkmanager "platforms;android-30" "build-tools;30.0.3"

echo "=== 4. Setting up android.jar ==="
cp "$HOME/android-sdk/platforms/android-30/android.jar" "$HOME/android-sdk/android.jar"

echo "=== 5. Setting up environment variables ==="
# Add tools to PATH for the current session
export PATH="$PATH:$HOME/android-sdk/build-tools/30.0.3"

echo ""
echo "✅ Setup complete successfully!"
echo "⚠️ NOTE: You need to run the following command once per terminal session before building:"
echo "export PATH=\$PATH:\$HOME/android-sdk/build-tools/30.0.3"
