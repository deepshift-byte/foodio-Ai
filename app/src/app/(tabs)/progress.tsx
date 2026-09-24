import { Ionicons } from '@expo/vector-icons';
import { router, useLocalSearchParams } from 'expo-router';
import { dailyKcal } from '@shared/nutrition';
import type { CoachAdvice } from '@shared/types';
import { addDays, todayISO, weightPlan } from '@shared/weight';
import { useEffect, useMemo, useState } from 'react';
import { Image, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { AnalysisView } from '@/components/AnalysisView';
import { CalorieBars, WeightChart } from '@/components/Charts';
import { BudgetBar, BulletList } from '@/components/MealWidgets';
import { Body, Button, Card, Chip, Columns, ErrorBox, H1, SectionTitle, Screen, Stat } from '@/components/ui';
import { getCoachAdvice } from '@/lib/api';
import { isToday, useStore, type HistoryEntry } from '@/lib/store';
import { colors, radius, space, verdictStyle } from '@/lib/theme';

const num = (s: string) => {
  const n = Number(s.replace(',', '.'));
  return s.trim() && Number.isFinite(n) && n > 0 ? n : undefined;
};

const DURATIONS = [
  { label: '1 month', days: 30 },
  { label: '2 months', days: 61 },
  { label: '3 months', days: 91 },
  { label: '6 months', days: 182 },
];

export default function ProgressScreen() {
  const { profile, hasProfile, history, removeHistory, currentWeightKg } = useStore();
  const params = useLocalSearchParams<{ open?: string }>();
  const [openId, setOpenId] = useState<string | null>(null);

  useEffect(() => {
    if (params.open) setOpenId(params.open);
  }, [params.open]);

  const today = todayISO();
  const budget = dailyKcal(hasProfile ? profile : undefined, currentWeightKg);
  const todays = history.filter((h) => isToday(h.analysis.analyzedAt));
  const todayKcal = todays.reduce((s, h) => s + h.analysis.totals.kcal, 0);

  const last7 = useMemo(() => {
    return Array.from({ length: 7 }, (_, i) => {
      const date = addDays(today, i - 6);
      const kcal = history.filter((h) => todayISO(new Date(h.analysis.analyzedAt)) === date).reduce((s, h) => s + h.analysis.totals.kcal, 0);
      return { date, kcal };
    });
  }, [history, today]);

  const open = history.find((h) => h.id === openId);
  if (open) {
    return (
      <Screen>
        <Button
          title="Back to progress"
          icon="arrow-back"
          variant="ghost"
          onPress={() => {
            setOpenId(null);
            router.setParams({ open: '' });
          }}
          style={{ alignSelf: 'flex-start' }}
        />
        <AnalysisView analysis={open.analysis} imageUri={open.thumbnail} />
      </Screen>
    );
  }

  const left = (
    <>
      <GoalCard />
      <WeightLogCard />
    </>
  );

  const right = (
    <>
      <Card>
        <SectionTitle icon="today">Today</SectionTitle>
        <BudgetBar consumed={todayKcal} daily={budget} />
        {todays.length === 0 ? (
          <Body muted>No meals checked yet today.</Body>
        ) : (
          todays.map((h) => (
            <View key={h.id} style={styles.todayRow}>
              <Text style={styles.time}>{new Date(h.analysis.analyzedAt).toTimeString().slice(0, 5)}</Text>
              <Text style={styles.todayName} numberOfLines={1}>
                {h.analysis.mealName}
              </Text>
              <Text style={styles.todayKcal}>{h.analysis.totals.kcal} kcal</Text>
            </View>
          ))
        )}
      </Card>
      <CoachCard todays={todays} last7={last7} />
      <Card>
        <SectionTitle icon="bar-chart">Last 7 days</SectionTitle>
        <CalorieBars days={last7} budget={budget} />
      </Card>
    </>
  );

  return (
    <Screen>
      <H1>Progress</H1>
      <Body muted>Track your weight goal, what you eat each day, and get advice on what to eat next.</Body>
      <Columns left={left} right={right} />

      <SectionTitle icon="time">Meal history</SectionTitle>
      {history.length === 0 && (
        <Card>
          <Body muted>No meals yet. Scan your first plate to start tracking.</Body>
          <Button title="Scan a meal" icon="camera" onPress={() => router.push('/')} />
        </Card>
      )}
      {history.map((h) => {
        const v = verdictStyle[h.analysis.verdict];
        return (
          <Pressable key={h.id} onPress={() => setOpenId(h.id)}>
            <Card style={styles.row}>
              {h.thumbnail ? (
                <Image source={{ uri: h.thumbnail }} style={styles.thumb} />
              ) : (
                <View style={[styles.thumb, styles.thumbText]}>
                  <Ionicons name="chatbubble-ellipses" size={24} color={colors.primary} />
                </View>
              )}
              <View style={{ flex: 1, gap: 2 }}>
                <Text style={styles.name} numberOfLines={1}>
                  {h.analysis.mealName}
                </Text>
                <Body muted style={{ fontSize: 13 }}>
                  {new Date(h.analysis.analyzedAt).toLocaleString()} · {h.analysis.totals.kcal} kcal
                </Body>
                <Text style={[styles.badge, { color: v.color, backgroundColor: v.bg }]}>{v.label}</Text>
              </View>
              <Pressable onPress={() => removeHistory(h.id)} hitSlop={12} accessibilityLabel="Delete meal">
                <Ionicons name="trash-outline" size={20} color={colors.textMuted} />
              </Pressable>
            </Card>
          </Pressable>
        );
      })}
    </Screen>
  );
}

function GoalCard() {
  const { profile, saveProfile, addWeighIn, currentWeightKg } = useStore();
  const today = todayISO();
  const plan = profile.weightGoal && currentWeightKg ? weightPlan(profile, currentWeightKg, today) : null;
  const [editing, setEditing] = useState(!profile.weightGoal);
  const [current, setCurrent] = useState(currentWeightKg?.toString() ?? '');
  const [target, setTarget] = useState(profile.weightGoal?.targetWeightKg.toString() ?? '');
  const [date, setDate] = useState(profile.weightGoal?.targetDate ?? addDays(today, 30));
  const [error, setError] = useState<string | null>(null);

  function save() {
    const c = num(current);
    const t = num(target);
    if (!c || !t) return setError('Enter your current and target weight in kg.');
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || date <= today) return setError('Choose a target date in the future.');
    setError(null);
    saveProfile({
      ...profile,
      goal: t < c ? 'lose' : t > c ? 'gain' : 'maintain',
      weightKg: c,
      weightGoal: { startWeightKg: c, targetWeightKg: t, startDate: today, targetDate: date },
    });
    addWeighIn({ date: today, kg: c });
    setEditing(false);
  }

  if (editing || !plan) {
    const preview = (() => {
      const c = num(current);
      const t = num(target);
      if (!c || !t || date <= today) return null;
      return weightPlan({ ...profile, weightGoal: { startWeightKg: c, targetWeightKg: t, startDate: today, targetDate: date } }, c, today);
    })();
    return (
      <Card>
        <SectionTitle icon="flag">Weight goal</SectionTitle>
        <Body muted>Set a target and Foodio will plan a safe daily calorie budget and check every meal against it.</Body>
        <View style={styles.inputs}>
          <Field label="Current weight (kg)">
            <TextInput value={current} onChangeText={setCurrent} keyboardType="decimal-pad" placeholder="80" placeholderTextColor={colors.textMuted} style={styles.input} />
          </Field>
          <Field label="Target weight (kg)">
            <TextInput value={target} onChangeText={setTarget} keyboardType="decimal-pad" placeholder="75" placeholderTextColor={colors.textMuted} style={styles.input} />
          </Field>
        </View>
        <Field label="Reach it by">
          <View style={styles.chips}>
            {DURATIONS.map((d) => {
              const iso = addDays(today, d.days);
              return <Chip key={d.label} label={d.label} selected={date === iso} onPress={() => setDate(iso)} />;
            })}
          </View>
          <TextInput value={date} onChangeText={setDate} placeholder="YYYY-MM-DD" placeholderTextColor={colors.textMuted} style={styles.input} />
        </Field>
        {preview && (
          <View style={[styles.preview, preview.status === 'too-fast' && { backgroundColor: colors.warningSoft }]}>
            <Text style={styles.previewText}>
              {preview.status === 'gain'
                ? `Aim for about ${preview.dailyKcal} kcal a day to gain weight steadily.`
                : `${preview.kgToGo} kg in ${preview.daysLeft} days = ${preview.requiredWeeklyKg} kg a week. Daily budget: ${preview.dailyKcal} kcal.`}
            </Text>
            {preview.warnings.map((w) => (
              <Text key={w} style={styles.previewWarn}>
                ⚠ {w}
              </Text>
            ))}
          </View>
        )}
        {error && <ErrorBox message={error} />}
        <View style={styles.inputs}>
          <Button title="Save goal" icon="checkmark" onPress={save} style={{ flex: 1 }} />
          {profile.weightGoal && <Button title="Cancel" variant="outline" onPress={() => setEditing(false)} style={{ flex: 1 }} />}
        </View>
      </Card>
    );
  }

  return (
    <Card>
      <SectionTitle icon="flag">Weight goal</SectionTitle>
      <View style={{ flexDirection: 'row' }}>
        <Stat label="Now" value={plan.currentKg} unit="kg" />
        <Stat label="Target" value={plan.targetKg} unit="kg" color={colors.primary} />
        <Stat label={plan.kgToGo >= 0 ? 'To go' : 'To gain'} value={Math.abs(plan.kgToGo)} unit="kg" color={colors.accent} />
      </View>
      <View style={styles.progressTrack}>
        <View style={[styles.progressFill, { width: `${plan.progressPct}%` }]} />
      </View>
      <Body muted style={{ fontSize: 13 }}>
        {plan.status === 'reached'
          ? '🎉 You reached your target! Foodio will now help you stay there.'
          : `${plan.progressPct}% of the way · ${plan.daysLeft} days left · target ${profile.weightGoal!.targetDate}`}
      </Body>
      <View style={{ flexDirection: 'row' }}>
        <Stat label="Daily budget" value={plan.dailyKcal} unit="kcal" color={colors.accent} />
        <Stat label="Safe pace" value={plan.safeWeeklyKg} unit="kg/wk" />
        <Stat label="On plan today" value={plan.expectedTodayKg} unit="kg" />
      </View>
      {plan.warnings.map((w) => (
        <View key={w} style={styles.warn}>
          <Ionicons name="alert-circle" size={16} color={colors.warning} />
          <Text style={styles.warnText}>{w}</Text>
        </View>
      ))}
      <View style={styles.inputs}>
        <Button title="Edit goal" icon="create" variant="outline" onPress={() => setEditing(true)} style={{ flex: 1 }} />
        <Button
          title="Remove"
          variant="ghost"
          onPress={() => saveProfile({ ...profile, weightGoal: undefined, goal: 'maintain' })}
          style={{ flex: 1 }}
        />
      </View>
    </Card>
  );
}

function WeightLogCard() {
  const { profile, weighIns, addWeighIn, removeWeighIn } = useStore();
  const today = todayISO();
  const [kg, setKg] = useState('');
  const sorted = [...weighIns].sort((a, b) => b.date.localeCompare(a.date));
  return (
    <Card>
      <SectionTitle icon="scale">Weigh-ins</SectionTitle>
      <View style={styles.inputs}>
        <TextInput
          value={kg}
          onChangeText={setKg}
          keyboardType="decimal-pad"
          placeholder="Today's weight (kg)"
          placeholderTextColor={colors.textMuted}
          style={[styles.input, { flex: 1 }]}
        />
        <Button
          title="Log"
          icon="add"
          onPress={() => {
            const v = num(kg);
            if (v) {
              addWeighIn({ date: today, kg: Math.round(v * 10) / 10 });
              setKg('');
            }
          }}
          disabled={!num(kg)}
        />
      </View>
      {(weighIns.length > 0 || profile.weightGoal) && (
        <WeightChart
          weighIns={weighIns}
          plan={
            profile.weightGoal
              ? {
                  startDate: profile.weightGoal.startDate,
                  startKg: profile.weightGoal.startWeightKg,
                  targetDate: profile.weightGoal.targetDate,
                  targetKg: profile.weightGoal.targetWeightKg,
                }
              : undefined
          }
        />
      )}
      {sorted.length === 0 ? (
        <Body muted>Weigh yourself once a week at the same time of day for the clearest trend.</Body>
      ) : (
        sorted.slice(0, 6).map((w, i) => {
          const prev = sorted[i + 1];
          const diff = prev ? Math.round((w.kg - prev.kg) * 10) / 10 : null;
          return (
            <View key={w.date} style={styles.logRow}>
              <Text style={styles.logDate}>{w.date}</Text>
              <Text style={styles.logKg}>{w.kg} kg</Text>
              <Text style={[styles.logDiff, { color: diff === null ? colors.textMuted : diff <= 0 ? colors.primary : colors.accent }]}>
                {diff === null ? '' : `${diff > 0 ? '+' : ''}${diff}`}
              </Text>
              <Pressable onPress={() => removeWeighIn(w.date)} hitSlop={10} accessibilityLabel={`Delete weigh-in ${w.date}`}>
                <Ionicons name="close" size={18} color={colors.textMuted} />
              </Pressable>
            </View>
          );
        })
      )}
    </Card>
  );
}

function CoachCard({ todays, last7 }: { todays: HistoryEntry[]; last7: { date: string; kcal: number }[] }) {
  const { profile, hasProfile, eatingContext } = useStore();
  const [question, setQuestion] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [advice, setAdvice] = useState<CoachAdvice | null>(null);

  async function ask() {
    setLoading(true);
    setError(null);
    try {
      setAdvice(
        await getCoachAdvice({
          profile: hasProfile ? profile : undefined,
          context: eatingContext(),
          todaysMeals: todays.map((h) => ({
            name: h.analysis.mealName,
            kcal: h.analysis.totals.kcal,
            carbs: h.analysis.totals.carbs,
            protein: h.analysis.totals.protein,
            time: new Date(h.analysis.analyzedAt).toTimeString().slice(0, 5),
          })),
          recentDays: last7.slice(0, 6).filter((d) => d.kcal > 0),
          question: question.trim() || undefined,
        }),
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not reach Foodio.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <Card style={{ borderColor: '#BFE0CD' }}>
      <SectionTitle icon="sparkles">Foodio coach</SectionTitle>
      <TextInput
        value={question}
        onChangeText={setQuestion}
        placeholder="Ask anything, e.g. “What should I eat for supper?”"
        placeholderTextColor={colors.textMuted}
        style={styles.input}
        maxLength={400}
      />
      <Button title="What should I eat next?" icon="restaurant" onPress={ask} loading={loading} />
      {error && <ErrorBox message={error} />}
      {advice && (
        <View style={{ gap: space(3) }}>
          <Text style={styles.headline}>{advice.headline}</Text>
          <BulletList items={advice.advice} icon="checkmark-circle" />
          {advice.nextMealIdeas.map((m) => (
            <View key={m.name} style={styles.idea}>
              <View style={{ flex: 1 }}>
                <Text style={styles.ideaName}>{m.name}</Text>
                <Body muted style={{ fontSize: 13 }}>
                  {m.why}
                </Body>
              </View>
              <Text style={styles.ideaKcal}>~{Math.round(m.kcal)} kcal</Text>
            </View>
          ))}
        </View>
      )}
    </Card>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <View style={{ gap: space(1), flex: 1 }}>
      <Text style={styles.label}>{label}</Text>
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  inputs: { flexDirection: 'row', gap: space(3), alignItems: 'flex-end' },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: space(2), marginBottom: space(2) },
  label: { fontSize: 13, fontWeight: '700', color: colors.textMuted },
  input: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    padding: space(3),
    fontSize: 15,
    color: colors.text,
    backgroundColor: colors.surface,
  },
  preview: { backgroundColor: colors.primarySoft, borderRadius: radius.md, padding: space(3), gap: space(1) },
  previewText: { fontSize: 14, fontWeight: '700', color: colors.text },
  previewWarn: { fontSize: 13, color: '#8A5A12' },
  progressTrack: { height: 12, borderRadius: 6, backgroundColor: colors.surfaceAlt, overflow: 'hidden' },
  progressFill: { height: '100%', backgroundColor: colors.primary, borderRadius: 6 },
  warn: { flexDirection: 'row', gap: space(2), backgroundColor: colors.warningSoft, borderRadius: radius.md, padding: space(3) },
  warnText: { flex: 1, fontSize: 13, color: colors.text, lineHeight: 19 },
  logRow: { flexDirection: 'row', alignItems: 'center', gap: space(3), paddingVertical: 2 },
  logDate: { flex: 1, fontSize: 14, color: colors.textMuted },
  logKg: { fontSize: 15, fontWeight: '700', color: colors.text },
  logDiff: { width: 44, textAlign: 'right', fontSize: 13, fontWeight: '700' },
  todayRow: { flexDirection: 'row', alignItems: 'center', gap: space(3) },
  time: { fontSize: 13, color: colors.textMuted, width: 42 },
  todayName: { flex: 1, fontSize: 14, color: colors.text, fontWeight: '600' },
  todayKcal: { fontSize: 14, fontWeight: '700', color: colors.text },
  headline: { fontSize: 16, fontWeight: '800', color: colors.primaryDark },
  idea: { flexDirection: 'row', gap: space(3), padding: space(3), borderRadius: radius.md, backgroundColor: colors.surfaceAlt, alignItems: 'center' },
  ideaName: { fontSize: 15, fontWeight: '700', color: colors.text },
  ideaKcal: { fontSize: 14, fontWeight: '800', color: colors.accent },
  row: { flexDirection: 'row', alignItems: 'center', gap: space(3) },
  thumb: { width: 72, height: 72, borderRadius: radius.md, backgroundColor: colors.surfaceAlt },
  thumbText: { alignItems: 'center', justifyContent: 'center', backgroundColor: colors.primarySoft },
  name: { fontSize: 16, fontWeight: '700', color: colors.text },
  badge: { alignSelf: 'flex-start', fontSize: 12, fontWeight: '700', paddingHorizontal: 8, paddingVertical: 2, borderRadius: 999, overflow: 'hidden' },
});
