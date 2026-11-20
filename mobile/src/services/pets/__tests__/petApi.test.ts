jest.mock('../../api/client', () => ({
  apiClient: { delete: jest.fn(), get: jest.fn(), patch: jest.fn(), post: jest.fn() },
}));

import { apiClient } from '../../api/client';
import {
  createPet,
  deleteMyPet,
  getAnimalBreeds,
  getAnimalCombinations,
  getAnimalSpecies,
  getMyPet,
  getMyPets,
  updateMyPet,
} from '../petApi';

const mockDelete = apiClient.delete as jest.Mock;
const mockGet = apiClient.get as jest.Mock;
const mockPatch = apiClient.patch as jest.Mock;
const mockPost = apiClient.post as jest.Mock;

describe('petApi', () => {
  beforeEach(() => {
    mockGet.mockReset();
    mockPatch.mockReset();
    mockPost.mockReset();
    mockDelete.mockReset();
  });

  it('종·품종·색상 기준정보를 조회한다', async () => {
    mockGet.mockResolvedValue({ data: { data: [] } });

    await getAnimalSpecies();
    await getAnimalBreeds(1);
    await getAnimalCombinations(21);

    expect(mockGet).toHaveBeenNthCalledWith(1, '/animal-species');
    expect(mockGet).toHaveBeenNthCalledWith(2, '/animal-species/1/breeds');
    expect(mockGet).toHaveBeenNthCalledWith(3, '/animal-combinations', {
      params: { breedSeq: 21 },
    });
  });

  it('확정된 사진 순서와 combinationSeq로 반려동물을 등록한다', async () => {
    const request = {
      age: '3살',
      combinationSeq: 88,
      description: '파란 목줄',
      gender: 'MALE' as const,
      imageSeqs: [1001, 1002],
      isNeutered: true,
      microchipNumber: '410100012345678',
      name: '초코',
      weight: '4.5kg',
    };
    const data = { imageCount: 2, petSeq: 5, representativeImageSeq: 1001 };
    mockPost.mockResolvedValue({ data: { data } });

    await expect(createPet(request)).resolves.toEqual(data);
    expect(mockPost).toHaveBeenCalledWith('/users/me/pets', request);
  });

  it('내 반려동물 목록과 상세를 조회한다', async () => {
    const page = {
      content: [],
      hasNext: false,
      page: 0,
      size: 100,
      totalElements: 0,
      totalPages: 0,
    };
    const detail = { petSeq: 5 };
    mockGet
      .mockResolvedValueOnce({ data: { data: page } })
      .mockResolvedValueOnce({ data: { data: detail } });

    await expect(getMyPets()).resolves.toEqual(page);
    await expect(getMyPet(5)).resolves.toEqual(detail);
    expect(mockGet).toHaveBeenNthCalledWith(1, '/users/me/pets', {
      params: { page: 0, size: 100, sort: 'regDttm,desc' },
    });
    expect(mockGet).toHaveBeenNthCalledWith(2, '/users/me/pets/5');
  });

  it('반려동물을 수정하고 삭제한다', async () => {
    const request = { name: '초코' };
    const detail = { name: '초코', petSeq: 5 };
    mockPatch.mockResolvedValue({ data: { data: detail } });
    mockDelete.mockResolvedValue({ status: 204 });

    await expect(updateMyPet(5, request)).resolves.toEqual(detail);
    await expect(deleteMyPet(5)).resolves.toBeUndefined();
    expect(mockPatch).toHaveBeenCalledWith('/users/me/pets/5', request);
    expect(mockDelete).toHaveBeenCalledWith('/users/me/pets/5');
  });
});
