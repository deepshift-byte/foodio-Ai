import type { HealthProfile, WeighIn } from './types';

// Weight-goal planning. Guidance used:
// - Safe loss is about 0.5–1 kg a week (and no more than ~1% of body weight a week).
// - ~7,700 kcal of energy deficit ≈ 1 kg of body fat.
// - Daily intake should not go below ~1,200 kcal (women) / 1,500 kcal (men)
//   without medical supervision.

export const KCAL_PER_KG = 7700;
const DAY_MS = 86_400_000;

export function todayISO(d = new Date()): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function parseISO(s: string): Date {
  const [y, m, d] = s.split('-').map(Number);
  return new Date(y, (m ?? 1) - 1, d ?? 1);
}

export function daysBetween(a: string, b: string): number {
  return Math.round((parseISO(b).getTime() - parseISO(a).getTime()) / DAY_MS);
}

export function addDays(iso: string, days: number): string {
  return todayISO(new Date(parseISO(iso).getTime() + days * DAY_MS));
}

export function minimumKcal(profile?: HealthProfile): number {
  return profile?.sex === 'male' ? 1500 : profile?.sex === 'female' ? 1200 : 1350;
}

/** Energy needed to keep the current weight (Mifflin–St Jeor × activity). */
export function maintenanceKcal(profile?: HealthProfile, weightKg?: number): number {
  const w = weightKg ?? profile?.weightKg;
  const factor = profile?.activity === 'high' ? 1.725 : profile?.activity === 'moderate' ? 1.55 : 1.375;
  if (w && profile?.heightCm && profile.age) {
    const s = profile.sex === 'male' ? 5 : profile.sex === 'female' ? -161 : -78;
    return Math.round(((10 * w + 6.25 * profile.heightCm - 5 * profile.age + s) * factor) / 10) * 10;
  }
  if (w) return Math.round((w * 22 * factor) / 10) * 10; // rough estimate without age/height
  return 2000;
}

export function latestWeight(log: WeighIn[], profile?: HealthProfile): number | undefined {
  const sorted = [...log].sort((a, b) => a.date.localeCompare(b.date));
  return sorted.at(-1)?.kg ?? profile?.weightGoal?.startWeightKg ?? profile?.weightKg;
}

export type PlanStatus = 'on-track' | 'too-fast' | 'reached' | 'past-date' | 'gain';

export interface WeightPlan {
  currentKg: number;
  targetKg: number;
  kgToGo: number; // positive = still to lose
  daysLeft: number;
  requiredWeeklyKg: number;
  safeWeeklyKg: number;
  status: PlanStatus;
  /** Earliest target date reachable at a safe pace. */
  safeTargetDate: string;
  maintenanceKcal: number;
  dailyKcal: number; // recommended intake
  deficitKcal: number;
  /** Where the person would be today on a straight line from start to target. */
  expectedTodayKg: number;
  progressPct: number; // 0..100 of the way from start to target
  warnings: string[];
}

export function weightPlan(profile: HealthProfile, currentKg: number, today = todayISO()): WeightPlan | null {
  const goal = profile.weightGoal;
  if (!goal) return null;

  const targetKg = goal.targetWeightKg;
  const kgToGo = Math.round((currentKg - targetKg) * 10) / 10;
  const daysLeft = Math.max(0, daysBetween(today, goal.targetDate));
  const safeWeeklyKg = Math.round(Math.min(1, Math.max(0.5, currentKg * 0.01)) * 10) / 10;
  const requiredWeeklyKg = daysLeft > 0 ? Math.round(((kgToGo / daysLeft) * 7) * 100) / 100 : kgToGo;
  const maintenance = maintenanceKcal(profile, currentKg);
  const floor = minimumKcal(profile);
  const warnings: string[] = [];

  const totalDays = Math.max(1, daysBetween(goal.startDate, goal.targetDate));
  const elapsed = Math.min(totalDays, Math.max(0, daysBetween(goal.startDate, today)));
  const expectedTodayKg = Math.round((goal.startWeightKg + ((targetKg - goal.startWeightKg) * elapsed) / totalDays) * 10) / 10;
  const span = goal.startWeightKg - targetKg;
  const progressPct = span !== 0 ? Math.max(0, Math.min(100, Math.round(((goal.startWeightKg - currentKg) / span) * 100))) : 100;
  const safeTargetDate = addDays(today, Math.ceil((Math.max(0, kgToGo) / safeWeeklyKg) * 7));

  let status: PlanStatus;
  let deficit = 0;
  if (targetKg > goal.startWeightKg) {
    status = 'gain';
    deficit = -400; // a modest surplus for healthy weight gain
  } else if (kgToGo <= 0) {
    status = 'reached';
  } else if (daysLeft === 0) {
    status = 'past-date';
    deficit = (safeWeeklyKg * KCAL_PER_KG) / 7;
    warnings.push('Your target date has passed. Set a new date — Foodio will keep you at a safe pace meanwhile.');
  } else {
    status = requiredWeeklyKg > safeWeeklyKg ? 'too-fast' : 'on-track';
    deficit = (Math.min(requiredWeeklyKg, safeWeeklyKg) * KCAL_PER_KG) / 7;
    if (status === 'too-fast') {
      warnings.push(
        `Losing ${kgToGo} kg by then means about ${requiredWeeklyKg} kg a week — faster than the safe ${safeWeeklyKg} kg a week. ` +
          `Foodio plans for a safe pace, which reaches your goal around ${safeTargetDate}.`,
      );
    }
  }

  const conditions = new Set(profile.conditions);
  if (deficit > 0 && conditions.has('pregnancy')) {
    deficit = 0;
    warnings.push('Weight loss is not recommended during pregnancy. Foodio will focus on healthy, nourishing meals instead.');
  }
  if (deficit > 0 && conditions.has('cancer') && profile.cancerCare?.phase === 'in-treatment') {
    deficit = 0;
    warnings.push('During cancer treatment, keeping your strength matters more than losing weight. Please agree any weight-loss plan with your cancer care team.');
  }
  if (deficit > 0 && (conditions.has('diabetes') || conditions.has('kidney-disease'))) {
    warnings.push('With your condition, check this plan with your doctor — medicines may need adjusting as you lose weight.');
  }

  deficit = Math.min(deficit, 1000);
  let dailyKcal = Math.round((maintenance - deficit) / 10) * 10;
  if (dailyKcal < floor) {
    dailyKcal = floor;
    if (deficit > 0) warnings.push(`Foodio won't plan below ${floor} kcal a day. Add walking or other activity to go faster safely.`);
  }

  return {
    currentKg,
    targetKg,
    kgToGo,
    daysLeft,
    requiredWeeklyKg,
    safeWeeklyKg,
    status,
    safeTargetDate,
    maintenanceKcal: maintenance,
    dailyKcal,
    deficitKcal: Math.round(maintenance - dailyKcal),
    expectedTodayKg,
    progressPct,
    warnings,
  };
}
