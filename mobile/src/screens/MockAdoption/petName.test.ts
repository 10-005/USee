import { getPetCallName, getPetNameError } from './petName';

test.each(['초코', '별', '가나다라마바', '힣'])('accepts completed Korean name %s', name => {
  expect(getPetNameError(name)).toBeUndefined();
});
test.each([
  '',
  'ㄱ',
  'ㅏ',
  '초ㅋ',
  '초',
  '초코야잘지내렴',
  'choco',
  '초코1',
  '초 코',
  ' 초코',
  '초코!',
  '🐶',
])('rejects invalid name %s', name => {
  expect(getPetNameError(name)).toBeDefined();
});
test('uses the appropriate Korean calling suffix', () => {
  expect(getPetCallName('초코')).toBe('초코야');
  expect(getPetCallName('별')).toBe('별아');
});
