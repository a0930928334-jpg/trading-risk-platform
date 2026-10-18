import { describe, expect, it } from 'vitest';
import { depthPercent, deviationPercent, formatMoney, formatPrice, notional, spread } from './format';

describe('format helpers', () => {
  it('formats money with two decimals and grouping', () => {
    expect(formatMoney(21025)).toBe('21,025.00');
    expect(formatMoney(null)).toBe('—');
  });

  it('keeps up to four decimals for prices', () => {
    expect(formatPrice(193.8)).toBe('193.80');
    expect(formatPrice(0.1234)).toBe('0.1234');
  });

  it('scales depth bars against the largest level and caps at 100%', () => {
    expect(depthPercent(50, 200)).toBe(25);
    expect(depthPercent(300, 200)).toBe(100);
    expect(depthPercent(10, 0)).toBe(0);
  });

  it('computes the spread only when both sides exist', () => {
    expect(spread(192, 193.8)).toBe(1.8);
    expect(spread(undefined, 193.8)).toBeNull();
  });

  it('rounds the order value to four decimals like the server', () => {
    expect(notional(193.8, 150)).toBe(29070);
    expect(notional(0.1, 3)).toBe(0.3);
    expect(notional(Number.NaN, 3)).toBe(0);
  });

  it('measures distance from the mark price in percent', () => {
    expect(deviationPercent(110, 100)).toBeCloseTo(10);
    expect(deviationPercent(90, 100)).toBeCloseTo(-10);
    expect(deviationPercent(1, 0)).toBe(0);
  });
});
