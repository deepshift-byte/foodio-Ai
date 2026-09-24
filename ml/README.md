# Foodio food-recognition model

This folder trains Foodio's own image classifier for African dishes, with the focus on Ugandan food. Its predictions go to the API server as **hints**. Claude still does the full analysis, but a model trained on local food helps with dishes that general vision models often confuse, such as matooke vs. posho or dodo vs. nakati.

The pipeline:

1. `prepare_data.py` merges one or more labelled photo folders (Kaggle datasets and your own photos). It maps folder names to Foodio food ids, removes duplicates, and splits the images into train/val/test.
2. `train.py` fine-tunes an ImageNet model (EfficientNet-B0 by default; MobileNetV3 for phones). It reports test accuracy per dish and exports `artifacts/foodio.onnx`.
3. `serve.py` is a small FastAPI service. Point the API at it with `ML_CLASSIFIER_URL`.

## Data

### Public datasets (Kaggle)
- [African Foods Datasets](https://www.kaggle.com/datasets/graccy/african-foods-datasets): African dish photos.
- General food sets such as [Food-101](https://www.kaggle.com/datasets/kmader/food41) are useful as extra "other" classes. Unmapped classes are skipped unless you pass `--keep-unmapped`.

Folder names are matched against `label_map.json`. It holds every food id with its English and local names (e.g. `ugali → posho`, `binyebwa → gnut-sauce`, `nyama choma → muchomo`). The script prints every folder it could not map, so you can add aliases. Regenerate the base map from `shared/foods.ts` with:

```bash
cd ml && ../server/node_modules/.bin/tsx export_label_map.ts
```

### Ugandan photos (most important)
There is no large public dataset of Ugandan meal photos, so the model will only be as good as the local photos you collect. Aim for **100–300 photos per dish**:

```
raw/ugandan-photos/
  matooke/  posho/  kalo/  beans/  binyebwa/  dodo/  nakati/  malakwang/
  luwombo/  katogo/  rolex/  eshabwe/  mukene/  nsenene/  tilapia/ ...
```

Tips:
- Take photos on ordinary phones in real settings: home plates, restaurants, street stalls, daylight and indoor light, top-down and angled.
- Include mixed plates, and label each one by its main or most visible item.
- Keep a separate test set of photos from people and places not in the training set, to measure real accuracy.

## Train

A GPU is strongly recommended. A free Kaggle Notebook with GPU works well, and it can attach the Kaggle dataset directly.

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt

# Kaggle API token in ~/.kaggle/kaggle.json (kaggle.com → Settings → API)
python prepare_data.py --kaggle graccy/african-foods-datasets --source raw/ugandan-photos
python train.py --data data/foodio --model efficientnet_b0 --epochs 25
# for a smaller, phone-friendly model:
python train.py --data data/foodio --model mobilenetv3_large_100 --epochs 30
```

Outputs go to `artifacts/`: `foodio.onnx`, `labels.json`, `report.json` (per-class test accuracy) and `best.pt`.

## Serve

```bash
uvicorn serve:app --port 8000
# in server/.env
ML_CLASSIFIER_URL=http://localhost:8000
```

If the classifier is down or unreachable, the API ignores it and carries on with Claude alone.
