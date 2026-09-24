import type { FoodGroup, Verdict } from '@shared/types';

export const colors = {
  bg: '#FBF7F0',
  surface: '#FFFFFF',
  surfaceAlt: '#F3EDE3',
  border: '#E7DFD2',
  text: '#1B2A21',
  textMuted: '#5E6B62',
  primary: '#1F7A4D',
  primaryDark: '#155C39',
  primarySoft: '#E3F2EA',
  accent: '#F28C28',
  accentSoft: '#FDEBD8',
  danger: '#C2410C',
  dangerSoft: '#FDE7DC',
  warning: '#B7791F',
  warningSoft: '#FDF3D8',
  info: '#2563EB',
  infoSoft: '#E3ECFD',
  white: '#FFFFFF',
};

export const groupColors: Record<FoodGroup, string> = {
  vegetable: '#2E9E5B',
  protein: '#B5523B',
  starch: '#E8B53A',
  fruit: '#E0638C',
  fat: '#8A9A3B',
  dairy: '#6BA3D6',
  sugary: '#9B6BD6',
};

export const verdictStyle: Record<Verdict, { color: string; bg: string; label: string; icon: string }> = {
  good: { color: colors.primary, bg: colors.primarySoft, label: 'Good for you', icon: 'checkmark-circle' },
  caution: { color: colors.warning, bg: colors.warningSoft, label: 'Eat with care', icon: 'alert-circle' },
  avoid: { color: colors.danger, bg: colors.dangerSoft, label: 'Not recommended', icon: 'close-circle' },
};

export const radius = { sm: 8, md: 14, lg: 20, pill: 999 };
export const space = (n: number) => n * 4;

export const shadow = {
  shadowColor: '#3B2F1E',
  shadowOpacity: 0.08,
  shadowRadius: 12,
  shadowOffset: { width: 0, height: 4 },
  elevation: 2,
};
