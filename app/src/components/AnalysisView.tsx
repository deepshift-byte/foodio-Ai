import { router } from 'expo-router';
import { GROUP_LABELS } from '@shared/nutrition';
import type { MealAnalysis } from '@shared/types';
import type { ReactNode } from 'react';
import { Image, StyleSheet, Text, View } from 'react-native';
import { BulletList, FlagList, NutrientBars, ScoreRing, VerdictBanner } from './MealWidgets';
import { PlateView } from './PlateView';
import { Body, Button, Card, Columns, SectionTitle, Stat, useLayout } from './ui';
import { colors, groupColors, radius, space } from '@/lib/theme';

/** Full, read-only presentation of a meal analysis. */
export function AnalysisView({ analysis, imageUri, questionsSlot }: { analysis: MealAnalysis; imageUri: string; questionsSlot?: ReactNode }) {
  const { isWide } = useLayout();
  const a = analysis;

  const left = (
    <>
      <Card style={{ padding: 0, overflow: 'hidden' }}>
        <Image source={{ uri: imageUri }} style={[styles.photo, { height: isWide ? 320 : 240 }]} resizeMode="cover" />
        <View style={{ padding: space(4), gap: space(2) }}>
          <Text style={styles.mealName}>{a.mealName}</Text>
          {!!a.summary && <Body muted>{a.summary}</Body>}
        </View>
      </Card>

      <VerdictBanner verdict={a.verdict} reason={a.verdictReason} />

      <Card>
        <View style={styles.statsRow}>
          <ScoreRing score={a.balanceScore} />
          <View style={styles.stats}>
            <Stat label="Calories" value={a.totals.kcal} unit="kcal" color={colors.accent} />
            <Stat label="Carbs" value={Math.round(a.totals.carbs)} unit="g" />
            <Stat label="Protein" value={Math.round(a.totals.protein)} unit="g" />
          </View>
        </View>
        {a.overallConfidence < 0.6 && (
          <Body muted style={{ fontSize: 13 }}>
            Foodio is not very sure about this photo — answering the questions below will improve the estimate.
          </Body>
        )}
      </Card>

      {questionsSlot}

      {a.flags.length > 0 && (
        <Card>
          <SectionTitle icon="medkit">Health check</SectionTitle>
          <FlagList flags={a.flags} />
        </Card>
      )}

      <Card>
        <SectionTitle icon="list">What's on your plate</SectionTitle>
        {a.items.length === 0 && <Body muted>No foods detected.</Body>}
        {a.items.map((it, i) => (
          <View key={i} style={styles.item}>
            <View style={[styles.dot, { backgroundColor: groupColors[it.group] }]} />
            <View style={{ flex: 1 }}>
              <Text style={styles.itemName}>
                {it.name}
                {it.confidence < 0.6 && <Text style={styles.unsure}>  unsure</Text>}
              </Text>
              <Body muted style={{ fontSize: 13 }}>
                ~{it.grams} g · {it.preparation} · {GROUP_LABELS[it.group]}
              </Body>
            </View>
            <Text style={styles.itemKcal}>{it.nutrients.kcal} kcal</Text>
            {it.foodId && (
              <Text style={styles.link} onPress={() => router.push({ pathname: '/food/[id]', params: { id: it.foodId! } })}>
                Info
              </Text>
            )}
          </View>
        ))}
      </Card>
    </>
  );

  const right = (
    <>
      <Card>
        <SectionTitle icon="pie-chart">Your plate vs. a healthy plate</SectionTitle>
        <View style={[styles.plates, isWide && { flexDirection: 'row' }]}>
          <View style={styles.plateCol}>
            <Text style={styles.plateTitle}>Your plate</Text>
            <PlateView
              size={180}
              slices={a.items.map((it) => ({ group: it.group, fraction: it.plateFraction, label: it.name, grams: it.grams }))}
            />
          </View>
          <View style={styles.plateCol}>
            <Text style={styles.plateTitle}>How it should look</Text>
            <PlateView size={180} slices={a.idealPlate} />
          </View>
        </View>
        {a.missingGroups.length > 0 && (
          <View style={styles.missing}>
            <Text style={styles.missingText}>Missing: {a.missingGroups.map((g) => GROUP_LABELS[g]).join(', ')}</Text>
          </View>
        )}
      </Card>

      {a.portionAdvice.length > 0 && (
        <Card>
          <SectionTitle icon="resize">How to portion this meal</SectionTitle>
          <BulletList items={a.portionAdvice} icon="ellipse" color={colors.accent} />
        </Card>
      )}

      {a.suggestions.length > 0 && (
        <Card>
          <SectionTitle icon="bulb">Make it healthier</SectionTitle>
          <BulletList items={a.suggestions} />
        </Card>
      )}

      <Card>
        <SectionTitle icon="stats-chart">Nutrition</SectionTitle>
        <NutrientBars totals={a.totals} />
        <Body muted style={{ fontSize: 12 }}>
          Estimates from the photo and Foodio's African food database. Actual values vary with recipe and portion.
        </Body>
      </Card>

      <Button
        title="Show me a healthy recipe for this"
        icon="restaurant"
        variant="outline"
        onPress={() => router.push({ pathname: '/kitchen', params: { dish: a.mealName } })}
      />
    </>
  );

  return <Columns left={left} right={right} />;
}

const styles = StyleSheet.create({
  photo: { width: '100%', backgroundColor: colors.surfaceAlt },
  mealName: { fontSize: 22, fontWeight: '800', color: colors.text },
  statsRow: { flexDirection: 'row', alignItems: 'center', gap: space(4) },
  stats: { flex: 1, flexDirection: 'row', flexWrap: 'wrap', gap: space(2) },
  item: { flexDirection: 'row', alignItems: 'center', gap: space(3), paddingVertical: space(1) },
  dot: { width: 12, height: 12, borderRadius: 6 },
  itemName: { fontSize: 15, fontWeight: '700', color: colors.text },
  unsure: { fontSize: 12, color: colors.warning, fontWeight: '700' },
  itemKcal: { fontSize: 14, fontWeight: '700', color: colors.text },
  link: { color: colors.primary, fontWeight: '700', fontSize: 13, paddingLeft: space(1) },
  plates: { gap: space(6) },
  plateCol: { flex: 1, gap: space(2), alignItems: 'center' },
  plateTitle: { fontSize: 14, fontWeight: '800', color: colors.textMuted, textTransform: 'uppercase', letterSpacing: 0.5 },
  missing: { backgroundColor: colors.accentSoft, borderRadius: radius.md, padding: space(3) },
  missingText: { color: colors.text, fontWeight: '700' },
});
