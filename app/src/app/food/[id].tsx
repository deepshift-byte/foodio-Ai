import { Stack, router, useLocalSearchParams } from 'expo-router';
import { FOOD_BY_ID } from '@shared/foods';
import { GROUP_LABELS, nutrientsFor } from '@shared/nutrition';
import { StyleSheet, Text, View } from 'react-native';
import { NutrientBars } from '@/components/MealWidgets';
import { Body, Button, Card, Chip, H1, SectionTitle, Screen, Stat } from '@/components/ui';
import { colors, groupColors, space } from '@/lib/theme';

export default function FoodScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const food = FOOD_BY_ID[id];

  if (!food) {
    return (
      <Screen>
        <Body>Food not found.</Body>
      </Screen>
    );
  }

  const serving = nutrientsFor(food.id, food.typicalServingG)!;

  return (
    <Screen>
      <Stack.Screen options={{ title: food.name.split(' (')[0] }} />
      <View style={{ gap: space(1) }}>
        <Text style={[styles.group, { color: groupColors[food.group] }]}>{GROUP_LABELS[food.group]} · {food.origin}</Text>
        <H1>{food.name}</H1>
        <Body muted>Also called: {food.localNames.join(', ')}</Body>
      </View>
      <Card>
        <Body>{food.description}</Body>
        <View style={{ flexDirection: 'row' }}>
          <Stat label={`per ${food.typicalServingG} g serving`} value={serving.kcal} unit="kcal" color={colors.accent} />
          <Stat label="per 100 g" value={food.per100g.kcal} unit="kcal" />
          <Stat
            label="Glycaemic index"
            value={food.gi.toUpperCase()}
            color={food.gi === 'low' ? colors.primary : food.gi === 'medium' ? colors.warning : colors.danger}
          />
        </View>
      </Card>
      <Card>
        <SectionTitle icon="stats-chart">Nutrition per serving</SectionTitle>
        <NutrientBars totals={serving} />
      </Card>
      <Card style={{ backgroundColor: colors.primarySoft, borderColor: '#BFE0CD' }}>
        <SectionTitle icon="bulb">Healthier tip</SectionTitle>
        <Body>{food.healthierTip}</Body>
      </Card>
      {food.tags.length > 0 && (
        <View style={styles.tags}>
          {food.tags.map((t) => (
            <Chip key={t} label={t.replace(/-/g, ' ')} />
          ))}
        </View>
      )}
      <Button
        title={`Healthy recipes with ${food.name.split(' (')[0]}`}
        icon="restaurant"
        variant="outline"
        onPress={() => router.push({ pathname: '/kitchen', params: { dish: food.name } })}
      />
      <Body muted style={{ fontSize: 12 }}>
        Values are approximate for the food as typically served in Uganda.
      </Body>
    </Screen>
  );
}

const styles = StyleSheet.create({
  group: { fontSize: 13, fontWeight: '800', textTransform: 'uppercase', letterSpacing: 0.5 },
  tags: { flexDirection: 'row', flexWrap: 'wrap', gap: space(2) },
});
