import '@testing-library/jest-dom'

// Polyfill matchMedia for components that use it
if (!window.matchMedia) {
  window.matchMedia = (query) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  })
}

// Clear localStorage between tests
beforeEach(() => {
  localStorage.clear()
})
