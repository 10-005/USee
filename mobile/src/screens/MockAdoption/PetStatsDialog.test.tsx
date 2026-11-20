import ReactTestRenderer from 'react-test-renderer';
import { Modal } from 'react-native';
import { PetStatsDialog } from './PetStatsDialog';

test('shows seven unknown stats and supports all dialog dismissal paths', async () => {
  const onClose = jest.fn();
  let renderer: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(
      <PetStatsDialog visible petName="시바 이누" onClose={onClose} />,
    );
  });
  for (const label of ['포만감', '수분', '청결도', '친밀도', '안정감', '건강', '활력']) {
    const progress = renderer!.root.findAllByProps({ accessibilityLabel: label })[0];
    expect(progress.props.accessibilityValue).toEqual({ text: '상태 정보 없음' });
  }
  const close = renderer!.root.findAllByProps({ accessibilityLabel: '상태 다이얼로그 닫기' })[0];
  await ReactTestRenderer.act(() => close.props.onPress());
  const backdrop = renderer!.root.findAllByProps({
    accessibilityLabel: '상태 다이얼로그 바깥 영역',
  })[0];
  await ReactTestRenderer.act(() => backdrop.props.onPress());
  await ReactTestRenderer.act(() => renderer.root.findByType(Modal).props.onRequestClose());
  expect(onClose).toHaveBeenCalledTimes(3);
  await ReactTestRenderer.act(() => renderer.unmount());
});

test('shows skeleton rows until the stats arrive', async () => {
  let renderer: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(
      <PetStatsDialog visible loading petName="시바 이누" onClose={jest.fn()} />,
    );
  });
  expect(
    renderer!.root.findAllByProps({ accessibilityLabel: '포만감 불러오는 중' }).length,
  ).toBeGreaterThan(0);
  expect(renderer!.root.findAllByProps({ accessibilityLabel: '포만감' })).toHaveLength(0);
  expect(renderer!.root.findAllByProps({ children: '아직 상태 정보가 없어요.' })).toHaveLength(0);
  await ReactTestRenderer.act(() => renderer.unmount());
});

test('supports actual zero and hundred values, clamps invalid bounds, leaves missing data unknown', async () => {
  let renderer: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(
      <PetStatsDialog
        visible
        petName="시바 이누"
        onClose={jest.fn()}
        stats={{ satiety: 0, hydration: 100, calmness: 120, energy: -5, health: Number.NaN }}
      />,
    );
  });
  const value = (label: string) =>
    renderer!.root.findAllByProps({ accessibilityLabel: label })[0].props.accessibilityValue;
  expect(value('포만감')).toEqual({ min: 0, max: 100, now: 0 });
  expect(value('수분')).toEqual({ min: 0, max: 100, now: 100 });
  expect(value('안정감').now).toBe(100);
  expect(value('활력').now).toBe(0);
  expect(value('건강')).toEqual({ text: '상태 정보 없음' });
  expect(value('친밀도')).toEqual({ text: '상태 정보 없음' });
  await ReactTestRenderer.act(() => renderer.unmount());
});
