import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import type { RootStackParamList } from '../../app/navigation/types';

type MockAdoptionNavigation = NativeStackNavigationProp<RootStackParamList, 'Home'>;

export function MockAdoptionScreen() {
  const navigation = useNavigation<MockAdoptionNavigation>();

  return (
    <View style={styles.container}>
      <Text>USee</Text>
      <Pressable
        accessibilityLabel="AR 산책"
        accessibilityRole="button"
        onPress={() => navigation.navigate('ArWalk')}
        style={styles.button}
      >
        <Text style={styles.buttonLabel}>AR 산책</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    flex: 1,
    gap: 24,
    justifyContent: 'center',
  },
  button: {
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  buttonLabel: {
    fontSize: 16,
  },
});
