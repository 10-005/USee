import {
  formatBirthDate,
  formatPhoneNumber,
  isValidName,
  isValidNickname,
  isValidPhoneNumber,
} from '../onboardingValidation';

describe('onboardingValidation', () => {
  it('닉네임 길이, 공백, 허용 특수문자를 검증한다', () => {
    expect(isValidNickname('치')).toBe(false);
    expect(isValidNickname('치이카와입니다요요요요')).toBe(false);
    expect(isValidNickname('치이 카와')).toBe(false);
    expect(isValidNickname('치이카와!')).toBe(false);
    expect(isValidNickname('치이,카와')).toBe(false);
    expect(isValidNickname('치이카와._')).toBe(true);
  });

  it('이름에는 한글과 영문만 허용한다', () => {
    expect(isValidName('')).toBe(false);
    expect(isValidName('박범준')).toBe(true);
    expect(isValidName('Park')).toBe(true);
    expect(isValidName('박범준1')).toBe(false);
    expect(isValidName('박 범준')).toBe(false);
  });

  it('전화번호와 생년월일 표시 형식을 만든다', () => {
    expect(formatPhoneNumber('01012345678')).toBe('010-1234-5678');
    expect(isValidPhoneNumber('010-1234-5678')).toBe(true);
    expect(formatBirthDate(new Date(2001, 4, 27))).toBe('2001.05.27');
  });
});
