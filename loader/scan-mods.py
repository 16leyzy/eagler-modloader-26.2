#!/usr/bin/env python3
"""Quick compatibility scan of the jars in mods/, run before anything is built (takes seconds).

Usage: scan-mods.py MODS_DIR REPORT_MD [WORKSPACE]
With WORKSPACE (the Eaglercraft 26.2 workspace), classes Eaglercraft provides in the browser (its
LWJGL stand-ins and Java emulation) aren't reported.
Stops the build (exit 1) for jars that can't be loaded at all: not a Fabric mod (Forge, NeoForge,
Quilt or no mod metadata) or not a valid jar. Everything else is a warning: code that usually can't
run in a browser (native libraries, desktop graphics, raw memory, raw network connections, desktop
Java), with the mod it's in. Warnings don't stop the build; whether the mod still works depends on
whether that code actually runs. The report is Markdown, for the build summary and release notes.
"""
import io, json, os, struct, sys, zipfile

# class-name prefixes (internal form) -> what they mean for a browser build
RISKS = [
    ("desktop graphics", ("org/lwjgl/opengl/", "org/lwjgl/vulkan/", "org/lwjgl/glfw/")),
    ("raw memory access", ("org/lwjgl/system/MemoryUtil", "org/lwjgl/system/MemoryStack", "sun/misc/Unsafe", "jdk/internal/misc/Unsafe")),
    ("raw network connections", ("java/net/Socket", "java/net/ServerSocket", "java/net/DatagramSocket", "java/net/MulticastSocket",
                                 "java/nio/channels/SocketChannel", "java/nio/channels/ServerSocketChannel", "java/nio/channels/DatagramChannel")),
    ("running other programs", ("java/lang/ProcessBuilder", "java/lang/Process")),
    ("desktop-only Java (windows, sound hardware, system monitoring)", ("java/awt/", "javax/swing/", "javax/sound/", "java/lang/management/")),
]
NATIVE_EXT = (".so", ".dll", ".dylib", ".jnilib")

def matches(name, pattern):
    """'pkg/' matches the whole package; 'pkg/Class' matches that class and its inner classes."""
    if pattern.endswith("/"):
        return name.startswith(pattern)
    return name == pattern or name.startswith(pattern + "$")

def class_refs(b):
    """Class names referenced from a class file's constant pool."""
    if b[:4] != b"\xca\xfe\xba\xbe":
        return set()
    n = struct.unpack(">H", b[8:10])[0]
    utf, classes = {}, []
    i, k = 10, 1
    while k < n:
        tag = b[i]
        if tag == 1:
            ln = struct.unpack(">H", b[i + 1:i + 3])[0]
            utf[k] = b[i + 3:i + 3 + ln].decode("utf-8", "replace"); i += 3 + ln
        elif tag == 7:
            classes.append(struct.unpack(">H", b[i + 1:i + 3])[0]); i += 3
        elif tag in (3, 4): i += 5
        elif tag in (5, 6): i += 9; k += 1
        elif tag in (8, 16, 19, 20): i += 3
        elif tag in (9, 10, 11, 12, 17, 18): i += 5
        elif tag == 15: i += 4
        else: return set()
        k += 1
    out = set()
    for c in classes:
        s = utf.get(c, "")
        out.add(s.lstrip("[").removeprefix("L").removesuffix(";"))
    return out

def loader_kind(zf):
    names = set(zf.namelist())
    if "fabric.mod.json" in names: return "fabric"
    if "quilt.mod.json" in names: return "Quilt"
    if "META-INF/neoforge.mods.toml" in names: return "NeoForge"
    if "META-INF/mods.toml" in names or "mcmod.info" in names: return "Forge"
    return None

def scan(zf, label, mods, errors):
    kind = loader_kind(zf)
    if kind != "fabric":
        if kind is None:
            errors.append("`%s` isn't a mod (no fabric.mod.json)." % label)
        else:
            errors.append("`%s` is a %s mod. Only Fabric mods for Minecraft 26.2 can be built in." % (label, kind))
        return
    try:
        meta = json.loads(zf.read("fabric.mod.json").decode("utf-8", "replace"), strict=False)
    except Exception as e:
        errors.append("`%s` has an unreadable fabric.mod.json (%s)." % (label, e))
        return
    mod = {"id": meta.get("id", "?"), "name": meta.get("name") or meta.get("id", "?"), "file": label,
           "risks": {}, "natives": []}
    mods.append(mod)
    for info in zf.infolist():
        n = info.filename
        if n.endswith("/"): continue
        if n.startswith("META-INF/jars/") and n.endswith(".jar"):
            try:
                with zipfile.ZipFile(io.BytesIO(zf.read(n))) as inner:
                    scan(inner, label + " → " + os.path.basename(n), mods, errors)
            except zipfile.BadZipFile:
                errors.append("`%s` contains a broken jar: %s" % (label, n))
            continue
        if n.lower().endswith(NATIVE_EXT):
            mod["natives"].append(n)
        elif n.endswith(".class"):
            refs = class_refs(zf.read(n))
            for what, prefixes in RISKS:
                hit = sorted(r for r in refs if any(matches(r, x) for x in prefixes))
                if hit:
                    mod["risks"].setdefault(what, set()).update(hit)

def eagler_provided(ws):
    """Class names (internal form) that Eaglercraft's browser runtime provides itself."""
    out = set()
    if not ws: return out
    roots = [(os.path.join(ws, "platform-teavm/src/main/java"), None),
             (os.path.join(ws, "teavm-compat/src/main/java/net/lax1dude/eaglercraft/v1_8/teavmcompat"), "T")]
    for root, prefix in roots:
        for d, _, files in os.walk(root):
            for f in files:
                if not f.endswith(".java"): continue
                rel = os.path.relpath(os.path.join(d, f[:-5]), root).replace(os.sep, "/")
                if prefix:  # teavmcompat/java/net/TSocket.java emulates java/net/Socket
                    pkg, _, name = rel.rpartition("/")
                    if not name.startswith(prefix): continue
                    rel = pkg + "/" + name[len(prefix):]
                out.add(rel)
    return out

def main():
    mods_dir, report = sys.argv[1:3]
    provided = eagler_provided(sys.argv[3] if len(sys.argv) > 3 else None)
    mods, errors = [], []
    jars = sorted(f for f in os.listdir(mods_dir) if f.endswith(".jar")) if os.path.isdir(mods_dir) else []
    for f in jars:
        try:
            with zipfile.ZipFile(os.path.join(mods_dir, f)) as zf:
                scan(zf, f, mods, errors)
        except zipfile.BadZipFile:
            errors.append("`%s` isn't a valid jar file." % f)
    seen, lines = set(), []
    for m in mods:
        if m["id"] in seen: continue  # the same library bundled in several mods
        seen.add(m["id"])
        notes = []
        if m["natives"]:
            notes.append("native code (%d file%s), which can't run in a browser" % (len(m["natives"]), "" if len(m["natives"]) == 1 else "s"))
        for what, refs in m["risks"].items():
            # Eaglercraft's stand-ins for LWJGL and desktop Java work; its network and process classes
            # are only there so code compiles (connections go through Eaglercraft's own networking)
            if what not in ("raw network connections", "running other programs"):
                refs = {r for r in refs if r.split("$")[0] not in provided}
            if not refs: continue
            ex = ", ".join(r.split("/")[-1] for r in sorted(refs)[:3])
            notes.append("%s (%s)" % (what, ex))
        if notes:
            lines.append("- **%s** (`%s`): uses %s." % (m["name"], m["file"], "; ".join(notes)))
    out = ["## Mod check", ""]
    if errors:
        out += ["These jars can't be built in:", ""] + ["- " + e for e in errors] + [""]
    out.append("%d jar%s in mods/, %d mod%s including bundled ones." % (len(jars), "" if len(jars) == 1 else "s", len(seen), "" if len(seen) == 1 else "s"))
    if lines:
        out += ["", "Code that usually can't run in a browser. These mods may lose features or crash when that code runs:", ""] + lines
    elif not errors:
        out += ["", "Nothing browser-incompatible found in the jars."]
    text = "\n".join(out) + "\n"
    with open(report, "w") as fh:
        fh.write(text)
    print(text)
    sys.exit(1 if errors else 0)

if __name__ == "__main__":
    main()
