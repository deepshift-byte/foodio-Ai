import { Ionicons } from '@expo/vector-icons';
import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { Image, Pressable, StyleSheet, Text, View } from 'react-native';
import { AnalysisView } from '@/components/AnalysisView';
import { Body, Button, Card, H1, Screen } from '@/components/ui';
import { useStore } from '@/lib/store';
import { colors, radius, space, verdictStyle } from '@/lib/theme';

export default function HistoryScreen() {
  const { history, removeHistory } = useStore();
  const params = useLocalSearchParams<{ open?: string }>();
  const [openId, setOpenId] = useState<string | null>(null);

  useEffect(() => {
    if (params.open) setOpenId(params.open);
  }, [params.open]);

  const open = history.find((h) => h.id === openId);
  if (open) {
    return (
      <Screen>
        <Button title="Back to history" icon="arrow-back" variant="ghost" onPress={() => { setOpenId(null); router.setParams({ open: '' }); }} style={{ alignSelf: 'flex-start' }} />
        <AnalysisView analysis={open.analysis} imageUri={open.thumbnail} />
      </Screen>
    );
  }

  const today = new Date().toDateString();
  const todays = history.filter((h) => new Date(h.analysis.analyzedAt).toDateString() === today);
  const todayKcal = todays.reduce((s, h) => s + h.analysis.totals.kcal, 0);

  return (
    <Screen>
      <H1>Meal history</H1>
      <Card style={styles.today}>
        <Ionicons name="sunny" size={28} color={colors.accent} />
        <View style={{ flex: 1 }}>
          <Text style={styles.todayTitle}>Today: {todayKcal} kcal from {todays.length} meal{todays.length === 1 ? '' : 's'}</Text>
          <Body muted>Only meals you've scanned are counted.</Body>
        </View>
      </Card>
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
              <Image source={{ uri: h.thumbnail }} style={styles.thumb} />
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

const styles = StyleSheet.create({
  today: { flexDirection: 'row', alignItems: 'center', gap: space(3), backgroundColor: colors.accentSoft, borderColor: '#F6C99A' },
  todayTitle: { fontSize: 16, fontWeight: '800', color: colors.text },
  row: { flexDirection: 'row', alignItems: 'center', gap: space(3) },
  thumb: { width: 72, height: 72, borderRadius: radius.md, backgroundColor: colors.surfaceAlt },
  name: { fontSize: 16, fontWeight: '700', color: colors.text },
  badge: { alignSelf: 'flex-start', fontSize: 12, fontWeight: '700', paddingHorizontal: 8, paddingVertical: 2, borderRadius: 999, overflow: 'hidden' },
});
