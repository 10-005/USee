import ReactTestRenderer from 'react-test-renderer';
import Geolocation, { type GeolocationResponse } from '@react-native-community/geolocation';
import { PermissionsAndroid } from 'react-native';
import { useWalkRoute } from './useWalkRoute';

jest.mock('@react-native-community/geolocation', () => ({
  watchPosition: jest.fn(() => 1),
  clearWatch: jest.fn(),
  getCurrentPosition: jest.fn(),
  setRNConfiguration: jest.fn(),
}));

test('continuous fixes preserve distance across a long stop and stop watching on exit', async () => {
  jest
    .spyOn(PermissionsAndroid, 'requestMultiple')
    .mockImplementation(
      async permissions =>
        Object.fromEntries(permissions.map(permission => [permission, 'granted'])) as Awaited<
          ReturnType<typeof PermissionsAndroid.requestMultiple>
        >,
    );
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
    const seed = jest.mocked(Geolocation.getCurrentPosition).mock.calls[0][0];
    await ReactTestRenderer.act(() => {
      seed({
        timestamp: start - 1000,
        coords: { latitude: 36, longitude: 127, accuracy: 120 },
      } as GeolocationResponse);
    });
    expect(route.mapPosition?.latitude).toBe(36);
    expect(route.segments).toHaveLength(0);
    expect(route.meters).toBe(0);
    await emit(0);
    for (let seconds = 5; seconds <= 45; seconds += 5) await emit(seconds);
    await emit(50, 37.00003);
    expect(route.meters).toBeGreaterThan(3);
    expect(route.segments).toHaveLength(1);
    expect(route.segments[0]).toHaveLength(2);
    await emit(55, 38);
    expect(route.segments[0]).toHaveLength(2);
    const gpsMeters = route.meters;
    await ReactTestRenderer.act(() => {
      route.onPose({ segment: 1, time: 1000, x: 0, z: 0, alignment: Math.PI / 2 });
      route.onPose({ segment: 1, time: 1250, x: 0, z: -0.5, alignment: Math.PI / 2 });
    });
    expect(route.meters).toBeCloseTo(gpsMeters + 0.5);
    expect(route.mapPosition!.longitude).toBeGreaterThan(127);
    await emit(55.5, 37.00004);
    expect(route.meters).toBeCloseTo(gpsMeters + 0.5);
    await ReactTestRenderer.act(() => {
      route.onPose({ segment: 2, time: 2000, x: 20, z: 20 });
    });
    expect(route.meters).toBeCloseTo(gpsMeters + 0.5);
  } finally {
    await ReactTestRenderer.act(() => renderer.unmount());
    expect(Geolocation.clearWatch).toHaveBeenCalledWith(1);
    jest.restoreAllMocks();
  }
});
