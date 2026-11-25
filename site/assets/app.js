/* Lumen Store website. Plain ES2020. Reads the Lumen index (deepcodecreate/lumenrepo) live in the browser. */
(() => {
"use strict";

const CFG = {
  site: "https://deepcodecreate.github.io/lumenstore/",
  appRepo: "deepcodecreate/lumenstore",
  scheme: "lumenstore",
  telegram: "lumenapps",
  channel: "deepcodecreate",
};
const RAW_BASE = "https://raw.githubusercontent.com/deepcodecreate/lumenrepo/main";
const SOURCES = [
  { url: RAW_BASE + "/index-v2.json", live: true },
  { url: "https://cdn.jsdelivr.net/gh/deepcodecreate/lumenrepo@main/index-v2.json", live: false },
  { url: "data/lumen-index-v2.json", live: false },
];
const REFRESH_MS = 4 * 60 * 1000;

/* ───────────── Language and theme ───────────── */
const root = document.documentElement;
const LANG = root.lang === "fa" ? "fa" : "en";
const STR = window.LUMEN_STR || { en: {}, fa: {} };
const t = (k, vars) => {
  let s = STR[LANG][k] ?? STR.en[k] ?? k;
  if (vars) for (const [a, b] of Object.entries(vars)) s = s.split("{" + a + "}").join(b);
  return s;
};
const LOCALE = LANG === "fa" ? "fa-IR" : "en-US";
const nf = (n) => (Number.isFinite(+n) ? (+n).toLocaleString(LOCALE) : "-");
const store = {
  get(k) { try { return localStorage.getItem(k); } catch { return null; } },
  set(k, v) { try { localStorage.setItem(k, v); } catch { /* ignore */ } },
};
function setTheme(theme) {
  root.dataset.theme = theme; store.set("lumen-theme", theme);
  const m = document.querySelector('meta[name="theme-color"]');
  if (m) m.content = theme === "light" ? "#f6faf8" : "#060809";
}
function switchLang() { store.set("lumen-lang", LANG === "fa" ? "en" : "fa"); location.reload(); }

/* ───────────── Helpers ───────────── */
const $ = (s, r = document) => r.querySelector(s);
const PKG_RE = /^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$/;
const safeUrl = (u) => { try { const x = new URL(u, location.href); return /^https?:$/.test(x.protocol) ? x.href : null; } catch { return null; } };
const siteLink = (pkg) => `${CFG.site}app.html?id=${encodeURIComponent(pkg)}`;
const deepLink = (pkg) => `${CFG.scheme}://app/${encodeURIComponent(pkg)}`;
const TILE_COLORS = ["#4fd1c5", "#a8cbe4", "#d9a17c", "#b1ccc6", "#9cf6e1", "#e6b8a2", "#8fd0e8", "#c8e0b0"];
const tileColor = (key) => { let x = 0; for (const c of key) x = (x * 31 + c.charCodeAt(0)) >>> 0; return TILE_COLORS[x % TILE_COLORS.length]; };
const initials = (name) => {
  const parts = String(name || "?").trim().split(/[\s._-]+/).filter(Boolean);
  const a = parts[0] ? [...parts[0]][0] : "?", b = parts[1] ? [...parts[1]][0] : "";
  return (a + b).toUpperCase();
};
const fmtSize = (b) => (b > 0 ? (b >= 1048576 ? `${(b / 1048576).toLocaleString(LOCALE, { maximumFractionDigits: 1 })} ${t("mb")}` : `${nf(Math.round(b / 1024))} ${t("kb")}`) : null);
const dateFmt = (opts) => { try { return new Intl.DateTimeFormat(LANG === "fa" ? "fa-IR-u-ca-persian" : "en-US", opts); } catch { return new Intl.DateTimeFormat("en-US", opts); } };
const fmtDate = (ms) => (ms ? dateFmt({ year: "numeric", month: "long", day: "numeric" }).format(new Date(ms)) : null);
const fmtShort = (ms) => (ms ? dateFmt({ year: "2-digit", month: "short", day: "numeric" }).format(new Date(ms)) : null);

const CAT_FA = { "Connectivity": "اتصال", "Development": "توسعه", "Games": "بازی", "Graphics": "گرافیک", "Internet": "اینترنت", "Money": "مالی", "Multimedia": "چندرسانه‌ای", "Navigation": "نقشه و سفر", "Phone & SMS": "تماس و پیامک", "Reading": "مطالعه", "Science & Education": "علم و آموزش", "Security": "امنیت", "Sports & Health": "ورزش و سلامت", "System": "سیستم", "Theming": "ظاهر", "Time": "زمان", "Writing": "نوشتن" };
const catName = (c) => (LANG === "fa" ? CAT_FA[c] || c : c);

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
  refresh: () => svgIcon('<path d="M20 11a8 8 0 1 0-2.3 5.7M20 4v7h-7"/>'),
  moon: () => svgIcon('<path d="M20 14.5A8.5 8.5 0 0 1 9.5 4a8.5 8.5 0 1 0 10.5 10.5z"/>'),
  sun: () => svgIcon('<circle cx="12" cy="12" r="4"/><path d="M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4"/>'),
  globe: () => svgIcon('<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3c2.5 2.7 3.8 5.7 3.8 9s-1.3 6.3-3.8 9c-2.5-2.7-3.8-5.7-3.8-9S9.5 5.7 12 3z"/>'),
  search: () => svgIcon('<circle cx="11" cy="11" r="7"/><path d="m20 20-3.6-3.6"/>'),
};
const DIAMOND = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 2.5 19 12l-7 9.5L5 12z" fill="#F6F3EC"/></svg>';

let toastTimer;
function toast(msg) {
  let el = $(".toast");
  if (!el) { el = h("div", { class: "toast", role: "status", "aria-live": "polite" }); document.body.append(el); }
  el.textContent = msg; el.classList.add("show");
  clearTimeout(toastTimer); toastTimer = setTimeout(() => el.classList.remove("show"), 2800);
}
async function copyText(text) {
  try {
    if (navigator.clipboard && window.isSecureContext) await navigator.clipboard.writeText(text);
    else { const ta = h("textarea", { style: "position:fixed;opacity:0" }); ta.value = text; document.body.append(ta); ta.select(); document.execCommand("copy"); ta.remove(); }
    return true;
  } catch { return false; }
}
const copyToast = async (text, what) => toast((await copyText(text)) ? t("toast_copied", { x: what }) : t("toast_copy_fail"));

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

/* ───────────── Code viewer ───────────── */
function highlight(code, lang) {
  const out = document.createDocumentFragment();
  if (lang === "link" || lang === "plain") { out.append(h("span", { class: "s" }, code)); return out; }
  const rules = lang === "html"
    ? [[/&lt;|<\/?[a-z]+/i, "t"], [/[a-z-]+(?==")/i, "a"], [/"[^"]*"/, "s"]]
    : [[/!?\[[^\]]*\]/, "t"], [/\(https?:[^)]*\)/, "s"]];
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
  let key = tabs[0][0];
  const body = h("pre", { class: "cv-body" + (opts.rtl ? " rtl" : ""), dir: opts.rtl ? "rtl" : "ltr", tabindex: "0" });
  const label = h("span", { class: "cv-lang" });
  const copy = h("button", { class: "cv-copy", type: "button", "aria-label": t("copy") });
  const bar = h("div", { class: "cv-bar" });
  const rootEl = h("div", { class: "cv" }, bar, body);
  const paint = () => {
    const [, lab, lang] = tabs.find((x) => x[0] === key);
    body.replaceChildren(highlight(getText(key), lang));
    label.textContent = lab;
    bar.replaceChildren(
      tabs.length > 1 ? h("div", { class: "cv-tabs", role: "tablist" }, tabs.map(([k, l]) => h("button", { class: "cv-tab", type: "button", role: "tab", "aria-selected": String(k === key), onclick: () => { key = k; paint(); } }, l))) : label,
      copy);
    copy.replaceChildren(I.copy(), h("span", {}, t("copy")));
    copy.onclick = async () => {
      const ok = await copyText(getText(key));
      copy.replaceChildren(ok ? I.check() : I.copy(), h("span", {}, ok ? t("copied") : t("failed")));
      copy.classList.toggle("done", ok);
      setTimeout(() => { copy.classList.remove("done"); copy.replaceChildren(I.copy(), h("span", {}, t("copy"))); }, 1600);
    };
  };
  rootEl.refresh = paint; paint();
  return rootEl;
}

/* ───────────── Storage (IndexedDB, best-effort offline copy) ───────────── */
const DB = (() => {
  let dbp;
  const open = () => dbp || (dbp = new Promise((res) => {
    try {
      const rq = indexedDB.open("lumen-site", 2);
      rq.onupgradeneeded = () => { if (!rq.result.objectStoreNames.contains("kv")) rq.result.createObjectStore("kv"); };
      rq.onsuccess = () => res(rq.result); rq.onerror = () => res(null); rq.onblocked = () => res(null);
    } catch { res(null); }
  }));
  const run = async (mode, fn) => {
    const db = await open(); if (!db) return null;
    return new Promise((res) => {
      try { const tx = db.transaction("kv", mode); const r = fn(tx.objectStore("kv")); tx.oncomplete = () => res(r && "result" in r ? r.result : true); tx.onerror = tx.onabort = () => res(null); } catch { res(null); }
    });
  };
  return { get: (k) => run("readonly", (s) => s.get(k)), put: (k, v) => run("readwrite", (s) => s.put(v, k)) };
})();

/* ───────────── Index parsing ───────────── */
const LOCALES = LANG === "fa" ? ["fa", "fa-IR", "en-US", "en", "en-GB"] : ["en-US", "en", "en-GB", "fa", "fa-IR"];
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
    const gh = [m.releases, m.sourceCode, m.webSite, m.changelog, m.issueTracker].map((u) => GH_RE.exec(u || "")).find(Boolean);
    if (!versions.length && !gh) continue;
    let shots = [];
    const sc = m.screenshots;
    if (sc) {
      let arr = sc.phone || sc.phoneScreenshots || (Array.isArray(sc) ? sc : null);
      if (arr && !Array.isArray(arr)) { const ph = arr; arr = null; for (const k of [...LOCALES, ...Object.keys(ph || {})]) if (Array.isArray(ph?.[k])) { arr = ph[k]; break; } }
      shots = (arr || []).map((s) => res(typeof s === "string" ? s : s?.name)).filter(Boolean);
    }
    out.set(pkg, { pkg, name: pickLoc(m.name) || pkg, summary: pickLoc(m.summary), description: pickLoc(m.description), icon: res(pickLocFile(m.icon)),
      shots, cats: (m.categories || []).map((c) => cats[c] || c), license: m.license || null, source: m.sourceCode || null, website: m.webSite || null, issues: m.issueTracker || null,
      changelog: m.changelog || null, donate: Array.isArray(m.donate) ? m.donate[0] : m.donate || null, author: m.authorName || null,
      added: Number(m.added) || 0, updated: Number(m.lastUpdated) || 0, versions, ghSlug: gh ? `${gh[1]}/${gh[2].replace(/\.git$/i, "")}` : null });
  }
  return out;
}

function fetchGuard(url, { connectMs = 20000, stallMs = 30000 } = {}) {
  const ctl = new AbortController(); let timer = setTimeout(() => ctl.abort(), connectMs);
  const done = () => clearTimeout(timer);
  return fetch(url, { cache: "no-cache", signal: ctl.signal }).then((res) => { clearTimeout(timer); timer = setTimeout(() => ctl.abort(), stallMs); return { res, done }; }, (e) => { done(); throw e; });
}

/* The Lumen index is read live from GitHub on every visit and refreshed while the page stays open.
   The jsDelivr copy and the build-time snapshot are only fallbacks, so adding an app never needs a site rebuild. */
const Index = (() => {
  const st = { status: "idle", count: 0, error: null, errs: [], at: 0 };
  let recs = new Map(), busy = false, started = false;
  const listeners = new Set();
  const emit = () => { for (const f of listeners) { try { f(); } catch (e) { console.error(e); } } };

  async function fetchJson() {
    const errs = [], stamp = Math.floor(Date.now() / 60000);
    for (const s of SOURCES) {
      try {
        const g = await fetchGuard(s.live ? `${s.url}?v=${stamp}` : s.url);
        try {
          if (!g.res.ok) throw new Error("HTTP " + g.res.status);
          const json = await g.res.json();
          if (!json || typeof json !== "object" || !json.packages) throw new Error("not an index");
          return json;
        } finally { g.done(); }
      } catch (e) {
        let host = s.url; try { host = new URL(s.url, location.href).host; } catch { /* keep */ }
        errs.push(`${host}: ${e && e.name === "AbortError" ? "timeout" : (e && e.message) || e}`);
      }
    }
    const err = new Error(errs.join(" | ")); err.errs = errs; throw err;
  }
  async function load() {
    if (busy) return; busy = true;
    if (!recs.size) { st.status = "loading"; emit(); }
    try {
      let json;
      try { json = await fetchJson(); DB.put("raw:lumen", json); }
      catch (e) { json = await DB.get("raw:lumen"); if (!json) throw e; }
      recs = parseV2(json, RAW_BASE + "/index-v2.json");
      st.count = recs.size; st.status = "ready"; st.error = null; st.errs = []; st.at = Date.now();
    } catch (e) {
      st.status = recs.size ? "ready" : "error"; st.error = String((e && e.message) || e); st.errs = (e && e.errs) || [];
    } finally { busy = false; emit(); }
  }
  return {
    st,
    on: (f) => { listeners.add(f); return () => listeners.delete(f); },
    start() {
      if (started) return; started = true; load();
      setInterval(() => { if (!document.hidden && Date.now() - st.at > REFRESH_MS) load(); }, 60000);
      document.addEventListener("visibilitychange", () => { if (!document.hidden && st.at && Date.now() - st.at > REFRESH_MS / 2) load(); });
    },
    retry: () => load(),
    record: (pkg) => recs.get(pkg) || null,
    list() {
      return [...recs.values()].map((r) => ({ pkg: r.pkg, name: r.name, summary: r.summary || "", icon: r.icon || "", cats: (r.cats || []).map(catName), updated: r.updated || 0, version: r.versions[0]?.name || "" }));
    },
  };
})();

/* ───────────── GitHub releases: live first, snapshot only as fallback ───────────── */
let releasesData = null;
const loadReleases = () => (releasesData ||= fetch("data/releases.json", { cache: "no-cache" }).then((r) => (r.ok ? r.json() : {})).catch(() => ({})));
const pickApk = (rel) => {
  const apks = (rel.assets || []).filter((a) => /\.apk$/i.test(a.name) && !/debug|unsigned|test/i.test(a.name));
  if (!apks.length) return null;
  const pref = apks.find((a) => /universal|all/i.test(a.name)) || apks.find((a) => /arm64/i.test(a.name)) || apks[0];
  return { name: rel.tag_name || rel.name || "latest", code: 0, size: pref.size || 0, url: pref.browser_download_url, hash: null, arch: null, perms: [], anti: [], pre: !!rel.prerelease };
};
async function liveRelease(slug) {
  const key = "lumen-rel:" + slug.toLowerCase();
  try { const c = JSON.parse(sessionStorage.getItem(key) || "null"); if (c && Date.now() - c.at < 10 * 60000) return c.v; } catch { /* ignore */ }
  let r;
  try { r = await fetch(`https://api.github.com/repos/${slug}/releases?per_page=10`, { headers: { Accept: "application/vnd.github+json" } }); } catch { return null; }
  if (!r.ok) return null;
  let found = null;
  try {
    const list = (await r.json()).filter((x) => !x.draft);
    for (const group of [list.filter((x) => !x.prerelease), list]) { for (const rel of group) { found = pickApk(rel); if (found) break; } if (found) break; }
  } catch { return null; }
  if (found) { try { sessionStorage.setItem(key, JSON.stringify({ at: Date.now(), v: found })); } catch { /* ignore */ } }
  return found;
}
const relCache = new Map();
function githubApk(slug) {
  const key = slug.toLowerCase();
  if (!relCache.has(key)) {
    relCache.set(key, (async () => {
      const live = await liveRelease(slug);
      if (live) return live;
      const snap = (await loadReleases())[key];
      return snap ? { ...snap, perms: [], anti: [], hash: null, arch: null, code: 0 } : null;
    })());
  }
  return relCache.get(key);
}

/* ───────────── Open in app / share ───────────── */
const isAndroid = () => /Android/i.test(navigator.userAgent);
async function openInApp(pkg) {
  if (isAndroid()) {
    const fallback = encodeURIComponent(location.href.replace(/#.*$/, "") + "#no-app");
    location.href = `intent://app/${encodeURIComponent(pkg)}#Intent;scheme=${CFG.scheme};S.browser_fallback_url=${fallback};end`;
    setTimeout(() => { if (!document.hidden) toast(t("toast_open_fail")); }, 1800);
  } else { await copyText(deepLink(pkg)); toast(t("toast_desktop")); }
}
async function share(title, pkg) {
  const url = siteLink(pkg);
  if (navigator.share) { try { await navigator.share({ title, url }); return; } catch (e) { if (e && e.name === "AbortError") return; } }
  copyToast(url, t("link_label"));
}

/* ───────────── Shared chrome ───────────── */
function brand() {
  const a = h("a", { class: "brand", href: "index.html", "aria-label": "Lumen Store" }, h("span", { class: "brand-mark" }), "Lumen Store");
  a.firstChild.innerHTML = DIAMOND; return a;
}
function renderTopbar(el) {
  const page = document.body.dataset.page;
  const cur = (p) => (page === p ? "page" : null);
  let nav, btn;
  const setOpen = (open) => { nav.classList.toggle("open", open); btn.setAttribute("aria-expanded", String(open)); root.classList.toggle("menu-open", open); };
  nav = h("nav", { class: "nav-links", id: "site-nav", "aria-label": t("nav_aria") },
    h("a", { href: "index.html", "aria-current": cur("home") || (page === "detail" ? "page" : null), onclick: () => setOpen(false) }, t("nav_home")),
    h("a", { href: "submit.html", "aria-current": cur("submit"), onclick: () => setOpen(false) }, t("nav_submit")),
    h("a", { class: "nav-danger", href: "danger.html", "aria-current": cur("danger"), onclick: () => setOpen(false) }, h("span", { class: "pulse-dot" }), t("nav_danger")));
  const themeBtn = h("button", { class: "tool-btn theme-btn", type: "button", "aria-label": t("theme_aria"), onclick: () => setTheme(root.dataset.theme === "light" ? "dark" : "light") }, I.moon(), I.sun());
  themeBtn.querySelector("svg:nth-child(1)").classList.add("i-moon"); themeBtn.querySelector("svg:nth-child(2)").classList.add("i-sun");
  const langBtn = h("button", { class: "tool-btn", type: "button", "aria-label": t("lang_aria"), onclick: switchLang }, I.globe(), t("lang_btn"));
  btn = h("button", { class: "menu-toggle", type: "button", "aria-label": t("menu_aria"), "aria-expanded": "false", "aria-controls": "site-nav", onclick: () => setOpen(!nav.classList.contains("open")) }, h("i"), h("i"), h("i"));
  document.addEventListener("keydown", (e) => { if (e.key === "Escape") setOpen(false); });
  window.addEventListener("resize", () => { if (innerWidth > 760) setOpen(false); });
  window.addEventListener("pageshow", () => setOpen(false));
  el.replaceChildren(h("div", { class: "container nav" }, brand(), nav, h("div", { class: "nav-tools" }, langBtn, themeBtn, btn)));
  const onScroll = () => el.classList.toggle("scrolled", scrollY > 8);
  addEventListener("scroll", onScroll, { passive: true }); onScroll();
}
function renderFooter(el) {
  el.replaceChildren(h("div", { class: "container footer-row" }, brand(),
    h("span", { class: "footer-note" }, t("footer_note")),
    h("nav", { class: "footer-links", "aria-label": "Footer" },
      h("a", { href: "index.html" }, t("nav_home")), h("a", { href: "submit.html" }, t("nav_submit")), h("a", { href: "danger.html" }, t("nav_danger")),
      h("a", { href: `https://t.me/${CFG.channel}`, target: "_blank", rel: "noopener" }, t("footer_tg")),
      h("a", { href: `https://github.com/${CFG.appRepo}`, target: "_blank", rel: "noopener" }, t("footer_github")))));
}
function applyStatic() {
  document.querySelectorAll("[data-i18n]").forEach((el) => { el.textContent = t(el.dataset.i18n); });
  document.querySelectorAll("[data-i18n-ph]").forEach((el) => { el.placeholder = t(el.dataset.i18nPh); });
  document.querySelectorAll("[data-i18n-aria]").forEach((el) => { el.setAttribute("aria-label", t(el.dataset.i18nAria)); });
  const tk = document.body.dataset.title; if (tk) document.title = t(tk);
  const dk = document.body.dataset.desc;
  if (dk) document.querySelectorAll('meta[name="description"], meta[property="og:description"]').forEach((m) => { m.content = t(dk); });
  if (tk) document.querySelectorAll('meta[property="og:title"]').forEach((m) => { m.content = t(tk); });
}
function setupMotion() {
  const items = [...document.querySelectorAll(".reveal")];
  if (!("IntersectionObserver" in window)) { items.forEach((x) => x.classList.add("in")); }
  else {
    const io = new IntersectionObserver((es) => { for (const e of es) if (e.isIntersecting) { e.target.classList.add("in"); io.unobserve(e.target); } }, { threshold: 0.12, rootMargin: "0px 0px -40px 0px" });
    items.forEach((x, i) => { if (!x.style.getPropertyValue("--d")) x.style.setProperty("--d", `${(i % 4) * 70}ms`); io.observe(x); });
  }
  const counters = [...document.querySelectorAll("[data-count]")];
  const run = (el) => {
    const to = Number(el.dataset.count), dur = 1100, t0 = performance.now();
    if (matchMedia("(prefers-reduced-motion: reduce)").matches) { el.textContent = nf(to); return; }
    const step = (now) => { const p = Math.min(1, (now - t0) / dur); el.textContent = nf(Math.round(to * (1 - Math.pow(1 - p, 3)))); if (p < 1) requestAnimationFrame(step); };
    requestAnimationFrame(step);
  };
  counters.forEach((el) => { el.textContent = nf(0); });
  if (!("IntersectionObserver" in window)) counters.forEach(run);
  else { const co = new IntersectionObserver((es) => { for (const e of es) if (e.isIntersecting) { run(e.target); co.unobserve(e.target); } }, { threshold: 0.6 }); counters.forEach((el) => co.observe(el)); }
}

const badgeSnippets = (pkg) => {
  const img = CFG.site + "badges/lumen-store.svg", link = siteLink(pkg);
  return { markdown: `[![Get it in Lumen Store](${img})](${link})`, html: `<a href="${link}">\n  <img src="${img}" alt="Get it in Lumen Store" height="56">\n</a>`, link, id: pkg };
};
const badgeTabs = () => [["markdown", t("cv_markdown"), "markdown"], ["html", t("cv_html"), "html"], ["link", t("cv_link"), "link"], ["id", t("cv_id"), "plain"]];

/* ───────────── Home ───────────── */
function setupHome() {
  const dl = $("#hero-download");
  githubApk(CFG.appRepo).then((rel) => {
    if (!rel) return;
    for (const a of [dl, $("#banner-download")]) { if (!a) continue; a.href = rel.url; a.removeAttribute("aria-disabled"); }
    if ($("#release-note")) $("#release-note").replaceChildren(h("span", { class: "dot ok" }), t("latest_v", { v: rel.name }) + (rel.size ? " · " + fmtSize(rel.size) : ""));
  }).catch(() => {});

  const st = { q: "", cat: "", limit: 30, all: [] };
  const grid = $("#grid"), chipsEl = $("#chips"), countEl = $("#count"), moreEl = $("#more"), statusEl = $("#ixstatus"), input = $("#search");
  let filtered = [], lastChips = "", lastSig = "", firstPaint = true;

  const card = (x, i) => h("article", { class: "card", style: `--i:${firstPaint ? Math.min(i, 14) : 0}` },
    tile(x.name, x.pkg, [x.icon]),
    h("div", { class: "card-body" },
      h("a", { class: "card-name", href: `app.html?id=${encodeURIComponent(x.pkg)}` }, x.name),
      x.summary ? h("p", { class: "card-sum" }, x.summary) : h("p", { class: "card-pkg ltr mono" }, x.pkg),
      h("div", { class: "card-meta" }, x.cats[0] ? h("span", { class: "tag" }, x.cats[0]) : null, x.version ? h("span", { class: "ver ltr" }, x.version) : null)));

  const paintStatus = () => {
    const s = Index.st;
    if (s.status === "ready") statusEl.replaceChildren(h("span", { class: "ix" }, I.check(), t("live"), " · ", t("apps_n", { n: nf(s.count) })));
    else if (s.status === "error") statusEl.replaceChildren(h("span", { class: "ix bad" }, t("conn_error"), h("button", { class: "ix-retry", type: "button", "aria-label": t("retry"), onclick: () => Index.retry() }, I.refresh())), s.errs.length ? h("div", { class: "ix-err ltr mono" }, s.errs.map((l) => h("div", {}, l))) : null);
    else statusEl.replaceChildren(h("span", { class: "ix" }, h("span", { class: "spin" }), t("connecting")));
  };
  const paintList = () => {
    const s = Index.st, busy = s.status === "loading" || s.status === "idle";
    countEl.textContent = t("count_of", { a: nf(Math.min(filtered.length, st.limit)), b: nf(filtered.length) }) + (busy ? t("loading_suffix") : "");
    let nodes = filtered.slice(0, st.limit).map(card);
    if (!nodes.length) {
      if (busy) nodes = Array.from({ length: 6 }, () => h("div", { class: "skeleton" }));
      else if (s.status === "error") nodes = [h("div", { class: "empty" }, h("strong", {}, t("empty_err")), t("empty_err_sub"), h("div", { class: "empty-act" }, h("button", { class: "btn btn-outline btn-sm", type: "button", onclick: () => Index.retry() }, I.refresh(), t("retry"))))];
      else nodes = [h("div", { class: "empty" }, h("strong", {}, t("empty_none")), t("empty_none_sub"))];
    }
    grid.replaceChildren(...nodes);
    moreEl.replaceChildren(...(filtered.length > st.limit ? [h("button", { class: "btn btn-outline", type: "button", onclick: () => { st.limit += 60; paintList(); } }, t("more", { n: nf(filtered.length - st.limit) }))] : []));
    firstPaint = false;
  };
  const paintChips = () => {
    const counts = new Map();
    for (const x of st.all) for (const c of x.cats) counts.set(c, (counts.get(c) || 0) + 1);
    const cats = ["", ...[...counts].sort((a, b) => b[1] - a[1]).slice(0, 24).map((e) => e[0])];
    const key = cats.join("|") + "#" + st.cat;
    if (key === lastChips) return; lastChips = key;
    chipsEl.replaceChildren(...cats.map((c) => h("button", { class: "chip" + (c === st.cat ? " active" : ""), type: "button", "aria-pressed": String(c === st.cat), onclick: () => { st.cat = c; st.limit = 30; apply(); } }, c || t("all"))));
  };
  const apply = () => {
    const q = st.q.trim().toLowerCase();
    filtered = st.all.filter((x) => (!st.cat || x.cats.includes(st.cat)) && (!q || x._s.includes(q)));
    paintChips(); paintList();
  };
  const reload = () => {
    paintStatus();
    const list = Index.list();
    for (const x of list) x._s = `${x.pkg} ${x.name} ${x.summary} ${x.cats.join(" ")}`.toLowerCase();
    list.sort((a, b) => (b.updated || 0) - (a.updated || 0) || a.name.localeCompare(b.name, "en"));
    const sig = Index.st.status + "|" + list.map((x) => x.pkg + x.version + x.updated).join(",");
    if (sig === lastSig) return; lastSig = sig;
    st.all = list; lastChips = ""; apply();
  };
  input.addEventListener("input", (e) => { st.q = e.target.value; st.limit = 30; apply(); });
  document.addEventListener("keydown", (e) => { if (e.key === "/" && !/^(input|textarea|select)$/i.test(document.activeElement?.tagName || "")) { e.preventDefault(); input.focus(); } });
  if ("IntersectionObserver" in window) new IntersectionObserver((es) => { if (es[0].isIntersecting && filtered.length > st.limit && st.limit < 90) { st.limit += 30; paintList(); } }, { rootMargin: "700px" }).observe($("#sentinel"));
  Index.on(reload); reload(); Index.start();
  const m = /^#\/app\/([^/?#]+)/.exec(location.hash); if (m) location.replace(`app.html?id=${m[1]}`);
}

/* ───────────── Submit page ───────────── */
const GH_REPO_RE = /^https:\/\/github\.com\/[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})\/[A-Za-z0-9._-]+?(?:\.git)?\/?$/i;
function setupSubmit() {
  $("#terms-list").replaceChildren(...[1, 2, 3, 4, 5, 6, 7, 8, 9].map((n) => h("li", {}, t("term" + n))));
  $("#steps-list").replaceChildren(...[1, 2, 3, 4].map((n) => h("li", {}, t("step" + n))));
  $("#confirms").replaceChildren(...[1, 2, 3].map((n) => h("label", { class: "check" }, h("input", { type: "checkbox", name: "c" + n }), h("span", {}, t("conf" + n)))));
  const f = $("#submit-form");
  const val = (n) => (f.elements[n]?.value || "").trim();
  const checked = (n) => !!f.elements[n]?.checked;
  const catSel = f.elements.cat;
  for (const c of [...Object.keys(CAT_FA).map(catName).sort((x, y) => x.localeCompare(y, LANG)), t("f_other")]) catSel.append(h("option", { value: c }, c));
  const repoUrl = () => val("repo").replace(/\.git$/i, "").replace(/\/$/, "");
  const message = () => [
    t("msg_title"), "",
    `${t("m_name")}: ${val("name") || "-"}`, `${t("m_pkg")}: ${val("pkg") || "-"}`, `${t("m_repo")}: ${repoUrl() || "-"}`, `${t("m_cat")}: ${val("cat") || "-"}`, `${t("m_tags")}: ${val("tags") || "-"}`,
    `${t("m_index")}: ${val("index") || "-"}`, `${t("m_desc")}: ${val("desc") || "-"}`, `${t("m_who")}: ${val("who") || "-"}`, "",
    `${t("m_conf")}:`, ...[1, 2, 3].map((n) => `${checked("c" + n) ? "✓" : "✗"} ${t("conf" + n)}`),
  ].join("\n");
  const cv = codeViewer([["msg", t("preview_label"), "plain"]], message, { rtl: LANG === "fa" });
  $("#submit-preview").replaceChildren(cv);

  const clearErr = (el) => { el.removeAttribute("aria-invalid"); const e = el.closest(".field, .checks")?.querySelector(".field-err"); if (e) e.remove(); };
  const fail = (name, msg) => {
    const el = f.elements[name]; if (!el) return false;
    clearErr(el); el.setAttribute("aria-invalid", "true");
    el.closest(".field, .checks")?.append(h("small", { class: "field-err", role: "alert" }, msg));
    try { el.scrollIntoView({ block: "center", behavior: "smooth" }); el.focus({ preventScroll: true }); } catch { /* ignore */ }
    toast(msg); return false;
  };
  const validate = () => {
    for (const el of f.elements) if (el.name) clearErr(el);
    if (!val("name")) return fail("name", t("e_name"));
    if (!val("pkg")) return fail("pkg", t("e_pkg_req"));
    if (!PKG_RE.test(val("pkg"))) return fail("pkg", t("e_pkg_bad"));
    if (!val("repo")) return fail("repo", t("e_repo_req"));
    if (!GH_REPO_RE.test(val("repo"))) return fail("repo", t("e_repo_bad"));
    if (!val("cat")) return fail("cat", t("e_cat"));
    if (val("index") && !safeUrl(val("index"))) return fail("index", t("e_url"));
    for (const n of [1, 2, 3]) if (!checked("c" + n)) return fail("c" + n, t("e_confirm"));
    return true;
  };
  f.addEventListener("input", (e) => { if (e.target.name) clearErr(e.target); cv.refresh(); });
  f.addEventListener("change", (e) => { if (e.target.name) clearErr(e.target); cv.refresh(); });
  f.addEventListener("submit", async (e) => {
    e.preventDefault();
    if (!validate()) return;
    const ok = await copyText(message());
    toast(ok ? t("toast_msg_copied") : t("toast_msg_manual"));
    setTimeout(() => { location.href = `https://t.me/${CFG.telegram}`; }, ok ? 700 : 0);
  });

  const input = $("#badge-pkg");
  const cur = () => badgeSnippets(PKG_RE.test(input.value.trim()) ? input.value.trim() : "com.example.app");
  const bv = codeViewer(badgeTabs(), (k) => cur()[k]);
  $("#badge-viewer").replaceChildren(bv);
  input.addEventListener("input", bv.refresh);
}

/* ───────────── Detail ───────────── */
const getPkg = () => {
  const q = new URLSearchParams(location.search).get("id");
  const m = /^#\/app\/([^/?#]+)/.exec(location.hash);
  let p = ""; try { p = decodeURIComponent(q || (m && m[1]) || "").trim(); } catch { p = ""; }
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
  const pkg = getPkg();
  if (!pkg) { location.replace("index.html"); return; }
  const host = $("#detail");
  const S = { live: null };

  const status = () => {
    const r = Index.record(pkg), ix = Index.st;
    if (r?.versions?.length) return { kind: "ok", versions: r.versions };
    if (S.live === "loading") return { kind: "loading" };
    if (S.live && S.live.status === "ok") return { kind: "ok", versions: S.live.versions };
    if (ix.status === "error") return { kind: "error" };
    if (ix.status === "ready") return { kind: "missing" };
    return { kind: "loading" };
  };
  const meta = () => Index.record(pkg);
  const nameOf = () => meta()?.name || pkg;

  const regions = { head: h("div"), actions: h("div", { id: "actions" }), body: h("div") };
  host.replaceChildren(h("div", { class: "container" }, h("div", { class: "dwrap" },
    h("nav", { class: "crumbs", "aria-label": t("crumb_aria") }, h("a", { href: "index.html" }, "Lumen Store"), h("span", {}, "/"), h("a", { href: "index.html#apps" }, t("crumb_apps")), h("span", {}, "/"), h("span", { class: "ltr" }, pkg)),
    location.hash === "#no-app" ? h("div", { class: "notice warn" }, t("no_app_notice")) : null,
    regions.head, regions.actions, regions.body)));

  const paintHead = () => {
    const m = meta(), tags = (m?.cats || []).slice(0, 4).map((c) => h("span", { class: "tag" }, catName(c)));
    if (m) tags.unshift(h("span", { class: "tag lumen" }, t("in_lumen")));
    regions.head.replaceChildren(h("header", { class: "dh" },
      tile(nameOf(), pkg, [m?.icon]),
      h("div", { class: "dh-text" }, h("h1", {}, nameOf()), m?.author ? h("div", { class: "dh-author" }, m.author) : null, h("div", { class: "dh-pkg ltr mono" }, pkg)),
      m?.summary ? h("p", { class: "dh-sum" }, m.summary) : null,
      tags.length ? h("div", { class: "meta-tags" }, tags) : null));
    document.title = `${nameOf()} | Lumen Store`;
  };
  const paintActions = () => {
    const s = status(), m = meta(), latest = s.kind === "ok" ? s.versions[0] : null;
    const dlBtn = latest
      ? h("a", { class: "btn btn-primary btn-lg", href: latest.url, rel: "noopener", download: "" }, I.down(), t("dl_apk"), h("small", { class: "ltr" }, latest.name))
      : h("button", { class: "btn btn-primary btn-lg", type: "button", disabled: true }, s.kind === "loading" ? [h("span", { class: "spin" }), t("finding")] : t("apk_missing"));
    const stats = [];
    if (latest) stats.push([latest.name, t("stat_version"), true]);
    const sz = latest && fmtSize(latest.size); if (sz) stats.push([sz, t("stat_size")]);
    const up = fmtShort(m?.updated); if (up) stats.push([up, t("stat_updated")]);
    if (m?.license) stats.push([m.license, t("stat_license"), true]); else if (latest?.arch) stats.push([latest.arch.split(",")[0], t("stat_arch"), true]);
    regions.actions.replaceChildren(
      stats.length ? h("div", { class: "stats", role: "list" }, stats.map(([v, k, ltr]) => h("div", { class: "stat", role: "listitem" }, h("b", { class: ltr ? "ltr" : "" }, v), h("small", {}, k)))) : null,
      h("div", { class: "actions" }, dlBtn, h("div", { class: "actions-row" },
        h("button", { class: "btn btn-tonal", type: "button", onclick: () => openInApp(pkg) }, I.phone(), t("open_app")),
        h("button", { class: "btn btn-outline btn-icon", type: "button", "aria-label": t("copy_link"), onclick: () => copyToast(siteLink(pkg), t("link_label")) }, I.copy()),
        h("button", { class: "btn btn-outline btn-icon", type: "button", "aria-label": t("share"), onclick: () => share(nameOf(), pkg) }, I.share()))));
    let bar = $(".mbar");
    if (!bar) { bar = h("div", { class: "mbar", "aria-hidden": "true" }); document.body.append(bar); document.body.classList.add("has-mbar"); watchActions(); }
    bar.replaceChildren(
      latest ? h("a", { class: "btn btn-primary", href: latest.url, rel: "noopener", download: "", tabindex: "-1" }, I.down(), t("dl_short"), h("small", { class: "ltr" }, latest.name)) : h("span", { class: "btn btn-primary", "aria-disabled": "true" }, s.kind === "loading" ? t("finding_short") : t("apk_missing")),
      h("button", { class: "btn btn-tonal", type: "button", tabindex: "-1", onclick: () => openInApp(pkg) }, I.phone(), t("in_app")));
  };
  let watching = false;
  const watchActions = () => {
    if (watching || !("IntersectionObserver" in window)) { document.body.classList.add("mbar-on"); return; } watching = true;
    new IntersectionObserver((es) => document.body.classList.toggle("mbar-on", !es[0].isIntersecting && es[0].boundingClientRect.top < 0)).observe(regions.actions);
  };

  let shotsKey = "", descKey = "";
  const shotsNode = h("div"), descNode = h("div");
  const paintBody = () => {
    const m = meta(), s = status(), sec = [];
    const versions = s.kind === "ok" ? s.versions : [], latest = versions[0];
    if (s.kind === "missing") sec.push(h("section", { class: "sec" }, h("p", { class: "muted" }, t("not_in_index")), h("div", { class: "links", style: "margin-top:14px" }, h("a", { class: "btn btn-outline btn-sm", href: "index.html#apps" }, t("back_all")))));
    if (s.kind === "error") sec.push(h("section", { class: "sec" }, h("p", { class: "muted" }, t("err_index")), h("button", { class: "btn btn-outline btn-sm", type: "button", onclick: () => Index.retry() }, I.refresh(), t("retry"))));
    if (m?.shots?.length) {
      const k = m.shots.join();
      if (k !== shotsKey) { shotsKey = k; shotsNode.replaceChildren(h("section", { class: "sec" }, h("h2", {}, t("sec_shots")), h("div", { class: "shots", role: "list" }, m.shots.map((u) => h("img", { role: "listitem", src: safeUrl(u) || "", alt: t("shot_alt", { n: nameOf() }), loading: "lazy", referrerpolicy: "no-referrer", onerror: (e) => { const box = e.target.parentNode; e.target.remove(); if (box && !box.children.length) box.closest(".sec")?.remove(); } }))))); }
      sec.push(shotsNode);
    }
    if (m?.description) {
      if (m.description !== descKey) {
        descKey = m.description;
        const d = h("div", { class: "desc" }, sanitize(m.description)), long = m.description.length > 420;
        if (long) d.classList.add("clamp");
        const toggle = long ? h("button", { class: "btn btn-outline btn-sm", type: "button", onclick: (e) => { e.currentTarget.textContent = d.classList.toggle("clamp") ? t("show_more") : t("show_less"); } }, t("show_more")) : null;
        descNode.replaceChildren(h("section", { class: "sec" }, h("h2", {}, t("sec_about")), d, toggle));
      }
      sec.push(descNode);
    }
    if (versions.length > 1) sec.push(h("section", { class: "sec" }, h("h2", {}, t("sec_versions")), h("ul", { class: "vlist" }, versions.slice(0, 8).map((v) => h("li", {}, h("div", {}, h("b", { class: "ltr" }, v.name), h("small", { class: "mono ltr" }, `${v.code || ""}${fmtSize(v.size) ? " · " + fmtSize(v.size) : ""}`)), h("a", { class: "btn btn-outline btn-sm", href: v.url, rel: "noopener", download: "" }, I.down(), "APK"))))));
    const kv = [], add = (k, v, ltr) => v && kv.push(h("div", { class: "kv-row" }, h("span", {}, k), h("span", { class: ltr ? "ltr" : "" }, v)));
    add(t("kv_pkg"), pkg, true);
    if (latest) { add(t("kv_latest"), `${latest.name}${latest.code ? ` (${latest.code})` : ""}`, true); add(t("kv_size"), fmtSize(latest.size)); add(t("kv_arch"), latest.arch, true); }
    add(t("kv_license"), m?.license, true); add(t("kv_added"), fmtDate(m?.added)); add(t("kv_updated"), fmtDate(m?.updated));
    if (latest?.hash) add("SHA-256", latest.hash.slice(0, 16) + "…", true);
    sec.push(h("section", { class: "sec" }, h("h2", {}, t("sec_details")), h("div", { class: "kv" }, kv)));
    const lk = [[t("lk_source"), m?.source], [t("lk_website"), m?.website], [t("lk_issues"), m?.issues], [t("lk_changelog"), m?.changelog], [t("lk_donate"), m?.donate]].map(([x, u]) => [x, safeUrl(u)]).filter(([, u]) => u);
    if (lk.length) sec.push(h("section", { class: "sec" }, h("h2", {}, t("sec_links")), h("div", { class: "links" }, lk.map(([x, u]) => h("a", { class: "btn btn-outline btn-sm", href: u, target: "_blank", rel: "noopener noreferrer" }, I.ext(), x)))));
    if (latest?.anti?.length) sec.push(h("section", { class: "sec" }, h("h2", {}, t("sec_anti")), h("div", { class: "meta-tags" }, latest.anti.map((a) => h("span", { class: "tag" }, a)))));
    if (latest?.perms?.length) sec.push(h("section", { class: "sec" }, h("h2", {}, t("sec_perms"), h("small", {}, nf(latest.perms.length))), h("ul", { class: "perm-list" }, latest.perms.slice(0, 40).map((p) => h("li", {}, p.replace(/^android\.permission\./, ""))))));
    sec.push(h("section", { class: "sec" }, h("h2", {}, t("sec_badge")), h("img", { class: "badge-img", src: "badges/lumen-store.svg", alt: "Get it in Lumen Store" }), codeViewer(badgeTabs(), (k) => badgeSnippets(pkg)[k])));
    regions.body.replaceChildren(...sec);
  };

  let lastSig = "";
  const paint = () => { paintHead(); paintActions(); paintBody(); };
  const refresh = () => {
    const r = Index.record(pkg);
    if (r && !r.versions.length && r.ghSlug && S.live === null) {
      S.live = "loading";
      githubApk(r.ghSlug).then((rel) => { S.live = rel ? { status: "ok", versions: [rel] } : { status: "missing" }; lastSig = ""; refresh(); }).catch(() => { S.live = { status: "error" }; lastSig = ""; refresh(); });
    }
    const sig = `${status().kind}:${r ? r.versions.length + ":" + r.updated : 0}:${Index.st.status}`;
    if (sig !== lastSig) { lastSig = sig; paint(); }
  };
  paint();
  Index.on(refresh); refresh(); Index.start();
}

/* ───────────── Boot ───────────── */
document.addEventListener("DOMContentLoaded", () => {
  applyStatic();
  const top = $("#topbar"), foot = $("#footer");
  if (top) renderTopbar(top);
  if (foot) renderFooter(foot);
  const page = document.body.dataset.page;
  if (page === "home") setupHome(); else if (page === "detail") setupDetail(); else if (page === "submit") setupSubmit();
  setupMotion();
});
})();
