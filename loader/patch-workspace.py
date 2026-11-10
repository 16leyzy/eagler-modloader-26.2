#!/usr/bin/env python3
"""Small patches to the Eaglercraft 26.2 workspace for mods (run by weave.sh; safe to run twice).

Usage: patch-workspace.py WORKSPACE
- ServiceLoader.findFirst(): registers the donor in loader/teavm-compat with Eaglercraft's JdkMethodInjector.
- glfwGetMouseButton(): added to Eaglercraft's GLFW stand-in.
- ServerFunctionLibrary: mods' datapack functions (.mcfunction) are compiled right away instead of as
  async tasks the loader then waits on, which the browser's single-threaded server can't do (vanilla
  ships no functions, so Eaglercraft never hit this); a function that fails to compile is logged.
- StandaloneTeaVMLinker: classes or methods the browser doesn't have, used by mods, no longer stop the
  build. TeaVM compiles each such spot to throw NoClassDefFoundError/NoSuchMethodError if it runs; the
  build log lists them ("was not found"). Any other compiler error still stops the build.
"""
import os, sys


def patch(path, old, new, what):
    s = open(path).read()
    if new in s:
        return
    if old not in s:
        sys.exit("eagler-modloader: %s changed; check the %s patch" % (os.path.basename(path), what))
    open(path, "w").write(s.replace(old, new, 1))


def main():
    ws = sys.argv[1]
    inj = os.path.join(ws, "teavm-compat/src/main/java/net/lax1dude/eaglercraft/v1_8/teavmcompat/plugin/JdkMethodInjector.java")
    anchor = '\t\t{ "Base64Inject", "java.util.Base64", false },\n'
    patch(inj, anchor, anchor + '\t\t{ "ServiceLoaderInject", "java.util.ServiceLoader", false },\n', "ServiceLoader donor")

    glfw = os.path.join(ws, "platform-teavm/src/main/java/org/lwjgl/glfw/GLFW.java")
    anchor = "\tpublic static String glfwGetKeyName("
    patch(glfw, anchor, "\tpublic static int glfwGetMouseButton(final long window, final int button) {\n"
          "\t\treturn PlatformInput.mouseIsButtonDown(button) ? 1 : 0; // added by eagler-modloader\n\t}\n\n" + anchor,
          "glfwGetMouseButton")

    sfl = os.path.join(ws, "game/src/main/java/net/minecraft/server/ServerFunctionLibrary.java")
    old = """               result.put(id, CompletableFuture.supplyAsync(() -> {
                  List<String> lines = readLines(entry.getValue());
                  return CommandFunction.fromLines(id, this.dispatcher, compilationContext, lines);
               }, taskExecutor));"""
    new = """               // eagler-modloader: compiled right away; the browser's server can't wait on a task
               CompletableFuture<CommandFunction<CommandSourceStack>> compiled = new CompletableFuture<>();
               try {
                  compiled.complete(CommandFunction.fromLines(id, this.dispatcher, compilationContext, readLines(entry.getValue())));
               } catch (Throwable t) {
                  compiled.completeExceptionally(t);
               }
               result.put(id, compiled);"""
    patch(sfl, old, new, "datapack functions")

    linker = os.path.join(ws, "wasm-toolchain/StandaloneTeaVMLinker.java")
    old = """        if (!result.getProblems().getSevereProblems().isEmpty()) {
            throw new IllegalStateException("TeaVM reported severe problems");
        }"""
    new = """        int missing = 0, other = 0;
        for (var p : result.getProblems().getSevereProblems()) {
            if (String.valueOf(p.getText()).contains("was not found")) missing++; else other++;
        }
        if (missing > 0) {
            System.out.println("[eagler-modloader] " + missing + " uses of classes or methods the browser doesn't have"
                + " (listed above as 'was not found'); that code throws an error if it runs");
        }
        if (other > 0) {
            throw new IllegalStateException("TeaVM reported severe problems");
        }"""
    patch(linker, old, new, "missing-class handling")
    print("workspace: ServiceLoader.findFirst, glfwGetMouseButton, datapack functions; mods' missing classes don't stop the build")


if __name__ == "__main__":
    main()
