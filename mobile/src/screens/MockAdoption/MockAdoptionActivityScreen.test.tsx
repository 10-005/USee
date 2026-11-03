import { STORAGE_KEYS } from '../../constants/storageKeys';
import AsyncStorage from '@react-native-async-storage/async-storage';
import {
  getActiveMockAdoption,
  startMockAdoption,
} from '../../services/storage/mockAdoptionStorage';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import ReactTestRenderer from 'react-test-renderer';
import { BackHandler } from 'react-native';
import type { RootStackParamList } from '../../app/navigation/types';
import { MockAdoptionActivityScreen } from './MockAdoptionActivityScreen';
import { PetStatsDialog } from './PetStatsDialog';

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

test('system back waits for the model surface to disappear and ignores repeated requests', async () => {
  let systemBack: () => boolean | null | undefined = () => false;
  const subscribe = jest
    .spyOn(BackHandler, 'addEventListener')
    .mockImplementation((_event, callback) => {
      systemBack = () => callback({ type: 'hardwareBackPress', timeStamp: 0 });
      return { remove: jest.fn() };
    });
  const goBack = jest.fn();
  const props = {
    navigation: { goBack },
    route: { key: 'activity', name: 'MockAdoptionActivity', params: { petId: 'dog-1' } },
  } as unknown as NativeStackScreenProps<RootStackParamList, 'MockAdoptionActivity'>;
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<MockAdoptionActivityScreen {...props} />);
  });
  await ReactTestRenderer.act(() =>
    renderer.root
      .findAll(node => typeof node.props.onLayout === 'function')[0]
      .props.onLayout({
        nativeEvent: { layout: { width: 390, height: 844 } },
      }),
  );
  await ReactTestRenderer.act(() => {
    expect(systemBack()).toBe(true);
    expect(systemBack()).toBe(true);
  });
  expect(goBack).not.toHaveBeenCalled();
  const onHidden = renderer!.root.findByType('RoomPet' as never).props.onHidden;
  await ReactTestRenderer.act(() => {
    onHidden();
    onHidden();
  });
  expect(goBack).toHaveBeenCalledTimes(1);
  await ReactTestRenderer.act(() => renderer.unmount());
  subscribe.mockRestore();
});

test('activity screen has one action connected to the existing AR walk', async () => {
  const navigate = jest.fn();
  const goBack = jest.fn();
  const props = {
    navigation: { navigate, goBack },
    route: { key: 'activity', name: 'MockAdoptionActivity', params: { petId: 'cat-2' } },
  } as unknown as NativeStackScreenProps<RootStackParamList, 'MockAdoptionActivity'>;
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    renderer = ReactTestRenderer.create(<MockAdoptionActivityScreen {...props} />);
  });
  const button = renderer!.root.findAllByProps({ accessibilityLabel: 'AR 산책하기' })[0];
  expect(button.props.accessibilityRole).toBe('button');
  await ReactTestRenderer.act(() => {
    renderer.root
      .findAll(node => typeof node.props.onLayout === 'function')[0]
      .props.onLayout({
        nativeEvent: { layout: { width: 390, height: 844 } },
      });
  });
  const roomPet = () => renderer.root.findByType('RoomPet' as never);
  expect(roomPet().props.returnSequence).toBe(0);
  await ReactTestRenderer.act(() => roomPet().props.onPress());
  expect(renderer!.root.findByType(PetStatsDialog).props.visible).toBe(true);
  await ReactTestRenderer.act(() => renderer.root.findByType(PetStatsDialog).props.onClose());
  expect(renderer!.root.findByType(PetStatsDialog).props.visible).toBe(false);
  let blurRoom: () => void;
  await ReactTestRenderer.act(() => {
    blurRoom = mockFocusRoom();
  });
  await ReactTestRenderer.act(() => button.props.onPress());
  expect(navigate).not.toHaveBeenCalled();
  expect(roomPet().props.leaving).toBe(true);
  await ReactTestRenderer.act(() => roomPet().props.onHidden());
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
  expect(goBack).not.toHaveBeenCalled();
  await ReactTestRenderer.act(() => roomPet().props.onHidden());
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
  let renderer!: ReactTestRenderer.ReactTestRenderer;
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
  let renderer!: ReactTestRenderer.ReactTestRenderer;
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

test.each(['food', 'water'] as const)(
  'serves %s and refuses the fifth consecutive request',
  async kind => {
    jest.useFakeTimers();
    const props = {
      navigation: {},
      route: { key: 'activity', name: 'MockAdoptionActivity', params: { petId: 'dog-1' } },
    } as unknown as NativeStackScreenProps<RootStackParamList, 'MockAdoptionActivity'>;
    let renderer!: ReactTestRenderer.ReactTestRenderer;
    await ReactTestRenderer.act(() => {
      renderer = ReactTestRenderer.create(<MockAdoptionActivityScreen {...props} />, {
        createNodeMock: () => ({
          measureInWindow: (callback: (...args: number[]) => void) => callback(20, 500, 80, 80),
        }),
      });
    });
    await ReactTestRenderer.act(() =>
      renderer.root
        .findAll(node => typeof node.props.onLayout === 'function')[0]
        .props.onLayout({ nativeEvent: { layout: { width: 390, height: 844 } } }),
    );
    const bowl = () =>
      renderer.root.findAllByProps({
        accessibilityLabel: kind === 'food' ? '사료 그릇' : '물그릇',
      })[0];
    const pet = () => renderer.root.findByType('RoomPet' as never);
    expect(bowl().props.accessibilityValue.text).toBe('빈 그릇');
    for (let count = 0; count < 4; count++) {
      await ReactTestRenderer.act(() => bowl().props.onPress());
      expect(bowl().props.accessibilityValue.text).toBe('가득 찬 그릇');
      await ReactTestRenderer.act(() => pet().props.onConsumed());
      await ReactTestRenderer.act(() => pet().props.onCareComplete());
    }
    await ReactTestRenderer.act(() => bowl().props.onPress());
    expect(pet().props.care).toBeNull();
    expect(
      renderer.root.findAllByProps({
        children: kind === 'food' ? '지금은 배불러요' : '지금은 목마르지 않아요',
      }).length,
    ).toBeGreaterThan(0);
    await ReactTestRenderer.act(() => renderer.unmount());
    jest.useRealTimers();
  },
);

test.each([
  ['dog-1', '가득 찬 배변 패드', '빈 배변 패드'],
  ['cat-2', '가득 찬 고양이 화장실', '빈 고양이 화장실'],
])('cleans and persists the toilet for %s', async (petId, fullLabel, emptyLabel) => {
  await AsyncStorage.clear();
  const session = await startMockAdoption(petId, 35000);
  await AsyncStorage.setItem(
    STORAGE_KEYS.MOCK_ADOPTION_TOILET,
    JSON.stringify({
      petId,
      startedAt: session.startedAt,
      cleanedAt: session.startedAt - 2000,
      events: [session.startedAt - 1000, session.startedAt + 3600000],
    }),
  );
  const props = {
    navigation: {},
    route: { key: 'activity', name: 'MockAdoptionActivity', params: { petId } },
  } as unknown as NativeStackScreenProps<RootStackParamList, 'MockAdoptionActivity'>;
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(async () => {
    renderer = ReactTestRenderer.create(<MockAdoptionActivityScreen {...props} />);
  });
  await ReactTestRenderer.act(async () => {
    await renderer.root.findAllByProps({ accessibilityLabel: fullLabel })[0].props.onPress();
  });
  expect(renderer.root.findAllByProps({ accessibilityLabel: emptyLabel }).length).toBeGreaterThan(
    0,
  );
  expect(renderer.root.findAllByProps({ children: '깨끗해져서 좋아요!' }).length).toBeGreaterThan(
    0,
  );
  await ReactTestRenderer.act(() => renderer.unmount());
  await ReactTestRenderer.act(async () => {
    renderer = ReactTestRenderer.create(<MockAdoptionActivityScreen {...props} />);
  });
  expect(renderer.root.findAllByProps({ accessibilityLabel: fullLabel })).toHaveLength(0);
  expect(renderer.root.findAllByProps({ accessibilityLabel: emptyLabel }).length).toBeGreaterThan(
    0,
  );
  await ReactTestRenderer.act(() => renderer.unmount());
  await AsyncStorage.clear();
});
