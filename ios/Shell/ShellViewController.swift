/*
 * Uygulamanın tek ekranı: oyunun index.html sayfasını açar, oyunun window.AfiyetApp köprüsünü
 * (akşam hatırlatması, titreşim, ekran yönü; bkz. ShellAfiyet) bağlar ve alçak ekranda oyunu sığdırır.
 * Dış bağlantılar (gizlilik politikası) telefonda açılır.
 * Telif Hakkı (c) 2026 Egemen Çalıkoğlu. Tüm hakları saklıdır.
 */
import UIKit
import WebKit

final class ShellViewController: UIViewController, WKNavigationDelegate, WKUIDelegate, WKScriptMessageHandler {

    private let config = ShellConfig.shared
    private var web: WKWebView!

    private var afiyet: ShellAfiyet?
    private var askedPermission = false
    private var fit: (zoom: CGFloat, width: CGFloat) = (1, 0)

    /// Oyunun istediği ekran yönü (oyunun ayarlarındaki "Ekran yönü"); nil ise Info.plist'teki ayar
    static var orientationOverride: UIInterfaceOrientationMask?

    override var preferredStatusBarStyle: UIStatusBarStyle {
        config.lightStatusBar ? .lightContent : .darkContent
    }
    override var prefersHomeIndicatorAutoHidden: Bool { config.orientations == .landscape }
    override var supportedInterfaceOrientations: UIInterfaceOrientationMask {
        Self.orientationOverride ?? config.orientations
    }

    // MARK: - Kurulum

    override func loadView() {
        let content = WKUserContentController()
        content.add(WeakHandler(self), name: "shell")
        for js in userScripts() {
            content.addUserScript(WKUserScript(source: js, injectionTime: .atDocumentStart, forMainFrameOnly: true))
        }

        let cfg = WKWebViewConfiguration()
        cfg.userContentController = content
        cfg.allowsInlineMediaPlayback = true
        cfg.mediaTypesRequiringUserActionForPlayback = []
        cfg.dataDetectorTypes = []
        cfg.websiteDataStore = .default()
        let site = Bundle.main.url(forResource: "site", withExtension: nil) ?? Bundle.main.bundleURL
        cfg.setURLSchemeHandler(SiteScheme(root: site), forURLScheme: SiteScheme.scheme)

        web = WKWebView(frame: .zero, configuration: cfg)
        web.navigationDelegate = self
        web.uiDelegate = self
        web.isOpaque = false
        web.backgroundColor = config.background
        web.scrollView.backgroundColor = config.background
        // Sayfa güvenli alan boşluklarını kendisi veriyor (viewport-fit=cover + env(safe-area-inset-*))
        web.scrollView.contentInsetAdjustmentBehavior = .never
        web.scrollView.bounces = false
        web.allowsLinkPreview = false
        if #available(iOS 16.4, *) { web.isInspectable = false }
        view = web
        view.backgroundColor = config.background
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        // Oyun açılırken akşam hatırlatması için bildirim izni bir kez sorulur
        if !askedPermission {
            askedPermission = true
            afiyet?.askPermission()
        }
    }

    // Alçak ekranlarda (ör. yatay iPhone) oyun, tasarlandığı yüksekliğe sığacak kadar uzaklaştırılır.
    // pageZoom kullanılmıyor: sayfayı küçültür ama genişliği büyütmez, sağda boş şerit kalıyordu.
    // Bunun yerine sayfanın viewport ölçeği ayarlanır (genişlik = ekran / ölçek), oyun ekranı tam doldurur.
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        guard config.fitHeight > 0, view.bounds.height > 0 else { return }
        let usable = view.bounds.height - view.safeAreaInsets.top - view.safeAreaInsets.bottom
        var zoom = max(0.7, min(1, usable / config.fitHeight))
        zoom = (zoom * 1000).rounded(.down) / 1000
        if abs(fit.zoom - zoom) > 0.005 || abs(fit.width - view.bounds.width) > 0.5 {
            fit = (zoom, view.bounds.width)
            sendFit()
        }
    }

    private func sendFit() {
        guard config.fitHeight > 0, fit.width > 0 else { return }
        call("window.__shellFit", [Double(fit.zoom), Double(fit.width)])
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        afiyet = ShellAfiyet(web: web)
        web.load(URLRequest(url: URL(string: "\(SiteScheme.scheme)://\(SiteScheme.host)/\(config.startPage)")!))
    }

    private func userScripts() -> [String] {
        // Sayfa hataları cihaz günlüğüne yazılır ("[kabuk]" ile başlar); sorun ararken işe yarar
        var list: [String] = ["""
        (function () {
          function send(k, a) { try { webkit.messageHandlers.shell.postMessage({ op: 'log', text: k + ': ' + Array.prototype.map.call(a, String).join(' ') }); } catch (e) {} }
          window.addEventListener('error', function (e) { send('hata', [e.message, (e.filename || '') + ':' + (e.lineno || '')]); });
          window.addEventListener('unhandledrejection', function (e) { send('söz', [e.reason]); });
          document.addEventListener('securitypolicyviolation', function (e) { send('csp', [e.violatedDirective, e.blockedURI]); });
          var ce = console.error; console.error = function () { send('console', arguments); return ce.apply(console, arguments); };
          window.addEventListener('DOMContentLoaded', function () { send('hazır', [location.href, document.title]); });
        })();
        """]
        // Sayfa iki parmakla büyümesin: oyun dokunuşları kendisi işler.
        // Alçak ekranda sığdırma da buradan yapılır: uygulama ölçeği window.__shellFit ile bildirir.
        list.append("""
        (function () {
          var fit = null;
          function apply() {
            var m = document.querySelector('meta[name=viewport]');
            if (!m) { if (!document.head) return; m = document.createElement('meta'); m.name = 'viewport'; document.head.appendChild(m); }
            if (!m.getAttribute('data-shell')) m.setAttribute('data-shell', m.content || 'width=device-width,initial-scale=1,viewport-fit=cover');
            var c = m.getAttribute('data-shell')
              .split(',').map(function (s) { return s.trim(); })
              .filter(function (s) { return s && !/^(maximum-scale|minimum-scale|user-scalable)/.test(s); });
            if (fit && fit[0] < 1) {
              var z = fit[0];
              c = c.filter(function (s) { return !/^(width|initial-scale)/.test(s); });
              m.content = c.concat(['width=' + Math.round(fit[1] / z), 'initial-scale=' + z, 'maximum-scale=' + z, 'minimum-scale=' + z, 'user-scalable=no']).join(',');
            } else {
              m.content = c.concat(['maximum-scale=1', 'minimum-scale=1', 'user-scalable=no']).join(',');
            }
          }
          window.__shellFit = function (z, w) { fit = [z, w]; if (document.readyState !== 'loading') apply(); };
          document.addEventListener('DOMContentLoaded', apply);
          try { webkit.messageHandlers.shell.postMessage({ op: 'fit' }); } catch (e) {}
        })();
        """)
        list.append(ShellAfiyet.script)
        return list
    }

    // MARK: - Sayfa ve bağlantılar

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        NSLog("[kabuk] yüklenemedi: %@", error.localizedDescription)
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        NSLog("[kabuk] sayfa hatası: %@", error.localizedDescription)
    }

    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        NSLog("[kabuk] sayfa motoru kapandı, yeniden açılıyor")
        // Sayfa motoru kapanırsa (bellek vb.) sayfayı yeniden aç; kayıtlar korunur
        webView.reload()
    }

    func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction,
                 decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        guard let url = action.request.url, let scheme = url.scheme?.lowercased() else { return decisionHandler(.cancel) }
        if scheme == SiteScheme.scheme || ["about", "data", "blob", "javascript"].contains(scheme) {
            return decisionHandler(.allow)
        }
        // Web ve e-posta bağlantıları telefonun kendi uygulamalarında açılır
        if action.targetFrame == nil || action.targetFrame?.isMainFrame == true {
            UIApplication.shared.open(url)
        }
        decisionHandler(.cancel)
    }

    func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration,
                 for action: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
        if let url = action.request.url {
            if url.scheme == SiteScheme.scheme { webView.load(action.request) } else { UIApplication.shared.open(url) }
        }
        return nil
    }

    // alert / confirm / prompt pencereleri
    func webView(_ webView: WKWebView, runJavaScriptAlertPanelWithMessage message: String,
                 initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping () -> Void) {
        let a = UIAlertController(title: nil, message: message, preferredStyle: .alert)
        a.addAction(UIAlertAction(title: "Tamam", style: .default) { _ in completionHandler() })
        presentAlert(a, otherwise: completionHandler)
    }

    func webView(_ webView: WKWebView, runJavaScriptConfirmPanelWithMessage message: String,
                 initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping (Bool) -> Void) {
        let a = UIAlertController(title: nil, message: message, preferredStyle: .alert)
        a.addAction(UIAlertAction(title: "Vazgeç", style: .cancel) { _ in completionHandler(false) })
        a.addAction(UIAlertAction(title: "Tamam", style: .default) { _ in completionHandler(true) })
        presentAlert(a) { completionHandler(false) }
    }

    func webView(_ webView: WKWebView, runJavaScriptTextInputPanelWithPrompt prompt: String, defaultText: String?,
                 initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping (String?) -> Void) {
        let a = UIAlertController(title: nil, message: prompt, preferredStyle: .alert)
        a.addTextField { $0.text = defaultText }
        a.addAction(UIAlertAction(title: "Vazgeç", style: .cancel) { _ in completionHandler(nil) })
        a.addAction(UIAlertAction(title: "Tamam", style: .default) { _ in completionHandler(a.textFields?.first?.text) })
        presentAlert(a) { completionHandler(nil) }
    }

    private func presentAlert(_ a: UIAlertController, otherwise: @escaping () -> Void) {
        guard presentedViewController == nil else { return otherwise() }
        present(a, animated: true)
    }

    // MARK: - Köprü

    func userContentController(_ controller: WKUserContentController, didReceive message: WKScriptMessage) {
        guard let body = message.body as? [String: Any], let op = body["op"] as? String else { return }
        if let a = afiyet, a.handle(op, body) { return }
        switch op {
        case "log":
            NSLog("[kabuk] %@", body["text"] as? String ?? "")
        case "fit":
            sendFit()
        case "orient":
            setOrientation(body["mode"] as? String ?? "")
        default:
            break
        }
    }

    private func setOrientation(_ mode: String) {
        let mask: UIInterfaceOrientationMask
        switch mode {
        case "portrait": mask = .portrait
        case "landscape": mask = .landscape
        default: mask = .allButUpsideDown
        }
        guard Self.orientationOverride != mask else { return }
        Self.orientationOverride = mask
        if #available(iOS 16.0, *) {
            setNeedsUpdateOfSupportedInterfaceOrientations()
            view.window?.windowScene?.requestGeometryUpdate(.iOS(interfaceOrientations: mask)) { _ in }
        } else {
            UIViewController.attemptRotationToDeviceOrientation()
        }
    }

    func call(_ function: String, _ args: [Any]) {
        guard let json = try? JSONSerialization.data(withJSONObject: args),
              let list = String(data: json, encoding: .utf8) else { return }
        web.evaluateJavaScript("\(function) && \(function).apply(null, \(list))", completionHandler: nil)
    }
}

/// Betik işleyicisi güçlü tutulur; döngü olmasın diye araya zayıf bir aracı konur.
private final class WeakHandler: NSObject, WKScriptMessageHandler {
    weak var target: WKScriptMessageHandler?
    init(_ target: WKScriptMessageHandler) { self.target = target }
    func userContentController(_ c: WKUserContentController, didReceive m: WKScriptMessage) {
        target?.userContentController(c, didReceive: m)
    }
}
