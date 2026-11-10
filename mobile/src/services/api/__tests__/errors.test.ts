import { ApiClientError, getApiFieldError } from '../errors';

describe('getApiFieldError', () => {
  it('returns the server validation reason for the requested field', () => {
    const error = new ApiClientError({
      code: 'COMMON_INVALID_INPUT',
      details: [
        { field: 'phone', reason: '전화번호 형식이 올바르지 않습니다.' },
        { field: 'birthDate', reason: '날짜 형식이 올바르지 않습니다.' },
      ],
      message: '입력값이 올바르지 않습니다.',
      status: 400,
    });

    expect(getApiFieldError(error, 'phone')).toBe('전화번호 형식이 올바르지 않습니다.');
  });

  it('returns null when the response has no matching field error', () => {
    expect(getApiFieldError(new Error('network'), 'phone')).toBeNull();
  });
});
