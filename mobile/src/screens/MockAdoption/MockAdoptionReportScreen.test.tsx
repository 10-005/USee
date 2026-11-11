import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import ReactTestRenderer from 'react-test-renderer';
import { StyleSheet } from 'react-native';

import type { RootStackParamList } from '../../app/navigation/types';
import {
  getSimulationReport,
  type SimulationReportDetail,
} from '../../services/simulation/reports';
import { MockAdoptionReportScreen } from './MockAdoptionReportScreen';
import { won } from './reportLabels';

const navigation = { goBack: jest.fn(), navigate: jest.fn() };
const screenProps = {
  navigation,
  route: { key: 'report', name: 'MockAdoptionReport', params: { sessionSeq: 3001 } },
} as unknown as NativeStackScreenProps<RootStackParamList, 'MockAdoptionReport'>;

jest.mock('../../services/simulation/reports', () => ({
  acknowledgeSimulationReport: jest.fn(),
  getSimulationReport: jest.fn(),
  getSimulationReportErrorMessage: () => '리포트를 불러오지 못했어요. 다시 시도해주세요.',
}));

jest.mock(
  'react-native-safe-area-context',
  () => require('react-native-safe-area-context/jest/mock').default,
);

const detail: SimulationReportDetail = {
  sessionSeq: 3001,
  status: 'ABANDONED',
  isFinal: true,
  animalName: '초코',
  breed: {
    breedSeq: '21',
    breedName: '말티즈',
    speciesType: 'DOG',
    representativeImageUrl: 'https://example.test/maltese.png',
  },
  speciesType: 'DOG',
  personality: 'FRIENDLY',
  startedDttm: '2026-11-01T14:00:00+09:00',
  endedDttm: '2026-11-03T10:20:00+09:00',
  expectedCost: 150000,
  totalSpent: 52800,
  difference: 97200,
  spentByCategory: {
    FOOD_BOWL: 10000,
    WATER_BOWL: 10000,
    LITTER_BOX: 0,
    TOY: 0,
    FURNITURE: 0,
    LEASH: 0,
    GROOMING_KIT: 0,
    FOOD: 21200,
    SNACK: 0,
    LITTER: 0,
    SHAMPOO: 0,
    MEDICINE: 0,
    POOP_BAG: 0,
    CATNIP: 0,
    EVENT: 11600,
  },
  dailySpent: { 1: 31200, 2: 21600, 3: 0, 4: 0, 5: 0, 6: 0, 7: 0 },
  finalStats: {
    satiety: 55,
    hydration: 60,
    cleanliness: 70,
    bond: 42,
    calmness: 52,
    health: 84,
    energy: 30,
  },
  actionCount: 12,
  eventCount: 2,
  eventResolvedCount: 1,
  walkCount: 3,
  summary: '2일째에 돌봄을 그만뒀어요.',
};

beforeEach(() => {
  navigation.goBack.mockClear();
  navigation.navigate.mockClear();
  jest.mocked(getSimulationReport).mockReset();
  jest.useRealTimers();
});

async function renderReport() {
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(async () => {
    renderer = ReactTestRenderer.create(<MockAdoptionReportScreen {...screenProps} />);
    await Promise.resolve();
    await Promise.resolve();
  });
  return renderer;
}

test('shows the finished report sections from the web report layout', async () => {
  jest.mocked(getSimulationReport).mockResolvedValue(detail);
  const renderer = await renderReport();

  expect(renderer.root.findAllByProps({ children: '초코' }).length).toBeGreaterThan(0);
  expect(renderer.root.findAllByProps({ children: '중도 포기' }).length).toBeGreaterThan(0);
  expect(renderer.root.findAllByProps({ children: '개 · 말티즈' }).length).toBeGreaterThan(0);
  expect(
    renderer.root.findAllByProps({ children: '2026.11.01 14:00 ~ 2026.11.03 10:20' }).length,
  ).toBeGreaterThan(0);
  expect(renderer.root.findAllByProps({ children: '사람 친화적' }).length).toBeGreaterThan(0);
  expect(
    renderer.root.findAllByProps({ children: '2일째에 돌봄을 그만뒀어요.' }).length,
  ).toBeGreaterThan(0);
  expect(renderer.root.findAllByProps({ children: won(52800) }).length).toBeGreaterThan(0);
  expect(renderer.root.findAllByProps({ children: `${won(97200)} 절약` }).length).toBeGreaterThan(
    0,
  );
  expect(
    renderer.root.findAllByProps({ accessibilityLabel: `선택한 지출 식사 ${won(41200)}` }).length,
  ).toBeGreaterThan(0);
  expect(renderer.root.findAllByProps({ children: '1일차 지출' }).length).toBeGreaterThan(0);
  expect(renderer.root.findAllByProps({ children: won(31200) }).length).toBeGreaterThan(0);
  const firstDay = renderer.root.findByProps({
    accessibilityLabel: `1일차 지출 ${won(31200)}`,
  });
  expect(
    firstDay.findAll(node => {
      const style = StyleSheet.flatten(node.props.style);
      return (
        typeof style?.width === 'number' &&
        style.width >= 12 &&
        typeof style?.height === 'number' &&
        style.height >= 10 &&
        style.height < 168
      );
    }).length,
  ).toBeGreaterThan(0);
  expect(renderer.root.findAllByProps({ children: '12회' }).length).toBeGreaterThan(0);
  expect(renderer.root.findAllByProps({ children: '3회' }).length).toBeGreaterThan(0);
  expect(renderer.root.findAllByProps({ children: '해결 1건' }).length).toBeGreaterThan(0);
  expect(renderer.root.findAllByProps({ children: '미해결 1건' }).length).toBeGreaterThan(0);
  expect(renderer.root.findAllByProps({ children: '84%' }).length).toBeGreaterThan(0);

  await ReactTestRenderer.act(() => {
    renderer.root.findByProps({ accessibilityLabel: `생활 ${won(11600)}` }).props.onPress();
  });
  expect(
    renderer.root.findAllByProps({ accessibilityLabel: `선택한 지출 생활 ${won(11600)}` }).length,
  ).toBeGreaterThan(0);

  await ReactTestRenderer.act(() => {
    renderer.root.findByProps({ accessibilityLabel: `2일차 지출 ${won(21600)}` }).props.onPress();
  });
  expect(renderer.root.findAllByProps({ children: '2일차 지출' }).length).toBeGreaterThan(0);
  expect(renderer.root.findAllByProps({ children: won(21600) }).length).toBeGreaterThan(0);

  await ReactTestRenderer.act(() => {
    renderer.root.findByProps({ accessibilityLabel: '뒤로 가기' }).props.onPress();
  });
  expect(navigation.goBack).toHaveBeenCalledTimes(1);
  await ReactTestRenderer.act(() => renderer.unmount());
});

test('expands grouped purchase amounts and collapses them again', async () => {
  jest.mocked(getSimulationReport).mockResolvedValue(detail);
  const renderer = await renderReport();
  try {
    const meal = () => renderer.root.findByProps({ accessibilityLabel: `식사 ${won(41200)}` });
    expect(meal().props.accessibilityState.expanded).toBe(false);
    expect(renderer.root.findAllByProps({ accessibilityLabel: '식사 구매 내역' })).toHaveLength(0);
    await ReactTestRenderer.act(() => meal().props.onPress());
    const purchases = renderer.root.findByProps({ accessibilityLabel: '식사 구매 내역' });
    expect(purchases.findAllByProps({ children: '사료' }).length).toBeGreaterThan(0);
    expect(purchases.findAllByProps({ children: '밥그릇' }).length).toBeGreaterThan(0);
    expect(purchases.findAllByProps({ children: '물그릇' }).length).toBeGreaterThan(0);
    expect(purchases.findAllByProps({ children: won(21200) }).length).toBeGreaterThan(0);
    await ReactTestRenderer.act(() => meal().props.onPress());
    expect(renderer.root.findAllByProps({ accessibilityLabel: '식사 구매 내역' })).toHaveLength(0);
    await ReactTestRenderer.act(() => {
      renderer.root.findByProps({ accessibilityLabel: '간식 0원' }).props.onPress();
    });
    expect(
      renderer.root.findAllByProps({ children: '구매 내역이 없어요.' }).length,
    ).toBeGreaterThan(0);
  } finally {
    await ReactTestRenderer.act(() => renderer.unmount());
  }
});

test('marks spending over the expected cost', async () => {
  jest.mocked(getSimulationReport).mockResolvedValue({
    ...detail,
    expectedCost: 100000,
    totalSpent: 120000,
    difference: -20000,
    spentByCategory: {
      ...detail.spentByCategory,
      EVENT: 120000,
      FOOD: 0,
      FOOD_BOWL: 0,
      WATER_BOWL: 0,
    },
    dailySpent: { 1: 120000, 2: 0, 3: 0, 4: 0, 5: 0, 6: 0, 7: 0 },
  });
  const renderer = await renderReport();
  const over = renderer.root.findByProps({ children: `${won(20000)} 초과` });
  expect(StyleSheet.flatten(over.props.style).color).toBe('#FA705B');
  await ReactTestRenderer.act(() => renderer.unmount());
});

test('shows a report-shaped skeleton while the detail is loading', async () => {
  let resolveReport: (value: SimulationReportDetail) => void = () => undefined;
  jest.mocked(getSimulationReport).mockImplementation(
    () =>
      new Promise(resolve => {
        resolveReport = resolve;
      }),
  );
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(async () => {
    renderer = ReactTestRenderer.create(<MockAdoptionReportScreen {...screenProps} />);
  });

  expect(
    renderer.root.findAllByProps({ accessibilityLabel: '리포트 불러오는 중' }).length,
  ).toBeGreaterThan(0);
  const host = (node: ReactTestRenderer.ReactTestInstance) => typeof node.type === 'string';
  expect(
    renderer.root.findAll(
      node =>
        host(node) &&
        StyleSheet.flatten(node.props.style)?.width === 220 &&
        StyleSheet.flatten(node.props.style)?.height === 220,
    ).length,
  ).toBe(2);
  expect(
    renderer.root.findAll(node => host(node) && StyleSheet.flatten(node.props.style)?.width === 18)
      .length,
  ).toBe(7);

  await ReactTestRenderer.act(async () => {
    resolveReport(detail);
    await Promise.resolve();
    await Promise.resolve();
  });
  expect(renderer.root.findAllByProps({ children: '초코' }).length).toBeGreaterThan(0);
  expect(renderer.root.findAllByProps({ accessibilityLabel: '리포트 불러오는 중' }).length).toBe(0);
  await ReactTestRenderer.act(() => renderer.unmount());
});

test('asks again when the report cannot be loaded', async () => {
  jest
    .mocked(getSimulationReport)
    .mockRejectedValueOnce(new Error('offline'))
    .mockResolvedValueOnce(detail);
  const renderer = await renderReport();
  expect(
    renderer.root.findAllByProps({ children: '리포트를 불러오지 못했어요. 다시 시도해주세요.' })
      .length,
  ).toBeGreaterThan(0);
  await ReactTestRenderer.act(async () => {
    await renderer.root.findByProps({ accessibilityLabel: '리포트 다시 불러오기' }).props.onPress();
    await Promise.resolve();
    await Promise.resolve();
  });
  expect(renderer.root.findAllByProps({ children: '초코' }).length).toBeGreaterThan(0);
  await ReactTestRenderer.act(() => renderer.unmount());
});

test('refreshes until the finished summary is ready', async () => {
  jest.useFakeTimers();
  jest
    .mocked(getSimulationReport)
    .mockResolvedValueOnce({ ...detail, summary: null })
    .mockResolvedValueOnce(detail);
  const renderer = await renderReport();
  expect(renderer.root.findAllByProps({ children: '요약 준비 중' }).length).toBeGreaterThan(0);

  await ReactTestRenderer.act(async () => {
    jest.advanceTimersByTime(10_000);
    await Promise.resolve();
    await Promise.resolve();
  });
  expect(
    renderer.root.findAllByProps({ children: '2일째에 돌봄을 그만뒀어요.' }).length,
  ).toBeGreaterThan(0);
  await ReactTestRenderer.act(() => renderer.unmount());
});
