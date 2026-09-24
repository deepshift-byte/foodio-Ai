import type { FoodGroup } from '@shared/types';
import { GROUP_LABELS } from '@shared/nutrition';
import { StyleSheet, Text, View } from 'react-native';
import Svg, { Circle, G, Path, Text as SvgText } from 'react-native-svg';
import { colors, groupColors, space } from '@/lib/theme';

export interface PlateSlice {
  group: FoodGroup;
  fraction: number;
  label?: string;
  foods?: string[];
  grams?: number;
}

function sectorPath(cx: number, cy: number, r: number, start: number, end: number) {
  // Angles in radians, 0 = 12 o'clock, clockwise.
  if (end - start >= Math.PI * 2 - 1e-6) {
    return `M ${cx} ${cy - r} A ${r} ${r} 0 1 1 ${cx - 0.01} ${cy - r} Z`;
  }
  const x1 = cx + r * Math.sin(start);
  const y1 = cy - r * Math.cos(start);
  const x2 = cx + r * Math.sin(end);
  const y2 = cy - r * Math.cos(end);
  const large = end - start > Math.PI ? 1 : 0;
  return `M ${cx} ${cy} L ${x1} ${y1} A ${r} ${r} 0 ${large} 1 ${x2} ${y2} Z`;
}

/**
 * A drawing of a plate divided by food group — used both for "what's on your
 * plate" and for "how your plate should look".
 */
export function PlateView({ slices, size = 220, showLegend = true }: { slices: PlateSlice[]; size?: number; showLegend?: boolean }) {
  const visible = slices.filter((s) => s.fraction > 0.005);
  const total = visible.reduce((s, x) => s + x.fraction, 0) || 1;
  const cx = size / 2;
  const cy = size / 2;
  const rim = size / 2 - 2;
  const r = rim * 0.8;

  let angle = 0;
  const arcs = visible.map((s) => {
    const sweep = (s.fraction / total) * Math.PI * 2;
    const start = angle;
    angle += sweep;
    const mid = start + sweep / 2;
    return { ...s, start, end: angle, mid, pct: Math.round((s.fraction / total) * 100) };
  });

  return (
    <View style={styles.wrap}>
      <Svg width={size} height={size} accessibilityLabel="Plate diagram">
        <Circle cx={cx} cy={cy} r={rim} fill="#FFFFFF" stroke={colors.border} strokeWidth={2} />
        <Circle cx={cx} cy={cy} r={rim * 0.9} fill="none" stroke={colors.surfaceAlt} strokeWidth={3} />
        {arcs.length === 0 && <Circle cx={cx} cy={cy} r={r} fill={colors.surfaceAlt} />}
        <G>
          {arcs.map((a, i) => (
            <Path key={i} d={sectorPath(cx, cy, r, a.start, a.end)} fill={groupColors[a.group]} stroke="#FFFFFF" strokeWidth={2} />
          ))}
          {arcs
            .filter((a) => a.pct >= 8)
            .map((a, i) => (
              <SvgText
                key={`t${i}`}
                x={cx + r * 0.62 * Math.sin(a.mid)}
                y={cy - r * 0.62 * Math.cos(a.mid) + 5}
                fill="#FFFFFF"
                fontSize={size / 15}
                fontWeight="bold"
                textAnchor="middle"
              >
                {`${a.pct}%`}
              </SvgText>
            ))}
        </G>
      </Svg>
      {showLegend && (
        <View style={styles.legend}>
          {arcs.map((a, i) => (
            <View key={i} style={styles.legendRow}>
              <View style={[styles.swatch, { backgroundColor: groupColors[a.group] }]} />
              <View style={{ flex: 1 }}>
                <Text style={styles.legendTitle}>
                  {a.label ?? GROUP_LABELS[a.group]} · {a.pct}%{a.grams ? ` · ${Math.round(a.grams)} g` : ''}
                </Text>
                {a.foods && a.foods.length > 0 && <Text style={styles.legendFoods}>{a.foods.join(', ')}</Text>}
              </View>
            </View>
          ))}
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { alignItems: 'center', gap: space(3), width: '100%' },
  legend: { alignSelf: 'stretch', gap: space(2) },
  legendRow: { flexDirection: 'row', gap: space(2), alignItems: 'flex-start' },
  swatch: { width: 14, height: 14, borderRadius: 4, marginTop: 3 },
  legendTitle: { fontSize: 14, fontWeight: '700', color: colors.text },
  legendFoods: { fontSize: 13, color: colors.textMuted },
});
