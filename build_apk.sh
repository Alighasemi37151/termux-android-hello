#!/bin/bash
# جستجوی فایل gradlew
GRADLEW=$(find . -name "gradlew" -type f | head -n 1)
if [ -z "$GRADLEW" ]; then
    echo "❌ gradlew پیدا نشد. لطفا مطمئن شوید در پوشه اصلی پروژه اندروید هستید."
    exit 1
fi

DIR=$(dirname "$GRADLEW")
echo "✅ پروژه در پوشه $DIR پیدا شد."
cd "$DIR"

read -s -p "🔑 رمز عبور Keystore را وارد کنید: " STORE_PASS
echo ""
read -s -p "🔑 رمز عبور Key (معمولا همان قبلی): " KEY_PASS
echo ""

# انتقال فایل کلید از پوشه خانگی به پوشه پروژه
if [ -f ~/my-release-key.jks ]; then
    mv ~/my-release-key.jks .
    echo "📁 فایل کلید به پوشه پروژه منتقل شد."
fi

# بیلد گرفتن
echo "⏳ در حال بیلد گرفتن... لطفا صبر کنید."
chmod +x gradlew
./gradlew assembleRelease \
-Pandroid.injected.signing.store.file="$(pwd)/my-release-key.jks" \
-Pandroid.injected.signing.store.password="$STORE_PASS" \
-Pandroid.injected.signing.key.alias="my-alias" \
-Pandroid.injected.signing.key.password="$KEY_PASS"

echo "✅ بیلد تمام شد. فایل APK در مسیر $DIR/app/build/outputs/apk/release/ قرار دارد."
