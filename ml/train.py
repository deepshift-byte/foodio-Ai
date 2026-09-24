"""Fine-tune an ImageNet model to recognise African / Ugandan dishes.

Transfer learning in two phases: first only the new classifier head is trained,
then the whole network is fine-tuned with a lower learning rate. The best model
(by validation accuracy) is evaluated on the test split and exported to ONNX
for serve.py and for on-device use.

    python train.py --data data/foodio --model efficientnet_b0 --epochs 25

Runs on CPU but a GPU is strongly recommended (a free Kaggle or Colab GPU
notebook works well).
"""

from __future__ import annotations

import argparse
import json
import time
from collections import Counter
from pathlib import Path

import timm
import torch
import torch.nn as nn
from torch.utils.data import DataLoader, WeightedRandomSampler
from torchvision import datasets, transforms


def build_loaders(data: Path, size: int, batch: int, workers: int):
    mean, std = (0.485, 0.456, 0.406), (0.229, 0.224, 0.225)
    train_tf = transforms.Compose([
        transforms.RandomResizedCrop(size, scale=(0.6, 1.0)),
        transforms.RandomHorizontalFlip(),
        transforms.RandomRotation(20),  # plates are photographed from any angle
        transforms.ColorJitter(0.3, 0.3, 0.3, 0.05),  # kitchen and outdoor lighting varies a lot
        transforms.ToTensor(),
        transforms.Normalize(mean, std),
    ])
    eval_tf = transforms.Compose([
        transforms.Resize(int(size * 1.14)),
        transforms.CenterCrop(size),
        transforms.ToTensor(),
        transforms.Normalize(mean, std),
    ])
    train_ds = datasets.ImageFolder(data / "train", train_tf)
    val_ds = datasets.ImageFolder(data / "val", eval_tf)
    test_ds = datasets.ImageFolder(data / "test", eval_tf)
    assert train_ds.classes == val_ds.classes == test_ds.classes, "splits must contain the same classes"

    # Balance classes: Ugandan classes are usually much smaller than big public ones.
    counts = Counter(train_ds.targets)
    weights = [1.0 / counts[t] for t in train_ds.targets]
    sampler = WeightedRandomSampler(weights, num_samples=len(weights), replacement=True)

    kw = dict(batch_size=batch, num_workers=workers, pin_memory=torch.cuda.is_available())
    return (
        DataLoader(train_ds, sampler=sampler, **kw),
        DataLoader(val_ds, shuffle=False, **kw),
        DataLoader(test_ds, shuffle=False, **kw),
        train_ds.classes,
    )


@torch.no_grad()
def evaluate(model, loader, device, n_classes):
    model.eval()
    correct1 = correct5 = total = 0
    per_class = torch.zeros(n_classes, 2)  # correct, total
    for x, y in loader:
        x, y = x.to(device), y.to(device)
        logits = model(x)
        top5 = logits.topk(min(5, n_classes), dim=1).indices
        hit1 = top5[:, 0] == y
        correct1 += hit1.sum().item()
        correct5 += (top5 == y[:, None]).any(dim=1).sum().item()
        total += y.numel()
        for t, h in zip(y.cpu(), hit1.cpu()):
            per_class[t, 0] += h.item()
            per_class[t, 1] += 1
    return correct1 / max(total, 1), correct5 / max(total, 1), per_class


def run_epoch(model, loader, device, opt, sched, scaler, loss_fn):
    model.train()
    total_loss = n = 0
    for x, y in loader:
        x, y = x.to(device), y.to(device)
        opt.zero_grad(set_to_none=True)
        with torch.autocast(device_type=device.type, enabled=device.type == "cuda"):
            loss = loss_fn(model(x), y)
        scaler.scale(loss).backward()
        scaler.step(opt)
        scaler.update()
        sched.step()
        total_loss += loss.item() * y.size(0)
        n += y.size(0)
    return total_loss / max(n, 1)


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--data", default="data/foodio")
    ap.add_argument("--model", default="efficientnet_b0", help="any timm model, e.g. mobilenetv3_large_100 for phones")
    ap.add_argument("--size", type=int, default=224)
    ap.add_argument("--batch", type=int, default=32)
    ap.add_argument("--epochs", type=int, default=25)
    ap.add_argument("--head-epochs", type=int, default=3, help="epochs training only the classifier head")
    ap.add_argument("--lr", type=float, default=1e-3)
    ap.add_argument("--workers", type=int, default=4)
    ap.add_argument("--out", default="artifacts")
    ap.add_argument("--no-pretrained", action="store_true", help="start from random weights (offline testing only)")
    args = ap.parse_args()

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    train_dl, val_dl, test_dl, classes = build_loaders(Path(args.data), args.size, args.batch, args.workers)
    print(f"{len(classes)} classes, {len(train_dl.dataset)} training images, device={device}")

    model = timm.create_model(args.model, pretrained=not args.no_pretrained, num_classes=len(classes)).to(device)
    loss_fn = nn.CrossEntropyLoss(label_smoothing=0.1)
    scaler = torch.amp.GradScaler(enabled=device.type == "cuda")
    best_acc, best_path = 0.0, out / "best.pt"

    def phase(epochs: int, lr: float, head_only: bool):
        nonlocal best_acc
        head = model.get_classifier()
        for p in model.parameters():
            p.requires_grad = not head_only
        for p in head.parameters():
            p.requires_grad = True
        params = [p for p in model.parameters() if p.requires_grad]
        opt = torch.optim.AdamW(params, lr=lr, weight_decay=0.05)
        sched = torch.optim.lr_scheduler.OneCycleLR(opt, max_lr=lr, total_steps=max(1, epochs * len(train_dl)))
        for epoch in range(epochs):
            t0 = time.time()
            loss = run_epoch(model, train_dl, device, opt, sched, scaler, loss_fn)
            top1, top5, _ = evaluate(model, val_dl, device, len(classes))
            flag = ""
            if top1 > best_acc:
                best_acc = top1
                torch.save(model.state_dict(), best_path)
                flag = "  *best*"
            stage = "head" if head_only else "full"
            print(f"[{stage}] epoch {epoch + 1}/{epochs} loss={loss:.3f} val_top1={top1:.3f} val_top5={top5:.3f} ({time.time() - t0:.0f}s){flag}")

    phase(args.head_epochs, args.lr, head_only=True)
    phase(max(0, args.epochs - args.head_epochs), args.lr / 5, head_only=False)

    model.load_state_dict(torch.load(best_path, map_location=device))
    top1, top5, per_class = evaluate(model, test_dl, device, len(classes))
    print(f"\nTEST top1={top1:.3f} top5={top5:.3f}")
    report = {"model": args.model, "size": args.size, "test_top1": top1, "test_top5": top5, "per_class": {}}
    for i, c in enumerate(classes):
        correct, total = per_class[i].tolist()
        acc = correct / total if total else 0.0
        report["per_class"][c] = {"accuracy": acc, "n": int(total)}
        print(f"  {c:20s} {acc:.2f} (n={int(total)})")
    (out / "report.json").write_text(json.dumps(report, indent=2))
    (out / "labels.json").write_text(json.dumps({"classes": classes, "size": args.size}, indent=2))

    # ONNX export for serve.py (and for on-device inference, e.g. onnxruntime-react-native).
    model.eval().cpu()
    dummy = torch.randn(1, 3, args.size, args.size)
    torch.onnx.export(
        model, dummy, out / "foodio.onnx",
        input_names=["image"], output_names=["logits"],
        dynamic_axes={"image": {0: "batch"}, "logits": {0: "batch"}},
        opset_version=18,
        external_data=False,  # single self-contained .onnx file
    )
    print(f"Saved {out / 'foodio.onnx'}, labels.json and report.json")


if __name__ == "__main__":
    main()
