# Fit bis April

Persönliche Fitness- und Ernährungs-App für Android: Kalorien und Makros, Foto-Erkennung von Mahlzeiten, Trainingsplan, Gewichtsverlauf und Tagebuch.

## Installieren

Neueste Version herunterladen: [fit-bis-april.apk](https://github.com/Rapida88/fitness-app/releases/latest/download/fit-bis-april.apk)

Die App sucht beim Start selbst nach Updates (`version.json` im neuesten Release) und installiert sie nach Rückfrage.

## Aufbau

- `web/fitness-app.html`: die komplette Oberfläche (läuft auch im Browser)
- `android/`: schlanke Android-Hülle (WebView, Kamera, Foto-Analyse über die Claude API, Update-Prüfung)
- `android/build.sh <versionCode> <versionName>`: baut und signiert die APK. Der Signierschlüssel liegt bewusst nicht im Repo.
