# The reverse-engineering method

This is the cross-cutting process that applies to every target type. The
per-target reference files tell you *which tools*; this one tells you *how to
think* so you spend minutes, not hours, finding the part that matters.

## The loop

Reverse engineering is iterative hypothesis testing, not linear reading. You will
almost never read a program top to bottom.

1. **Orient** — triage, then map the shape of the target: entry points, exports,
   strings, imports, sections, assets. Build a mental model of "what kind of thing
   is this and roughly how is it organized."
2. **Locate** — find the part relevant to your question. Use strings, symbols,
   cross-references, and the UI/behavior to jump near the target code or data.
3. **Hypothesize** — state what you think a function/field/packet does, in one
   sentence, before studying it.
4. **Confirm** — verify with the cheapest check that settles it: read a few
   instructions, set a breakpoint, hook the function, diff two inputs.
5. **Record and rename** — write the finding down, rename the symbol, move on.

Repeat. Each confirmed fact makes the next one cheaper.

## Finding where to look

The hardest part is usually *locating* the relevant code, not understanding it.
Techniques, cheapest first:

- **String search.** An error message, menu label, log line, format string, or
  URL you can see in the UI is a string in the binary. Find it, then find what
  references it. This is the single most productive technique.
- **Import/symbol search.** Want the crypto? Look for `AES`, `EVP_`, `CryptoAPI`,
  `javax.crypto`. Networking? `connect`, `send`, `WSASocket`, `NSURLSession`,
  `OkHttp`. File access? `fopen`, `CreateFile`, `open`. The imports tell you the
  vocabulary of the program.
- **Cross-references (xrefs).** Once you find one interesting function or string,
  "who calls this?" and "what does this call?" walk you through the logic. Every
  serious tool (Ghidra, rizin, IDA, jadx) has xref navigation — learn its key.
- **Entry points and callbacks.** `main`, `DllMain`, `JNI_OnLoad`, Android
  `onCreate`, iOS `application(_:didFinishLaunching…)`, event handlers, exported
  functions, and registered callbacks are where control actually starts.
- **Diffing.** Two firmware versions, two builds, before/after a setting change —
  a binary diff (BinDiff/Diaphora/`rz-diff`) isolates exactly what changed.
- **Behavioral anchoring.** Do something in the app, watch what happens (a file
  written, a request sent, a value shown), and work backward from that observable
  effect using a tracer or monitor.

## Static vs dynamic — when to switch

**Start static** (reading code/data without running it): safe, reproducible, no
environment setup, works on samples you must never execute.

**Switch to dynamic** (running under a debugger/tracer/hook) when:

- The interesting value is *computed at runtime* (decrypted config, derived key,
  server response) and never appears literally in the file.
- The code is *packed or obfuscated* — let it unpack itself in memory, then dump.
- Control flow is *data-dependent* in a way that's painful to follow statically
  (state machines, virtualized/obfuscated dispatchers, huge switch tables).
- You need to *confirm* a static hypothesis fast: a breakpoint or hook settles in
  seconds what could be an hour of reading.

Dynamic analysis of untrusted samples must be sandboxed — see
`references/dynamic-analysis.md`.

## Working with obfuscation and protections

Obfuscation raises cost; it rarely makes analysis impossible. Recognize the class
first, then apply the standard counter.

- **Packing / self-extracting** (high entropy, tiny import table, a stub that
  jumps to freshly written memory): run it under a debugger to the "original entry
  point" after it unpacks, then dump the process image and fix imports. Tools like
  packer-specific unpackers or generic dumpers help. Static-only: carve the
  compressed blob and decompress if the algorithm is known.
- **String/constant encryption** (no readable strings, a decrypt routine called
  everywhere): find the decrypt function, then either script it statically or hook
  it dynamically and log every (ciphertext → plaintext) pair.
- **Control-flow flattening / opaque predicates**: a decompiler's output looks
  like one giant dispatcher. Use a deobfuscation pass (e.g. symbolic execution or
  tool scripts) or just anchor dynamically on inputs/outputs and treat the middle
  as a black box you probe.
- **Anti-debug / anti-VM / anti-tamper**: identify the check (timing, `ptrace`,
  `IsDebuggerPresent`, VM artifacts, integrity hashes) and neutralize it *in your
  analysis environment* (patch the check, hook the API to lie). This is standard
  for malware analysis; see `references/dynamic-analysis.md`.
- **Virtualization/VM-based protection** (custom bytecode interpreted by a
  dispatch loop): the heaviest case. Identify the handler table and the VM's
  bytecode, then build a disassembler for that custom ISA. Expensive; make sure
  the question justifies it before committing.

For a protection you only need to *understand* (e.g. for a research write-up or
interoperability), analyzing it statically on your own copy is usually enough —
you rarely need to actually bypass it.

## Naming, notes, and reproducibility

- **Rename aggressively.** `FUN_00401a20` → `validate_checksum`. `param_2` →
  `key_len`. Named code is a map; un-named code is a maze you re-solve every visit.
- **Annotate with addresses/offsets.** Every note should carry the coordinate that
  lets someone (including future-you) jump straight back: a virtual address, file
  offset, class/method name, or request number.
- **Keep an artifact log.** Hashes (from triage), tool versions, and the exact
  commands you ran. RE findings that can't be reproduced aren't findings.
- Use `assets/findings-template.md` to structure the write-up. Fill it as you go,
  not at the end.

## Compiler and language fingerprinting

Knowing the toolchain reshapes your expectations:

- **Go** — fat static binaries, `runtime.`/`main.` symbols, a `.gopclntab`
  section, distinctive calling convention; use Go-aware scripts to recover symbols.
- **Rust** — heavy monomorphization, `core::`/`alloc::` panics with source paths,
  mangled names (`_ZN...`), lots of inlining.
- **C++** — mangled names (demangle with `c++filt`/tool built-ins), vtables, RTTI
  you can use to recover class hierarchies.
- **Swift/Obj-C** — rich metadata; `class-dump`/Mach-O parsers recover class and
  method names almost for free.
- **Managed (.NET/Java)** — decompiles to near-source; different reference file.
- **Scripting bundled as binary** (PyInstaller, Nuitka, nexe, Electron) — the real
  logic is script; extract it rather than disassembling the launcher.

The triage script surfaces many of these markers automatically; confirm with the
per-target tools.
