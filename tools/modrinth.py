#!/usr/bin/env python3
"""Fabric mods from Modrinth for the build.

  modrinth.py search GAME_VERSION TERM [TERM ...]
      For each term, list matching Fabric mods that have a version for GAME_VERSION.

  modrinth.py download GAME_VERSION LIST_FILE MODS_DIR
      LIST_FILE has one Modrinth project per line: its slug (from the mod's URL), optionally
      "slug@version-number" to pin a version. '#' starts a comment. For each project, the newest Fabric
      version for GAME_VERSION is downloaded into MODS_DIR, with the mods it requires. Mods already in
      MODS_DIR (matched by file hash) aren't downloaded again. Stops with an error if a project has no
      Fabric version for GAME_VERSION.
"""
import hashlib, json, os, sys, time, urllib.parse, urllib.request

API = "https://api.modrinth.com/v2"
UA = "eagler-modloader-26.2 build (github.com/omigabeta/eagler-modloader-26.2)"


def get(path, data=None):
    url = path if path.startswith("http") else API + path
    body = json.dumps(data).encode() if data is not None else None
    for attempt in range(5):
        try:
            req = urllib.request.Request(url, data=body, headers={"User-Agent": UA, "Content-Type": "application/json"})
            with urllib.request.urlopen(req, timeout=60) as r:
                raw = r.read()
            return json.loads(raw) if not url.startswith("https://cdn.") else raw
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            if attempt == 4:
                raise
        except Exception:
            if attempt == 4:
                raise
        time.sleep(3 * (attempt + 1))


def q(v):
    return urllib.parse.quote(json.dumps(v))


def fabric_versions(project, game):
    return get("/project/%s/version?loaders=%s&game_versions=%s" % (project, q(["fabric"]), q([game]))) or []


def search(game, terms):
    for term in terms:
        facets = [["versions:" + game], ["categories:fabric"], ["project_type:mod"]]
        res = get("/search?query=%s&facets=%s&limit=10" % (urllib.parse.quote(term), q(facets)))
        print("== %s" % term)
        for h in (res or {}).get("hits", []):
            print("  %-28s %-36s %9d dl  client=%s  %s" % (h["slug"], h["title"][:36], h["downloads"], h.get("client_side"), h["description"][:90]))


def download(game, list_file, mods_dir):
    os.makedirs(mods_dir, exist_ok=True)
    have = {}  # project id -> file name, for jars already in mods/
    hashes = {}
    for f in os.listdir(mods_dir):
        if f.endswith(".jar"):
            hashes[hashlib.sha1(open(os.path.join(mods_dir, f), "rb").read()).hexdigest()] = f
    if hashes:
        found = get("/version_files", {"hashes": list(hashes), "algorithm": "sha1"}) or {}
        for h, v in found.items():
            have[v["project_id"]] = hashes[h]
    wanted = []
    for line in open(list_file):
        line = line.split("#", 1)[0].strip()
        if line:
            slug, _, pin = line.partition("@")
            wanted.append((slug.strip(), pin.strip() or None, None))
    errors, done = [], set()
    while wanted:
        slug, pin, why = wanted.pop(0)
        proj = get("/project/" + slug)
        if proj is None:
            errors.append("%s: no such Modrinth project" % slug)
            continue
        pid = proj["id"]
        if pid in done:
            continue
        done.add(pid)
        if pid in have:
            print("[modrinth] %s: already in mods/ (%s)" % (proj["title"], have[pid]))
            continue
        versions = fabric_versions(pid, game)
        if pin:
            versions = [v for v in versions if v["version_number"] == pin or v["id"] == pin]
        if not versions:
            errors.append("%s (%s): no Fabric version for Minecraft %s%s%s" % (proj["title"], slug, game,
                          " matching " + pin if pin else "", ", needed by " + why if why else ""))
            continue
        v = versions[0]
        f = next((x for x in v["files"] if x.get("primary")), v["files"][0])
        data = get(f["url"])
        if hashlib.sha1(data).hexdigest() != f["hashes"]["sha1"]:
            errors.append("%s: download didn't match its hash" % slug)
            continue
        with open(os.path.join(mods_dir, f["filename"]), "wb") as out:
            out.write(data)
        print("[modrinth] %s %s -> %s%s" % (proj["title"], v["version_number"], f["filename"], "  (needed by %s)" % why if why else ""))
        for d in v.get("dependencies", []):
            if d.get("dependency_type") == "required" and d.get("project_id") and d["project_id"] not in done:
                wanted.append((d["project_id"], None, proj["title"]))
    if errors:
        print("\n[modrinth] problems:")
        for e in errors:
            print("  - " + e)
        sys.exit(1)


if __name__ == "__main__":
    if len(sys.argv) >= 4 and sys.argv[1] == "search":
        search(sys.argv[2], sys.argv[3:])
    elif len(sys.argv) == 5 and sys.argv[1] == "download":
        download(sys.argv[2], sys.argv[3], sys.argv[4])
    else:
        sys.exit(__doc__)
