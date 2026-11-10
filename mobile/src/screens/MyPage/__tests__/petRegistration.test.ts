import { MAX_IMAGE_SIZE_BYTES } from '../../../constants/upload';
import { normalizePetSpecies, petOptions, validatePetImage } from '../petRegistration';

describe('pet registration helpers', () => {
  it('서버에서 받은 동물 종류를 수정 화면 초기값으로 유지한다', () => {
    expect(normalizePetSpecies('개')).toBe('개');
    expect(normalizePetSpecies('고양이')).toBe('고양이');
    expect(normalizePetSpecies('기타')).toBe('기타');
  });

  it('provides dependent breed and color options by species', () => {
    const dog = petOptions('강아지', '도고 아르젠티노', '');
    expect(dog.breeds.filter(name => name === '도고 아르젠티노')).toHaveLength(1);
    expect(dog.breeds[dog.breeds.length - 1]).toBe('기타');
    expect(dog.colors).toEqual(['은색&흰색', '흰색', '밝은 금색', '살구색', '크림색', '기타']);
    const cat = petOptions('고양이', '러시안 블루', '');
    expect(cat.breeds[cat.breeds.length - 1]).toBe('기타');
    expect(cat.colors).toEqual(['파란색', '푸른 회색', '기타']);
    expect(petOptions('기타', '', '').breeds).toEqual(['기타축종']);
  });

  it('accepts jpg and png images up to 10MB', () => {
    expect(
      validatePetImage({
        fileSize: MAX_IMAGE_SIZE_BYTES,
        type: 'image/jpeg',
        uri: 'file://pet.jpg',
      }),
    ).toBeNull();
    expect(
      validatePetImage({ fileSize: 100, type: 'image/png', uri: 'file://pet.png' }),
    ).toBeNull();
  });

  it('rejects unsupported or oversized images', () => {
    expect(
      validatePetImage({
        fileSize: MAX_IMAGE_SIZE_BYTES + 1,
        type: 'image/jpeg',
        uri: 'file://pet.jpg',
      }),
    ).toContain('10MB');
    expect(validatePetImage({ fileSize: 100, type: 'image/gif', uri: 'file://pet.gif' })).toContain(
      'JPG',
    );
  });
});
