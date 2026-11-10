import ReactTestRenderer from 'react-test-renderer';

import {
  getActiveSimulation,
  type ActiveSimulation,
} from '../../services/simulation/activeSession';
import { loadSimulationReports } from '../../services/simulation/reports';
import { MockAdoptionOverviewScreen } from './MockAdoptionOverviewScreen';

jest.mock('../../services/simulation/activeSession', () => {
  const actual = jest.requireActual('../../services/simulation/activeSession');
  return {
    ...actual,
    getActiveSimulation: jest.fn().mockResolvedValue(null),
  };
});

jest.mock('../../services/simulation/reports', () => ({
  loadSimulationReports: jest.fn(),
  invalidateSimulationReports: jest.fn(),
  getSimulationReportErrorMessage: () => '리포트를 불러오지 못했어요. 다시 시도해주세요.',
}));

jest.mock('@react-navigation/native', () => ({
  useFocusEffect: (callback: () => void) => {
    require('react').useEffect(callback, [callback]);
  },
}));

jest.mock(
  'react-native-safe-area-context',
  () => require('react-native-safe-area-context/jest/mock').default,
);

const report = {
  sessionSeq: 3002,
  animalName: '나비',
  breed: {
    breedSeq: '000172',
    breedName: '러시안 블루',
    speciesType: 'CAT' as const,
    representativeImageUrl: 'https://example.test/cat.png',
  },
  status: 'ABANDONED',
  startedDttm: '2026-11-01T10:00:00+09:00',
  endedDttm: '2026-11-03T10:00:00+09:00',
  expectedCost: 150000,
  totalSpent: 40000,
  difference: 110000,
};

test('marks only reports that were not in the previous list', async () => {
  jest.mocked(loadSimulationReports).mockResolvedValue({
    reports: [report, { ...report, sessionSeq: 3001, animalName: '초코' }],
    freshSessionSeqs: [3002],
  });
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(async () => {
    renderer = ReactTestRenderer.create(
      <MockAdoptionOverviewScreen onBack={jest.fn()} onStart={jest.fn()} onResume={jest.fn()} />,
    );
    await Promise.resolve();
  });

  const titleRow = (name: string) =>
    renderer.root.findAll(
      node => Array.isArray(node.props.children) && node.props.children[0] === name,
    )[0].parent;
  expect(titleRow('나비')?.findAllByProps({ children: 'new' }).length).toBeGreaterThan(0);
  expect(titleRow('초코')?.findAllByProps({ children: 'new' })).toHaveLength(0);
  expect(renderer.root.findAllByProps({ children: '2026.11.03' }).length).toBeGreaterThan(0);
  expect(renderer.root.findAllByProps({ children: '7일간의 모의 입양이 끝났어요!' })).toHaveLength(
    0,
  );
  await ReactTestRenderer.act(() => renderer.unmount());
});

test('announces a finished adoption with the sudden event dialog', async () => {
  jest.mocked(getActiveSimulation).mockResolvedValueOnce({
    status: 'COMPLETED',
    animalName: '초코',
    breed: { breedSeq: '000018', representativeImageUrl: null },
  } as ActiveSimulation);
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(async () => {
    renderer = ReactTestRenderer.create(
      <MockAdoptionOverviewScreen onBack={jest.fn()} onStart={jest.fn()} onResume={jest.fn()} />,
    );
    await Promise.resolve();
  });
  expect(
    renderer.root.findAllByProps({ children: '7일간의 모의 입양이 끝났어요!' }).length,
  ).toBeGreaterThan(0);
  expect(
    renderer.root.findAllByProps({
      children: '발급된 리포트를 확인해 내 모의 입양 결과를 확인해볼 수 있어요',
    }).length,
  ).toBeGreaterThan(0);
  await ReactTestRenderer.act(() =>
    renderer.root.findAllByProps({ accessibilityLabel: '돌발 이벤트 확인' })[0].props.onPress(),
  );
  expect(renderer.root.findAllByProps({ children: '7일간의 모의 입양이 끝났어요!' })).toHaveLength(
    0,
  );
  await ReactTestRenderer.act(() => renderer.unmount());
});
