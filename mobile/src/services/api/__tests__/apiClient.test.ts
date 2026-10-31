import { createApiClient } from '../apiClient';

describe('createApiClient', () => {
  it('applies the base URL and default timeout', () => {
    const client = createApiClient({ baseURL: 'https://api.example.test' });

    expect(client.defaults.baseURL).toBe('https://api.example.test');
    expect(client.defaults.timeout).toBe(10_000);
  });

  it('applies a custom timeout', () => {
    const client = createApiClient({
      baseURL: 'https://api.example.test',
      timeout: 3_000,
    });

    expect(client.defaults.timeout).toBe(3_000);
  });
});
