import { parseWalkVoiceCommand } from './voiceCommand';

test.each(['초코', '초코야', '초코야!'])('recognizes the saved name %s as attention', text => {
  expect(parseWalkVoiceCommand(text, '초코')).toBe('look');
});
test('only matches the saved name and gives action commands precedence', () => {
  expect(parseWalkVoiceCommand('초코야')).toBeNull();
  expect(parseWalkVoiceCommand('초코야', '보리')).toBeNull();
  expect(parseWalkVoiceCommand('초코야 뭐해', '초코')).toBeNull();
  expect(parseWalkVoiceCommand('별아', '별')).toBe('look');
  expect(parseWalkVoiceCommand('초코야 앉아', '초코')).toBe('sit');
  expect(parseWalkVoiceCommand('초코야 멈춰', '초코')).toBe('stop');
  expect(parseWalkVoiceCommand('초코야 가자', '초코')).toBe('go');
});

test('valid names ending in a command word can still be called', () => {
  expect(parseWalkVoiceCommand('가자', '가자')).toBe('look');
  expect(parseWalkVoiceCommand('가자야 멈춰', '가자')).toBe('stop');
  expect(parseWalkVoiceCommand('앉아', '앉아')).toBe('look');
  expect(parseWalkVoiceCommand('앉아야 앉아', '앉아')).toBe('sit');
});

test.each([
  ['멈춰', 'stop'],
  ['초코야 멈춰!', 'stop'],
  ['멈춰 줘', 'stop'],
  ['가자', 'go'],
  ['초코야가자', 'go'],
  ['앉아 멈춰', 'stop'],
])('%s maps to %s', (text, command) => {
  expect(parseWalkVoiceCommand(text)).toBe(command);
});

test.each(['앉아', '초코야 앉아', '초코야앉아!', '보리야 앉아 줘', '앉으세요'])(
  '%s requests sitting',
  text => {
    expect(parseWalkVoiceCommand(text)).toBe('sit');
  },
);

test.each(['', '초코야', '이리 와', '앉아 하지 마', '앉아서 기다려'])(
  '%s is not a sit command',
  text => {
    expect(parseWalkVoiceCommand(text)).toBeNull();
  },
);
