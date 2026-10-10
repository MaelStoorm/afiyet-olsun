// iOS simülatöründe oyunu adım adım gezen test sürücüsü (yalnızca deneme dalında, uygulamaya girmez)
(function () {
  var T0 = Date.now();
  function log() {
    var t = Array.prototype.map.call(arguments, function (a) { return typeof a === 'string' ? a : JSON.stringify(a); }).join(' ');
    t = '[test ' + ((Date.now() - T0) / 1000).toFixed(1) + 's] ' + t;
    try { webkit.messageHandlers.shell.postMessage({ op: 'log', text: t }); } catch (e) { console.log(t); }
  }
  var $ = function (s) { return document.querySelector(s); };
  function click(s) { var e = typeof s === 'string' ? $(s) : s; if (e) { e.click(); return true; } log('YOK', s); return false; }
  function vis(s) { var e = $(s); if (!e || e.hidden) return false; var r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; }
  function info(tag) {
    var p = document.createElement('div');
    p.style.cssText = 'position:fixed;left:0;top:0;padding:env(safe-area-inset-top) env(safe-area-inset-right) env(safe-area-inset-bottom) env(safe-area-inset-left);visibility:hidden';
    document.body.appendChild(p); var cs = getComputedStyle(p);
    var safe = [cs.paddingTop, cs.paddingRight, cs.paddingBottom, cs.paddingLeft].join(' ');
    p.remove();
    var m = $('meta[name=viewport]');
    log(tag, { w: innerWidth, h: innerHeight, cw: document.documentElement.clientWidth, sw: document.documentElement.scrollWidth,
      vv: window.visualViewport ? [visualViewport.width, visualViewport.height, visualViewport.scale] : null,
      dpr: devicePixelRatio, safe: safe, meta: m && m.content, bridge: window.AfiyetAndroid ? Object.keys(window.AfiyetAndroid) : null,
      hidden: document.hidden });
  }
  var stage = sessionStorage.getItem('tstage') || 'A';
  var A = [
    function () { info('baslik'); },
    function () { click('#setBtn'); setTimeout(function () {
      log('ayarlar', { notif: !$('#sNotifRow').hidden, vib: !$('#sVibRow').hidden, ori: !$('#sOriRow').hidden, set: localStorage.getItem('afiyet-olsun-set') });
    }, 400); },
    function () { click('#sVib'); click('#sVib'); click('#setClose'); click('#newBtn'); },
    function () {
      var w = 0;
      (function pf() {
        if (!vis('#profScr')) { if (w++ < 8) return setTimeout(pf, 400); log('profil ekranı yok'); return; }
        $('#pfName').value = 'Egemen'; click('#pfG button[data-v="e"]'); click('#pfOk');
      })();
    },
    function () { click('#dayBanner'); info('sabah1'); },
    function () { click('#openShopBtn'); setTimeout(function () { click('#dayBanner'); info('dukkan'); }, 1500); },
    function () { var w = 0; (function tk() { if (vis('#takeBtn')) { click('#takeBtn'); log('siparis alindi'); } else if (w++ < 10) setTimeout(tk, 500); else log('takeBtn gelmedi'); })(); },
    function () { var d = Array.prototype.find.call(document.querySelectorAll('.dough'), function (e) { return /Lahmacun/i.test(e.textContent); }); click(d); info('mutfak'); },
    function () { click('#bookBtn'); },
    function () {
      click('#bookClose');
      var s = JSON.parse(localStorage.getItem('afiyet-olsun-v1') || 'null');
      if (!s) { log('kayit yok'); return; }
      var d = new Date(), today = d.getFullYear() + '-' + (d.getMonth() + 1) + '-' + d.getDate();
      Object.assign(s, { login: { last: today, streak: 3 }, phase: 'morning', tut: true, day: 6, dayServed: 0, time: 9 * 60,
        ev: { rival1: true, rival2: true, morning1: true, teyze1: true, teyze2: true }, learned: { kurabiye: true, recel: true },
        money: 1840, mkList: { havuc: 3, yumurta: 1, peynir: 2 } });
      ['domates', 'biber', 'tofu', 'zeytinyagi', 'sogan', 'yumurta', 'tereyagi', 'bulgur', 'salca', 'yesilsogan', 'maydanoz', 'nane', 'nar',
        'pulbiber', 'kasar', 'sucuk', 'kiyma', 'pirinc', 'limon', 'mantar', 'sivribiber'].forEach(function (k) { s.inv[k] = 30; });
      localStorage.setItem('afiyet-olsun-v1', JSON.stringify(s));
      sessionStorage.setItem('tstage', 'B');
      log('kayit hazir, yeniden yukleniyor');
      location.reload();
    },
  ];
  var B = [
    function () { click('#contBtn'); setTimeout(function () { click('#dayBanner'); var r = $('#rewardScr'); if (r && !r.hidden) click('#rwTake'); }, 600); },
    function () { info('sabah'); var b = document.querySelector('.m-card button'); click(b); },
    function () { info('market'); },
    function () { click('#mkClose'); click('#openShopBtn'); },
    function () { click('#dayBanner'); info('dukkan6'); },
    function () { log('dikey'); window.AfiyetAndroid && AfiyetAndroid.setOrientation('portrait'); },
    function () { info('dikey'); },
    function () { log('yatay'); window.AfiyetAndroid && AfiyetAndroid.setOrientation('landscape'); },
    function () { info('yatay'); log('saat', $('#hClock').textContent); },
    function () { log('saat', $('#hClock').textContent, 'arka plana hazir'); },
    // uygulama arka plana gider ve geri gelir; sonra saat ilerliyor mu
    function () { log('saat', $('#hClock').textContent, document.hidden); },
    function () { log('saat', $('#hClock').textContent, document.hidden); info('donus'); },
    function () { log('saat', $('#hClock').textContent, document.hidden); },
  ];
  var steps = stage === 'A' ? A : B;
  var i = 0;
  function next() {
    if (i >= steps.length) { log('bitti', stage); return; }
    try { steps[i](); } catch (e) { log('HATA adim', stage + i, String(e)); }
    i++;
    setTimeout(next, 5000);
  }
  window.addEventListener('error', function (e) { log('SAYFA HATASI', e.message); });
  if (document.readyState === 'complete') setTimeout(next, 2500); else window.addEventListener('load', function () { setTimeout(next, 2500); });
})();
