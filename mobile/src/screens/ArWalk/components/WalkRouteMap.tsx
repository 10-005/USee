import { Pressable, StyleSheet, Text, View } from 'react-native';
import {
  NaverMapView,
  NaverMapArrowheadPathOverlay,
  NaverMapMarkerOverlay,
} from '@mj-studio/react-native-naver-map';
import type { useWalkRoute } from '../useWalkRoute';

export function WalkRouteMap({ route }: { route: ReturnType<typeof useWalkRoute> }) {
  const first = route.segments[0]?.[0];
  const last = route.mapPosition;
  return (
    <View style={styles.container} accessibilityLabel="실시간 산책 경로">
      <NaverMapView
        isUseTextureViewAndroid
        style={styles.map}
        camera={last ? { latitude: last.latitude, longitude: last.longitude, zoom: 17 } : undefined}
        isShowCompass
        isScrollGesturesEnabled={false}
        isZoomGesturesEnabled={false}
        isRotateGesturesEnabled={false}
        isTiltGesturesEnabled={false}
      >
        {route.segments.map((points, index) =>
          points.length >= 2 ? (
            <NaverMapArrowheadPathOverlay key={index} coords={points} width={4} color="#2185FF" />
          ) : null,
        )}
        {first && (
          <NaverMapMarkerOverlay
            {...first}
            caption={{ text: '출발' }}
            tintColor="#29A36A"
            width={18}
            height={24}
          />
        )}
        {last && <NaverMapMarkerOverlay {...last} tintColor="#2185FF" width={18} height={24} />}
      </NaverMapView>
      {!!route.message && (
        <Pressable
          onPress={route.retry}
          accessibilityRole="button"
          accessibilityLabel="산책 위치 다시 확인"
          style={styles.status}
        >
          <Text style={styles.notice}>{route.message} 터치하여 재시도</Text>
        </Pressable>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    width: 180,
    height: 126,
    borderRadius: 10,
    overflow: 'hidden',
    backgroundColor: '#F0F3F5',
  },
  map: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  status: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    backgroundColor: 'rgba(255,255,255,0.9)',
    padding: 6,
  },
  notice: { fontSize: 10, color: '#444444' },
});
