// Types shared by the Foodio mobile/web app and the Foodio API server.

export type FoodGroup = 'starch' | 'protein' | 'vegetable' | 'fruit' | 'fat' | 'dairy' | 'sugary';

export type GlycemicIndex = 'low' | 'medium' | 'high';

export type FoodTag =
  | 'fried'
  | 'high-sodium'
  | 'high-sat-fat'
  | 'added-sugar'
  | 'contains-peanut'
  | 'contains-gluten'
  | 'contains-fish'
  | 'contains-egg'
  | 'contains-dairy'
  | 'high-purine'
  | 'high-potassium'
  | 'iron-rich'
  | 'calcium-rich'
  | 'vitamin-a-rich'
  | 'fiber-rich'
  | 'whole-grain'
  | 'plant-protein'
  | 'processed';

export interface NutrientsPer100g {
  kcal: number;
  carbs: number; // g
  protein: number; // g
  fat: number; // g
  fiber: number; // g
  sodiumMg: number;
}

export interface FoodItem {
  id: string;
  name: string;
  localNames: string[];
  origin: string; // e.g. "Uganda (Central)", "West Africa"
  group: FoodGroup;
  per100g: NutrientsPer100g;
  gi: GlycemicIndex;
  typicalServingG: number;
  tags: FoodTag[];
  description: string;
  healthierTip: string;
}

export type HealthCondition =
  | 'diabetes'
  | 'prediabetes'
  | 'hypertension'
  | 'high-cholesterol'
  | 'kidney-disease'
  | 'pregnancy'
  | 'weight-loss'
  | 'anemia'
  | 'ulcers-gerd'
  | 'gout'
  | 'celiac';

export type Allergy = 'peanut' | 'gluten' | 'fish' | 'egg' | 'dairy';

export type Goal = 'maintain' | 'lose' | 'gain' | 'muscle';

export interface HealthProfile {
  name: string;
  age?: number;
  sex?: 'female' | 'male' | 'other';
  weightKg?: number;
  heightCm?: number;
  activity?: 'low' | 'moderate' | 'high';
  goal: Goal;
  conditions: HealthCondition[];
  allergies: Allergy[];
  notes?: string; // free-text, e.g. medications or doctor's advice
}

export interface MealItem {
  name: string;
  foodId: string | null; // match in the Foodio food database, if any
  grams: number;
  plateFraction: number; // 0..1 share of the plate area
  preparation: string;
  confidence: number; // 0..1
  group: FoodGroup;
  nutrients: Omit<NutrientsPer100g, 'sodiumMg'> & { sodiumMg: number }; // totals for this portion
}

export interface ClarifyingQuestion {
  id: string;
  question: string;
  options: string[];
}

export type Verdict = 'good' | 'caution' | 'avoid';

export interface HealthFlag {
  severity: 'info' | 'warning' | 'danger';
  condition: HealthCondition | Allergy | 'general';
  message: string;
}

export interface PlateSection {
  label: string;
  group: FoodGroup;
  fraction: number;
  foods: string[];
  grams?: number;
}

export interface MealAnalysis {
  mealName: string;
  summary: string;
  items: MealItem[];
  totals: NutrientsPer100g;
  balanceScore: number; // 0..100
  groupShares: Record<FoodGroup, number>;
  missingGroups: FoodGroup[];
  verdict: Verdict;
  verdictReason: string;
  flags: HealthFlag[];
  suggestions: string[];
  portionAdvice: string[];
  idealPlate: PlateSection[];
  questions: ClarifyingQuestion[];
  overallConfidence: number;
  analyzedAt: string;
}

export interface RecipeIdea {
  name: string;
  description: string;
  whyHealthy: string;
  prepMinutes: number;
  servings: number;
  ingredientsUsed: string[];
  extraIngredientsNeeded: string[];
  steps: string[];
  plate: PlateSection[];
  kcalPerServing: number;
  suitableFor: string; // how it fits the user's conditions
}

export interface RecipeResponse {
  ideas: RecipeIdea[];
  note: string;
}
