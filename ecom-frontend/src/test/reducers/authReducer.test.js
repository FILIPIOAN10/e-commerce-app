import { describe, it, expect, beforeEach } from 'vitest'
import { authReducer } from '../../store/reducers/authReducer'

describe('authReducer', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('returns initial state with no saved auth', () => {
    const state = authReducer(undefined, { type: 'UNKNOWN' })
    expect(state.user).toBeNull()
    expect(state.address).toEqual([])
    expect(state.selectedUserCheckoutAddress).toBeNull()
    expect(state.clientSecret).toBeNull()
  })

  it('logs in a user', () => {
    const action = {
      type: 'LOGIN_USER',
      payload: { id: 1, username: 'admin', roles: ['ROLE_ADMIN'] },
    }
    const state = authReducer(undefined, action)
    expect(state.user.username).toBe('admin')
    expect(state.user.roles).toContain('ROLE_ADMIN')
  })

  it('logs out and resets state', () => {
    const initialState = {
      user: { id: 1, username: 'admin' },
      address: [{ street: '123 Main' }],
      selectedUserCheckoutAddress: { city: 'NYC' },
      clientSecret: 'secret123',
    }
    const state = authReducer(initialState, { type: 'LOG_OUT' })
    expect(state.user).toBeNull()
    expect(state.address).toEqual([])
    expect(state.selectedUserCheckoutAddress).toBeNull()
    expect(state.clientSecret).toBeNull()
  })

  it('sets user address', () => {
    const action = { type: 'USER_ADDRESS', payload: [{ street: '123 Main' }] }
    const state = authReducer(undefined, action)
    expect(state.address).toHaveLength(1)
    expect(state.address[0].street).toBe('123 Main')
  })

  it('selects checkout address', () => {
    const addr = { city: 'NYC', street: '456 Park' }
    const state = authReducer(undefined, { type: 'SELECT_CHECKOUT_ADDRESS', payload: addr })
    expect(state.selectedUserCheckoutAddress).toEqual(addr)
  })

  it('removes checkout address', () => {
    const initialState = { selectedUserCheckoutAddress: { city: 'NYC' } }
    const state = authReducer(initialState, { type: 'REMOVE_CHECKOUT_ADDRESS' })
    expect(state.selectedUserCheckoutAddress).toBeNull()
  })

  it('sets and removes client secret', () => {
    let state = authReducer(undefined, { type: 'CLIENT_SECRET', payload: 'secret123' })
    expect(state.clientSecret).toBe('secret123')
    state = authReducer(state, { type: 'REMOVE_CLIENT_SECRET_ADDRESS' })
    expect(state.clientSecret).toBeNull()
    expect(state.selectedUserCheckoutAddress).toBeNull()
  })
})
