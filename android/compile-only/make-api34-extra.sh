#!/bin/bash
# Erzeugt api34-extra.jar (nur zum Kompilieren, kommt nicht in die APK):
# Health-Connect-Klassen aus Robolectric android-all 14 plus java.time aus dem lokalen JDK.
set -e
cd "$(dirname "$0")"
T=$(mktemp -d)
curl -sSL -o "$T/all.jar" https://repo1.maven.org/maven2/org/robolectric/android-all/14-robolectric-10818077/android-all-14-robolectric-10818077.jar
(cd "$T" && unzip -q all.jar 'android/health/connect/*' 'android/os/OutcomeReceiver.class')
JH=$(dirname "$(dirname "$(readlink -f "$(which javac)")")")
"$JH/bin/jimage" extract --dir "$T/jdk" --include 'regex:/java.base/java/time/.*' "$JH/lib/modules"
cp -r "$T/jdk/java.base/java" "$T/"
(cd "$T" && zip -q -r api34-extra.jar android java)
mv "$T/api34-extra.jar" . && rm -rf "$T"
echo "api34-extra.jar erstellt"
