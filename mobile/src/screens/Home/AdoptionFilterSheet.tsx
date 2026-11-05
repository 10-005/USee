import { useEffect, useMemo, useState } from 'react';
import { BackHandler, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import FilterArrowIcon from '../../assets/icons/common/filter-arrow.svg';
import LeftArrowIcon from '../../assets/icons/common/left-arrow.svg';
import RightArrowIcon from '../../assets/icons/common/right-arrow.svg';
import SelectedIcon from '../../assets/icons/navigation/adoption-active.svg';
import { colors, radius, typography } from '../../theme';

export type FilterKey = 'period' | 'region' | 'species' | 'status' | 'gender' | 'neutered' | 'sort';
export type AdoptionFilters = Record<FilterKey, string>;

type Props = {
  activeFilter: FilterKey | null;
  filters: AdoptionFilters;
  onChange: (key: FilterKey, value: string) => void;
  onClose: () => void;
  statusOptions?: string[];
};

const titles: Record<FilterKey, string> = {
  period: '기간설정',
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
  value,
  onChange,
}: {
  kind: 'region' | 'species';
  value: string;
  onChange: (value: string) => void;
}) {
  const source = kind === 'region' ? regions : species;
  const initialPrimary = value.includes(' > ') ? value.split(' > ')[0] : value;
  const [primary, setPrimary] = useState(source[initialPrimary] ? initialPrimary : '전체');
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
              style={[styles.columnOption, primary === option && styles.columnOptionSelected]}
            >
              <Text style={styles.columnOptionText}>{option}</Text>
            </Pressable>
          ))}
        </ScrollView>
      </View>
      <View style={[styles.column, styles.secondaryColumn]}>
        <Text style={styles.columnTitle}>{kind === 'region' ? '시군구' : '종'}</Text>
        <ScrollView decelerationRate="fast" showsVerticalScrollIndicator={false}>
          {source[primary].map(option => (
            <Pressable
              key={option}
              onPress={() => onChange(option === '전체' ? primary : `${primary} > ${option}`)}
              style={[
                styles.columnOption,
                (secondary === option || (option === '전체' && value === primary)) &&
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
  const [start = '전체', end = formatDate(new Date())] = value.split('|');
  return { start, end };
};

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
  const [visibleDate, setVisibleDate] = useState(today);
  const year = visibleDate.getFullYear();
  const month = visibleDate.getMonth() + 1;
  const firstDay = new Date(year, month - 1, 1).getDay();
  const lastDate = new Date(year, month, 0).getDate();
  const cellCount = Math.ceil((firstDay + lastDate) / 7) * 7;
  const days = useMemo(
    () => [
      ...Array.from({ length: firstDay }, () => 0),
      ...Array.from({ length: lastDate }, (_, index) => index + 1),
    ],
    [firstDay, lastDate],
  );
  const activeValue = draftPeriod[activeBoundary];
  const selectedDay = activeValue.startsWith(`${year}.${String(month).padStart(2, '0')}.`)
    ? Number(activeValue.split('.')[2])
    : 0;

  const selectDate = (day: number) => {
    const selected = `${year}.${String(month).padStart(2, '0')}.${String(day).padStart(2, '0')}`;
    if (activeBoundary === 'start') {
      setDraftPeriod(current => ({
        end: selected > current.end ? selected : current.end,
        start: selected,
      }));
      setActiveBoundary('end');
      return;
    }
    setDraftPeriod(current =>
      current.start !== '전체' && selected < current.start
        ? { start: selected, end: current.start }
        : { ...current, end: selected },
    );
  };

  const moveMonth = (offset: number) => {
    setVisibleDate(current => new Date(current.getFullYear(), current.getMonth() + offset, 1));
  };

  const chooseQuickPeriod = (kind: 'all' | 'today' | 'week' | 'month') => {
    if (kind === 'all') {
      setDraftPeriod({ start: '전체', end: todayText });
      setActiveBoundary('start');
      return;
    }
    const start = new Date(today);
    if (kind === 'week') {
      start.setDate(start.getDate() - 6);
    }
    if (kind === 'month') {
      start.setMonth(start.getMonth() - 1);
    }
    setDraftPeriod({ start: formatDate(start), end: todayText });
    setActiveBoundary('end');
  };

  const weekStart = new Date(today);
  weekStart.setDate(weekStart.getDate() - 6);
  const monthStart = new Date(today);
  monthStart.setMonth(monthStart.getMonth() - 1);
  const quickPeriod =
    draftPeriod.start === '전체' && draftPeriod.end === todayText
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
      <View style={styles.dateLabels}>
        <Text style={styles.dateLabel}>시작일</Text>
        <Text style={styles.dateLabel}>종료일</Text>
      </View>
      <View style={styles.periodFields}>
        <Pressable
          onPress={() => setActiveBoundary('start')}
          style={[styles.dateField, activeBoundary === 'start' && styles.dateFieldSelected]}
        >
          <Text style={[styles.dateFieldText, activeBoundary === 'start' && styles.selectedText]}>
            {draftPeriod.start}
          </Text>
          <FilterArrowIcon height={20} width={20} />
        </Pressable>
        <Text style={styles.rangeText}>→</Text>
        <Pressable
          onPress={() => setActiveBoundary('end')}
          style={[styles.dateField, activeBoundary === 'end' && styles.dateFieldSelected]}
        >
          <Text style={[styles.dateFieldText, activeBoundary === 'end' && styles.selectedText]}>
            {draftPeriod.end}
          </Text>
          <FilterArrowIcon height={20} width={20} />
        </Pressable>
      </View>
      <View style={styles.calendarHeader}>
        <Pressable
          accessibilityLabel="이전 달"
          onPress={() => moveMonth(-1)}
          style={styles.monthMove}
        >
          <LeftArrowIcon />
        </Pressable>
        <Text style={styles.calendarMonth}>{`${year}년 ${month}월`}</Text>
        <Pressable
          accessibilityLabel="다음 달"
          onPress={() => moveMonth(1)}
          style={styles.monthMove}
        >
          <RightArrowIcon />
        </Pressable>
      </View>
      <View style={styles.weekRow}>
        {['일', '월', '화', '수', '목', '금', '토'].map(day => (
          <Text key={day} style={styles.weekText}>
            {day}
          </Text>
        ))}
      </View>
      <View style={styles.calendarGrid}>
        {Array.from({ length: cellCount / 7 }, (_unusedWeek, weekIndex) => (
          <View key={weekIndex} style={styles.calendarWeek}>
            {Array.from({ length: 7 }, (_unusedDay, dayIndex) => {
              const index = weekIndex * 7 + dayIndex;
              const day = days[index] ?? 0;
              const date = day
                ? `${year}.${String(month).padStart(2, '0')}.${String(day).padStart(2, '0')}`
                : '';
              const inRange =
                day > 0 &&
                draftPeriod.start !== '전체' &&
                date >= draftPeriod.start &&
                date <= draftPeriod.end;
              const endpoint = day > 0 && (date === draftPeriod.start || date === draftPeriod.end);
              return (
                <Pressable
                  key={`${day}-${index}`}
                  disabled={!day}
                  onPress={() => selectDate(day)}
                  style={[
                    styles.day,
                    inRange && styles.rangeDay,
                    (endpoint || (day > 0 && day === selectedDay)) && styles.selectedDay,
                  ]}
                >
                  {day ? (
                    <Text style={[styles.dayText, endpoint && styles.selectedDayText]}>{day}</Text>
                  ) : null}
                </Pressable>
              );
            })}
          </View>
        ))}
      </View>
      <View style={styles.periodActions}>
        <Pressable
          onPress={() => {
            setDraftPeriod({ start: '전체', end: todayText });
            setActiveBoundary('start');
          }}
          style={styles.resetButton}
        >
          <Text style={styles.resetButtonText}>초기화</Text>
        </Pressable>
        <Pressable
          onPress={() => {
            onApply(`${draftPeriod.start}|${draftPeriod.end}`);
            onClose();
          }}
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
          { paddingBottom: (activeFilter === 'period' ? 16 : 28) + insets.bottom },
          (activeFilter === 'region' || activeFilter === 'species') && [
            styles.twoColumnSheet,
            { height: 333 + insets.bottom },
          ],
        ]}
      >
        <Text style={styles.title}>{titles[activeFilter]}</Text>
        <View style={styles.divider} />
        {activeFilter === 'period' ? (
          <CalendarFilter
            onApply={value => onChange('period', value)}
            onClose={onClose}
            value={filters.period}
          />
        ) : null}
        {activeFilter === 'region' || activeFilter === 'species' ? (
          <TwoColumnFilter
            kind={activeFilter}
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
  quickPeriods: { flexDirection: 'row', gap: 6, marginTop: 16 },
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
  dateLabels: { flexDirection: 'row', gap: 28, marginTop: 12 },
  dateLabel: { ...typography.captionSemiBold12, color: colors.gray500, flex: 1 },
  periodFields: { alignItems: 'center', flexDirection: 'row', gap: 8, marginTop: 6 },
  dateField: {
    alignItems: 'center',
    borderColor: colors.gray200,
    borderRadius: radius.md,
    borderWidth: 1,
    flex: 1,
    flexDirection: 'row',
    height: 46,
    justifyContent: 'space-between',
    padding: 10,
  },
  dateFieldSelected: { borderColor: colors.green400 },
  dateFieldText: { ...typography.bodyRegular14, color: colors.gray600 },
  rangeText: { ...typography.bodySemiBold16, color: colors.gray600 },
  calendarHeader: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 12,
  },
  calendarMonth: { ...typography.bodySemiBold16, color: colors.gray800 },
  monthMove: { alignItems: 'center', height: 40, justifyContent: 'center', width: 40 },
  weekRow: {
    flexDirection: 'row',
    gap: 5,
    justifyContent: 'center',
    marginTop: 4,
  },
  weekText: {
    ...typography.captionSemiBold12,
    color: colors.black,
    textAlign: 'center',
    width: 40,
  },
  calendarGrid: {
    gap: 4,
    marginTop: 6,
  },
  calendarWeek: { flexDirection: 'row', gap: 5, justifyContent: 'center' },
  day: {
    alignItems: 'center',
    backgroundColor: '#F7F6F2',
    borderRadius: radius.md,
    height: 40,
    justifyContent: 'center',
    width: 40,
  },
  rangeDay: { backgroundColor: '#DCFCE7' },
  selectedDay: { backgroundColor: colors.green400 },
  selectedDayText: { color: colors.white },
  dayText: { ...typography.captionSemiBold12, color: colors.black },
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
