import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Provider } from 'react-redux'
import { configureStore } from '@reduxjs/toolkit'
import { MemoryRouter } from 'react-router-dom'

vi.mock('../../api/api', () => ({
  default: { get: vi.fn(), post: vi.fn() },
}))
vi.mock('react-hot-toast', () => ({ default: { error: vi.fn() } }))

import api from '../../api/api'
import SupportChatWidget from '../../components/support/SupportChatWidget'

const renderWidget = ({ user = { username: 'user1', roles: ['ROLE_USER'] }, path = '/en' } = {}) => {
  const store = configureStore({ reducer: { auth: () => ({ user }) } })
  return render(
    <Provider store={store}>
      <MemoryRouter initialEntries={[path]}>
        <SupportChatWidget />
      </MemoryRouter>
    </Provider>
  )
}

const routeGets = (routes) => {
  api.get.mockImplementation((url) => Promise.resolve(routes[url] ?? { status: 204, data: '' }))
}

describe('SupportChatWidget', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    Element.prototype.scrollTo = () => {}
  })

  it('renders nothing when the assistant is disabled, or for a guest', async () => {
    routeGets({ '/support/status': { data: { enabled: false } } })
    const { container } = renderWidget()
    await waitFor(() => expect(api.get).toHaveBeenCalledWith('/support/status'))
    expect(container).toBeEmptyDOMElement()

    vi.clearAllMocks()
    const guest = renderWidget({ user: null })
    expect(guest.container).toBeEmptyDOMElement()
    expect(api.get).not.toHaveBeenCalled()
  })

  it('sends a message and shows the reply, then keeps the conversation id', async () => {
    routeGets({ '/support/status': { data: { enabled: true } } })
    api.post.mockResolvedValue({ data: { conversationId: 'c-1', status: 'open', reply: 'Order 5 is shipped.' } })
    renderWidget()

    await userEvent.click(await screen.findByRole('button', { name: 'Open support chat' }))
    await userEvent.type(screen.getByLabelText('Message'), 'Where is order 5?')
    await userEvent.click(screen.getByRole('button', { name: 'Send' }))

    expect(await screen.findByText('Order 5 is shipped.')).toBeInTheDocument()
    expect(api.post).toHaveBeenCalledWith('/support/chat', { message: 'Where is order 5?', conversationId: null })

    await userEvent.type(screen.getByLabelText('Message'), 'thanks')
    await userEvent.click(screen.getByRole('button', { name: 'Send' }))
    await waitFor(() => expect(api.post).toHaveBeenLastCalledWith('/support/chat', { message: 'thanks', conversationId: 'c-1' }))
  })

  it('locks the input while a refund waits for a colleague', async () => {
    routeGets({
      '/support/status': { data: { enabled: true } },
      '/support/conversations/latest': {
        status: 200,
        data: { conversationId: 'c-2', status: 'awaiting_approval', messages: [{ role: 'customer', content: 'refund 7' }] },
      },
    })
    renderWidget()
    await userEvent.click(await screen.findByRole('button', { name: 'Open support chat' }))

    expect(await screen.findByText(/reviewing your refund request/)).toBeInTheDocument()
    expect(screen.getByLabelText('Message')).toBeDisabled()
  })

  it('stays out of the admin pages', async () => {
    routeGets({ '/support/status': { data: { enabled: true } } })
    const { container } = renderWidget({ path: '/en/admin/returns' })
    await waitFor(() => expect(api.get).toHaveBeenCalled())
    expect(container).toBeEmptyDOMElement()
  })
})
