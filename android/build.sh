#!/bin/sh
# Needs (Ubuntu): apt-get install aapt apksigner zipalign dalvik-exchange android-sdk-platform-23 openjdk-21-jdk
# Before building: copy the new game HTML into assets/index.html (font links replaced with fonts.css), bump versionCode in AndroidManifest.xml.
# Updates must be signed with the same key.jks or Android refuses to install them over the old version.
set -e; cd "$(dirname "$0")"; AJ=/usr/lib/android-sdk/platforms/android-23/android.jar
rm -rf gen obj bin; mkdir -p gen obj bin
aapt package -f -m -J gen -M AndroidManifest.xml -S res -I $AJ
javac --release 8 -classpath $AJ -d obj src/com/afiyetolsun/oyun/*.java gen/com/afiyetolsun/oyun/R.java
dalvik-exchange --dex --output=bin/classes.dex obj
aapt package -f -M AndroidManifest.xml -S res -A assets -I $AJ -F bin/unsigned.apk
(cd bin && aapt add unsigned.apk classes.dex)
zipalign -f -p 4 bin/unsigned.apk bin/aligned.apk
apksigner sign --ks key.jks --ks-pass env:AFIYET_KS_PASS --ks-key-alias afiyet --out bin/AfiyetOlsun.apk bin/aligned.apk
# The signing key (key.jks) is kept out of this repo on purpose. Set AFIYET_KS_PASS before running.
