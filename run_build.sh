#!/bin/bash
echo "🔍 در حال جستجوی پروژه اندروید در کل فضای کد اسپیس..."
GRADLEW_PATH=$(find /workspaces -name "gradlew" -type f 2>/dev/null | head -n 1)

if [ -z "$GRADLEW_PATH" ]; then
    echo "❌ هیچ پروژه اندرویدی (فایل gradlew) در این محیط پیدا نشد!"
    echo "لطفا مطمئن شوید که پروژه اندروید خود را در این کد اسپیس کلون یا ایجاد کرده‌اید."
    exit 1
fi

DIR=$(dirname "$GRADLEW_PATH")
echo "✅ پروژه در این مسیر پیدا شد: $DIR"
cd "$DIR" || exit 1

if [ ! -f ~/my-release-key.jks ]; then
    if [ ! -f "$DIR/my-release-key.jks" ]; then
        echo "❌ فایل کلید (my-release-key.jks) در پوشه خانگی پیدا نشد! لطفا مطمئن شوید قبلا آن را ساخته‌اید."
        exit 1
    fi
else
    mv ~/my-release-key.jks "$DIR/"
    echo "📁 فایل کلید به پوشه پروژه منتقل شد."
fi

read -s -p "🔑 رمز عبور Keystore را وارد کنید: " STORE_PASS
echo ""
read -s -p "🔑 رمز عبور Key (معمولا همان قبلی): " KEY_PASS
echo ""

echo "⏳ در حال بیلد گرفتن... لطفا صبر کنید (ممکن است چند دقیقه طول بکشد)."
chmod +x gradlew
./gradlew assembleRelease \
-Pandroid.injected.signing.store.file="$DIR/my-release-key.jks" \
-Pandroid.injected.signing.store.password="$STORE_PASS" \
-Pandroid.injected.signing.key.alias="my-alias" \
-Pandroid.injected.signing.key.password="$KEY_PASS"

if [ $? -eq 0 ]; then
    echo "✅ بیلد با موفقیت تمام شد!"
    echo "📍 فایل APK در این مسیر قرار دارد:"
    echo "$DIR/app/build/outputs/apk/release/app-release.apk"
else
    echo "❌ بیلد با خطا مواجه شد. متن خطای بالا را بررسی کنید."
fi
