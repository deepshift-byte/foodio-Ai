import { dailyKcal } from '@shared/nutrition';
import type { Allergy, Goal, HealthCondition, HealthProfile } from '@shared/types';
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
  const { profile, saveProfile } = useStore();
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
  const bmi = draft.weightKg && draft.heightCm ? draft.weightKg / (draft.heightCm / 100) ** 2 : undefined;

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
        </Field>
      </Card>
      <Card style={{ flexDirection: 'row' }}>
        <Stat label="Daily energy (est.)" value={dailyKcal(draft)} unit="kcal" color={colors.accent} />
        <Stat label="Per meal" value={Math.round(dailyKcal(draft) / 3)} unit="kcal" />
        <Stat label="BMI" value={bmi ? bmi.toFixed(1) : '—'} />
      </Card>
    </>
  );

  const right = (
    <>
      <Card>
        <SectionTitle icon="medkit">Health conditions</SectionTitle>
        <Body muted>Foodio checks every meal against these before you eat.</Body>
        <View style={styles.chips}>
          {CONDITIONS.map((c) => (
            <Chip key={c.id} label={c.label} selected={p.conditions.includes(c.id)} onPress={() => setP({ ...p, conditions: toggle(p.conditions, c.id) })} color={colors.accent} />
          ))}
        </View>
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
