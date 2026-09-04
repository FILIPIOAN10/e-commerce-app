import { describe, it, expect } from 'vitest'
import { errorReducer } from '../../store/reducers/errorReducer'

const fetching = { type: 'IS_FETCHING' }
const success = { type: 'IS_SUCCESS' }
const failure = { type: 'IS_ERROR', payload: 'boom' }

describe('errorReducer', () => {
  it('starts idle', () => {
    const state = errorReducer(undefined, { type: '@@INIT' })
    expect(state.isLoading).toBe(false)
    expect(state.errorMessage).toBeNull()
  })

  it('is loading while a request is in flight', () => {
    const state = errorReducer(undefined, fetching)
    expect(state.isLoading).toBe(true)
  })

  it('stays loading until the LAST concurrent request finishes', () => {
    // The regression this guards: two requests start, the faster one returns,
    // and the slower one's screen used to render empty because the shared flag
    // had already been cleared.
    let state = errorReducer(undefined, fetching)
    state = errorReducer(state, fetching)
    expect(state.isLoading).toBe(true)

    state = errorReducer(state, success)
    expect(state.isLoading).toBe(true)

    state = errorReducer(state, success)
    expect(state.isLoading).toBe(false)
  })

  it('clears loading when a concurrent request fails', () => {
    let state = errorReducer(undefined, fetching)
    state = errorReducer(state, fetching)
    state = errorReducer(state, failure)
    expect(state.isLoading).toBe(true)
    expect(state.errorMessage).toBe('boom')

    state = errorReducer(state, success)
    expect(state.isLoading).toBe(false)
  })

  it('does not go negative when IS_SUCCESS arrives without IS_FETCHING', () => {
    // The button-loader flows do exactly this; an unclamped counter would go
    // negative and leave isLoading stuck on for the rest of the session.
    let state = errorReducer(undefined, success)
    expect(state.isLoading).toBe(false)

    state = errorReducer(state, fetching)
    expect(state.isLoading).toBe(true)
  })

  it('clears a previous error when a new request starts', () => {
    let state = errorReducer(undefined, failure)
    expect(state.errorMessage).toBe('boom')
    state = errorReducer(state, fetching)
    expect(state.errorMessage).toBeNull()
  })
})
