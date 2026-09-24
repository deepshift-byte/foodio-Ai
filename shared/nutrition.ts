import { FOOD_BY_ID } from './foods';
import { latestWeight, maintenanceKcal, weightPlan } from './weight';
import type {
  Allergy,
  CalorieBudget,
  DietPlan,
  EatingContext,
  FoodGroup,
  FoodTag,
  HealthFlag,
  HealthProfile,
  MealItem,
  NutrientsPer100g,
  PlateSection,
  Verdict,
} from './types';

// Deterministic nutrition maths and health rules. The AI identifies foods and
// estimates portions; everything below turns that into numbers and warnings
// that are reproducible and testable.

export const FOOD_GROUPS: FoodGroup[] = ['vegetable', 'protein', 'starch', 'fruit', 'fat', 'dairy', 'sugary'];

export const GROUP_LABELS: Record<FoodGroup, string> = {
  vegetable: 'Vegetables',
  protein: 'Protein',
  starch: 'Starch / staples',
  fruit: 'Fruit',
  fat: 'Healthy fats',
  dairy: 'Dairy',
  sugary: 'Sugary / treats',
};

const ZERO: NutrientsPer100g = { kcal: 0, carbs: 0, protein: 0, fat: 0, fiber: 0, sodiumMg: 0 };

const round = (n: number, dp = 0) => {
  const f = 10 ** dp;
  return Math.round(n * f) / f;
};

/** Nutrients for `grams` of a database food, or null if the id is unknown. */
export function nutrientsFor(foodId: string | null, grams: number): NutrientsPer100g | null {
  const food = foodId ? FOOD_BY_ID[foodId] : undefined;
  if (!food) return null;
  const k = grams / 100;
  const p = food.per100g;
  return {
    kcal: round(p.kcal * k),
    carbs: round(p.carbs * k, 1),
    protein: round(p.protein * k, 1),
    fat: round(p.fat * k, 1),
    fiber: round(p.fiber * k, 1),
    sodiumMg: round(p.sodiumMg * k),
  };
}

export function sumNutrients(items: Pick<MealItem, 'nutrients'>[]): NutrientsPer100g {
  const t = items.reduce(
    (acc, it) => ({
      kcal: acc.kcal + it.nutrients.kcal,
      carbs: acc.carbs + it.nutrients.carbs,
      protein: acc.protein + it.nutrients.protein,
      fat: acc.fat + it.nutrients.fat,
      fiber: acc.fiber + it.nutrients.fiber,
      sodiumMg: acc.sodiumMg + it.nutrients.sodiumMg,
    }),
    { ...ZERO },
  );
  return {
    kcal: round(t.kcal),
    carbs: round(t.carbs, 1),
    protein: round(t.protein, 1),
    fat: round(t.fat, 1),
    fiber: round(t.fiber, 1),
    sodiumMg: round(t.sodiumMg),
  };
}

/** Share of the plate taken by each food group (normalised to sum to 1). */
export function groupShares(items: Pick<MealItem, 'group' | 'plateFraction'>[]): Record<FoodGroup, number> {
  const shares = Object.fromEntries(FOOD_GROUPS.map((g) => [g, 0])) as Record<FoodGroup, number>;
  const total = items.reduce((s, it) => s + Math.max(0, it.plateFraction), 0);
  if (total <= 0) return shares;
  for (const it of items) shares[it.group] += Math.max(0, it.plateFraction) / total;
  for (const g of FOOD_GROUPS) shares[g] = round(shares[g], 3);
  return shares;
}

type Target = Partial<Record<FoodGroup, number>>;

/** Recommended plate proportions for this person (a "healthy plate" model). */
export function targetPlate(profile?: HealthProfile): Target {
  const c = new Set(profile?.conditions ?? []);
  const diets = new Set(profile?.dietPlans ?? []);
  if (c.has('kidney-disease')) return { vegetable: 0.45, starch: 0.3, protein: 0.2, fruit: 0.05 };
  if (c.has('cancer') && profile?.cancerCare?.phase === 'in-treatment') {
    return { vegetable: 0.35, protein: 0.3, starch: 0.3, fruit: 0.05 };
  }
  if (diets.has('keto') && !c.has('pregnancy')) return { vegetable: 0.55, protein: 0.3, fat: 0.15 };
  if (diets.has('low-carb')) return { vegetable: 0.5, protein: 0.3, starch: 0.15, fat: 0.05 };
  if (diets.has('high-protein')) return { vegetable: 0.4, protein: 0.35, starch: 0.2, fruit: 0.05 };
  if (c.has('diabetes') || c.has('prediabetes') || c.has('weight-loss') || profile?.goal === 'lose') {
    return { vegetable: 0.5, protein: 0.25, starch: 0.25 };
  }
  if (c.has('pregnancy') || c.has('anemia')) return { vegetable: 0.4, protein: 0.3, starch: 0.25, fruit: 0.05 };
  if (profile?.goal === 'gain' || profile?.goal === 'muscle') {
    return { vegetable: 0.35, protein: 0.3, starch: 0.3, fruit: 0.05 };
  }
  return { vegetable: 0.4, protein: 0.25, starch: 0.25, fruit: 0.1 };
}

/** 0–100 score: how close the plate is to the recommended proportions. */
export function balanceScore(shares: Record<FoodGroup, number>, profile?: HealthProfile): number {
  const target = targetPlate(profile);
  let distance = 0;
  for (const g of FOOD_GROUPS) {
    const want = target[g] ?? 0;
    // Healthy fats and dairy in small amounts are fine; don't penalise < 10%.
    const have = (g === 'fat' || g === 'dairy') && !(target[g] ?? 0) ? Math.max(0, shares[g] - 0.1) : shares[g];
    distance += Math.abs(have - want);
  }
  // distance ranges 0..2; add an extra penalty for sugary food.
  const score = 100 - distance * 55 - shares.sugary * 40;
  return Math.max(0, Math.min(100, Math.round(score)));
}

export function missingGroups(shares: Record<FoodGroup, number>, profile?: HealthProfile): FoodGroup[] {
  const target = targetPlate(profile);
  return (Object.keys(target) as FoodGroup[]).filter((g) => (target[g] ?? 0) >= 0.1 && shares[g] < 0.05);
}

/**
 * Daily calorie target: from the weight plan when the person has a weight
 * goal, otherwise maintenance adjusted for their general goal.
 */
export function dailyKcal(profile?: HealthProfile, currentWeightKg?: number): number {
  if (!profile) return 2000;
  const current = currentWeightKg ?? latestWeight([], profile);
  if (profile.weightGoal && current) {
    const plan = weightPlan(profile, current);
    if (plan) return plan.dailyKcal;
  }
  if (!profile.weightKg || !profile.heightCm || !profile.age) return 2000;
  let kcal = maintenanceKcal(profile, current);
  if (profile.goal === 'lose' || profile.conditions.includes('weight-loss')) kcal -= 500;
  if (profile.goal === 'gain' || profile.goal === 'muscle') kcal += 300;
  if (profile.conditions.includes('pregnancy')) kcal += 340;
  return Math.max(1200, Math.round(kcal / 10) * 10);
}

export function calorieBudget(mealKcal: number, profile?: HealthProfile, context?: EatingContext): CalorieBudget {
  const daily = dailyKcal(profile, context?.currentWeightKg);
  const before = Math.round(context?.todayKcal ?? 0);
  const after = before + Math.round(mealKcal);
  return { dailyKcal: daily, consumedBefore: before, afterMeal: after, remaining: daily - after };
}

export const DIET_LABELS: Record<DietPlan, string> = {
  keto: 'Keto',
  'low-carb': 'Low-carb',
  'intermittent-fasting': 'Intermittent fasting',
  mediterranean: 'Mediterranean',
  dash: 'DASH (low salt)',
  vegetarian: 'Vegetarian',
  vegan: 'Vegan',
  'high-protein': 'High-protein',
};

/** Is "HH:MM" inside the eating window? */
export function inEatingWindow(localTime: string, startHour: number, hours: number): boolean {
  const [h, m] = localTime.split(':').map(Number);
  if (!Number.isFinite(h)) return true;
  const minutes = h * 60 + (m || 0);
  const start = startHour * 60;
  const end = start + hours * 60;
  return end <= 1440 ? minutes >= start && minutes < end : minutes >= start || minutes < end - 1440;
}

export function formatHour(h: number): string {
  return `${String(((h % 24) + 24) % 24).padStart(2, '0')}:00`;
}

export function idealPlate(profile?: HealthProfile): PlateSection[] {
  const target = targetPlate(profile);
  const c = new Set(profile?.conditions ?? []);
  const lowGi = c.has('diabetes') || c.has('prediabetes');
  const examples: Record<FoodGroup, string[]> = {
    vegetable: ['Dodo', 'Nakati', 'Sukuma wiki', 'Cabbage', 'Kachumbari'],
    protein: c.has('kidney-disease')
      ? ['Small portion of fish', 'Egg']
      : profile?.dietPlans?.includes('vegan')
      ? ['Beans', 'Peas', 'Groundnut sauce', 'Mushrooms']
      : profile?.dietPlans?.includes('vegetarian')
      ? ['Beans', 'Peas', 'Eggs', 'Groundnut sauce']
      : ['Beans', 'Peas', 'Tilapia', 'Chicken', c.has('pregnancy') || c.has('anemia') ? 'Mukene' : 'Groundnut sauce'],
    starch: profile?.dietPlans?.includes('low-carb') ? ['Small sweet potato', 'Small kalo'] : lowGi ? ['Millet (kalo)', 'Sweet potato', 'Yam', 'Small matooke'] : ['Matooke', 'Kalo', 'Sweet potato', 'Brown rice'],
    fruit: ['Pawpaw', 'Orange', 'Passion fruit'],
    fat: profile?.allergies.includes('peanut') ? ['Avocado'] : ['Avocado', 'Roasted groundnuts'],
    dairy: ['Milk'],
    sugary: [],
  };
  return (Object.keys(target) as FoodGroup[]).map((g) => ({
    label: GROUP_LABELS[g],
    group: g,
    fraction: target[g] ?? 0,
    foods: examples[g].filter((f) => !(profile?.allergies.includes('peanut') && /groundnut/i.test(f))),
  }));
}

const ALLERGY_TAG: Record<Allergy, FoodTag> = {
  peanut: 'contains-peanut',
  gluten: 'contains-gluten',
  fish: 'contains-fish',
  egg: 'contains-egg',
  dairy: 'contains-dairy',
};

function itemTags(item: MealItem): FoodTag[] {
  return item.foodId && FOOD_BY_ID[item.foodId] ? FOOD_BY_ID[item.foodId].tags : [];
}

function itemGi(item: MealItem) {
  return item.foodId && FOOD_BY_ID[item.foodId] ? FOOD_BY_ID[item.foodId].gi : undefined;
}

/**
 * Rule-based checks of a meal against the person's conditions, allergies,
 * diet plans, weight goal and what they have already eaten today.
 */
export function healthFlags(items: MealItem[], profile?: HealthProfile, context?: EatingContext): HealthFlag[] {
  const flags: HealthFlag[] = [];
  const totals = sumNutrients(items);
  const shares = groupShares(items);
  const c = new Set(profile?.conditions ?? []);
  const withTag = (tag: FoodTag) => items.filter((it) => itemTags(it).includes(tag)).map((it) => it.name);
  const list = (names: string[]) => names.join(', ');

  for (const allergy of profile?.allergies ?? []) {
    const hits = withTag(ALLERGY_TAG[allergy]);
    if (hits.length) {
      flags.push({ severity: 'danger', condition: allergy, message: `Contains ${allergy}: ${list(hits)}. Do not eat this if you are allergic.` });
    }
  }

  if (c.has('celiac')) {
    const hits = withTag('contains-gluten');
    if (hits.length) flags.push({ severity: 'danger', condition: 'celiac', message: `Contains gluten (wheat): ${list(hits)}.` });
  }

  if (c.has('diabetes') || c.has('prediabetes')) {
    const cond = c.has('diabetes') ? 'diabetes' : 'prediabetes';
    if (totals.carbs > 90) {
      flags.push({ severity: 'danger', condition: cond, message: `About ${Math.round(totals.carbs)} g of carbohydrate — too much for one meal. Aim for roughly 45–60 g.` });
    } else if (totals.carbs > 60) {
      flags.push({ severity: 'warning', condition: cond, message: `About ${Math.round(totals.carbs)} g of carbohydrate — reduce the starch portion (aim for 45–60 g).` });
    }
    const highGi = items.filter((it) => it.group === 'starch' && itemGi(it) === 'high').map((it) => it.name);
    if (highGi.length && shares.starch > 0.3) {
      flags.push({ severity: 'warning', condition: cond, message: `High-GI starch (${list(highGi)}) makes up a large part of the plate and can spike blood sugar.` });
    }
    const sugary = items
      .filter((it) => it.group === 'sugary' || itemTags(it).includes('added-sugar') || it.hidden.sugarG >= 4)
      .map((it) => it.name);
    if (sugary.length) {
      flags.push({ severity: c.has('diabetes') ? 'danger' : 'warning', condition: cond, message: `Added sugar in ${list(sugary)}.` });
    }
    if (totals.fiber < 8) {
      flags.push({ severity: 'info', condition: cond, message: 'Low in fibre — add greens or beans to slow sugar absorption.' });
    }
  }

  if (c.has('hypertension')) {
    if (totals.sodiumMg > 1200) {
      flags.push({ severity: 'danger', condition: 'hypertension', message: `Roughly ${totals.sodiumMg} mg sodium — very salty for one meal.` });
    } else if (totals.sodiumMg > 800) {
      flags.push({ severity: 'warning', condition: 'hypertension', message: `Roughly ${totals.sodiumMg} mg sodium — try to keep meals under 700 mg.` });
    }
    const salty = [...new Set([...withTag('high-sodium'), ...items.filter((it) => it.hidden.saltMg >= 200).map((it) => it.name)])];
    if (salty.length) flags.push({ severity: 'warning', condition: 'hypertension', message: `Salty foods (including added salt or stock cubes): ${list(salty)}.` });
  }

  if (c.has('high-cholesterol')) {
    const fatty = [...new Set([...withTag('high-sat-fat'), ...withTag('fried')])];
    if (fatty.length) flags.push({ severity: 'warning', condition: 'high-cholesterol', message: `Fried or saturated-fat foods: ${list(fatty)}.` });
    const organ = withTag('high-purine').filter((n) => /offal|byenda|liver/i.test(n));
    if (organ.length) flags.push({ severity: 'warning', condition: 'high-cholesterol', message: `Organ meats are high in cholesterol: ${list(organ)}.` });
  }

  if (c.has('kidney-disease')) {
    if (totals.protein > 30) {
      flags.push({ severity: 'warning', condition: 'kidney-disease', message: `About ${Math.round(totals.protein)} g protein — larger than usually advised for kidney disease. Follow your doctor's limit.` });
    }
    const potassium = withTag('high-potassium');
    if (potassium.length) flags.push({ severity: 'warning', condition: 'kidney-disease', message: `High-potassium foods: ${list(potassium)}.` });
    if (totals.sodiumMg > 700) flags.push({ severity: 'warning', condition: 'kidney-disease', message: `Roughly ${totals.sodiumMg} mg sodium.` });
  }

  if (c.has('gout')) {
    const purine = withTag('high-purine');
    if (purine.length) flags.push({ severity: 'warning', condition: 'gout', message: `High-purine foods can trigger gout: ${list(purine)}.` });
  }

  if (c.has('ulcers-gerd')) {
    const fried = withTag('fried');
    if (fried.length || totals.fat > 30) {
      flags.push({ severity: 'warning', condition: 'ulcers-gerd', message: `Fatty or fried food can worsen reflux${fried.length ? `: ${list(fried)}` : ''}.` });
    }
  }

  if (c.has('pregnancy') || c.has('anemia')) {
    const cond = c.has('pregnancy') ? 'pregnancy' : 'anemia';
    if (!withTag('iron-rich').length) {
      flags.push({ severity: 'info', condition: cond, message: 'No iron-rich food — add beans, dodo, nakati, mukene or lean meat.' });
    }
    const tea = items.filter((it) => it.foodId === 'tea-milk-sugar').map((it) => it.name);
    if (tea.length) flags.push({ severity: 'info', condition: cond, message: 'Tea with meals reduces iron absorption — drink it an hour later.' });
  }

  flags.push(...dietFlags(items, totals, profile, context));
  flags.push(...cancerFlags(items, totals, profile));

  // Calorie budget (weight goal or general weight-loss goal).
  const losing = Boolean(profile?.weightGoal && profile.weightGoal.targetWeightKg < profile.weightGoal.startWeightKg) || c.has('weight-loss') || profile?.goal === 'lose';
  if (losing && totals.kcal > 0) {
    const budget = calorieBudget(totals.kcal, profile, context);
    if (budget.remaining < -Math.max(100, budget.dailyKcal * 0.05)) {
      flags.push({
        severity: 'warning',
        condition: 'weight-goal',
        message: `This meal takes you to about ${budget.afterMeal} of your ${budget.dailyKcal} kcal for today — ${-budget.remaining} kcal over. Eat a smaller portion or save part of it.`,
      });
    } else if (totals.kcal > budget.dailyKcal * 0.45) {
      flags.push({
        severity: 'warning',
        condition: 'weight-goal',
        message: `About ${totals.kcal} kcal is a big share of your ${budget.dailyKcal} kcal day. Reduce the starch or oil.`,
      });
    }
    const fried = [...new Set([...withTag('fried'), ...items.filter((it) => it.hidden.oilG >= 10).map((it) => it.name)])];
    if (fried.length) flags.push({ severity: 'info', condition: 'weight-goal', message: `Oil adds calories fast (1 tablespoon ≈ 120 kcal): ${list(fried)}.` });
  }

  if (shares.vegetable < 0.05 && items.length > 0) {
    flags.push({ severity: 'info', condition: 'general', message: 'No vegetables on this plate.' });
  }

  return flags;
}

const VERDICT_RANK: Record<Verdict, number> = { good: 0, caution: 1, avoid: 2 };

export function ruleVerdict(flags: HealthFlag[], score: number): Verdict {
  if (flags.some((f) => f.severity === 'danger')) return 'avoid';
  if (flags.filter((f) => f.severity === 'warning').length >= 1 || score < 50) return 'caution';
  return 'good';
}

/** Combine the AI's verdict with the rule verdict, keeping the stricter one. */
export function stricterVerdict(a: Verdict, b: Verdict): Verdict {
  return VERDICT_RANK[a] >= VERDICT_RANK[b] ? a : b;
}

function dietFlags(items: MealItem[], totals: NutrientsPer100g, profile?: HealthProfile, context?: EatingContext): HealthFlag[] {
  const flags: HealthFlag[] = [];
  const diets = new Set(profile?.dietPlans ?? []);
  const withTag = (tag: FoodTag) => items.filter((it) => itemTags(it).includes(tag)).map((it) => it.name);
  const list = (names: string[]) => [...new Set(names)].join(', ');
  const starches = items.filter((it) => it.group === 'starch' || it.group === 'sugary').map((it) => it.name);

  // Keto and low-carb targets count net carbs (total carbs minus fibre).
  const netCarbs = Math.max(0, totals.carbs - totals.fiber);
  if (diets.has('keto')) {
    if (netCarbs > 40) {
      flags.push({ severity: 'danger', condition: 'keto', message: `About ${Math.round(netCarbs)} g of net carbs — this will break ketosis (keto is usually 20–50 g for the whole day). Carb sources: ${list(starches) || 'sauces or fruit'}.` });
    } else if (netCarbs > 15) {
      flags.push({ severity: 'warning', condition: 'keto', message: `About ${Math.round(netCarbs)} g of net carbs — high for one keto meal (aim for under 15 g).` });
    }
    const todayCarbs = (context?.todayCarbs ?? 0) + netCarbs;
    if (todayCarbs > 50 && netCarbs <= 40) {
      flags.push({ severity: 'warning', condition: 'keto', message: `With this meal you'll be at about ${Math.round(todayCarbs)} g of carbs today — over a typical keto limit.` });
    }
    if (profile?.conditions.includes('diabetes')) {
      flags.push({ severity: 'info', condition: 'keto', message: 'Keto with diabetes medicines (especially insulin or SGLT2 inhibitors) needs your doctor’s supervision.' });
    }
    if (profile?.conditions.includes('pregnancy')) {
      flags.push({ severity: 'warning', condition: 'keto', message: 'Keto diets are not recommended during pregnancy.' });
    }
  } else if (diets.has('low-carb') && netCarbs > 40) {
    flags.push({ severity: 'warning', condition: 'low-carb', message: `About ${Math.round(netCarbs)} g of net carbs — above a low-carb meal target of about 30–40 g.` });
  }

  if (diets.has('intermittent-fasting') && context?.localTime && totals.kcal > 15) {
    const w = profile?.fastingWindow ?? { startHour: 12, hours: 8 };
    if (!inEatingWindow(context.localTime, w.startHour, w.hours)) {
      flags.push({
        severity: 'warning',
        condition: 'intermittent-fasting',
        message: `It's ${context.localTime} — you're in your fasting period (eating window ${formatHour(w.startHour)}–${formatHour(w.startHour + w.hours)}). Water, black tea or black coffee without sugar won't break your fast.`,
      });
    }
  }

  if (diets.has('vegan')) {
    const animal = [...withTag('contains-meat'), ...withTag('contains-fish'), ...withTag('contains-egg'), ...withTag('contains-dairy')];
    if (animal.length) flags.push({ severity: 'danger', condition: 'vegan', message: `Not vegan: ${list(animal)}.` });
  } else if (diets.has('vegetarian')) {
    const meat = [...withTag('contains-meat'), ...withTag('contains-fish')];
    if (meat.length) flags.push({ severity: 'danger', condition: 'vegetarian', message: `Not vegetarian: ${list(meat)}.` });
  }

  if (diets.has('dash') && totals.sodiumMg > 600) {
    flags.push({ severity: 'warning', condition: 'dash', message: `Roughly ${totals.sodiumMg} mg sodium — DASH meals aim for about 500 mg or less.` });
  }

  if (diets.has('mediterranean')) {
    const heavy = [...withTag('fried'), ...withTag('high-sat-fat'), ...withTag('red-meat')];
    if (heavy.length) flags.push({ severity: 'info', condition: 'mediterranean', message: `Mediterranean eating favours fish, beans and vegetables over fried food and red meat: ${list(heavy)}.` });
  }

  if (diets.has('high-protein') && totals.protein < 25 && totals.kcal > 250) {
    flags.push({ severity: 'info', condition: 'high-protein', message: `Only about ${Math.round(totals.protein)} g protein — add beans, eggs, fish or chicken to reach 25–40 g.` });
  }
  return flags;
}

function cancerFlags(items: MealItem[], totals: NutrientsPer100g, profile?: HealthProfile): HealthFlag[] {
  if (!profile?.conditions.includes('cancer')) return [];
  const flags: HealthFlag[] = [];
  const care = profile.cancerCare;
  const symptoms = new Set(care?.symptoms ?? []);
  const withTag = (tag: FoodTag) => items.filter((it) => itemTags(it).includes(tag)).map((it) => it.name);
  const list = (names: string[]) => [...new Set(names)].join(', ');

  if (symptoms.has('low-immunity')) {
    const raw = [...withTag('raw'), ...items.filter((it) => /\b(raw|rare|undercooked|unwashed)\b/i.test(it.preparation)).map((it) => it.name)];
    if (raw.length) {
      flags.push({ severity: 'warning', condition: 'food-safety', message: `With low immunity, raw or undercooked foods carry infection risk: ${list(raw)}. Eat freshly cooked food; wash and peel fruit; follow your care team's advice.` });
    }
  }
  if (symptoms.has('mouth-sores')) {
    const acidic = withTag('acidic-spicy');
    if (acidic.length) flags.push({ severity: 'info', condition: 'cancer', message: `Acidic or spicy foods can sting mouth sores: ${list(acidic)}. Soft, moist foods like mashed matooke, porridge and soups are easier.` });
  }
  if (symptoms.has('nausea')) {
    const greasy = withTag('fried');
    if (greasy.length || totals.fat > 30) flags.push({ severity: 'info', condition: 'cancer', message: 'Greasy food can worsen nausea — try small, plain meals and sip fluids between meals.' });
  }
  if (symptoms.has('low-appetite') || symptoms.has('unintended-weight-loss') || care?.phase === 'in-treatment') {
    if (totals.protein < 20 && totals.kcal > 0) {
      flags.push({ severity: 'info', condition: 'cancer', message: `Only about ${Math.round(totals.protein)} g protein. Protein helps your body recover — add eggs, milk, beans, g-nut sauce, fish or chicken.` });
    }
    if ((symptoms.has('low-appetite') || symptoms.has('unintended-weight-loss')) && totals.kcal < 400 && totals.kcal > 0) {
      flags.push({ severity: 'info', condition: 'cancer', message: 'A small meal — if you are losing weight without trying, add energy: avocado, g-nut paste, whole milk or an extra snack.' });
    }
  }
  const processedOrGrilled = items.filter((it) => itemTags(it).includes('processed') || (itemTags(it).includes('red-meat') && /grill|char|roast|muchomo/i.test(`${it.preparation} ${it.name}`)));
  if (processedOrGrilled.length) {
    flags.push({ severity: 'info', condition: 'cancer', message: `Cancer-prevention guidance is to limit red, charred and processed meat: ${list(processedOrGrilled.map((i) => i.name))}.` });
  }
  const sugary = items.filter((it) => it.group === 'sugary').map((it) => it.name);
  if (sugary.length) flags.push({ severity: 'info', condition: 'cancer', message: `Limit sugary foods and drinks: ${list(sugary)}.` });
  return flags;
}
