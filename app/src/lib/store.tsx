import AsyncStorage from '@react-native-async-storage/async-storage';
import type { HealthProfile, MealAnalysis, RecipeIdea } from '@shared/types';
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import type { PickedImage } from './image';

export interface HistoryEntry {
  id: string;
  thumbnail: string;
  analysis: MealAnalysis;
}

export interface CurrentScan {
  image: PickedImage;
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
}

export const EMPTY_PROFILE: HealthProfile = { name: '', goal: 'maintain', conditions: [], allergies: [] };

const PROFILE_KEY = 'foodio.profile.v1';
const HISTORY_KEY = 'foodio.history.v1';
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

  useEffect(() => {
    (async () => {
      const [p, h] = await Promise.all([load<HealthProfile>(PROFILE_KEY), load<HistoryEntry[]>(HISTORY_KEY)]);
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

  const value = useMemo(
    () => ({ ready, profile, hasProfile, saveProfile, history, addOrUpdateHistory, removeHistory, scan, setScan, recipe, setRecipe }),
    [ready, profile, hasProfile, saveProfile, history, addOrUpdateHistory, removeHistory, scan, recipe],
  );
  return <StoreContext.Provider value={value}>{children}</StoreContext.Provider>;
}

export function useStore(): Store {
  const ctx = useContext(StoreContext);
  if (!ctx) throw new Error('useStore must be used inside StoreProvider');
  return ctx;
}
