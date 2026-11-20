import ReactTestRenderer from 'react-test-renderer';
import { FlatList } from 'react-native';
import { DatePickerModal } from './DatePickerModal';

it('applies the centered year and month when dismissing either backdrop', async () => {
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  const close = jest.fn();
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(
      <DatePickerModal
        visible
        title="날짜 선택"
        value={new Date(2001, 1, 1)}
        minimumDate={new Date(2000, 0, 1)}
        maximumDate={new Date(2005, 11, 31)}
        onClose={close}
        onConfirm={jest.fn()}
      />,
    );
  });
  const button = (label: string) => renderer.root.findAllByProps({ accessibilityLabel: label })[0];
  for (const [field, index, backdrop, expected] of [
    ['연도 선택', 5, '목록 닫기', '2005년'],
    ['월 선택', 0, '날짜 선택 닫기', '1월'],
  ] as const) {
    await ReactTestRenderer.act(() => button(field).props.onPress());
    const wheel = renderer.root.findByType(FlatList).props;
    const row = wheel.getItemLayout(wheel.data, 0).length;
    const padding = (wheel.data.length - wheel.snapToOffsets.length) / 2;
    expect(padding).toBe(2);
    expect(wheel.data[index + padding]).toBe(parseInt(expected, 10));
    await ReactTestRenderer.act(() => {
      wheel.onScroll({ nativeEvent: { contentOffset: { y: index * row } } });
      button(backdrop).props.onPress();
    });
    expect(button(field).props.accessibilityValue.text).toBe(expected);
  }
  expect(close).not.toHaveBeenCalled();
  await ReactTestRenderer.act(() => renderer.unmount());
});

it('jumps between years and months and confirms the chosen day within the date limits', async () => {
  const onConfirm = jest.fn();
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(
      <DatePickerModal
        visible
        value={new Date(1982, 3, 1)}
        minimumDate={new Date(1980, 1, 15)}
        maximumDate={new Date(2025, 2, 12)}
        onClose={jest.fn()}
        onConfirm={onConfirm}
      />,
    );
  });
  const options = (label: string) => renderer.root.findAllByProps({ accessibilityLabel: label });
  const press = async (label: string) => {
    await ReactTestRenderer.act(() => options(label)[0].props.onPress());
  };
  await press('연도 선택');
  await press('1980년');
  await press('월 선택');
  expect(options('1월')).toHaveLength(0);
  await press('2월');
  expect(options('1980년 2월 14일')[0].props.disabled).toBe(true);
  await press('1980년 2월 29일');
  expect(onConfirm).not.toHaveBeenCalled();
  await press('날짜 선택 확인');
  expect(onConfirm).toHaveBeenCalledWith(new Date(1980, 1, 29));
  await ReactTestRenderer.act(() => renderer.unmount());
});

jest.mock(
  'react-native-safe-area-context',
  () => require('react-native-safe-area-context/jest/mock').default,
);

it.each([
  { value: new Date(2024, 0, 31), expected: 1, blocked: '이전 달' },
  { value: new Date(2024, 2, 1), expected: 29, blocked: '다음 달' },
])(
  'confirms an allowed date when the saved value is $value',
  async ({ value, expected, blocked }) => {
    const onConfirm = jest.fn();
    let renderer!: ReactTestRenderer.ReactTestRenderer;
    await ReactTestRenderer.act(() => {
      renderer = ReactTestRenderer.create(
        <DatePickerModal
          minimumDate={new Date(2024, 1, 1)}
          maximumDate={new Date(2024, 1, 29)}
          onClose={jest.fn()}
          onConfirm={onConfirm}
          value={value}
          visible
        />,
      );
    });
    expect(renderer.root.findAllByProps({ accessibilityLabel: blocked })).toHaveLength(0);
    const confirm = renderer.root.findAllByProps({ accessibilityLabel: '날짜 선택 확인' })[0];
    expect(confirm.props.disabled).toBe(false);
    confirm.props.onPress();
    expect(onConfirm).toHaveBeenCalledWith(new Date(2024, 1, expected));
    await ReactTestRenderer.act(() => renderer.unmount());
  },
);
