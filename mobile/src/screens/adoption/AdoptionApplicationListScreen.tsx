import { useFocusEffect } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useCallback, useMemo, useState } from 'react';
import { FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import type { RootStackParamList } from '../../app/navigation/types';
import FilterArrowIcon from '../../assets/icons/common/filter-arrow.svg';
import { AppHeader } from '../../components/common';
import {
  type AdoptionApplicationRecord,
  type AdoptionApplicationStatus,
  getAdoptionApplicationRecord,
} from '../../services/storage/adoptionApplicationStorage';
import {
  type AdoptionApplicationDraft,
  getAdoptionApplicationDraft,
} from '../../services/storage/adoptionDraftStorage';
import { colors, radius, spacing, typography } from '../../theme';

type Props = NativeStackScreenProps<RootStackParamList, 'AdoptionApplicationList'>;

type FilterValue = 'ALL' | AdoptionApplicationStatus;

type ListItem = {
  animalId: string;
  date: string;
  id: string;
  status: AdoptionApplicationStatus;
};

const previewItems: ListItem[] = [
  { animalId: 'animal-1', date: '2026-10-27', id: 'preview-draft', status: 'DRAFT' },
  { animalId: 'animal-1', date: '2026-10-27', id: 'preview-submitted', status: 'SUBMITTED' },
  {
    animalId: 'animal-1',
    date: '2026-10-27',
    id: 'preview-document-approved',
    status: 'DOCUMENT_APPROVED',
  },
  {
    animalId: 'animal-1',
    date: '2026-10-27',
    id: 'preview-adoption-approved',
    status: 'ADOPTION_APPROVED',
  },
  { animalId: 'animal-1', date: '2026-10-27', id: 'preview-rejected', status: 'REJECTED' },
  { animalId: 'animal-1', date: '2026-10-27', id: 'preview-canceled', status: 'CANCELED' },
];

const filterOptions: Array<{ label: string; value: FilterValue }> = [
  { label: '전체 상태', value: 'ALL' },
  { label: '임시저장', value: 'DRAFT' },
  { label: '서류 제출 완료', value: 'SUBMITTED' },
  { label: '서류 제출 취소', value: 'CANCELED' },
  { label: '서류 승인', value: 'DOCUMENT_APPROVED' },
  { label: '최종 입양 승인', value: 'ADOPTION_APPROVED' },
  { label: '입양 거절', value: 'REJECTED' },
];

const statusStyle: Record<AdoptionApplicationStatus, { backgroundColor: string; color: string }> = {
  DRAFT: { backgroundColor: '#EFEEEA', color: colors.gray600 },
  SUBMITTED: { backgroundColor: '#EDF4FF', color: '#5B91EB' },
  CANCELED: { backgroundColor: '#EFEEEA', color: colors.gray600 },
  DOCUMENT_APPROVED: { backgroundColor: '#EDF4FF', color: '#5B91EB' },
  ADOPTION_APPROVED: { backgroundColor: '#E4F3E9', color: '#3C9A61' },
  REJECTED: { backgroundColor: '#EFEEEA', color: colors.gray600 },
};

function getStatusLabel(status: AdoptionApplicationStatus) {
  return filterOptions.find(option => option.value === status)?.label ?? '';
}

function formatDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(
    date.getDate(),
  ).padStart(2, '0')}`;
}

export function AdoptionApplicationListScreen({ navigation }: Props) {
  const [record, setRecord] = useState<AdoptionApplicationRecord | null>(null);
  const [draft, setDraft] = useState<AdoptionApplicationDraft<unknown> | null>(null);
  const [filter, setFilter] = useState<FilterValue>('ALL');
  const [filterOpen, setFilterOpen] = useState(false);

  useFocusEffect(
    useCallback(() => {
      Promise.all([getAdoptionApplicationRecord(), getAdoptionApplicationDraft()]).then(
        ([storedRecord, storedDraft]) => {
          setRecord(storedRecord);
          setDraft(storedDraft);
        },
      );
    }, []),
  );

  const items = useMemo(() => {
    const result: ListItem[] = [];
    if (draft) {
      result.push({
        animalId: record?.animalId ?? 'animal-1',
        date: draft.savedAt,
        id: `draft-${draft.savedAt}`,
        status: 'DRAFT',
      });
    }
    if (record) {
      result.push({
        animalId: record.animalId,
        date: record.submittedAt,
        id: `record-${record.applicationNumber}`,
        status: record.status,
      });
    }
    const existingStatuses = new Set(result.map(item => item.status));
    const withPreviews = [
      ...result,
      ...previewItems.filter(item => !existingStatuses.has(item.status)),
    ];
    return filter === 'ALL' ? withPreviews : withPreviews.filter(item => item.status === filter);
  }, [draft, filter, record]);

  const totalCount = useMemo(() => {
    const statuses = new Set<AdoptionApplicationStatus>();
    if (draft) {
      statuses.add('DRAFT');
    }
    if (record) {
      statuses.add(record.status);
    }
    previewItems.forEach(item => statuses.add(item.status));
    return statuses.size;
  }, [draft, record]);
  const selectedFilterLabel =
    filterOptions.find(option => option.value === filter)?.label ?? '전체 상태';

  const openItem = (item: ListItem) => {
    if (item.id.startsWith('preview-')) {
      navigation.navigate('AdoptionApplicationHistory', {
        animalId: item.animalId,
        previewStatus: item.status,
      });
      return;
    }
    if (item.status === 'DRAFT') {
      navigation.navigate('AdoptionApplicationHistory', {
        animalId: item.animalId,
        source: 'draft',
      });
      return;
    }
    navigation.navigate('AdoptionApplicationHistory', {
      animalId: item.animalId,
      source: 'record',
    });
  };

  return (
    <SafeAreaView edges={['top', 'bottom']} style={styles.safeArea}>
      <AppHeader onBackPress={navigation.goBack} title="입양 신청 내역" />
      {totalCount === 0 ? (
        <View style={styles.empty}>
          <Text style={styles.emptyTitle}>입양 신청 내역이 없습니다</Text>
          <Text style={styles.emptyDescription}>
            마음에 드는 보호동물을 찾아 입양 신청을 시작해 보세요
          </Text>
        </View>
      ) : (
        <View style={styles.content}>
          <View style={styles.listHeader}>
            <Text style={styles.total}>
              {filter === 'ALL' ? '전체' : selectedFilterLabel} {items.length}건
            </Text>
            <Pressable
              accessibilityRole="button"
              onPress={() => setFilterOpen(current => !current)}
              style={[styles.filterButton, filterOpen && styles.filterButtonOpen]}
            >
              <Text style={styles.filterButtonText}>{selectedFilterLabel}</Text>
              <View style={[styles.filterArrow, filterOpen && styles.filterArrowOpen]}>
                <FilterArrowIcon height={16} width={16} />
              </View>
            </Pressable>
          </View>
          <FlatList
            contentContainerStyle={styles.list}
            data={items}
            keyExtractor={item => item.id}
            ListEmptyComponent={
              <Text style={styles.filteredEmpty}>선택한 상태의 신청 내역이 없습니다</Text>
            }
            renderItem={({ item }) => (
              <Pressable
                accessibilityRole="button"
                onPress={() => openItem(item)}
                style={({ pressed }) => [styles.card, pressed && styles.pressed]}
              >
                <View style={styles.cardTop}>
                  <Text style={[styles.status, statusStyle[item.status]]}>
                    {getStatusLabel(item.status)}
                  </Text>
                  <Text style={styles.date}>{formatDate(item.date)}</Text>
                </View>
                <Text style={styles.animal}>[개] 믹스견</Text>
                <Text numberOfLines={1} style={styles.shelter}>
                  경북 구미시 3공단 3로 302 박범준보호소
                </Text>
              </Pressable>
            )}
            showsVerticalScrollIndicator={false}
          />
          {filterOpen ? (
            <>
              <Pressable
                accessibilityLabel="상태 필터 닫기"
                onPress={() => setFilterOpen(false)}
                style={styles.dismissArea}
              />
              <View style={styles.filterMenu}>
                {filterOptions.map(option => (
                  <Pressable
                    accessibilityRole="button"
                    key={option.value}
                    onPress={() => {
                      setFilter(option.value);
                      setFilterOpen(false);
                    }}
                    style={styles.filterOption}
                  >
                    <Text
                      style={[
                        styles.filterOptionText,
                        filter === option.value && styles.filterOptionSelected,
                      ]}
                    >
                      {option.label}
                    </Text>
                  </Pressable>
                ))}
              </View>
            </>
          ) : null}
        </View>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: { backgroundColor: colors.white, flex: 1 },
  empty: { alignItems: 'center', flex: 1, justifyContent: 'center', paddingBottom: 80 },
  emptyTitle: { ...typography.bodySemiBold16, color: colors.gray600 },
  emptyDescription: { ...typography.bodyRegular14, color: colors.gray400, marginTop: spacing[6] },
  content: { flex: 1, paddingHorizontal: spacing[12], position: 'relative' },
  listHeader: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: spacing[6],
    paddingTop: spacing[10],
  },
  total: { ...typography.bodyRegular14, color: colors.gray500 },
  filterButton: {
    alignItems: 'center',
    borderColor: colors.gray400,
    borderRadius: radius.pill,
    borderWidth: 1,
    flexDirection: 'row',
    gap: spacing[2],
    height: 30,
    paddingHorizontal: spacing[7],
  },
  filterButtonOpen: { backgroundColor: '#EBEAE5' },
  filterButtonText: { ...typography.bodyRegular14, color: colors.gray500 },
  filterArrow: { transform: [{ rotate: '0deg' }] },
  filterArrowOpen: { transform: [{ rotate: '180deg' }] },
  list: { gap: spacing[6], paddingBottom: spacing[16] },
  card: {
    borderColor: colors.gray200,
    borderRadius: radius.md,
    borderWidth: 1,
    minHeight: 112,
    paddingHorizontal: spacing[8],
    paddingVertical: spacing[6],
  },
  cardTop: { flexDirection: 'row', justifyContent: 'space-between' },
  status: {
    ...typography.captionSemiBold12,
    borderRadius: radius.sm,
    overflow: 'hidden',
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[2],
  },
  date: { ...typography.bodyRegular14, color: colors.gray400 },
  animal: { ...typography.titleBold18, color: colors.black, marginTop: spacing[5] },
  shelter: { ...typography.bodyRegular14, color: colors.gray500, marginTop: spacing[3] },
  filteredEmpty: {
    ...typography.bodyRegular14,
    color: colors.gray400,
    marginTop: spacing[24],
    textAlign: 'center',
  },
  dismissArea: { bottom: 0, left: 0, position: 'absolute', right: 0, top: 0, zIndex: 2 },
  filterMenu: {
    backgroundColor: colors.white,
    borderColor: colors.gray500,
    borderRadius: radius.md,
    borderWidth: 1,
    paddingVertical: spacing[3],
    position: 'absolute',
    right: spacing[12],
    top: 54,
    width: 168,
    zIndex: 3,
  },
  filterOption: { justifyContent: 'center', minHeight: 48, paddingHorizontal: spacing[8] },
  filterOptionText: { ...typography.bodySemiBold16, color: colors.black },
  filterOptionSelected: { color: '#FF6B5E' },
  pressed: { opacity: 0.7 },
});
