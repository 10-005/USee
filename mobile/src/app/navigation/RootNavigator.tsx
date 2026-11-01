import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';

import { ArWalkScreen } from '../../screens/ArWalk/ArWalkScreen';
import { HomeScreen } from '../../screens/Home/HomeScreen';
import type { RootStackParamList } from './types';

const Stack = createNativeStackNavigator<RootStackParamList>();

export function RootNavigator() {
  return (
    <NavigationContainer>
      <Stack.Navigator initialRouteName="Home">
        <Stack.Screen name="Home" component={HomeScreen} options={{ title: 'USee' }} />
        <Stack.Screen
          name="ArWalk"
          component={ArWalkScreen}
          options={{
            title: 'AR 산책',
            headerTransparent: true,
            headerShadowVisible: false,
            headerTintColor: '#FFFFFF',
            headerStyle: { backgroundColor: 'transparent' },
            headerTitleStyle: { color: '#FFFFFF' },
          }}
        />
      </Stack.Navigator>
    </NavigationContainer>
  );
}
