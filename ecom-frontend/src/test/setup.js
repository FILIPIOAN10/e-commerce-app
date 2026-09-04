import '@testing-library/jest-dom'
import { beforeEach } from 'vitest'

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

// jsdom does not expose localStorage on every Node version — it is undefined
// under Node 26, which failed every suite before a single test body ran. Supply
// an in-memory implementation when the environment lacks one, so the tests
// exercise the real storage code paths regardless of the runtime.
const hasStorage = (() => {
  try {
    return typeof globalThis.localStorage !== 'undefined' && globalThis.localStorage !== null
  } catch {
    return false
  }
})()

if (!hasStorage) {
  const entries = new Map()
  const memoryStorage = {
    getItem: (key) => (entries.has(String(key)) ? entries.get(String(key)) : null),
    setItem: (key, value) => { entries.set(String(key), String(value)) },
    removeItem: (key) => { entries.delete(String(key)) },
    clear: () => { entries.clear() },
    key: (index) => Array.from(entries.keys())[index] ?? null,
    get length() { return entries.size },
  }
  const descriptor = { value: memoryStorage, configurable: true, writable: true }
  Object.defineProperty(globalThis, 'localStorage', descriptor)
  if (typeof window !== 'undefined') {
    Object.defineProperty(window, 'localStorage', descriptor)
  }
}

// Clear localStorage between tests
beforeEach(() => {
  localStorage.clear()
})
