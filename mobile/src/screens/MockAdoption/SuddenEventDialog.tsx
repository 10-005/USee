import {
  Image,
  type ImageSourcePropType,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
  useWindowDimensions,
} from 'react-native';
import { typography } from '../../theme';
import type { SimulationEvent } from '../../services/simulation/events';

type Props = {
  event: SimulationEvent;
  image: ImageSourcePropType;
  petName: string;
  onClose: () => void;
};

export function SuddenEventDialog({ event, image, petName, onClose }: Props) {
  const { height } = useWindowDimensions();
  return (
    <Modal transparent visible animationType="fade" statusBarTranslucent onRequestClose={onClose}>
      <View style={styles.overlay}>
        <View accessibilityViewIsModal style={[styles.content, { maxHeight: height - 64 }]}>
          <View pointerEvents="none" style={styles.sticker}>
            <View style={styles.glass}>
              <Image
                source={require('../../assets/images/mock-adoption/img_home_background.png')}
                blurRadius={24}
                style={styles.glassBackground}
              />
              <View style={styles.glassTint} />
            </View>
            <Image accessibilityLabel={`${petName} 스티커`} source={image} style={styles.pet} />
          </View>
          <ScrollView style={styles.card} contentContainerStyle={styles.cardContent}>
            <Text accessibilityRole="header" style={styles.title}>
              {event.scenario.title}
            </Text>
            <Text style={styles.body}>{event.scenario.description}</Text>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="돌발 이벤트 확인"
              onPress={onClose}
              style={({ pressed }) => [styles.button, pressed && styles.pressed]}
            >
              <Text style={styles.buttonLabel}>확인</Text>
            </Pressable>
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
    paddingHorizontal: 28,
    backgroundColor: 'rgba(0,0,0,0.28)',
  },
  content: { width: '100%', maxWidth: 480, alignSelf: 'center', paddingTop: 54 },
  card: { backgroundColor: '#FFFFFF', borderRadius: 24 },
  cardContent: { paddingHorizontal: 24, paddingTop: 66, paddingBottom: 24 },
  sticker: {
    position: 'absolute',
    top: 0,
    alignSelf: 'center',
    width: 108,
    height: 108,
    zIndex: 1,
  },
  glass: {
    ...StyleSheet.absoluteFill,
    borderRadius: 54,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.8)',
  },
  glassBackground: { width: '100%', height: '100%', opacity: 0.25 },
  glassTint: { ...StyleSheet.absoluteFill, backgroundColor: 'rgba(255,255,255,0.45)' },
  pet: { width: '100%', height: '100%', resizeMode: 'contain' },
  title: { ...typography.titleBold18, color: '#222222', marginBottom: 18 },
  body: { ...typography.bodyRegular16, color: '#666666', marginBottom: 24 },
  button: {
    minHeight: 48,
    borderRadius: 12,
    backgroundColor: '#68BF65',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 12,
  },
  buttonLabel: { ...typography.bodySemiBold16, color: '#FFFFFF' },
  pressed: { opacity: 0.75 },
});
