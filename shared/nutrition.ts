import { FOOD_BY_ID } from './foods';
import type {
  Allergy,
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
  if (c.has('diabetes') || c.has('prediabetes') || c.has('weight-loss') || profile?.goal === 'lose') {
    return { vegetable: 0.5, protein: 0.25, starch: 0.25 };
  }
  if (c.has('kidney-disease')) return { vegetable: 0.45, starch: 0.3, protein: 0.2, fruit: 0.05 };
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
    const have = g === 'fat' || g === 'dairy' ? Math.max(0, shares[g] - 0.1) : shares[g];
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

/** Estimated daily energy need (Mifflin–St Jeor), falling back to 2000 kcal. */
export function dailyKcal(profile?: HealthProfile): number {
  if (!profile?.weightKg || !profile.heightCm || !profile.age) return 2000;
  const s = profile.sex === 'male' ? 5 : profile.sex === 'female' ? -161 : -78;
  const bmr = 10 * profile.weightKg + 6.25 * profile.heightCm - 5 * profile.age + s;
  const factor = profile.activity === 'high' ? 1.725 : profile.activity === 'moderate' ? 1.55 : 1.375;
  let kcal = bmr * factor;
  if (profile.goal === 'lose' || profile.conditions.includes('weight-loss')) kcal -= 500;
  if (profile.goal === 'gain' || profile.goal === 'muscle') kcal += 300;
  if (profile.conditions.includes('pregnancy')) kcal += 340;
  return Math.max(1200, Math.round(kcal / 10) * 10);
}

export function idealPlate(profile?: HealthProfile): PlateSection[] {
  const target = targetPlate(profile);
  const c = new Set(profile?.conditions ?? []);
  const lowGi = c.has('diabetes') || c.has('prediabetes');
  const examples: Record<FoodGroup, string[]> = {
    vegetable: ['Dodo', 'Nakati', 'Sukuma wiki', 'Cabbage', 'Kachumbari'],
    protein: c.has('kidney-disease')
      ? ['Small portion of fish', 'Egg']
      : ['Beans', 'Peas', 'Tilapia', 'Chicken', c.has('pregnancy') || c.has('anemia') ? 'Mukene' : 'Groundnut sauce'],
    starch: lowGi ? ['Millet (kalo)', 'Sweet potato', 'Yam', 'Small matooke'] : ['Matooke', 'Kalo', 'Sweet potato', 'Brown rice'],
    fruit: ['Pawpaw', 'Orange', 'Passion fruit'],
    fat: ['Avocado'],
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

/** Rule-based checks of a meal against the person's conditions and allergies. */
export function healthFlags(items: MealItem[], profile?: HealthProfile): HealthFlag[] {
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
    const sugary = items.filter((it) => it.group === 'sugary' || itemTags(it).includes('added-sugar')).map((it) => it.name);
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
    const salty = withTag('high-sodium');
    if (salty.length) flags.push({ severity: 'warning', condition: 'hypertension', message: `Salty foods: ${list(salty)}.` });
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

  const mealKcal = dailyKcal(profile) / 3;
  if ((c.has('weight-loss') || profile?.goal === 'lose') && totals.kcal > mealKcal * 1.15) {
    flags.push({ severity: 'warning', condition: 'weight-loss', message: `About ${totals.kcal} kcal — above your ~${Math.round(mealKcal)} kcal per-meal target.` });
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
