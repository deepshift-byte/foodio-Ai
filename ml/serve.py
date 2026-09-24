"""Serve the trained Foodio classifier over HTTP.

    uvicorn serve:app --port 8000
    # then start the API server with ML_CLASSIFIER_URL=http://localhost:8000

POST /predict  {"image_base64": "...", "top_k": 5}
-> {"predictions": [{"label": "matooke", "probability": 0.91}, ...]}

Labels are Foodio food ids, which the API passes to Claude as hints.
"""

from __future__ import annotations

import base64
import io
import json
import os
from pathlib import Path

import numpy as np
import onnxruntime as ort
from fastapi import FastAPI, HTTPException
from PIL import Image
from pydantic import BaseModel

ARTIFACTS = Path(os.environ.get("FOODIO_ARTIFACTS", Path(__file__).parent / "artifacts"))
meta = json.loads((ARTIFACTS / "labels.json").read_text())
CLASSES: list[str] = meta["classes"]
SIZE: int = meta["size"]
session = ort.InferenceSession(str(ARTIFACTS / "foodio.onnx"), providers=["CPUExecutionProvider"])

MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32)
STD = np.array([0.229, 0.224, 0.225], dtype=np.float32)

app = FastAPI(title="Foodio classifier")


class PredictRequest(BaseModel):
    image_base64: str
    top_k: int = 5


def preprocess(img: Image.Image) -> np.ndarray:
    # Mirrors the eval transform in train.py: resize short side, centre crop, normalise.
    img = img.convert("RGB")
    short = int(SIZE * 1.14)
    w, h = img.size
    scale = short / min(w, h)
    img = img.resize((round(w * scale), round(h * scale)), Image.BILINEAR)
    w, h = img.size
    left, top = (w - SIZE) // 2, (h - SIZE) // 2
    img = img.crop((left, top, left + SIZE, top + SIZE))
    arr = (np.asarray(img, dtype=np.float32) / 255.0 - MEAN) / STD
    return arr.transpose(2, 0, 1)[None]


@app.get("/health")
def health():
    return {"ok": True, "classes": len(CLASSES)}


@app.post("/predict")
def predict(req: PredictRequest):
    try:
        img = Image.open(io.BytesIO(base64.b64decode(req.image_base64)))
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(400, f"invalid image: {exc}") from exc
    logits = session.run(None, {"image": preprocess(img)})[0][0]
    probs = np.exp(logits - logits.max())
    probs /= probs.sum()
    top = np.argsort(-probs)[: max(1, min(req.top_k, len(CLASSES)))]
    return {"predictions": [{"label": CLASSES[i], "probability": float(probs[i])} for i in top]}
