import renderer, { act } from 'react-test-renderer';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { usePetStore } from '../../../stores/petStore';
import { PetDetailScreen } from '../PetDetailScreen';

jest.mock('react-native/Libraries/Alert/Alert', () => ({ alert: jest.fn() }));

describe('PetDetailScreen', () => {
  beforeEach(() => usePetStore.getState().resetPets());

  it('상세 정보를 확인하고 반려동물을 삭제한다', () => {
    const goBack = jest.fn();
    let screen!: renderer.ReactTestRenderer;

    act(() => {
      screen = renderer.create(
        <SafeAreaProvider
          initialMetrics={{
            frame: { height: 917, width: 412, x: 0, y: 0 },
            insets: { bottom: 0, left: 0, right: 0, top: 0 },
          }}
        >
          <PetDetailScreen
            navigation={{ goBack } as never}
            route={{ key: 'PetDetail', name: 'PetDetail', params: { petId: 'mongsil' } }}
          />
        </SafeAreaProvider>,
      );
    });

    expect(screen.root.findByProps({ children: '몽실이' })).toBeDefined();
    act(() => screen.root.findByProps({ accessibilityLabel: '반려동물 메뉴' }).props.onPress());
    act(() => screen.root.findByProps({ accessibilityLabel: '반려동물 삭제' }).props.onPress());
    act(() =>
      screen.root.findByProps({ accessibilityLabel: '반려동물 삭제 확인' }).props.onPress(),
    );

    expect(usePetStore.getState().pets.find(pet => pet.id === 'mongsil')).toBeUndefined();
    expect(goBack).toHaveBeenCalledTimes(1);
  });
});
