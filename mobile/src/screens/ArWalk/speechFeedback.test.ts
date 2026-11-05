import { getWalkSpeechErrorMessage } from './speechFeedback';

test.each([
  ['network', '인터넷 확인'],
  ['audio', '마이크 오류'],
  ['busy', '마이크 사용 중'],
  ['language', '한국어'],
  ['rateLimit', '요청이 많아요'],
  ['service', '음성 서비스 오류'],
  [undefined, '인식 중단'],
])('provides reason-specific recovery feedback for %s', (reason, expected) => {
  expect(getWalkSpeechErrorMessage(reason)).toContain(expected);
});

test.each(['network', 'audio', 'busy', 'language', 'rateLimit', undefined])(
  'reports retry progress separately from a stopped session: %s',
  reason => {
    const message = getWalkSpeechErrorMessage(reason, true);
    expect(message).not.toContain('껐다');
    expect(message).not.toContain('인식 중단');
  },
);
