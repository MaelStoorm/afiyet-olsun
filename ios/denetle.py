"""iPhone uygulamasına girecek site klasörünü denetler; sorun varsa derleme durur.

  python3 ios/denetle.py ios/gen/Uygulama/site

- Klasördeki metin dosyalarında Android'e ait özelliklerin (ödüllü reklam, reklam izni,
  Google Play) izi kalmamalı.
- index.html içindeki her <script> bloğu ayrı ayrı `node --check` ile sözdizimi denetiminden geçer.
"""
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

YASAK = [
    "AndroidAds", "onAndroidAd", "AfiyetAndroid", "adNative", "showRewarded", "cancelRewarded",
    "privacyOptions", "showPrivacyOptions", "sAdPriv", "admob", "Reklam", "reklam",
    "play.google", "Google Play", "billing", "purchase", "adfree", "ad-free", "android:start", "android:end",
]
METIN = {".html", ".htm", ".js", ".css", ".json", ".webmanifest", ".txt"}

site = Path(sys.argv[1] if len(sys.argv) > 1 else "ios/gen/AfiyetOlsun/site")
hata = []
for f in sorted(site.rglob("*")):
    if not f.is_file() or f.suffix.lower() not in METIN:
        continue
    text = f.read_text(encoding="utf-8", errors="replace")
    low = text.lower()
    for w in YASAK:
        hits = [i + 1 for i, line in enumerate(text.splitlines()) if w.lower() in line.lower()] if w.lower() in low else []
        if hits:
            hata.append(f"{f.relative_to(site)}: '{w}' kalmış (satır {', '.join(map(str, hits[:5]))})")

html = (site / "index.html").read_text(encoding="utf-8")
node = shutil.which("node")
if not node:
    hata.append("node bulunamadı, betikler denetlenemedi")
else:
    blocks = re.findall(r"<script\b([^>]*)>(.*?)</script>", html, re.S | re.I)
    n = 0
    with tempfile.TemporaryDirectory() as tmp:
        for i, (attrs, code) in enumerate(blocks):
            if "src=" in attrs or not code.strip():
                continue
            n += 1
            js = Path(tmp) / f"betik{i}.js"
            js.write_text(code, encoding="utf-8")
            r = subprocess.run([node, "--check", str(js)], capture_output=True, text=True)
            if r.returncode:
                hata.append(f"index.html <script> #{i}: sözdizimi hatası\n{r.stderr.strip()}")
    print(f"{n} betik bloğu node --check ile denetlendi")

if hata:
    print("iOS sitesi denetimi BAŞARISIZ:")
    for h in hata:
        print(" - " + h)
    sys.exit(1)
print(f"{site}: yasak ifade yok, betikler sağlam")
