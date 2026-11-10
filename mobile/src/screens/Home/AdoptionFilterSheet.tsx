import { useEffect, useState } from 'react';
import { BackHandler, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { CalendarDialog } from '../../components/common/CalendarDialog';
import { CalendarMonthFields, CalendarMonthGrid } from '../../components/common/CalendarMonthGrid';
import SelectedIcon from '../../assets/icons/navigation/adoption-active.svg';
import PeriodArrowIcon from '../../assets/icons/common/period-arrow-down.svg';
import { colors, radius, typography } from '../../theme';

export type FilterKey = 'period' | 'region' | 'species' | 'status' | 'gender' | 'neutered' | 'sort';
export type AdoptionFilters = Record<FilterKey, string>;
export type TwoColumnFilterOptions = Record<string, string[]>;

type Props = {
  activeFilter: FilterKey | null;
  filters: AdoptionFilters;
  onChange: (key: FilterKey, value: string) => void;
  onClose: () => void;
  regionOptions?: TwoColumnFilterOptions;
  speciesOptions?: TwoColumnFilterOptions;
  statusOptions?: string[];
};

const titles: Record<FilterKey, string> = {
  period: '기간 설정',
  region: '지역 설정',
  species: '축종',
  status: '상태',
  gender: '성별',
  neutered: '중성화 여부',
  sort: '정렬',
};

const simpleOptions: Partial<Record<FilterKey, string[]>> = {
  status: ['전체', '보호', '종료'],
  gender: ['전체', '수컷', '암컷'],
  neutered: ['전체', '중성화 X', '중성화 O'],
  sort: ['최신순', '오래된 순'],
};

const regions: Record<string, string[]> = {
  전체: [],
  서울특별시: ['전체', '강남구', '마포구', '송파구', '종로구'],
  부산광역시: ['전체', '해운대구', '수영구', '부산진구'],
  대구광역시: ['전체', '달서구', '수성구', '중구'],
  인천광역시: ['전체', '남동구', '부평구', '연수구'],
  세종특별자치시: ['전체'],
  대전광역시: ['전체', '유성구', '서구', '중구'],
};

const species: Record<string, string[]> = {
  전체: [],
  개: ['전체', '믹스견', '말티즈', '푸들', '진도견'],
  고양이: ['전체', '한국고양이', '믹스묘', '코리안 숏헤어'],
  기타: ['전체', '토끼', '조류', '기타'],
};

function OptionRow({
  label,
  selected,
  onPress,
}: {
  label: string;
  selected: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable onPress={onPress} style={styles.optionRow}>
      <Text style={[styles.optionText, selected && styles.selectedText]}>{label}</Text>
      {selected ? <SelectedIcon height={20} width={20} /> : null}
    </Pressable>
  );
}

function SimpleFilter({
  filterKey,
  options,
  value,
  onChange,
}: {
  filterKey: FilterKey;
  options?: string[];
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <View style={styles.simpleOptions}>
      {(options ?? simpleOptions[filterKey])?.map(option => (
        <OptionRow
          key={option}
          label={option}
          onPress={() => onChange(option)}
          selected={value === option}
        />
      ))}
    </View>
  );
}

function TwoColumnFilter({
  kind,
  options,
  value,
  onChange,
}: {
  kind: 'region' | 'species';
  options?: TwoColumnFilterOptions;
  value: string;
  onChange: (value: string) => void;
}) {
  const source = options ?? (kind === 'region' ? regions : species);
  const initialPrimary = value.includes(' > ') ? value.split(' > ')[0] : value;
  const [primary, setPrimary] = useState(source[initialPrimary] ? initialPrimary : '전체');
  const effectivePrimary = source[primary] ? primary : '전체';
  const secondary = value.includes(' > ') ? value.split(' > ')[1] : '';

  return (
    <View style={styles.columns}>
      <View style={styles.column}>
        <Text style={styles.columnTitle}>{kind === 'region' ? '시도' : '대분류'}</Text>
        <ScrollView decelerationRate="fast" showsVerticalScrollIndicator={false}>
          {Object.keys(source).map(option => (
            <Pressable
              key={option}
              onPress={() => {
                setPrimary(option);
                onChange(option);
              }}
              style={[
                styles.columnOption,
                effectivePrimary === option && styles.columnOptionSelected,
              ]}
            >
              <Text style={styles.columnOptionText}>{option}</Text>
            </Pressable>
          ))}
        </ScrollView>
      </View>
      <View style={[styles.column, styles.secondaryColumn]}>
        <Text style={styles.columnTitle}>{kind === 'region' ? '시군구' : '종'}</Text>
        <ScrollView decelerationRate="fast" showsVerticalScrollIndicator={false}>
          {(source[effectivePrimary] ?? []).map(option => (
            <Pressable
              key={option}
              onPress={() =>
                onChange(option === '전체' ? effectivePrimary : `${effectivePrimary} > ${option}`)
              }
              style={[
                styles.columnOption,
                (secondary === option || (option === '전체' && value === effectivePrimary)) &&
                  styles.columnOptionSelected,
              ]}
            >
              <Text style={styles.columnOptionText}>{option}</Text>
            </Pressable>
          ))}
        </ScrollView>
      </View>
    </View>
  );
}

const formatDate = (date: Date) =>
  `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(
    date.getDate(),
  ).padStart(2, '0')}`;

const parsePeriod = (value: string) => {
  const [start = '전체', end = '전체'] = value.split('|');
  return { start, end };
};

function previousMonth(date: Date) {
  const lastDay = new Date(date.getFullYear(), date.getMonth(), 0);
  lastDay.setDate(Math.min(date.getDate(), lastDay.getDate()));
  return lastDay;
}

function periodDate(value: string, fallback: Date) {
  const [year, month, day] = value.split('.').map(Number);
  return year && month && day ? new Date(year, month - 1, day) : fallback;
}

function CalendarFilter({
  value,
  onApply,
  onClose,
}: {
  value: string;
  onApply: (value: string) => void;
  onClose: () => void;
}) {
  const today = new Date();
  const todayText = formatDate(today);
  const initialPeriod = parsePeriod(value);
  const [draftPeriod, setDraftPeriod] = useState(initialPeriod);
  const [activeBoundary, setActiveBoundary] = useState<'start' | 'end'>('start');
  const endDate = periodDate(draftPeriod.end, today);
  const startDate = periodDate(draftPeriod.start, endDate);
  const visibleDate = activeBoundary === 'start' ? startDate : endDate;

  const updateBoundary = (boundary: 'start' | 'end', date: Date) => {
    const selected = formatDate(date);
    setDraftPeriod(current => {
      if (current.end === '전체') {
        return { start: selected, end: selected };
      }
      return boundary === 'start'
        ? {
            start: selected,
            end: current.start === '전체' || selected > current.end ? selected : current.end,
          }
        : { ...current, end: selected };
    });
  };

  const selectDate = (date: Date) => {
    const selected = formatDate(date);
    const { start, end } = draftPeriod;
    if (selected === start && selected === end) {
      setDraftPeriod({ start: '전체', end: '전체' });
    } else if (end === '전체') {
      setDraftPeriod({ start: selected, end: selected });
    } else if (selected === start) {
      setDraftPeriod({ start: end, end });
    } else if (selected === end && start !== '전체') {
      setDraftPeriod({ start, end: start });
    } else if (selected < start || start === '전체') {
      setDraftPeriod({ start: selected, end: selected > end ? selected : end });
      setActiveBoundary('start');
    } else {
      setDraftPeriod({ start, end: selected });
      setActiveBoundary('end');
    }
  };

  const selectMonth = (boundary: 'start' | 'end', month: Date) => {
    const year = month.getFullYear();
    const index = month.getMonth();
    const lastDay = new Date(year, index + 1, 0).getDate();
    const day =
      boundary === 'start' && draftPeriod.start === '전체'
        ? 1
        : (boundary === 'start' ? startDate : endDate).getDate();
    const date = new Date(year, index, Math.min(day, lastDay));
    const minimum =
      boundary === 'end' && draftPeriod.start !== '전체' ? startDate.getTime() : -Infinity;
    updateBoundary(
      boundary,
      new Date(Math.max(minimum, Math.min(today.getTime(), date.getTime()))),
    );
  };

  const chooseQuickPeriod = (kind: 'all' | 'today' | 'week' | 'month') => {
    if (kind === 'all') {
      setDraftPeriod({ start: '전체', end: '전체' });
      setActiveBoundary('start');
      return;
    }
    const start = kind === 'month' ? previousMonth(today) : new Date(today);
    if (kind === 'week') {
      start.setDate(start.getDate() - 6);
    }
    setDraftPeriod({ start: formatDate(start), end: todayText });
    setActiveBoundary('end');
  };

  const weekStart = new Date(today);
  weekStart.setDate(weekStart.getDate() - 6);
  const monthStart = previousMonth(today);
  const quickPeriod =
    draftPeriod.end === '전체'
      ? 'all'
      : draftPeriod.start === todayText && draftPeriod.end === todayText
      ? 'today'
      : draftPeriod.start === formatDate(weekStart) && draftPeriod.end === todayText
      ? 'week'
      : draftPeriod.start === formatDate(monthStart) && draftPeriod.end === todayText
      ? 'month'
      : null;

  return (
    <View style={styles.calendarContent}>
      <View style={styles.quickPeriods}>
        {[
          { key: 'all', label: '전체' },
          { key: 'today', label: '오늘' },
          { key: 'week', label: '최근 1주' },
          { key: 'month', label: '최근 1개월' },
        ].map(option => (
          <Pressable
            key={option.key}
            onPress={() => chooseQuickPeriod(option.key as 'all' | 'today' | 'week' | 'month')}
            style={[styles.quickPeriod, quickPeriod === option.key && styles.quickPeriodSelected]}
          >
            <Text
              style={[
                styles.quickPeriodText,
                quickPeriod === option.key && styles.quickPeriodTextSelected,
              ]}
            >
              {option.label}
            </Text>
          </Pressable>
        ))}
      </View>
      {(['start', 'end'] as const).map(boundary => {
        const label = boundary === 'start' ? '시작일' : '종료일';
        const active = activeBoundary === boundary;
        return (
          <View key={boundary} style={boundary === 'end' && styles.endBoundary}>
            {boundary === 'end' && (
              <View style={styles.downArrow} importantForAccessibility="no-hide-descendants">
                <PeriodArrowIcon width={24} height={24} color={colors.gray600} />
              </View>
            )}
            <View>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel={label + ' 날짜 선택'}
                accessibilityValue={{ text: draftPeriod[boundary] }}
                accessibilityState={{ selected: active }}
                onPress={() => setActiveBoundary(boundary)}
                style={styles.boundaryLabel}
              >
                <Text style={styles.dateLabel}>{label}</Text>
                <Text style={styles.dateValue}>{draftPeriod[boundary]}</Text>
              </Pressable>
              <CalendarMonthFields
                label={label}
                month={boundary === 'start' ? startDate : endDate}
                onOpen={() => setActiveBoundary(boundary)}
                onMonthChange={month => selectMonth(boundary, month)}
                maximumDate={today}
                minimumDate={
                  boundary === 'end' && draftPeriod.start !== '전체' ? startDate : undefined
                }
              />
            </View>
          </View>
        );
      })}
      <CalendarMonthGrid
        showHeader={false}
        month={visibleDate}
        onMonthChange={month => selectMonth(activeBoundary, month)}
        maximumDate={today}
        onDayPress={selectDate}
        dayState={date => {
          const label = formatDate(date);
          if (label === draftPeriod.start || label === draftPeriod.end) {
            return 'selected';
          }
          if (
            draftPeriod.start !== '전체' &&
            label > draftPeriod.start &&
            label < draftPeriod.end
          ) {
            return 'range';
          }
          return undefined;
        }}
      />
      <View style={styles.periodActions}>
        <Pressable
          onPress={() => chooseQuickPeriod('all')}
          style={styles.resetButton}
        >
          <Text style={styles.resetButtonText}>초기화</Text>
        </Pressable>
        <Pressable
          onPress={() => {
            onApply(draftPeriod.end === '전체' ? '전체' : `${draftPeriod.start}|${draftPeriod.end}`);
            onClose();
          }}
          accessibilityRole="button"
          accessibilityLabel="기간 적용"
          style={styles.applyButton}
        >
          <Text style={styles.applyButtonText}>적용하기</Text>
        </Pressable>
      </View>
    </View>
  );
}

export function AdoptionFilterSheet({
  activeFilter,
  filters,
  onChange,
  onClose,
  regionOptions,
  speciesOptions,
  statusOptions,
}: Props) {
  const insets = useSafeAreaInsets();
  useEffect(() => {
    if (!activeFilter) {
      return;
    }
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      onClose();
      return true;
    });
    return () => subscription.remove();
  }, [activeFilter, onClose]);

  if (!activeFilter) {
    return null;
  }
  if (activeFilter === 'period') {
    return (
      <CalendarDialog visible title={titles.period} onClose={onClose}>
        <CalendarFilter
          value={filters.period}
          onApply={value => onChange('period', value)}
          onClose={onClose}
        />
      </CalendarDialog>
    );
  }
  return (
    <View style={styles.overlay}>
      <Pressable
        accessibilityLabel="필터 닫기"
        accessibilityRole="button"
        onPress={onClose}
        style={styles.backdrop}
      />
      <View
        style={[
          styles.sheet,
          { paddingBottom: 28 + insets.bottom },
          (activeFilter === 'region' || activeFilter === 'species') && [
            styles.twoColumnSheet,
            { height: 333 + insets.bottom },
          ],
        ]}
      >
        <Text style={styles.title}>{titles[activeFilter]}</Text>
        <View style={styles.divider} />
        {activeFilter === 'region' || activeFilter === 'species' ? (
          <TwoColumnFilter
            kind={activeFilter}
            options={activeFilter === 'region' ? regionOptions : speciesOptions}
            onChange={value => onChange(activeFilter, value)}
            value={filters[activeFilter]}
          />
        ) : null}
        {simpleOptions[activeFilter] ? (
          <SimpleFilter
            filterKey={activeFilter}
            options={activeFilter === 'status' ? statusOptions : undefined}
            onChange={value => onChange(activeFilter, value)}
            value={filters[activeFilter]}
          />
        ) : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  overlay: {
    bottom: 0,
    left: 0,
    position: 'absolute',
    right: 0,
    top: 0,
    zIndex: 20,
  },
  backdrop: {
    bottom: 0,
    left: 0,
    position: 'absolute',
    right: 0,
    top: 0,
  },
  sheet: {
    backgroundColor: colors.white,
    borderTopLeftRadius: 10,
    borderTopRightRadius: 10,
    boxShadow: '0px -4px 8px rgba(0, 0, 0, 0.1)',
    minHeight: 190,
    paddingHorizontal: 20,
    paddingTop: 14,
    bottom: 0,
    elevation: 20,
    left: 0,
    position: 'absolute',
    right: 0,
  },
  twoColumnSheet: {},
  title: { ...typography.bodySemiBold16, color: colors.black, textAlign: 'center' },
  divider: { backgroundColor: colors.gray200, height: 1, marginTop: 12 },
  simpleOptions: { gap: 2, paddingTop: 8 },
  optionRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: 8,
    minHeight: 36,
    paddingHorizontal: 8,
  },
  optionText: { ...typography.bodyRegular16, color: colors.gray600 },
  selectedText: { color: colors.green400 },
  columns: { flex: 1, flexDirection: 'row' },
  column: { flex: 1 },
  secondaryColumn: { borderLeftColor: colors.gray200, borderLeftWidth: 1 },
  columnTitle: {
    ...typography.bodySemiBold16,
    color: colors.gray800,
    height: 36,
    paddingHorizontal: 8,
    paddingTop: 10,
  },
  columnOption: { justifyContent: 'center', minHeight: 36, paddingHorizontal: 8 },
  columnOptionSelected: { backgroundColor: '#EBEAE5' },
  columnOptionText: { ...typography.bodyRegular16, color: colors.gray700 },
  calendarContent: {},
  quickPeriods: { flexDirection: 'row', gap: 6, marginBottom: 10 },
  quickPeriod: {
    alignItems: 'center',
    borderColor: colors.gray200,
    borderRadius: radius.pill,
    borderWidth: 1,
    flex: 1,
    height: 34,
    justifyContent: 'center',
  },
  quickPeriodSelected: { backgroundColor: '#ECFDF3', borderColor: colors.green400 },
  quickPeriodText: { ...typography.captionSemiBold12, color: colors.gray500 },
  quickPeriodTextSelected: { color: colors.green400 },
  endBoundary: { marginBottom: 16 },
  boundaryLabel: {
    minHeight: 32,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  dateLabel: { ...typography.bodySemiBold16, color: colors.gray800 },
  dateValue: { ...typography.captionSemiBold12, color: colors.gray600 },
  downArrow: { alignItems: 'center', marginVertical: 2 },
  periodActions: { flexDirection: 'row', gap: 10, marginTop: 16 },
  resetButton: {
    alignItems: 'center',
    borderColor: colors.gray300,
    borderRadius: radius.md,
    borderWidth: 1,
    height: 48,
    justifyContent: 'center',
    width: 92,
  },
  resetButtonText: { ...typography.bodySemiBold16, color: colors.gray600 },
  applyButton: {
    alignItems: 'center',
    backgroundColor: colors.green400,
    borderRadius: radius.md,
    flex: 1,
    height: 48,
    justifyContent: 'center',
  },
  applyButtonText: { ...typography.bodySemiBold16, color: colors.white },
});
