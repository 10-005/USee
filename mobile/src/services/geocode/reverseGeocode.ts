import { NativeModules, Platform } from 'react-native';

type LocationGeocoderModule = {
  reverse: (latitude: number, longitude: number) => Promise<string>;
};

export async function reverseGeocode(latitude: number, longitude: number): Promise<string> {
  const module = NativeModules.LocationGeocoder as LocationGeocoderModule | undefined;
  if (Platform.OS !== 'android' || !module?.reverse) {
    throw new Error('주소 변환을 지원하지 않는 환경입니다.');
  }
  return module.reverse(latitude, longitude);
}
