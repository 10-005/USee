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

jest.mock('./RoomPet', () => ({ RoomPet: 'RoomPet' }));
let mockFocusRoom: () => () => void;
jest.mock('@react-navigation/native', () => ({
  useFocusEffect: (effect: () => () => void) => {
    mockFocusRoom = effect;
    require('react').useEffect(effect, [effect]);
  },
}));

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
  await ReactTestRenderer.act(() => {
    renderer.root
      .findAll(node => typeof node.props.onLayout === 'function')[0]
      .props.onLayout({
        nativeEvent: { layout: { width: 390, height: 844 } },
      });
  });
  const roomPet = () => renderer.root.findByType('RoomPet' as never);
  expect(roomPet().props.returnSequence).toBe(0);
  let blurRoom: () => void;
  await ReactTestRenderer.act(() => {
    blurRoom = mockFocusRoom();
  });
  await ReactTestRenderer.act(() => button.props.onPress());
  expect(navigate).toHaveBeenCalledWith('ArWalk');
  await ReactTestRenderer.act(() => blurRoom());
  expect(renderer!.root.findAllByType('RoomPet' as never)).toHaveLength(0);
  await ReactTestRenderer.act(() => {
    mockFocusRoom();
  });
  expect(roomPet().props.returnSequence).toBe(1);
  // Refocusing without another AR visit must not request a second entrance.
  await ReactTestRenderer.act(() => {
    mockFocusRoom();
  });
  expect(roomPet().props.returnSequence).toBe(1);
  expect(roomPet().props.entryOffset.x).toBeGreaterThan(0);
  expect(roomPet().props.entryOffset.y).toBeGreaterThan(0);
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

test.each([
  ['dog-1', '빈 배변 패드', '빈 고양이 화장실'],
  ['cat-2', '빈 고양이 화장실', '빈 배변 패드'],
])('shows the empty toilet for %s', async (petId, expected, absent) => {
  const props = {
    navigation: {},
    route: { key: 'activity', name: 'MockAdoptionActivity', params: { petId } },
  } as unknown as NativeStackScreenProps<RootStackParamList, 'MockAdoptionActivity'>;
  let renderer: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<MockAdoptionActivityScreen {...props} />);
  });
  expect(renderer!.root.findAllByProps({ accessibilityLabel: expected }).length).toBeGreaterThan(0);
  expect(renderer!.root.findAllByProps({ accessibilityLabel: absent })).toHaveLength(0);
  await ReactTestRenderer.act(() => {
    renderer.root
      .findAll(node => typeof node.props.onLayout === 'function')[0]
      .props.onLayout({
        nativeEvent: { layout: { width: 390, height: 844 } },
      });
  });
  expect(renderer!.root.findByType('RoomPet' as never).props.petId).toBe(petId);
  await ReactTestRenderer.act(() => renderer.unmount());
});
