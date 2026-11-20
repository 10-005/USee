export function formatReportDateTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  const period = date.getHours() < 12 ? '오전' : '오후';
  const hour = date.getHours() % 12 || 12;
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const minute = String(date.getMinutes()).padStart(2, '0');

  return `${date.getFullYear()}-${month}-${day} ${period} ${hour}:${minute}`;
}
