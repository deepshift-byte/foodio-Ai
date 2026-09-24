"""Build the Foodio training set from one or more folders of labelled food photos.

Each source is an "ImageFolder" directory: one sub-folder per dish, e.g.

    raw/african-foods/Jollof Rice/*.jpg
    raw/ugandan-photos/matooke/*.jpg

Folder names are matched to Foodio food ids using label_map.json (English and
local names, e.g. "binyebwa" -> gnut-sauce, "ugali" -> posho). Images are
de-duplicated and split into train/val/test.

Examples
--------
    # Download a Kaggle dataset first (needs ~/.kaggle/kaggle.json):
    python prepare_data.py --kaggle graccy/african-foods-datasets --source raw/ugandan-photos

    # Only local folders:
    python prepare_data.py --source raw/african-foods --source raw/ugandan-photos
"""

from __future__ import annotations

import argparse
import hashlib
import json
import random
import re
import shutil
import subprocess
from collections import defaultdict
from pathlib import Path

IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}
HERE = Path(__file__).parent


def normalise(name: str) -> str:
    name = name.lower().replace("_", " ").replace("-", " ")
    return re.sub(r"\s+", " ", re.sub(r"[^a-z0-9 ()]", "", name)).strip()


def load_aliases() -> dict[str, str]:
    label_map = json.loads((HERE / "label_map.json").read_text())
    aliases: dict[str, str] = {}
    for food_id, names in label_map.items():
        for n in names:
            aliases[normalise(n)] = food_id
    return aliases


def download_kaggle(slug: str, raw_dir: Path) -> Path:
    target = raw_dir / slug.replace("/", "__")
    if target.exists() and any(target.iterdir()):
        print(f"[kaggle] {slug} already downloaded")
        return target
    target.mkdir(parents=True, exist_ok=True)
    print(f"[kaggle] downloading {slug} ...")
    subprocess.run(["kaggle", "datasets", "download", "-d", slug, "-p", str(target), "--unzip"], check=True)
    return target


def find_class_dirs(root: Path) -> list[Path]:
    """Folders that directly contain images (handles datasets nested under train/ etc.)."""
    dirs = set()
    for p in root.rglob("*"):
        if p.is_file() and p.suffix.lower() in IMAGE_EXTS:
            dirs.add(p.parent)
    return sorted(dirs)


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--source", action="append", default=[], help="ImageFolder-style directory (repeatable)")
    ap.add_argument("--kaggle", action="append", default=[], help="Kaggle dataset slug to download (repeatable)")
    ap.add_argument("--raw-dir", default="raw", help="Where Kaggle downloads go")
    ap.add_argument("--out", default="data/foodio", help="Output directory")
    ap.add_argument("--val", type=float, default=0.15)
    ap.add_argument("--test", type=float, default=0.10)
    ap.add_argument("--min-images", type=int, default=20, help="Drop classes with fewer images")
    ap.add_argument("--keep-unmapped", action="store_true", help="Keep classes not in label_map.json under their folder name")
    ap.add_argument("--seed", type=int, default=42)
    args = ap.parse_args()

    sources = [Path(s) for s in args.source]
    sources += [download_kaggle(slug, Path(args.raw_dir)) for slug in args.kaggle]
    if not sources:
        ap.error("give at least one --source or --kaggle")

    aliases = load_aliases()
    by_class: dict[str, list[Path]] = defaultdict(list)
    seen_hashes: set[str] = set()
    unmapped: dict[str, int] = defaultdict(int)

    for root in sources:
        for d in find_class_dirs(root):
            name = normalise(d.name)
            label = aliases.get(name) or (re.sub(r"\s+", "-", name) if args.keep_unmapped else None)
            images = [p for p in d.iterdir() if p.suffix.lower() in IMAGE_EXTS]
            if not label:
                unmapped[d.name] += len(images)
                continue
            for img in images:
                digest = hashlib.md5(img.read_bytes()).hexdigest()
                if digest in seen_hashes:
                    continue
                seen_hashes.add(digest)
                by_class[label].append(img)

    if unmapped:
        print("\nSkipped folders with no Foodio mapping (add them to label_map.json or use --keep-unmapped):")
        for name, n in sorted(unmapped.items(), key=lambda x: -x[1]):
            print(f"  {name!r}: {n} images")

    out = Path(args.out)
    if out.exists():
        shutil.rmtree(out)
    rng = random.Random(args.seed)
    summary = {}
    for label, images in sorted(by_class.items()):
        if len(images) < args.min_images:
            print(f"  dropping {label}: only {len(images)} images (< {args.min_images})")
            continue
        rng.shuffle(images)
        n_test = max(1, int(len(images) * args.test))
        n_val = max(1, int(len(images) * args.val))
        splits = {"test": images[:n_test], "val": images[n_test : n_test + n_val], "train": images[n_test + n_val :]}
        for split, files in splits.items():
            dest = out / split / label
            dest.mkdir(parents=True, exist_ok=True)
            for i, src in enumerate(files):
                shutil.copy2(src, dest / f"{i:05d}{src.suffix.lower()}")
        summary[label] = {k: len(v) for k, v in splits.items()}

    (out / "summary.json").write_text(json.dumps(summary, indent=2))
    print(f"\nWrote {sum(s['train'] + s['val'] + s['test'] for s in summary.values())} images in {len(summary)} classes to {out}")
    for label, s in summary.items():
        print(f"  {label:20s} train={s['train']:5d} val={s['val']:4d} test={s['test']:4d}")


if __name__ == "__main__":
    main()
