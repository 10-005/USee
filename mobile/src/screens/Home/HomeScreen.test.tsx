import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import ReactTestRenderer from 'react-test-renderer';

import type { RootStackParamList } from '../../app/navigation/types';
import { HomeScreen } from './HomeScreen';

const mockNavigate = jest.fn();

jest.mock('@react-navigation/native', () => ({
  ...jest.requireActual('@react-navigation/native'),
  useNavigation: () => ({ navigate: mockNavigate }),
}));

jest.mock(
  'react-native-safe-area-context',
  () => require('react-native-safe-area-context/jest/mock').default,
);

test('mock adoption tab opens the AR entry screen and preserves adoption filters', async () => {
  const props = {
    navigation: { navigate: mockNavigate },
    route: { key: 'home', name: 'Home' },
  } as unknown as NativeStackScreenProps<RootStackParamList, 'Home'>;
  let renderer: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<HomeScreen {...props} />);
  });
  expect(renderer!.root.findAllByProps({ accessibilityLabel: '기간 필터' }).length).toBeGreaterThan(
    0,
  );
  await ReactTestRenderer.act(() => {
    renderer.root
      .findAllByProps({ placeholder: '특징으로 검색해보세요' })[0]
      .props.onChangeText('진도');
  });
  await ReactTestRenderer.act(() => {
    renderer.root.findAllByProps({ accessibilityLabel: '모의입양' })[0].props.onPress();
  });
  expect(
    renderer!.root.findAllByProps({ accessibilityLabel: '모의입양' })[0].props.accessibilityState,
  ).toEqual({ selected: true });
  expect(renderer!.root.findAllByProps({ accessibilityLabel: '기간 필터' })).toHaveLength(0);
  await ReactTestRenderer.act(() => {
    renderer.root.findAllByProps({ accessibilityLabel: 'AR 산책' })[0].props.onPress();
  });
  expect(mockNavigate).toHaveBeenCalledWith('ArWalk');
  await ReactTestRenderer.act(() => {
    renderer.root.findAllByProps({ accessibilityLabel: '입양' })[0].props.onPress();
  });
  expect(
    renderer!.root.findAllByProps({ placeholder: '특징으로 검색해보세요' })[0].props.value,
  ).toBe('진도');
  await ReactTestRenderer.act(() => renderer.unmount());
}, 20000);
