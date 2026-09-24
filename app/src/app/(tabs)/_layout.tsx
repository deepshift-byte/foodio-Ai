import { Ionicons } from '@expo/vector-icons';
import { Tabs } from 'expo-router';
import type { ComponentProps } from 'react';
import type { ColorValue } from 'react-native';
import { colors } from '@/lib/theme';

type IconName = ComponentProps<typeof Ionicons>['name'];

const icon =
  (name: IconName) =>
  ({ color, size }: { color: ColorValue; size: number }) => <Ionicons name={name} size={size} color={color} />;

export default function TabsLayout() {
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: colors.primary,
        tabBarInactiveTintColor: colors.textMuted,
        tabBarStyle: { backgroundColor: colors.surface, borderTopColor: colors.border },
        tabBarLabelStyle: { fontWeight: '600' },
      }}
    >
      <Tabs.Screen name="index" options={{ title: 'Scan', tabBarIcon: icon('camera') }} />
      <Tabs.Screen name="kitchen" options={{ title: 'Kitchen', tabBarIcon: icon('restaurant') }} />
      <Tabs.Screen name="foods" options={{ title: 'Foods', tabBarIcon: icon('nutrition') }} />
      <Tabs.Screen name="progress" options={{ title: 'Progress', tabBarIcon: icon('trending-down') }} />
      <Tabs.Screen name="profile" options={{ title: 'Health', tabBarIcon: icon('heart') }} />
    </Tabs>
  );
}
