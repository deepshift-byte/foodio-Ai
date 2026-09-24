import { describe, expect, it } from 'vitest';
import { FOOD_BY_ID } from '../../shared/foods';
import { healthFlags, inEatingWindow, nutrientsFor } from '../../shared/nutrition';
import type { HealthProfile, MealItem } from '../../shared/types';
import { weightPlan } from '../../shared/weight';
import { buildAnalysis } from '../src/analysis';
import type { AiMeal } from '../src/schemas';

const base: HealthProfile = { name: 'T', goal: 'maintain', conditions: [], allergies: [], sex: 'female', age: 35, heightCm: 165, activity: 'moderate' };

const item = (foodId: string, grams: number, plateFraction = 0.5, hidden = { saltMg: 0, oilG: 0, sugarG: 0 }): MealItem => ({
  name: FOOD_BY_ID[foodId].name,
  foodId,
  grams,
  plateFraction,
  preparation: 'cooked',
  confidence: 1,
  group: FOOD_BY_ID[foodId].group,
  nutrients: nutrientsFor(foodId, grams)!,
  hidden,
});

describe('weight plan', () => {
  const goal = (target: number, targetDate: string) => ({ ...base, weightGoal: { startWeightKg: 80, targetWeightKg: target, startDate: '2026-09-01', targetDate } });

  it('keeps a realistic goal on track with a deficit', () => {
    const plan = weightPlan(goal(76, '2026-11-01'), 80, '2026-09-01')!;
    expect(plan.status).toBe('on-track');
    expect(plan.dailyKcal).toBeLessThan(plan.maintenanceKcal);
    expect(plan.dailyKcal).toBeGreaterThanOrEqual(1200);
  });

  it('flags a crash goal as too fast and caps the pace', () => {
    const plan = weightPlan(goal(70, '2026-10-01'), 80, '2026-09-01')!; // 10 kg in a month
    expect(plan.status).toBe('too-fast');
    expect(plan.warnings[0]).toMatch(/safe/);
    expect(plan.deficitKcal).toBeLessThanOrEqual(1000);
    expect(plan.safeTargetDate > '2026-10-01').toBe(true);
  });

  it('never plans below the minimum intake', () => {
    const small = { ...goal(45, '2026-10-01'), weightGoal: { startWeightKg: 55, targetWeightKg: 45, startDate: '2026-09-01', targetDate: '2026-10-01' }, activity: 'low' as const };
    expect(weightPlan(small, 55, '2026-09-01')!.dailyKcal).toBe(1200);
  });

  it('does not create a deficit during pregnancy or cancer treatment', () => {
    const preg = weightPlan({ ...goal(76, '2026-11-01'), conditions: ['pregnancy'] }, 80, '2026-09-01')!;
    expect(preg.deficitKcal).toBe(0);
    const cancer = weightPlan({ ...goal(76, '2026-11-01'), conditions: ['cancer'], cancerCare: { phase: 'in-treatment', symptoms: [] } }, 80, '2026-09-01')!;
    expect(cancer.deficitKcal).toBe(0);
    expect(cancer.warnings.join(' ')).toMatch(/care team/);
  });

  it('warns when a meal blows the daily budget', () => {
    const p = goal(76, '2026-11-01');
    const flags = healthFlags([item('posho', 500), item('beef-stew', 300)], p, { todayKcal: 1300, currentWeightKg: 80 });
    expect(flags.some((f) => f.condition === 'weight-goal' && f.severity === 'warning')).toBe(true);
  });
});

describe('diet plans', () => {
  it('keto rejects a matooke plate', () => {
    const flags = healthFlags([item('matooke', 300), item('beans', 150)], { ...base, dietPlans: ['keto'] });
    expect(flags).toContainEqual(expect.objectContaining({ condition: 'keto', severity: 'danger' }));
  });

  it('keto accepts fish, greens and avocado', () => {
    const flags = healthFlags([item('tilapia-stew', 200), item('dodo', 150), item('avocado', 70)], { ...base, dietPlans: ['keto'] });
    expect(flags.filter((f) => f.condition === 'keto')).toHaveLength(0);
  });

  it('intermittent fasting warns outside the eating window', () => {
    const p: HealthProfile = { ...base, dietPlans: ['intermittent-fasting'], fastingWindow: { startHour: 12, hours: 8 } };
    expect(healthFlags([item('rolex', 200)], p, { localTime: '08:30' })).toContainEqual(expect.objectContaining({ condition: 'intermittent-fasting' }));
    expect(healthFlags([item('rolex', 200)], p, { localTime: '13:00' }).some((f) => f.condition === 'intermittent-fasting')).toBe(false);
  });

  it('handles eating windows that cross midnight', () => {
    expect(inEatingWindow('23:30', 20, 6)).toBe(true);
    expect(inEatingWindow('01:30', 20, 6)).toBe(true);
    expect(inEatingWindow('03:00', 20, 6)).toBe(false);
  });

  it('vegetarian and vegan checks', () => {
    expect(healthFlags([item('beef-stew', 200)], { ...base, dietPlans: ['vegetarian'] })).toContainEqual(expect.objectContaining({ condition: 'vegetarian', severity: 'danger' }));
    expect(healthFlags([item('egg-boiled', 100)], { ...base, dietPlans: ['vegan'] })).toContainEqual(expect.objectContaining({ condition: 'vegan' }));
    expect(healthFlags([item('egg-boiled', 100)], { ...base, dietPlans: ['vegetarian'] }).some((f) => f.condition === 'vegetarian')).toBe(false);
  });
});

describe('hidden ingredients', () => {
  const ai = (hiddenAdditions: { saltMg: number; oilG: number; sugarG: number }): AiMeal => ({
    isFood: true,
    mealName: 'Beans',
    summary: '',
    items: [{ name: 'Beans', foodId: 'beans', grams: 200, plateFraction: 1, preparation: 'stewed', confidence: 1, group: 'protein', hiddenAdditions, estimatedPer100g: { kcal: 0, carbs: 0, protein: 0, fat: 0, fiber: 0, sodiumMg: 0 } }],
    overallConfidence: 1,
    questions: [],
    verdict: 'good',
    verdictReason: '',
    suggestions: [],
    portionAdvice: [],
  });

  it('adds stock cubes and oil the person reported', () => {
    const plain = buildAnalysis(ai({ saltMg: 0, oilG: 0, sugarG: 0 }));
    const salty = buildAnalysis(ai({ saltMg: 1800, oilG: 14, sugarG: 0 }));
    expect(salty.totals.sodiumMg - plain.totals.sodiumMg).toBe(1800);
    expect(salty.totals.kcal - plain.totals.kcal).toBe(126);
  });

  it('turns hidden salt into a hypertension warning', () => {
    const a = buildAnalysis(ai({ saltMg: 1800, oilG: 0, sugarG: 0 }), { ...base, conditions: ['hypertension'] });
    expect(a.flags.some((f) => f.condition === 'hypertension' && f.severity === 'danger')).toBe(true);
    expect(a.verdict).toBe('avoid');
  });
});

describe('cancer care', () => {
  const p: HealthProfile = { ...base, conditions: ['cancer'], cancerCare: { phase: 'in-treatment', symptoms: ['low-immunity', 'mouth-sores'] } };

  it('flags raw salad for low immunity and acidic food for mouth sores', () => {
    const flags = healthFlags([item('kachumbari', 100), item('rice', 200)], p);
    expect(flags).toContainEqual(expect.objectContaining({ condition: 'food-safety', severity: 'warning' }));
    expect(flags.some((f) => f.condition === 'cancer' && /mouth sores/.test(f.message))).toBe(true);
  });

  it('encourages protein in low-protein meals', () => {
    expect(healthFlags([item('rice', 200)], p).some((f) => /protein/i.test(f.message))).toBe(true);
  });
});
