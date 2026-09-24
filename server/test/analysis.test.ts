import { describe, expect, it } from 'vitest';
import { FOODS, FOOD_BY_ID } from '../../shared/foods';
import { balanceScore, dailyKcal, groupShares, healthFlags, nutrientsFor } from '../../shared/nutrition';
import type { HealthProfile, MealItem } from '../../shared/types';
import { buildAnalysis } from '../src/analysis';
import type { AiMeal } from '../src/schemas';

const profile = (p: Partial<HealthProfile> = {}): HealthProfile => ({
  name: 'Test',
  goal: 'maintain',
  conditions: [],
  allergies: [],
  ...p,
});

function aiItem(foodId: string | null, grams: number, plateFraction: number, group: AiMeal['items'][number]['group'] = 'starch') {
  return {
    name: foodId ?? 'mystery',
    foodId,
    grams,
    plateFraction,
    preparation: 'cooked',
    confidence: 0.9,
    group,
    estimatedPer100g: { kcal: 100, carbs: 10, protein: 5, fat: 3, fiber: 1, sodiumMg: 50 },
  };
}

function aiMeal(items: AiMeal['items']): AiMeal {
  return {
    isFood: true,
    mealName: 'Test meal',
    summary: '',
    items,
    overallConfidence: 0.9,
    questions: [],
    verdict: 'good',
    verdictReason: '',
    suggestions: [],
    portionAdvice: [],
  };
}

describe('food database', () => {
  it('has unique ids and sane nutrient values', () => {
    expect(new Set(FOODS.map((f) => f.id)).size).toBe(FOODS.length);
    for (const f of FOODS) {
      const p = f.per100g;
      expect(p.kcal).toBeGreaterThan(0);
      // Energy from macros should roughly match kcal (4/4/9 rule).
      const fromMacros = 4 * p.carbs + 4 * p.protein + 9 * p.fat;
      expect(Math.abs(fromMacros - p.kcal) / p.kcal).toBeLessThan(0.3);
    }
  });
});

describe('nutrition maths', () => {
  it('scales database nutrients by grams', () => {
    expect(nutrientsFor('matooke', 200)).toMatchObject({ kcal: 244, carbs: 62 });
    expect(nutrientsFor('not-a-food', 100)).toBeNull();
  });

  it('normalises plate shares', () => {
    const shares = groupShares([
      { group: 'starch', plateFraction: 0.6 },
      { group: 'protein', plateFraction: 0.3 },
      { group: 'vegetable', plateFraction: 0.3 },
    ]);
    expect(shares.starch).toBeCloseTo(0.5);
    expect(shares.protein).toBeCloseTo(0.25);
  });

  it('scores a balanced plate higher than a starch-heavy one', () => {
    const balanced = balanceScore({ vegetable: 0.4, protein: 0.25, starch: 0.25, fruit: 0.1, fat: 0, dairy: 0, sugary: 0 });
    const starchy = balanceScore({ vegetable: 0, protein: 0.15, starch: 0.85, fruit: 0, fat: 0, dairy: 0, sugary: 0 });
    expect(balanced).toBe(100);
    expect(starchy).toBeLessThan(50);
  });

  it('estimates daily energy needs', () => {
    expect(dailyKcal()).toBe(2000);
    expect(dailyKcal(profile({ age: 30, sex: 'female', weightKg: 65, heightCm: 165, activity: 'moderate' }))).toBeGreaterThan(1800);
  });
});

describe('health rules', () => {
  const item = (foodId: string, grams: number, plateFraction: number): MealItem => ({
    name: FOOD_BY_ID[foodId].name,
    foodId,
    grams,
    plateFraction,
    preparation: '',
    confidence: 1,
    group: FOOD_BY_ID[foodId].group,
    nutrients: nutrientsFor(foodId, grams)!,
  });

  it('warns a diabetic about a big posho plate with sugary tea', () => {
    const flags = healthFlags(
      [item('posho', 400, 0.7), item('beans', 150, 0.3), item('tea-milk-sugar', 250, 0)],
      profile({ conditions: ['diabetes'] }),
    );
    expect(flags.some((f) => f.condition === 'diabetes' && f.severity === 'danger')).toBe(true);
  });

  it('flags peanut allergy in groundnut sauce', () => {
    const flags = healthFlags([item('matooke', 300, 0.6), item('gnut-sauce', 150, 0.4)], profile({ allergies: ['peanut'] }));
    expect(flags).toContainEqual(expect.objectContaining({ severity: 'danger', condition: 'peanut' }));
  });

  it('flags salty food for hypertension', () => {
    const flags = healthFlags([item('eshabwe', 100, 0.3), item('muchomo', 200, 0.7)], profile({ conditions: ['hypertension'] }));
    expect(flags.some((f) => f.condition === 'hypertension')).toBe(true);
  });
});

describe('buildAnalysis', () => {
  it('uses database nutrients for matched foods and AI estimates otherwise', () => {
    const a = buildAnalysis(aiMeal([aiItem('beans', 200, 0.5, 'protein'), aiItem(null, 100, 0.5)]));
    expect(a.items[0].nutrients.kcal).toBe(256);
    expect(a.items[1].nutrients.kcal).toBe(100);
    expect(a.totals.kcal).toBe(356);
  });

  it('ignores unknown food ids from the AI', () => {
    const a = buildAnalysis(aiMeal([aiItem('made-up-id', 100, 1)]));
    expect(a.items[0].foodId).toBeNull();
  });

  it('never softens the verdict below what the rules require', () => {
    const a = buildAnalysis(aiMeal([aiItem('gnut-sauce', 150, 0.4, 'protein'), aiItem('matooke', 300, 0.6)]), profile({ allergies: ['peanut'] }));
    expect(a.verdict).toBe('avoid');
  });

  it('marks the veg group missing on a vegetable-free plate', () => {
    const a = buildAnalysis(aiMeal([aiItem('posho', 400, 0.7), aiItem('beef-stew', 200, 0.3, 'protein')]));
    expect(a.missingGroups).toContain('vegetable');
    expect(a.balanceScore).toBeLessThan(60);
  });
});

describe('verdict reason', () => {
  it('explains a rule-escalated verdict', () => {
    const a = buildAnalysis(aiMeal([aiItem('gnut-sauce', 150, 0.4, 'protein')]), profile({ allergies: ['peanut'] }));
    expect(a.verdictReason).toMatch(/peanut/);
  });
});
