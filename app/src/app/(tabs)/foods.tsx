import { Ionicons } from '@expo/vector-icons';
import { router } from 'expo-router';
import { searchFoods } from '@shared/foods';
import { FOOD_GROUPS, GROUP_LABELS } from '@shared/nutrition';
import type { FoodGroup } from '@shared/types';
import { useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { Body, Chip, H1, Screen, useLayout } from '@/components/ui';
import { colors, groupColors, radius, shadow, space } from '@/lib/theme';

const GI_COLOR = { low: colors.primary, medium: colors.warning, high: colors.danger };

export default function FoodsScreen() {
  const [query, setQuery] = useState('');
  const [group, setGroup] = useState<FoodGroup | null>(null);
  const { width, isWide } = useLayout();
  const foods = useMemo(() => searchFoods(query).filter((f) => !group || f.group === group), [query, group]);
  const cols = width >= 1100 ? 3 : isWide || width >= 640 ? 2 : 1;

  return (
    <Screen>
      <H1>African Food Guide</H1>
      <Body muted>Nutrition for Ugandan and other African foods, per typical serving. Search by English or local name.</Body>
      <View style={styles.search}>
        <Ionicons name="search" size={18} color={colors.textMuted} />
        <TextInput
          value={query}
          onChangeText={setQuery}
          placeholder="Search: matooke, binyebwa, dodo, rolex…"
          placeholderTextColor={colors.textMuted}
          style={styles.searchInput}
        />
      </View>
      <View style={styles.chips}>
        <Chip label="All" selected={!group} onPress={() => setGroup(null)} />
        {FOOD_GROUPS.map((g) => (
          <Chip key={g} label={GROUP_LABELS[g]} selected={group === g} onPress={() => setGroup(group === g ? null : g)} color={groupColors[g]} />
        ))}
      </View>
      <View style={styles.grid}>
        {foods.map((f) => {
          const k = f.typicalServingG / 100;
          return (
            <Pressable
              key={f.id}
              onPress={() => router.push({ pathname: '/food/[id]', params: { id: f.id } })}
              style={({ pressed }) => [styles.card, { width: cols === 1 ? '100%' : cols === 2 ? '48.8%' : '32.4%', opacity: pressed ? 0.85 : 1 }]}
            >
              <View style={[styles.band, { backgroundColor: groupColors[f.group] }]} />
              <View style={{ flex: 1, gap: 4 }}>
                <Text style={styles.name}>{f.name}</Text>
                <Text style={styles.local} numberOfLines={1}>
                  {f.localNames.join(' · ')}
                </Text>
                <View style={styles.metaRow}>
                  <Text style={styles.kcal}>{Math.round(f.per100g.kcal * k)} kcal</Text>
                  <Text style={styles.serving}>per {f.typicalServingG} g</Text>
                  <Text style={[styles.gi, { color: GI_COLOR[f.gi] }]}>GI {f.gi}</Text>
                </View>
              </View>
            </Pressable>
          );
        })}
      </View>
      {foods.length === 0 && <Body muted>No foods match “{query}”.</Body>}
    </Screen>
  );
}

const styles = StyleSheet.create({
  search: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space(2),
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    paddingHorizontal: space(3),
  },
  searchInput: { flex: 1, paddingVertical: space(3), fontSize: 15, color: colors.text },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: space(2) },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: space(3), justifyContent: 'space-between' },
  card: {
    flexDirection: 'row',
    gap: space(3),
    backgroundColor: colors.surface,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    padding: space(3),
    ...shadow,
  },
  band: { width: 6, borderRadius: 3 },
  name: { fontSize: 15, fontWeight: '700', color: colors.text },
  local: { fontSize: 13, color: colors.textMuted },
  metaRow: { flexDirection: 'row', gap: space(3), alignItems: 'baseline', flexWrap: 'wrap' },
  kcal: { fontSize: 15, fontWeight: '800', color: colors.accent },
  serving: { fontSize: 12, color: colors.textMuted },
  gi: { fontSize: 12, fontWeight: '700' },
});
