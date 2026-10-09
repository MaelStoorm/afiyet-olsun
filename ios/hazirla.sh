#!/bin/bash
# iOS uygulamasının site klasör(ler)ini hazırlar (GitHub Actions'ta derlemeden önce çalışır).
set -euo pipefail
cd "$(dirname "$0")/.."
python3 ios/site.py --html index.html --out ios/gen/AfiyetOlsun/site --fonts-css android/app/src/main/assets/fonts.css --fonts-dir android/app/src/main/assets/fonts --copy gizlilik.html icon-192.png icon-512.png
