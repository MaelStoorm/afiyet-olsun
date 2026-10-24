"""iPhone uygulamasına girecek dosyaları (site klasörü, kabuk kodu, proje ayarları) denetler; sorun varsa derleme durur.

  python3 ios/denetle.py ios/gen/AfiyetOlsun/site ios/Shell ios/project.yml

- Dosyalarda Android'e ait özelliklerin (ödüllü reklam, reklam izni, Google Play) izi kalmamalı.
- Kabukta oyunun kullanmadığı, ayarla açılıp kapanan özellikler (konum, dosya, ağ iletimi, ek CSS) olmamalı.
  Afiyet Olsun telefondan yalnızca akşam hatırlatması (bildirim), titreşim ve ekran yönünü kullanır.
- Her .html dosyasındaki her <script> bloğu ayrı ayrı `node --check` ile sözdizimi denetiminden geçer.
"""
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

# Büyük/küçük harf fark etmeden aranır
YASAK = [
    "AndroidAds", "onAndroidAd", "AfiyetAndroid", "AndroidBridge", "adNative", "showRewarded", "cancelRewarded",
    "privacyOptions", "showPrivacyOptions", "sAdPriv", "admob", "Reklam", "reklam",
    "play.google", "Google Play", "billing", "purchase", "adfree", "ad-free", "android:start", "android:end",
    "ExtraCSS", "ProxyOrigin", "__vekil__", "proxy", "workers.dev", "ShellNoAsk",
]
# Büyük/küçük harfe duyarlı (kabuk kodu ve ayarlar için)
YASAK_KOD = [
    r"CLLocationManager", r"CoreLocation", r"NSLocation\w*UsageDescription", r"\bGeolocation\b", r'"Reminders"',
    r"URLSession", r"UIDocumentPicker", r"UIPasteboard", r"StoreKit", r"GoogleMobileAds",
]
METIN = {".html", ".htm", ".js", ".css", ".json", ".webmanifest", ".txt", ".swift", ".yml", ".plist"}

kokler = [Path(k) for k in (sys.argv[1:] or ["ios/gen/AfiyetOlsun/site", "ios/Shell", "ios/project.yml"])]
hata = []
htmls = []
for kok in kokler:
    if not kok.exists():
        hata.append(f"{kok}: bulunamadı")
        continue
    for f in [kok] if kok.is_file() else sorted(kok.rglob("*")):
        if not f.is_file() or f.suffix.lower() not in METIN:
            continue
        text = f.read_text(encoding="utf-8", errors="replace")
        lines = text.splitlines()
        for w in YASAK:
            hits = [i + 1 for i, line in enumerate(lines) if w.lower() in line.lower()]
            if hits:
                hata.append(f"{f}: '{w}' kalmış (satır {', '.join(map(str, hits[:5]))})")
        for y in YASAK_KOD:
            hits = [i + 1 for i, line in enumerate(lines) if re.search(y, line)]
            if hits:
                hata.append(f"{f}: '{y}' kalmış (satır {', '.join(map(str, hits[:5]))})")
        if f.suffix.lower() in (".html", ".htm"):
            htmls.append((f, text))

node = shutil.which("node")
if not node:
    hata.append("node bulunamadı, betikler denetlenemedi")
else:
    n = 0
    with tempfile.TemporaryDirectory() as tmp:
        for f, html in htmls:
            blocks = re.findall(r"<script\b([^>]*)>(.*?)</script>", html, re.S | re.I)
            for i, (attrs, code) in enumerate(blocks):
                tur = re.search(r"\btype=[\"']?([^\"'\s>]+)", attrs)
                tur = tur.group(1).lower() if tur else ""
                if "src=" in attrs or not code.strip() or tur not in ("", "text/javascript", "module"):
                    continue
                n += 1
                js = Path(tmp) / f"betik{n}{'.mjs' if tur == 'module' else '.js'}"
                js.write_text(code, encoding="utf-8")
                r = subprocess.run([node, "--check", str(js)], capture_output=True, text=True)
                if r.returncode:
                    hata.append(f"{f} <script> #{i}: sözdizimi hatası\n{r.stderr.strip()}")
    print(f"{n} betik bloğu node --check ile denetlendi")

if hata:
    print("iOS denetimi BAŞARISIZ:")
    for h in hata:
        print(" - " + h)
    sys.exit(1)
print(f"{', '.join(map(str, kokler))}: yasak ifade yok, betikler sağlam")
