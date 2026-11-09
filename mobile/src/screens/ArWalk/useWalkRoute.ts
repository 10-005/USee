import { useEffect, useRef, useState } from 'react';
import { AppState, PermissionsAndroid, Platform } from 'react-native';
import Geolocation from '@react-native-community/geolocation';

type Point = { latitude: number; longitude: number; timestamp: number };

export function useWalkRoute(enabled: boolean) {
  const [segments, setSegments] = useState<Point[][]>([]);
  const [meters, setMeters] = useState(0);
  const [message, setMessage] = useState('강아지를 인식하면 이동 경로를 기록합니다.');
  const previous = useRef<Point | null>(null);
  useEffect(() => {
    if (!enabled) return;
    let disposed = false;
    let watch: number | null = null;
    let generation = 0;
    const stop = () => {
      generation++;
      if (watch !== null) Geolocation.clearWatch(watch);
      watch = null;
      previous.current = null;
    };
    const start = async () => {
      stop();
      const current = generation;
      setMessage('GPS 위치를 확인하고 있습니다.');
      try {
        if (Platform.OS === 'android') {
          const permission = await PermissionsAndroid.request(
            PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION,
          );
          if (disposed || current !== generation) return;
          if (permission !== PermissionsAndroid.RESULTS.GRANTED) {
            setMessage('이동 거리와 경로를 기록하려면 위치 권한이 필요합니다.');
            return;
          }
        }
        if (disposed || current !== generation) return;
        watch = Geolocation.watchPosition(
          ({ coords, timestamp }) => {
            if (disposed || current !== generation) return;
            if (
              !Number.isFinite(coords.latitude) ||
              !Number.isFinite(coords.longitude) ||
              !Number.isFinite(coords.accuracy) ||
              !Number.isFinite(timestamp) ||
              Math.abs(coords.latitude) > 90 ||
              Math.abs(coords.longitude) > 180 ||
              coords.accuracy < 0 ||
              coords.accuracy > 25 ||
              Date.now() - timestamp > 15000
            ) {
              setMessage('GPS 정확도가 낮아 위치를 기다리고 있습니다.');
              return;
            }
            const point = { latitude: coords.latitude, longitude: coords.longitude, timestamp };
            const last = previous.current;
            setMessage('');
            if (!last || timestamp - last.timestamp > 30000) {
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
            if (distance < Math.max(3, coords.accuracy / 2) || distance / seconds > 8) return;
            previous.current = point;
            setMeters(value => value + distance);
            setSegments(value => [...value.slice(0, -1), [...value[value.length - 1], point]]);
          },
          () => {
            if (disposed || current !== generation) return;
            previous.current = null;
            setMessage('위치 신호를 확인해 주세요. 신호가 돌아오면 기록을 이어갑니다.');
          },
          {
            enableHighAccuracy: true,
            distanceFilter: 3,
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
  }, [enabled]);
  return { segments, meters, message };
}
