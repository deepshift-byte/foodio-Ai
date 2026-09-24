import Anthropic from '@anthropic-ai/sdk';
import { betaZodOutputFormat } from '@anthropic-ai/sdk/helpers/beta/zod';
import type { z } from 'zod';
import { dailyKcal, DIET_LABELS, formatHour } from '../../shared/nutrition';
import type { EatingContext, HealthProfile } from '../../shared/types';
import { weightPlan } from '../../shared/weight';
import { ANALYZE_SYSTEM, COACH_SYSTEM, RECIPE_SYSTEM } from './prompts';
import {
  aiCoachSchema,
  aiMealSchema,
  aiRecipesSchema,
  type AiCoach,
  type AiMeal,
  type AiRecipes,
} from './schemas';
import type { ClassifierHint } from './classifier';

const MODEL = process.env.FOODIO_MODEL || 'claude-opus-5';

let client: Anthropic | undefined;
function anthropic() {
  client ??= new Anthropic();
  return client;
}

export class AiRefusalError extends Error {}
export class AiOutputError extends Error {}

function describeProfile(profile?: HealthProfile, context?: EatingContext) {
  if (!profile) return 'No health profile provided — give general healthy-eating advice.';
  const lines = [`Person's health profile (JSON):\n${JSON.stringify(profile, null, 2)}`];
  if (profile.dietPlans?.length) {
    lines.push(`Diet plans: ${profile.dietPlans.map((d) => DIET_LABELS[d]).join(', ')}.`);
  }
  if (profile.dietPlans?.includes('intermittent-fasting')) {
    const w = profile.fastingWindow ?? { startHour: 12, hours: 8 };
    lines.push(`Eating window: ${formatHour(w.startHour)}–${formatHour(w.startHour + w.hours)} (${24 - w.hours}:${w.hours} fasting).`);
  }
  const current = context?.currentWeightKg ?? profile.weightGoal?.startWeightKg ?? profile.weightKg;
  const plan = current ? weightPlan(profile, current) : null;
  if (plan) {
    lines.push(
      `Weight goal: ${plan.currentKg} kg now → ${plan.targetKg} kg by ${profile.weightGoal!.targetDate} (${plan.kgToGo} kg to go, safe pace ${plan.safeWeeklyKg} kg/week). Daily calorie budget: ${plan.dailyKcal} kcal.` +
        (plan.warnings.length ? ` Notes: ${plan.warnings.join(' ')}` : ''),
    );
  }
  return lines.join('\n');
}

function describeContext(profile?: HealthProfile, context?: EatingContext) {
  if (!context) return '';
  const parts: string[] = [];
  if (context.localTime) parts.push(`Local time now: ${context.localTime}.`);
  if (context.todayKcal !== undefined) {
    const budget = dailyKcal(profile, context.currentWeightKg);
    parts.push(
      `Already eaten today: about ${Math.round(context.todayKcal)} kcal and ${Math.round(context.todayCarbs ?? 0)} g carbs in ${context.mealsToday ?? 0} meal(s). Daily budget ${budget} kcal, so about ${Math.round(budget - context.todayKcal)} kcal remain before this meal.`,
    );
  }
  return parts.join(' ');
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

export async function analyzeMeal(args: {
  imageBase64?: string;
  mediaType: 'image/jpeg' | 'image/png' | 'image/webp';
  profile?: HealthProfile;
  context?: EatingContext;
  note?: string;
  answers?: { question: string; answer: string }[];
  hints?: ClassifierHint[];
}): Promise<AiMeal> {
  const parts: string[] = [describeProfile(args.profile, args.context)];
  const ctx = describeContext(args.profile, args.context);
  if (ctx) parts.push(ctx);
  if (args.note) {
    parts.push(args.imageBase64 ? `The person says about this meal: "${args.note}"` : `The person describes the meal they are about to eat: "${args.note}"`);
  }
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
  parts.push(args.imageBase64 ? 'Analyse this meal photo. Is it a good choice for this person right now?' : 'Analyse this described meal. Is it a good choice for this person right now?');

  const content: Anthropic.Beta.BetaContentBlockParam[] = [];
  if (args.imageBase64) {
    content.push({ type: 'image', source: { type: 'base64', media_type: args.mediaType, data: args.imageBase64 } });
  }
  content.push({ type: 'text', text: parts.join('\n\n') });
  return structured(aiMealSchema, ANALYZE_SYSTEM, content);
}

export async function suggestRecipes(args: {
  ingredients: string[];
  dish?: string;
  mealType: string;
  profile?: HealthProfile;
  context?: EatingContext;
}): Promise<AiRecipes> {
  const parts = [describeProfile(args.profile, args.context)];
  const ctx = describeContext(args.profile, args.context);
  if (ctx) parts.push(ctx);
  if (args.dish) parts.push(`They want to cook: ${args.dish}`);
  if (args.ingredients.length) parts.push(`Ingredients they have: ${args.ingredients.join(', ')}`);
  parts.push(`Meal: ${args.mealType}. Suggest healthy meal ideas.`);
  return structured(aiRecipesSchema, RECIPE_SYSTEM, [{ type: 'text', text: parts.join('\n\n') }]);
}

export async function coach(args: {
  profile?: HealthProfile;
  context?: EatingContext;
  todaysMeals: { name: string; kcal: number; carbs: number; protein: number; time: string }[];
  recentDays: { date: string; kcal: number }[];
  question?: string;
}): Promise<AiCoach> {
  const parts = [describeProfile(args.profile, args.context)];
  const ctx = describeContext(args.profile, args.context);
  if (ctx) parts.push(ctx);
  parts.push(
    args.todaysMeals.length
      ? `Meals eaten today:\n${args.todaysMeals.map((m) => `- ${m.time} ${m.name}: ${Math.round(m.kcal)} kcal, ${Math.round(m.carbs)} g carbs, ${Math.round(m.protein)} g protein`).join('\n')}`
      : 'No meals logged yet today.',
  );
  if (args.recentDays.length) {
    parts.push(`Recent days (kcal): ${args.recentDays.map((d) => `${d.date}: ${Math.round(d.kcal)}`).join(', ')}`);
  }
  if (args.question) parts.push(`Their question: "${args.question}"`);
  return structured(aiCoachSchema, COACH_SYSTEM, [{ type: 'text', text: parts.join('\n\n') }]);
}
