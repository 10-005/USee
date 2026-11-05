// Names are optional: match the command at the end, including attached Korean names.
export function parseWalkVoiceCommand(transcript: string): 'sit' | 'stop' | 'go' | null {
  const normalized = transcript.replace(/[\s.,!?。？！]/g, '');
  if (/(?:앉아|앉아줘|앉으렴|앉으세요)$/.test(normalized)) {
    return 'sit';
  }
  if (/(?:멈춰|멈춰줘)$/.test(normalized)) {
    return 'stop';
  }
  return /가자$/.test(normalized) ? 'go' : null;
}
