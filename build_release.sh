#!/bin/bash
set -e

PROJECT="$(cd "$(dirname "$0")" && pwd)"
ANDROID_JAR="$HOME/android-sdk/android.jar"
BUILD="$PROJECT/build"
GEN="$BUILD/gen"
OBJ="$BUILD/obj"
APK_DIR="$BUILD/apk"
COMPILED_RES="$BUILD/compiled_res"

if [ ! -f "$ANDROID_JAR" ]; then
    echo "ERROR: android.jar not found at $ANDROID_JAR"
    exit 1
fi

echo "=== Cleaning build artifacts ==="
rm -rf "$GEN" "$OBJ" "$APK_DIR" "$COMPILED_RES" "$BUILD/classes.dex"
mkdir -p "$GEN" "$OBJ" "$APK_DIR" "$COMPILED_RES"

echo "=== Step 1: Compile resources ==="
aapt2 compile --dir "$PROJECT/res" -o "$COMPILED_RES/"

echo "=== Step 2: Link resources ==="
aapt2 link \
    -I "$ANDROID_JAR" \
    --manifest "$PROJECT/AndroidManifest.xml" \
    --java "$GEN" \
    -o "$APK_DIR/app-unaligned.apk" \
    "$COMPILED_RES"/*.flat

echo "=== Step 3: Compile Java ==="
javac \
    -source 1.8 -target 1.8 \
    -classpath "$ANDROID_JAR" \
    -d "$OBJ" \
    "$GEN/com/example/hello/R.java" \
    "$PROJECT/src/com/example/hello/MainActivity.java"

echo "=== Step 4: DEX ==="
dx --dex --output="$BUILD/classes.dex" "$OBJ"

echo "=== Step 5: Package APK ==="
cp "$APK_DIR/app-unaligned.apk" "$APK_DIR/app.apk"
cd "$BUILD" && zip -j "$APK_DIR/app.apk" classes.dex
cd "$PROJECT"
if [ -d "assets" ]; then
    zip -r "$APK_DIR/app.apk" assets/
fi

echo "=== Step 6: Sign with RELEASE key ==="
KEYSTORE="$PROJECT/my-release-key.jks"
if [ ! -f "$KEYSTORE" ]; then
    echo "ERROR: Release keystore not found at $KEYSTORE"
    echo "Please make sure my-release-key.jks is in the project folder."
    exit 1
fi

read -s -p "🔑 Enter Keystore Password: " STORE_PASS
echo ""
read -s -p "🔑 Enter Key Password (usually same): " KEY_PASS
echo ""

apksigner sign \
    --ks "$KEYSTORE" \
    --ks-key-alias "my-alias" \
    --ks-pass pass:"$STORE_PASS" \
    --key-pass pass:"$KEY_PASS" \
    "$APK_DIR/app.apk"

echo ""
echo "=== BUILD SUCCESSFUL ==="
ls -lh "$APK_DIR/app.apk"
echo ""
echo "To download this file, use the VS Code file explorer on the left."
