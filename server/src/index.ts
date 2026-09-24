import 'dotenv/config';
import Anthropic from '@anthropic-ai/sdk';
import cors from 'cors';
import express, { type NextFunction, type Request, type Response } from 'express';
import { ZodError } from 'zod';
import { FOODS } from '../../shared/foods';
import { AiOutputError, AiRefusalError, analyzeMealImage, suggestRecipes } from './ai';
import { buildAnalysis } from './analysis';
import { classifyImage } from './classifier';
import { analyzeRequestSchema, recipeRequestSchema } from './schemas';

const app = express();
app.use(cors());
app.use(express.json({ limit: '12mb' }));

app.get('/health', (_req, res) => {
  res.json({ ok: true, ai: Boolean(process.env.ANTHROPIC_API_KEY), classifier: Boolean(process.env.ML_CLASSIFIER_URL) });
});

app.get('/api/foods', (_req, res) => {
  res.json({ foods: FOODS });
});

app.post('/api/analyze', async (req, res) => {
  const body = analyzeRequestSchema.parse(req.body);
  const hints = await classifyImage(body.imageBase64);
  const ai = await analyzeMealImage({ ...body, hints });
  res.json(buildAnalysis(ai, body.profile));
});

app.post('/api/recipes', async (req, res) => {
  const body = recipeRequestSchema.parse(req.body);
  if (!body.ingredients.length && !body.dish) {
    res.status(400).json({ error: 'Tell Foodio some ingredients or a dish.' });
    return;
  }
  res.json(await suggestRecipes(body));
});

app.use((err: unknown, _req: Request, res: Response, _next: NextFunction) => {
  if (err instanceof ZodError) {
    res.status(400).json({ error: 'Invalid request', details: err.issues });
  } else if (err instanceof AiRefusalError) {
    res.status(422).json({ error: err.message });
  } else if (err instanceof Anthropic.RateLimitError) {
    res.status(429).json({ error: 'Foodio is busy right now. Please try again in a moment.' });
  } else if (err instanceof Anthropic.AuthenticationError) {
    console.error('Anthropic authentication failed — check ANTHROPIC_API_KEY');
    res.status(500).json({ error: 'The AI service is not configured correctly.' });
  } else if (err instanceof Anthropic.APIError || err instanceof AiOutputError) {
    console.error(err);
    res.status(502).json({ error: 'The AI service had a problem. Please try again.' });
  } else {
    console.error(err);
    res.status(500).json({ error: 'Something went wrong.' });
  }
});

const port = Number(process.env.PORT) || 8787;
app.listen(port, () => {
  console.log(`Foodio API listening on http://localhost:${port}`);
});
