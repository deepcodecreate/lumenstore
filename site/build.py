import html
import json
import os
import re
import shutil
import sys
import time
import urllib.request
from datetime import date, datetime, timezone
from pathlib import Path
from urllib.parse import quote

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "site"
OUT = ROOT / "public"
SITE_URL = os.environ.get("SITE_URL", "https://deepcodecreate.github.io/lumenstore").rstrip("/")
INDEX_URL = os.environ.get("INDEX_URL", "https://raw.githubusercontent.com/deepcodecreate/lumenrepo/main/index-v2.json")
INDEX_FILE = os.environ.get("INDEX_FILE")
BASE = INDEX_URL.rsplit("/", 1)[0]
REPO = "https://github.com/deepcodecreate/lumenstore"
LATEST = REPO + "/releases"
INDEX_REPO = "https://github.com/deepcodecreate/lumenrepo"
TODAY = date.today().isoformat()

EN = {
    "lang": "en",
    "dir": "ltr",
    "title": "Lumen Store - Open-source Android app store, no ads, no tracking",
    "desc": "Lumen Store finds, installs and updates open-source Android apps straight from F-Droid, IzzyOnDroid, GitHub, GitLab and Codeberg. Free, no account, no ads, no tracking.",
    "nav_features": "Features",
    "nav_shots": "Screenshots",
    "nav_apps": "Apps",
    "nav_install": "Install",
    "nav_faq": "FAQ",
    "other_lang": "فارسی",
    "h1": "Lumen Store",
    "tagline": "Find, install and update open-source Android apps straight from their source.",
    "dl": "Download APK",
    "gh": "View on GitHub",
    "meta_line": "Free and open source. Android 8.0 or newer. No account, no ads, no tracking.",
    "features_h": "Everything you need to keep open-source apps up to date",
    "features": [
        ("Scan and update", "Matches your installed apps with F-Droid, IzzyOnDroid and GitHub releases and shows what has a new version."),
        ("One-tap install", "Downloads the right APK for your device and installs or updates it in one tap."),
        ("Explore GitHub", "Browse projects with rendered READMEs, screenshots, releases and developer profiles."),
        ("Your own sources", "Add repositories from GitHub, GitLab or Codeberg and get updates from them."),
        ("Private by design", "No account, no ads, no analytics. An optional GitHub token stays encrypted on your device."),
        ("Made to look good", "Material 3 themes, custom icon shapes, live backgrounds, English and Persian with full RTL."),
    ],
    "shots_h": "Screenshots",
    "apps_h": "Apps in the Lumen index",
    "apps_p": "A catalog of open-source apps available through Lumen Store. Download an APK directly or add the index inside the app.",
    "search": "Search apps",
    "no_results": "No apps match your search.",
    "empty": "The catalog is empty for now.",
    "details": "Details",
    "install_h": "How to install",
    "steps": [
        "Download the latest APK from the Releases page.",
        "Open the file and allow installs from your browser or file manager when Android asks.",
        "Open Lumen Store and let it scan your apps.",
    ],
    "verify_h": "Verify your download",
    "verify_p": "Every release includes SHA-256 checksums and a signed build provenance attestation.",
    "faq_h": "Frequently asked questions",
    "faq": [
        ("Is Lumen Store free?", "Yes. It is free and open source under the GPL-3.0 license."),
        ("Which Android versions are supported?", "Android 8.0 (API 26) and newer."),
        ("Does Lumen Store track me?", "No. There are no analytics, ads or trackers, and no account is required. Read the Privacy Policy for the details."),
        ("What is the Lumen index?", "It is the app catalog maintained for Lumen Store in the lumenrepo repository on GitHub. This site lists the apps it contains."),
        ("How do I report a bug or ask for a feature?", "Open an issue on GitHub. Security problems can be reported privately from the Security tab."),
    ],
    "privacy": "Privacy Policy",
    "security": "Security Policy",
    "license": "Licensed under GPL-3.0",
    "made_by": "Made by deepcodecreate",
    "back": "All apps",
    "version": "Version",
    "package": "Package",
    "min_android": "Minimum Android",
    "updated": "Updated",
    "size": "Size",
    "license_l": "License",
    "source": "Source code",
    "website": "Website",
    "versions_h": "Versions",
    "screens_h": "Screenshots",
    "download_apk": "Download APK",
    "app_title": "{name} for Android - download APK | Lumen Store",
    "open_in": "Open Lumen Store",
}

FA = {
    "lang": "fa",
    "dir": "rtl",
    "title": "لومن استور - فروشگاه برنامه‌های متن‌باز اندروید، بدون تبلیغات و ردیابی",
    "desc": "لومن استور برنامه‌های متن‌باز اندروید را مستقیم از F-Droid، IzzyOnDroid، GitHub، GitLab و Codeberg پیدا، نصب و به‌روز می‌کند. رایگان، بدون حساب کاربری، بدون تبلیغات و ردیابی.",
    "nav_features": "امکانات",
    "nav_shots": "اسکرین‌شات‌ها",
    "nav_apps": "برنامه‌ها",
    "nav_install": "نصب",
    "nav_faq": "پرسش‌ها",
    "other_lang": "English",
    "h1": "لومن استور",
    "tagline": "برنامه‌های متن‌باز اندروید را مستقیم از منبع پیدا، نصب و به‌روزرسانی کنید.",
    "dl": "دانلود APK",
    "gh": "مشاهده در GitHub",
    "meta_line": "رایگان و متن‌باز. اندروید ۸٫۰ یا بالاتر. بدون حساب کاربری، تبلیغات و ردیابی.",
    "features_h": "هرچه برای به‌روز نگه داشتن برنامه‌های متن‌باز لازم دارید",
    "features": [
        ("اسکن و به‌روزرسانی", "برنامه‌های نصب‌شده را با F-Droid، IzzyOnDroid و نسخه‌های GitHub تطبیق می‌دهد و نسخه‌های جدید را نشان می‌دهد."),
        ("نصب با یک ضربه", "APK مناسب دستگاه شما را دانلود می‌کند و با یک ضربه نصب یا به‌روز می‌کند."),
        ("کاوش در GitHub", "پروژه‌ها را همراه با README، اسکرین‌شات، نسخه‌ها و پروفایل توسعه‌دهندگان مرور کنید."),
        ("منابع دلخواه", "مخزن‌های GitHub، GitLab یا Codeberg را اضافه کنید و از آن‌ها به‌روزرسانی بگیرید."),
        ("حریم خصوصی", "بدون حساب کاربری، تبلیغات و تحلیل‌گر. توکن اختیاری GitHub رمزنگاری‌شده روی دستگاه می‌ماند."),
        ("طراحی زیبا", "تم‌های Material 3، شکل آیکون، پس‌زمینه زنده و پشتیبانی کامل از فارسی و راست‌به‌چپ."),
    ],
    "shots_h": "اسکرین‌شات‌ها",
    "apps_h": "برنامه‌های ایندکس لومن",
    "apps_p": "فهرست برنامه‌های متن‌باز در دسترس از طریق لومن استور. APK را مستقیم دانلود کنید یا ایندکس را داخل برنامه اضافه کنید.",
    "search": "جست‌وجوی برنامه",
    "no_results": "برنامه‌ای با این جست‌وجو پیدا نشد.",
    "empty": "فهرست فعلاً خالی است.",
    "details": "جزئیات",
    "install_h": "نحوه نصب",
    "steps": [
        "آخرین APK را از صفحه Releases دانلود کنید.",
        "فایل را باز کنید و وقتی اندروید پرسید، اجازه نصب از مرورگر یا فایل‌منیجر را بدهید.",
        "لومن استور را باز کنید و اجازه دهید برنامه‌هایتان را اسکن کند.",
    ],
    "verify_h": "بررسی صحت فایل دانلودی",
    "verify_p": "هر ریلیز همراه با چک‌سام SHA-256 و گواهی امضاشده منشأ ساخت منتشر می‌شود.",
    "faq_h": "پرسش‌های متداول",
    "faq": [
        ("آیا لومن استور رایگان است؟", "بله. رایگان و متن‌باز با مجوز GPL-3.0 است."),
        ("چه نسخه‌هایی از اندروید پشتیبانی می‌شود؟", "اندروید ۸٫۰ (API 26) و بالاتر."),
        ("آیا لومن استور ردیابی می‌کند؟", "خیر. هیچ تحلیل‌گر، تبلیغ یا ردیابی وجود ندارد و حساب کاربری لازم نیست. جزئیات را در سیاست حریم خصوصی بخوانید."),
        ("ایندکس لومن چیست؟", "فهرست برنامه‌هایی است که برای لومن استور در مخزن lumenrepo روی GitHub نگهداری می‌شود. این سایت برنامه‌های همان ایندکس را نمایش می‌دهد."),
        ("باگ را کجا گزارش کنم؟", "در GitHub یک Issue باز کنید. مشکلات امنیتی را از تب Security به‌صورت خصوصی گزارش دهید."),
    ],
    "privacy": "سیاست حریم خصوصی",
    "security": "سیاست امنیتی",
    "license": "مجوز GPL-3.0",
    "made_by": "ساخته‌شده توسط deepcodecreate",
}


def e(value):
    return html.escape(str(value or ""), quote=True)


def pick(v):
    if isinstance(v, str):
        return v
    if isinstance(v, dict):
        for k in ("en-US", "en", "en-GB"):
            if k in v:
                r = pick(v[k])
                if r:
                    return r
        for x in v.values():
            r = pick(x)
            if r:
                return r
    if isinstance(v, list):
        for x in v:
            r = pick(x)
            if r:
                return r
    return ""


def pick_file(v):
    if isinstance(v, str):
        return v
    if isinstance(v, dict):
        if isinstance(v.get("name"), str):
            return v["name"]
        for k in ("en-US", "en", "en-GB"):
            if k in v:
                r = pick_file(v[k])
                if r:
                    return r
        for x in v.values():
            r = pick_file(x)
            if r:
                return r
    if isinstance(v, list):
        for x in v:
            r = pick_file(x)
            if r:
                return r
    return ""


def files_of(v):
    if isinstance(v, list):
        return [n for n in (pick_file(x) for x in v) if n]
    if isinstance(v, dict):
        if isinstance(v.get("name"), str):
            return [v["name"]]
        for k in ("en-US", "en", "en-GB"):
            if k in v:
                r = files_of(v[k])
                if r:
                    return r
        for x in v.values():
            r = files_of(x)
            if r:
                return r
    return []


def resolve(name):
    if not name:
        return ""
    if name.startswith("http://") or name.startswith("https://"):
        return name
    path = name if name.startswith("/") else "/" + name
    return BASE + quote(path, safe="/:@%+")


def to_int(v, default=0):
    try:
        return int(v)
    except (TypeError, ValueError):
        return default


def fmt_size(n):
    n = to_int(n)
    if n <= 0:
        return ""
    if n >= 1024 * 1024:
        return f"{n / 1024 / 1024:.1f} MB"
    return f"{max(1, n // 1024)} KB"


def fmt_date(ms):
    ms = to_int(ms)
    if ms <= 0:
        return ""
    try:
        return datetime.fromtimestamp(ms / 1000, tz=timezone.utc).strftime("%Y-%m-%d")
    except (OverflowError, OSError, ValueError):
        return ""


def shorten(text, limit=155):
    text = re.sub(r"\s+", " ", text or "").strip()
    if len(text) <= limit:
        return text
    return text[: limit - 1].rsplit(" ", 1)[0] + "..."


def paragraphs(text):
    parts = [p.strip() for p in re.split(r"\n\s*\n", text or "") if p.strip()]
    return "".join("<p>" + e(p).replace("\n", "<br>") + "</p>" for p in parts)


def load_index():
    if INDEX_FILE:
        return json.loads(Path(INDEX_FILE).read_text(encoding="utf-8"))
    last = None
    for attempt in range(4):
        try:
            req = urllib.request.Request(INDEX_URL, headers={"User-Agent": "lumenstore-site/1.0"})
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.loads(r.read().decode("utf-8"))
        except Exception as exc:
            last = exc
            time.sleep(3 * (attempt + 1))
    print(f"::error::Could not fetch the Lumen index: {last}")
    sys.exit(1)


def parse_apps(data):
    categories = {}
    repo = data.get("repo") or {}
    for cid, cval in (repo.get("categories") or {}).items():
        categories[cid] = pick((cval or {}).get("name")) or cid
    apps = []
    for pkg, entry in (data.get("packages") or {}).items():
        if not isinstance(entry, dict):
            continue
        meta = entry.get("metadata") or {}
        versions = entry.get("versions") or {}
        items = list(versions.values()) if isinstance(versions, dict) else list(versions)
        parsed = []
        for v in items:
            if not isinstance(v, dict):
                continue
            m = v.get("manifest") or {}
            f = v.get("file") or {}
            fname = f.get("name") if isinstance(f, dict) else ""
            if not fname:
                continue
            sdk = m.get("usesSdk") or {}
            parsed.append({
                "code": to_int(m.get("versionCode")),
                "name": str(m.get("versionName") or ""),
                "url": resolve(fname),
                "size": to_int(f.get("size")),
                "sha256": str(f.get("sha256") or ""),
                "min_sdk": to_int(sdk.get("minSdkVersion")),
                "added": to_int(v.get("added")),
            })
        if not parsed:
            continue
        parsed.sort(key=lambda x: x["code"], reverse=True)
        latest = parsed[0]
        name = pick(meta.get("name")) or pkg
        cats = [categories.get(c, c) for c in (meta.get("categories") or []) if isinstance(c, str)]
        shots = [resolve(n) for n in files_of((meta.get("screenshots") or {}).get("phone") if isinstance(meta.get("screenshots"), dict) else None)][:6]
        apps.append({
            "pkg": pkg,
            "name": name,
            "summary": pick(meta.get("summary")),
            "description": pick(meta.get("description")),
            "icon": resolve(pick_file(meta.get("icon"))),
            "latest": latest,
            "versions": parsed[:8],
            "source": pick(meta.get("sourceCode")),
            "website": pick(meta.get("webSite")),
            "license": pick(meta.get("license")),
            "categories": cats,
            "shots": shots,
            "updated": to_int(meta.get("lastUpdated")),
        })
    apps.sort(key=lambda a: a["name"].lower())
    return apps


def gsc_meta():
    f = SRC / "gsc.txt"
    if f.exists():
        token = f.read_text(encoding="utf-8").strip()
        if token:
            return f'<meta name="google-site-verification" content="{e(token)}">'
    return ""


def ld_json(data):
    return '<script type="application/ld+json">' + json.dumps(data, ensure_ascii=False).replace("</", "<\\/") + "</script>"


def shell(t, title, desc, canonical, body, root, alternates=None, ld="", og_type="website"):
    alt = ""
    for hl, url in (alternates or []):
        alt += f'<link rel="alternate" hreflang="{hl}" href="{e(url)}">'
    return f"""<!DOCTYPE html>
<html lang="{t['lang']}" dir="{t['dir']}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{e(title)}</title>
<meta name="description" content="{e(desc)}">
<meta name="robots" content="index, follow, max-image-preview:large">
<meta name="theme-color" content="#2a1b14">
<link rel="canonical" href="{e(canonical)}">
{alt}
<link rel="icon" type="image/png" href="{root}static/icon.png">
<link rel="apple-touch-icon" href="{root}static/icon.png">
<meta property="og:type" content="{og_type}">
<meta property="og:site_name" content="Lumen Store">
<meta property="og:title" content="{e(title)}">
<meta property="og:description" content="{e(desc)}">
<meta property="og:url" content="{e(canonical)}">
<meta property="og:image" content="{SITE_URL}/static/og.png">
<meta name="twitter:card" content="summary_large_image">
<meta name="twitter:title" content="{e(title)}">
<meta name="twitter:description" content="{e(desc)}">
<meta name="twitter:image" content="{SITE_URL}/static/og.png">
{gsc_meta()}
<link rel="stylesheet" href="{root}static/style.css">
{ld}
</head>
<body>
{body}
<script src="{root}static/app.js" defer></script>
</body>
</html>
"""


def header(t, root, home_href, other_href, home=True):
    nav = ""
    if home:
        nav = (
            f'<a href="#features">{t["nav_features"]}</a>'
            f'<a href="#screenshots" class="shots-link">{t["nav_shots"]}</a>'
            f'<a href="#apps">{t["nav_apps"]}</a>'
            f'<a href="#install">{t["nav_install"]}</a>'
            f'<a href="#faq">{t["nav_faq"]}</a>'
        )
    return f"""<header class="top">
<a class="brand" href="{home_href}"><img src="{root}static/icon.png" alt="" width="32" height="32"><span>Lumen Store</span></a>
<nav>{nav}<a href="{REPO}" rel="noopener">GitHub</a><a class="lang" href="{other_href}" hreflang="{'fa' if t['lang'] == 'en' else 'en'}">{t['other_lang']}</a></nav>
</header>"""


def footer(t):
    return f"""<footer class="foot">
<div><strong>Lumen Store</strong><p>{t['license']} · {t['made_by']}</p></div>
<div class="links"><a href="{REPO}/blob/main/PRIVACY.md">{t['privacy']}</a><a href="{REPO}/blob/main/SECURITY.md">{t['security']}</a><a href="{REPO}/issues">Issues</a><a href="{INDEX_REPO}">lumenrepo</a></div>
</footer>"""


def app_card(a, t, href):
    summary = a["summary"] or shorten(a["description"], 110)
    icon = f'<img src="{e(a["icon"])}" alt="" width="56" height="56" loading="lazy" onerror="this.remove()">' if a["icon"] else '<span class="noicon"></span>'
    hay = " ".join([a["name"], a["pkg"], a["summary"], " ".join(a["categories"])]).lower()
    return f"""<a class="card app" href="{href}" data-search="{e(hay)}">
{icon}
<div><h3>{e(a['name'])}</h3><p>{e(shorten(summary, 100))}</p><span class="ver">{e(a['latest']['name'])}</span></div>
</a>"""


def home_page(t, apps, shots, root, home_href, other_href, canonical, alternates, apps_prefix):
    feats = "".join(f'<div class="card"><h3>{e(h)}</h3><p>{e(p)}</p></div>' for h, p in t["features"])
    shots_html = ""
    if shots:
        imgs = "".join(
            f'<img src="{root}static/shots/{e(s)}" alt="Lumen Store screenshot {i + 1}" loading="lazy">'
            for i, s in enumerate(shots)
        )
        shots_html = f'<section id="screenshots"><h2>{t["shots_h"]}</h2><div class="shots">{imgs}</div></section>'
    cards = "".join(app_card(a, t, f"{apps_prefix}{quote(a['pkg'])}/") for a in apps)
    if not apps:
        cards = f'<p class="muted">{t["empty"]}</p>'
    steps = "".join(f"<li>{e(s)}</li>" for s in t["steps"])
    faq = "".join(f"<details><summary>{e(q)}</summary><p>{e(a)}</p></details>" for q, a in t["faq"])
    body = f"""{header(t, root, home_href, other_href)}
<main>
<section class="hero">
<img class="hero-icon" src="{root}static/icon.png" alt="Lumen Store icon" width="112" height="112">
<h1>{t['h1']}</h1>
<p class="lead">{t['tagline']}</p>
<div class="cta"><a class="btn primary" href="{LATEST}" rel="noopener">{t['dl']}</a><a class="btn" href="{REPO}" rel="noopener">{t['gh']}</a></div>
<p class="muted">{t['meta_line']}</p>
</section>
<section id="features"><h2>{t['features_h']}</h2><div class="grid">{feats}</div></section>
{shots_html}
<section id="apps"><h2>{t['apps_h']}</h2><p class="muted">{t['apps_p']}</p>
<input id="q" type="search" placeholder="{t['search']}" aria-label="{t['search']}" autocomplete="off">
<div class="grid apps" id="app-grid">{cards}</div>
<p id="no-results" class="muted" hidden>{t['no_results']}</p>
</section>
<section id="install"><h2>{t['install_h']}</h2><ol class="steps">{steps}</ol>
<h3>{t['verify_h']}</h3><p>{t['verify_p']}</p>
<pre dir="ltr"><code>sha256sum -c SHA256SUMS.txt
gh attestation verify Lumenstore-vX.Y.Z.apk --repo deepcodecreate/lumenstore</code></pre></section>
<section id="faq"><h2>{t['faq_h']}</h2>{faq}</section>
</main>
{footer(t)}"""
    ld = ld_json([
        {
            "@context": "https://schema.org",
            "@type": "SoftwareApplication",
            "name": "Lumen Store",
            "applicationCategory": "UtilitiesApplication",
            "operatingSystem": "Android 8.0+",
            "description": t["desc"],
            "url": canonical,
            "downloadUrl": LATEST,
            "image": SITE_URL + "/static/icon.png",
            "license": "https://www.gnu.org/licenses/gpl-3.0.html",
            "offers": {"@type": "Offer", "price": "0", "priceCurrency": "USD"},
            "author": {"@type": "Person", "name": "deepcodecreate", "url": "https://github.com/deepcodecreate"},
        },
        {
            "@context": "https://schema.org",
            "@type": "FAQPage",
            "mainEntity": [
                {"@type": "Question", "name": q, "acceptedAnswer": {"@type": "Answer", "text": a}}
                for q, a in t["faq"]
            ],
        },
    ])
    return shell(t, t["title"], t["desc"], canonical, body, root, alternates, ld)


def app_page(a, t):
    root = "../../"
    lv = a["latest"]
    canonical = f"{SITE_URL}/apps/{quote(a['pkg'])}/"
    title = t["app_title"].format(name=a["name"])
    desc = shorten(a["summary"] or a["description"] or f"Download {a['name']} for Android from the Lumen index.")
    icon = f'<img class="app-icon" src="{e(a["icon"])}" alt="{e(a["name"])} icon" width="96" height="96" onerror="this.remove()">' if a["icon"] else ""
    buttons = f'<a class="btn primary" href="{e(lv["url"])}" rel="noopener">{t["download_apk"]} {e(lv["name"])}{" (" + fmt_size(lv["size"]) + ")" if lv["size"] else ""}</a>'
    if a["source"]:
        buttons += f'<a class="btn" href="{e(a["source"])}" rel="noopener nofollow">{t["source"]}</a>'
    if a["website"]:
        buttons += f'<a class="btn" href="{e(a["website"])}" rel="noopener nofollow">{t["website"]}</a>'
    rows = [
        (t["package"], a["pkg"]),
        (t["version"], f'{lv["name"]} ({lv["code"]})'),
        (t["min_android"], f'API {lv["min_sdk"]}' if lv["min_sdk"] else ""),
        (t["size"], fmt_size(lv["size"])),
        (t["license_l"], a["license"]),
        (t["updated"], fmt_date(a["updated"]) or fmt_date(lv["added"])),
        ("SHA-256", lv["sha256"]),
    ]
    table = "".join(f"<tr><th>{e(k)}</th><td>{e(v)}</td></tr>" for k, v in rows if v)
    shots = ""
    if a["shots"]:
        shots = f'<h2>{t["screens_h"]}</h2><div class="shots">' + "".join(
            f'<img src="{e(s)}" alt="{e(a["name"])} screenshot {i + 1}" loading="lazy">' for i, s in enumerate(a["shots"])
        ) + "</div>"
    vers = "".join(
        f'<tr><td>{e(v["name"])}</td><td>{e(fmt_date(v["added"]))}</td><td>{e(fmt_size(v["size"]))}</td><td><a href="{e(v["url"])}" rel="noopener">APK</a></td></tr>'
        for v in a["versions"]
    )
    body = f"""{header(t, root, root, root + 'fa/', home=False)}
<main class="app-page">
<p class="crumb"><a href="{root}#apps">{t['back']}</a></p>
<div class="app-head">{icon}<div><h1>{e(a['name'])}</h1><p class="lead">{e(a['summary'])}</p><div class="cta">{buttons}</div></div></div>
<section><h2>{t['details']}</h2><table class="meta" dir="ltr">{table}</table></section>
<section class="desc">{paragraphs(a['description'])}</section>
{shots}
<section><h2>{t['versions_h']}</h2><table class="meta" dir="ltr"><tr><th>{t['version']}</th><th>{t['updated']}</th><th>{t['size']}</th><th></th></tr>{vers}</table></section>
</main>
{footer(t)}"""
    ld = ld_json({
        "@context": "https://schema.org",
        "@type": "SoftwareApplication",
        "name": a["name"],
        "applicationCategory": "UtilitiesApplication",
        "operatingSystem": "Android",
        "description": desc,
        "softwareVersion": lv["name"],
        "downloadUrl": lv["url"],
        "url": canonical,
        "image": a["icon"] or SITE_URL + "/static/icon.png",
        "offers": {"@type": "Offer", "price": "0", "priceCurrency": "USD"},
    })
    return shell(t, title, desc, canonical, body, root, None, ld, "article"), canonical


def write(path, content):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def main():
    data = load_index()
    apps = parse_apps(data)
    if not apps:
        print("::warning::The Lumen index produced no apps")
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)
    shutil.copytree(SRC / "static", OUT / "static")
    shots = []
    shots_dir = OUT / "static" / "shots"
    shots_dir.mkdir(parents=True, exist_ok=True)
    for i in range(1, 6):
        s = ROOT / ".github" / "assets" / f"screen{i}.png"
        if s.exists():
            shutil.copy(s, shots_dir / s.name)
            shots.append(s.name)
    en_url = SITE_URL + "/"
    fa_url = SITE_URL + "/fa/"
    alts = [("en", en_url), ("fa", fa_url), ("x-default", en_url)]
    write(OUT / "index.html", home_page(EN, apps, shots, "", "./", "fa/", en_url, alts, "apps/"))
    write(OUT / "fa" / "index.html", home_page(FA, apps, shots, "../", "./", "../", fa_url, alts, "../apps/"))
    urls = [(en_url, TODAY), (fa_url, TODAY)]
    for a in apps:
        page, canonical = app_page(a, EN)
        write(OUT / "apps" / a["pkg"] / "index.html", page)
        urls.append((canonical, fmt_date(a["updated"]) or TODAY))
    sitemap = '<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n'
    for u, d in urls:
        sitemap += f"<url><loc>{e(u)}</loc><lastmod>{d}</lastmod></url>\n"
    sitemap += "</urlset>\n"
    write(OUT / "sitemap.xml", sitemap)
    write(OUT / "robots.txt", f"User-agent: *\nAllow: /\n\nSitemap: {SITE_URL}/sitemap.xml\n")
    write(OUT / "apps.json", json.dumps([
        {"package": a["pkg"], "name": a["name"], "summary": a["summary"], "version": a["latest"]["name"], "apk": a["latest"]["url"]}
        for a in apps
    ], ensure_ascii=False, indent=2))
    write(OUT / ".nojekyll", "")
    notfound = shell(EN, "Page not found - Lumen Store", "Page not found", SITE_URL + "/404.html",
                     f'{header(EN, "/lumenstore/".replace("/lumenstore/", SITE_URL.split("//", 1)[-1].split("/", 1)[1:] and "/" + SITE_URL.split("//", 1)[-1].split("/", 1)[1] + "/" or "/"), SITE_URL + "/", SITE_URL + "/fa/", home=False)}<main class="center"><h1>404</h1><p class="lead">This page does not exist.</p><a class="btn primary" href="{SITE_URL}/">Lumen Store</a></main>',
                     SITE_URL + "/")
    write(OUT / "404.html", notfound)
    print(f"Built {len(apps)} apps")


if __name__ == "__main__":
    main()
