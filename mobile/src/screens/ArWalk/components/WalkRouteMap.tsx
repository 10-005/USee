import { Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import {
  NaverMapView,
  NaverMapArrowheadPathOverlay,
  NaverMapMarkerOverlay,
} from '@mj-studio/react-native-naver-map';
import type { useWalkRoute } from '../useWalkRoute';

export function WalkRouteMap({
  visible,
  onClose,
  route,
}: {
  visible: boolean;
  onClose: () => void;
  route: ReturnType<typeof useWalkRoute>;
}) {
  const points = route.segments.flat();
  const first = points[0];
  const last = points[points.length - 1];
  return (
    <Modal visible={visible} onRequestClose={onClose} animationType="slide">
      <SafeAreaView style={styles.screen}>
        <View style={styles.header}>
          <Text>산책 경로 · {(route.meters / 1000).toFixed(2)} km</Text>
          <Pressable
            onPress={onClose}
            accessibilityRole="button"
            accessibilityLabel="산책 경로 닫기"
            style={styles.close}
          >
            <Text>닫기</Text>
          </Pressable>
        </View>
        {visible && last ? (
          <NaverMapView style={styles.map} initialCamera={{ ...last, zoom: 16 }} isShowCompass>
            {route.segments.map((segment, index) =>
              segment.length >= 2 ? (
                <NaverMapArrowheadPathOverlay
                  key={index}
                  coords={segment}
                  width={5}
                  color="#2185FF"
                />
              ) : null,
            )}
            <NaverMapMarkerOverlay {...first} caption={{ text: '출발' }} tintColor="#29A36A" />
            <NaverMapMarkerOverlay {...last} caption={{ text: '현재' }} tintColor="#2185FF" />
          </NaverMapView>
        ) : (
          <View style={styles.map} />
        )}
        <Text style={styles.notice}>
          {route.message ||
            '화살표 방향으로 이동했습니다. 지도를 확대하거나 이동해 경로를 확인하세요.'}
        </Text>
        <Text style={styles.notice}>
          산책 화면이 활성화된 동안 기록하며, 화면을 나가면 기록이 종료됩니다.
        </Text>
      </SafeAreaView>
    </Modal>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#FFFFFF' },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
  },
  close: { padding: 16 },
  map: { flex: 1 },
  notice: { padding: 12, color: '#555555', fontSize: 12 },
});
