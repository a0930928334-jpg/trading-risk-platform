import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError } from './api';

function respond(status: number, body: string, contentType: string) {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(body, { status, headers: { 'Content-Type': contentType } })));
}

async function failure(request: Promise<unknown>): Promise<ApiError> {
  try {
    await request;
  } catch (e) {
    return e as ApiError;
  }
  throw new Error('Expected the request to fail');
}

describe('api error handling', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('turns a problem detail into an error carrying its detail and code', async () => {
    respond(
      401,
      JSON.stringify({ status: 401, detail: 'Invalid email or password', code: 'invalid-credentials' }),
      'application/problem+json',
    );

    const error = await failure(api.login('a@example.com', 'wrong'));

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(401);
    expect(error.code).toBe('invalid-credentials');
    expect(error.message).toBe('Invalid email or password');
  });

  it('lists field errors from a validation problem', async () => {
    respond(
      400,
      JSON.stringify({ status: 400, code: 'validation-failed', errors: [{ field: 'amount', message: 'must be positive' }] }),
      'application/problem+json',
    );

    await expect(api.deposit(0)).rejects.toThrow('amount must be positive');
  });

  it('explains a non-JSON error as an unreachable API instead of a bare status', async () => {
    respond(404, 'Not Found', 'text/plain');

    const error = await failure(api.login('a@example.com', 'x'));

    expect(error.code).toBe('api-unreachable');
    expect(error.message).toContain('port 8080');
    expect(error.message).toContain('npm run dev');
  });

  it('reports a network failure', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

    await expect(api.account()).rejects.toMatchObject({ code: 'network-error' });
  });
});
