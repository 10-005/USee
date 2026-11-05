import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import Clipboard from '@react-native-clipboard/clipboard';
import { useEffect, useRef, useState } from 'react';
import { Image, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';

import type { RootStackParamList } from '../../app/navigation/types';
import BadgeIcon from '../../assets/icons/common/badge.svg';
import LeftArrowIcon from '../../assets/icons/common/left-arrow.svg';
import MapIcon from '../../assets/icons/common/map.svg';
import PhoneIcon from '../../assets/icons/common/phone.svg';
import { Button } from '../../components/common';
import { colors, typography } from '../../theme';

type Props = NativeStackScreenProps<RootStackParamList, 'ShelterDetail'>;
const MAX_FONT_SIZE_MULTIPLIER = 1.15;

const shelterDescription =
  '안녕하세요 박범준보호소입니다. 보호동물이 안전하게 가족을 기다릴 수 있도록 돌보고 있습니다. 입양을 희망하시는 분께서는 보호동물의 성격과 건강 상태를 충분히 확인해 주세요. 방문 상담을 통해 동물과 직접 만나고 가족 구성원 모두의 동의를 확인한 뒤 입양을 진행하고 있습니다. 입양 상담과 방문 일정은 채팅으로 문의해 주세요.';

export function ShelterDetailScreen({ navigation }: Props) {
  const insets = useSafeAreaInsets();
  const [copiedField, setCopiedField] = useState<'address' | 'phone' | null>(null);
  const [descriptionExpanded, setDescriptionExpanded] = useState(false);
  const copyTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(
    () => () => {
      if (copyTimer.current) {
        clearTimeout(copyTimer.current);
      }
    },
    [],
  );

  const copyToClipboard = (field: 'address' | 'phone', value: string) => {
    Clipboard.setString(value);
    setCopiedField(field);
    if (copyTimer.current) {
      clearTimeout(copyTimer.current);
    }
    copyTimer.current = setTimeout(() => setCopiedField(null), 1500);
  };

  return (
    <SafeAreaView edges={[]} style={styles.safeArea}>
      <ScrollView
        contentContainerStyle={[styles.scrollContent, { paddingBottom: 112 + insets.bottom }]}
        showsVerticalScrollIndicator={false}
      >
        <Image
          resizeMode="cover"
          source={require('../../assets/images/adoption/shelter-cover.png')}
          style={styles.coverImage}
        />
        <View style={styles.contentCard}>
          <Image
            resizeMode="cover"
            source={require('../../assets/images/adoption/shelter-avatar.jpeg')}
            style={styles.avatar}
          />
          <View style={styles.shelterNameRow}>
            <Text
              adjustsFontSizeToFit
              maxFontSizeMultiplier={MAX_FONT_SIZE_MULTIPLIER}
              minimumFontScale={0.8}
              numberOfLines={1}
              style={styles.shelterName}
            >
              박범준동물보호소
            </Text>
            <BadgeIcon />
          </View>
          <View style={styles.contactList}>
            <View style={styles.contactRow}>
              <View style={[styles.icon, styles.mapIcon]}>
                <MapIcon />
              </View>
              <Text
                adjustsFontSizeToFit
                maxFontSizeMultiplier={MAX_FONT_SIZE_MULTIPLIER}
                minimumFontScale={0.65}
                numberOfLines={1}
                style={styles.contactText}
              >
                전북특별자치도 익산시 삼기면 황금 342-17
              </Text>
              <Pressable
                accessibilityLabel="보호센터 주소 복사"
                accessibilityRole="button"
                hitSlop={8}
                onPress={() =>
                  copyToClipboard('address', '전북특별자치도 익산시 삼기면 황금 342-17')
                }
              >
                <Text maxFontSizeMultiplier={MAX_FONT_SIZE_MULTIPLIER} style={styles.copyText}>
                  {copiedField === 'address' ? '복사됨' : '복사'}
                </Text>
              </Pressable>
            </View>
            <View style={styles.contactRow}>
              <View style={[styles.icon, styles.phoneIcon]}>
                <PhoneIcon />
              </View>
              <Text maxFontSizeMultiplier={MAX_FONT_SIZE_MULTIPLIER} style={styles.contactText}>
                063-855-1214
              </Text>
              <Pressable
                accessibilityLabel="보호센터 전화번호 복사"
                accessibilityRole="button"
                hitSlop={8}
                onPress={() => copyToClipboard('phone', '063-855-1214')}
              >
                <Text maxFontSizeMultiplier={MAX_FONT_SIZE_MULTIPLIER} style={styles.copyText}>
                  {copiedField === 'phone' ? '복사됨' : '복사'}
                </Text>
              </Pressable>
            </View>
            <View style={styles.infoRow}>
              <Text
                maxFontSizeMultiplier={MAX_FONT_SIZE_MULTIPLIER}
                numberOfLines={1}
                style={styles.infoLabel}
              >
                운영시간
              </Text>
              <Text maxFontSizeMultiplier={MAX_FONT_SIZE_MULTIPLIER} style={styles.infoValue}>
                09:00~18:00
              </Text>
            </View>
            <View style={styles.infoRow}>
              <Text
                maxFontSizeMultiplier={MAX_FONT_SIZE_MULTIPLIER}
                numberOfLines={1}
                style={styles.infoLabel}
              >
                휴무일
              </Text>
              <Text maxFontSizeMultiplier={MAX_FONT_SIZE_MULTIPLIER} style={styles.infoValue}>
                일월 휴무
              </Text>
            </View>
          </View>
          <View style={styles.divider} />
          <Text maxFontSizeMultiplier={MAX_FONT_SIZE_MULTIPLIER} style={styles.sectionTitle}>
            보호센터 소개
          </Text>
          <Text
            maxFontSizeMultiplier={MAX_FONT_SIZE_MULTIPLIER}
            numberOfLines={descriptionExpanded ? undefined : 4}
            style={styles.description}
          >
            {shelterDescription}
          </Text>
          <Pressable
            accessibilityRole="button"
            accessibilityState={{ expanded: descriptionExpanded }}
            hitSlop={8}
            onPress={() => setDescriptionExpanded(current => !current)}
            style={styles.descriptionToggle}
          >
            <Text
              maxFontSizeMultiplier={MAX_FONT_SIZE_MULTIPLIER}
              style={styles.descriptionToggleText}
            >
              {descriptionExpanded ? '간략히' : '더보기'}
            </Text>
          </Pressable>
        </View>
      </ScrollView>
      <View style={[styles.actionContainer, { paddingBottom: insets.bottom + 16 }]}>
        <Button>채팅 보내기</Button>
      </View>
      <Pressable
        accessibilityLabel="뒤로가기"
        accessibilityRole="button"
        hitSlop={10}
        onPress={navigation.goBack}
        style={[styles.backButton, { top: insets.top + 8 }]}
      >
        <LeftArrowIcon />
      </Pressable>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: { backgroundColor: colors.white, flex: 1 },
  scrollContent: { flexGrow: 1 },
  coverImage: { height: 412, width: '100%' },
  backButton: {
    alignItems: 'center',
    height: 40,
    justifyContent: 'center',
    left: 8,
    position: 'absolute',
    width: 40,
    zIndex: 2,
  },
  contentCard: {
    backgroundColor: colors.white,
    borderTopLeftRadius: 30,
    borderTopRightRadius: 30,
    flex: 1,
    marginTop: -65,
    minHeight: 500,
    paddingHorizontal: 26,
    paddingTop: 60,
  },
  avatar: {
    borderColor: colors.white,
    borderRadius: 35,
    borderWidth: 2,
    height: 70,
    left: 26,
    position: 'absolute',
    top: -35,
    width: 70,
  },
  shelterNameRow: { alignItems: 'center', flexDirection: 'row', gap: 4 },
  shelterName: {
    color: colors.black,
    flexShrink: 1,
    fontFamily: 'Pretendard-Bold',
    fontSize: 22,
    lineHeight: 28,
  },
  contactList: { gap: 14, marginTop: 26 },
  contactRow: { alignItems: 'center', flexDirection: 'row', gap: 8 },
  icon: { alignItems: 'center', height: 20, justifyContent: 'center', width: 20 },
  mapIcon: { opacity: 0.36 },
  phoneIcon: { opacity: 0.55 },
  contactText: { ...typography.bodyRegular16, color: colors.black, flex: 1, minWidth: 0 },
  copyText: {
    ...typography.bodyRegular16,
    color: colors.green400,
    textDecorationLine: 'underline',
  },
  infoRow: { alignItems: 'center', flexDirection: 'row', gap: 13 },
  infoLabel: { ...typography.bodyRegular16, color: colors.gray300, width: 64 },
  infoValue: { ...typography.bodyRegular16, color: colors.black, flexShrink: 1 },
  divider: { backgroundColor: colors.gray200, height: 1, marginVertical: 26 },
  sectionTitle: { ...typography.bodySemiBold16, color: colors.black },
  description: { ...typography.bodyRegular16, color: colors.gray600, marginTop: 20 },
  descriptionToggle: { alignSelf: 'flex-start', marginTop: 6 },
  descriptionToggleText: {
    ...typography.bodyRegular16,
    color: colors.gray300,
    textDecorationLine: 'underline',
  },
  actionContainer: {
    backgroundColor: colors.white,
    bottom: 0,
    left: 0,
    paddingHorizontal: 26,
    paddingTop: 8,
    position: 'absolute',
    right: 0,
  },
});
