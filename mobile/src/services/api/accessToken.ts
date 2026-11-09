type LocalAccessTokenModule = {
  TEMP_ACCESS_TOKEN?: string | null;
};

function readTemporaryAccessToken(): string | null {
  try {
    const local = require('./accessToken.local') as LocalAccessTokenModule;
    const token = local.TEMP_ACCESS_TOKEN;
    return typeof token === 'string' && token.length > 0 ? token : null;
  } catch {
    return null;
  }
}

export const TEMP_ACCESS_TOKEN = readTemporaryAccessToken();
