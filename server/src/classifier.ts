// Optional: the Foodio image classifier trained on African/Ugandan food photos
// (see ml/). When ML_CLASSIFIER_URL is set, its top predictions are passed to
// Claude as hints. Any failure is ignored — Claude works without it.

export interface ClassifierHint {
  label: string;
  probability: number;
}

export async function classifyImage(imageBase64: string): Promise<ClassifierHint[]> {
  const url = process.env.ML_CLASSIFIER_URL;
  if (!url) return [];
  try {
    const res = await fetch(`${url.replace(/\/$/, '')}/predict`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ image_base64: imageBase64, top_k: 5 }),
      signal: AbortSignal.timeout(5000),
    });
    if (!res.ok) return [];
    const data = (await res.json()) as { predictions?: ClassifierHint[] };
    return (data.predictions ?? []).filter((p) => p.probability >= 0.05);
  } catch {
    return [];
  }
}
