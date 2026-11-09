import { StyleSheet } from 'react-native';

export const styles = StyleSheet.create({
  container: {
    width: 60,
    height: 60,
    borderRadius: 30,
    alignItems: 'center',
    justifyContent: 'center',
  },
  idle: { backgroundColor: 'rgba(0, 0, 0, 0.5)' },
  icon: { width: 30, height: 30, resizeMode: 'contain', tintColor: '#FFFFFF' },
  glow: { position: 'absolute', top: 0, left: 0 },
});
