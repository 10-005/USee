import { useEffect, useRef, useState } from 'react';
import { AppState, PermissionsAndroid, Platform } from 'react-native';
import Geolocation, { type GeolocationResponse } from '@react-native-community/geolocation';

type Point = { latitude: number; longitude: number; timestamp: number };

export function useWalkRoute(enabled: boolean) {
  const [segments, setSegments] = useState<Point[][]>([]);
  const [meters, setMeters] = useState(0);
  const [mapPosition, setMapPosition] = useState<Point | null>(null);
  const [message, setMessage] = useState('강아지를 인식하면 이동 경로를 기록합니다.');
  const previous = useRef<Point | null>(null);
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    if (!enabled) return;
    let disposed = false;
    let watch: number | null = null;
    let generation = 0;
    let lastFixAt = 0;
    const stop = () => {
      generation++;
      if (watch !== null) Geolocation.clearWatch(watch);
      watch = null;
      previous.current = null;
      lastFixAt = 0;
    };
    const start = async () => {
      stop();
      const current = generation;
      let received = 0;
      const report = (
        source: string,
        reason: string,
        accuracy: number,
        age: number,
        distance = 0,
      ) =>
        console.info(
          `[WalkRoute] source=${source} count=${received} result=${reason} accuracyM=${Math.round(
            accuracy,
          )} ageMs=${Math.round(age)} deltaM=${distance.toFixed(1)}`,
        );
      console.info('[WalkRoute] starting');
      setMessage('GPS 위치를 확인하고 있습니다.');
      try {
        let precise = true;
        if (Platform.OS === 'android') {
          const { ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION } = PermissionsAndroid.PERMISSIONS;
          const permissions = await PermissionsAndroid.requestMultiple([
            ACCESS_COARSE_LOCATION,
            ACCESS_FINE_LOCATION,
          ]);
          if (disposed || current !== generation) return;
          precise = permissions[ACCESS_FINE_LOCATION] === PermissionsAndroid.RESULTS.GRANTED;
          if (
            !precise &&
            permissions[ACCESS_COARSE_LOCATION] !== PermissionsAndroid.RESULTS.GRANTED
          ) {
            setMessage('이동 거리와 경로를 기록하려면 위치 권한이 필요합니다.');
            return;
          }
          Geolocation.setRNConfiguration({
            locationProvider: 'playServices',
            skipPermissionRequests: false,
          });
        }
        if (disposed || current !== generation) return;
        const onPosition = (source: string, { coords, timestamp }: GeolocationResponse) => {
          if (disposed || current !== generation) return;
          received++;
          const age = Date.now() - timestamp;
          if (
            !Number.isFinite(coords.latitude) ||
            !Number.isFinite(coords.longitude) ||
            !Number.isFinite(coords.accuracy) ||
            !Number.isFinite(timestamp) ||
            Math.abs(coords.latitude) > 90 ||
            Math.abs(coords.longitude) > 180 ||
            coords.accuracy < 0 ||
            Date.now() - timestamp > 120000
          ) {
            report(source, 'invalid-or-expired', coords.accuracy, age);
            setMessage('GPS 정확도가 낮아 위치를 기다리고 있습니다.');
            return;
          }
          const point = { latitude: coords.latitude, longitude: coords.longitude, timestamp };
          if (!precise || coords.accuracy > 25 || Date.now() - timestamp > 15000) {
            report(source, 'approximate-or-stale', coords.accuracy, age);
            if (lastFixAt === 0 || Date.now() - lastFixAt > 30000) {
              setMapPosition(old => (old && old.timestamp > timestamp ? old : point));
              setMessage(
                precise
                  ? '대략적인 위치입니다. 정확한 위치를 기다리고 있어요.'
                  : '거리 기록에는 정확한 위치 권한이 필요합니다.',
              );
            }
            return;
          }
          const last = previous.current;
          if (timestamp <= lastFixAt) {
            report(source, 'duplicate', coords.accuracy, age);
            return;
          }
          const disconnected = lastFixAt > 0 && timestamp - lastFixAt > 30000;
          lastFixAt = timestamp;
          setMessage('');
          if (!last || disconnected) {
            report(source, disconnected ? 'gap' : 'start', coords.accuracy, age);
            setMapPosition(point);
            previous.current = point;
            setSegments(value => [...value, [point]]);
            return;
          }
          const seconds = (timestamp - last.timestamp) / 1000;
          if (seconds <= 0) return;
          const radians = Math.PI / 180;
          const a =
            Math.sin(((point.latitude - last.latitude) * radians) / 2) ** 2 +
            Math.cos(last.latitude * radians) *
              Math.cos(point.latitude * radians) *
              Math.sin(((point.longitude - last.longitude) * radians) / 2) ** 2;
          const distance = 6371000 * 2 * Math.asin(Math.sqrt(Math.min(1, a)));
          if (
            distance < Math.max(2, Math.min(5, coords.accuracy * 0.15)) ||
            distance / seconds > 8
          ) {
            report(
              source,
              distance / seconds > 8 ? 'speed' : 'below-threshold',
              coords.accuracy,
              age,
              distance,
            );
            return;
          }
          report(source, 'accepted', coords.accuracy, age, distance);
          previous.current = point;
          setMapPosition(point);
          setMeters(value => value + distance);
          setSegments(value => [...value.slice(0, -1), [...value[value.length - 1], point]]);
        };
        const onError = () => {
          if (disposed || current !== generation) return;
          setMessage('위치 신호를 확인해 주세요. 신호가 돌아오면 기록을 이어갑니다.');
        };
        // Seed the map from a cached or low-power fix while the precise watch acquires GPS.
        Geolocation.getCurrentPosition(
          position => onPosition('initial', position),
          () => {
            if (!lastFixAt) onError();
          },
          {
            enableHighAccuracy: false,
            timeout: 15000,
            maximumAge: 120000,
          },
        );
        watch = Geolocation.watchPosition(
          position => onPosition('watch', position),
          error => {
            if (!disposed && current === generation)
              console.info(`[WalkRoute] watch-error=${error.code}`);
            onError();
          },
          {
            enableHighAccuracy: precise,
            distanceFilter: 0,
            interval: 2000,
            fastestInterval: 1000,
            maximumAge: 0,
          },
        );
      } catch {
        if (!disposed) setMessage('위치 권한과 GPS 설정을 확인해 주세요.');
      }
    };
    void start();
    const subscription = AppState.addEventListener('change', state => {
      if (state === 'active') void start();
      else stop();
    });
    return () => {
      disposed = true;
      stop();
      subscription.remove();
    };
  }, [enabled, retry]);
  return { segments, meters, mapPosition, message, retry: () => setRetry(value => value + 1) };
}
