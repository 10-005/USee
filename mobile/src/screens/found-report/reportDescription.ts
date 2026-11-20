// The API stores both form fields in one description. Keep a labelled boundary
// so a multiline feature or situation survives the next edit.
const situationBoundary = '\n\n발견 상황\n';

export function joinReportDescription(features: string, situation: string) {
  return [features.trim(), '발견 상황\n' + situation.trim()].filter(Boolean).join('\n\n');
}

export function splitReportDescription(value: string) {
  if (value.startsWith('발견 상황\n')) {
    return { description: '', foundSituation: value.slice('발견 상황\n'.length) };
  }
  const boundary = value.indexOf(situationBoundary);
  if (boundary >= 0) {
    return {
      description: value.slice(0, boundary),
      foundSituation: value.slice(boundary + situationBoundary.length),
    };
  }
  // Older mobile versions omitted empty features and joined fields with a newline.
  const newline = value.indexOf('\n');
  return newline < 0
    ? { description: '', foundSituation: value }
    : { description: value.slice(0, newline), foundSituation: value.slice(newline + 1) };
}
