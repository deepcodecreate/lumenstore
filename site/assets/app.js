/* Lumen Store — website. Plain ES2020. Reads the Lumen index (deepcodecreate/lumenrepo) directly. */
(() => {
"use strict";

/* ───────────── Config (kept in sync with the Android app) ───────────── */
const CFG = {
  site: "https://deepcodecreate.github.io/lumenstore/",
  appRepo: "deepcodecreate/lumenstore",            // where the Lumen Store APK is released
  scheme: "lumenstore",                            // lumenstore://app/<package>  (LumenLinks.java)
  telegram: "lumenapps",                              // DM used for app submissions
  channel: "deepcodecreate",                       // announcement channel
  cacheHours: 12,
  proxy: "",   // live CORS pass-through (tools/cors-worker.js), e.g. "https://lumen-proxy.YOURNAME.workers.dev/". F-Droid / Izzy send no CORS headers, so browsers need it.
};
/* Tells "server answered but the browser blocked it (CORS)" apart from "server unreachable / blocked network". */
async function probe(url) {
  const ctl = new AbortController(), t = setTimeout(() => ctl.abort(), 8000);
  try { await fetch(url, { method: "HEAD", mode: "no-cors", cache: "no-store", signal: ctl.signal }); return " → server reachable, browser blocked it (CORS)"; }
  catch { return " → server unreachable (network / DNS / blocked)"; }
  finally { clearTimeout(t); }
}
const EXT = /^https?:\/\//i;
const viaProxy = (u) => (CFG.proxy && EXT.test(u) && !u.startsWith(CFG.proxy) ? `${CFG.proxy.replace(/\/?$/, "/")}?url=${encodeURIComponent(u)}` : u);
const REPOS = [
  { id: "lumen",  label: "Lumen",       short: "Lu",
    sources: [{ kind: "v2", url: "https://raw.githubusercontent.com/deepcodecreate/lumenrepo/main/index-v2.json" },
               { kind: "v2", url: "data/lumen-index-v2.json", base: "https://raw.githubusercontent.com/deepcodecreate/lumenrepo/main", snap: true }] },
];
const TILE_COLORS = ["#4fd1c5", "#a8cbe4", "#d9a17c", "#b1ccc6", "#9cf6e1", "#e6b8a2", "#8fd0e8", "#c8e0b0"];
const CAT_FA = { "Connectivity": "اتصال", "Development": "توسعه", "Games": "بازی", "Graphics": "گرافیک", "Internet": "اینترنت", "Money": "مالی", "Multimedia": "چندرسانه‌ای", "Navigation": "نقشه و سفر", "Phone & SMS": "تماس و پیامک", "Reading": "مطالعه", "Science & Education": "علم و آموزش", "Security": "امنیت", "Sports & Health": "ورزش و سلامت", "System": "سیستم", "Theming": "ظاهر", "Time": "زمان", "Writing": "نوشتن" };
const catName = (c) => CAT_FA[c] || c;

/* ───────────── Helpers ───────────── */
const $ = (s, r = document) => r.querySelector(s);
const fa = (n) => (Number.isFinite(+n) ? (+n).toLocaleString("fa-IR") : "—");
const PKG_RE = /^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$/;
const safeUrl = (u) => { try { const x = new URL(u, location.href); return /^https?:$/.test(x.protocol) ? x.href : null; } catch { return null; } };
const siteLink = (pkg) => `${CFG.site}app.html?id=${encodeURIComponent(pkg)}`;
const deepLink = (pkg) => `${CFG.scheme}://app/${encodeURIComponent(pkg)}`;
const initials = (name) => {
  const w = String(name).replace(/[^\p{L}\p{N} .\-_]/gu, "").split(/[ .\-_]+/).filter(Boolean);
  return ((w[0]?.[0] || "?") + (w[1]?.[0] || w[0]?.[1] || "")).toUpperCase();
};
const tileColor = (key) => { let h = 0; for (const c of key) h = (h * 31 + c.charCodeAt(0)) >>> 0; return TILE_COLORS[h % TILE_COLORS.length]; };
const fmtSize = (b) => (b > 0 ? (b >= 1048576 ? `${(b / 1048576).toLocaleString("fa-IR", { maximumFractionDigits: 1 })} مگابایت` : `${fa(Math.round(b / 1024))} کیلوبایت`) : null);
const fmtDate = (ms) => { if (!ms) return null; try { return new Intl.DateTimeFormat("fa-IR-u-ca-persian", { year: "numeric", month: "long", day: "numeric" }).format(new Date(ms)); } catch { return null; } };
const fmtShort = (ms) => { if (!ms) return null; try { return new Intl.DateTimeFormat("fa-IR-u-ca-persian", { year: "2-digit", month: "short", day: "numeric" }).format(new Date(ms)); } catch { return null; } };

function h(tag, attrs, ...kids) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v == null || v === false) continue;
    if (k === "class") el.className = v;
    else if (k.startsWith("on")) el.addEventListener(k.slice(2), v);
    else el.setAttribute(k, v === true ? "" : v);
  }
  for (const k of kids.flat()) if (k != null && k !== false) el.append(k.nodeType ? k : document.createTextNode(k));
  return el;
}
const svgIcon = (d) => { const s = document.createElementNS("http://www.w3.org/2000/svg", "svg"); for (const [k, v] of [["viewBox", "0 0 24 24"], ["fill", "none"], ["stroke", "currentColor"], ["stroke-width", "2"], ["stroke-linecap", "round"], ["stroke-linejoin", "round"], ["aria-hidden", "true"]]) s.setAttribute(k, v); s.innerHTML = d; return s; };
const I = {
  down: () => svgIcon('<path d="M12 4v11m-5-4 5 5 5-5M5 20h14"/>'),
  phone: () => svgIcon('<rect x="7" y="2.5" width="10" height="19" rx="2.5"/><path d="M11 18.5h2"/>'),
  copy: () => svgIcon('<rect x="9" y="9" width="11" height="11" rx="2.5"/><path d="M5 15V6.5A2.5 2.5 0 0 1 7.5 4H15"/>'),
  share: () => svgIcon('<circle cx="18" cy="5.5" r="2.5"/><circle cx="6" cy="12" r="2.5"/><circle cx="18" cy="18.5" r="2.5"/><path d="m8.2 10.8 7.6-4m-7.6 6.4 7.6 4"/>'),
  ext: () => svgIcon('<path d="M14 4h6v6m0-6-9 9M18 14v4.5A1.5 1.5 0 0 1 16.5 20h-11A1.5 1.5 0 0 1 4 18.5v-11A1.5 1.5 0 0 1 5.5 6H10"/>'),
  check: () => svgIcon('<path d="m5 12.5 4.5 4.5L19 7.5"/>'),
  send: () => svgIcon('<path d="m21 3-9.5 18-2.6-7.9L1 10.5z"/><path d="m8.9 13.1 4.6-4.6"/>'),
  refresh: () => svgIcon('<path d="M20 11a8 8 0 1 0-2.3 5.7M20 4v7h-7"/>'),
};
const DIAMOND = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 2.5 19 12l-7 9.5L5 12z" fill="#F6F3EC"/></svg>';

let toastTimer;
function toast(msg) {
  let t = $(".toast");
  if (!t) { t = h("div", { class: "toast", role: "status", "aria-live": "polite" }); document.body.append(t); }
  t.textContent = msg; t.classList.add("show");
  clearTimeout(toastTimer); toastTimer = setTimeout(() => t.classList.remove("show"), 2800);
}
async function copyText(text) {
  try {
    if (navigator.clipboard && window.isSecureContext) await navigator.clipboard.writeText(text);
    else { const t = h("textarea", { style: "position:fixed;opacity:0" }); t.value = text; document.body.append(t); t.select(); document.execCommand("copy"); t.remove(); }
    return true;
  } catch { return false; }
}
const copyToast = async (text, what) => toast((await copyText(text)) ? `${what} کپی شد` : "کپی انجام نشد");

/* App tile: coloured initials; the real icon fades in on top (and falls through a list of candidates). */
function tile(name, pkg, iconUrls) {
  const el = h("span", { class: "tile", style: `--tile:${tileColor(pkg)}`, "aria-hidden": "true" }, initials(name));
  const list = (iconUrls || []).filter(Boolean).map(safeUrl).filter(Boolean);
  const tryNext = () => {
    const src = list.shift(); if (!src) return;
    const img = h("img", { alt: "", loading: "lazy", decoding: "async", referrerpolicy: "no-referrer" });
    img.onload = () => img.classList.add("on");
    img.onerror = () => { img.remove(); tryNext(); };
    el.append(img); img.src = src;
  };
  tryNext();
  return el;
}

/* Code viewer: header (language tabs + copy) over a wrapped, lightly highlighted body. */
function highlight(code, lang) {
  const out = document.createDocumentFragment();
  const rules = lang === "html"
    ? [[/&lt;|<\/?[a-z]+/i, "t"], [/[a-z-]+(?==")/i, "a"], [/"[^"]*"/, "s"]]
    : [[/!?\[[^\]]*\]/, "t"], [/\(https?:[^)]*\)/, "s"]];
  if (lang === "link" || lang === "plain") { out.append(h("span", { class: "s" }, code)); return out; }
  const re = new RegExp(rules.map((r) => `(${r[0].source})`).join("|"), "gi");
  let last = 0, m;
  while ((m = re.exec(code))) {
    if (m.index > last) out.append(code.slice(last, m.index));
    const idx = m.slice(1).findIndex((x) => x != null);
    out.append(h("span", { class: rules[idx][1] }, m[0]));
    last = m.index + m[0].length;
    if (m[0].length === 0) re.lastIndex++;
  }
  if (last < code.length) out.append(code.slice(last));
  return out;
}
function codeViewer(tabs, getText, opts = {}) {
  /* tabs: [[key, label, lang]] ; getText(key) → string */
  let key = tabs[0][0];
  const body = h("pre", { class: "cv-body" + (opts.rtl ? " rtl" : ""), dir: opts.rtl ? "rtl" : "ltr", tabindex: "0" });
  const label = h("span", { class: "cv-lang" });
  const copy = h("button", { class: "cv-copy", type: "button", "aria-label": "کپی" });
  const bar = h("div", { class: "cv-bar" });
  const root = h("div", { class: "cv" }, bar, body);
  const paint = () => {
    const [, lab, lang] = tabs.find((t) => t[0] === key);
    const text = getText(key);
    body.replaceChildren(highlight(text, lang));
    label.textContent = lab;
    bar.replaceChildren(
      tabs.length > 1 ? h("div", { class: "cv-tabs", role: "tablist" }, tabs.map(([k, l]) => h("button", { class: "cv-tab", type: "button", role: "tab", "aria-selected": String(k === key), onclick: () => { key = k; paint(); } }, l))) : label,
      copy);
    copy.replaceChildren(I.copy(), h("span", {}, "کپی"));
    copy.onclick = async () => { const ok = await copyText(getText(key)); copy.replaceChildren(I.check(), h("span", {}, ok ? "کپی شد" : "ناموفق")); copy.classList.add("done"); setTimeout(() => { copy.classList.remove("done"); copy.replaceChildren(I.copy(), h("span", {}, "کپی")); }, 1600); };
  };
  root.refresh = paint; paint();
  return root;
}

/* ───────────── Storage (IndexedDB, best-effort) ───────────── */
const DB = (() => {
  let dbp;
  const open = () => dbp || (dbp = new Promise((res) => {
    try {
      const rq = indexedDB.open("lumen-site", 1);
      rq.onupgradeneeded = () => { rq.result.createObjectStore("kv"); rq.result.createObjectStore("apps"); };
      rq.onsuccess = () => res(rq.result); rq.onerror = () => res(null); rq.onblocked = () => res(null);
    } catch { res(null); }
  }));
  const run = async (store, mode, fn) => {
    const db = await open(); if (!db) return null;
    return new Promise((res) => {
      try { const tx = db.transaction(store, mode); const r = fn(tx.objectStore(store)); tx.oncomplete = () => res(r && "result" in r ? r.result : true); tx.onerror = tx.onabort = () => res(null); } catch { res(null); }
    });
  };
  return {
    get: (store, k) => run(store, "readonly", (s) => s.get(k)),
    put: (store, k, v) => run(store, "readwrite", (s) => s.put(v, k)),
    putMany: async (store, entries) => { for (let i = 0; i < entries.length; i += 400) { if (!(await run(store, "readwrite", (s) => { for (const [k, v] of entries.slice(i, i + 400)) s.put(v, k); }))) return false; } return true; },
    clearApps: (prefix) => run("apps", "readwrite", (s) => { const rq = s.openKeyCursor(IDBKeyRange.bound(prefix, prefix + "￿")); rq.onsuccess = () => { const c = rq.result; if (c) { s.delete(c.primaryKey); c.continue(); } }; return null; }),
  };
})();

/* ───────────── Index parsing ───────────── */
const LOCALES = ["fa", "fa-IR", "en-US", "en", "en-GB"];
const pickLoc = (v) => {
  if (v == null) return null; if (typeof v === "string") return v; if (typeof v !== "object") return null;
  for (const k of LOCALES) if (typeof v[k] === "string" && v[k].trim()) return v[k];
  for (const x of Object.values(v)) if (typeof x === "string" && x.trim()) return x;
  return null;
};
const pickLocFile = (v) => {
  if (!v) return null; if (typeof v === "string") return v;
  const f = (x) => (x && typeof x === "object" ? x.name : typeof x === "string" ? x : null);
  for (const k of LOCALES) if (v[k]) return f(v[k]);
  for (const x of Object.values(v)) if (f(x)) return f(x);
  return null;
};
const GH_RE = /github\.com\/([^/\s]+)\/([^/#?\s]+)/i;

/* Lumen: index-v2 (small, parsed in one go) */
function parseV2(json, indexUrl) {
  const base = indexUrl.replace(/[?#].*$/, "").replace(/\/[^/]*$/, "");
  const res = (n) => (!n ? null : /^https?:/.test(n) ? n : base + (n.startsWith("/") ? "" : "/") + n);
  const cats = {};
  for (const [id, c] of Object.entries(json?.repo?.categories || {})) cats[id] = pickLoc(c?.name) || id;
  const out = new Map();
  for (const [pkg, p] of Object.entries(json?.packages || {})) {
    const m = p.metadata || {};
    const versions = [];
    for (const v of Object.values(p.versions || {})) {
      const mf = v.manifest || {}, f = v.file || {}, code = Number(mf.versionCode);
      if (!f.name || !Number.isFinite(code)) continue;
      versions.push({ name: mf.versionName || String(code), code, size: Number(f.size) || 0, url: res(f.name), hash: f.sha256 || null,
        arch: (mf.nativecode || []).join(", ") || null, perms: [...(mf.usesPermission || []), ...(mf.usesPermissionSdk23 || [])].map((x) => x.name).filter(Boolean),
        anti: Object.keys(v.antiFeatures || {}), pre: (v.releaseChannels || []).length > 0 });
    }
    versions.sort((a, b) => b.code - a.code);
    const stable = versions.filter((v) => !v.pre);
    if (stable.length && stable[0] !== versions[0]) versions.unshift(...versions.splice(versions.indexOf(stable[0]), 1));
    versions.forEach((v, i) => { if (i) { v.perms = []; v.anti = []; } });
    const ghEarly = [m.releases, m.sourceCode, m.webSite, m.changelog, m.issueTracker].some((u) => GH_RE.test(u || ""));
    if (!versions.length && !ghEarly) continue;
    let shots = [];
    const sc = m.screenshots;
    if (sc) {
      let arr = sc.phone || sc.phoneScreenshots || (Array.isArray(sc) ? sc : null);
      if (arr && !Array.isArray(arr)) { arr = null; const ph = sc.phone || sc.phoneScreenshots; for (const k of [...LOCALES, ...Object.keys(ph || {})]) if (Array.isArray(ph?.[k])) { arr = ph[k]; break; } }
      shots = (arr || []).map((s) => res(typeof s === "string" ? s : s?.name)).filter(Boolean);
    }
    const gh = [m.releases, m.sourceCode, m.webSite, m.changelog, m.issueTracker].map((u) => GH_RE.exec(u || "")).find(Boolean);
    out.set(pkg, { pkg, src: "lumen", name: pickLoc(m.name) || pkg, summary: pickLoc(m.summary), description: pickLoc(m.description), icon: res(pickLocFile(m.icon)),
      shots, cats: (m.categories || []).map((c) => cats[c] || c), license: m.license || null, source: m.sourceCode || null, website: m.webSite || null, issues: m.issueTracker || null,
      changelog: m.changelog || null, donate: Array.isArray(m.donate) ? m.donate[0] : m.donate || null, author: m.authorName || null,
      added: Number(m.added) || 0, updated: Number(m.lastUpdated) || 0, versions, ghSlug: gh ? `${gh[1]}/${gh[2].replace(/\.git$/i, "")}` : null });
  }
  return out;
}

/* Network guard: a source that never answers, or stalls mid-download, is aborted so it can fail on its own without blocking the others. */
const yieldMain = () => (window.scheduler && scheduler.yield ? scheduler.yield() : new Promise((r) => { const c = new MessageChannel(); c.port1.onmessage = () => { c.port1.close(); r(); }; c.port2.postMessage(0); }));
function fetchGuard(url, { connectMs = 25000, stallMs = 30000 } = {}) {
  const ctl = new AbortController(); let timer = setTimeout(() => ctl.abort(), connectMs);
  const done = () => clearTimeout(timer), poke = () => { clearTimeout(timer); timer = setTimeout(() => ctl.abort(), stallMs); };
  return fetch(url, { cache: "no-cache", signal: ctl.signal }).then((res) => { poke(); return { res, poke, done }; }, (e) => { done(); throw e; });
}

/* F-Droid / Izzy: index-v1 is tens of MB, so it is parsed as a stream, one app / one package at a time. */
async function streamV1(url, { onApp, onPkg, onBytes }) {
  const g = await fetchGuard(url), res = g.res;
  if (!res.ok) { g.done(); throw new Error("HTTP " + res.status); }
  if (!res.body || !res.body.getReader) { g.done(); throw new Error("no-stream"); }
  const reader = res.body.getReader(), dec = new TextDecoder("utf-8");
  let depth = 0, inStr = false, esc = false, topKey = null, key2 = null, strBuf = "", collect = false, isKey = false;
  const stack = [], ek = [];                       // container type and "next string is a key" per level
  let cap = null, capKind = 0, capKey = null, bytes = 0;
  try { for (;;) {
    const { done, value } = await reader.read(); g.poke();
    const text = done ? dec.decode() : dec.decode(value, { stream: true });
    if (value) { bytes += value.length; onBytes && onBytes(bytes); }
    let capStart = cap ? 0 : -1;
    for (let i = 0, n = text.length; i < n; i++) {
      const c = text.charCodeAt(i);
      if (inStr) {
        if (esc) { esc = false; if (collect) strBuf += text[i]; }
        else if (c === 92) { esc = true; if (collect) strBuf += "\\"; }
        else if (c === 34) { inStr = false; if (isKey) { if (depth === 1) topKey = strBuf; else key2 = strBuf; } collect = false; }
        else if (collect) strBuf += text[i];
        continue;
      }
      switch (c) {
        case 34: {
          inStr = true; isKey = ek[depth - 1] === true && stack[depth - 1] === 123;
          collect = isKey && !cap && (depth === 1 || (depth === 2 && topKey === "packages")); strBuf = ""; break;
        }
        case 123: case 91: {
          depth++; stack[depth - 1] = c; ek[depth - 1] = c === 123;
          if (!cap && depth === 3) {
            if (topKey === "apps" && c === 123) { cap = []; capKind = 1; capStart = i; }
            else if (topKey === "packages" && c === 91) { cap = []; capKind = 2; capKey = key2; capStart = i; }
          }
          break;
        }
        case 125: case 93: {
          if (cap && depth === 3) {
            cap.push(text.slice(capStart, i + 1)); let obj = null;
            try { obj = JSON.parse(cap.join("")); } catch { /* skip malformed entry */ }
            if (obj) { if (capKind === 1) onApp(obj); else onPkg(capKey, obj); }
            cap = null; capStart = -1;
          }
          depth--; break;
        }
        case 58: ek[depth - 1] = false; break;
        case 44: if (stack[depth - 1] === 123) ek[depth - 1] = true; break;
      }
    }
    if (cap) { cap.push(text.slice(Math.max(capStart, 0))); }
    if (done) break;
    await yieldMain();
  } } finally { g.done(); }
}

function v1Record(a, base, id) {
  const pkg = a.packageName, L = a.localized || {};
  const loc = (f) => { for (const k of LOCALES) if (L[k]?.[f]) return [L[k][f], k]; for (const [k, d] of Object.entries(L)) if (d?.[f]) return [d[f], k]; return null; };
  const name = a.name || loc("name")?.[0] || pkg;
  let icon = null;
  if (a.icon) icon = `${base}/icons-640/${a.icon}`;
  else { const r = loc("icon"); if (r) icon = `${base}/${pkg}/${r[1]}/${r[0]}`; }
  const sr = loc("phoneScreenshots");
  return { pkg, src: id, name, summary: a.summary || loc("summary")?.[0] || null, description: a.description || loc("description")?.[0] || null, icon,
    shots: sr ? sr[0].map((s) => `${base}/${pkg}/${sr[1]}/phoneScreenshots/${s}`) : [], cats: a.categories || [], license: a.license || null,
    source: a.sourceCode || null, website: a.webSite || null, issues: a.issueTracker || null, changelog: a.changelog || null,
    donate: a.donate || null, author: a.authorName || null, added: a.added || 0, updated: a.lastUpdated || 0, sug: Number(a.suggestedVersionCode) || 0, versions: [] };
}
function v1Versions(arr, base, sug) {
  const seen = new Set(), vs = [];
  for (const v of [...arr].sort((a, b) => (b.versionCode || 0) - (a.versionCode || 0))) {
    if (v.versionCode == null || !v.apkName || seen.has(v.versionCode)) continue; seen.add(v.versionCode);
    vs.push({ name: v.versionName || String(v.versionCode), code: v.versionCode, size: v.size || 0, url: `${base}/${v.apkName}`, hash: v.hash || null,
      arch: (v.nativecode || []).join(", ") || null, perms: (v["uses-permission"] || []).map((x) => x && x[0]).filter(Boolean), anti: v.antiFeatures || [], pre: false });
  }
  if (sug) vs.sort((a, b) => (b.code === sug) - (a.code === sug) || b.code - a.code);
  vs.forEach((v, i) => { if (i) { v.perms = []; v.anti = []; } });
  return vs.slice(0, 8);
}
const toCard = (r) => [r.pkg, r.name, (r.summary || "").slice(0, 140), r.icon || "", r.cats, r.updated || 0, r.versions[0]?.name || ""];

/* ───────────── Index manager: one shared loader for every page ─────────────
   Every source (Lumen, F-Droid, IzzyOnDroid) loads in parallel and fails on its own: an error, a timeout or a slow mirror in one
   source only changes that source's status chip; the other sources and the catalogue keep working. */
const Indexes = (() => {
  const st = {};                     // id → { status: idle|loading|ready|error, count, mb, cached, error }
  const cards = {};                  // id → Map(pkg → card)
  const mem = { lumen: new Map() };  // full records kept in memory (Lumen always; others only if IndexedDB is unavailable)
  const listeners = new Set();
  let started = false, tick = 0;
  for (const r of REPOS) { st[r.id] = { status: "idle", count: 0, mb: 0, busy: false }; cards[r.id] = new Map(); }
  const emit = (force) => {
    const run = () => listeners.forEach((f) => { try { f(); } catch (e) { console.error(e); } });
    if (force) { clearTimeout(tick); tick = 0; run(); } else if (!tick) tick = setTimeout(() => { tick = 0; run(); }, 350);
  };

  async function readSource(r, src, s, cached) {
    if (r.id === "lumen") {
      let json;
      try { const g = await fetchGuard(src.url); try { if (!g.res.ok) throw new Error("HTTP " + g.res.status); json = await g.res.json(); } finally { g.done(); } DB.put("kv", "raw:lumen", json); }
      catch (e) { json = await DB.get("kv", "raw:lumen"); if (!json) throw e; }     // offline: last copy
      const map = parseV2(json, src.base || src.url);
      mem.lumen = map; cards.lumen = new Map([...map].map(([k, v]) => [k, toCard(v)])); s.count = map.size;
      return;
    }
    const persist = async (recs) => {
      for (const [k, rec] of recs) if (!rec.versions.length) recs.delete(k);
      cards[r.id] = new Map([...recs].map(([k, rec]) => [k, toCard(rec)])); s.count = recs.size;
      const ok = await DB.putMany("apps", [...recs].map(([k, rec]) => [r.id + ":" + k, rec]));
      if (ok) await DB.put("kv", "list:" + r.id, { at: Date.now(), cards: [...cards[r.id].values()] }); else mem[r.id] = recs;
    };
    if (src.kind === "v1") {
      const base = src.base || src.url.replace(/\/index-v1\.json$/, "");
      const recs = new Map(), live = new Map();
      let n = 0;
      await streamV1(src.proxy ? viaProxy(src.url) : src.url, {
        onBytes: (b) => { s.mb = b / 1048576; emit(); },
        onApp: (a) => { if (!a.packageName) return; const rec = v1Record(a, base, r.id); recs.set(rec.pkg, rec); if (!cached) { live.set(rec.pkg, toCard(rec)); if (++n % 300 === 0) { cards[r.id] = live; s.count = live.size; emit(); } } },
        onPkg: (pkg, arr) => { const rec = recs.get(pkg); if (rec) rec.versions = v1Versions(arr, base, rec.sug); },
      });
      await persist(recs);
    } else {                                                  // index-v2 fallback (used only when every v1 URL failed)
      const g = await fetchGuard(src.proxy ? viaProxy(src.url) : src.url, { connectMs: 25000, stallMs: 45000 });
      let json; try { if (!g.res.ok) throw new Error("HTTP " + g.res.status); json = await g.res.json(); } finally { g.done(); }
      const recs = parseV2(json, src.url);
      for (const rec of recs.values()) { rec.src = r.id; rec.versions = rec.versions.slice(0, 8); }
      await persist(recs);
    }
  }

  async function loadRepo(r, force) {
    const s = st[r.id];
    if (s.busy) return; s.busy = true;
    try {
      const cached = force || r.id === "lumen" ? null : await DB.get("kv", "list:" + r.id);
      const fresh = cached && Date.now() - cached.at < CFG.cacheHours * 3600e3;
      if (cached) { cards[r.id] = new Map(cached.cards.map((c) => [c[0], c])); s.count = cards[r.id].size; s.cached = true; s.status = "ready"; emit(true); if (fresh) return; }
      else s.status = "loading";
      s.error = null; emit(true);
      let lastErr; s.errs = [];
      const snaps = r.sources.filter((x) => x.snap), ext = r.sources.filter((x) => !x.snap);
      const order = [...snaps, ...(CFG.proxy ? ext.filter((x, i) => ext.findIndex((y) => y.kind === x.kind) === i).map((x) => ({ ...x, proxy: true })) : []), ...ext];
      let cors = false;
      for (const src of order) {
        if (cors && !src.proxy && !src.snap) continue;          // every direct URL fails the same way once CORS is blocked
        try { await readSource(r, src, s, cached); s.status = "ready"; s.cached = false; s.error = null; s.errs = []; emit(true); return; }
        catch (e) {
          lastErr = e;
          let m = e && e.name === "AbortError" ? "timeout" : e instanceof TypeError ? "Failed to fetch" : String((e && e.message) || e);
          if (e instanceof TypeError && EXT.test(src.url)) m += await probe(src.proxy ? viaProxy(src.url) : src.url);
          let host = src.url; try { host = new URL(src.url).host + new URL(src.url).pathname.replace(/^.*\//, "/"); } catch { /* keep */ }
          if (!src.proxy && !src.snap && /CORS/.test(m)) cors = true;
          s.errs.push(`${src.proxy ? "proxy " : src.snap ? "snapshot " : ""}${src.kind} ${host} → ${m}`); emit(true);
        }
      }
      s.status = cached ? "ready" : "error"; s.error = String((lastErr && lastErr.message) || lastErr || "error"); emit(true);
    } catch (e) {                                              // never let one source's bug escape to the others
      s.status = cards[r.id].size ? "ready" : "error"; s.error = String((e && e.message) || e); emit(true);
    } finally { s.busy = false; }
  }
  return {
    st, cards, mem, REPOS,
    on: (f) => { listeners.add(f); return () => listeners.delete(f); },
    start(force) { if (started && !force) return; started = true; for (const r of REPOS) loadRepo(r, force); },   // all sources in parallel
    retry(id) { return loadRepo(REPOS.find((r) => r.id === id), true); },
    retryAll() { for (const r of REPOS) if (st[r.id].status === "error") loadRepo(r, true); },
    async record(id, pkg) { return mem[id]?.get(pkg) || (id === "lumen" ? null : await DB.get("apps", id + ":" + pkg)); },
    merged() {
      const out = new Map();
      for (const r of [...REPOS].reverse()) for (const [pkg, c] of cards[r.id]) {            // Lumen last → wins the display fields
        const prev = out.get(pkg);
        out.set(pkg, { pkg, name: c[1] || prev?.name || pkg, summary: c[2] || prev?.summary || "", icon: c[3] || prev?.icon || "", cats: (c[4]?.length ? c[4] : prev?.cats || []).map(catName),
          updated: Math.max(c[5] || 0, prev?.updated || 0), version: c[6] || prev?.version || "", src: (prev?.src || "") + r.id[0] });
      }
      return [...out.values()];
    },
  };
})();

/* GitHub release → APK (Lumen entries that point at releases instead of listing files, and the Lumen Store app itself) */
let releasesData = null;
const loadReleases = () => (releasesData ||= fetch("data/releases.json", { cache: "no-cache" }).then((r) => (r.ok ? r.json() : {})).catch(() => ({})));
const pickApk = (rel) => {
  const apks = (rel.assets || []).filter((a) => /\.apk$/i.test(a.name) && !/debug|unsigned|test/i.test(a.name));
  if (!apks.length) return null;
  const pref = apks.find((a) => /universal|all/i.test(a.name)) || apks.find((a) => /arm64/i.test(a.name)) || apks[0];
  return { name: rel.tag_name || rel.name || "latest", code: 0, size: pref.size || 0, url: pref.browser_download_url, hash: null, arch: null, perms: [], anti: [], pre: !!rel.prerelease, notes: rel.body || "" };
};
async function githubApk(slug) {
  const snap = (await loadReleases())[slug.toLowerCase()];
  if (snap) return { ...snap, perms: [], anti: [], hash: null, arch: null, code: 0 };
  let r;
  try { r = await fetch(`https://api.github.com/repos/${slug}/releases?per_page=10`, { headers: { Accept: "application/vnd.github+json" } }); } catch { return null; }
  if (!r.ok) return null;
  const list = (await r.json()).filter((x) => !x.draft);
  for (const group of [list.filter((x) => !x.prerelease), list]) for (const rel of group) { const v = pickApk(rel); if (v) return v; }
  return null;
}
/* Cheap per-package lookup while a big index has not been read yet */
async function liveLookup(r, pkg) {
  let res;
  try { res = await fetch(viaProxy(r.api + encodeURIComponent(pkg))); } catch { return { status: "error" }; }
  if (res.status === 404) return { status: "missing" };
  if (!res.ok) return { status: "error" };
  const j = await res.json(), base = r.base;
  const vs = (j.packages || []).filter((v) => v && Number.isFinite(+v.versionCode)).map((v) => ({ name: v.versionName || String(v.versionCode), code: +v.versionCode, size: 0, url: `${base}/${pkg}_${v.versionCode}.apk`, hash: null, arch: null, perms: [], anti: [], pre: false })).sort((a, b) => b.code - a.code);
  if (!vs.length) return { status: "missing" };
  const sug = vs.findIndex((v) => v.code === +j.suggestedVersionCode); if (sug > 0) vs.unshift(...vs.splice(sug, 1));
  return { status: "ok", versions: vs, icon: `${base}/icons-640/${pkg}.${vs[0].code}.png` };
}

/* ───────────── Open in app / share ───────────── */
const isAndroid = () => /Android/i.test(navigator.userAgent);
async function openInApp(pkg) {
  if (isAndroid()) {
    const fallback = encodeURIComponent(location.href.replace(/#.*$/, "") + "#no-app");
    location.href = `intent://app/${encodeURIComponent(pkg)}#Intent;scheme=${CFG.scheme};S.browser_fallback_url=${fallback};end`;
    setTimeout(() => { if (!document.hidden) toast("اگر Lumen Store نصب است و باز نشد، یک‌بار آن را اجرا کنید؛ وگرنه APK را بگیرید."); }, 1800);
  } else { await copyText(deepLink(pkg)); toast("باز کردن در برنامه فقط روی گوشی اندروید با Lumen Store نصب‌شده کار می‌کند. پیوند کپی شد."); }
}
async function share(title, pkg) {
  const url = siteLink(pkg);
  if (navigator.share) { try { await navigator.share({ title, url }); return; } catch (e) { if (e && e.name === "AbortError") return; } }
  copyToast(url, "پیوند برنامه");
}

/* ───────────── Shared chrome ───────────── */
function brand(href) {
  const a = h("a", { class: "brand", href, "aria-label": "Lumen Store" }, h("span", { class: "brand-mark" }), "Lumen Store");
  a.firstChild.innerHTML = DIAMOND; return a;
}
function renderTopbar(root, home) {
  const links = [[home + "#apps", "اپلیکیشن‌ها"], [home + "#submit", "ثبت اپ"], [home + "#listing", "فهرست‌ها"], [home + "#badge", "نشان دریافت"]];
  let nav, btn;
  const setOpen = (open) => {
    nav.classList.toggle("open", open); btn.setAttribute("aria-expanded", String(open));
    document.documentElement.classList.toggle("menu-open", open);
  };
  nav = h("nav", { class: "nav-links", id: "site-nav", "aria-label": "ناوبری اصلی" },
    links.map(([href, t]) => h("a", { href, onclick: () => setOpen(false) }, t)),
    h("a", { class: "nav-extra", href: home + "#download", onclick: () => setOpen(false) }, I.down(), "دریافت اپ"));
  btn = h("button", { class: "menu-toggle", type: "button", "aria-label": "باز و بسته کردن فهرست", "aria-expanded": "false", "aria-controls": "site-nav", onclick: () => setOpen(!nav.classList.contains("open")) }, h("i"), h("i"), h("i"));
  document.addEventListener("keydown", (e) => { if (e.key === "Escape") setOpen(false); });
  window.addEventListener("resize", () => { if (innerWidth > 720) setOpen(false); });
  root.replaceChildren(h("div", { class: "container nav" }, brand(home), nav, h("a", { class: "btn btn-tonal btn-sm nav-cta", href: home + "#download" }, I.down(), "دریافت اپ"), btn));
}
function renderFooter(root, home) {
  root.replaceChildren(h("div", { class: "container footer-row" }, brand(home),
    h("span", { class: "footer-note" }, "برای جامعهٔ متن‌باز، با احترام به انتخاب شما."),
    h("nav", { class: "footer-links", "aria-label": "پیوندهای پایین صفحه" },
      h("a", { href: home + "#apps" }, "اپلیکیشن‌ها"), h("a", { href: home + "#submit" }, "ثبت اپ"),
      h("a", { href: `https://t.me/${CFG.channel}`, target: "_blank", rel: "noopener" }, "کانال تلگرام"), h("a", { href: home + "#home" }, "بالای صفحه"))));
}

const badgeSnippets = (pkg) => {
  const img = CFG.site + "badges/lumen-store.svg", link = siteLink(pkg);
  return { markdown: `[![Get it in Lumen Store](${img})](${link})`, html: `<a href="${link}">\n  <img src="${img}" alt="Get it in Lumen Store" height="56">\n</a>`, link, id: pkg };
};

/* ───────────── Home ───────────── */
const SRC_LABEL = { l: "Lumen", f: "F-Droid", z: "Izzy" };
const SRC_ID = { l: "lumen", f: "fdroid", z: "izzy" };

function indexStatus(box) {
  const paint = () => {
    const errLines = REPOS.flatMap((r) => (Indexes.st[r.id].status === "error" ? Indexes.st[r.id].errs || [] : []).map((x) => `${r.label}: ${x}`));
    box.replaceChildren(...REPOS.map((r) => {
      const s = Indexes.st[r.id];
      let body, cls = "ix";
      if (s.status === "loading") { cls += " busy"; body = [h("span", { class: "spin" }), `${r.label} `, h("small", {}, s.count ? `${fa(s.count)} برنامه` : (s.mb ? `${fa(Math.round(s.mb * 10) / 10)} مگابایت` : "در حال اتصال…"))]; }
      else if (s.status === "error") { cls += " bad"; body = [`${r.label} `, h("small", {}, "خطا در اتصال"), h("button", { class: "ix-retry", type: "button", "aria-label": `تلاش دوباره ${r.label}`, onclick: () => Indexes.retry(r.id) }, I.refresh())]; }
      else if (s.status === "ready") { cls += " ok"; body = [I.check(), `${r.label} `, h("small", {}, `${fa(s.count)} برنامه`), s.cached && Indexes.st[r.id].mb ? null : null]; }
      else body = [`${r.label} `, h("small", {}, "در صف")];
      return h("span", { class: cls, title: s.error || "" }, body);
    }), ...(errLines.length ? [h("div", { class: "ix-err ltr mono" }, errLines.map((l) => h("div", {}, l)))] : []));
  };
  Indexes.on(paint); paint();
}

function setupHome() {
  const home = "index.html";
  renderTopbar($("#topbar"), home); renderFooter($("#footer"), home);

  const dl = $("#hero-download"), note = $("#release-note");
  githubApk(CFG.appRepo).then((rel) => {
    if (!rel) throw 0;
    dl.replaceChildren(I.down(), "دانلود APK ", h("small", { class: "ltr" }, rel.name));
    dl.href = rel.url; dl.removeAttribute("aria-disabled");
    note.replaceChildren(h("span", { class: "dot ok" }), `آخرین نسخه: ${rel.name}${rel.size ? " · " + fmtSize(rel.size) : ""}`);
  }).catch(() => {});

  const st = { q: "", cat: "همه", src: "", limit: 30, all: [] };
  const grid = $("#grid"), chipsEl = $("#chips"), srcEl = $("#srcchips"), countEl = $("#count"), moreEl = $("#more");
  let filtered = [], lastCatKey = "";

  const card = (x) => h("article", { class: "card" },
    tile(x.name, x.pkg, [x.icon]),
    h("div", { class: "card-body" },
      h("a", { class: "card-name card-link", href: `app.html?id=${encodeURIComponent(x.pkg)}` }, x.name),
      x.summary ? h("p", { class: "card-sum" }, x.summary) : h("p", { class: "card-pkg ltr mono" }, x.pkg),
      h("div", { class: "card-meta" }, (REPOS.length > 1 ? [...x.src] : []).map((c) => h("span", { class: "tag" + (c === "l" ? " lumen" : "") }, SRC_LABEL[c])), x.version ? h("span", { class: "ver ltr" }, x.version) : null)));

  const paintList = () => {
    const busy = REPOS.some((r) => Indexes.st[r.id].status === "loading");
    countEl.textContent = `${fa(Math.min(filtered.length, st.limit))} از ${fa(filtered.length)} برنامه${busy ? " · در حال بارگذاری" : ""}`;
    const nodes = filtered.slice(0, st.limit).map(card);
    const allBad = !busy && REPOS.every((r) => Indexes.st[r.id].status === "error");
    const empty = allBad
      ? h("div", { class: "empty" }, h("strong", {}, "هیچ‌کدام از فهرست‌ها در دسترس نیست."), "اتصال اینترنت را بررسی کنید و دوباره تلاش کنید.", h("div", { class: "empty-act" }, h("button", { class: "btn btn-outline btn-sm", type: "button", onclick: () => Indexes.retryAll() }, I.refresh(), "تلاش دوباره")))
      : h("div", { class: "empty" }, h("strong", {}, busy ? "در حال خواندن فهرست‌ها…" : "چیزی با این مشخصات پیدا نشد."), busy ? "برنامه‌ها همین‌که برسند اینجا نمایش داده می‌شوند." : "جست‌وجو را کوتاه‌تر کنید یا فیلتر دیگری را امتحان کنید.");
    grid.replaceChildren(...(nodes.length ? nodes : [empty]));
    moreEl.replaceChildren(...(filtered.length > st.limit ? [h("button", { class: "btn btn-outline", type: "button", onclick: () => { st.limit += 60; paintList(); } }, `نمایش بیشتر (${fa(filtered.length - st.limit)})`)] : []));
  };
  const paintChips = () => {
    const counts = new Map();
    for (const x of st.all) for (const c of x.cats) counts.set(c, (counts.get(c) || 0) + 1);
    const cats = ["همه", ...[...counts].sort((a, b) => b[1] - a[1]).slice(0, 24).map((e) => e[0])];
    const key = cats.join("|") + st.cat + st.src;
    if (key === lastCatKey) return; lastCatKey = key;
    chipsEl.replaceChildren(...cats.map((c) => h("button", { class: "chip" + (c === st.cat ? " active" : ""), type: "button", "aria-pressed": String(c === st.cat), onclick: () => { st.cat = c; st.limit = 30; apply(); } }, c)));
    srcEl.replaceChildren(); srcEl.hidden = true;
  };
  const apply = () => {
    const q = st.q.trim().toLowerCase();
    filtered = st.all.filter((x) => (st.cat === "همه" || x.cats.includes(st.cat)) && (!st.src || x.src.includes(st.src)) && (!q || x._s.includes(q)));
    paintChips(); paintList();
  };
  const reload = () => {
    const list = Indexes.merged();
    for (const x of list) x._s = `${x.pkg} ${x.name} ${x.summary} ${x.cats.join(" ")}`.toLowerCase();
    list.sort((a, b) => (b.src.includes("l") - a.src.includes("l")) || (b.updated || 0) - (a.updated || 0) || a.name.localeCompare(b.name, "en"));
    st.all = list; lastCatKey = ""; apply();
  };
  $("#search").addEventListener("input", (e) => { st.q = e.target.value; st.limit = 30; apply(); });
  if ("IntersectionObserver" in window) new IntersectionObserver((es) => { if (es[0].isIntersecting && filtered.length > st.limit && st.limit < 90) { st.limit += 30; paintList(); } }, { rootMargin: "700px" }).observe($("#sentinel"));
  indexStatus($("#ixstatus"));
  Indexes.on(reload); reload(); Indexes.start();

  setupSubmit();

  /* badge builder: one code viewer, tabs for each format */
  const input = $("#badge-pkg");
  const cur = () => badgeSnippets(PKG_RE.test(input.value.trim()) ? input.value.trim() : "com.example.app");
  const cv = codeViewer([["markdown", "Markdown", "markdown"], ["html", "HTML", "html"], ["link", "پیوند", "link"], ["id", "شناسه", "plain"]], (k) => cur()[k]);
  $("#badge-viewer").replaceChildren(cv);
  input.addEventListener("input", cv.refresh);
  const m = /^#\/app\/([^/?#]+)/.exec(location.hash); if (m) location.replace(`app.html?id=${m[1]}`);
}

/* App submission through Telegram */
const GH_REPO_RE = /^https:\/\/github\.com\/[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})\/[A-Za-z0-9._-]+?(?:\.git)?\/?$/i;
const CONFIRMS = [
  ["c_oss",    "برنامه متن‌باز است و سورس‌کد آن در مخزن عمومی GitHub منتشر شده است."],
  ["c_assets", "مخزن شامل آیکون رسمی PNG و اسکرین‌شات‌های عمودی ۹:۱۶ است و آیکون با شرایط پذیرش (پس‌زمینهٔ مناسب، لوگوی استاندارد) مطابقت دارد."],
  ["c_review", "می‌دانم برنامه پیش از انتشار از نظر کیفیت، صحت اطلاعات، امنیت و سلامت فایل اجرایی بررسی می‌شود و انتشار منوط به تأیید نهایی مدیریت است."],
];
function setupSubmit() {
  const f = $("#submit-form"); if (!f) return;
  const val = (n) => (f.elements[n]?.value || "").trim();
  const checked = (n) => !!f.elements[n]?.checked;

  const catSel = f.elements.cat;                       // categories come from the same list the catalogue uses
  if (catSel) for (const c of [...new Set(Object.values(CAT_FA)), "سایر"].sort((x, y) => x.localeCompare(y, "fa"))) catSel.append(h("option", { value: c }, c));

  const repoUrl = () => { const v = val("repo").replace(/\.git$/i, "").replace(/\/$/, ""); return v; };
  const message = () => [
    "درخواست ثبت اپ در Lumen Store", "",
    `نام برنامه: ${val("name") || "—"}`, `شناسهٔ بسته: ${val("pkg") || "—"}`,
    `مخزن GitHub: ${repoUrl() || "—"}`, `دسته‌بندی: ${val("cat") || "—"}`, `تگ‌ها: ${val("tags") || "—"}`,
    `صفحهٔ F-Droid / Izzy (اختیاری): ${val("index") || "—"}`, `توضیح کوتاه: ${val("desc") || "—"}`, `نام برای تماس: ${val("who") || "—"}`, "",
    "تأییدیه‌ها:", ...CONFIRMS.map(([n, t]) => `${checked(n) ? "✓" : "✗"} ${t}`),
  ].join("\n");
  const cv = codeViewer([["msg", "پیام آمادهٔ تلگرام", "plain"]], message, { rtl: true });
  $("#submit-preview").replaceChildren(cv);

  const clearErr = (el) => { el.removeAttribute("aria-invalid"); const e = el.closest(".field, .checks")?.querySelector(".field-err"); if (e) e.remove(); };
  const fail = (name, msg) => {
    const el = f.elements[name]; if (!el) return false;
    clearErr(el); el.setAttribute("aria-invalid", "true");
    const holder = el.closest(".field, .checks");
    holder?.append(h("small", { class: "field-err", role: "alert" }, msg));
    try { el.scrollIntoView({ block: "center", behavior: "smooth" }); el.focus({ preventScroll: true }); } catch { /* ignore */ }
    toast(msg); return false;
  };
  const validate = () => {
    for (const el of f.elements) if (el.name) clearErr(el);
    if (!val("name")) return fail("name", "نام برنامه را بنویسید.");
    if (!val("pkg")) return fail("pkg", "شناسهٔ بسته الزامی است؛ مثل com.example.app");
    if (!PKG_RE.test(val("pkg"))) return fail("pkg", "شناسهٔ بسته معتبر نیست؛ مثل com.example.app");
    if (!val("repo")) return fail("repo", "پیوند مخزن GitHub الزامی است.");
    if (!GH_REPO_RE.test(val("repo"))) return fail("repo", "پیوند باید به شکل https://github.com/نام‌کاربری/مخزن باشد.");
    if (!val("cat")) return fail("cat", "یک دسته‌بندی انتخاب کنید.");
    if (val("index") && !safeUrl(val("index"))) return fail("index", "این پیوند معتبر نیست.");
    for (const [n] of CONFIRMS) if (!checked(n)) return fail(n, "برای ارسال، همهٔ تأییدیه‌ها لازم است.");
    return true;
  };
  f.addEventListener("input", (e) => { if (e.target.name) clearErr(e.target); cv.refresh(); });
  f.addEventListener("change", (e) => { if (e.target.name) clearErr(e.target); cv.refresh(); });
  f.addEventListener("submit", async (e) => {
    e.preventDefault();
    if (!validate()) return;
    const ok = await copyText(message());
    toast(ok ? "پیام کپی شد؛ در تلگرام فقط Paste کنید" : "پیام را از کادر پایین کپی کنید");
    setTimeout(() => { location.href = `https://t.me/${CFG.telegram}`; }, ok ? 700 : 0);
  });
}

/* ───────────── Detail ───────────── */
const getPkg = () => {
  const q = new URLSearchParams(location.search).get("id");
  const m = /^#\/app\/([^/?#]+)/.exec(location.hash);
  const p = decodeURIComponent(q || (m && m[1]) || "").trim();
  return PKG_RE.test(p) ? p : null;
};

function sanitize(html) {
  const ALLOWED = new Set(["B", "STRONG", "I", "EM", "U", "P", "BR", "UL", "OL", "LI", "A", "H1", "H2", "H3", "H4", "BLOCKQUOTE", "CODE"]);
  if (!/<(p|br|ul|ol|li)\b/i.test(html)) html = String(html).replace(/\n{2,}/g, "<br><br>").replace(/\n/g, "<br>");
  const doc = new DOMParser().parseFromString(String(html), "text/html");
  const walk = (node) => {
    const frag = document.createDocumentFragment();
    node.childNodes.forEach((n) => {
      if (n.nodeType === 3) frag.append(document.createTextNode(n.textContent));
      else if (n.nodeType === 1) {
        if (!ALLOWED.has(n.tagName)) { frag.append(walk(n)); return; }
        const tag = /^H[1-4]$/.test(n.tagName) ? "strong" : n.tagName.toLowerCase(), el = document.createElement(tag);
        if (tag === "a") { const u = safeUrl(n.getAttribute("href")); if (u) { el.href = u; el.target = "_blank"; el.rel = "noopener noreferrer"; } }
        el.append(walk(n)); frag.append(el);
      }
    });
    return frag;
  };
  const wrap = document.createElement("div"); wrap.append(walk(doc.body)); return wrap;
}

function setupDetail() {
  const home = "index.html";
  renderTopbar($("#topbar"), home); renderFooter($("#footer"), home);
  const root = $("#detail"), pkg = getPkg();
  if (!pkg) { location.replace(home + "#apps"); return; }
  if (false) {
    root.replaceChildren(h("div", { class: "container notfound" }, h("h1", {}, "برنامه‌ای مشخص نشده"), h("p", { class: "lead" }, "نشانی باید به شکل app.html?id=com.example.app باشد."), h("a", { class: "btn btn-primary", href: home + "#apps" }, "دیدن همهٔ برنامه‌ها")));
    return;
  }
  const S = { recs: {}, live: {} };      // recs[id] = index record, live[id] = quick API lookup result
  const R = (id) => S.recs[id];
  const status = (r) => {
    const rec = R(r.id), ix = Indexes.st[r.id], lv = S.live[r.id], page = r.page && r.page(pkg);
    if (rec?.versions?.length) return { status: "ok", versions: rec.versions, latest: rec.versions[0], page };
    if (lv?.status === "ok") return { status: "ok", versions: lv.versions, latest: lv.versions[0], page };
    if (ix.status === "ready" && ix.count > 0 && !(rec?.ghSlug && lv?.status === "loading")) return { status: "missing", page };
    if (ix.status === "error") return { status: lv?.status === "missing" ? "missing" : "error", page };
    return lv?.status === "missing" && ix.status !== "loading" ? { status: "missing" } : { status: "loading" };
  };
  const meta = () => {
    const order = REPOS.map((r) => R(r.id)).filter(Boolean);
    if (!order.length) return null;
    const m = { ...order[0] };
    for (const o of order.slice(1)) for (const k of ["summary", "description", "icon", "license", "source", "website", "issues", "changelog", "donate", "author", "added", "updated"]) if (!m[k] && o[k]) m[k] = o[k];
    if (!m.shots?.length) m.shots = order.find((o) => o.shots?.length)?.shots || [];
    m.cats = [...new Set(order.flatMap((o) => o.cats || []))].map(catName).filter((c) => !/^(lumen|f-droid|izzyondroid)$/i.test(c));
    return m;
  };
  const best = () => { let b = null; for (const r of REPOS) { const s = status(r); if (s.status === "ok" && (!b || s.latest.code > b.v.code)) b = { r, v: s.latest }; } return b; };
  const loading = () => REPOS.some((r) => status(r).status === "loading");
  const nameOf = () => meta()?.name || pkg;

  const regions = { head: h("div"), actions: h("div", { id: "actions" }), body: h("div") };
  root.replaceChildren(h("div", { class: "container" }, h("div", { class: "dwrap" },
    h("nav", { class: "crumbs", "aria-label": "مسیر" }, h("a", { href: home }, "Lumen Store"), h("span", {}, "‹"), h("a", { href: home + "#apps" }, "اپلیکیشن‌ها"), h("span", {}, "‹"), h("span", { class: "crumb-cur ltr" }, pkg)),
    location.hash === "#no-app" ? h("div", { class: "notice warn" }, "Lumen Store روی این دستگاه پیدا نشد. APK را از پایین بگیرید و نصب کنید.") : null,
    regions.head, regions.actions, regions.body)));

  const paintHead = () => {
    const m = meta(), tags = (m?.cats || []).slice(0, 4).map((c) => h("span", { class: "tag" }, c));
    if (Indexes.mem.lumen.has(pkg)) tags.unshift(h("span", { class: "tag lumen" }, "در فهرست Lumen"));
    const icons = [m?.icon, ...REPOS.map((r) => S.live[r.id]?.icon)];
    regions.head.replaceChildren(h("header", { class: "dh" },
      tile(nameOf(), pkg, icons),
      h("div", { class: "dh-text" }, h("h1", {}, nameOf()), m?.author ? h("div", { class: "dh-author" }, m.author) : null, h("div", {}, h("span", { class: "dh-pkg ltr mono" }, pkg))),
      m?.summary ? h("p", { class: "dh-sum" }, m.summary) : null,
      tags.length ? h("div", { class: "meta-tags" }, tags) : null));
    document.title = `${nameOf()} — Lumen Store`;
  };

  const stats = () => {
    const b = best(), m = meta(), c = [];
    if (b) c.push([b.v.name, "نسخه", true]);
    const sz = b && fmtSize(b.v.size); if (sz) c.push([sz, "حجم"]);
    const up = fmtShort(m?.updated); if (up) c.push([up, "بروزرسانی"]);
    if (m?.license) c.push([m.license, "مجوز", true]);
    else if (b?.v.arch) c.push([b.v.arch.split(",")[0], "معماری", true]);
    return c;
  };

  const paintActions = () => {
    const b = best(), ld = loading();
    const dlBtn = b
      ? h("a", { class: "btn btn-primary btn-lg", href: b.v.url, rel: "noopener", download: "" }, I.down(), "دانلود APK", h("small", {}, b.r.label))
      : h("button", { class: "btn btn-primary btn-lg", type: "button", disabled: true }, ld ? [h("span", { class: "spin" }), "در حال یافتن APK…"] : "APK پیدا نشد");
    const st = stats();
    regions.actions.replaceChildren(
      st.length ? h("div", { class: "stats", role: "list" }, st.map(([v, k, ltr]) => h("div", { class: "stat", role: "listitem" }, h("b", { class: ltr ? "ltr" : "" }, v), h("small", {}, k)))) : null,
      h("div", { class: "actions" }, dlBtn,
        h("div", { class: "actions-row" },
          h("button", { class: "btn btn-tonal", type: "button", onclick: () => openInApp(pkg) }, I.phone(), "باز کردن در اپ"),
          h("button", { class: "btn btn-outline btn-icon", type: "button", "aria-label": "کپی پیوند", onclick: () => copyToast(siteLink(pkg), "پیوند برنامه") }, I.copy()),
          h("button", { class: "btn btn-outline btn-icon", type: "button", "aria-label": "اشتراک‌گذاری", onclick: () => share(nameOf(), pkg) }, I.share()))));
    let bar = $(".mbar");
    if (!bar) { bar = h("div", { class: "mbar", "aria-hidden": "true" }); document.body.append(bar); document.body.classList.add("has-mbar"); watchActions(); }
    bar.replaceChildren(
      b ? h("a", { class: "btn btn-primary", href: b.v.url, rel: "noopener", download: "", tabindex: "-1" }, I.down(), "دانلود", h("small", { class: "ltr" }, b.v.name)) : h("span", { class: "btn btn-primary", "aria-disabled": "true" }, ld ? "در حال یافتن…" : "APK پیدا نشد"),
      h("button", { class: "btn btn-tonal", type: "button", tabindex: "-1", onclick: () => openInApp(pkg) }, I.phone(), "در اپ"));
  };
  let watching = false;
  const watchActions = () => {
    if (watching || !("IntersectionObserver" in window)) { document.body.classList.add("mbar-on"); return; } watching = true;
    new IntersectionObserver((es) => document.body.classList.toggle("mbar-on", !es[0].isIntersecting && es[0].boundingClientRect.top < 0)).observe(regions.actions);
  };

  const srcRow = (r) => {
    const s = status(r), ok = s.status === "ok";
    let sub, right;
    if (s.status === "loading") { sub = [h("span", { class: "spin" }), "در حال خواندن فهرست…"]; }
    else if (ok) { const sz = fmtSize(s.latest.size); sub = [h("span", { class: "ltr" }, `${s.latest.name}${s.latest.code ? ` (${s.latest.code})` : ""}`), sz ? ` · ${sz}` : ""]; right = h("a", { class: "btn btn-tonal btn-sm", href: s.latest.url, rel: "noopener", download: "" }, I.down(), "APK"); }
    else if (s.status === "error") { sub = "دسترسی به این فهرست ممکن نشد"; right = r.page ? h("a", { class: "btn btn-outline btn-sm", href: r.page(pkg), target: "_blank", rel: "noopener" }, I.ext(), "صفحه") : h("button", { class: "btn btn-outline btn-sm", type: "button", onclick: () => Indexes.retry(r.id) }, I.refresh(), "تلاش"); }
    else sub = "در این فهرست نیست";
    return h("div", { class: "src-row" + (ok ? " ok" : "") }, h("span", { class: "src-ico" }, r.short), h("div", { class: "src-txt" }, h("div", { class: "src-name" }, r.label), h("div", { class: "src-sub" }, sub)), right);
  };

  let shotsKey = "", descKey = "";
  const shotsNode = h("div"), descNode = h("div");
  const paintBody = () => {
    const m = meta(), b = best();
    const sec = [];
    sec.push(h("section", { class: "sec" }, h("h2", {}, "دریافت از"), h("div", { class: "src-list" }, REPOS.map(srcRow))));
    if (m?.shots?.length) {
      const k = m.shots.join();
      if (k !== shotsKey) { shotsKey = k; shotsNode.replaceChildren(h("section", { class: "sec" }, h("h2", {}, "تصاویر"), h("div", { class: "shots", role: "list" }, m.shots.map((u) => h("img", { role: "listitem", src: safeUrl(u) || "", alt: `تصویری از ${nameOf()}`, loading: "lazy", referrerpolicy: "no-referrer", onerror: (e) => { const box = e.target.parentNode; e.target.remove(); if (box && !box.children.length) box.closest(".sec")?.remove(); } }))))); }
      sec.push(shotsNode);
    }
    if (m?.description) {
      if (m.description !== descKey) {
        descKey = m.description;
        const d = h("div", { class: "desc" }, sanitize(m.description)), long = m.description.length > 420;
        if (long) d.classList.add("clamp");
        descNode.replaceChildren(h("section", { class: "sec" }, h("h2", {}, "دربارهٔ برنامه"), d, long ? h("button", { class: "btn btn-outline btn-sm", type: "button", onclick: (e) => { e.currentTarget.textContent = d.classList.toggle("clamp") ? "نمایش بیشتر" : "نمایش کمتر"; } }, "نمایش بیشتر") : null));
      }
      sec.push(descNode);
    } else if (!loading()) {
      sec.push(h("section", { class: "sec" }, h("h2", {}, "دربارهٔ برنامه"), h("p", { class: "muted" }, "توضیحات این برنامه هنوز خوانده نشده است. صفحهٔ آن را در مخزن مبدأ ببینید:"),
        h("div", { class: "links" }, REPOS.filter((r) => r.page).map((r) => h("a", { class: "btn btn-outline btn-sm", href: r.page(pkg), target: "_blank", rel: "noopener" }, I.ext(), r.label)))));
    }
    const hist = (R("lumen")?.versions?.length ? R("lumen").versions : REPOS.map((r) => R(r.id)?.versions || S.live[r.id]?.versions).find((v) => v?.length) || []).slice(0, 8);
    if (hist.length > 1) sec.push(h("section", { class: "sec" }, h("h2", {}, "نسخه‌ها"), h("ul", { class: "vlist" }, hist.map((v) => h("li", {}, h("div", {}, h("b", { class: "ltr" }, v.name), h("small", { class: "mono ltr" }, `${v.code}${fmtSize(v.size) ? " · " + fmtSize(v.size).replace(" مگابایت", " MB") : ""}`)), h("a", { class: "btn btn-outline btn-sm", href: v.url, rel: "noopener", download: "" }, I.down(), "APK"))))));
    const kv = [], add = (k, v, ltr) => v && kv.push(h("div", { class: "kv-row" }, h("span", {}, k), h("span", { class: ltr ? "ltr" : "" }, v)));
    add("شناسهٔ بسته", pkg, true);
    if (b) { add("آخرین نسخه", `${b.v.name}${b.v.code ? ` (${b.v.code})` : ""}`, true); add("حجم", fmtSize(b.v.size)); add("معماری", b.v.arch, true); }
    add("مجوز", m?.license, true); add("افزوده‌شده", fmtDate(m?.added)); add("بروزرسانی", fmtDate(m?.updated));
    if (b?.v.hash) add("SHA-256", b.v.hash.slice(0, 16) + "…", true);
    sec.push(h("section", { class: "sec" }, h("h2", {}, "مشخصات"), h("div", { class: "kv" }, kv)));
    const lk = [["کد منبع", m?.source], ["وب‌سایت", m?.website], ["گزارش مشکل", m?.issues], ["تغییرات", m?.changelog], ["حمایت مالی", m?.donate]].map(([t, u]) => [t, safeUrl(u)]).filter(([, u]) => u);
    if (lk.length) sec.push(h("section", { class: "sec" }, h("h2", {}, "پیوندها"), h("div", { class: "links" }, lk.map(([t, u]) => h("a", { class: "btn btn-outline btn-sm", href: u, target: "_blank", rel: "noopener noreferrer" }, I.ext(), t)))));
    if (b?.v.anti?.length) sec.push(h("section", { class: "sec" }, h("h2", {}, "ویژگی‌های ناخواسته"), h("div", { class: "meta-tags" }, b.v.anti.map((a) => h("span", { class: "tag" }, a)))));
    if (b?.v.perms?.length) sec.push(h("section", { class: "sec" }, h("h2", {}, "مجوزها", h("small", {}, fa(b.v.perms.length))), h("ul", { class: "perm-list" }, b.v.perms.slice(0, 40).map((p) => h("li", {}, p.replace(/^android\.permission\./, ""))))));
    const bv = codeViewer([["markdown", "Markdown", "markdown"], ["html", "HTML", "html"], ["link", "پیوند", "link"], ["id", "شناسه", "plain"]], (k) => badgeSnippets(pkg)[k]);
    sec.push(h("section", { class: "sec" }, h("h2", {}, "نشان برای پروژهٔ شما"), h("img", { class: "badge-img", src: "badges/lumen-store.svg", alt: "Get it in Lumen Store" }), bv));
    regions.body.replaceChildren(...sec);
  };

  const paint = () => { paintHead(); paintActions(); paintBody(); };
  const refresh = async () => {
    for (const r of REPOS) { const rec = await Indexes.record(r.id, pkg); if (rec) S.recs[r.id] = rec; else if (r.id === "lumen") delete S.recs.lumen; }
    const lr = S.recs.lumen;
    if (lr && !lr.versions.length && lr.ghSlug && !S.live.lumen) { S.live.lumen = { status: "loading" }; githubApk(lr.ghSlug).then((rel) => { S.live.lumen = rel ? { status: "ok", versions: [rel] } : { status: "missing" }; lastSig = ""; refresh(); }).catch(() => { S.live.lumen = { status: "error" }; lastSig = ""; refresh(); }); }
    const sig = REPOS.map((r) => `${status(r).status}:${S.recs[r.id] ? 1 : 0}`).join(",");
    if (sig !== lastSig) { lastSig = sig; paint(); }
  };
  let lastSig = "";
  paint();
  /* until a big index has been read, a cheap per-package API call gives the APK link right away */
  for (const r of REPOS.filter((x) => x.api)) liveLookup(r, pkg).then((res) => { S.live[r.id] = res; lastSig = ""; refresh(); });
  Indexes.on(refresh); refresh(); Indexes.start();
}

/* ───────────── Boot ───────────── */
document.addEventListener("DOMContentLoaded", () => {
  const page = document.body.dataset.page;
  if (page === "home") setupHome(); else if (page === "detail") setupDetail();
});
})();
