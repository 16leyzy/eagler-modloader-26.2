#!/usr/bin/env python3
"""Copy the assets/ and data/ of every mod (including jar-in-jar mods) into the game's resources,
so they are packed into assets.epk with the vanilla files.

Usage: merge-assets.py RESOURCES_DIR MOD_JAR...
Mod files are layered over vanilla ones, like a mod resource pack sits above the vanilla pack.
A mod's other files (its icon, config defaults and so on, but not classes) go to
eml-mods/<mod id>/..., where the browser loader finds them for ModContainer.findPath().
Language files, fonts, sounds.json, atlases and tags that already exist are combined with the
mod's, as Minecraft does across resource and data packs (a mod's font file replacing the game's left
every letter blank). Mods' META-INF/services files (Java ServiceLoader registrations) are merged into the game's, so the
browser compiler builds those services in (libraries such as Collective and ukulib look up their
platform code this way).
"""
import io, json, os, sys, zipfile

def mod_id(zf):
    try:
        return json.loads(zf.read("fabric.mod.json").decode("utf-8", "replace"), strict=False).get("id")
    except Exception:
        return None

def merge_json(n, old_bytes, new_bytes):
    """Minecraft combines these files across resource packs and data packs instead of replacing them;
    do the same when a mod ships one the game (or another mod) already has. None = just replace."""
    parts = n.split("/")
    kind = None
    if len(parts) == 4 and parts[0] == "assets" and parts[2] == "lang" and n.endswith(".json"): kind = "lang"
    elif len(parts) == 4 and parts[0] == "assets" and parts[2] == "font" and n.endswith(".json"): kind = "font"
    elif len(parts) == 3 and parts[0] == "assets" and parts[2] == "sounds.json": kind = "sounds"
    elif len(parts) == 4 and parts[0] == "assets" and parts[2] == "atlases" and n.endswith(".json"): kind = "atlas"
    elif len(parts) >= 4 and parts[0] == "data" and parts[2] == "tags" and n.endswith(".json"): kind = "tag"
    if kind is None:
        return None
    try:
        old = json.loads(old_bytes.decode("utf-8-sig"), strict=False)
        new = json.loads(new_bytes.decode("utf-8-sig"), strict=False)
    except Exception:
        return None
    if not isinstance(old, dict) or not isinstance(new, dict):
        return None
    if kind == "lang":
        merged = dict(old); merged.update(new)
    elif kind == "font":
        # the mod's providers are checked first (it's the higher pack), then the game's
        merged = dict(old); merged["providers"] = list(new.get("providers", [])) + list(old.get("providers", []))
    elif kind == "sounds":
        merged = dict(old)
        for k, v in new.items():
            if k in merged and isinstance(v, dict) and isinstance(merged[k], dict) and not v.get("replace"):
                e = dict(merged[k]); e.update({x: y for x, y in v.items() if x != "sounds"})
                e["sounds"] = list(merged[k].get("sounds", [])) + list(v.get("sounds", []))
                merged[k] = e
            else:
                merged[k] = v
    elif kind == "atlas":
        merged = dict(old); merged["sources"] = list(old.get("sources", [])) + list(new.get("sources", []))
    else:  # tag
        if new.get("replace"):
            return None
        vals = list(old.get("values", []))
        for v in new.get("values", []):
            if v not in vals:
                vals.append(v)
        merged = dict(old); merged["values"] = vals
    return json.dumps(merged, indent=1, ensure_ascii=False).encode("utf-8")

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
            if os.path.exists(dest):
                merged = merge_json(n, open(dest, "rb").read(), zf.read(n))
                if merged is not None:
                    with open(dest, "wb") as f:
                        f.write(merged)
                    stats["merged"] += 1
                    continue
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
    stats = {"added": 0, "replaced": 0, "merged": 0, "own": 0, "services": 0}
    for jar in sys.argv[2:]:
        with zipfile.ZipFile(jar) as zf:
            walk(zf, os.path.basename(jar), out, stats)
    print("[assets] %d mod files added, %d replaced, %d merged (fonts, language files, sounds, atlases, tags), %d other mod files, %d service registrations"
          % (stats["added"], stats["replaced"], stats["merged"], stats["own"], stats["services"]))

if __name__ == "__main__":
    main()
