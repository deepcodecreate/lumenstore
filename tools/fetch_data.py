import datetime
import json
import os
import re
import sys
import time
import urllib.request

LUMEN = "https://raw.githubusercontent.com/deepcodecreate/lumenrepo/main/index-v2.json"
SITE = "https://deepcodecreate.github.io/lumenstore"
APP_REPO = "deepcodecreate/lumenstore"
OUT = sys.argv[1] if len(sys.argv) > 1 else "site"
TOKEN = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN") or ""
GH_RE = re.compile(r"github\.com/([^/\s]+)/([^/#?\s]+)", re.I)


def get(url, headers=None, tries=4):
    last = None
    for n in range(tries):
        try:
            req = urllib.request.Request(url, headers=dict({"User-Agent": "lumenstore-site"}, **(headers or {})))
            with urllib.request.urlopen(req, timeout=60) as r:
                return r.read()
        except Exception as e:
            last = e
            time.sleep(3 * (n + 1))
    raise last


def slug_of(meta):
    for key in ("releases", "sourceCode", "webSite", "changelog", "issueTracker"):
        m = GH_RE.search(meta.get(key) or "")
        if m:
            return "%s/%s" % (m.group(1), re.sub(r"\.git$", "", m.group(2), flags=re.I))
    return None


def pick_apk(rel):
    apks = [a for a in rel.get("assets") or [] if re.search(r"\.apk$", a.get("name", ""), re.I) and not re.search(r"debug|unsigned|test", a.get("name", ""), re.I)]
    if not apks:
        return None
    pref = next((a for a in apks if re.search(r"universal|all", a["name"], re.I)), None) or next((a for a in apks if re.search(r"arm64", a["name"], re.I)), None) or apks[0]
    return {"name": rel.get("tag_name") or rel.get("name") or "latest", "size": pref.get("size") or 0, "url": pref.get("browser_download_url"), "pre": bool(rel.get("prerelease")), "notes": (rel.get("body") or "")[:4000]}


def latest_release(slug):
    headers = {"Accept": "application/vnd.github+json"}
    if TOKEN:
        headers["Authorization"] = "Bearer " + TOKEN
    rels = [r for r in json.loads(get("https://api.github.com/repos/%s/releases?per_page=10" % slug, headers).decode("utf-8")) if not r.get("draft")]
    for group in ([r for r in rels if not r.get("prerelease")], rels):
        for rel in group:
            v = pick_apk(rel)
            if v:
                return v
    return None


def main():
    data_dir = os.path.join(OUT, "data")
    os.makedirs(data_dir, exist_ok=True)
    slugs = {APP_REPO.lower(): APP_REPO}
    packages = []
    local = os.environ.get("LUMEN_INDEX_FILE")
    try:
        raw = open(local, "rb").read() if local else get(LUMEN)
        index = json.loads(raw.decode("utf-8"))
        with open(os.path.join(data_dir, "lumen-index-v2.json"), "wb") as f:
            f.write(raw)
        for pkg, entry in (index.get("packages") or {}).items():
            packages.append(pkg)
            meta = entry.get("metadata") or {}
            has_files = any(isinstance(v, dict) and (v.get("file") or {}).get("name") and (v.get("manifest") or {}).get("versionCode") is not None for v in (entry.get("versions") or {}).values())
            s = None if has_files else slug_of(meta)
            if s:
                slugs.setdefault(s.lower(), s)
        print("lumen index:", len(packages), "apps")
    except Exception as e:
        print("WARNING: could not read the Lumen index:", e, file=sys.stderr)
    releases = {}
    if not os.environ.get("SKIP_RELEASES"):
        for key, slug in sorted(slugs.items()):
            try:
                v = latest_release(slug)
                if v:
                    releases[key] = v
            except Exception as e:
                print("WARNING: releases of", slug, "failed:", e, file=sys.stderr)
    with open(os.path.join(data_dir, "releases.json"), "w", encoding="utf-8") as f:
        json.dump(releases, f, ensure_ascii=False, separators=(",", ":"))
    print("releases:", len(releases), "of", len(slugs))
    today = datetime.date.today().isoformat()
    urls = [SITE + "/", SITE + "/submit.html", SITE + "/danger.html"] + ["%s/app.html?id=%s" % (SITE, p) for p in packages if re.match(r"^[A-Za-z0-9._]+$", p)]
    body = "".join("<url><loc>%s</loc><lastmod>%s</lastmod></url>" % (u.replace("&", "&amp;"), today) for u in urls)
    with open(os.path.join(OUT, "sitemap.xml"), "w", encoding="utf-8") as f:
        f.write('<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">%s</urlset>\n' % body)


main()
