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

export const getNicknameError = (nickname: string) => {
  const trimmed = nickname.trim();
  if (!trimmed) {
    return '닉네임을 입력해주세요.';
  }
  if (!/^[가-힣ㄱ-ㅎㅏ-ㅣa-zA-Z0-9._]{2,10}$/.test(trimmed)) {
    return '한글, 영문, 숫자와 . _를 사용해 2~10자로 입력해주세요.';
  }
  return null;
};

export const getNameError = (name: string) => {
  const trimmed = name.trim();
  if (!trimmed) {
    return '이름을 입력해주세요.';
  }
  if (!/^[가-힣a-zA-Z ]{2,20}$/.test(trimmed)) {
    return '한글 또는 영문 2~20자로 입력해주세요.';
  }
  return null;
};

export const isValidPhoneNumber = (phone: string) => /^01[016789]-\d{3,4}-\d{4}$/.test(phone);

export const parseDate = (value: string) => {
  const [year, month, day] = value.split('.').map(Number);
  const date = new Date(year, month - 1, day);
  return Number.isNaN(date.getTime()) ? new Date() : date;
};

export const formatDate = (date: Date) =>
  `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(
    date.getDate(),
  ).padStart(2, '0')}`;
