import type { WeighIn } from '@shared/types';
import { useState } from 'react';
import { Platform, StyleSheet, Text, View, type LayoutChangeEvent } from 'react-native';
import Svg, { Circle, G, Line, Path, Rect, Text as SvgText } from 'react-native-svg';
import { colors, space } from '@/lib/theme';

const AXIS = '#E7DFD2';
const MUTED = '#8C958F';
const PLAN = '#A8B0AA';
// SVG text defaults to a serif face on web; match the app's sans-serif UI.
export const SVG_FONT = Platform.OS === 'web' ? 'system-ui, -apple-system, Segoe UI, Roboto, sans-serif' : undefined;

function useWidth(initial = 320) {
  const [width, setWidth] = useState(initial);
  const onLayout = (e: LayoutChangeEvent) => setWidth(Math.max(200, e.nativeEvent.layout.width));
  return { width, onLayout };
}

function dayNumber(iso: string) {
  const [y, m, d] = iso.split('-').map(Number);
  return Date.UTC(y, m - 1, d) / 86_400_000;
}

function shortDate(iso: string) {
  const [, m, d] = iso.split('-').map(Number);
  return `${d} ${['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'][m - 1]}`;
}

function niceTicks(min: number, max: number, count = 4) {
  const span = max - min || 1;
  const step = [0.5, 1, 2, 5, 10, 20, 50].find((s) => span / s <= count) ?? 100;
  const ticks: number[] = [];
  // Start at or below min and keep going until a tick covers max.
  for (let v = Math.floor(min / step) * step; ticks.length === 0 || ticks[ticks.length - 1] < max - 1e-9; v += step) {
    ticks.push(Math.round(v * 10) / 10);
  }
  return ticks;
}

function Legend({ items }: { items: { color: string; label: string; dashed?: boolean }[] }) {
  return (
    <View style={styles.legend}>
      {items.map((i) => (
        <View key={i.label} style={styles.legendItem}>
          <View style={[styles.legendKey, { backgroundColor: i.color }, i.dashed && { height: 2 }]} />
          <Text style={styles.legendText}>{i.label}</Text>
        </View>
      ))}
    </View>
  );
}

/**
 * Weigh-ins over time, with the straight-line plan from start weight to the
 * target. Tap a point to read its value.
 */
export function WeightChart({
  weighIns,
  plan,
}: {
  weighIns: WeighIn[];
  plan?: { startDate: string; startKg: number; targetDate: string; targetKg: number };
}) {
  const { width, onLayout } = useWidth();
  const [selected, setSelected] = useState<number | null>(null);
  const height = 200;
  const pad = { l: 36, r: 16, t: 16, b: 26 };

  const points = [...weighIns].sort((a, b) => a.date.localeCompare(b.date));
  if (points.length === 0 && !plan) return null;

  const days = [...points.map((p) => dayNumber(p.date)), ...(plan ? [dayNumber(plan.startDate), dayNumber(plan.targetDate)] : [])];
  const kgs = [...points.map((p) => p.kg), ...(plan ? [plan.startKg, plan.targetKg] : [])];
  const x0 = Math.min(...days);
  const x1 = Math.max(...days, x0 + 1);
  const ticks = niceTicks(Math.min(...kgs) - 0.5, Math.max(...kgs) + 0.5);
  const y0 = ticks[0];
  const y1 = ticks[ticks.length - 1];
  const x = (iso: string) => pad.l + ((dayNumber(iso) - x0) / (x1 - x0)) * (width - pad.l - pad.r);
  const y = (kg: number) => pad.t + (1 - (kg - y0) / (y1 - y0 || 1)) * (height - pad.t - pad.b);

  const line = points.map((p, i) => `${i ? 'L' : 'M'}${x(p.date).toFixed(1)} ${y(p.kg).toFixed(1)}`).join(' ');
  const last = points.at(-1);
  const sel = selected !== null ? points[selected] : null;

  return (
    <View onLayout={onLayout} style={{ gap: space(2) }}>
      {plan && (
        <Legend
          items={[
            { color: colors.primary, label: 'Your weight' },
            { color: PLAN, label: `Plan to ${plan.targetKg} kg`, dashed: true },
          ]}
        />
      )}
      <Svg width={width} height={height} accessibilityLabel="Weight over time">
        {ticks.map((t) => (
          <G key={t}>
            <Line x1={pad.l} x2={width - pad.r} y1={y(t)} y2={y(t)} stroke={AXIS} strokeWidth={1} />
            <SvgText fontFamily={SVG_FONT} x={pad.l - 6} y={y(t) + 4} fontSize={11} fill={MUTED} textAnchor="end">
              {String(t)}
            </SvgText>
          </G>
        ))}
        {plan && (
          <Line
            x1={x(plan.startDate)}
            y1={y(plan.startKg)}
            x2={x(plan.targetDate)}
            y2={y(plan.targetKg)}
            stroke={PLAN}
            strokeWidth={2}
            strokeDasharray="6 5"
            strokeLinecap="round"
          />
        )}
        {points.length > 1 && <Path d={line} stroke={colors.primary} strokeWidth={2} fill="none" strokeLinejoin="round" strokeLinecap="round" />}
        {points.map((p, i) => (
          <G key={p.date}>
            <Circle cx={x(p.date)} cy={y(p.kg)} r={i === selected ? 6 : 4.5} fill={colors.primary} stroke={colors.surface} strokeWidth={2} />
            {/* Larger invisible hit target for tapping */}
            <Circle cx={x(p.date)} cy={y(p.kg)} r={16} fill="transparent" onPress={() => setSelected(i === selected ? null : i)} />
          </G>
        ))}
        {last && selected === null && (
          // Direct label beside the latest point (left of it if there's no room on the right).
          <SvgText
            fontFamily={SVG_FONT}
            x={x(last.date) + 60 < width ? x(last.date) + 10 : x(last.date) - 10}
            y={y(last.kg) + 4}
            fontSize={12}
            fontWeight="bold"
            fill={colors.text}
            textAnchor={x(last.date) + 60 < width ? 'start' : 'end'}
          >
            {`${last.kg} kg`}
          </SvgText>
        )}
        {sel && (
          <G>
            <Rect x={Math.min(Math.max(x(sel.date) - 48, 0), width - 96)} y={Math.max(y(sel.kg) - 44, 0)} width={96} height={32} rx={6} fill={colors.text} />
            <SvgText fontFamily={SVG_FONT} x={Math.min(Math.max(x(sel.date), 48), width - 48)} y={Math.max(y(sel.kg) - 24, 20)} fontSize={12} fill="#FFFFFF" textAnchor="middle">
              {`${sel.kg} kg · ${shortDate(sel.date)}`}
            </SvgText>
          </G>
        )}
        {[points[0]?.date ?? plan?.startDate, plan?.targetDate ?? last?.date]
          .filter((d): d is string => Boolean(d))
          .map((d, i) => (
            <SvgText fontFamily={SVG_FONT} key={`${d}${i}`} x={x(d)} y={height - 6} fontSize={11} fill={MUTED} textAnchor={i === 0 ? 'start' : 'end'}>
              {shortDate(d)}
            </SvgText>
          ))}
      </Svg>
    </View>
  );
}

/** Daily calories for recent days against the daily budget. */
export function CalorieBars({ days, budget }: { days: { date: string; kcal: number }[]; budget: number }) {
  const { width, onLayout } = useWidth();
  const [selected, setSelected] = useState<number | null>(null);
  const height = 170;
  const pad = { l: 40, r: 8, t: 18, b: 22 };
  const max = Math.max(budget * 1.2, ...days.map((d) => d.kcal));
  const ticks = niceTicks(0, max, 3).filter((t) => t % 500 === 0 || t === 0);
  const top = Math.max(max, ticks.at(-1) ?? max);
  const y = (v: number) => pad.t + (1 - v / top) * (height - pad.t - pad.b);
  const slot = (width - pad.l - pad.r) / Math.max(days.length, 1);
  const barW = Math.min(24, slot * 0.6);
  const anyOver = days.some((d) => d.kcal > budget * 1.05);

  return (
    <View onLayout={onLayout} style={{ gap: space(2) }}>
      <Legend
        items={[
          { color: colors.primary, label: 'Within budget' },
          ...(anyOver ? [{ color: colors.accent, label: 'Over budget' }] : []),
          { color: colors.text, label: `Budget ${budget} kcal`, dashed: true },
        ]}
      />
      <Svg width={width} height={height} accessibilityLabel="Calories per day">
        {ticks.map((t) => (
          <G key={t}>
            <Line x1={pad.l} x2={width - pad.r} y1={y(t)} y2={y(t)} stroke={AXIS} strokeWidth={1} />
            <SvgText fontFamily={SVG_FONT} x={pad.l - 6} y={y(t) + 4} fontSize={11} fill={MUTED} textAnchor="end">
              {t.toLocaleString()}
            </SvgText>
          </G>
        ))}
        {days.map((d, i) => {
          const cx = pad.l + slot * i + slot / 2;
          const h = Math.max(0, y(0) - y(d.kcal));
          const r = Math.min(4, h / 2);
          const x0 = cx - barW / 2;
          const yTop = y(d.kcal);
          // Rounded data end, square at the baseline.
          const path = `M${x0} ${y(0)} L${x0} ${yTop + r} Q${x0} ${yTop} ${x0 + r} ${yTop} L${x0 + barW - r} ${yTop} Q${x0 + barW} ${yTop} ${x0 + barW} ${yTop + r} L${x0 + barW} ${y(0)} Z`;
          const day = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'][new Date(`${d.date}T12:00:00`).getDay()];
          return (
            <G key={d.date}>
              {h > 0 && <Path d={path} fill={d.kcal > budget * 1.05 ? colors.accent : colors.primary} />}
              <Rect x={cx - slot / 2} y={pad.t} width={slot} height={height - pad.t - pad.b} fill="transparent" onPress={() => setSelected(i === selected ? null : i)} />
              <SvgText fontFamily={SVG_FONT} x={cx} y={height - 6} fontSize={11} fill={MUTED} textAnchor="middle">
                {day}
              </SvgText>
              {selected === i && (
                <SvgText fontFamily={SVG_FONT} x={cx} y={Math.max(yTop - 6, 12)} fontSize={12} fontWeight="bold" fill={colors.text} textAnchor="middle">
                  {Math.round(d.kcal).toLocaleString()}
                </SvgText>
              )}
            </G>
          );
        })}
        <Line x1={pad.l} x2={width - pad.r} y1={y(budget)} y2={y(budget)} stroke={colors.text} strokeWidth={1.5} strokeDasharray="5 4" />
      </Svg>
      <Text style={styles.hint}>Tap a day to see its total.</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  legend: { flexDirection: 'row', flexWrap: 'wrap', gap: space(4) },
  legendItem: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  legendKey: { width: 16, height: 4, borderRadius: 2 },
  legendText: { fontSize: 12, color: colors.textMuted, fontWeight: '600' },
  hint: { fontSize: 11, color: MUTED },
});
