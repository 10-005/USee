import renderer, { act } from 'react-test-renderer';

import { getMyPet, getMyPets } from '../../../services/pets';
import { usePetStore } from '../../../stores/petStore';
import { PetListScreen } from '../PetListScreen';

jest.mock('@react-navigation/native', () => ({
  ...jest.requireActual('@react-navigation/native'),
  useFocusEffect: (callback: () => void) =>
    jest.requireActual('react').useEffect(callback, [callback]),
}));
jest.mock('../../../services/pets', () => ({
  getMyPet: jest.fn(),
  getMyPets: jest.fn(),
}));

const mockGetMyPets = getMyPets as jest.Mock;
const pet = {
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
  gender: 'FEMALE' as const,
  name: '몽실이',
  petSeq: 5,
  regDttm: '2026-11-09T10:15:30+09:00',
  representativeImageUrl: 'https://example.com/pet.jpg',
};

describe('PetListScreen', () => {
  beforeEach(() => {
    usePetStore.getState().resetPets();
    mockGetMyPets.mockReset();
  });

  it('목록을 불러오는 동안 스켈레톤을 표시한다', () => {
    mockGetMyPets.mockReturnValue(new Promise(() => undefined));
    let screen!: renderer.ReactTestRenderer;

    act(() => {
      screen = renderer.create(
        <PetListScreen
          navigation={{ goBack: jest.fn() } as never}
          route={{ key: 'PetList', name: 'PetList' }}
        />,
      );
    });

    expect(
      screen.root.findByProps({ accessibilityLabel: '반려동물 목록 불러오는 중' }),
    ).toBeDefined();
    act(() => screen.unmount());
  });

  it('서버에서 조회한 반려동물 목록과 추가 등록 버튼을 표시한다', async () => {
    (getMyPet as jest.Mock).mockResolvedValueOnce({ age: '2살' });
    mockGetMyPets.mockResolvedValue({
      content: [pet],
      hasNext: false,
      page: 0,
      size: 100,
      totalElements: 1,
      totalPages: 1,
    });
    let screen!: renderer.ReactTestRenderer;

    await act(async () => {
      screen = renderer.create(
        <PetListScreen
          navigation={{ goBack: jest.fn() } as never}
          route={{ key: 'PetList', name: 'PetList' }}
        />,
      );
    });

    expect(screen.root.findByProps({ accessibilityLabel: '등록된 반려동물 1마리' })).toBeDefined();
    expect(screen.root.findByProps({ accessibilityLabel: '몽실이 상세보기' })).toBeDefined();
    expect(JSON.stringify(screen.toJSON())).toContain('2살');
    expect(screen.root.findByProps({ accessibilityLabel: '반려동물 추가 등록' })).toBeDefined();
    act(() => screen.unmount());
  });

  it('등록된 반려동물이 없으면 추가 등록 카드만 표시한다', async () => {
    mockGetMyPets.mockResolvedValue({
      content: [],
      hasNext: false,
      page: 0,
      size: 100,
      totalElements: 0,
      totalPages: 0,
    });
    let screen!: renderer.ReactTestRenderer;

    await act(async () => {
      screen = renderer.create(
        <PetListScreen
          navigation={{ goBack: jest.fn() } as never}
          route={{ key: 'PetList', name: 'PetList' }}
        />,
      );
    });

    expect(screen.root.findAllByProps({ children: '등록된 반려동물이 없어요.' })).toHaveLength(0);
    expect(screen.root.findAllByProps({ children: '함께하는 반려동물을 등록해 보세요.' })).toHaveLength(0);
    expect(screen.root.findByProps({ children: '반려동물 추가 등록' })).toBeDefined();
    act(() => screen.unmount());
  });
});
