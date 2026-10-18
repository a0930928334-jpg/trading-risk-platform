// Typed client for the Spring Boot API. Every request goes to /api (proxied by Vite in development).

export type Side = 'BUY' | 'SELL';
export type OrderStatus = 'NEW' | 'PARTIALLY_FILLED' | 'FILLED' | 'CANCELLED' | 'REJECTED';

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  expiresAt: string;
  userId: number;
  email: string;
  fullName: string;
}

export interface Account {
  accountId: number;
  accountNumber: string;
  status: string;
  currency: string;
  balance: number;
  reserved: number;
  available: number;
}

export interface Instrument {
  symbol: string;
  name: string;
  status: 'ACTIVE' | 'HALTED';
  currency: string;
  referencePrice: number;
  lastTradePrice: number | null;
}

export interface PriceLevel {
  price: number;
  quantity: number;
  orderCount: number;
}

export interface OrderBook {
  symbol: string;
  lastTradePrice: number | null;
  referencePrice: number;
  bids: PriceLevel[];
  asks: PriceLevel[];
}

export interface Order {
  id: number;
  symbol: string;
  side: Side;
  type: string;
  timeInForce: string;
  price: number;
  quantity: number;
  filledQuantity: number;
  remainingQuantity: number;
  status: OrderStatus;
  rejectCode: string | null;
  rejectReason: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface Trade {
  id: number;
  symbol: string;
  side: Side;
  price: number;
  quantity: number;
  value: number;
  orderId: number;
  liquidity: 'MAKER' | 'TAKER';
  executedAt: string;
}

export interface Position {
  symbol: string;
  quantity: number;
  reservedQuantity: number;
  availableQuantity: number;
}

export interface LedgerEntry {
  id: number;
  type: string;
  currency: string;
  amount: number;
  balanceAfter: number;
  reservedAfter: number;
  orderId: number | null;
  tradeId: number | null;
  createdAt: string;
}

/** An RFC 9457 problem detail returned by the API, thrown as an Error. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string | undefined,
    message: string,
  ) {
    super(message);
  }
}

const TOKEN_KEY = 'trp.token';

export const session = {
  token(): string | null {
    try {
      return localStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  },
  save(token: string) {
    try {
      localStorage.setItem(TOKEN_KEY, token);
    } catch {
      // Storage may be unavailable (private mode); the session then lasts until reload.
    }
  },
  clear() {
    try {
      localStorage.removeItem(TOKEN_KEY);
    } catch {
      // ignored
    }
  },
};

/** Called when the API answers 401, so the app can return to the login screen. */
let onUnauthorized: () => void = () => {};
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {};
  const token = session.token();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  let response: Response;
  try {
    response = await fetch(`/api${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, 'network-error', 'Cannot reach the server. Check that the front end dev server is running.');
  }

  // Every API error is a JSON problem detail. Anything else means the request never reached the
  // Spring Boot application (back end stopped, or the page is not served through the Vite proxy).
  const isJson = (response.headers.get('Content-Type') ?? '').includes('json');
  if (!response.ok && !isJson) {
    throw new ApiError(
      response.status,
      'api-unreachable',
      `The API is not reachable (HTTP ${response.status}). Make sure the back end is running on port 8080 ` +
        'and open the dashboard from `npm run dev`, which forwards /api to it.',
    );
  }

  if (!response.ok) {
    let detail = `${response.status} ${response.statusText}`;
    let code: string | undefined;
    try {
      const problem = await response.json();
      detail = problem.detail ?? problem.title ?? detail;
      code = problem.code;
      if (Array.isArray(problem.errors) && problem.errors.length > 0) {
        detail = problem.errors.map((e: { field: string; message: string }) => `${e.field} ${e.message}`).join(', ');
      }
    } catch {
      // Not a JSON body; keep the status text.
    }
    if (response.status === 401 && token) onUnauthorized();
    throw new ApiError(response.status, code, detail);
  }
  return (await response.json()) as T;
}

export const api = {
  register: (email: string, password: string, fullName: string) =>
    request<unknown>('POST', '/auth/register', { email, password, fullName }),
  login: (email: string, password: string) => request<LoginResponse>('POST', '/auth/login', { email, password }),
  account: () => request<Account>('GET', '/account'),
  deposit: (amount: number) => request<{ account: Account }>('POST', '/account/deposits', { amount }),
  ledger: () => request<Page<LedgerEntry>>('GET', '/account/ledger?size=50'),
  instruments: () => request<Page<Instrument>>('GET', '/instruments?size=100'),
  book: (symbol: string) => request<OrderBook>('GET', `/instruments/${encodeURIComponent(symbol)}/book?depth=10`),
  placeOrder: (symbol: string, side: Side, price: number, quantity: number) =>
    request<Order>('POST', '/orders', { symbol, side, price, quantity }),
  cancelOrder: (id: number) => request<Order>('DELETE', `/orders/${id}`),
  orders: (status?: OrderStatus) => request<Page<Order>>('GET', `/orders?size=50${status ? `&status=${status}` : ''}`),
  trades: () => request<Page<Trade>>('GET', '/trades?size=50'),
  positions: () => request<Page<Position>>('GET', '/positions?size=100'),
};
