# Afiyet Olsun 🧿

Türk mutfağı temalı bir lokanta oyunu. Müşteri ne isterse onu yap: kısır, lahmacun, mantı, yaprak sarma, mercimek çorbası… Doğru malzemeleri koy, pişir, servis et, dükkanını büyüt.

Her gün sabah hazırlığıyla başlar: marketten malzeme al (sabah aldıkların market listene kaydedilir, ertesi gün tek dokunuşla yine alırsın), çayı demle, sarma, börek, mercimek gibi yemekleri önceden hazırla, sonra dükkanı aç. Lahmacun ve pide siparişle sıcak yapılır. Vegan ve vejetaryen müşteriler için çoban salatası, humus, mercimek köftesi, etsiz çiğ köfte, tahinli kurabiye, tofulu menemen gibi bitkisel yemekler, huysuz müşteriler, gazeteden bir yemek eleştirmeni ve işini kolaylaştıran ustalıklar var.

## Oyna

- **Telefonda veya bilgisayarda:** https://maelstoorm.github.io/afiyet-olsun/
- **iPhone:** Linki Safari'de aç → Paylaş → "Ana Ekrana Ekle". Ana ekrandaki simgeden tam ekran, uygulama gibi açılır; bir kez açtıktan sonra internetsiz de oynanır. Telefonu yan çevirerek oyna (dönmüyorsa Denetim Merkezi'nden dikey yön kilidini kapat). Akşam hatırlatma bildirimleri yalnızca Android uygulamasında var.
- **Android APK:** https://maelstoorm.github.io/afiyet-olsun/AfiyetOlsun.apk
  İndir, dokun, yükle. "Bilinmeyen uygulamalar" uyarısı çıkarsa izin ver.

## Dosyalar

- `index.html`: oyunun tamamı (tek dosya).
- `AfiyetOlsun.apk`: Android sürümü.
- `android/`: Android uygulamasının Gradle projesi.
- `admob.properties`: Android uygulamasının AdMob reklam kimlikleri.
- `gizlilik.html`: gizlilik politikası (oyun kişisel veri toplamaz): https://maelstoorm.github.io/afiyet-olsun/gizlilik.html

## Android (Google Play)

- `android/` normal bir Gradle projesidir (Android Gradle Plugin 8.13, Gradle 8.14.3, Java 17). Paket adı `com.afiyetolsun.oyun`.
- Derleme sırasında kökteki `index.html` uygulamaya kopyalanır (Google Fonts bağlantıları yerine uygulamanın içindeki `fonts.css` kullanılır). Yani uygulama her zaman oyunun son hâlini taşır; `index.html`'i ayrıca kopyalamak gerekmez.
- `main` dalına her gönderimde GitHub Actions imzasız bir paket derler ve `builds` dalına `afiyet-olsun-unsigned.aab` olarak koyar. `commit.txt` hangi commit'ten derlendiğini yazar. Derleme bozulursa aynı dala `build-log.txt` eklenir.
- Play Console'a yüklemeden önce paketi yükleme anahtarıyla imzala (anahtar ve parolası asla depoya konmaz):
  `jarsigner -sigalg SHA256withRSA -digestalg SHA-256 -keystore key.jks afiyet-olsun-unsigned.aab afiyet`
- Hediyeler Android'de ödüllü reklamla (Google AdMob) verilir; reklam yüklenemezse ya da web sürümündeyse eski 5 saniyelik bekleme kullanılır. Reklam kimlikleri kökteki `admob.properties` dosyasına yazılır (`appId=`, `rewardedId=`). Boş kalırsa Google'ın test reklamları çıkar.
- Bilgisayarda derlemek için (Android SDK gerekir): `cd android && ./gradlew bundleRelease`

## Telif hakkı

© 2026 Egemen ([MaelStoorm](https://github.com/MaelStoorm)). **Tüm hakları saklıdır.**

Bu oyun açık kaynak değildir. Oynayabilirsin, ama kodu, çizimleri, müziği ya da oyunun kendisini izin almadan kopyalamak, değiştirmek, kendi adınla yayınlamak veya satmak yasaktır. Ayrıntılar [LICENSE](LICENSE) dosyasında.

