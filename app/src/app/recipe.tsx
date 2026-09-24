import { router } from 'expo-router';
import { StyleSheet, Text, View } from 'react-native';
import { BulletList } from '@/components/MealWidgets';
import { PlateView } from '@/components/PlateView';
import { Body, Button, Card, Columns, H1, SectionTitle, Screen, Stat } from '@/components/ui';
import { useStore } from '@/lib/store';
import { colors, space } from '@/lib/theme';

export default function RecipeScreen() {
  const { recipe } = useStore();

  if (!recipe) {
    return (
      <Screen>
        <Card>
          <Body>No recipe selected.</Body>
          <Button title="Open Kitchen" icon="restaurant" onPress={() => router.replace('/kitchen')} />
        </Card>
      </Screen>
    );
  }

  const left = (
    <>
      <Card>
        <SectionTitle icon="pie-chart">How to plate it (per serving)</SectionTitle>
        <PlateView slices={recipe.plate} size={240} />
      </Card>
      <Card style={{ backgroundColor: colors.primarySoft, borderColor: '#BFE0CD' }}>
        <SectionTitle icon="heart">Why it's healthy</SectionTitle>
        <Body>{recipe.whyHealthy}</Body>
        {!!recipe.suitableFor && <Body muted>{recipe.suitableFor}</Body>}
      </Card>
    </>
  );

  const right = (
    <>
      <Card>
        <SectionTitle icon="basket">Ingredients</SectionTitle>
        <BulletList items={recipe.ingredientsUsed} icon="checkmark-circle" />
        {recipe.extraIngredientsNeeded.length > 0 && (
          <>
            <Text style={styles.sub}>You'll also need</Text>
            <BulletList items={recipe.extraIngredientsNeeded} icon="cart" color={colors.accent} />
          </>
        )}
      </Card>
      <Card>
        <SectionTitle icon="flame">Steps</SectionTitle>
        {recipe.steps.map((s, i) => (
          <View key={i} style={styles.step}>
            <View style={styles.num}>
              <Text style={styles.numText}>{i + 1}</Text>
            </View>
            <Body style={{ flex: 1 }}>{s}</Body>
          </View>
        ))}
      </Card>
    </>
  );

  return (
    <Screen>
      <H1>{recipe.name}</H1>
      <Body muted>{recipe.description}</Body>
      <Card style={{ flexDirection: 'row' }}>
        <Stat label="Per serving" value={Math.round(recipe.kcalPerServing)} unit="kcal" color={colors.accent} />
        <Stat label="Time" value={recipe.prepMinutes} unit="min" />
        <Stat label="Serves" value={recipe.servings} />
      </Card>
      <Columns left={left} right={right} />
    </Screen>
  );
}

const styles = StyleSheet.create({
  sub: { fontSize: 14, fontWeight: '700', color: colors.textMuted, marginTop: space(2) },
  step: { flexDirection: 'row', gap: space(3), alignItems: 'flex-start' },
  num: { width: 28, height: 28, borderRadius: 14, backgroundColor: colors.primary, alignItems: 'center', justifyContent: 'center' },
  numText: { color: colors.white, fontWeight: '800' },
});
