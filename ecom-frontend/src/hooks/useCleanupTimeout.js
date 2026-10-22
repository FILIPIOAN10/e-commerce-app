import { useCallback, useEffect, useRef } from 'react';

/**
 * A `setTimeout` that cancels itself when the component unmounts, and cancels
 * an in-flight timer if the caller schedules another one.
 *
 * The auth flows had four handlers doing `setTimeout(() => navigate(...), 3000)`
 * (or firing an onSuccess callback, or clearing a banner) with no cleanup, so
 * closing the modal or navigating away during that window still fired the
 * callback against gone state — and in ResetPassword's case force-navigated
 * users who had since gone elsewhere back to /login. This hook takes the
 * timer id out of the component body so the linter's `react-hooks/refs`
 * rule (which flags `onSubmit={handleSubmit(myHandler)}` when `myHandler`
 * closes over a raw ref) has nothing to complain about, and every caller
 * gets the same unmount cancel.
 */
export default function useCleanupTimeout() {
  const timerRef = useRef(null);

  useEffect(() => () => {
    if (timerRef.current) clearTimeout(timerRef.current);
  }, []);

  const schedule = useCallback((callback, ms) => {
    if (timerRef.current) clearTimeout(timerRef.current);
    timerRef.current = setTimeout(callback, ms);
  }, []);

  return schedule;
}
