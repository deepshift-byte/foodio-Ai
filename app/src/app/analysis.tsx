import { Ionicons } from '@expo/vector-icons';
import { router } from 'expo-router';
import type { ClarifyingQuestion } from '@shared/types';
import { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Image, StyleSheet, Text, TextInput, View } from 'react-native';
import { AnalysisView } from '@/components/AnalysisView';
import { Body, Button, Card, Chip, ErrorBox, SectionTitle, Screen } from '@/components/ui';
import { analyzeMeal } from '@/lib/api';
import { useStore } from '@/lib/store';
import { colors, radius, space } from '@/lib/theme';

const LOADING_LINES = [
  'Looking at your plate…',
  'Recognising the foods…',
  'Estimating portions…',
  'Checking against your health profile…',
];

export default function AnalysisScreen() {
  const { scan, setScan, profile, hasProfile, addOrUpdateHistory } = useStore();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [line, setLine] = useState(0);
  const started = useRef(false);

  const run = useCallback(
    async (answers: { question: string; answer: string }[]) => {
      if (!scan) return;
      setLoading(true);
      setError(null);
      try {
        const analysis = await analyzeMeal({
          imageBase64: scan.image.base64,
          profile: hasProfile ? profile : undefined,
          note: scan.note,
          answers: answers.length ? answers : undefined,
        });
        const historyId = scan.historyId ?? `${Date.now()}`;
        setScan({ ...scan, analysis, answers, historyId });
        addOrUpdateHistory({ id: historyId, thumbnail: scan.image.thumbnail, analysis });
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Analysis failed.');
      } finally {
        setLoading(false);
      }
    },
    [scan, setScan, profile, hasProfile, addOrUpdateHistory],
  );

  useEffect(() => {
    if (scan && !scan.analysis && !started.current) {
      started.current = true;
      run([]);
    }
  }, [scan, run]);

  useEffect(() => {
    if (!loading) return;
    const t = setInterval(() => setLine((l) => (l + 1) % LOADING_LINES.length), 1800);
    return () => clearInterval(t);
  }, [loading]);

  if (!scan) {
    return (
      <Screen>
        <Card>
          <Body>No meal photo yet.</Body>
          <Button title="Scan a meal" icon="camera" onPress={() => router.replace('/')} />
        </Card>
      </Screen>
    );
  }

  if (!scan.analysis) {
    return (
      <Screen>
        <Card style={{ padding: 0, overflow: 'hidden' }}>
          <Image source={{ uri: scan.image.uri }} style={styles.photo} resizeMode="cover" />
          <View style={styles.loadingBox}>
            {loading && <ActivityIndicator size="large" color={colors.primary} />}
            {loading && <Text style={styles.loadingText}>{LOADING_LINES[line]}</Text>}
            {error && <ErrorBox message={error} />}
            {error && <Button title="Try again" icon="refresh" onPress={() => run(scan.answers)} />}
          </View>
        </Card>
      </Screen>
    );
  }

  return (
    <Screen>
      <AnalysisView
        analysis={scan.analysis}
        imageUri={scan.image.uri}
        questionsSlot={
          scan.analysis.questions.length > 0 ? (
            <QuestionsCard
              key={scan.analysis.analyzedAt}
              questions={scan.analysis.questions}
              loading={loading}
              error={error}
              onSubmit={(newAnswers) => run([...scan.answers, ...newAnswers])}
            />
          ) : null
        }
      />
    </Screen>
  );
}

function QuestionsCard({
  questions,
  loading,
  error,
  onSubmit,
}: {
  questions: ClarifyingQuestion[];
  loading: boolean;
  error: string | null;
  onSubmit: (answers: { question: string; answer: string }[]) => void;
}) {
  const [picked, setPicked] = useState<Record<string, string>>({});
  const [extra, setExtra] = useState('');

  const answers = questions.filter((q) => picked[q.id]).map((q) => ({ question: q.question, answer: picked[q.id] }));
  if (extra.trim()) answers.push({ question: 'Anything else about the meal?', answer: extra.trim() });

  return (
    <Card style={styles.questions}>
      <SectionTitle icon="chatbubbles">Foodio has a few questions</SectionTitle>
      <Body muted>Your answers help Foodio get the portions and advice right.</Body>
      {questions.map((q) => (
        <View key={q.id} style={{ gap: space(2) }}>
          <Text style={styles.q}>{q.question}</Text>
          <View style={styles.chips}>
            {q.options.map((o) => (
              <Chip key={o} label={o} selected={picked[q.id] === o} onPress={() => setPicked((p) => ({ ...p, [q.id]: o }))} />
            ))}
          </View>
        </View>
      ))}
      <TextInput
        value={extra}
        onChangeText={setExtra}
        placeholder="Anything else? (optional)"
        placeholderTextColor={colors.textMuted}
        style={styles.input}
        maxLength={300}
      />
      {error && <ErrorBox message={error} />}
      <Button
        title="Update analysis"
        icon="refresh"
        onPress={() => onSubmit(answers)}
        loading={loading}
        disabled={answers.length === 0}
      />
      {loading && (
        <View style={styles.updating}>
          <Ionicons name="sparkles" size={14} color={colors.primary} />
          <Body muted style={{ fontSize: 13 }}>
            Re-analysing with your answers…
          </Body>
        </View>
      )}
    </Card>
  );
}

const styles = StyleSheet.create({
  photo: { width: '100%', height: 320, backgroundColor: colors.surfaceAlt },
  loadingBox: { padding: space(6), alignItems: 'center', gap: space(3) },
  loadingText: { fontSize: 17, fontWeight: '700', color: colors.text },
  questions: { borderColor: colors.accent, borderWidth: 1.5 },
  q: { fontSize: 15, fontWeight: '700', color: colors.text },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: space(2) },
  input: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md,
    padding: space(3),
    fontSize: 15,
    color: colors.text,
    backgroundColor: colors.surface,
  },
  updating: { flexDirection: 'row', gap: space(2), alignItems: 'center', justifyContent: 'center' },
});
