/**
 * Sample React Native App
 * https://github.com/facebook/react-native
 *
 * @format
 */

import { ActivityIndicator, StyleSheet, StatusBar, View, useColorScheme } from 'react-native';
import { useEffect } from 'react';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { PushPopup } from './PushPopup';
import { RootNavigator } from './navigation/RootNavigator';
import { startForegroundMessaging } from '../services/notifications/firebaseMessaging';
import { installMockAdoptionWalkExpiry } from '../services/simulation/walkLifecycle';
import { useAuthStore } from '../stores/authStore';

installMockAdoptionWalkExpiry();

function App() {
  const isDarkMode = useColorScheme() === 'dark';
  const initializeAuth = useAuthStore(state => state.initialize);
  const isInitializing = useAuthStore(state => state.isInitializing);

  useEffect(() => {
    initializeAuth();
    return startForegroundMessaging();
  }, [initializeAuth]);

  if (isInitializing) {
    return (
      <SafeAreaProvider>
        <View style={styles.loading}>
          <ActivityIndicator />
        </View>
      </SafeAreaProvider>
    );
  }

  return (
    <SafeAreaProvider>
      <StatusBar barStyle={isDarkMode ? 'light-content' : 'dark-content'} />
      <RootNavigator />
      <PushPopup />
    </SafeAreaProvider>
  );
}

export default App;

const styles = StyleSheet.create({
  loading: { alignItems: 'center', flex: 1, justifyContent: 'center' },
});
