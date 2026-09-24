import { Ionicons } from '@expo/vector-icons';
import type { ComponentProps, ReactNode } from 'react';
import {
  ActivityIndicator,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
  useWindowDimensions,
  type StyleProp,
  type TextStyle,
  type ViewStyle,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { colors, radius, shadow, space } from '@/lib/theme';

export type IconName = ComponentProps<typeof Ionicons>['name'];

export const WIDE_BREAKPOINT = 900;

/** Layout helpers: phones get one column, tablets and PCs get two. */
export function useLayout() {
  const { width } = useWindowDimensions();
  return { width, isWide: width >= WIDE_BREAKPOINT, gutter: width >= 600 ? space(6) : space(4) };
}

export function Screen({ children, scroll = true }: { children: ReactNode; scroll?: boolean }) {
  const { gutter } = useLayout();
  const insets = useSafeAreaInsets();
  const inner = <View style={[styles.inner, { paddingHorizontal: gutter }]}>{children}</View>;
  if (!scroll) return <View style={[styles.screen, { paddingTop: insets.top }]}>{inner}</View>;
  return (
    <ScrollView
      style={styles.screen}
      contentContainerStyle={{ paddingTop: insets.top + space(4), paddingBottom: space(12) }}
      keyboardShouldPersistTaps="handled"
    >
      {inner}
    </ScrollView>
  );
}

/** Two columns on wide screens, stacked on phones. */
export function Columns({ left, right }: { left: ReactNode; right: ReactNode }) {
  const { isWide } = useLayout();
  if (!isWide) {
    return (
      <View style={{ gap: space(4) }}>
        {left}
        {right}
      </View>
    );
  }
  return (
    <View style={{ flexDirection: 'row', gap: space(6), alignItems: 'flex-start' }}>
      <View style={{ flex: 1, gap: space(4) }}>{left}</View>
      <View style={{ flex: 1, gap: space(4) }}>{right}</View>
    </View>
  );
}

export function Card({ children, style }: { children: ReactNode; style?: StyleProp<ViewStyle> }) {
  return <View style={[styles.card, style]}>{children}</View>;
}

export function H1({ children, style }: { children: ReactNode; style?: StyleProp<TextStyle> }) {
  return <Text style={[styles.h1, style]}>{children}</Text>;
}

export function H2({ children, style }: { children: ReactNode; style?: StyleProp<TextStyle> }) {
  return <Text style={[styles.h2, style]}>{children}</Text>;
}

export function Body({ children, muted, style }: { children: ReactNode; muted?: boolean; style?: StyleProp<TextStyle> }) {
  return <Text style={[styles.body, muted && { color: colors.textMuted }, style]}>{children}</Text>;
}

export function SectionTitle({ icon, children }: { icon?: IconName; children: ReactNode }) {
  return (
    <View style={styles.sectionTitle}>
      {icon && <Ionicons name={icon} size={18} color={colors.primary} />}
      <Text style={styles.h3}>{children}</Text>
    </View>
  );
}

type ButtonVariant = 'primary' | 'accent' | 'outline' | 'ghost';

export function Button({
  title,
  onPress,
  icon,
  variant = 'primary',
  loading,
  disabled,
  style,
}: {
  title: string;
  onPress: () => void;
  icon?: IconName;
  variant?: ButtonVariant;
  loading?: boolean;
  disabled?: boolean;
  style?: StyleProp<ViewStyle>;
}) {
  const bg = { primary: colors.primary, accent: colors.accent, outline: colors.surface, ghost: 'transparent' }[variant];
  const fg = variant === 'primary' || variant === 'accent' ? colors.white : colors.primary;
  const inactive = disabled || loading;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={title}
      onPress={onPress}
      disabled={inactive}
      style={({ pressed }) => [
        styles.button,
        { backgroundColor: bg, opacity: inactive ? 0.55 : pressed ? 0.85 : 1 },
        variant === 'outline' && { borderWidth: 1.5, borderColor: colors.primary },
        style,
      ]}
    >
      {loading ? <ActivityIndicator color={fg} /> : icon && <Ionicons name={icon} size={20} color={fg} />}
      <Text style={[styles.buttonText, { color: fg }]}>{title}</Text>
    </Pressable>
  );
}

export function Chip({
  label,
  selected,
  onPress,
  color = colors.primary,
}: {
  label: string;
  selected?: boolean;
  onPress?: () => void;
  color?: string;
}) {
  return (
    <Pressable
      onPress={onPress}
      disabled={!onPress}
      accessibilityRole={onPress ? 'button' : 'text'}
      accessibilityState={{ selected }}
      style={[
        styles.chip,
        selected ? { backgroundColor: color, borderColor: color } : { backgroundColor: colors.surface, borderColor: colors.border },
      ]}
    >
      {selected && <Ionicons name="checkmark" size={14} color={colors.white} />}
      <Text style={[styles.chipText, { color: selected ? colors.white : colors.text }]}>{label}</Text>
    </Pressable>
  );
}

export function ErrorBox({ message }: { message: string }) {
  return (
    <View style={styles.error}>
      <Ionicons name="warning" size={18} color={colors.danger} />
      <Text style={[styles.body, { color: colors.danger, flex: 1 }]}>{message}</Text>
    </View>
  );
}

export function Stat({ label, value, unit, color = colors.text }: { label: string; value: string | number; unit?: string; color?: string }) {
  return (
    <View style={styles.stat}>
      <Text style={[styles.statValue, { color }]}>
        {value}
        {unit ? <Text style={styles.statUnit}> {unit}</Text> : null}
      </Text>
      <Text style={styles.statLabel}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg },
  inner: { width: '100%', maxWidth: 1120, alignSelf: 'center', gap: space(4) },
  card: {
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    padding: space(4),
    gap: space(3),
    borderWidth: 1,
    borderColor: colors.border,
    ...shadow,
  },
  h1: { fontSize: 28, fontWeight: '800', color: colors.text, letterSpacing: -0.5 },
  h2: { fontSize: 20, fontWeight: '700', color: colors.text },
  h3: { fontSize: 16, fontWeight: '700', color: colors.text },
  body: { fontSize: 15, lineHeight: 22, color: colors.text },
  sectionTitle: { flexDirection: 'row', alignItems: 'center', gap: space(2) },
  button: {
    minHeight: 50,
    borderRadius: radius.md,
    paddingHorizontal: space(5),
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: space(2),
  },
  buttonText: { fontSize: 16, fontWeight: '700' },
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    paddingHorizontal: space(3),
    paddingVertical: space(2),
    borderRadius: radius.pill,
    borderWidth: 1,
  },
  chipText: { fontSize: 14, fontWeight: '600' },
  error: {
    flexDirection: 'row',
    gap: space(2),
    padding: space(3),
    borderRadius: radius.md,
    backgroundColor: colors.dangerSoft,
    alignItems: 'flex-start',
  },
  stat: { flex: 1, minWidth: 70, alignItems: 'center', gap: 2 },
  statValue: { fontSize: 20, fontWeight: '800' },
  statUnit: { fontSize: 12, fontWeight: '600', color: colors.textMuted },
  statLabel: { fontSize: 12, color: colors.textMuted, fontWeight: '600' },
});
