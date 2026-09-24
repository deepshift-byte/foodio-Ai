import { dailyKcal, formatHour } from '@shared/nutrition';
import type { Allergy, CancerSymptom, DietPlan, Goal, HealthCondition, HealthProfile } from '@shared/types';
import { router } from 'expo-router';
import { useEffect, useState } from 'react';
import { StyleSheet, Text, TextInput, View } from 'react-native';
import { Body, Button, Card, Chip, Columns, H1, SectionTitle, Screen, Stat } from '@/components/ui';
import { useStore } from '@/lib/store';
import { colors, radius, space } from '@/lib/theme';

const CONDITIONS: { id: HealthCondition; label: string }[] = [
  { id: 'diabetes', label: 'Diabetes' },
  { id: 'prediabetes', label: 'Pre-diabetes' },
  { id: 'hypertension', label: 'High blood pressure' },
  { id: 'high-cholesterol', label: 'High cholesterol' },
  { id: 'kidney-disease', label: 'Kidney disease' },
  { id: 'pregnancy', label: 'Pregnant' },
  { id: 'weight-loss', label: 'Trying to lose weight' },
  { id: 'anemia', label: 'Anaemia' },
  { id: 'ulcers-gerd', label: 'Ulcers / heartburn' },
  { id: 'gout', label: 'Gout' },
  { id: 'celiac', label: 'Coeliac disease' },
  { id: 'cancer', label: 'Cancer' },
];

const DIETS: { id: DietPlan; label: string; hint: string }[] = [
  { id: 'keto', label: 'Keto', hint: 'Very low carb (about 20–50 g a day), higher fat' },
  { id: 'low-carb', label: 'Low-carb', hint: 'About 30–40 g carbs per meal' },
  { id: 'intermittent-fasting', label: 'Intermittent fasting', hint: 'Only eat inside your eating window' },
  { id: 'mediterranean', label: 'Mediterranean', hint: 'Fish, beans, vegetables, healthy oils' },
  { id: 'dash', label: 'DASH (low salt)', hint: 'For blood pressure — little salt' },
  { id: 'vegetarian', label: 'Vegetarian', hint: 'No meat or fish' },
  { id: 'vegan', label: 'Vegan', hint: 'No animal products at all' },
  { id: 'high-protein', label: 'High-protein', hint: 'Protein at every meal' },
];

const FASTING_PRESETS = [
  { label: '16:8', hours: 8 },
  { label: '18:6', hours: 6 },
  { label: '14:10', hours: 10 },
  { label: '20:4', hours: 4 },
];

const SYMPTOMS: { id: CancerSymptom; label: string }[] = [
  { id: 'nausea', label: 'Nausea' },
  { id: 'mouth-sores', label: 'Mouth sores' },
  { id: 'low-appetite', label: 'Low appetite' },
  { id: 'unintended-weight-loss', label: 'Losing weight without trying' },
  { id: 'low-immunity', label: 'Low immunity' },
  { id: 'taste-changes', label: 'Taste changes' },
];

const ALLERGIES: { id: Allergy; label: string }[] = [
  { id: 'peanut', label: 'Groundnuts / peanuts' },
  { id: 'gluten', label: 'Wheat / gluten' },
  { id: 'fish', label: 'Fish' },
  { id: 'egg', label: 'Eggs' },
  { id: 'dairy', label: 'Milk / dairy' },
];

const GOALS: { id: Goal; label: string }[] = [
  { id: 'maintain', label: 'Stay healthy' },
  { id: 'lose', label: 'Lose weight' },
  { id: 'gain', label: 'Gain weight' },
  { id: 'muscle', label: 'Build muscle' },
];

const num = (s: string) => {
  const n = Number(s.replace(',', '.'));
  return s.trim() && Number.isFinite(n) && n > 0 ? n : undefined;
};

export default function ProfileScreen() {
  const { profile, saveProfile, currentWeightKg } = useStore();
  const [p, setP] = useState<HealthProfile>(profile);
  const [age, setAge] = useState(profile.age?.toString() ?? '');
  const [weight, setWeight] = useState(profile.weightKg?.toString() ?? '');
  const [height, setHeight] = useState(profile.heightCm?.toString() ?? '');
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    setP(profile);
    setAge(profile.age?.toString() ?? '');
    setWeight(profile.weightKg?.toString() ?? '');
    setHeight(profile.heightCm?.toString() ?? '');
  }, [profile]);

  const draft: HealthProfile = {
    ...p,
    age: num(age) ? Math.round(num(age)!) : undefined,
    weightKg: num(weight),
    heightCm: num(height),
  };
  const diets = p.dietPlans ?? [];
  const bmiWeight = currentWeightKg ?? draft.weightKg;
  const bmi = bmiWeight && draft.heightCm ? bmiWeight / (draft.heightCm / 100) ** 2 : undefined;

  function toggle<T>(list: T[], item: T) {
    return list.includes(item) ? list.filter((x) => x !== item) : [...list, item];
  }

  function save() {
    saveProfile(draft);
    setSaved(true);
    setTimeout(() => setSaved(false), 2000);
  }

  const left = (
    <>
      <Card>
        <SectionTitle icon="person">About you</SectionTitle>
        <Field label="Name">
          <TextInput value={p.name} onChangeText={(name) => setP({ ...p, name })} placeholder="Your name" placeholderTextColor={colors.textMuted} style={styles.input} />
        </Field>
        <View style={styles.row}>
          <Field label="Age" flex>
            <TextInput value={age} onChangeText={setAge} keyboardType="number-pad" placeholder="30" placeholderTextColor={colors.textMuted} style={styles.input} />
          </Field>
          <Field label="Weight (kg)" flex>
            <TextInput value={weight} onChangeText={setWeight} keyboardType="decimal-pad" placeholder="65" placeholderTextColor={colors.textMuted} style={styles.input} />
          </Field>
          <Field label="Height (cm)" flex>
            <TextInput value={height} onChangeText={setHeight} keyboardType="decimal-pad" placeholder="165" placeholderTextColor={colors.textMuted} style={styles.input} />
          </Field>
        </View>
        <Field label="Sex">
          <View style={styles.chips}>
            {(['female', 'male', 'other'] as const).map((s) => (
              <Chip key={s} label={s[0].toUpperCase() + s.slice(1)} selected={p.sex === s} onPress={() => setP({ ...p, sex: p.sex === s ? undefined : s })} />
            ))}
          </View>
        </Field>
        <Field label="How active are you?">
          <View style={styles.chips}>
            {(['low', 'moderate', 'high'] as const).map((a) => (
              <Chip key={a} label={a[0].toUpperCase() + a.slice(1)} selected={p.activity === a} onPress={() => setP({ ...p, activity: a })} />
            ))}
          </View>
        </Field>
        <Field label="Goal">
          <View style={styles.chips}>
            {GOALS.map((g) => (
              <Chip key={g.id} label={g.label} selected={p.goal === g.id} onPress={() => setP({ ...p, goal: g.id })} />
            ))}
          </View>
          <Text style={styles.link} onPress={() => router.push('/progress')}>
            {p.weightGoal
              ? `Target: ${p.weightGoal.targetWeightKg} kg by ${p.weightGoal.targetDate} — edit in Progress →`
              : 'Set a target weight and date in Progress →'}
          </Text>
        </Field>
      </Card>
      <Card style={{ flexDirection: 'row' }}>
        <Stat label={draft.weightGoal ? 'Daily budget' : 'Daily energy (est.)'} value={dailyKcal(draft, currentWeightKg)} unit="kcal" color={colors.accent} />
        <Stat label="Per meal" value={Math.round(dailyKcal(draft, currentWeightKg) / 3)} unit="kcal" />
        <Stat label="BMI" value={bmi ? bmi.toFixed(1) : '—'} />
      </Card>
      {draft.weightGoal && draft.conditions.includes('cancer') && draft.cancerCare?.phase === 'in-treatment' && (
        <Body muted style={{ fontSize: 13 }}>
          During cancer treatment Foodio doesn't plan a calorie deficit — keeping your strength comes first. Agree any weight-loss plan with your care team.
        </Body>
      )}
    </>
  );

  const right = (
    <>
      <Card>
        <SectionTitle icon="medkit">Health conditions</SectionTitle>
        <Body muted>Foodio checks every meal against these before you eat.</Body>
        <View style={styles.chips}>
          {CONDITIONS.map((c) => (
            <Chip
              key={c.id}
              label={c.label}
              selected={p.conditions.includes(c.id)}
              onPress={() => {
                const conditions = toggle(p.conditions, c.id);
                setP({
                  ...p,
                  conditions,
                  cancerCare: conditions.includes('cancer') ? p.cancerCare ?? { phase: 'in-treatment', symptoms: [] } : undefined,
                });
              }}
              color={colors.accent}
            />
          ))}
        </View>
        {p.conditions.includes('cancer') && p.cancerCare && (
          <View style={styles.subSection}>
            <Text style={styles.subTitle}>Cancer care</Text>
            <Body muted style={{ fontSize: 13 }}>
              Foodio will focus on strength, protein and food safety, and adapt to how you feel. Always follow your cancer care team.
            </Body>
            <View style={styles.chips}>
              <Chip label="In treatment" selected={p.cancerCare.phase === 'in-treatment'} onPress={() => setP({ ...p, cancerCare: { ...p.cancerCare!, phase: 'in-treatment' } })} />
              <Chip label="Recovery / survivor" selected={p.cancerCare.phase === 'recovery'} onPress={() => setP({ ...p, cancerCare: { ...p.cancerCare!, phase: 'recovery' } })} />
            </View>
            <Text style={styles.label}>How are you feeling? (choose any)</Text>
            <View style={styles.chips}>
              {SYMPTOMS.map((sym) => (
                <Chip
                  key={sym.id}
                  label={sym.label}
                  selected={p.cancerCare!.symptoms.includes(sym.id)}
                  onPress={() => setP({ ...p, cancerCare: { ...p.cancerCare!, symptoms: toggle(p.cancerCare!.symptoms, sym.id) } })}
                  color={colors.info}
                />
              ))}
            </View>
          </View>
        )}
      </Card>
      <Card>
        <SectionTitle icon="nutrition">Diet plan</SectionTitle>
        <Body muted>Foodio checks every meal against the plans you follow.</Body>
        <View style={styles.chips}>
          {DIETS.map((d) => (
            <Chip
              key={d.id}
              label={d.label}
              selected={diets.includes(d.id)}
              onPress={() => {
                let next = toggle(diets, d.id);
                // Keto already implies low-carb; vegan implies vegetarian.
                if (d.id === 'keto' && next.includes('keto')) next = next.filter((x) => x !== 'low-carb');
                if (d.id === 'low-carb' && next.includes('low-carb')) next = next.filter((x) => x !== 'keto');
                if (d.id === 'vegan' && next.includes('vegan')) next = next.filter((x) => x !== 'vegetarian');
                if (d.id === 'vegetarian' && next.includes('vegetarian')) next = next.filter((x) => x !== 'vegan');
                setP({
                  ...p,
                  dietPlans: next,
                  fastingWindow: next.includes('intermittent-fasting') ? p.fastingWindow ?? { startHour: 12, hours: 8 } : undefined,
                });
              }}
              color={colors.primary}
            />
          ))}
        </View>
        {diets.map((id) => (
          <Body key={id} muted style={{ fontSize: 13 }}>
            • {DIETS.find((d) => d.id === id)!.label}: {DIETS.find((d) => d.id === id)!.hint}
          </Body>
        ))}
        {diets.includes('intermittent-fasting') && p.fastingWindow && (
          <View style={styles.subSection}>
            <Text style={styles.subTitle}>Fasting schedule</Text>
            <View style={styles.chips}>
              {FASTING_PRESETS.map((f) => (
                <Chip key={f.label} label={f.label} selected={p.fastingWindow!.hours === f.hours} onPress={() => setP({ ...p, fastingWindow: { ...p.fastingWindow!, hours: f.hours } })} />
              ))}
            </View>
            <Text style={styles.label}>Eating window starts at</Text>
            <View style={styles.chips}>
              {[6, 8, 10, 11, 12, 13, 14].map((h) => (
                <Chip key={h} label={formatHour(h)} selected={p.fastingWindow!.startHour === h} onPress={() => setP({ ...p, fastingWindow: { ...p.fastingWindow!, startHour: h } })} />
              ))}
            </View>
            <Body style={{ fontWeight: '700' }}>
              Eat between {formatHour(p.fastingWindow.startHour)} and {formatHour(p.fastingWindow.startHour + p.fastingWindow.hours)} · fast{' '}
              {24 - p.fastingWindow.hours} hours
            </Body>
          </View>
        )}
      </Card>
      <Card>
        <SectionTitle icon="warning">Allergies</SectionTitle>
        <View style={styles.chips}>
          {ALLERGIES.map((a) => (
            <Chip key={a.id} label={a.label} selected={p.allergies.includes(a.id)} onPress={() => setP({ ...p, allergies: toggle(p.allergies, a.id) })} color={colors.danger} />
          ))}
        </View>
      </Card>
      <Card>
        <SectionTitle icon="document-text">Notes for Foodio</SectionTitle>
        <TextInput
          value={p.notes ?? ''}
          onChangeText={(notes) => setP({ ...p, notes })}
          placeholder="e.g. on metformin, doctor said max 1 matooke finger per meal, vegetarian"
          placeholderTextColor={colors.textMuted}
          style={[styles.input, { minHeight: 90, textAlignVertical: 'top' }]}
          multiline
          maxLength={1000}
        />
      </Card>
    </>
  );

  return (
    <Screen>
      <H1>Your health profile</H1>
      <Body muted>Stored only on this device, and sent with a photo only when you analyse a meal.</Body>
      <Columns left={left} right={right} />
      <Button title={saved ? 'Saved ✓' : 'Save profile'} icon="save" onPress={save} />
    </Screen>
  );
}

function Field({ label, children, flex }: { label: string; children: React.ReactNode; flex?: boolean }) {
  return (
    <View style={[{ gap: space(1) }, flex && { flex: 1 }]}>
      <Text style={styles.label}>{label}</Text>
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', gap: space(3) },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: space(2) },
  label: { fontSize: 13, fontWeight: '700', color: colors.textMuted },
  link: { color: colors.primary, fontWeight: '700', fontSize: 14, marginTop: space(1) },
  subSection: { gap: space(2), padding: space(3), borderRadius: radius.md, backgroundColor: colors.surfaceAlt },
  subTitle: { fontSize: 15, fontWeight: '800', color: colors.text },
  input: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    padding: space(3),
    fontSize: 15,
    color: colors.text,
    backgroundColor: colors.surface,
  },
});
