import type { CoachAdvice, EatingContext, HealthProfile, MealAnalysis, RecipeResponse } from '@shared/types';

export const API_URL = (process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:8787').replace(/\/$/, '');

export class ApiError extends Error {}

async function post<T>(path: string, body: unknown): Promise<T> {
  let res: Response;
  try {
    res = await fetch(`${API_URL}${path}`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(body),
    });
  } catch {
    throw new ApiError(`Can't reach the Foodio server at ${API_URL}. Check your internet connection.`);
  }
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new ApiError(data.error ?? `Request failed (${res.status})`);
  return data as T;
}

export function analyzeMeal(args: {
  imageBase64?: string;
  profile?: HealthProfile;
  context?: EatingContext;
  note?: string;
  answers?: { question: string; answer: string }[];
}) {
  return post<MealAnalysis>('/api/analyze', { ...args, mediaType: 'image/jpeg' });
}

export function getRecipes(args: {
  ingredients: string[];
  dish?: string;
  mealType?: string;
  profile?: HealthProfile;
  context?: EatingContext;
}) {
  return post<RecipeResponse>('/api/recipes', args);
}

export function getCoachAdvice(args: {
  profile?: HealthProfile;
  context?: EatingContext;
  todaysMeals: { name: string; kcal: number; carbs: number; protein: number; time: string }[];
  recentDays: { date: string; kcal: number }[];
  question?: string;
}) {
  return post<CoachAdvice>('/api/coach', args);
}
