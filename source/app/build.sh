#!/bin/bash
set -e
cd "$(dirname "$0")"
SDK="$(pwd)/../sdk36/android-36/android.jar"
OUT="build"
rm -rf "$OUT" gen
mkdir -p "$OUT" gen

aapt2 compile --dir res -o "$OUT/res.zip"
aapt2 link -o "$OUT/base.apk" -I "$SDK" --manifest AndroidManifest.xml --java gen --auto-add-overlay "$OUT/res.zip"

find src gen -name '*.java' > "$OUT/sources.txt"
LIBS="$(pwd)/libs/api-13.1.5.jar:$(pwd)/libs/provider-13.1.5.jar:$(pwd)/libs/shared-13.1.5.jar:$(pwd)/libs/aidl-13.1.5.jar"
javac -source 11 -target 11 -classpath "$SDK:$LIBS" -d "$OUT/classes" @"$OUT/sources.txt"
jar cf "$OUT/classes.jar" -C "$OUT/classes" .
mkdir -p "$OUT/dex"
d8 --release --lib "$SDK" --min-api 36 --output "$OUT/dex" "$OUT/classes.jar" \
  libs/api-13.1.5.jar libs/provider-13.1.5.jar libs/shared-13.1.5.jar libs/aidl-13.1.5.jar

cd "$OUT"
zip -q -j base.apk dex/classes.dex
echo "built: $(pwd)/base.apk"
