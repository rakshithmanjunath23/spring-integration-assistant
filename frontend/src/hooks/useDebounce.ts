import { useEffect, useState } from 'react';

/**
 * Debounces a value by the specified delay in milliseconds.
 * Returns the debounced value that only updates after the delay
 * has elapsed since the last change.
 *
 * Useful for search inputs to avoid triggering API calls on every keystroke.
 */
export function useDebounce<T>(value: T, delay: number): T {
  const [debouncedValue, setDebouncedValue] = useState<T>(value);

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedValue(value);
    }, delay);

    return () => {
      clearTimeout(timer);
    };
  }, [value, delay]);

  return debouncedValue;
}
