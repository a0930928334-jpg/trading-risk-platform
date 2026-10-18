import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * Loads data now, again every `intervalMs`, and whenever `deps` change or `refresh()` is called.
 * The API has no push channel in v0.1, so the dashboard polls.
 * Responses that arrive after `deps` changed (for example a slow order book for the previous symbol) are ignored.
 */
export function usePolling<T>(load: () => Promise<T>, intervalMs: number, deps: unknown[] = []) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const loadRef = useRef(load);
  const generation = useRef(0);
  loadRef.current = load;

  const refresh = useCallback(async () => {
    const requestedIn = generation.current;
    try {
      const result = await loadRef.current();
      if (requestedIn === generation.current) {
        setData(result);
        setError(null);
      }
    } catch (e) {
      if (requestedIn === generation.current) {
        setError(e instanceof Error ? e.message : String(e));
      }
    }
  }, []);

  useEffect(() => {
    generation.current += 1;
    setData(null);
    void refresh();
    const timer = window.setInterval(() => void refresh(), intervalMs);
    return () => window.clearInterval(timer);
  }, [refresh, intervalMs, ...deps]);

  return { data, error, refresh };
}
