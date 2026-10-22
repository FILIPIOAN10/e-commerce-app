import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'

vi.mock('../../api/api', () => ({
  default: { get: vi.fn(), post: vi.fn() },
}))

import api from '../../api/api'
import TwoFactorSettings from '../../components/profile/TwoFactorSettings'
import Setup2FA from '../../components/auth/Setup2FA'

// Both components fetch on mount through use2FA. When the hook handed out a
// new function on every render, those mount effects re-ran on every render:
// the profile never left "Loading 2FA status..." and the setup modal minted a
// new TOTP secret per render, so the QR code could not be verified.
const settle = () => new Promise((r) => setTimeout(r, 300))

describe('2FA screens fetch once', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('the profile asks for the 2FA status once and stops loading', async () => {
    api.get.mockResolvedValue({ data: { is2faEnabled: false } })
    render(<TwoFactorSettings />)

    await waitFor(() => expect(screen.queryByText(/Loading 2FA status/)).not.toBeInTheDocument())
    await settle()

    expect(api.get.mock.calls.filter(([url]) => url === '/auth/user/2fa-status')).toHaveLength(1)
  })

  it('the setup modal generates one QR code, not one per render', async () => {
    api.post.mockResolvedValue({ data: 'otpauth://totp/E-Shop:admin?secret=ABC' })
    render(<Setup2FA onClose={() => {}} onSuccess={() => {}} />)

    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/auth/enable-2fa'))
    await settle()

    expect(api.post.mock.calls.filter(([url]) => url === '/auth/enable-2fa')).toHaveLength(1)
  })
})
