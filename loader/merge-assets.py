#!/usr/bin/env python3
"""Copy the assets/ and data/ of every mod (including jar-in-jar mods) into the game's resources,
so they are packed into assets.epk with the vanilla files.

Usage: merge-assets.py RESOURCES_DIR MOD_JAR...
Mod files are layered over vanilla ones, like a mod resource pack sits above the vanilla pack.
A mod's other files (its icon, config defaults and so on, but not classes) go to
eml-mods/<mod id>/..., where the browser loader finds them for ModContainer.findPath().
Mods' META-INF/services files (Java ServiceLoader registrations) are merged into the game's, so the
browser compiler builds those services in (libraries such as Collective and ukulib look up their
platform code this way).
"""
import io, json, os, sys, zipfile

def mod_id(zf):
    try:
        return json.loads(zf.read("fabric.mod.json").decode("utf-8", "replace"), strict=False).get("id")
    except Exception:
        return None

def walk(zf, label, out, stats):
    mid = mod_id(zf)
    for info in zf.infolist():
        n = info.filename
        if n.endswith("/"):
            continue
        if n.startswith("META-INF/jars/") and n.endswith(".jar"):
            with zipfile.ZipFile(io.BytesIO(zf.read(n))) as inner:
                walk(inner, label + "!" + os.path.basename(n), out, stats)
            continue
        if n.startswith("META-INF/services/") and n.count("/") == 2:
            dest = os.path.join(out, n)
            have = open(dest, encoding="utf-8").read().split("\n") if os.path.exists(dest) else []
            lines = [l.split("#", 1)[0].strip() for l in zf.read(n).decode("utf-8", "replace").split("\n")]
            new = [l for l in lines if l and l not in have]
            if new:
                os.makedirs(os.path.dirname(dest), exist_ok=True)
                with open(dest, "a", encoding="utf-8") as f:
                    if have and have[-1] != "":
                        f.write("\n")
                    f.write("\n".join(new) + "\n")
                stats["services"] += len(new)
            continue
        if n.startswith("assets/") or n.startswith("data/"):
            dest = os.path.join(out, n)
            stats["replaced" if os.path.exists(dest) else "added"] += 1
        elif mid and not n.endswith(".class") and not n.startswith("META-INF/") and ".." not in n.split("/"):
            dest = os.path.join(out, "eml-mods", mid, n)
            stats["own"] += 1
        else:
            continue
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        with open(dest, "wb") as f:
            f.write(zf.read(n))

def main():
    out = sys.argv[1]
    stats = {"added": 0, "replaced": 0, "own": 0, "services": 0}
    for jar in sys.argv[2:]:
        with zipfile.ZipFile(jar) as zf:
            walk(zf, os.path.basename(jar), out, stats)
    print("[assets] %d mod files added, %d vanilla files replaced, %d other mod files, %d service registrations" % (stats["added"], stats["replaced"], stats["own"], stats["services"]))

if __name__ == "__main__":
    main()
