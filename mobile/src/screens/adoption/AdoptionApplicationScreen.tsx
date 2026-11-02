import { StyleSheet, Text, View } from 'react-native';

export function AdoptionApplicationScreen() {
  return (
    <View style={styles.container}>
      <Text>입양 신청</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { alignItems: 'center', flex: 1, justifyContent: 'center' },
});
