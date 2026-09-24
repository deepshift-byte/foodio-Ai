import { Ionicons } from '@expo/vector-icons';
import { router } from 'expo-router';
import { useState } from 'react';
import { Image, Platform, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { dailyKcal, DIET_LABELS } from '@shared/nutrition';
import { BudgetBar } from '@/components/MealWidgets';
import { Body, Button, Card, Columns, ErrorBox, H1, SectionTitle, Screen, useLayout } from '@/components/ui';
import { pickMealPhoto } from '@/lib/image';
import { isToday, useStore } from '@/lib/store';
import { colors, radius, space, verdictStyle } from '@/lib/theme';

export default function ScanScreen() {
  const { profile, hasProfile, history, setScan, currentWeightKg } = useStore();
  const { isWide } = useLayout();
  const [note, setNote] = useState('');
  const [question, setQuestion] = useState('');
  const [busy, setBusy] = useState<'camera' | 'library' | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function start(source: 'camera' | 'library') {
    setError(null);
    setBusy(source);
    try {
      const image = await pickMealPhoto(source);
      if (image) {
        setScan({ image, note: note.trim() || undefined, answers: [] });
        setNote('');
        router.push('/analysis');
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not open the photo.');
    } finally {
      setBusy(null);
    }
  }

  function askWithoutPhoto() {
    const text = question.trim();
    if (!text) return;
    setScan({ note: text, answers: [] });
    setQuestion('');
    router.push('/analysis');
  }

  const todays = history.filter((h) => isToday(h.analysis.analyzedAt));
  const todayKcal = todays.reduce((s, h) => s + h.analysis.totals.kcal, 0);
  const budget = dailyKcal(hasProfile ? profile : undefined, currentWeightKg);
  const plans = profile.dietPlans ?? [];

  const greeting = profile.name ? `Hi ${profile.name.split(' ')[0]}!` : 'Welcome to Foodio';

  const hero = (
    <View style={styles.hero}>
      <View style={styles.brandRow}>
        <View style={styles.logo}>
          <Ionicons name="leaf" size={22} color={colors.white} />
        </View>
        <Text style={styles.brand}>Foodio AI</Text>
      </View>
      <H1 style={{ color: colors.white }}>{greeting}</H1>
      <Text style={styles.heroText}>
        Snap your plate before you eat. Foodio recognises Ugandan and African foods, estimates portions and calories, and tells you if the
        meal is right for your health.
      </Text>

      <View style={[styles.actions, isWide && { flexDirection: 'row' }]}>
        <Button
          title={Platform.OS === 'web' ? 'Take or upload photo' : 'Take a photo'}
          icon="camera"
          variant="accent"
          onPress={() => start('camera')}
          loading={busy === 'camera'}
          style={{ flex: isWide ? 1 : undefined }}
        />
        <Button
          title="Choose from gallery"
          icon="images"
          variant="outline"
          onPress={() => start('library')}
          loading={busy === 'library'}
          style={{ flex: isWide ? 1 : undefined }}
        />
      </View>
      <TextInput
        value={note}
        onChangeText={setNote}
        placeholder="Optional: tell Foodio anything, e.g. “fish is fried, tea has 2 sugars”"
        placeholderTextColor="#CFE5D8"
        style={styles.noteInput}
        maxLength={300}
      />
      {error && <ErrorBox message={error} />}
    </View>
  );

  const left = (
    <>
      <Card>
        <SectionTitle icon="help-circle">Can I eat this?</SectionTitle>
        <Body muted>No photo? Describe what you're about to eat and Foodio checks it against your health, diet plan and today's calories.</Body>
        <TextInput
          value={question}
          onChangeText={setQuestion}
          placeholder="e.g. 2 chapatis, a bowl of beans and a cup of tea with sugar"
          placeholderTextColor={colors.textMuted}
          style={styles.askInput}
          multiline
          maxLength={400}
        />
        <Button title="Check this meal" icon="shield-checkmark" onPress={askWithoutPhoto} disabled={!question.trim()} />
      </Card>
      {hasProfile && (
        <Pressable onPress={() => router.push('/progress')} accessibilityRole="button">
          <Card>
            <SectionTitle icon="today">Today</SectionTitle>
            <BudgetBar consumed={todayKcal} daily={budget} />
            {(plans.length > 0 || profile.weightGoal) && (
              <Body muted style={{ fontSize: 13 }}>
                {[
                  ...plans.map((p) => DIET_LABELS[p]),
                  profile.weightGoal ? `Goal ${profile.weightGoal.targetWeightKg} kg by ${profile.weightGoal.targetDate}` : '',
                ]
                  .filter(Boolean)
                  .join(' · ')}
              </Body>
            )}
          </Card>
        </Pressable>
      )}
      {!hasProfile && (
        <Pressable onPress={() => router.push('/profile')} accessibilityRole="button">
          <Card style={styles.profileNudge}>
            <Ionicons name="heart-circle" size={36} color={colors.accent} />
            <View style={{ flex: 1 }}>
              <Text style={styles.nudgeTitle}>Personalise your advice</Text>
              <Body muted>Diabetic? High blood pressure? Pregnant? Allergic to groundnuts? Tell Foodio so it can warn you before you eat.</Body>
            </View>
            <Ionicons name="chevron-forward" size={22} color={colors.textMuted} />
          </Card>
        </Pressable>
      )}
      <Card>
        <SectionTitle icon="sparkles">How Foodio works</SectionTitle>
        {[
          ['camera-outline', 'Snap your meal', 'Take a clear photo from above, with the whole plate in view.'],
          ['scan-outline', 'Foodio identifies each food', 'Matooke, posho, beans, g-nut sauce, dodo… with portion sizes and calories.'],
          ['chatbubbles-outline', 'Answer quick questions', 'If Foodio is unsure (fried or stewed? sugar in the tea?) it asks.'],
          ['eye-off-outline', 'Invisible ingredients too', 'Salt, stock cubes, oil and sugar can’t be seen — Foodio asks about them when they matter for you.'],
          ['heart-outline', 'Get personal advice', 'Is it safe for your condition, your diet plan (keto, 16:8…) and your weight goal?'],
        ].map(([icon, title, text]) => (
          <View key={title} style={styles.step}>
            <View style={styles.stepIcon}>
              <Ionicons name={icon as 'camera-outline'} size={20} color={colors.primary} />
            </View>
            <View style={{ flex: 1 }}>
              <Text style={styles.stepTitle}>{title}</Text>
              <Body muted>{text}</Body>
            </View>
          </View>
        ))}
      </Card>
    </>
  );

  const right = (
    <>
      <Card>
        <SectionTitle icon="time">Recent meals</SectionTitle>
        {history.length === 0 ? (
          <Body muted>Your analysed meals will appear here.</Body>
        ) : (
          history.slice(0, 4).map((h) => {
            const v = verdictStyle[h.analysis.verdict];
            return (
              <Pressable
                key={h.id}
                style={styles.recent}
                onPress={() => {
                  router.push({ pathname: '/progress', params: { open: h.id } });
                }}
              >
                {h.thumbnail ? (
                  <Image source={{ uri: h.thumbnail }} style={styles.thumb} />
                ) : (
                  <View style={[styles.thumb, styles.thumbText]}>
                    <Ionicons name="chatbubble-ellipses" size={22} color={colors.primary} />
                  </View>
                )}
                <View style={{ flex: 1 }}>
                  <Text style={styles.stepTitle} numberOfLines={1}>
                    {h.analysis.mealName}
                  </Text>
                  <Body muted>
                    {h.analysis.totals.kcal} kcal · balance {h.analysis.balanceScore}
                  </Body>
                </View>
                <Ionicons name={v.icon as 'checkmark-circle'} size={22} color={v.color} />
              </Pressable>
            );
          })
        )}
      </Card>
    </>
  );

  return (
    <Screen>
      {hero}
      <Columns left={left} right={right} />
      <Body muted style={styles.disclaimer}>
        Foodio gives general nutrition guidance, not medical advice. Always follow your doctor or health worker.
      </Body>
    </Screen>
  );
}

const styles = StyleSheet.create({
  hero: {
    backgroundColor: colors.primary,
    borderRadius: radius.lg + 4,
    padding: space(6),
    gap: space(3),
    overflow: 'hidden',
  },
  brandRow: { flexDirection: 'row', alignItems: 'center', gap: space(2) },
  logo: { width: 36, height: 36, borderRadius: 10, backgroundColor: colors.accent, alignItems: 'center', justifyContent: 'center' },
  brand: { color: colors.white, fontSize: 18, fontWeight: '800', letterSpacing: 0.3 },
  heroText: { color: '#E3F2EA', fontSize: 16, lineHeight: 24, maxWidth: 640 },
  actions: { gap: space(3), marginTop: space(2) },
  noteInput: {
    borderWidth: 1,
    borderColor: '#4E9C74',
    borderRadius: radius.md,
    paddingHorizontal: space(3),
    paddingVertical: space(3),
    color: colors.white,
    fontSize: 14,
    backgroundColor: colors.primaryDark,
  },
  profileNudge: { flexDirection: 'row', alignItems: 'center', gap: space(3), backgroundColor: colors.accentSoft, borderColor: '#F6C99A' },
  nudgeTitle: { fontSize: 16, fontWeight: '800', color: colors.text, marginBottom: 2 },
  step: { flexDirection: 'row', gap: space(3), alignItems: 'flex-start' },
  stepIcon: { width: 36, height: 36, borderRadius: 18, backgroundColor: colors.primarySoft, alignItems: 'center', justifyContent: 'center' },
  stepTitle: { fontSize: 15, fontWeight: '700', color: colors.text },
  recent: { flexDirection: 'row', alignItems: 'center', gap: space(3) },
  askInput: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    padding: space(3),
    minHeight: 72,
    fontSize: 15,
    color: colors.text,
    backgroundColor: colors.surface,
    textAlignVertical: 'top',
  },
  thumbText: { alignItems: 'center', justifyContent: 'center', backgroundColor: colors.primarySoft },
  thumb: { width: 56, height: 56, borderRadius: radius.sm, backgroundColor: colors.surfaceAlt },
  disclaimer: { textAlign: 'center', fontSize: 12 },
});
