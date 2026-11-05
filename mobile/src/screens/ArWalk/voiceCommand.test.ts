import { parseWalkVoiceCommand } from './voiceCommand';

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
