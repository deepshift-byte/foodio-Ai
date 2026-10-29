# Firmware and embedded / IoT

Firmware images are usually a packed blob: a bootloader, one or more kernels, and
one or more filesystems, sometimes compressed or signed. The job is to carve it
apart, extract the filesystem, and then analyze the binaries inside like any other
native target. Work only on devices/firmware you own or are authorized to study.

## Table of contents
- Get the image
- Carve and extract (binwalk / unblob)
- Filesystems you'll meet
- Analyzing the extracted root filesystem
- Bare-metal / RTOS images (no filesystem)
- Hardware interfaces (context)
- Common recipes

## Getting the image

- Vendor firmware update files (download) — the easiest and most clearly
  legitimate source.
- Dumped from flash (SPI/NAND/eMMC) with a programmer, or over a debug interface,
  on hardware you own.
- Extracted from an installer/updater app (route that app to its own reference).

Treat every image as untrusted data: fresh empty directory, parse don't execute,
and run extraction tools with the image as an argument (don't `cd` into an
extracted root and run things from there).

## Carve and extract

```bash
binwalk firmware.bin                 # signature scan: what's inside and where
binwalk -e firmware.bin              # extract known parts (v3 writes to ./extractions/; v2 to _firmware.bin.extracted/)
binwalk -Me firmware.bin             # recursive extract (matryoshka)
```

`unblob` is a newer, often more reliable extractor with broad format support and
safer handling:
```bash
unblob -e out/ firmware.bin
```

Entropy from the triage script (or `binwalk -E`) tells you whether regions are
compressed/encrypted. A wholly high-entropy image with no recognizable
signatures usually means the payload is encrypted or the whole thing is signed and
packed — look for the bootloader/header that would decrypt it, or an unencrypted
update tool that knows the key.

## Filesystems you'll meet

- **SquashFS** (`hsqs`/`sqsh`) — by far the most common root FS; `unsquashfs`
  extracts it. binwalk/unblob usually do this for you.
- **JFFS2 / UBIFS / YAFFS** — raw-flash filesystems; extract with `jefferson`,
  `ubi_reader`, or unblob.
- **CramFS / ext2-4 / FAT** — standard; loop-mount read-only or extract with
  `debugfs`/`7z`.
- **CPIO / initramfs** — common for Linux kernels; `cpio -idmv` or let the
  extractor handle it.

## Analyzing the extracted root filesystem

A Linux root FS is a goldmine of context before you touch a single binary:

- `etc/` — service configs, default credentials (grep for `password`, `admin`),
  `inittab`/`rcS` startup, `passwd`/`shadow`.
- `bin/ sbin/ usr/` — BusyBox and vendor daemons; the custom daemons (the web UI
  backend, the update client, the protocol services) are your real targets →
  route each to `references/native-binaries.md` (they're usually ELF, often MIPS
  or ARM).
- `www/ htdocs/` — the device's web interface: CGI binaries and scripts that are a
  classic analysis focus.
- Startup scripts — tell you what runs at boot and how services are wired, i.e.
  where to start.

Note the architecture (`file` on any binary → MIPS/ARM/etc.) — you'll want a
disassembler that supports it (Ghidra covers them all) and, for dynamic analysis,
`qemu-user` to run individual binaries, or full-system QEMU to boot the image.

## Bare-metal / RTOS images (no filesystem)

Microcontroller firmware is often one flat binary with no filesystem (the triage
entropy is moderate and binwalk finds little). Then:

- Determine the **architecture and load address** (datasheet, vendor tooling, or
  inference from the vector table / reset handler). Loading at the correct base is
  what makes xrefs and pointers resolve.
- In Ghidra: import as "raw binary", pick the processor (e.g. ARM Cortex-M), set
  the base address, and point the disassembler at the reset vector. The vector
  table at the start gives you reset/interrupt handlers as entry points.
- Identify peripherals by their memory-mapped register addresses (from the chip's
  datasheet) to understand what the code is driving.

## Hardware interfaces (context)

For completeness — these are how images and live access are obtained on devices
you own: **UART** (serial console; often a root shell or bootloader prompt),
**JTAG/SWD** (debug/halt/flash access, driven by OpenOCD), and direct **SPI/NAND**
flash dumping. Details are hardware-specific and out of scope here; the analysis
once you have the bytes is what this skill covers.

## Common recipes

- **"What's in this firmware?"** `binwalk` then `binwalk -Me` (or `unblob`);
  identify and extract the filesystem; inventory `etc`/`bin`/`www`.
- **"Find the web/admin backend."** Look in `www`/`htdocs` and the CGI binaries;
  analyze those as native targets.
- **"Hardcoded credentials / keys?"** grep the extracted FS and run triage's
  string pass over config files and binaries.
- **"No filesystem, just a blob."** Bare-metal path: get arch + load address, load
  raw in Ghidra at the right base, start from the vector table.
