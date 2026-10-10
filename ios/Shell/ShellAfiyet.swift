/*
 * Afiyet Olsun'un Android köprüsü (window.AfiyetAndroid) iOS'ta: akşam hatırlatması, titreşim ve ekran yönü.
 * Hatırlatma Android'deki ReminderReceiver ile aynı kurallarla kurulur: oyun arka plana geçince saat 18:30'a
 * (en az 3 saat sonrasına) bir bildirim, oyuncu dönmezse en fazla 3 gün üst üste; oyun açılınca hepsi iptal edilir.
 * Oyun arka plana geçerken sesi kesilir ve oyun bekler (window.__afiyetBg). Hiçbir veri cihazdan çıkmaz.
 * Yalnızca Info.plist'te Shell > Reminders = "afiyet" olan uygulamada çalışır.
 * Telif Hakkı (c) 2026 Egemen Çalıkoğlu. Tüm hakları saklıdır.
 */
import UIKit
import UserNotifications
import WebKit

final class ShellAfiyet {
    static let maxInARow = 3

    /// Oyunun Android'de kullandığı köprü; çağrılar "shell" iletisiyle uygulamaya gelir
    static let script = """
    window.AfiyetAndroid = {
      setReminder: function (t, m) { webkit.messageHandlers.shell.postMessage({ op: 'remSet', title: String(t), messages: String(m) }); },
      setReminderOn: function (on) { webkit.messageHandlers.shell.postMessage({ op: 'remOn', on: !!on }); },
      vibrate: function (ms) { webkit.messageHandlers.shell.postMessage({ op: 'vib', ms: Number(ms) || 0 }); },
      setOrientation: function (m) { webkit.messageHandlers.shell.postMessage({ op: 'orient', mode: String(m) }); }
    };
    """

    private let center = UNUserNotificationCenter.current()
    private let prefs = UserDefaults.standard
    private weak var web: WKWebView?
    private let light = UIImpactFeedbackGenerator(style: .light)
    private let medium = UIImpactFeedbackGenerator(style: .medium)
    private let heavy = UIImpactFeedbackGenerator(style: .heavy)
    private let notice = UINotificationFeedbackGenerator()

    init(web: WKWebView) {
        self.web = web
        let nc = NotificationCenter.default
        nc.addObserver(self, selector: #selector(resign), name: UIApplication.willResignActiveNotification, object: nil)
        nc.addObserver(self, selector: #selector(background), name: UIApplication.didEnterBackgroundNotification, object: nil)
        nc.addObserver(self, selector: #selector(active), name: UIApplication.didBecomeActiveNotification, object: nil)
    }

    /// Oyun açılırken bir kez sorulur; reddedilirse oyun aynen çalışır, sadece hatırlatma gelmez.
    func askPermission() {
        center.requestAuthorization(options: [.alert, .sound]) { _, _ in }
    }

    /// Sayfadan gelen köprü çağrıları; işlenmediyse false
    func handle(_ op: String, _ body: [String: Any]) -> Bool {
        switch op {
        case "remSet":
            prefs.set(body["title"] as? String ?? "Afiyet Olsun", forKey: "afiyet.rem.title")
            prefs.set(body["messages"] as? String ?? "", forKey: "afiyet.rem.messages")
        case "remOn":
            let on = body["on"] as? Bool ?? true
            prefs.set(!on, forKey: "afiyet.rem.off")
            if !on { center.removeAllPendingNotificationRequests() }
        case "vib":
            vibrate((body["ms"] as? NSNumber)?.intValue ?? 0)
        default:
            return false
        }
        return true
    }

    /// Android'deki titreşim süresine göre telefonun dokunsal geri bildirimi
    private func vibrate(_ ms: Int) {
        if ms < 20 { light.impactOccurred() }
        else if ms < 35 { medium.impactOccurred() }
        else if ms < 60 { heavy.impactOccurred() }
        else { notice.notificationOccurred(.error) }
    }

    // Kontrol merkezi, gelen arama, ana ekran: oyun durur ve sesi kesilir
    @objc private func resign() {
        web?.evaluateJavaScript("window.__afiyetBg&&window.__afiyetBg(true)", completionHandler: nil)
    }

    @objc private func active() {
        web?.evaluateJavaScript("window.__afiyetBg&&window.__afiyetBg(!!document.hidden)", completionHandler: nil)
        center.removeAllPendingNotificationRequests()
        center.removeAllDeliveredNotifications()
    }

    @objc private func background() {
        // Bildirimler kurulana kadar uygulama arka planda kısa bir süre daha çalışsın
        var task: UIBackgroundTaskIdentifier = .invalid
        task = UIApplication.shared.beginBackgroundTask { UIApplication.shared.endBackgroundTask(task) }
        schedule {
            DispatchQueue.main.async { UIApplication.shared.endBackgroundTask(task) }
        }
    }

    private func schedule(done: @escaping () -> Void) {
        center.removeAllPendingNotificationRequests()
        guard !prefs.bool(forKey: "afiyet.rem.off") else { return done() }
        let title = prefs.string(forKey: "afiyet.rem.title") ?? "Afiyet Olsun"
        var msgs = (prefs.string(forKey: "afiyet.rem.messages") ?? "")
            .split(separator: "|").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        if msgs.isEmpty { msgs = ["Müşteriler kapıda bekliyor, dükkanı aç usta! 🍽️"] }

        // Bugün 18:30 en az 3 saat sonra değilse yarın 18:30; oyuncu dönmezse sonraki iki akşam da
        let cal = Calendar.current
        let now = Date()
        guard var first = cal.date(bySettingHour: 18, minute: 30, second: 0, of: now) else { return done() }
        while first < now.addingTimeInterval(3 * 3600) {
            guard let next = cal.date(byAdding: .day, value: 1, to: first) else { return done() }
            first = next
        }
        let group = DispatchGroup()
        for i in 0..<Self.maxInARow {
            guard let fire = cal.date(byAdding: .day, value: i, to: first) else { continue }
            let c = UNMutableNotificationContent()
            c.title = title
            c.body = msgs.randomElement() ?? msgs[0]
            c.sound = .default
            let when = cal.dateComponents([.year, .month, .day, .hour, .minute], from: fire)
            let trigger = UNCalendarNotificationTrigger(dateMatching: when, repeats: false)
            group.enter()
            center.add(UNNotificationRequest(identifier: "aksam-\(i)", content: c, trigger: trigger)) { _ in group.leave() }
        }
        NSLog("[kabuk] %@", "hatırlatma kuruldu: \(first), \(Self.maxInARow) akşam")
        group.notify(queue: .main, execute: done)
    }
}
