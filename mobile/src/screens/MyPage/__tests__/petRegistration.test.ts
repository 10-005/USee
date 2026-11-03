import { MAX_IMAGE_SIZE_BYTES } from '../../../constants/upload';
import { PET_OPTIONS, validatePetImage } from '../petRegistration';

describe('pet registration helpers', () => {
  it('provides dependent breed and color options by species', () => {
    expect(PET_OPTIONS.강아지.breeds).toContain('푸들');
    expect(PET_OPTIONS.고양이.colors).toContain('치즈색');
    expect(PET_OPTIONS.기타.breeds).toContain('기타');
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
