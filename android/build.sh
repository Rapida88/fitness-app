#!/bin/bash
# Baut die APK. Aufruf: ./build.sh <versionCode> <versionName>
set -e
cd "$(dirname "$0")"
VC=${1:?versionCode}; VN=${2:?versionName}
JAR=/usr/lib/android-sdk/platforms/android-23/android.jar
rm -rf build && mkdir -p build/gen build/obj
cp ../../fitness-app/index.html assets/index.html 2>/dev/null || true
aapt package -f -M AndroidManifest.xml -S res -A assets -I $JAR -J build/gen \
  --min-sdk-version 29 --target-sdk-version 34 --version-code $VC --version-name $VN \
  -0 arsc -F build/app.unaligned.apk
javac -nowarn -source 8 -target 8 -bootclasspath $JAR:compile-only/api34-extra.jar -classpath $JAR -d build/obj \
  build/gen/R.java src/de/chris/fitbisapril/*.java
dalvik-exchange --dex --min-sdk-version=26 --output=build/classes.dex build/obj
(cd build && zip -q -j app.unaligned.apk classes.dex)
zipalign -f -p 4 build/app.unaligned.apk build/app.aligned.apk
apksigner sign --ks keystore/release.jks --ks-pass file:keystore/password.txt \
  --out build/fit-bis-april.apk build/app.aligned.apk
apksigner verify build/fit-bis-april.apk
echo "APK: build/fit-bis-april.apk"
