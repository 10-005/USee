import {
  Image,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
  useWindowDimensions,
} from 'react-native';
import { useState } from 'react';
import { typography } from '../../theme';

export const petStatItems = [
  { key: 'hunger', label: '배고픔', english: 'Hunger', color: '#DBAA6B' },
  { key: 'hydration', label: '목마름', english: 'Hydration', color: '#78B8D6' },
  { key: 'clean', label: '청결', english: 'Clean', color: '#8BBFB0' },
  { key: 'friendship', label: '친밀도', english: 'Friendship', color: '#D5A0AD' },
  { key: 'stress', label: '스트레스', english: 'Stress', color: '#B0A1CB' },
  { key: 'health', label: '건강', english: 'Health', color: '#83AF89' },
  { key: 'activity', label: '활동', english: 'Activity', color: '#C8B36A' },
] as const;

export type PetStats = Record<(typeof petStatItems)[number]['key'], number | null>;

type Props = {
  visible: boolean;
  petName: string;
  stats?: Partial<PetStats>;
  onClose: () => void;
};

export function PetStatsDialog({ visible, petName, stats = {}, onClose }: Props) {
  const { width, height } = useWindowDimensions();
  const [cardSize, setCardSize] = useState({ width: 0, height: 0 });
  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      statusBarTranslucent
      navigationBarTranslucent
      onRequestClose={onClose}
    >
      <View style={styles.overlay}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="상태 다이얼로그 바깥 영역"
          onPress={onClose}
          style={StyleSheet.absoluteFill}
        />
        <View
          accessibilityViewIsModal
          accessibilityLabel={`${petName} 상태`}
          style={[styles.card, { maxHeight: height - 80 }]}
          onLayout={({ nativeEvent: { layout } }) =>
            setCardSize({ width: layout.width, height: layout.height })
          }
        >
          <View pointerEvents="none" style={styles.frostedBackground}>
            <Image
              source={require('../../assets/images/mock-adoption/img_home_background.png')}
              resizeMode="cover"
              blurRadius={22}
              style={[
                styles.backgroundImage,
                {
                  width,
                  height,
                  left: -(width - cardSize.width) / 2,
                  top: -(height - cardSize.height) / 2,
                },
              ]}
            />
            <View style={styles.glassTint} />
          </View>
          <View style={styles.header}>
            <View style={styles.heading}>
              <Text style={styles.eyebrow}>STAT</Text>
              <Text accessibilityRole="header" style={styles.title}>
                {petName}의 상태
              </Text>
            </View>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="상태 다이얼로그 닫기"
              onPress={onClose}
              style={({ pressed }) => [styles.closeButton, pressed && styles.pressed]}
            >
              <Text style={styles.closeLabel}>닫기</Text>
            </Pressable>
          </View>
          <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={styles.stats}>
            {petStatItems.map(item => {
              const raw = stats[item.key];
              const value =
                typeof raw === 'number' && Number.isFinite(raw)
                  ? Math.max(0, Math.min(100, raw))
                  : null;
              return (
                <View key={item.key} style={styles.stat}>
                  <View style={styles.statHeading}>
                    <View style={styles.statLabels}>
                      <Text style={styles.statLabel}>{item.label}</Text>
                      <Text style={styles.english}>{item.english}</Text>
                    </View>
                    <Text style={styles.value}>
                      {value === null ? '—' : value}
                      <Text style={styles.maximum}> / 100</Text>
                    </Text>
                  </View>
                  <View
                    accessibilityRole="progressbar"
                    accessibilityLabel={item.label}
                    accessibilityValue={
                      value === null ? { text: '상태 정보 없음' } : { min: 0, max: 100, now: value }
                    }
                    style={styles.track}
                  >
                    {value !== null ? (
                      <View
                        style={[styles.fill, { width: `${value}%`, backgroundColor: item.color }]}
                      />
                    ) : null}
                  </View>
                </View>
              );
            })}
            {petStatItems.every(item => stats[item.key] == null) ? (
              <Text style={styles.empty}>아직 상태 정보가 없어요.</Text>
            ) : null}
          </ScrollView>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 24,
    backgroundColor: 'rgba(25, 34, 29, 0.26)',
  },
  card: {
    width: '100%',
    maxWidth: 400,
    borderRadius: 28,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.85)',
    overflow: 'hidden',
    backgroundColor: 'rgba(247,250,245,0.4)',
    boxShadow: '0px 16px 48px rgba(21, 41, 30, 0.2)',
  },
  frostedBackground: { ...StyleSheet.absoluteFill, overflow: 'hidden' },
  backgroundImage: { position: 'absolute' },
  glassTint: {
    ...StyleSheet.absoluteFill,
    backgroundColor: 'rgba(250,253,249,0.78)',
    backgroundImage: 'linear-gradient(135deg, rgba(255,255,255,0.4), rgba(233,244,232,0.12))',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: 24,
    paddingBottom: 20,
    gap: 8,
  },
  heading: { flex: 1, gap: 6 },
  eyebrow: { ...typography.captionSemiBold12, color: '#60816D', letterSpacing: 2 },
  title: { ...typography.titleBold18, color: '#293D30' },
  closeButton: {
    minWidth: 48,
    minHeight: 44,
    justifyContent: 'center',
    alignItems: 'center',
    borderRadius: 14,
    backgroundColor: 'rgba(255,255,255,0.54)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.8)',
  },
  closeLabel: { ...typography.bodyRegular14, color: '#526657' },
  stats: { paddingHorizontal: 24, paddingBottom: 24, gap: 18 },
  stat: { gap: 8 },
  statHeading: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 8,
  },
  statLabels: { flexDirection: 'row', alignItems: 'baseline', gap: 8 },
  statLabel: { ...typography.bodySemiBold16, color: '#354A3C' },
  english: { ...typography.captionRegular10, fontSize: 11, lineHeight: 15, color: '#738579' },
  value: { ...typography.bodySemiBold16, color: '#425C49', fontVariant: ['tabular-nums'] },
  maximum: { ...typography.captionRegular10, color: '#7D8F82' },
  track: {
    height: 8,
    borderRadius: 4,
    overflow: 'hidden',
    backgroundColor: 'rgba(82,111,89,0.1)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.55)',
  },
  fill: { height: '100%', borderRadius: 4 },
  empty: { ...typography.captionRegular10, color: '#788C7D', textAlign: 'center', marginTop: 2 },
  pressed: { opacity: 0.65 },
});
