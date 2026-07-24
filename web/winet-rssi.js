/*!
 * winet-rssi.js — Captura y clasificación de RSSI para W-NET TELECOM.
 * Funciona SOLO dentro de la app "W-NET Señal" (puente window.WinetWifi).
 * En un navegador normal reporta { available:false } y no rompe el speedtest.
 */
(function (global) {
  'use strict';

  var BRIDGE = 'WinetWifi';

  // Clasificación OFICIAL W-NET (dBm)
  function classify(rssi) {
    if (rssi == null || rssi === 0 || rssi <= -127) {
      return { quality: 'na', label: 'Sin datos', color: '#9E9E9E' };
    }
    if (rssi >= -50) return { quality: 'optima', label: 'Óptima', color: '#0F7B0F' };
    if (rssi >= -60) return { quality: 'buena',  label: 'Buena',  color: '#7CB518' };
    if (rssi >= -77) return { quality: 'baja',   label: 'Baja',   color: '#E6B800' };
    if (rssi >= -84) return { quality: 'debil',  label: 'Débil',  color: '#E8720C' };
    return { quality: 'fuera', label: 'Fuera de cobertura', color: '#D32F2F' };
  }

  function hasBridge() {
    return typeof global[BRIDGE] !== 'undefined' && !!global[BRIDGE];
  }

  function readNative() {
    if (!hasBridge()) return { available: false, rssi: null, band: '', frequencyMhz: 0 };
    try {
      var d = JSON.parse(global[BRIDGE].getInfoJson());
      return {
        available: !!d.available,
        rssi: d.rssi,
        band: d.band || '',
        frequencyMhz: d.frequencyMhz || 0,
        // Diagnóstico: de qué fuente salió el dBm y qué reporta cada una.
        source: d.source || '',
        rssiScan: typeof d.rssiScan === 'number' ? d.rssiScan : null,
        rssiLink: typeof d.rssiLink === 'number' ? d.rssiLink : null,
        rssiCaps: typeof d.rssiCaps === 'number' ? d.rssiCaps : null,
        scanAgeMs: typeof d.scanAgeMs === 'number' ? d.scanAgeMs : -1,
        bssid: d.bssid || '',
        linkSpeedMbps: typeof d.linkSpeedMbps === 'number' ? d.linkSpeedMbps : -1
      };
    } catch (e) {
      return { available: false, rssi: null, band: '', frequencyMhz: 0 };
    }
  }

  function getWifiSignal() {
    var info = readNative();
    var q = classify(info.available ? info.rssi : null);
    return {
      available: info.available,
      rssi: info.available ? info.rssi : null,
      band: info.band,
      frequencyMhz: info.frequencyMhz,
      quality: q.quality,
      label: q.label,
      color: q.color,
      source: info.source || '',
      rssiScan: info.rssiScan == null ? null : info.rssiScan,
      rssiLink: info.rssiLink == null ? null : info.rssiLink,
      rssiCaps: info.rssiCaps == null ? null : info.rssiCaps,
      scanAgeMs: info.scanAgeMs == null ? -1 : info.scanAgeMs,
      bssid: info.bssid || '',
      linkSpeedMbps: info.linkSpeedMbps == null ? -1 : info.linkSpeedMbps
    };
  }

  var timer = null;
  var widgetEl = null;

  function dispatch(signal) {
    var ev;
    try {
      ev = new CustomEvent('winet:rssi', { detail: signal });
    } catch (e) {
      ev = document.createEvent('CustomEvent');
      ev.initCustomEvent('winet:rssi', false, false, signal);
    }
    document.dispatchEvent(ev);
  }

  function tick() {
    var s = getWifiSignal();
    dispatch(s);
    if (widgetEl) renderWidget(widgetEl, s);
  }

  function start(intervalMs) {
    stop();
    tick();
    timer = setInterval(tick, intervalMs || 1000);
  }

  function stop() {
    if (timer) { clearInterval(timer); timer = null; }
  }

  function mount(selector) {
    widgetEl = typeof selector === 'string' ? document.querySelector(selector) : selector;
    if (widgetEl) renderWidget(widgetEl, getWifiSignal());
    return widgetEl;
  }

  function renderWidget(el, s) {
    if (!s.available) {
      el.innerHTML =
        '<div style="font:14px sans-serif;padding:12px;border-radius:10px;' +
        'background:#f2f2f2;color:#555;text-align:center">' +
        '📶 Abre desde la app <b>W-NET Señal</b> para medir la señal</div>';
      return;
    }
    el.innerHTML =
      '<div style="font-family:sans-serif;padding:14px;border-radius:12px;color:#fff;' +
      'text-align:center;background:' + s.color + '">' +
        '<div style="font-size:40px;font-weight:700;line-height:1">' +
          s.rssi + ' <span style="font-size:16px">dBm</span></div>' +
        '<div style="font-size:16px;margin-top:4px">' + s.label + '</div>' +
        (s.band ? '<div style="font-size:12px;opacity:.9;margin-top:2px">' + s.band + '</div>' : '') +
      '</div>';
  }

  var api = {
    classify: classify,
    getWifiSignal: getWifiSignal,
    hasBridge: hasBridge,
    start: start,
    stop: stop,
    mount: mount
  };

  global.WinetRSSI = api;
  global.getWifiSignal = getWifiSignal;

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = api; // pruebas en Node
  } else if (typeof document !== 'undefined') {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', function () { start(1000); });
    } else {
      start(1000);
    }
  }
})(typeof window !== 'undefined' ? window : this);
