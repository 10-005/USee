export function getPetNameError(name: string): string | undefined {
  if (!name) {
    return '반려동물의 이름을 입력해주세요.';
  }
  if (name.length > 6) {
    return '이름은 6글자 이내로 입력해주세요.';
  }
  if (!/^[가-힣]+$/.test(name)) {
    return '완성된 한글만 사용할 수 있어요. 공백·숫자·특수문자는 제외해주세요.';
  }
  return undefined;
}

export function getPetCallName(name: string): string {
  const hasFinalConsonant = (name.charCodeAt(name.length - 1) - 0xac00) % 28 !== 0;
  return `${name}${hasFinalConsonant ? '아' : '야'}`;
}
