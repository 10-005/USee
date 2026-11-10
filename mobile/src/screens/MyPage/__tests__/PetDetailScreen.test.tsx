import renderer, { act } from 'react-test-renderer';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { deleteMyPet, getMyPet } from '../../../services/pets';
import { usePetStore } from '../../../stores/petStore';
import { PetDetailScreen } from '../PetDetailScreen';

jest.mock('@react-navigation/native', () => ({
  ...jest.requireActual('@react-navigation/native'),
  useFocusEffect: (callback: () => void) =>
    jest.requireActual('react').useEffect(callback, [callback]),
}));
jest.mock('../../../services/pets', () => ({
  deleteMyPet: jest.fn(),
  getMyPet: jest.fn(),
}));
jest.mock('react-native/Libraries/Alert/Alert', () => ({ alert: jest.fn() }));

const mockDeleteMyPet = deleteMyPet as jest.Mock;
const mockGetMyPet = getMyPet as jest.Mock;
const detail = {
  age: '8살',
  classification: {
    breedName: '말티즈',
    breedSeq: 12,
    colorName: '흰색',
    colorSeq: 3,
    combinationSeq: 88,
    speciesCode: 'DOG' as const,
    speciesName: '개',
    speciesSeq: 1,
  },
  description: '꼬리 끝이 뭉툭함',
  gender: 'FEMALE' as const,
  images: [{ displayOrder: 1, imageSeq: 1001, imageUrl: 'https://example.com/pet.jpg' }],
  isNeutered: true,
  microchipNumber: '1234567890000',
  modDttm: '2026-11-09T11:20:00+09:00',
  name: '몽실이',
  petSeq: 5,
  regDttm: '2026-11-09T10:15:30+09:00',
  representativeImageUrl: 'https://example.com/pet.jpg',
  weight: '5kg',
};

describe('PetDetailScreen', () => {
  beforeEach(() => {
    usePetStore.getState().resetPets();
    mockGetMyPet.mockReset().mockResolvedValue(detail);
    mockDeleteMyPet.mockReset().mockResolvedValue(undefined);
  });

  it('서버에서 조회한 반려동물 상세 정보를 표시한다', async () => {
    let screen!: renderer.ReactTestRenderer;

    await act(async () => {
      screen = renderer.create(
        <SafeAreaProvider
          initialMetrics={{
            frame: { height: 917, width: 412, x: 0, y: 0 },
            insets: { bottom: 0, left: 0, right: 0, top: 0 },
          }}
        >
          <PetDetailScreen
            navigation={{ goBack: jest.fn() } as never}
            route={{ key: 'PetDetail', name: 'PetDetail', params: { petId: '5' } }}
          />
        </SafeAreaProvider>,
      );
    });

    expect(screen.root.findByProps({ children: '몽실이' })).toBeDefined();
    act(() => screen.root.findByProps({ accessibilityLabel: '반려동물 메뉴' }).props.onPress());
    expect(screen.root.findByProps({ accessibilityLabel: '반려동물 삭제' })).toBeDefined();
    act(() =>
      screen.root.findByProps({ accessibilityLabel: '반려동물 메뉴 닫기' }).props.onPress(),
    );
    expect(screen.root.findAllByProps({ accessibilityLabel: '반려동물 삭제' })).toHaveLength(0);
    act(() => screen.unmount());
  }, 15_000);

  it('삭제 중에는 중복 요청을 막고 로딩 상태를 표시한다', async () => {
    let resolveDelete!: () => void;
    mockDeleteMyPet.mockImplementation(
      () => new Promise<void>(resolve => (resolveDelete = resolve)),
    );
    const goBack = jest.fn();
    let screen!: renderer.ReactTestRenderer;

    await act(async () => {
      screen = renderer.create(
        <SafeAreaProvider
          initialMetrics={{
            frame: { height: 917, width: 412, x: 0, y: 0 },
            insets: { bottom: 0, left: 0, right: 0, top: 0 },
          }}
        >
          <PetDetailScreen
            navigation={{ goBack } as never}
            route={{ key: 'PetDetail', name: 'PetDetail', params: { petId: '5' } }}
          />
        </SafeAreaProvider>,
      );
    });

    act(() => screen.root.findByProps({ accessibilityLabel: '반려동물 메뉴' }).props.onPress());
    act(() => screen.root.findByProps({ accessibilityLabel: '반려동물 삭제' }).props.onPress());

    const deleteButton = screen.root.findByProps({ accessibilityLabel: '반려동물 삭제 확인' });
    await act(async () => {
      deleteButton.props.onPress();
    });

    const deletingButton = screen.root.findByProps({ accessibilityLabel: '반려동물 삭제 중' });
    expect(deletingButton.props.disabled).toBe(true);
    expect(mockDeleteMyPet).toHaveBeenCalledTimes(1);

    await act(async () => {
      resolveDelete();
    });

    expect(goBack).toHaveBeenCalledTimes(1);
    act(() => screen.unmount());
  });
});
