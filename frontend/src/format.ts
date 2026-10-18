// Display helpers. Values come from the API already rounded to 4 decimal places; these only format them.

const money = new Intl.NumberFormat('en-GB', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const price = new Intl.NumberFormat('en-GB', { minimumFractionDigits: 2, maximumFractionDigits: 4 });
const integer = new Intl.NumberFormat('en-GB', { maximumFractionDigits: 0 });

export const formatMoney = (value: number | null | undefined) => (value == null ? '—' : money.format(value));
export const formatPrice = (value: number | null | undefined) => (value == null ? '—' : price.format(value));
export const formatQuantity = (value: number | null | undefined) => (value == null ? '—' : integer.format(value));

export function formatTime(iso: string): string {
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? iso : date.toLocaleTimeString('en-GB');
}

/** Width of a depth bar as a percentage of the largest level shown on either side. */
export function depthPercent(quantity: number, maxQuantity: number): number {
  if (maxQuantity <= 0) return 0;
  return Math.min(100, Math.round((quantity / maxQuantity) * 100));
}

/** Best ask minus best bid, or null if either side is empty. */
export function spread(bestBid: number | undefined, bestAsk: number | undefined): number | null {
  if (bestBid === undefined || bestAsk === undefined) return null;
  return Math.round((bestAsk - bestBid) * 10_000) / 10_000;
}

/** Estimated order value: quantity x limit price, rounded to 4 decimal places like the server. */
export function notional(price: number, quantity: number): number {
  if (!Number.isFinite(price) || !Number.isFinite(quantity)) return 0;
  return Math.round(price * quantity * 10_000) / 10_000;
}

/** Price as a percentage away from a reference, for showing the ±10% band. */
export function deviationPercent(price: number, reference: number): number {
  if (reference <= 0) return 0;
  return ((price - reference) / reference) * 100;
}
