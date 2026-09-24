import Anthropic from '@anthropic-ai/sdk';
import { betaZodOutputFormat } from '@anthropic-ai/sdk/helpers/beta/zod';
import type { z } from 'zod';
import type { HealthProfile } from '../../shared/types';
import { ANALYZE_SYSTEM, RECIPE_SYSTEM } from './prompts';
import { aiMealSchema, aiRecipesSchema, type AiMeal, type AiRecipes } from './schemas';
import type { ClassifierHint } from './classifier';

const MODEL = process.env.FOODIO_MODEL || 'claude-opus-5';

let client: Anthropic | undefined;
function anthropic() {
  client ??= new Anthropic();
  return client;
}

export class AiRefusalError extends Error {}
export class AiOutputError extends Error {}

function describeProfile(profile?: HealthProfile) {
  if (!profile) return 'No health profile provided — give general healthy-eating advice.';
  return `Person's health profile (JSON):\n${JSON.stringify(profile, null, 2)}`;
}

async function structured<T extends z.ZodType>(
  schema: T,
  system: string,
  content: Anthropic.Beta.BetaContentBlockParam[],
): Promise<z.infer<T>> {
  const response = await anthropic().beta.messages.parse({
    model: MODEL,
    max_tokens: 16000,
    thinking: { type: 'adaptive' },
    // If a safety classifier declines, retry server-side on Anthropic's
    // recommended fallback model instead of failing the request.
    betas: ['server-side-fallback-2026-07-01'],
    fallbacks: 'default',
    system: [{ type: 'text', text: system, cache_control: { type: 'ephemeral' } }],
    messages: [{ role: 'user', content }],
    output_config: { format: betaZodOutputFormat(schema) },
  });

  if (response.stop_reason === 'refusal') {
    throw new AiRefusalError('The AI declined to analyse this request.');
  }
  if (!response.parsed_output) {
    throw new AiOutputError(`The AI response could not be parsed (stop reason: ${response.stop_reason}).`);
  }
  return response.parsed_output as z.infer<T>;
}

export async function analyzeMealImage(args: {
  imageBase64: string;
  mediaType: 'image/jpeg' | 'image/png' | 'image/webp';
  profile?: HealthProfile;
  note?: string;
  answers?: { question: string; answer: string }[];
  hints?: ClassifierHint[];
}): Promise<AiMeal> {
  const parts: string[] = [describeProfile(args.profile)];
  if (args.note) parts.push(`The person says about this meal: "${args.note}"`);
  if (args.hints?.length) {
    parts.push(
      `A food-image classifier trained on African dishes suggests (label: probability): ${args.hints
        .map((h) => `${h.label}: ${h.probability.toFixed(2)}`)
        .join(', ')}. Treat this as a hint only; trust what you see.`,
    );
  }
  if (args.answers?.length) {
    parts.push(
      `Answers to your earlier clarifying questions:\n${args.answers
        .map((a) => `- Q: ${a.question}\n  A: ${a.answer}`)
        .join('\n')}`,
    );
  }
  parts.push('Analyse this meal photo.');

  return structured(aiMealSchema, ANALYZE_SYSTEM, [
    { type: 'image', source: { type: 'base64', media_type: args.mediaType, data: args.imageBase64 } },
    { type: 'text', text: parts.join('\n\n') },
  ]);
}

export async function suggestRecipes(args: {
  ingredients: string[];
  dish?: string;
  mealType: string;
  profile?: HealthProfile;
}): Promise<AiRecipes> {
  const parts = [describeProfile(args.profile)];
  if (args.dish) parts.push(`They want to cook: ${args.dish}`);
  if (args.ingredients.length) parts.push(`Ingredients they have: ${args.ingredients.join(', ')}`);
  parts.push(`Meal: ${args.mealType}. Suggest healthy meal ideas.`);
  return structured(aiRecipesSchema, RECIPE_SYSTEM, [{ type: 'text', text: parts.join('\n\n') }]);
}
