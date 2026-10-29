# Tool matrix and setup

What to install, by task. When the user has nothing set up, the **bold** tool in
each row is the free, capable default to recommend. Check what's already installed
(`which ghidra radare2 rizin frida jadx apktool mitmproxy binwalk 2>/dev/null`)
before suggesting installs.

## By task

| Task | Tools (default in bold) |
| ---- | ----------------------- |
| Triage / identify | `scripts/triage.py` (bundled), **`file`**, `rz-bin -I`, `binwalk` |
| Disassemble / decompile native | **Ghidra**, rizin+Cutter, radare2, IDA Pro, Binary Ninja, Hopper |
| Debug native (Linux) | **gdb + GEF/pwndbg**, lldb |
| Debug native (Windows) | **x64dbg**, WinDbg, IDA |
| Debug native (macOS) | **lldb** |
| Android decompile | **jadx / jadx-gui**, apktool, baksmali, bundletool (AAB) |
| iOS | **class-dump**, Hopper, otool, plutil, codesign |
| .NET | **ILSpy / ilspycmd**, dnSpyEx, dotPeek, de4dot, monodis |
| Java | **jadx**, CFR, Procyon, javap, Recaf |
| JS / Electron / WASM | **prettier/js-beautify**, @electron/asar, wabt (wasm2wat), source-map tools |
| Dynamic hooking (cross-platform) | **Frida** (+ objection), frida-trace |
| Syscall/API trace | **strace/ltrace** (Linux), Procmon/API Monitor (Windows), dtruss (macOS) |
| Network / API | **mitmproxy**, Burp Suite, Wireshark, grpcurl, scapy |
| Firmware | **binwalk**, unblob, unsquashfs, jefferson, ubi_reader |
| Emulation / symbolic | **QEMU**, Unicorn, Qiling, angr |
| Binary diffing | **BinDiff**, Diaphora, `rz-diff` (rizin) / `radiff2` (radare2) |
| Agent-driven analysis (MCP) | **GhidraMCP**, official Hex-Rays IDA MCP, radare2-mcp, jadx MCP — see `references/mcp-servers.md` |

## Quick install notes

Versions below are current as of late 2026; tooling moves fast, so if a command
is missing or a package moved, do a quick web search for the current method
rather than guessing.

**Ghidra** (12.x) — download the release from the official GitHub (NSA), unzip,
run `ghidraRun`. It needs **JDK 21** (the current requirement; older JDKs won't
launch it). Ghidra 12 bundles **PyGhidra** for native CPython 3 scripting — run
`pyghidraRun` once and it offers to install the Python module. Headless/batch
analysis is `support/analyzeHeadless`; the bundled
`scripts/ghidra/DecompileExport.java` runs as a `-postScript` under it with no
Python needed. Since Ghidra 12, Jython is an optional extension, so `.py` scripts
only run under PyGhidra: `pyghidraRun -H <analyzeHeadless args>`. That launcher
prompts to install `pyghidra` on first use; to keep it non-interactive, create a
venv, `pip install --no-index -f <ghidra>/Ghidra/Features/PyGhidra/pypkg/dist
pyghidra`, and run `<venv python> <ghidra>/Ghidra/Features/PyGhidra/support/pyghidra_launcher.py
<ghidra> -H <analyzeHeadless args>`.

**rizin / radare2** — `pip`-adjacent native tools; install from each project's
releases or a package manager (`apt install rizin`, `brew install rizin`,
`winget`/choco on Windows). Add the ghidra decompiler plugin (`rz-ghidra`) for
`pdg`.

**Python-based (pip):**
```bash
pip install frida-tools objection      # dynamic hooking (Frida 17+ API; see dynamic-analysis.md)
pip install mitmproxy                   # TLS intercepting proxy
pip install unblob                      # modern firmware extractor (broad format support)
pip install angr                        # symbolic execution
pip install unicorn qiling              # emulation
```

**binwalk** — binwalk 3 is a Rust rewrite and is the current version. `pip
install binwalk` gives you the *old* Python 2.x branch, so prefer `cargo install
binwalk`, or on Kali `apt install binwalk3` (the `binwalk3` command; the plain
`binwalk` package there is the legacy Python build). Check `binwalk --help` for
the v3 flag names (`--extract`, `--matryoshka`, `--entropy`).

**JVM-based (need a JDK):** jadx, apktool, CFR, Procyon, bundletool. Install
jadx/apktool from their releases or a package manager (`brew install jadx
apktool`). **.NET:** ILSpy (9.x) provides `ilspycmd` via `dotnet tool install -g
ilspycmd`; for GUI + debugging use **dnSpyEx** (the maintained fork — the
original dnSpy is archived/read-only).

**Node-based (npx, no install):**
```bash
npx @electron/asar extract app.asar out/
npx prettier --write bundle.js
```

**wabt (WebAssembly):** `apt install wabt` / `brew install wabt`, or build from
source; gives `wasm2wat`, `wasm-objdump`, `wasm-decompile`.

**Windows user-mode dynamic stack:** x64dbg + ScyllaHide, Process Monitor
(Procmon), API Monitor, and Sysmon (Sysinternals) — all free downloads.

## Platform reality check

- **Ghidra, rizin/radare2, Frida, jadx, mitmproxy, binwalk, angr** are
  cross-platform (Windows/macOS/Linux).
- **x64dbg, API Monitor, Procmon** are Windows-only. **lldb, dtruss, class-dump,
  Hopper (also Linux)** lean macOS. **strace/ltrace** are Linux.
- For mobile dynamic analysis you generally need a **rooted Android device/
  emulator** or a **jailbroken iOS device** you own, plus the matching Frida
  server. Static analysis of APKs/IPAs needs none of that.
- Heavy work benefits from a dedicated **analysis VM** (e.g. a Linux VM with the
  toolchain, or FLARE-VM-style Windows setups) kept on a revertible snapshot.

## When the user has nothing installed

A capable, fully-free baseline that covers most of this skill:

```
Ghidra              (native decompiler, all archs)
rizin + Cutter      (CLI engine + GUI, scripting)
Frida + frida-tools (dynamic hooking, all platforms)
jadx + apktool      (Android)
ILSpy/ilspycmd      (.NET)   +  jadx/CFR (Java)
mitmproxy           (API capture)
binwalk             (firmware)
Python 3            (triage.py, angr, unicorn, scripting)
```

Recommend installing only what the current target needs — don't send the user to
install ten tools to read one APK.
