# Driving RE tools over MCP

The biggest shift in agent-assisted reverse engineering is that the major tools
now expose **MCP servers**. When one is connected, you can drive the
disassembler directly — list functions, read decompiled code, rename symbols,
follow cross-references, run scripts — instead of asking the user to copy-paste
from a GUI. If an RE MCP server is available in the session, prefer it over
shelling out: it keeps the tool's analysis database live and lets you iterate
(rename, re-read, pivot) the way a human analyst does in the GUI.

How to tell: your available tools/functions will include entries namespaced to
the server (e.g. tools whose names reference ghidra, ida, radare2, or jadx).
Check the tool list before planning a manual CLI workflow.

## What's out there (as of late 2026)

Verify the exact repo, maintainer, and install steps before recommending one —
these move quickly. The agent doesn't install these itself; point the user at
the project and let them set it up.

### Multi-tool
- **rea** (morluto) — one MCP server (plus CLI) over Hopper, Ghidra, or IDA, with
  built-in readers for Go/Rust binaries, .NET, Flutter, and JS/Electron. Results
  come with evidence and stated limitations. Setup: `npx rea-agents setup` (Node
  22+). Pairs well with this skill: rea does the analysis calls, the playbooks here
  give the method.

### Ghidra
- **GhidraMCP** (LaurieWired) — the most widely used. A Ghidra plugin exposing an
  MCP server so an agent can decompile functions, rename, list xrefs, and run
  analysis. Pairs with Ghidra's own decompiler.
- **re-mcp** (jtsylve) — a *headless* server covering both Ghidra (via PyGhidra)
  and IDA (via idalib) behind one tool interface; needs Ghidra 12+ / JDK 21+. Good
  when you want no GUI and a shared workflow across both backends.
- **ReVa** (cyberkaida) — Ghidra extension whose tools are designed around LLM
  context limits (small tools, extra context like xrefs next to decompilation).
  Runs inside the Ghidra GUI or headless (`uv tool install
  reverse-engineering-assistant`, then `mcp-reva`); Ghidra 12+.

### Persistent Ghidra without MCP
- **ghidra-cli** (akiselev) — a command-line tool that keeps Ghidra loaded between
  commands, so renames and retypes persist and nothing is re-analyzed: decompile,
  xrefs, rename, search (including crypto constants), call graphs, JSON output when
  piped. Built with Rust (`cargo install`), GPL-3.0. Use it instead of the
  one-shot export in SKILL.md step 6 when you'll iterate on the same binary.

### IDA Pro
- **Official Hex-Rays IDA MCP server** — if the user has an IDA license, prefer
  this; it's the vendor-supported option.
- **ida-pro-mcp** (mrexodia) — the established community server; its README now
  points users to the official Hex-Rays one. Still common in the wild.

### radare2 / rizin
- **radare2-mcp** — official radare2 MCP implementation, tools over stdio.
- **dnakov/radare2-mcp** — community server, supports local or containerized use.
  rizin users can often use the r2 ecosystem or rizin's own scripting bridge.

### Android / jadx
- **jadx-ai-mcp + jadx-mcp-server** (zinja-coder, "Zin MCP Suite") — a modified
  jadx-gui plus a Python MCP server; lets an agent query classes/methods, smali,
  the manifest, and resources live. The suite also includes an APKTool MCP server.

## How to use one well

1. **Orient with the server's listing tools first** — program metadata, function
   list, imports, strings — the same triage you'd do manually, but queryable.
2. **Decompile on demand, not all at once.** Ask for the one function you're
   reasoning about, read it, form a hypothesis, then pull its callees/callers.
   This is cheaper and sharper than exporting everything.
3. **Rename as you learn**, through the server's rename tool. The renames persist
   in the tool's database and compound across the session, exactly as in the GUI.
4. **Follow xrefs** to navigate — "who calls this", "what does this reference" —
   rather than reading linearly.
5. **Confirm, then record** findings with addresses/symbol names into the
   findings template, so the work survives the session.

## When there's no MCP server

Fall back to the per-target reference files and the bundled scripts:

- Batch-export decompiled C with the headless Ghidra script
  (`scripts/ghidra/DecompileExport.java`) and read it in slices — this is the
  closest no-MCP equivalent of "ask for one function at a time".
- Use `rizin`/`r2` in `-c`/pipe mode to script queries non-interactively.
- Use the CLI decompilers (jadx, ilspycmd, CFR) to dump to a directory you then
  read and grep.

The method is identical either way; MCP just removes the copy-paste.
