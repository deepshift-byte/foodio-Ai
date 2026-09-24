import { z } from 'zod';

const foodGroup = z.enum(['starch', 'protein', 'vegetable', 'fruit', 'fat', 'dairy', 'sugary']);

// ---------- Request bodies ----------

export const profileSchema = z.object({
  name: z.string().max(80).default(''),
  age: z.number().int().positive().max(120).optional(),
  sex: z.enum(['female', 'male', 'other']).optional(),
  weightKg: z.number().positive().max(400).optional(),
  heightCm: z.number().positive().max(260).optional(),
  activity: z.enum(['low', 'moderate', 'high']).optional(),
  goal: z.enum(['maintain', 'lose', 'gain', 'muscle']).default('maintain'),
  conditions: z
    .array(
      z.enum([
        'diabetes',
        'prediabetes',
        'hypertension',
        'high-cholesterol',
        'kidney-disease',
        'pregnancy',
        'weight-loss',
        'anemia',
        'ulcers-gerd',
        'gout',
        'celiac',
      ]),
    )
    .default([]),
  allergies: z.array(z.enum(['peanut', 'gluten', 'fish', 'egg', 'dairy'])).default([]),
  notes: z.string().max(1000).optional(),
});

export const analyzeRequestSchema = z.object({
  imageBase64: z.string().min(100),
  mediaType: z.enum(['image/jpeg', 'image/png', 'image/webp']).default('image/jpeg'),
  profile: profileSchema.optional(),
  note: z.string().max(500).optional(), // user's own description, e.g. "lunch at work"
  answers: z
    .array(z.object({ question: z.string().max(300), answer: z.string().max(300) }))
    .max(10)
    .optional(),
});

export const recipeRequestSchema = z.object({
  ingredients: z.array(z.string().max(80)).max(40).default([]),
  dish: z.string().max(120).optional(),
  mealType: z.enum(['breakfast', 'lunch', 'dinner', 'snack', 'any']).default('any'),
  profile: profileSchema.optional(),
});

// ---------- Structured outputs from Claude ----------
// Kept to features structured outputs supports (no min/max constraints).

export const aiMealSchema = z.object({
  isFood: z.boolean().describe('false if the photo does not show food or a meal'),
  mealName: z.string(),
  summary: z.string().describe('One or two friendly sentences describing the meal'),
  items: z.array(
    z.object({
      name: z.string(),
      foodId: z.string().nullable().describe('id from the Foodio database, or null if no match'),
      grams: z.number().describe('estimated edible weight on the plate in grams'),
      plateFraction: z.number().describe('share of the plate area, 0 to 1'),
      preparation: z.string().describe('e.g. steamed, fried, stewed, raw'),
      confidence: z.number().describe('0 to 1'),
      group: foodGroup,
      estimatedPer100g: z.object({
        kcal: z.number(),
        carbs: z.number(),
        protein: z.number(),
        fat: z.number(),
        fiber: z.number(),
        sodiumMg: z.number(),
      }),
    }),
  ),
  overallConfidence: z.number().describe('0 to 1'),
  questions: z
    .array(z.object({ id: z.string(), question: z.string(), options: z.array(z.string()) }))
    .describe('Clarifying questions when unsure; empty when confident'),
  verdict: z.enum(['good', 'caution', 'avoid']),
  verdictReason: z.string(),
  suggestions: z.array(z.string()).describe('Concrete ways to improve this meal using locally available foods'),
  portionAdvice: z.array(z.string()).describe('How to re-portion this plate for this person'),
});
export type AiMeal = z.infer<typeof aiMealSchema>;

export const aiRecipesSchema = z.object({
  ideas: z.array(
    z.object({
      name: z.string(),
      description: z.string(),
      whyHealthy: z.string(),
      prepMinutes: z.number(),
      servings: z.number(),
      ingredientsUsed: z.array(z.string()),
      extraIngredientsNeeded: z.array(z.string()),
      steps: z.array(z.string()),
      plate: z.array(
        z.object({
          label: z.string(),
          group: foodGroup,
          fraction: z.number(),
          foods: z.array(z.string()),
          grams: z.number(),
        }),
      ),
      kcalPerServing: z.number(),
      suitableFor: z.string(),
    }),
  ),
  note: z.string(),
});
export type AiRecipes = z.infer<typeof aiRecipesSchema>;
