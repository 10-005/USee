import {
  formatDate,
  formatPhoneNumber,
  getNameError,
  getNicknameError,
  isValidPhoneNumber,
  parseDate,
} from '../profileValidation';

describe('profileValidation', () => {
  it('휴대폰 번호를 입력 중 자동으로 구분한다', () => {
    expect(formatPhoneNumber('01012345678')).toBe('010-1234-5678');
    expect(isValidPhoneNumber('010-1234-5678')).toBe(true);
  });

  it('닉네임 형식과 로컬 중복값을 검증한다', () => {
    expect(getNicknameError('한', '치이카와')).not.toBeNull();
    expect(getNicknameError('관리자', '치이카와')).toBe('이미 사용 중인 닉네임입니다.');
    expect(getNicknameError('새닉네임', '치이카와')).toBeNull();
  });

  it('이름은 한글 또는 영문만 허용한다', () => {
    expect(getNameError('박범준')).toBeNull();
    expect(getNameError('박범준1')).not.toBeNull();
  });

  it('날짜 표시값을 Date로 변환하고 다시 표시한다', () => {
    expect(formatDate(parseDate('2001.05.27'))).toBe('2001.05.27');
  });
});
