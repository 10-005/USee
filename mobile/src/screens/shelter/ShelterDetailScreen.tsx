import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import Clipboard from '@react-native-clipboard/clipboard';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Image, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';

import type { RootStackParamList } from '../../app/navigation/types';
import BadgeIcon from '../../assets/icons/common/badge.svg';
import LeftArrowIcon from '../../assets/icons/common/left-arrow.svg';
import MapIcon from '../../assets/icons/common/map.svg';
import PhoneIcon from '../../assets/icons/common/phone.svg';
import { Button, DetailScreenSkeleton } from '../../components/common';
import { getShelter, type ShelterDetail } from '../../services/adoption';
import { getApiErrorDisplayMessage } from '../../services/api';
import { useChatStore } from '../../stores/chatStore';
import { colors, typography } from '../../theme';

type Props = NativeStackScreenProps<RootStackParamList, 'ShelterDetail'>;
const MAX_FONT_SIZE_MULTIPLIER = 1.15;

export function ShelterDetailScreen({ navigation, route }: Props) {
  const insets = useSafeAreaInsets();
  const [copiedField, setCopiedField] = useState<'address' | 'phone' | null>(null);
  const [descriptionExpanded, setDescriptionExpanded] = useState(false);
  const [shelter, setShelter] = useState<ShelterDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const copyTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(
    () => () => {
      if (copyTimer.current) {
        clearTimeout(copyTimer.current);
      }
    },
    [],
  );

  const loadShelter = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setShelter(await getShelter(Number(route.params.shelterId)));
    } catch (loadError) {
      setError(getApiErrorDisplayMessage(loadError, '보호센터 정보를 불러오지 못했어요.'));
    } finally {
      setLoading(false);
    }
  }, [route.params.shelterId]);

  useEffect(() => {
    loadShelter().catch(() => undefined);
  }, [loadShelter]);

  const copyToClipboard = (field: 'address' | 'phone', value: string) => {
    Clipboard.setString(value);
    setCopiedField(field);
    if (copyTimer.current) {
      clearTimeout(copyTimer.current);
    }
    copyTimer.current = setTimeout(() => setCopiedField(null), 1500);
  };

  const openChat = async () => {
    if (!shelter) {
      return;
    }
    try {
      const params = await useChatStore.getState().openShelterRoom(route.params.shelterId);
      navigation.navigate('ChatRoom', params);
    } catch {
      Alert.alert('채팅', '채팅방을 열 수 없어요. 다시 시도해 주세요.');
    }
  };

  if (loading) {
    return (
      <SafeAreaView edges={[]} style={styles.safeArea}>
        <DetailScreenSkeleton accessibilityLabel="보호센터 정보 불러오는 중" />
      </SafeAreaView>
    );
  }

  if (!shelter) {
    return (
      <SafeAreaView edges={[]} style={styles.safeArea}>
        <View style={styles.errorState}>
          <Text style={styles.errorText}>{error}</Text>
          <Button onPress={loadShelter}>다시 시도</Button>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView edges={[]} style={styles.safeArea}>
      <ScrollView
        contentContainerStyle={[styles.scrollContent, { paddingBottom: 112 + insets.bottom }]}
        decelerationRate="fast"
        showsVerticalScrollIndicator={false}
      >
        <Image
          resizeMode="cover"
          source={
            shelter.backgroundImage?.imageUrl
              ? { uri: shelter.backgroundImage.imageUrl }
              : require('../../assets/images/adoption/shelter-cover.png')
          }
          style={styles.coverImage}
        />
        <View style={styles.contentCard}>
          <Image
            resizeMode="cover"
            source={
              shelter.profileImage?.imageUrl
                ? { uri: shelter.profileImage.imageUrl }
                : require('../../assets/images/adoption/shelter-avatar.jpeg')
            }
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
              {shelter.shelterName}
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
                {shelter.address}
              </Text>
              <Pressable
                accessibilityLabel="보호센터 주소 복사"
                accessibilityRole="button"
                hitSlop={8}
                onPress={() => copyToClipboard('address', shelter.address)}
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
              <Text
                adjustsFontSizeToFit
                maxFontSizeMultiplier={MAX_FONT_SIZE_MULTIPLIER}
                minimumFontScale={0.8}
                numberOfLines={1}
                style={styles.contactText}
              >
                {shelter.phone}
              </Text>
              <Pressable
                accessibilityLabel="보호센터 전화번호 복사"
                accessibilityRole="button"
                hitSlop={8}
                onPress={() => copyToClipboard('phone', shelter.phone)}
              >
                <Text maxFontSizeMultiplier={MAX_FONT_SIZE_MULTIPLIER} style={styles.copyText}>
                  {copiedField === 'phone' ? '복사됨' : '복사'}
                </Text>
              </Pressable>
            </View>
            <View style={styles.infoRow}>
              <Text
                adjustsFontSizeToFit
                maxFontSizeMultiplier={MAX_FONT_SIZE_MULTIPLIER}
                minimumFontScale={0.8}
                numberOfLines={1}
                style={styles.infoLabel}
              >
                운영시간
              </Text>
              <Text
                adjustsFontSizeToFit
                maxFontSizeMultiplier={MAX_FONT_SIZE_MULTIPLIER}
                minimumFontScale={0.72}
                numberOfLines={1}
                style={styles.infoValue}
              >
                {shelter.operatingHours ?? '-'}
              </Text>
            </View>
            <View style={styles.infoRow}>
              <Text
                adjustsFontSizeToFit
                maxFontSizeMultiplier={MAX_FONT_SIZE_MULTIPLIER}
                minimumFontScale={0.8}
                numberOfLines={1}
                style={styles.infoLabel}
              >
                휴무일
              </Text>
              <Text
                adjustsFontSizeToFit
                maxFontSizeMultiplier={MAX_FONT_SIZE_MULTIPLIER}
                minimumFontScale={0.72}
                numberOfLines={1}
                style={styles.infoValue}
              >
                {shelter.closedDays ?? '-'}
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
            {shelter.description ?? '-'}
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
        <Button onPress={openChat} style={styles.chatButton}>채팅 보내기</Button>
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
  errorState: { alignItems: 'center', flex: 1, gap: 16, justifyContent: 'center', padding: 24 },
  errorText: { ...typography.bodyRegular14, color: colors.gray600, textAlign: 'center' },
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
  contactText: { ...typography.bodyRegular14, color: colors.black, flex: 1, minWidth: 0 },
  copyText: {
    ...typography.bodyRegular14,
    color: colors.green400,
    textDecorationLine: 'underline',
  },
  infoRow: { alignItems: 'center', flexDirection: 'row', gap: 13 },
  infoLabel: { ...typography.bodyRegular14, color: colors.gray300, width: 58 },
  infoValue: { ...typography.bodyRegular14, color: colors.black, flex: 1, minWidth: 0 },
  divider: { backgroundColor: colors.gray200, height: 1, marginVertical: 26 },
  sectionTitle: { ...typography.bodySemiBold16, color: colors.black },
  description: { ...typography.bodyRegular16, color: colors.gray600, marginTop: 20 },
  descriptionToggle: { alignSelf: 'flex-start', marginTop: 6 },
  descriptionToggleText: {
    ...typography.bodyRegular16,
    color: colors.gray300,
    textDecorationLine: 'underline',
  },
  chatButton: { elevation: 8 },
  actionContainer: {
    bottom: 0,
    left: 0,
    paddingHorizontal: 26,
    position: 'absolute',
    right: 0,
  },
});
