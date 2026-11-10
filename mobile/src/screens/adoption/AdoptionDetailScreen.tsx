import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useEffect } from 'react';
import { Alert, Image, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';

import type { RootStackParamList } from '../../app/navigation/types';
import BookmarkActiveIcon from '../../assets/icons/common/bookmark-active.svg';
import BookmarkIcon from '../../assets/icons/common/bookmark.svg';
import RightArrowGreenIcon from '../../assets/icons/common/right-arrow-green.svg';
import { AppHeader, Button, DetailScreenSkeleton } from '../../components/common';
import { getMyAdoptionApplications } from '../../services/adoption';
import { useChatStore } from '../../stores/chatStore';
import { useAdoptionBookmarkStore } from '../../stores/adoptionBookmarkStore';
import { useAdoptionAnimalStore } from '../../stores/adoptionAnimalStore';
import { colors, radius, typography } from '../../theme';

type Props = NativeStackScreenProps<RootStackParamList, 'AdoptionDetail'>;

function InfoRows({ rows }: { rows: string[][] }) {
  return (
    <View style={styles.infoRows}>
      {rows.map(([label, value]) => (
        <View key={label} style={styles.infoRow}>
          <Text style={styles.infoLabel}>{label}</Text>
          <Text style={styles.infoValue}>{value}</Text>
        </View>
      ))}
    </View>
  );
}

export function AdoptionDetailScreen({ navigation, route }: Props) {
  const insets = useSafeAreaInsets();
  const apiAnimal = useAdoptionAnimalStore(state =>
    state.animals.find(item => item.id === route.params.animalId),
  );
  const detail = useAdoptionAnimalStore(state => state.detailsById[route.params.animalId]);
  const fetchAnimal = useAdoptionAnimalStore(state => state.fetchAnimal);
  const isDetailLoading = useAdoptionAnimalStore(state => state.isDetailLoading);
  const detailError = useAdoptionAnimalStore(state => state.error);

  useEffect(() => {
    fetchAnimal(route.params.animalId).catch(() => undefined);
  }, [fetchAnimal, route.params.animalId]);

  const isClosed = detail?.protectionStatus !== 'PROTECTING';
  const canApply = !isClosed && detail?.adoptionStatus === 'AVAILABLE';
  const bookmarked = useAdoptionBookmarkStore(state =>
    state.bookmarkedIds.includes(route.params.animalId),
  );
  const toggleBookmark = useAdoptionBookmarkStore(state => state.toggleBookmark);
  const shelterName = detail?.shelter?.shelterName ?? apiAnimal?.location ?? '보호소 정보 없음';
  const gender = detail?.gender === 'MALE' ? '수컷' : detail?.gender === 'FEMALE' ? '암컷' : '모름';
  const basicInfo = [
    ['공고번호', String(detail?.animalSeq ?? route.params.animalId)],
    ['동물등록번호', detail?.registrationNumber ?? '미등록'],
    ['마이크로칩번호', detail?.microchipNumber ?? '미등록'],
    [
      '품종',
      detail?.classification
        ? `[${detail.classification.speciesName}] ${detail.classification.breedName}`
        : '모름',
    ],
    ['성별', gender],
    [
      '중성화',
      detail?.isNeutered === null || detail?.isNeutered === undefined
        ? '모름'
        : detail.isNeutered
        ? '예'
        : '아니오',
    ],
    ['나이 / 체중', `${detail?.age ?? '모름'} / ${detail?.weight ?? '모름'}`],
    ['색상', detail?.classification?.colorName ?? '모름'],
  ];
  const openChat = async () => {
    try {
      const params = await useChatStore
        .getState()
        .openPostRoom('adoption', route.params.animalId, shelterName, 'iksan-shelter');
      if (navigation.isFocused()) {
        navigation.navigate('ChatRoom', params);
      }
    } catch {
      Alert.alert('채팅', '채팅방을 열 수 없어요. 다시 시도해 주세요.');
    }
  };
  const openApplication = async () => {
    try {
      const page = await getMyAdoptionApplications({
        animalSeq: Number(route.params.animalId),
      });
      const activeApplication = page.content.find(application =>
        ['DRAFT', 'SUBMITTED', 'APPROVED'].includes(application.status),
      );
      if (activeApplication?.status !== 'DRAFT') {
        if (activeApplication) {
          navigation.navigate('AdoptionApplicationHistory', {
            animalId: route.params.animalId,
            applicationSeq: activeApplication.applicationSeq,
            applicationStatus: activeApplication.status,
            source: 'record',
          });
          return;
        }
      }
      navigation.navigate('AdoptionApplication', { animalId: route.params.animalId });
    } catch {
      Alert.alert('입양 신청', '신청 내역을 확인하지 못했어요. 다시 시도해 주세요.');
    }
  };

  if (!detail && (isDetailLoading || !detailError)) {
    return (
      <SafeAreaView edges={['top']} style={styles.safeArea}>
        <AppHeader onBackPress={navigation.goBack} title="게시글" />
        <DetailScreenSkeleton accessibilityLabel="보호동물 상세 불러오는 중" />
      </SafeAreaView>
    );
  }

  if (!detail && detailError) {
    return (
      <SafeAreaView edges={['top']} style={styles.safeArea}>
        <AppHeader onBackPress={navigation.goBack} title="게시글" />
        <View style={styles.errorState}>
          <Text style={styles.errorText}>{detailError}</Text>
          <Button onPress={() => fetchAnimal(route.params.animalId)}>다시 시도</Button>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView edges={['top']} style={styles.safeArea}>
      <AppHeader onBackPress={navigation.goBack} title="게시글" />
      <ScrollView
        contentContainerStyle={{
          paddingBottom: isClosed ? 24 + insets.bottom : 96 + insets.bottom,
        }}
        decelerationRate="fast"
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.shelterProfile}>
          <Image
            source={require('../../assets/images/adoption/shelter-profile.jpg')}
            style={styles.profileImage}
          />
          <Text style={styles.shelterName}>{shelterName}</Text>
          <Text style={styles.profileMeta}>{detail?.regDttm?.slice(0, 10) ?? ''}</Text>
        </View>
        <Image
          source={
            detail?.images[0]?.imageUrl
              ? { uri: detail.images[0].imageUrl }
              : require('../../assets/images/adoption/animal-dog-white.png')
          }
          style={styles.heroImage}
        />
        <View style={styles.titleRow}>
          <View style={styles.badge}>
            <Text style={styles.badgeText}>{isClosed ? '보호종료' : '보호중'}</Text>
          </View>
          <Text style={styles.title}>
            [{detail?.classification?.speciesName ?? '동물'}]{' '}
            {detail?.classification?.breedName ?? '품종 정보 없음'}
          </Text>
          <Pressable
            accessibilityLabel="북마크"
            accessibilityRole="button"
            accessibilityState={{ selected: bookmarked }}
            hitSlop={8}
            onPress={() => toggleBookmark(route.params.animalId)}
            style={({ pressed }) => [styles.bookmark, pressed && styles.pressed]}
          >
            {bookmarked ? (
              <BookmarkActiveIcon height={20} width={20} />
            ) : (
              <BookmarkIcon height={20} width={20} />
            )}
          </Pressable>
        </View>
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>기본정보</Text>
          <InfoRows rows={basicInfo} />
        </View>
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>상세정보</Text>
          <Text style={[styles.detailLabel, styles.firstDetailLabel]}>특징</Text>
          <Text style={styles.bodyText}>{detail?.description ?? '-'}</Text>
          <Text style={styles.detailLabel}>건강검진 특이사항</Text>
          <Text style={styles.bodyText}>{detail?.healthCheck ?? '-'}</Text>
          <Text style={styles.detailLabel}>예방접종 상태</Text>
          <Text style={styles.bodyText}>{detail?.vaccinationStatus ?? '-'}</Text>
        </View>
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>구조정보</Text>
          <InfoRows
            rows={[
              [
                '구조장소',
                [detail?.rescueInfo.rescueAddress, detail?.rescueInfo.rescueAddressDetail]
                  .filter(Boolean)
                  .join(' ') || '-',
              ],
              ['구조일시', detail?.rescueInfo.rescuedDttm?.slice(0, 10) ?? '-'],
              ['특이사항', detail?.rescueInfo.rescueMemo ?? '-'],
            ]}
          />
        </View>
        <View style={styles.section}>
          <View style={styles.sectionHeader}>
            <Text style={[styles.sectionTitle, styles.sectionTitleInHeader]}>보호센터 정보</Text>
            <Pressable
              accessibilityLabel="보호센터 상세 보기"
              accessibilityRole="button"
              hitSlop={8}
              onPress={() =>
                detail?.shelter?.shelterSeq
                  ? navigation.navigate('ShelterDetail', {
                      shelterId: String(detail.shelter.shelterSeq),
                    })
                  : undefined
              }
              style={({ pressed }) => [styles.shelterLink, pressed && styles.pressed]}
            >
              <Text style={styles.shelterLinkText}>보호센터 보기</Text>
              <RightArrowGreenIcon />
            </Pressable>
          </View>
          <InfoRows
            rows={[
              ['보호센터명', shelterName],
              ['보호센터연락처', detail?.shelter?.phone ?? '-'],
              ['보호센터 주소', detail?.shelter?.address ?? '-'],
            ]}
          />
        </View>
      </ScrollView>
      {!isClosed ? (
        <View style={[styles.actions, { bottom: insets.bottom + 12 }]}>
          <Button onPress={openChat} style={styles.action}>
            채팅 보내기
          </Button>
          {canApply ? (
            <Button onPress={openApplication} style={[styles.action, styles.applyButton]}>
              입양 신청하기
            </Button>
          ) : null}
        </View>
      ) : null}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: { backgroundColor: colors.white, flex: 1 },
  errorState: { alignItems: 'center', flex: 1, gap: 16, justifyContent: 'center', padding: 24 },
  errorText: { ...typography.bodyRegular14, color: colors.gray600, textAlign: 'center' },
  shelterProfile: { alignItems: 'center', flexDirection: 'row', height: 62, paddingHorizontal: 26 },
  profileImage: { borderRadius: 20, height: 40, width: 40 },
  shelterName: { ...typography.bodySemiBold16, color: colors.gray600, flex: 1, marginLeft: 12 },
  profileMeta: { ...typography.captionRegular10, color: colors.gray300 },
  heroImage: { height: 412, width: '100%' },
  titleRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: 6,
    paddingHorizontal: 26,
    paddingVertical: 16,
  },
  badge: {
    backgroundColor: '#FA705B',
    borderRadius: radius.pill,
    paddingHorizontal: 8,
    paddingVertical: 3,
  },
  badgeText: { ...typography.bodyRegular14, color: colors.white },
  title: { ...typography.titleSemiBold18, color: colors.black, flex: 1 },
  bookmark: {
    height: 20,
    width: 20,
  },
  section: {
    borderTopColor: colors.gray200,
    borderTopWidth: 1,
    gap: 10,
    paddingHorizontal: 26,
    paddingVertical: 24,
  },
  sectionTitle: { ...typography.titleBold18, color: colors.black, marginBottom: 8 },
  sectionHeader: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between' },
  sectionTitleInHeader: { marginBottom: 0 },
  shelterLink: { alignItems: 'center', flexDirection: 'row', gap: 2 },
  shelterLinkText: { ...typography.bodyRegular14, color: colors.green400 },
  detailLabel: {
    ...typography.bodyRegular14,
    color: colors.gray400,
    marginTop: 12,
  },
  firstDetailLabel: { marginTop: 0 },
  bodyText: { ...typography.bodyRegular14, color: colors.black },
  infoRows: { gap: 10 },
  infoRow: { flexDirection: 'row' },
  infoLabel: { ...typography.bodyRegular14, color: colors.gray400, width: 110 },
  infoValue: { ...typography.bodyRegular14, color: colors.black, flex: 1 },
  actions: {
    flexDirection: 'row',
    gap: 8,
    left: 0,
    paddingHorizontal: 15,
    position: 'absolute',
    right: 0,
  },
  action: { elevation: 8, flex: 1 },
  applyButton: { backgroundColor: '#FAB45B' },
  pressed: { opacity: 0.7 },
});
