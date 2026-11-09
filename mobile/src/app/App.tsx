/**
 * Sample React Native App
 * https://github.com/facebook/react-native
 *
 * @format
 */

import { StatusBar, useColorScheme } from 'react-native';
import { useEffect } from 'react';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { RootNavigator } from './navigation/RootNavigator';
import { startForegroundMessaging } from '../services/notifications/firebaseMessaging';
import { installMockAdoptionWalkExpiry } from '../services/simulation/walkLifecycle';

installMockAdoptionWalkExpiry();

function App() {
  const isDarkMode = useColorScheme() === 'dark';
  useEffect(() => startForegroundMessaging(), []);

  return (
    <SafeAreaProvider>
      <StatusBar barStyle={isDarkMode ? 'light-content' : 'dark-content'} />
      <RootNavigator />
    </SafeAreaProvider>
  );
}

export default App;
