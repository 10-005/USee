import AsyncStorage from '@react-native-async-storage/async-storage';
import {
  getActiveMockAdoption,
  startMockAdoption,
} from '../../services/storage/mockAdoptionStorage';
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

test('quitting clears the session and replaces the main screen with fresh selection', async () => {
  await AsyncStorage.clear();
  await startMockAdoption('dog-1', 35000);
  const replace = jest.fn();
  const props = {
    navigation: { replace },
    route: { key: 'activity', name: 'MockAdoptionActivity', params: { petId: 'dog-1' } },
  } as unknown as NativeStackScreenProps<RootStackParamList, 'MockAdoptionActivity'>;
  let renderer: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<MockAdoptionActivityScreen {...props} />);
  });
  await ReactTestRenderer.act(async () => {
    await renderer.root
      .findAllByProps({ accessibilityLabel: '모의입양 그만두기' })[0]
      .props.onPress();
  });
  expect(replace).toHaveBeenCalledWith('MockAdoptionSelection');
  await expect(getActiveMockAdoption()).resolves.toBeNull();
  await expect(startMockAdoption('cat-2', 50000)).resolves.toMatchObject({
    petId: 'cat-2',
    weeklyBudget: 50000,
  });
  await ReactTestRenderer.act(() => renderer.unmount());
  await AsyncStorage.clear();
});
