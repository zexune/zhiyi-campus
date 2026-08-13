import { afterEach } from 'vitest'
import { enableAutoUnmount } from '@vue/test-utils'

enableAutoUnmount(afterEach)

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

class IntersectionObserverStub extends ResizeObserverStub {
  takeRecords() { return [] }
}

globalThis.ResizeObserver = ResizeObserverStub
globalThis.IntersectionObserver = IntersectionObserverStub

Object.defineProperty(window, 'matchMedia', {
  configurable: true,
  value: (query) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener() {},
    removeEventListener() {},
    addListener() {},
    removeListener() {},
    dispatchEvent() { return false },
  }),
})

afterEach(() => {
  localStorage.clear()
  sessionStorage.clear()
  document.body.innerHTML = ''
})
