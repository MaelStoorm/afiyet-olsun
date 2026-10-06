#!/bin/sh
# Google Play için Android App Bundle (.aab) üretir. bundletool olmadan, Google'ın belgelediği paket düzeniyle:
#   BundleConfig.pb, base/manifest/AndroidManifest.xml (proto), base/resources.pb, base/res, base/assets, base/dex/classes.dex
# Yükleme anahtarı olarak key.jks ile imzalanır (Play App Signing).
set -e; cd "$(dirname "$0")"; AJ=/usr/lib/android-sdk/platforms/android-23/android.jar
rm -rf aab; mkdir -p aab/gen aab/obj aab/b/base/manifest aab/b/base/dex bin
aapt2 compile --dir res -o aab/res.zip
aapt2 link --proto-format -o aab/proto.apk -I $AJ --manifest AndroidManifest.xml -A assets --java aab/gen --auto-add-overlay aab/res.zip
javac -nowarn --release 8 -classpath $AJ -d aab/obj src/com/afiyetolsun/oyun/*.java aab/gen/com/afiyetolsun/oyun/R.java 2>/dev/null
dalvik-exchange --dex --output=aab/classes.dex aab/obj
(cd aab && mkdir p && cd p && unzip -q ../proto.apk)
mv aab/p/AndroidManifest.xml aab/b/base/manifest/AndroidManifest.xml
mv aab/p/resources.pb aab/b/base/resources.pb
mv aab/p/res aab/b/base/res
mv aab/p/assets aab/b/base/assets
cp aab/classes.dex aab/b/base/dex/classes.dex
# BundleConfig { bundletool { version: "1.17.2" } }
printf '\012\010\022\0061.17.2' > aab/b/BundleConfig.pb
rm -f bin/AfiyetOlsun.aab; (cd aab/b && zip -q -r -X ../../bin/AfiyetOlsun.aab BundleConfig.pb base)
jarsigner -sigalg SHA256withRSA -digestalg SHA-256 -keystore key.jks -storepass:env AFIYET_KS_PASS bin/AfiyetOlsun.aab afiyet >/dev/null
jarsigner -verify bin/AfiyetOlsun.aab | tail -1
# deneme: proto kaynakları gerçek APK'ya çevrilebiliyor mu (bundletool'un yaptığı gibi)
aapt2 convert -o aab/check.apk --output-format binary aab/proto.apk
(cd aab && zip -q check.apk classes.dex)
aapt dump badging aab/check.apk | grep -E "^package|targetSdk|launchable"
