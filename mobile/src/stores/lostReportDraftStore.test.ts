import { useLostReportDraftStore } from './lostReportDraftStore';
import type { Pet } from './petStore';

const pet: Pet = {
  age: 4,
  breed: '푸들',
  color: '갈색',
  description: '온순함',
  gender: '암컷',
  id: 'pet-1',
  image: { uri: 'main.jpg' },
  images: [{ uri: 'main.jpg' }, { uri: 'sub.jpg' }],
  microchipNumber: '1234',
  name: '보리',
  neutered: true,
  species: '강아지',
  weight: 5,
};

describe('lostReportDraftStore', () => {
  beforeEach(() => useLostReportDraftStore.getState().resetSelection());

  it('선택한 반려동물 정보를 신고서 초깃값으로 복사한다', () => {
    useLostReportDraftStore.getState().selectPet(pet);

    const state = useLostReportDraftStore.getState();
    expect(state.entryMode).toBe('pet');
    expect(state.selectedPetId).toBe(pet.id);
    expect(state.animal).toMatchObject({
      breed: pet.breed,
      name: pet.name,
      sourcePetId: pet.id,
      species: pet.species,
    });
    expect(state.animal.images).toEqual(pet.images);
    expect(state.animal.images).not.toBe(pet.images);
  });

  it('신고서 값을 수정해도 원본 반려동물 정보는 변경하지 않는다', () => {
    useLostReportDraftStore.getState().selectPet(pet);
    useLostReportDraftStore.getState().updateAnimal({ name: '수정한 이름' });

    expect(useLostReportDraftStore.getState().animal.name).toBe('수정한 이름');
    expect(pet.name).toBe('보리');
  });

  it('직접 입력 선택 시 빈 신고서 초깃값을 만든다', () => {
    useLostReportDraftStore.getState().selectPet(pet);
    useLostReportDraftStore.getState().selectManual();

    const state = useLostReportDraftStore.getState();
    expect(state.entryMode).toBe('manual');
    expect(state.selectedPetId).toBeNull();
    expect(state.animal).toMatchObject({
      images: [],
      name: '',
      sourcePetId: null,
      species: '',
    });
  });
});
