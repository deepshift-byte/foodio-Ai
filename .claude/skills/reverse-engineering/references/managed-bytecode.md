# Managed bytecode — .NET and Java

Managed code (.NET CIL, Java/JVM bytecode) keeps far more structure than native
machine code: names, types, and control flow survive compilation, so decompilers
reconstruct near-source. This is the easiest category to read — unless it's been
obfuscated, which is the main complication.

## Table of contents
- Is it .NET or Java? (and which flavor)
- .NET workflow (ILSpy, dnSpyEx, ilasm/ildasm)
- Java workflow (jadx, CFR, Procyon, javap)
- Deobfuscation
- Dynamic analysis
- Common recipes

## Is it .NET or Java?

The triage script distinguishes these. Quick manual checks:

- **.NET**: a PE (`MZ` header) that imports `mscoree.dll` / contains the CLR
  metadata signature `BSJB`. Could be a framework `.exe`/`.dll`, a self-contained
  single-file app, or Mono. `.NET` single-file bundles embed the real assemblies —
  extract them first.
- **Java**: a `.class` file (magic `CA FE BA BE`) or a `.jar`/`.war`/`.aar` (a ZIP
  of classes + `META-INF/MANIFEST.MF`). (Note: `CA FE BA BE` is also a Mach-O fat
  magic — if it's actually a Mach-O, go to `references/native-binaries.md`. A real
  `.class` has a sane version in bytes 6–7.)

## .NET workflow

Decompilers reconstruct C# (or VB/F#) remarkably well.

- **ILSpy** (free, open source) — the reliable default. GUI, plus `ilspycmd` for
  scripted/batch decompilation:
  ```bash
  ilspycmd Target.dll -o out/           # decompile to .cs files
  ilspycmd -il Target.dll               # raw CIL instead of C#
  ```
- **dnSpyEx** (free, the maintained fork of dnSpy) — GUI decompiler *and*
  debugger; you can set breakpoints and step through decompiled C# of a running
  .NET process, and edit methods in place. Excellent for dynamic work.
- **ildasm/ilasm** (ship with the SDK) — disassemble to CIL and reassemble;
  `monodis` is the Mono equivalent.
- **dotPeek** (free, JetBrains) — another strong GUI option.

Start at the entry point (`Main`) or at the type whose name matches the behavior
you're chasing. The assembly's referenced-assemblies list and namespaces act as a
table of contents. Resources are in the manifest (`.resources`); embedded files
and config often hide there.

## Java workflow

- **jadx** — yes, the Android tool also does plain JARs/classes well; great search
  and xrefs. Good first choice:
  ```bash
  jadx -d out/ app.jar
  jadx-gui app.jar
  ```
- **CFR** and **Procyon** — excellent standalone decompilers; try a second one
  when the first produces ugly output (they handle different constructs better):
  ```bash
  java -jar cfr.jar app.jar --outputdir out/
  ```
- **javap** (ships with the JDK) — official disassembler for ground-truth
  bytecode:
  ```bash
  javap -p -c -constants com.example.Foo    # private members + bytecode + constants
  ```
- **Bytecode editing**: `Recaf` (GUI) or the ASM/Javassist libraries for
  programmatic transforms — useful for patching a class in your own/CTF target.

## Deobfuscation

Obfuscators rename symbols to garbage, encrypt strings, and add junk control flow.

- **.NET**: `de4dot` is the classic cleaner — it detects many commercial
  obfuscators (ConfuserEx, etc.), restores readable names where possible, and
  decrypts strings. Run it before ILSpy/dnSpyEx for a big readability win. For
  string encryption it can't resolve statically, use dnSpyEx to break on the
  decrypt routine and read plaintext.
- **Java**: fewer turnkey tools; often you anchor on non-renamable anchors
  (framework/`java.*`/`javax.*` calls, reflection string literals, resource
  names), then rename outward in jadx. For string encryption, hook or invoke the
  decrypt method dynamically.
- General principle (from `references/workflow.md`): identify the obfuscation
  *class* first, apply the standard counter, and when static deobfuscation is
  expensive, pivot to dynamic — let the program decrypt itself and observe.

## Dynamic analysis

Managed runtimes make live debugging especially productive:

- **.NET**: dnSpyEx attaches to a running process and debugs decompiled C#
  directly — breakpoints, locals, edit-and-continue on methods. This is the
  fastest way to understand runtime-computed values.
- **Java**: attach a JVM debugger via JDWP (`-agentlib:jdwp=...`) and drive it from
  an IDE; or use a Java agent (`-javaagent`) / ByteBuddy to instrument classes at
  load time and log method I/O. BTrace/async-profiler help for tracing.

## Common recipes

- **"Recover the source."** `ilspycmd`/`jadx -d` to a directory, then read it like
  a normal project; search for the feature by name.
- **"Names are garbage (obfuscated)."** .NET → run `de4dot` first. Either platform
  → anchor on library/framework calls and string constants, rename outward.
- **"String is decrypted at runtime."** Set a breakpoint on the decrypt method
  (dnSpyEx / JVM debugger) and read the plaintext, or call the method directly.
- **"Where's the license/validation logic?"** Search type/method names
  (`License`, `Activation`, `Validate`, `Verify`); for *understanding* it, reading
  the decompiled method is usually enough — you rarely need to modify it.
