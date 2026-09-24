import {
  balanceScore,
  calorieBudget,
  groupShares,
  healthFlags,
  idealPlate,
  missingGroups,
  nutrientsFor,
  ruleVerdict,
  stricterVerdict,
  sumNutrients,
} from '../../shared/nutrition';
import { FOOD_BY_ID } from '../../shared/foods';
import type { EatingContext, HealthProfile, MealAnalysis, MealItem } from '../../shared/types';
import type { AiMeal } from './schemas';

const clamp01 = (n: number) => Math.max(0, Math.min(1, n));

/**
 * Turn Claude's identification of the plate into a full analysis. Nutrients
 * come from the Foodio database whenever the food was matched, and the
 * rule-based health checks can only make the verdict stricter, never softer.
 */
export function buildAnalysis(
  ai: AiMeal,
  profile?: HealthProfile,
  context?: EatingContext,
  fromPhoto = true,
): MealAnalysis {
  const items: MealItem[] = ai.items.map((it) => {
    const grams = Math.max(0, Math.round(it.grams));
    const foodId = it.foodId && FOOD_BY_ID[it.foodId] ? it.foodId : null;
    const fromDb = nutrientsFor(foodId, grams);
    const k = grams / 100;
    const e = it.estimatedPer100g;
    // Hidden additions (extra salt/oil/sugar the person told us about) are added
    // on top of database values; AI estimates for unknown foods already include them.
    const hidden = {
      saltMg: Math.max(0, Math.round(it.hiddenAdditions.saltMg)),
      oilG: Math.max(0, Math.round(it.hiddenAdditions.oilG)),
      sugarG: Math.max(0, Math.round(it.hiddenAdditions.sugarG)),
    };
    return {
      name: it.name,
      foodId,
      grams,
      plateFraction: clamp01(it.plateFraction),
      preparation: it.preparation,
      confidence: clamp01(it.confidence),
      group: foodId ? FOOD_BY_ID[foodId].group : it.group,
      hidden,
      nutrients: fromDb
        ? {
            ...fromDb,
            kcal: fromDb.kcal + hidden.oilG * 9 + hidden.sugarG * 4,
            fat: Math.round((fromDb.fat + hidden.oilG) * 10) / 10,
            carbs: Math.round((fromDb.carbs + hidden.sugarG) * 10) / 10,
            sodiumMg: fromDb.sodiumMg + hidden.saltMg,
          }
        : {
            kcal: Math.round(e.kcal * k),
            carbs: Math.round(e.carbs * k * 10) / 10,
            protein: Math.round(e.protein * k * 10) / 10,
            fat: Math.round(e.fat * k * 10) / 10,
            fiber: Math.round(e.fiber * k * 10) / 10,
            sodiumMg: Math.round(e.sodiumMg * k),
          },
    };
  });

  const shares = groupShares(items);
  const score = balanceScore(shares, profile);
  const flags = healthFlags(items, profile, context);
  const totals = sumNutrients(items);
  const verdict = ai.isFood ? stricterVerdict(ai.verdict, ruleVerdict(flags, score)) : 'caution';
  // If the rules escalated the verdict, lead with the reason they did.
  const decisive = verdict !== ai.verdict ? flags.find((f) => f.severity === (verdict === 'avoid' ? 'danger' : 'warning')) : undefined;
  const verdictReason = decisive ? `${decisive.message} ${ai.verdictReason}`.trim() : ai.verdictReason;

  return {
    mealName: ai.isFood ? ai.mealName : 'No meal detected',
    summary: ai.summary,
    items,
    totals,
    balanceScore: ai.isFood ? score : 0,
    groupShares: shares,
    missingGroups: ai.isFood ? missingGroups(shares, profile) : [],
    verdict,
    verdictReason,
    flags,
    suggestions: ai.suggestions,
    portionAdvice: ai.portionAdvice,
    idealPlate: idealPlate(profile),
    questions: ai.questions.slice(0, 4),
    overallConfidence: clamp01(ai.overallConfidence),
    analyzedAt: new Date().toISOString(),
    budget: ai.isFood ? calorieBudget(totals.kcal, profile, context) : undefined,
    fromPhoto,
  };
}
