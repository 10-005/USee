// Names are optional: match the command at the end, including attached Korean names.
export function parseWalkVoiceCommand(
  transcript: string,
  petName = '',
): 'sit' | 'stop' | 'go' | 'look' | 'spin' | null {
  const normalized = transcript.replace(/[\s.,!?。？！]/g, '');
  // An exact saved name is a call even if the name itself ends in a command word.
  // A name followed by a separate command still reaches the command checks below.
  if (petName && [petName, `${petName}야`, `${petName}아`].includes(normalized)) {
    return 'look';
  }
  if (/(?:앉아|앉아줘|앉으렴|앉으세요)$/.test(normalized)) {
    return 'sit';
  }
  if (/(?:멈춰|멈춰줘)$/.test(normalized)) {
    return 'stop';
  }
  if (/가자$/.test(normalized)) {
    return 'go';
  }
  if (/(?:돌아|돌아줘)$/.test(normalized)) {
    return 'spin';
  }
  return null;
}
