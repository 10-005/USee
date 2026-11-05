import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { Alert, Image, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';

import type { RootStackParamList } from '../../app/navigation/types';
import BookmarkActiveIcon from '../../assets/icons/common/bookmark-active.svg';
import BookmarkIcon from '../../assets/icons/common/bookmark.svg';
import RightArrowGreenIcon from '../../assets/icons/common/right-arrow-green.svg';
import { AppHeader, Button } from '../../components/common';
import { getAdoptionApplicationRecord } from '../../services/storage/adoptionApplicationStorage';
import { getAdoptionApplicationDraft } from '../../services/storage/adoptionDraftStorage';
import { useChatStore } from '../../stores/chatStore';
import { useAdoptionBookmarkStore } from '../../stores/adoptionBookmarkStore';
import { colors, radius, typography } from '../../theme';
import { adoptionAnimals } from './data';

type Props = NativeStackScreenProps<RootStackParamList, 'AdoptionDetail'>;

const basicInfo = [
  ['공고번호', '전북-익산-2026-01438'],
  ['동물등록번호', '미등록'],
  ['마이크로칩번호', '미등록'],
  ['품종', '[개] 믹스견'],
  ['성별', '암컷'],
  ['중성화', '아니오'],
  ['나이 / 체중', '2026년생 (60일 미만) / 3kg'],
  ['색상', '갈색 & 흰색'],
];

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
  const animal =
    adoptionAnimals.find(item => item.id === route.params.animalId) ?? adoptionAnimals[0];
  const isClosed = animal.status === '종료';
  const bookmarked = useAdoptionBookmarkStore(state => state.bookmarkedIds.includes(animal.id));
  const toggleBookmark = useAdoptionBookmarkStore(state => state.toggleBookmark);
  const shelterName = '익산유기동물보호소';
  const openChat = async () => {
    try {
      const params = await useChatStore.getState().openPostRoom('adoption', animal.id, shelterName, 'iksan-shelter');
      if (navigation.isFocused()) {
        navigation.navigate('ChatRoom', params);
      }
    } catch {
      Alert.alert('채팅', '채팅방을 열 수 없어요. 다시 시도해 주세요.');
    }
  };
  const openApplication = async () => {
    const [record, draft] = await Promise.all([
      getAdoptionApplicationRecord(),
      getAdoptionApplicationDraft(),
    ]);
    if (draft || record?.status === 'DRAFT') {
      navigation.navigate('AdoptionApplication', { animalId: animal.id });
      return;
    }
    if (record?.status === 'CANCELED' || record?.status === 'REJECTED') {
      navigation.navigate('AdoptionApplication', { animalId: animal.id });
      return;
    }
    if (record) {
      navigation.navigate('AdoptionApplicationHistory', { animalId: animal.id });
      return;
    }
    navigation.navigate('AdoptionApplication', { animalId: animal.id });
  };

  return (
    <SafeAreaView edges={['top']} style={styles.safeArea}>
      <AppHeader onBackPress={navigation.goBack} title="게시글" />
      <ScrollView
        contentContainerStyle={{ paddingBottom: isClosed ? 24 + insets.bottom : 96 + insets.bottom }}
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.shelterProfile}>
          <Image
            source={require('../../assets/images/adoption/shelter-profile.jpg')}
            style={styles.profileImage}
          />
          <Text style={styles.shelterName}>{shelterName}</Text>
          <Text style={styles.profileMeta}>26.10.26</Text>
        </View>
        <Image
          source={require('../../assets/images/adoption/animal-detail.jpg')}
          style={styles.heroImage}
        />
        <View style={styles.titleRow}>
          <View style={styles.badge}>
            <Text style={styles.badgeText}>보호중</Text>
          </View>
          <Text style={styles.title}>[{animal.species}] 믹스견</Text>
          <Pressable
            accessibilityLabel="북마크"
            accessibilityRole="button"
            accessibilityState={{ selected: bookmarked }}
            hitSlop={8}
            onPress={() => toggleBookmark(animal.id)}
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
          <Text style={styles.sectionTitle}>특징</Text>
          <Text style={styles.bodyText}>생후 3개월이며 경계심은 살짝 있지만 순한 편.</Text>
          <Text style={styles.subTitle}>건강검진 특이사항</Text>
          <Text style={styles.bodyText}>건강검진 X</Text>
          <Text style={styles.subTitle}>예방접종 상태</Text>
          <Text style={styles.bodyText}>광견병, 종합백신, 코로나 접종 완료</Text>
        </View>
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>구조정보</Text>
          <InfoRows
            rows={[
              ['구조장소', '익산시 여산면 호산길 251-68'],
              ['구조일시', '2026-10-20'],
              ['특이사항', '없음'],
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
              onPress={() => navigation.navigate('ShelterDetail', { shelterId: 'iksan-shelter' })}
              style={({ pressed }) => [styles.shelterLink, pressed && styles.pressed]}
            >
              <Text style={styles.shelterLinkText}>보호센터 보기</Text>
              <RightArrowGreenIcon />
            </Pressable>
          </View>
          <InfoRows
            rows={[
              ['보호센터명', shelterName],
              ['보호센터연락처', '063-855-1214'],
              ['보호센터 주소', '전북특별자치도 익산시 삼기면 황금로 342-17'],
            ]}
          />
        </View>
      </ScrollView>
      {!isClosed ? (
        <View style={[styles.actions, { bottom: insets.bottom + 12 }]}>
          <Button onPress={openChat} style={styles.action}>채팅 보내기</Button>
          <Button onPress={openApplication} style={[styles.action, styles.applyButton]}>
            입양 신청하기
          </Button>
        </View>
      ) : null}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: { backgroundColor: colors.white, flex: 1 },
  shelterProfile: { alignItems: 'center', flexDirection: 'row', height: 62, paddingHorizontal: 26 },
  profileImage: { borderRadius: 24, height: 48, width: 48 },
  shelterName: { ...typography.titleSemiBold18, color: colors.gray600, flex: 1, marginLeft: 16 },
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
  subTitle: { ...typography.titleBold18, color: colors.black, marginTop: 12 },
  bodyText: { ...typography.bodyRegular16, color: colors.gray500 },
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
