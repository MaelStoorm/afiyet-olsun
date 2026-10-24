#!/bin/bash
# iOS uygulamasının site klasör(ler)ini hazırlar (GitHub Actions'ta derlemeden önce çalışır).
# Sayfanın yalnızca Android'e ait bölümleri (ödüllü reklam vb.) iPhone kopyasına hiç konmaz (--strip-android).
# Gizlilik politikası uygulamada internetteki adresinden açılır, uygulamanın içine konmaz.
set -euo pipefail
cd "$(dirname "$0")/.."
python3 ios/site.py --html index.html --out ios/gen/AfiyetOlsun/site --strip-android --fonts-css android/app/src/main/assets/fonts.css --fonts-dir android/app/src/main/assets/fonts --copy icon-192.png icon-512.png
python3 ios/denetle.py ios/gen/AfiyetOlsun/site
