# Fit bis April

Persönliche Fitness- und Ernährungs-App für Android: Kalorien und Makros, Foto-Erkennung von Mahlzeiten, Trainingsplan, Gewichtsverlauf und Tagebuch.

## Installieren

Neueste Version herunterladen: [fit-bis-april.apk](https://raw.githubusercontent.com/Rapida88/fitness-app/main/dist/fit-bis-april.apk)

Die App sucht beim Start selbst nach Updates (`dist/version.json`) und installiert sie nach Rückfrage.

## Aufbau

- `web/fitness-app.html`: die komplette Oberfläche (läuft auch im Browser)
- `android/`: schlanke Android-Hülle (WebView, Kamera, Foto- und Rezept-KI über die Gemini API, Update-Prüfung)
- `android/build.sh <versionCode> <versionName>`: baut und signiert die APK. Der Signierschlüssel liegt bewusst nicht im Repo.
