export const MIN_WEEKLY_BUDGET = 10_000;
export const MAX_WEEKLY_BUDGET = 100_000_000;

export function getBudgetError(value: string): string | undefined {
  if (!value) {
    return '초기 준비 비용을 포함한 첫 주 예산을 입력해주세요.';
  }
  if (!/^[0-9]+$/.test(value)) {
    return '숫자만 입력해주세요.';
  }
  const amount = Number(value);
  if (amount < MIN_WEEKLY_BUDGET) {
    return '최소 10,000원 이상 입력해주세요.';
  }
  if (!Number.isSafeInteger(amount) || amount > MAX_WEEKLY_BUDGET) {
    return '입력 가능한 금액을 초과했어요. 1억 원 이하로 입력해주세요.';
  }
  return undefined;
}
