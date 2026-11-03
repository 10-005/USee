import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import ReactTestRenderer from 'react-test-renderer';
import type { RootStackParamList } from '../../app/navigation/types';
import { Button } from '../../components/common';
import { MockAdoptionActivityScreen } from './MockAdoptionActivityScreen';

jest.mock(
  'react-native-safe-area-context',
  () => require('react-native-safe-area-context/jest/mock').default,
);

test('activity screen has one action connected to the existing AR walk', async () => {
  const navigate = jest.fn();
  const goBack = jest.fn();
  const props = {
    navigation: { navigate, goBack },
    route: { key: 'activity', name: 'MockAdoptionActivity', params: { petId: 'cat-2' } },
  } as unknown as NativeStackScreenProps<RootStackParamList, 'MockAdoptionActivity'>;
  let renderer: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<MockAdoptionActivityScreen {...props} />);
  });
  expect(renderer!.root.findAllByType(Button)).toHaveLength(1);
  const button = renderer!.root.findByType(Button);
  expect(button.props.children).toBe('AR 산책하기');
  await ReactTestRenderer.act(() => button.props.onPress());
  expect(navigate).toHaveBeenCalledWith('ArWalk');
  await ReactTestRenderer.act(() => {
    renderer.root.findAllByProps({ accessibilityLabel: '뒤로가기' })[0].props.onPress();
  });
  expect(goBack).toHaveBeenCalledTimes(1);
  await ReactTestRenderer.act(() => renderer.unmount());
});
