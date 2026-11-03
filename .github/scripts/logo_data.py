"""Datos de los bancos de logos que van dentro de la app (app/src/main/assets).

Así el índice de logos sale siempre igual (en la tele y en el móvil) y no depende de que GitHub, Wikimedia o
iptv-org respondan en casa. Cada parte es independiente: si una falla, se avisa y la app la pedirá por internet.
"""
import json, os, subprocess, sys, tempfile, time, urllib.parse, urllib.request

ASSETS = "app/src/main/assets"
UA = "MiIPTV/1.0 (https://github.com/jesusvprado-cmd/MI-IPTV; Android TV)"
IPTV_COUNTRIES = {"ES", "AD", "PT", "GB", "UK", "US", "FR", "IT", "DE", "MX", "AR", "INT"}


def warn(msg):
    print("::warning::" + msg)


def fetch(url, tries=5):
    for attempt in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=60) as r:
                return r.read()
        except Exception as e:  # noqa
            last = e
            time.sleep(2 + attempt * 3)
    raise RuntimeError(f"{url}: {last}")


def tv_logos():
    d = tempfile.mkdtemp()
    subprocess.run(["git", "clone", "-q", "--filter=blob:none", "--no-checkout", "--depth", "1",
                    "https://github.com/tv-logo/tv-logos.git", d], check=True)
    out = subprocess.run(["git", "-C", d, "ls-tree", "-r", "--name-only", "HEAD"], check=True,
                         capture_output=True, text=True).stdout.split("\n")
    paths = [p for p in out if p.lower().endswith(".png")]
    if len(paths) < 5000:
        raise RuntimeError(f"solo {len(paths)} logos")
    with open(os.path.join(ASSETS, "tvlogos.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(paths) + "\n")
    print("tv-logos:", len(paths))


def commons():
    api = "https://commons.wikimedia.org/w/api.php?format=json&formatversion=2&"
    out, seen, level, depth = [], set(), ["Category:SVG logos of television channels of Spain"], 0
    while level and depth <= 2:
        nxt = []
        for cat in level:
            if cat in seen:
                continue
            seen.add(cat)
            if len(seen) > 120:
                continue
            cont = None
            while True:
                q = "action=query&list=categorymembers&cmtype=file|subcat&cmlimit=500&cmtitle=" + urllib.parse.quote(cat)
                if cont:
                    q += "&cmcontinue=" + urllib.parse.quote(cont)
                o = json.loads(fetch(api + q))
                for m in o.get("query", {}).get("categorymembers", []):
                    if m.get("ns") == 14:
                        nxt.append(m["title"])
                    elif m.get("ns") == 6:
                        out.append(m["title"])
                cont = (o.get("continue") or {}).get("cmcontinue")
                if not cont:
                    break
        level, depth = nxt, depth + 1
    if len(out) < 50:
        raise RuntimeError(f"solo {len(out)} logos")
    with open(os.path.join(ASSETS, "commons_titles.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(out) + "\n")
    print("Wikimedia:", len(out), "archivos en", len(seen), "categorías")


def iptv_org():
    channels = json.loads(fetch("https://iptv-org.github.io/api/channels.json"))
    logos = json.loads(fetch("https://iptv-org.github.io/api/logos.json"))
    keep = [{k: c.get(k) for k in ("id", "name", "country", "alt_names")}
            for c in channels if c.get("country") in IPTV_COUNTRIES and c.get("id")]
    ids = {c["id"] for c in keep}
    lg = [{k: l.get(k) for k in ("channel", "url", "width", "format")} for l in logos if l.get("channel") in ids]
    if len(keep) < 1000 or len(lg) < 1000:
        raise RuntimeError(f"pocos datos ({len(keep)} canales, {len(lg)} logos)")
    with open(os.path.join(ASSETS, "iptv_channels.json"), "w", encoding="utf-8") as f:
        json.dump(keep, f, ensure_ascii=False, separators=(",", ":"))
    with open(os.path.join(ASSETS, "iptv_logos.json"), "w", encoding="utf-8") as f:
        json.dump(lg, f, ensure_ascii=False, separators=(",", ":"))
    print("iptv-org:", len(keep), "canales,", len(lg), "logos")


# Los datos van guardados en el repositorio: así los logos no cambian solos cuando cambian los bancos.
# Solo se descargan si faltan, o todos de nuevo con LOGOS_REFRESCAR=1 (y entonces hay que revisar
# la prueba de logos, que avisará de cada canal cuyo logo cambie).
FILES = {tv_logos: "tvlogos.txt", commons: "commons_titles.txt", iptv_org: "iptv_channels.json"}
refresh = os.environ.get("LOGOS_REFRESCAR") == "1"
for part, name in FILES.items():
    if not refresh and os.path.exists(os.path.join(ASSETS, name)):
        print(f"{name}: se usa la copia del repositorio")
        continue
    try:
        part()
    except Exception as e:
        warn(f"Datos de logos ({part.__name__}) no descargados: {e}")
sys.exit(0)
