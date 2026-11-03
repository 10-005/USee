import renderer, { act } from 'react-test-renderer';

import { PetListScreen } from '../PetListScreen';
import { usePetStore } from '../../../stores/petStore';

jest.mock('react-native/Libraries/Alert/Alert', () => ({ alert: jest.fn() }));

describe('PetListScreen', () => {
  beforeEach(() => usePetStore.getState().resetPets());

  it('등록된 반려동물 목록과 추가 등록 버튼을 표시한다', () => {
    let screen!: renderer.ReactTestRenderer;

    act(() => {
      screen = renderer.create(
        <PetListScreen
          navigation={{ goBack: jest.fn() } as never}
          route={{ key: 'PetList', name: 'PetList' }}
        />,
      );
    });

    expect(screen.root.findByProps({ accessibilityLabel: '등록된 반려동물 2마리' })).toBeDefined();
    expect(screen.root.findByProps({ accessibilityLabel: '몽실이 상세보기' })).toBeDefined();
    expect(screen.root.findByProps({ accessibilityLabel: '반려동물 추가 등록' })).toBeDefined();
  });
});
