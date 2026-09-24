import { Ionicons } from '@expo/vector-icons';
import { router, useLocalSearchParams } from 'expo-router';
import type { RecipeResponse } from '@shared/types';
import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { PlateView } from '@/components/PlateView';
import { Body, Button, Card, Chip, ErrorBox, H1, SectionTitle, Screen, useLayout } from '@/components/ui';
import { getRecipes } from '@/lib/api';
import { useStore } from '@/lib/store';
import { colors, radius, space } from '@/lib/theme';

const QUICK_INGREDIENTS = [
  'Matooke', 'Posho flour', 'Millet flour', 'Sweet potatoes', 'Cassava', 'Rice',
  'Beans', 'Groundnuts', 'Peas', 'Eggs', 'Tilapia', 'Mukene', 'Chicken', 'Beef',
  'Dodo', 'Nakati', 'Sukuma wiki', 'Cabbage', 'Tomatoes', 'Onions', 'Carrots', 'Avocado',
];

const MEAL_TYPES = ['any', 'breakfast', 'lunch', 'dinner', 'snack'] as const;

export default function KitchenScreen() {
  const params = useLocalSearchParams<{ dish?: string }>();
  const { profile, hasProfile, setRecipe, eatingContext } = useStore();
  const { isWide } = useLayout();
  const [ingredients, setIngredients] = useState<string[]>([]);
  const [draft, setDraft] = useState('');
  const [dish, setDish] = useState('');
  const [mealType, setMealType] = useState<(typeof MEAL_TYPES)[number]>('any');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<RecipeResponse | null>(null);

  useEffect(() => {
    if (params.dish) setDish(params.dish);
  }, [params.dish]);

  function toggle(item: string) {
    setIngredients((list) => (list.includes(item) ? list.filter((i) => i !== item) : [...list, item]));
  }

  function addDraft() {
    const items = draft
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean);
    if (items.length) setIngredients((list) => [...list, ...items.filter((i) => !list.includes(i))]);
    setDraft('');
  }

  async function suggest() {
    setLoading(true);
    setError(null);
    try {
      setResult(
        await getRecipes({
          ingredients,
          dish: dish.trim() || undefined,
          mealType,
          profile: hasProfile ? profile : undefined,
          context: eatingContext(),
        }),
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not get recipes.');
    } finally {
      setLoading(false);
    }
  }

  const custom = ingredients.filter((i) => !QUICK_INGREDIENTS.includes(i));

  return (
    <Screen>
      <H1>Foodio Kitchen</H1>
      <Body muted>Tell Foodio what's in your kitchen — or a dish you'd like to cook — and get balanced, healthy meals with a picture of how to plate them.</Body>

      <Card>
        <SectionTitle icon="basket">Ingredients you have</SectionTitle>
        <View style={styles.chips}>
          {QUICK_INGREDIENTS.map((i) => (
            <Chip key={i} label={i} selected={ingredients.includes(i)} onPress={() => toggle(i)} />
          ))}
          {custom.map((i) => (
            <Chip key={i} label={i} selected onPress={() => toggle(i)} color={colors.accent} />
          ))}
        </View>
        <View style={styles.row}>
          <TextInput
            value={draft}
            onChangeText={setDraft}
            onSubmitEditing={addDraft}
            placeholder="Add others, e.g. ginger, pumpkin, obutiko"
            placeholderTextColor={colors.textMuted}
            style={[styles.input, { flex: 1 }]}
            returnKeyType="done"
          />
          <Pressable onPress={addDraft} style={styles.addBtn} accessibilityLabel="Add ingredient">
            <Ionicons name="add" size={24} color={colors.white} />
          </Pressable>
        </View>
      </Card>

      <Card>
        <SectionTitle icon="flame">Or a dish you want to cook</SectionTitle>
        <TextInput
          value={dish}
          onChangeText={setDish}
          placeholder="e.g. Luwombo, katogo, rolex, malakwang"
          placeholderTextColor={colors.textMuted}
          style={styles.input}
        />
        <View style={styles.chips}>
          {MEAL_TYPES.map((m) => (
            <Chip key={m} label={m === 'any' ? 'Any meal' : m[0].toUpperCase() + m.slice(1)} selected={mealType === m} onPress={() => setMealType(m)} />
          ))}
        </View>
        {error && <ErrorBox message={error} />}
        <Button
          title="Suggest healthy meals"
          icon="sparkles"
          onPress={suggest}
          loading={loading}
          disabled={!ingredients.length && !dish.trim()}
        />
      </Card>

      {result && (
        <>
          {!!result.note && <Body muted>{result.note}</Body>}
          <View style={[styles.grid, isWide && { flexDirection: 'row', flexWrap: 'wrap' }]}>
            {result.ideas.map((idea) => (
              <Pressable
                key={idea.name}
                style={[isWide && { width: '48.5%' }]}
                onPress={() => {
                  setRecipe(idea);
                  router.push('/recipe');
                }}
              >
                <Card>
                  <Text style={styles.ideaName}>{idea.name}</Text>
                  <Body muted>{idea.description}</Body>
                  <PlateView slices={idea.plate} size={170} showLegend={false} />
                  <View style={styles.meta}>
                    <Meta icon="flame" text={`${Math.round(idea.kcalPerServing)} kcal`} />
                    <Meta icon="time" text={`${idea.prepMinutes} min`} />
                    <Meta icon="people" text={`${idea.servings} servings`} />
                  </View>
                  <Text style={styles.open}>View recipe & plating →</Text>
                </Card>
              </Pressable>
            ))}
          </View>
        </>
      )}
    </Screen>
  );
}

function Meta({ icon, text }: { icon: 'flame' | 'time' | 'people'; text: string }) {
  return (
    <View style={styles.metaItem}>
      <Ionicons name={icon} size={14} color={colors.textMuted} />
      <Text style={styles.metaText}>{text}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: space(2) },
  row: { flexDirection: 'row', gap: space(2), alignItems: 'center' },
  input: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    padding: space(3),
    fontSize: 15,
    color: colors.text,
    backgroundColor: colors.surface,
  },
  addBtn: { width: 48, height: 48, borderRadius: radius.md, backgroundColor: colors.primary, alignItems: 'center', justifyContent: 'center' },
  grid: { gap: space(4), justifyContent: 'space-between' },
  ideaName: { fontSize: 19, fontWeight: '800', color: colors.text },
  meta: { flexDirection: 'row', gap: space(4), justifyContent: 'center' },
  metaItem: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  metaText: { fontSize: 13, color: colors.textMuted, fontWeight: '600' },
  open: { color: colors.primary, fontWeight: '700', textAlign: 'center' },
});
