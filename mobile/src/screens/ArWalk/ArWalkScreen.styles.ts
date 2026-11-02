import { StyleSheet } from 'react-native';

export const styles = StyleSheet.create({
  screen: {
    backgroundColor: '#111111',
    flex: 1,
  },
  camera: {
    ...StyleSheet.absoluteFill,
  },
  overlay: {
    ...StyleSheet.absoluteFill,
  },
  message: {
    alignItems: 'center',
    flex: 1,
    justifyContent: 'center',
    paddingHorizontal: 28,
  },
  messageText: {
    color: '#FFFFFF',
    fontSize: 16,
    lineHeight: 24,
    textAlign: 'center',
  },
  status: {
    backgroundColor: 'rgba(17, 17, 17, 0.55)',
    borderRadius: 16,
    left: 20,
    paddingHorizontal: 14,
    paddingVertical: 10,
    position: 'absolute',
    right: 20,
  },
  statusLabel: {
    color: '#FFFFFF',
    fontSize: 15,
    fontWeight: '700',
  },
  statusHint: {
    color: '#F4F4F4',
    fontSize: 13,
    lineHeight: 18,
    marginTop: 4,
  },
  placeButton: {
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    borderRadius: 28,
    left: 32,
    minHeight: 56,
    justifyContent: 'center',
    paddingHorizontal: 20,
    position: 'absolute',
    right: 32,
  },
  placeButtonDisabled: {
    backgroundColor: '#888888',
  },
  placeButtonText: {
    color: '#111111',
    fontSize: 16,
    fontWeight: '700',
  },
});
