jest.mock('../../api/client', () => ({
  apiClient: { delete: jest.fn(), get: jest.fn(), patch: jest.fn(), post: jest.fn() },
}));

import { apiClient } from '../../api/client';
import {
  createAdoptionDraft,
  getAdoptionQuestions,
  getMyAdoptionApplications,
  getShelterAnimal,
  searchShelterAnimals,
  submitAdoptionApplication,
} from '../adoptionApi';
import { getShelter } from '../shelterApi';

const mockGet = apiClient.get as jest.Mock;
const mockPost = apiClient.post as jest.Mock;

describe('adoptionApi', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('보호동물 목록과 상세를 조회한다', async () => {
    const page = { content: [], hasNext: false, page: 0, size: 100 };
    const detail = { animalSeq: 700 };
    mockGet
      .mockResolvedValueOnce({ data: { data: page } })
      .mockResolvedValueOnce({ data: { data: detail } });

    await expect(searchShelterAnimals({ keyword: '말티즈' })).resolves.toEqual(page);
    await expect(getShelterAnimal(700)).resolves.toEqual(detail);
    expect(mockGet).toHaveBeenNthCalledWith(1, '/shelter-animals', {
      params: { keyword: '말티즈', page: 0, size: 100, sort: 'regDttm,desc' },
    });
    expect(mockGet).toHaveBeenNthCalledWith(2, '/shelter-animals/700');
  });

  it('입양 문항과 내 신청 목록을 조회한다', async () => {
    const questions = [{ questionSeq: 1 }];
    const page = { content: [], totalElements: 0 };
    mockGet
      .mockResolvedValueOnce({ data: { data: { questions } } })
      .mockResolvedValueOnce({ data: { data: page } });

    await expect(getAdoptionQuestions()).resolves.toEqual(questions);
    await expect(getMyAdoptionApplications({ animalSeq: 700 })).resolves.toEqual(page);
    expect(mockGet).toHaveBeenNthCalledWith(1, '/adoption/questions');
    expect(mockGet).toHaveBeenNthCalledWith(2, '/adoption-applications', {
      params: { animalSeq: 700, page: 0, size: 100 },
    });
  });

  it('입양 신청서를 임시저장하고 제출한다', async () => {
    const form = { animalSeq: 700, answers: { '1': '아파트' } };
    mockPost
      .mockResolvedValueOnce({ data: { data: { applicationSeq: 1, status: 'DRAFT' } } })
      .mockResolvedValueOnce({ data: { data: { applicationSeq: 1, status: 'SUBMITTED' } } });

    await createAdoptionDraft(form);
    await submitAdoptionApplication(form);
    expect(mockPost).toHaveBeenNthCalledWith(1, '/adoption-applications', form);
    expect(mockPost).toHaveBeenNthCalledWith(2, '/adoption-applications/submit', form);
  });

  it('보호소 공개 상세를 조회한다', async () => {
    const shelter = { shelterName: '행복동물보호센터', shelterSeq: 7 };
    mockGet.mockResolvedValue({ data: { data: shelter } });

    await expect(getShelter(7)).resolves.toEqual(shelter);
    expect(mockGet).toHaveBeenCalledWith('/shelters/7');
  });
});
