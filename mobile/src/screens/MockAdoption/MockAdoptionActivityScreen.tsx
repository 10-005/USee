import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { Image, Pressable, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { RootStackParamList } from '../../app/navigation/types';
import { Button } from '../../components/common';
import BackIcon from '../../assets/icons/common/left-arrow.svg';
import { colors } from '../../theme';

type Props = NativeStackScreenProps<RootStackParamList, 'MockAdoptionActivity'>;

export function MockAdoptionActivityScreen({ navigation }: Props) {
  return (
    <View style={styles.container}>
      <Image
        source={require('../../assets/images/mock-adoption/img_home_background.png')}
        resizeMode="cover"
        style={styles.background}
      />
      <SafeAreaView style={styles.safeArea}>
        <View style={styles.header}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="뒤로가기"
            onPress={() => navigation.goBack()}
            style={({ pressed }) => [styles.iconButton, pressed && styles.pressed]}
          >
            <BackIcon width={24} height={24} />
          </Pressable>
          <Pressable accessibilityRole="button" accessibilityLabel="알림" style={styles.iconButton}>
            <Image
              source={require('../../assets/images/mock-adoption/img_alarm.png')}
              resizeMode="contain"
              style={styles.alarm}
            />
          </Pressable>
        </View>
        <View style={styles.room}>
          <View style={styles.bowls}>
            <Image
              accessibilityLabel="사료 그릇"
              source={require('../../assets/images/mock-adoption/img_full_feetstuff_bowl.png')}
              resizeMode="contain"
              style={styles.bowl}
            />
            <Image
              accessibilityLabel="물그릇"
              source={require('../../assets/images/mock-adoption/img_full_water_bowl.png')}
              resizeMode="contain"
              style={styles.bowl}
            />
          </View>
        </View>
        <View style={styles.action}>
          <Button onPress={() => navigation.navigate('ArWalk')}>AR 산책하기</Button>
        </View>
      </SafeAreaView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.white },
  background: { position: 'absolute', width: '100%', height: '100%' },
  safeArea: { flex: 1 },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingTop: 8,
  },
  iconButton: { width: 48, height: 48, alignItems: 'center', justifyContent: 'center' },
  alarm: { width: 36, height: 36 },
  room: { flex: 1 },
  bowls: {
    position: 'absolute',
    left: 16,
    bottom: 24,
    flexDirection: 'row',
    width: '44%',
    maxWidth: 220,
    gap: 4,
  },
  bowl: { flex: 1, aspectRatio: 1 },
  action: { paddingHorizontal: 20, paddingTop: 8, paddingBottom: 16 },
  pressed: { opacity: 0.7 },
});
