# JavaScript, Electron, and WebAssembly

Three related desktop/web targets where the "binary" is really script or a
portable bytecode. The real logic is usually recoverable as source-ish text.

## Table of contents
- Minified/bundled JavaScript
- Source maps
- Electron apps (.asar)
- Node single-executable / packaged apps (pkg, nexe)
- WebAssembly (.wasm)
- Dynamic analysis in the browser/runtime

## Minified / bundled JavaScript

Web and Node apps ship concatenated, minified bundles (webpack/rollup/esbuild/
Vite). It's still JavaScript — readable after formatting.

```bash
npx prettier --write bundle.js        # reflow minified code
npx js-beautify bundle.js             # alternative beautifier
```

Then work like any codebase: search for API endpoints, feature flags, string
literals you see in the UI, and function names that survived minification. Webpack
bundles have a module registry (a big object/array of module functions) and a
`__webpack_require__`-style loader — module boundaries are recoverable even when
names aren't. For heavy obfuscation (e.g. `obfuscator.io` output — string arrays,
control-flow flattening), apply the generic deobfuscation approach from
`references/workflow.md`: beautify, then anchor on observable strings and network
calls; AST-based tools (Babel transforms) can undo specific transforms.

## Source maps

The single biggest win if present. A `.map` file (or a `//# sourceMappingURL=`
comment) maps the bundle back to original, un-minified sources — often the full
source tree with real names and comments.

```bash
# if bundle.js references bundle.js.map, reconstruct original files:
npx source-map-explorer bundle.js bundle.js.map   # visualize composition
# or use a source-map unpacker to write the original tree to disk
```

Always check for source maps first — production builds sometimes ship them by
accident, and dev builds almost always do.

## Electron apps (.asar)

Electron packages a Chromium + Node app; the application code lives in an `app.asar`
archive (often under `resources/`). It's a simple, *unencrypted* container — the
JS inside is the real source (possibly minified).

```bash
npx @electron/asar list app.asar               # see the file tree
npx @electron/asar extract app.asar app_src/   # extract everything
```

Then analyze `app_src/` as a Node/JS project: `main` process code (full Node/OS
access — filesystem, child processes, native addons), `renderer` code (the UI),
and `preload` scripts (the bridge between them, where `contextBridge`/IPC define
what the renderer can ask the main process to do). Native addons (`.node`) are
ELF/PE/Mach-O — route to `references/native-binaries.md`. Check `package.json`
for entry points and dependencies.

## Node single-executable / packaged apps

Tools like `pkg`, `nexe`, and Node's SEA bundle a JS app into one executable by
embedding the script (and sometimes a V8 snapshot) as a blob inside a native
launcher.

- Run the triage script and `strings`; the embedded JS is often visible or
  lightly wrapped. For `pkg`, the virtual filesystem blob can be carved and
  unpacked with community tools; for a V8 snapshot, extraction is harder — fall
  back to dynamic analysis (below).
- PyInstaller/Nuitka Python equivalents: PyInstaller archives unpack with
  `pyinstxtractor`, yielding `.pyc` you then decompile; treat as a separate
  carve-then-decompile job.

## WebAssembly (.wasm)

Portable stack-machine bytecode (from C/C++/Rust/Go/etc.). Convert to a readable
form:

```bash
wasm2wat module.wasm -o module.wat     # WAT: the text format (wabt toolkit)
wasm-objdump -x module.wasm            # sections, imports, exports, memory
wasm-decompile module.wasm             # wabt's higher-level pseudo-C-ish output
```

- **Imports/exports** are the interface to the JS host — they name what the module
  calls out to and what it exposes. Start there.
- The data section holds strings/constants. Rust/Go panics and format strings
  often leak source paths and symbol names.
- Ghidra and some plugins import WASM for a proper decompiler view when WAT isn't
  enough.

## Dynamic analysis

For anything web/Electron, the browser/runtime is your best instrument:

- **DevTools** — set breakpoints, inspect the call stack and scope, watch network
  requests, and use the console to call functions and read state live. For
  Electron, open the renderer's DevTools; for the main process, start Node with
  `--inspect` and attach `chrome://inspect` or an editor debugger.
- **Hooking** — monkey-patch a function (`const orig = obj.fn; obj.fn = (...a)=>{
  console.log(a); return orig(...a); }`) to log I/O without editing the bundle on
  disk.
- For WASM, step through in DevTools (it shows WAT with breakpoints) or instrument
  the JS glue that calls into it.
- HTTPS/API capture: `references/web-apis-protocols.md`.
