import ReactTestRenderer from 'react-test-renderer';
import Geolocation, { type GeolocationResponse } from '@react-native-community/geolocation';
import { PermissionsAndroid } from 'react-native';
import { useWalkRoute } from './useWalkRoute';

jest.mock('@react-native-community/geolocation', () => ({
  watchPosition: jest.fn(() => 1),
  clearWatch: jest.fn(),
  getCurrentPosition: jest.fn(),
}));

test('continuous fixes preserve distance across a long stop and stop watching on exit', async () => {
  jest.spyOn(PermissionsAndroid, 'request').mockResolvedValue(PermissionsAndroid.RESULTS.GRANTED);
  let route!: ReturnType<typeof useWalkRoute>;
  function Harness() {
    route = useWalkRoute(true);
    return null;
  }
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(async () => {
    renderer = ReactTestRenderer.create(<Harness />);
  });
  const receive = jest.mocked(Geolocation.watchPosition).mock.calls[0][0];
  const start = Date.now();
  const now = jest.spyOn(Date, 'now');
  const emit = async (seconds: number, latitude = 37) => {
    now.mockReturnValue(start + seconds * 1000);
    await ReactTestRenderer.act(() => {
      receive({
        timestamp: start + seconds * 1000,
        coords: { latitude, longitude: 127, accuracy: 10 },
      } as GeolocationResponse);
    });
  };
  try {
    await emit(0);
    for (let seconds = 5; seconds <= 45; seconds += 5) await emit(seconds);
    await emit(50, 37.00003);
    expect(route.meters).toBeGreaterThan(3);
    expect(route.segments).toHaveLength(1);
    expect(route.segments[0]).toHaveLength(2);
    await emit(55, 38);
    expect(route.segments[0]).toHaveLength(2);
  } finally {
    await ReactTestRenderer.act(() => renderer.unmount());
    expect(Geolocation.clearWatch).toHaveBeenCalledWith(1);
    jest.restoreAllMocks();
  }
});
