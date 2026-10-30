import { StyleSheet, Text, View } from 'react-native';

export function HomeScreen() {
  return (
    <View style={styles.container}>
      <Text>USee</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    flex: 1,
    justifyContent: 'center',
  },
});
