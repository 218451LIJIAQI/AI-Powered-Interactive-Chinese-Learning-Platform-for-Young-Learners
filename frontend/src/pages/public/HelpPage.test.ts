import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'

import { i18n } from '@/plugins/i18n'
import { deferred, toastMessageStub } from '@/test/helpers'

const mocks = vi.hoisted(() => ({ sendHelpMessage: vi.fn() }))
const router = vi.hoisted(() => ({ route: { query: {} as Record<string, unknown>, hash: '' } }))

vi.mock('vue-router', async () => ({
  RouterLink: (await import('@/test/helpers')).routerLinkStub,
  useRoute: () => router.route,
}))

vi.mock('@/api/services', () => ({ publicApi: { sendHelpMessage: mocks.sendHelpMessage } }))

import HelpPage from './HelpPage.vue'

const TOPIC_IDS = ['getting-started', 'learning', 'your-account', 'ai-and-safety']
const SEND = '[data-testid="help-send"]'
const RESULTS = '[data-testid="help-search-results"]'
const TOAST = '[data-testid="toast"]'
const NETWORK_FAILURE = { request: {} }

function mountPage(attachTo?: HTMLElement): VueWrapper {
  return mount(HelpPage, {
    attachTo,
    global: {
      stubs: {
        IconGlyph: true,
        AppToast: toastMessageStub,
      },
    },
  })
}

async function fillForm(wrapper: VueWrapper): Promise<void> {
  await wrapper.get('#help-email').setValue(' Mei@Example.com ')
  await wrapper.get('#help-topic').setValue('LEARNING')
  await wrapper.get('#help-message').setValue('  I cannot open my lesson.  ')
}

async function sendForm(wrapper: VueWrapper): Promise<void> {
  await wrapper.get('form.help-form').trigger('submit')
  await flushPromises()
}

function questions(wrapper: VueWrapper): string[] {
  return wrapper.findAll('.help-questions h3').map((question) => question.text())
}

beforeEach(() => {
  mocks.sendHelpMessage.mockReset()
  router.route = reactive({ query: {}, hash: '' })
})

describe('HelpPage (FRSUP01)', () => {
  it('lists the questions by topic, with a link to each topic, as a top-level page without a breadcrumb', () => {
    const wrapper = mountPage()

    expect(wrapper.findAll('h1')).toHaveLength(1)
    for (const id of [...TOPIC_IDS, 'contact']) {
      expect(wrapper.find(`#${id}`).exists(), id).toBe(true)
      expect(wrapper.find(`a[href="#${id}"]`).exists(), `link to ${id}`).toBe(true)
    }
    expect(wrapper.findAll('.public-rail-link').map((link) => link.text())).toEqual([
      'Getting started',
      'Learning on MandarinSpark',
      'Your account',
      'AI and safety',
      'Contact us',
    ])
    expect(wrapper.find('.breadcrumb, nav[aria-label="Breadcrumb"]').exists()).toBe(false)
    expect(wrapper.find('a[href="/"]').exists()).toBe(false)
  })

  it('answers for learners only: how to create an account, and classes made by the MandarinSpark team', () => {
    const wrapper = mountPage()

    expect(questions(wrapper)).toContain('How do I create an account?')
    expect(wrapper.get('#learning').text()).toContain('Classes are themed courses made by the MandarinSpark team.')
    const answers = TOPIC_IDS.map((id) => wrapper.get(`#${id}`).text()).join(' ')
    expect(answers).not.toMatch(/teacher|live session|Help & support|Which account/i)
  })

  it('says what is sent to the AI as the Privacy and Responsible AI pages do, and links to the guidance', () => {
    const answer = mountPage().get('#ai-and-safety li:last-child .help-answer')

    expect(answer.text()).toContain('your level and the lesson topic')
    expect(answer.text()).toContain('the handwriting photo you submit')
    expect(answer.text()).toContain('Tencent Cloud receives your recording and target text')
    expect(answer.text()).not.toContain('Tavus')
    expect(answer.text()).toContain('Leave personal details out of your text, recordings and photos.')
    expect(answer.get('a').attributes('href')).toBe('/responsible-ai')
  })

  it('discloses Tavus live calls in the AI FAQ only when the live-call deployment is configured', () => {
    vi.stubEnv('VITE_TAVUS_DEPLOYMENT_ID', ' example ')
    try {
      const answer = mountPage().get('#ai-and-safety li:last-child .help-answer')
      expect(answer.text()).toContain('Tavus receives the call audio and recent messages')
    } finally {
      vi.unstubAllEnvs()
    }
  })

  it('marks every contact field as required', () => {
    const wrapper = mountPage()

    for (const id of ['help-email', 'help-topic', 'help-message']) {
      expect(wrapper.get(`#${id}`).attributes('required'), id).toBeDefined()
      expect(wrapper.get(`label[for="${id}"] .required-mark`).text(), id).toBe('*')
    }
  })

  it('checks a field when the visitor leaves it and keeps Send message off until the error is fixed', async () => {
    const wrapper = mountPage()
    const email = wrapper.get('#help-email')
    expect(wrapper.get(SEND).attributes('disabled')).toBeUndefined()

    await email.trigger('blur')
    expect(wrapper.get('#help-email-error').text()).toBe('Enter your email address.')
    expect(email.attributes('aria-invalid')).toBe('true')
    expect(email.attributes('aria-describedby')).toBe('help-email-error')
    expect(wrapper.get(SEND).attributes('disabled')).toBeDefined()

    // Typing is checked again only because the field shows an error.
    await email.setValue('aisyah.k@example')
    expect(wrapper.get('#help-email-error').text()).toBe('Enter a full email address, like name@example.com.')
    await email.setValue('aisyah.k@example.com')
    expect(wrapper.find('#help-email-error').exists()).toBe(false)
    expect(email.attributes('aria-invalid')).toBe('false')
    expect(wrapper.get(SEND).attributes('disabled')).toBeUndefined()

    // A field that has not been left yet is not judged while the visitor types.
    await wrapper.get('#help-message').setValue('   ')
    expect(wrapper.find('#help-message-error').exists()).toBe(false)
    await wrapper.get('#help-message').trigger('blur')
    expect(wrapper.get('#help-message-error').text()).toBe('Describe what happened so we can help.')
    expect(wrapper.get('#help-message').attributes('aria-describedby')).toBe('help-message-error help-message-hint')
    expect(wrapper.get(SEND).attributes('disabled')).toBeDefined()
  })

  it('checks the visitor form before sending anything', async () => {
    const wrapper = mountPage()

    await wrapper.get('#help-email').setValue('not-an-email')
    await wrapper.get('form.help-form').trigger('submit')

    expect(wrapper.get('#help-email-error').text()).toBe('Enter a full email address, like name@example.com.')
    expect(wrapper.find('#help-message-error').exists()).toBe(true)
    expect(mocks.sendHelpMessage).not.toHaveBeenCalled()
    // The fields themselves stop the address and the message at the server's limits, pasted text included.
    expect(wrapper.get('#help-email').attributes('maxlength')).toBe('150')
    expect(wrapper.get('#help-message').attributes('maxlength')).toBe('4000')
  })

  it('shows a field error in the language the visitor switches to', async () => {
    const wrapper = mountPage()
    await wrapper.get('#help-message').trigger('blur')

    i18n.global.locale.value = 'ms'
    await flushPromises()

    expect(wrapper.get('#help-message-error').text()).toBe('Terangkan apa yang berlaku supaya kami dapat membantu.')
  })

  it('keeps the typed message when only the address is wrong, and marks the address alone', async () => {
    const wrapper = mountPage(document.body)
    await wrapper.get('#help-message').setValue('My new password does not work.')
    await wrapper.get('#help-email').setValue('aisyah.k@example')
    await wrapper.get('#help-email').trigger('blur')

    await wrapper.get('form.help-form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('#help-email-error').text()).toBe('Enter a full email address, like name@example.com.')
    expect(wrapper.get<HTMLTextAreaElement>('#help-message').element.value).toBe('My new password does not work.')
    expect(wrapper.find('#help-message-error').exists()).toBe(false)
    expect(wrapper.get('#help-message').attributes('aria-invalid')).toBe('false')
    expect(document.activeElement).toBe(wrapper.get('#help-email').element)
    expect(mocks.sendHelpMessage).not.toHaveBeenCalled()
  })

  it('starts with an empty form', () => {
    const wrapper = mountPage()

    expect(wrapper.get<HTMLInputElement>('#help-email').element.value).toBe('')
    expect(wrapper.get<HTMLSelectElement>('#help-topic').element.value).toBe('ACCOUNT')
    expect(wrapper.get<HTMLTextAreaElement>('#help-message').element.value).toBe('')
  })

  it('opens the form on the topic a link asks for, as the Safety centre’s “Report a concern” does', async () => {
    router.route.query = { topic: 'SAFETY' }
    const wrapper = mountPage()

    const topic = wrapper.get<HTMLSelectElement>('#help-topic')
    expect(topic.element.value).toBe('SAFETY')
    expect(topic.element.selectedOptions[0]!.text).toBe('Safety concern')

    // A link to another topic while the page is open chooses that one.
    router.route.query = { topic: 'AI_HELPER' }
    await flushPromises()
    expect(topic.element.value).toBe('AI_HELPER')
  })

  it.each([['VERIFICATION'], ['safety'], ['<script>'], [['SAFETY', 'OTHER']]])(
    'ignores a topic the form does not offer (%j)',
    (asked) => {
      router.route.query = { topic: asked }
      const wrapper = mountPage()

      expect(wrapper.get<HTMLSelectElement>('#help-topic').element.value).toBe('ACCOUNT')
    },
  )

  it('sends a message, says so in a toast and shows where the reply will go in place of the form', async () => {
    mocks.sendHelpMessage.mockResolvedValue({ email: 'mei@example.com' })
    const wrapper = mountPage(document.body)

    await fillForm(wrapper)
    await sendForm(wrapper)

    expect(mocks.sendHelpMessage).toHaveBeenCalledWith({
      email: 'mei@example.com',
      topic: 'LEARNING',
      message: 'I cannot open my lesson.',
      language: 'en',
    })
    expect(wrapper.get(TOAST).text()).toBe('Message sent. We’ll reply by email within 2 working days.')
    const sent = wrapper.get('[data-testid="help-sent"]')
    expect(sent.get('h3').text()).toBe('Message sent')
    expect(sent.get('p').text()).toBe('We’ll reply to mei@example.com within 2 working days.')
    // The form made way for the confirmation: the focus waits on the next step there.
    expect(document.activeElement).toBe(wrapper.get('[data-testid="help-send-another"]').element)
  })

  it('shows no toast when the message could not be sent', async () => {
    mocks.sendHelpMessage.mockRejectedValueOnce(NETWORK_FAILURE)
    const wrapper = mountPage()

    await fillForm(wrapper)
    await sendForm(wrapper)

    expect(wrapper.get(TOAST).text()).toBe('')
  })

  it('sends the language the visitor reads the page in, so the e-mailed replies use it', async () => {
    mocks.sendHelpMessage.mockResolvedValue({ email: 'mei@example.com' })
    i18n.global.locale.value = 'zh-CN'
    const wrapper = mountPage()

    await fillForm(wrapper)
    await sendForm(wrapper)

    expect(mocks.sendHelpMessage).toHaveBeenCalledWith(expect.objectContaining({ language: 'zh-CN' }))
  })

  it('goes back to the form in place with "Send another message", keeping the address and topic', async () => {
    mocks.sendHelpMessage.mockResolvedValue({ email: 'mei@example.com' })
    const wrapper = mountPage(document.body)
    await fillForm(wrapper)
    await sendForm(wrapper)

    await wrapper.get('[data-testid="help-send-another"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="help-sent"]').exists()).toBe(false)
    expect(wrapper.get<HTMLInputElement>('#help-email').element.value).toBe('Mei@Example.com')
    expect(wrapper.get<HTMLSelectElement>('#help-topic').element.value).toBe('LEARNING')
    expect(wrapper.get<HTMLTextAreaElement>('#help-message').element.value).toBe('')
    expect(wrapper.find('#help-message-error').exists()).toBe(false)
    expect(document.activeElement).toBe(wrapper.get('#help-message').element)
  })

  it('keeps everything typed when sending fails, and the same button sends it again as Try again', async () => {
    mocks.sendHelpMessage.mockRejectedValueOnce(NETWORK_FAILURE)
    const wrapper = mountPage()

    await fillForm(wrapper)
    await sendForm(wrapper)

    const banner = wrapper.get('#help-send-error')
    expect(banner.attributes('role')).toBe('alert')
    expect(banner.text()).toBe(
      'We couldn’t send your message. Check your connection and try again. What you wrote is still here.',
    )
    expect(wrapper.get(SEND).text()).toBe('Try again')
    expect(wrapper.get(SEND).attributes('aria-describedby')).toBe('help-send-error')
    // (An email input drops the spaces around its value by itself.)
    expect(wrapper.get<HTMLInputElement>('#help-email').element.value).toBe('Mei@Example.com')
    expect(wrapper.get<HTMLSelectElement>('#help-topic').element.value).toBe('LEARNING')
    expect(wrapper.get<HTMLTextAreaElement>('#help-message').element.value).toBe('  I cannot open my lesson.  ')

    mocks.sendHelpMessage.mockResolvedValueOnce({ email: 'mei@example.com' })
    await sendForm(wrapper)

    expect(mocks.sendHelpMessage).toHaveBeenCalledTimes(2)
    expect(mocks.sendHelpMessage.mock.calls[1]).toEqual(mocks.sendHelpMessage.mock.calls[0])
    expect(wrapper.find('[data-testid="help-sent"]').exists()).toBe(true)
    expect(wrapper.find('#help-send-error').exists()).toBe(false)
  })

  it('says what the server answered when it refused the message, in the language on screen', async () => {
    mocks.sendHelpMessage.mockRejectedValueOnce({ response: { status: 429, data: {} } })
    const wrapper = mountPage()
    await fillForm(wrapper)
    await sendForm(wrapper)

    expect(wrapper.get('#help-send-error').text()).toBe(
      'We couldn’t send your message. You are doing that too often. Please wait a moment and try again. What you wrote is still here.',
    )
    i18n.global.locale.value = 'zh-CN'
    await flushPromises()
    expect(wrapper.get('#help-send-error').text()).toBe(
      '留言未能发送。操作过于频繁，请稍等片刻再试。你写的内容都还在。',
    )
  })

  it('keeps the banner and the busy button in place while the retry runs', async () => {
    mocks.sendHelpMessage.mockRejectedValueOnce({ response: { status: 500, data: {} } })
    const wrapper = mountPage()
    await fillForm(wrapper)
    await sendForm(wrapper)

    const resend = deferred()
    mocks.sendHelpMessage.mockReturnValueOnce(resend.promise)
    await wrapper.get('form.help-form').trigger('submit')

    const button = wrapper.get(SEND)
    expect(button.text()).toBe('Sending…')
    expect(button.attributes('disabled')).toBeDefined()
    expect(button.attributes('aria-busy')).toBe('true')
    expect(wrapper.get<HTMLInputElement>('#help-email').element.readOnly).toBe(true)
    expect(wrapper.get<HTMLSelectElement>('#help-topic').element.disabled).toBe(true)
    expect(wrapper.get<HTMLTextAreaElement>('#help-message').element.readOnly).toBe(true)
    expect(wrapper.find('#help-send-error').exists()).toBe(true)

    resend.reject({ response: { status: 500, data: {} } })
    await flushPromises()

    expect(wrapper.get(SEND).attributes('disabled')).toBeUndefined()
    expect(wrapper.get<HTMLTextAreaElement>('#help-message').element.readOnly).toBe(false)
    expect(wrapper.get<HTMLTextAreaElement>('#help-message').element.value).toBe('  I cannot open my lesson.  ')
  })

  it('gives the focus back to Try again when the sending button lost it, but not out of a field', async () => {
    mocks.sendHelpMessage.mockRejectedValue(NETWORK_FAILURE)
    const wrapper = mountPage(document.body)
    await fillForm(wrapper)

    // A browser takes the focus from the Send button when it is disabled for sending: nothing has the focus then.
    await sendForm(wrapper)
    const button = wrapper.get<HTMLButtonElement>(SEND)
    expect(document.activeElement).toBe(button.element)

    // Sent with Enter from a field: the visitor stays in that field.
    wrapper.get<HTMLTextAreaElement>('#help-message').element.focus()
    await sendForm(wrapper)
    expect(document.activeElement).toBe(wrapper.get('#help-message').element)
    wrapper.unmount()
  })

  it('does not steal focus if the visitor moved elsewhere while their message was sending', async () => {
    const pending = deferred<{ email: string }>()
    mocks.sendHelpMessage.mockReturnValueOnce(pending.promise)
    const wrapper = mountPage(document.body)
    try {
      await fillForm(wrapper)
      await wrapper.get('form.help-form').trigger('submit')
      const search = wrapper.get<HTMLInputElement>('#help-search').element
      search.focus()
      pending.resolve({ email: 'mei@example.com' })
      await flushPromises()
      expect(document.activeElement).toBe(search)
    } finally {
      wrapper.unmount()
    }
  })

  it('prevents mousedown on Send from blurring an active field before submit runs', () => {
    const wrapper = mountPage()
    const event = new MouseEvent('mousedown', { cancelable: true })
    wrapper.get(SEND).element.dispatchEvent(event)
    expect(event.defaultPrevented).toBe(true)
  })
})

describe('HelpPage search', () => {
  it('has a labelled search field in the rail, above the topics', () => {
    const wrapper = mountPage()

    expect(wrapper.get('label[for="help-search"]').text()).toBe('Search help')
    expect(wrapper.get('.public-rail [role="search"] #help-search').attributes('type')).toBe('search')
    expect(questions(wrapper)).toHaveLength(11)
    expect(wrapper.get('[data-testid="help-search-status"]').text()).toBe('')
  })

  it('shows the matching questions with their count, instead of the topics, until the search is cleared', async () => {
    const wrapper = mountPage(document.body)

    await wrapper.get('#help-search').setValue('  XP ')

    expect(questions(wrapper)).toEqual(['How do I earn XP?'])
    expect(wrapper.get(RESULTS).text()).toContain('1 result for “XP”')
    expect(wrapper.get('[data-testid="help-search-status"]').text()).toBe('1 result for “XP”')
    // The topics leave the rail while the results are shown; the message form stays.
    expect(wrapper.find('.public-rail nav').exists()).toBe(false)
    expect(wrapper.find('#learning').exists()).toBe(true)
    expect(wrapper.find('#getting-started').exists()).toBe(false)
    expect(wrapper.find('#contact').exists()).toBe(true)

    // A word that only an answer contains.
    await wrapper.get('#help-search').setValue('streak')
    expect(questions(wrapper)).toEqual(['How do I earn XP?'])

    await wrapper.get(`${RESULTS} button`).trigger('click')

    expect(wrapper.get<HTMLInputElement>('#help-search').element.value).toBe('')
    expect(questions(wrapper)).toHaveLength(11)
    expect(wrapper.find(RESULTS).exists()).toBe(false)
    expect(wrapper.find('.public-rail nav').exists()).toBe(true)
    expect(document.activeElement).toBe(wrapper.get('#help-search').element)
  })

  it('counts the results in the plural', async () => {
    const wrapper = mountPage()

    await wrapper.get('#help-search').setValue('account')

    const count = questions(wrapper).length
    expect(count).toBeGreaterThan(1)
    expect(wrapper.get(`${RESULTS} p`).text()).toBe(`${count} results for “account”`)
  })

  it('searches the text of the language on screen', async () => {
    i18n.global.locale.value = 'zh-CN'
    const wrapper = mountPage()

    await wrapper.get('#help-search').setValue('密码')

    expect(questions(wrapper)).toEqual(['我忘记了密码'])
  })

  it('says that nothing matches and points to the message form below, which stays', async () => {
    const wrapper = mountPage()

    await wrapper.get('#help-search').setValue('refund')

    expect(wrapper.get(`${RESULTS} p`).text()).toBe(
      'No results for “refund”. Try another word, or send us a message below.',
    )
    expect(questions(wrapper)).toEqual([])
    for (const id of TOPIC_IDS) expect(wrapper.find(`#${id}`).exists(), id).toBe(false)
    expect(wrapper.find('form.help-form').exists()).toBe(true)
  })

  it('shortens a long search in the result line', async () => {
    const wrapper = mountPage()

    await wrapper.get('#help-search').setValue('x'.repeat(60))

    expect(wrapper.get(`${RESULTS} p`).text()).toContain(`“${'x'.repeat(40)}…”`)
    await wrapper.get('#help-search').setValue(`${'字'.repeat(39)}𠀀内容`)
    expect(wrapper.get(`${RESULTS} p`).text()).toContain(`“${'字'.repeat(39)}…”`)
  })

  it('re-evaluates the active rail topic after clearing a search and follows same-page router hash updates', async () => {
    const tops: Record<string, number> = {
      'getting-started': 0,
      learning: 300,
      'your-account': 600,
      'ai-and-safety': 900,
      contact: 1200,
    }
    const rectSpy = vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function (
      this: HTMLElement,
    ) {
      const top = tops[this.id] ?? 0
      return { top, bottom: top + 200, left: 0, right: 100, width: 100, height: 200, x: 0, y: top, toJSON: () => ({}) }
    })
    const wrapper = mountPage(document.body)
    try {
      router.route.hash = '#contact'
      await flushPromises()
      expect(wrapper.get('a[href="#contact"]').attributes('aria-current')).toBe('location')

      await wrapper.get('#help-search').setValue('refund')
      await flushPromises()
      expect(wrapper.find('.public-rail nav').exists()).toBe(false)

      await wrapper.get(`${RESULTS} button`).trigger('click')
      await flushPromises()
      expect(wrapper.get('a[href="#getting-started"]').attributes('aria-current')).toBe('location')
      expect(wrapper.get('a[href="#contact"]').attributes('aria-current')).toBeUndefined()
    } finally {
      rectSpy.mockRestore()
      wrapper.unmount()
    }
  })
})
