import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';

import { HomeScreen } from '../../screens/Home/HomeScreen';
import { MockAdoptionActivityScreen } from '../../screens/MockAdoption/MockAdoptionActivityScreen';
import { ArWalkScreen } from '../../screens/ArWalk/ArWalkScreen';
import { AdoptionApplicationScreen } from '../../screens/adoption/AdoptionApplicationScreen';
import { AdoptionApplicationHistoryScreen } from '../../screens/adoption/AdoptionApplicationHistoryScreen';
import { AdoptionApplicationListScreen } from '../../screens/adoption/AdoptionApplicationListScreen';
import { AdoptionApplicationReviewScreen } from '../../screens/adoption/AdoptionApplicationReviewScreen';
import { AdoptionDetailScreen } from '../../screens/adoption/AdoptionDetailScreen';
import { ShelterDetailScreen } from '../../screens/shelter/ShelterDetailScreen';
import { PetListScreen } from '../../screens/MyPage/PetListScreen';
import { PetDetailScreen } from '../../screens/MyPage/PetDetailScreen';
import type { RootStackParamList } from './types';

const Stack = createNativeStackNavigator<RootStackParamList>();

export function RootNavigator() {
  return (
    <NavigationContainer>
      <Stack.Navigator initialRouteName="Home" screenOptions={{ headerShown: false }}>
        <Stack.Screen name="Home" component={HomeScreen} />
        <Stack.Screen name="MockAdoptionActivity" component={MockAdoptionActivityScreen} />
        <Stack.Screen name="PetList" component={PetListScreen} />
        <Stack.Screen name="PetDetail" component={PetDetailScreen} />
        <Stack.Screen
          name="ArWalk"
          component={ArWalkScreen}
          options={{
            headerShown: true,
            title: 'AR 산책',
            headerTransparent: true,
            headerShadowVisible: false,
            headerTintColor: '#FFFFFF',
            headerStyle: { backgroundColor: 'transparent' },
            headerTitleStyle: { color: '#FFFFFF' },
          }}
        />
        <Stack.Screen name="AdoptionDetail" component={AdoptionDetailScreen} />
        <Stack.Screen name="AdoptionApplication" component={AdoptionApplicationScreen} />
        <Stack.Screen
          name="AdoptionApplicationHistory"
          component={AdoptionApplicationHistoryScreen}
        />
        <Stack.Screen name="AdoptionApplicationList" component={AdoptionApplicationListScreen} />
        <Stack.Screen
          name="AdoptionApplicationReview"
          component={AdoptionApplicationReviewScreen}
        />
        <Stack.Screen name="ShelterDetail" component={ShelterDetailScreen} />
      </Stack.Navigator>
    </NavigationContainer>
  );
}
