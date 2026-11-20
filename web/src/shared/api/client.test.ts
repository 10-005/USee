import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, requestApi, requestJson } from './client';

afterEach(() => vi.unstubAllGlobals());

describe('JSON API 클라이언트', () => {
  it('공통 성공 봉투의 null 데이터와 추적 ID를 유지한다', async () => {
    const body = {
      status: 200,
      code: 'SUCCESS',
      message: '성공',
      data: null,
      errors: [],
      traceId: 'body-trace',
    };
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          new Response(JSON.stringify(body), { headers: { 'X-Trace-Id': 'header-trace' } }),
        ),
    );
    await expect(requestApi('/core/v1/items')).resolves.toEqual({
      status: 200,
      data: null,
      traceId: 'header-trace',
      response: body,
    });
  });

  it('204 응답의 헤더 추적 ID를 반환한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          new Response(null, { status: 204, headers: { 'X-Trace-Id': 'no-content-trace' } }),
        ),
    );
    await expect(requestApi('/core/v1/items/1', { method: 'DELETE' })).resolves.toEqual({
      status: 204,
      data: undefined,
      traceId: 'no-content-trace',
    });
  });

  it('검증 오류의 코드, 메시지, 필드 오류와 추적 ID를 보존한다', async () => {
    const body = {
      status: 400,
      code: 'COMMON_INVALID_INPUT',
      message: '입력값이 올바르지 않습니다.',
      data: null,
      errors: [{ field: 'phone', reason: '형식이 아닙니다.' }],
      traceId: 'body-trace',
    };
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response(JSON.stringify(body), { status: 400 })),
    );
    await expect(requestApi('/core/v1/items')).rejects.toMatchObject({
      status: 400,
      code: body.code,
      message: body.message,
      errors: body.errors,
      traceId: 'body-trace',
      body,
    });
  });

  it('공통 응답이 아닌 성공 본문과 성공 상태의 오류 코드를 구분한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(new Response(JSON.stringify({ data: [] })))
        .mockResolvedValueOnce(
          new Response(
            JSON.stringify({
              status: 200,
              code: 'COMMON_INVALID_INPUT',
              message: '오류',
              data: null,
              errors: [],
              traceId: 'trace',
            }),
          ),
        ),
    );
    await expect(requestApi('/core/v1/items')).rejects.toThrow('공통 응답 형식');
    await expect(requestApi('/core/v1/items')).rejects.toMatchObject({
      name: 'ApiError',
      code: 'COMMON_INVALID_INPUT',
    });
  });
  it('요청 본문과 헤더를 전달하고 응답 원문을 보존한다', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(new Response(JSON.stringify({ data: { id: '1' } })));
    vi.stubGlobal('fetch', fetchMock);
    await expect(
      requestJson('/items', {
        method: 'POST',
        json: { name: 'test' },
        headers: { 'X-Request-Id': 'test' },
      }),
    ).resolves.toEqual({ data: { id: '1' } });
    const [url, init] = fetchMock.mock.calls[0]!;
    expect(url).toBe('/api/items');
    expect(init.body).toBe('{"name":"test"}');
    expect(init.headers.get('Content-Type')).toBe('application/json');
    expect(init.headers.get('X-Request-Id')).toBe('test');
  });

  it('204 응답은 undefined로 처리한다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })));
    await expect(requestJson('/items/1', { method: 'DELETE' })).resolves.toBeUndefined();
  });

  it.each([
    [401, '{"error":{"code":"UNAUTHORIZED"}}', { error: { code: 'UNAUTHORIZED' } }],
    [502, '<html>Bad Gateway</html>', '<html>Bad Gateway</html>'],
  ])('HTTP %s 오류의 상태와 본문을 유지한다', async (status, body, expected) => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(body, { status })));
    await expect(requestJson('/items')).rejects.toMatchObject({
      name: 'ApiError',
      status,
      body: expected,
    });
  });

  it('HTML 성공 응답을 JSON 데이터로 오인하지 않는다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('<html>SPA fallback</html>')));
    await expect(requestJson('/items')).rejects.toThrow('유효한 JSON');
  });

  it('네트워크 실패를 HTTP 오류로 바꾸지 않는다', async () => {
    const error = new TypeError('Failed to fetch');
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(error));
    await expect(requestJson('/items')).rejects.toBe(error);
    expect(error).not.toBeInstanceOf(ApiError);
  });

  it('호출자의 요청 취소 신호를 전달한다', async () => {
    const controller = new AbortController();
    const fetchMock = vi.fn().mockImplementation((_url, init: RequestInit) => {
      expect(init.signal?.aborted).toBe(true);
      return Promise.reject(init.signal?.reason);
    });
    vi.stubGlobal('fetch', fetchMock);
    controller.abort();
    await expect(requestJson('/items', { signal: controller.signal })).rejects.toMatchObject({
      name: 'AbortError',
    });
  });

  it('타임아웃이 지나면 요청을 취소한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(
        (_url, init: RequestInit) =>
          new Promise((_resolve, reject) => {
            init.signal?.addEventListener('abort', () => reject(init.signal?.reason), {
              once: true,
            });
          }),
      ),
    );
    await expect(requestJson('/items', { timeoutMs: 10 })).rejects.toMatchObject({
      name: 'TimeoutError',
    });
  });

  it('외부 주소를 요청 경로로 받지 않는다', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    await expect(requestJson('https://example.com')).rejects.toThrow('상대 경로');
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
