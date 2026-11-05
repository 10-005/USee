export const ONBOARDING_NAME_ALERT = '올바른 이름을 입력을 해주세요';

export const isValidNickname = (value: string) =>
  value.length >= 2 &&
  value.length <= 10 &&
  !/\s/.test(value) &&
  /^[가-힣ㄱ-ㅎㅏ-ㅣa-zA-Z0-9,._]+$/.test(value);

export const isValidName = (value: string) =>
  value.length > 0 && /^[가-힣ㄱ-ㅎㅏ-ㅣa-zA-Z]+$/.test(value);

export const formatPhoneNumber = (value: string) => {
  const digits = value.replace(/\D/g, '').slice(0, 11);
  if (digits.length <= 3) {
    return digits;
  }
  if (digits.length <= 7) {
    return `${digits.slice(0, 3)}-${digits.slice(3)}`;
  }
  return `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7)}`;
};

export const isValidPhoneNumber = (value: string) => /^01[016789]-\d{3,4}-\d{4}$/.test(value);

export const formatBirthDate = (date: Date) =>
  `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(
    date.getDate(),
  ).padStart(2, '0')}`;
