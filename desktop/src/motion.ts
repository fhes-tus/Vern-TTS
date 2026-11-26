export function prefersReducedMotion(appPreference = false) {
  return (
    appPreference ||
    document.documentElement.dataset.reduceMotion === 'true' ||
    !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  );
}
