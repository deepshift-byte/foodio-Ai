import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { StoreProvider } from '@/lib/store';
import { colors } from '@/lib/theme';

export default function RootLayout() {
  return (
    <SafeAreaProvider>
      <StoreProvider>
        <StatusBar style="dark" />
        <Stack
          screenOptions={{
            headerStyle: { backgroundColor: colors.bg },
            headerTintColor: colors.primary,
            headerTitleStyle: { color: colors.text, fontWeight: '700' },
            headerShadowVisible: false,
            contentStyle: { backgroundColor: colors.bg },
          }}
        >
          <Stack.Screen name="(tabs)" options={{ headerShown: false, title: 'Foodio' }} />
          <Stack.Screen name="analysis" options={{ title: 'Meal analysis' }} />
          <Stack.Screen name="recipe" options={{ title: 'Recipe' }} />
          <Stack.Screen name="food/[id]" options={{ title: 'Food' }} />
        </Stack>
      </StoreProvider>
    </SafeAreaProvider>
  );
}
