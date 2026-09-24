import { Ionicons } from '@expo/vector-icons';
import type { HealthFlag, NutrientsPer100g, Verdict } from '@shared/types';
import { StyleSheet, Text, View } from 'react-native';
import Svg, { Circle } from 'react-native-svg';
import { colors, radius, space, verdictStyle } from '@/lib/theme';
import type { IconName } from './ui';

export function ScoreRing({ score, size = 96 }: { score: number; size?: number }) {
  const stroke = 10;
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  const color = score >= 75 ? colors.primary : score >= 50 ? colors.accent : colors.danger;
  return (
    <View style={{ width: size, height: size, alignItems: 'center', justifyContent: 'center' }} accessibilityLabel={`Balance score ${score} out of 100`}>
      <Svg width={size} height={size} style={StyleSheet.absoluteFill}>
        <Circle cx={size / 2} cy={size / 2} r={r} stroke={colors.surfaceAlt} strokeWidth={stroke} fill="none" />
        <Circle
          cx={size / 2}
          cy={size / 2}
          r={r}
          stroke={color}
          strokeWidth={stroke}
          fill="none"
          strokeDasharray={`${(score / 100) * c} ${c}`}
          strokeLinecap="round"
          transform={`rotate(-90 ${size / 2} ${size / 2})`}
        />
      </Svg>
      <Text style={{ fontSize: size / 4, fontWeight: '800', color }}>{score}</Text>
      <Text style={{ fontSize: 10, color: colors.textMuted, fontWeight: '700' }}>BALANCE</Text>
    </View>
  );
}

export function VerdictBanner({ verdict, reason }: { verdict: Verdict; reason: string }) {
  const v = verdictStyle[verdict];
  return (
    <View style={[styles.verdict, { backgroundColor: v.bg, borderColor: v.color }]}>
      <Ionicons name={v.icon as IconName} size={30} color={v.color} />
      <View style={{ flex: 1, gap: 2 }}>
        <Text style={[styles.verdictTitle, { color: v.color }]}>{v.label}</Text>
        {!!reason && <Text style={styles.text}>{reason}</Text>}
      </View>
    </View>
  );
}

const BARS: { key: keyof NutrientsPer100g; label: string; unit: string; color: string; ref: number }[] = [
  { key: 'carbs', label: 'Carbs', unit: 'g', color: '#E8B53A', ref: 75 },
  { key: 'protein', label: 'Protein', unit: 'g', color: '#B5523B', ref: 30 },
  { key: 'fat', label: 'Fat', unit: 'g', color: '#8A9A3B', ref: 25 },
  { key: 'fiber', label: 'Fibre', unit: 'g', color: '#2E9E5B', ref: 10 },
  { key: 'sodiumMg', label: 'Sodium', unit: 'mg', color: '#6BA3D6', ref: 700 },
];

/** Nutrient bars scaled against typical per-meal reference amounts. */
export function NutrientBars({ totals }: { totals: NutrientsPer100g }) {
  return (
    <View style={{ gap: space(3) }}>
      {BARS.map((b) => {
        const value = totals[b.key];
        const pct = Math.min(1, value / b.ref);
        return (
          <View key={b.key} style={{ gap: 4 }}>
            <View style={styles.barHead}>
              <Text style={styles.barLabel}>{b.label}</Text>
              <Text style={styles.barValue}>
                {Math.round(value)} {b.unit}
                <Text style={styles.barRef}> / ~{b.ref} per meal</Text>
              </Text>
            </View>
            <View style={styles.barTrack}>
              <View style={[styles.barFill, { width: `${pct * 100}%`, backgroundColor: value > b.ref * 1.2 && b.key !== 'fiber' && b.key !== 'protein' ? colors.danger : b.color }]} />
            </View>
          </View>
        );
      })}
    </View>
  );
}

const FLAG_STYLE = {
  danger: { icon: 'close-circle', color: colors.danger, bg: colors.dangerSoft },
  warning: { icon: 'alert-circle', color: colors.warning, bg: colors.warningSoft },
  info: { icon: 'information-circle', color: colors.info, bg: colors.infoSoft },
} as const;

export function FlagList({ flags }: { flags: HealthFlag[] }) {
  const order = { danger: 0, warning: 1, info: 2 };
  return (
    <View style={{ gap: space(2) }}>
      {[...flags]
        .sort((a, b) => order[a.severity] - order[b.severity])
        .map((f, i) => {
          const s = FLAG_STYLE[f.severity];
          return (
            <View key={i} style={[styles.flag, { backgroundColor: s.bg }]}>
              <Ionicons name={s.icon} size={18} color={s.color} />
              <Text style={[styles.text, { flex: 1 }]}>
                {f.condition !== 'general' && <Text style={{ fontWeight: '700', color: s.color }}>{f.condition.replace('-', ' ')}: </Text>}
                {f.message}
              </Text>
            </View>
          );
        })}
    </View>
  );
}

export function BulletList({ items, icon = 'leaf', color = colors.primary }: { items: string[]; icon?: IconName; color?: string }) {
  return (
    <View style={{ gap: space(2) }}>
      {items.map((t, i) => (
        <View key={i} style={{ flexDirection: 'row', gap: space(2), alignItems: 'flex-start' }}>
          <Ionicons name={icon} size={16} color={color} style={{ marginTop: 3 }} />
          <Text style={[styles.text, { flex: 1 }]}>{t}</Text>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  text: { fontSize: 15, lineHeight: 22, color: colors.text },
  verdict: {
    flexDirection: 'row',
    gap: space(3),
    padding: space(4),
    borderRadius: radius.lg,
    borderWidth: 1.5,
    alignItems: 'center',
  },
  verdictTitle: { fontSize: 18, fontWeight: '800' },
  barHead: { flexDirection: 'row', justifyContent: 'space-between' },
  barLabel: { fontSize: 14, fontWeight: '700', color: colors.text },
  barValue: { fontSize: 14, fontWeight: '700', color: colors.text },
  barRef: { fontSize: 12, fontWeight: '500', color: colors.textMuted },
  barTrack: { height: 10, borderRadius: 5, backgroundColor: colors.surfaceAlt, overflow: 'hidden' },
  barFill: { height: '100%', borderRadius: 5 },
  flag: { flexDirection: 'row', gap: space(2), padding: space(3), borderRadius: radius.md, alignItems: 'flex-start' },
});
