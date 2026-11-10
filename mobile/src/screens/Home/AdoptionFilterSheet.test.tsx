import ReactTestRenderer from 'react-test-renderer';
import { StyleSheet } from 'react-native';
import { colors } from '../../theme';
import { AdoptionFilterSheet } from './AdoptionFilterSheet';

jest.mock(
  'react-native-safe-area-context',
  () => require('react-native-safe-area-context/jest/mock').default,
);

it('edits the two months independently and applies the selected inclusive date range', async () => {
  const change = jest.fn();
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(
      <AdoptionFilterSheet
        activeFilter="period"
        filters={{
          period: '2024.02.01|2024.03.31',
          region: '전체',
          species: '전체',
          status: '전체',
          gender: '전체',
          neutered: '전체',
          sort: '최신순',
        }}
        onChange={change}
        onClose={jest.fn()}
      />,
    );
  });
  const button = (label: string) => renderer.root.findAllByProps({ accessibilityLabel: label })[0];
  const press = async (label: string) => {
    await ReactTestRenderer.act(() => button(label).props.onPress());
  };
  expect(button('시작일 연도 선택').props.accessibilityValue.text).toBe('2024년');
  const borders = () =>
    ['시작일 연도', '시작일 월', '종료일 연도', '종료일 월'].map(
      field => StyleSheet.flatten(button(field + ' 선택').props.style).borderColor,
    );
  const neutral = colors.gray300;
  expect(borders()).toEqual([neutral, neutral, neutral, neutral]);
  await press('시작일 월 선택');
  expect(borders()).toEqual([neutral, colors.green400, neutral, neutral]);
  await press('1월');
  expect(borders()).toEqual([neutral, neutral, neutral, neutral]);
  await press('2024년 1월 15일');
  expect(button('종료일 날짜 선택').props.accessibilityState.selected).toBe(true);
  await press('종료일 월 선택');
  await press('2월');
  expect(button('시작일 월 선택').props.accessibilityValue.text).toBe('1월');
  expect(change).not.toHaveBeenCalled();
  await press('기간 적용');
  expect(change).toHaveBeenCalledWith('period', '2024.01.01|2024.02.15');
  const period = () =>
    ['시작일', '종료일'].map(label => button(label + ' 날짜 선택').props.accessibilityValue.text);
  await press('2024년 2월 15일');
  expect(period()).toEqual(['2024.01.01', '2024.01.01']);
  await press('2024년 1월 20일');
  await press('2024년 1월 1일');
  expect(period()).toEqual(['2024.01.20', '2024.01.20']);
  expect(button('2024년 1월 5일').props.disabled).toBe(false);
  await press('2024년 1월 5일');
  expect(period()).toEqual(['2024.01.05', '2024.01.20']);
  await press('2024년 1월 12일');
  expect(period()).toEqual(['2024.01.05', '2024.01.12']);
  await press('2024년 1월 12일');
  await press('2024년 1월 5일');
  expect(period()).toEqual(['전체', '전체']);
  await press('기간 적용');
  expect(change).toHaveBeenLastCalledWith('period', '전체');
  const now = new Date();
  await press(now.getFullYear() + '년 ' + (now.getMonth() + 1) + '월 1일');
  const first = now.getFullYear() + '.' + String(now.getMonth() + 1).padStart(2, '0') + '.01';
  expect(period()).toEqual([first, first]);
  await ReactTestRenderer.act(() => renderer.unmount());
});
