import { StyleSheet } from 'react-native';

export const styles = StyleSheet.create({
  screen: {
    backgroundColor: '#111111',
    flex: 1,
  },
  camera: {
    ...StyleSheet.absoluteFill,
  },
  loadingOverlay: {
    ...StyleSheet.absoluteFill,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 28,
    gap: 16,
  },
  loadingText: {
    color: '#FFFFFF',
    fontSize: 18,
    fontWeight: '600',
    width: 180,
    textAlign: 'center',
  },
  walkTimer: {
    position: 'absolute',
    left: 0,
    right: 0,
    alignItems: 'center',
  },
  walkTimerText: {
    color: '#FFFFFF',
    fontSize: 20,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
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
  toggleTrack: {
    width: 48,
    height: 28,
    borderRadius: 14,
    backgroundColor: '#666666',
    justifyContent: 'center',
    paddingHorizontal: 3,
  },
  toggleTrackOn: { backgroundColor: '#36875C' },
  toggleThumb: { width: 22, height: 22, borderRadius: 11, backgroundColor: '#FFFFFF' },
  toggleThumbOn: { alignSelf: 'flex-end' },
  displayControls: {
    flexDirection: 'column',
    alignItems: 'flex-end',
    position: 'absolute',
    right: 20,
    zIndex: 10,
    elevation: 10,
    gap: 8,
  },
  areaToggle: {
    alignItems: 'center',
    flexDirection: 'row',
    minHeight: 44,
  },
  areaToggleLabel: {
    color: '#FFFFFF',
    fontSize: 9,
    fontWeight: '600',
    marginRight: 8,
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
