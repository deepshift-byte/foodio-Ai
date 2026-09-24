import AsyncStorage from '@react-native-async-storage/async-storage';
import type { EatingContext, HealthProfile, MealAnalysis, RecipeIdea, WeighIn } from '@shared/types';
import { latestWeight, todayISO } from '@shared/weight';
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import type { PickedImage } from './image';

export interface HistoryEntry {
  id: string;
  thumbnail?: string; // absent for meals checked from a text description
  analysis: MealAnalysis;
}

export interface CurrentScan {
  image?: PickedImage; // absent when the person only described the meal
  note?: string;
  analysis?: MealAnalysis;
  answers: { question: string; answer: string }[];
  historyId?: string;
}

interface Store {
  ready: boolean;
  profile: HealthProfile;
  hasProfile: boolean;
  saveProfile: (p: HealthProfile) => void;
  history: HistoryEntry[];
  addOrUpdateHistory: (entry: HistoryEntry) => void;
  removeHistory: (id: string) => void;
  scan: CurrentScan | null;
  setScan: (s: CurrentScan | null) => void;
  recipe: RecipeIdea | null;
  setRecipe: (r: RecipeIdea | null) => void;
  weighIns: WeighIn[];
  addWeighIn: (w: WeighIn) => void;
  removeWeighIn: (date: string) => void;
  currentWeightKg?: number;
  /** Today's intake etc., sent with every check so advice fits the whole day. */
  eatingContext: (excludeHistoryId?: string) => EatingContext;
}

export function isToday(iso: string) {
  return todayISO(new Date(iso)) === todayISO();
}

function localTime() {
  const d = new Date();
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

export const EMPTY_PROFILE: HealthProfile = { name: '', goal: 'maintain', conditions: [], allergies: [] };

const PROFILE_KEY = 'foodio.profile.v1';
const HISTORY_KEY = 'foodio.history.v1';
const WEIGHT_KEY = 'foodio.weighins.v1';
const MAX_HISTORY = 30;

const StoreContext = createContext<Store | null>(null);

async function load<T>(key: string): Promise<T | null> {
  try {
    const raw = await AsyncStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : null;
  } catch {
    return null;
  }
}

function persist(key: string, value: unknown) {
  AsyncStorage.setItem(key, JSON.stringify(value)).catch(() => {
    // Storage full or unavailable (e.g. private browsing) — keep working in memory.
  });
}

export function StoreProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false);
  const [profile, setProfile] = useState<HealthProfile>(EMPTY_PROFILE);
  const [hasProfile, setHasProfile] = useState(false);
  const [history, setHistory] = useState<HistoryEntry[]>([]);
  const [scan, setScan] = useState<CurrentScan | null>(null);
  const [recipe, setRecipe] = useState<RecipeIdea | null>(null);
  const [weighIns, setWeighIns] = useState<WeighIn[]>([]);

  useEffect(() => {
    (async () => {
      const [p, h, w] = await Promise.all([
        load<HealthProfile>(PROFILE_KEY),
        load<HistoryEntry[]>(HISTORY_KEY),
        load<WeighIn[]>(WEIGHT_KEY),
      ]);
      if (w) setWeighIns(w);
      if (p) {
        setProfile({ ...EMPTY_PROFILE, ...p });
        setHasProfile(true);
      }
      if (h) setHistory(h);
      setReady(true);
    })();
  }, []);

  const saveProfile = useCallback((p: HealthProfile) => {
    setProfile(p);
    setHasProfile(true);
    persist(PROFILE_KEY, p);
  }, []);

  const addOrUpdateHistory = useCallback((entry: HistoryEntry) => {
    setHistory((prev) => {
      const next = [entry, ...prev.filter((e) => e.id !== entry.id)].slice(0, MAX_HISTORY);
      persist(HISTORY_KEY, next);
      return next;
    });
  }, []);

  const removeHistory = useCallback((id: string) => {
    setHistory((prev) => {
      const next = prev.filter((e) => e.id !== id);
      persist(HISTORY_KEY, next);
      return next;
    });
  }, []);

  const addWeighIn = useCallback((w: WeighIn) => {
    setWeighIns((prev) => {
      const next = [...prev.filter((x) => x.date !== w.date), w].sort((a, b) => a.date.localeCompare(b.date));
      persist(WEIGHT_KEY, next);
      return next;
    });
  }, []);

  const removeWeighIn = useCallback((date: string) => {
    setWeighIns((prev) => {
      const next = prev.filter((x) => x.date !== date);
      persist(WEIGHT_KEY, next);
      return next;
    });
  }, []);

  const currentWeightKg = latestWeight(weighIns, profile);

  const eatingContext = useCallback(
    (excludeHistoryId?: string): EatingContext => {
      const today = history.filter((h) => h.id !== excludeHistoryId && isToday(h.analysis.analyzedAt));
      return {
        localTime: localTime(),
        todayKcal: Math.round(today.reduce((s, h) => s + h.analysis.totals.kcal, 0)),
        todayCarbs: Math.round(today.reduce((s, h) => s + h.analysis.totals.carbs, 0)),
        mealsToday: today.length,
        currentWeightKg,
      };
    },
    [history, currentWeightKg],
  );

  const value = useMemo(
    () => ({
      ready, profile, hasProfile, saveProfile, history, addOrUpdateHistory, removeHistory, scan, setScan, recipe, setRecipe,
      weighIns, addWeighIn, removeWeighIn, currentWeightKg, eatingContext,
    }),
    [ready, profile, hasProfile, saveProfile, history, addOrUpdateHistory, removeHistory, scan, recipe, weighIns, addWeighIn, removeWeighIn, currentWeightKg, eatingContext],
  );
  return <StoreContext.Provider value={value}>{children}</StoreContext.Provider>;
}

export function useStore(): Store {
  const ctx = useContext(StoreContext);
  if (!ctx) throw new Error('useStore must be used inside StoreProvider');
  return ctx;
}
