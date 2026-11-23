import { flushPromises, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { buttonNamed, chooseFile, deferred, fileWithSize } from '@/test/helpers'
import { draftLesson, editorActivity } from '@/test/lessonEditorFixtures'
import { mountAtRoute } from '@/test/routes'
import type { EditorQuestion, LessonEditor } from '@/types/adminClasses'

const api = vi.hoisted(() => ({
  get: vi.fn(),
  saveContent: vi.fn(),
  saveVideo: vi.fn(),
  removeVideo: vi.fn(),
  addActivity: vi.fn(),
  updateActivity: vi.fn(),
  addQuiz: vi.fn(),
  updateQuiz: vi.fn(),
  addQuestion: vi.fn(),
  updateQuestion: vi.fn(),
  deleteQuestion: vi.fn(),
  keepQuestion: vi.fn(),
  publish: vi.fn(),
  discardChanges: vi.fn(),
}))

vi.mock('@/api/adminClasses', () => ({ lessonEditorApi: api, adminQuizDraftApi: {} }))

import AdminLessonEditorPage from './AdminLessonEditorPage.vue'

function question(number: number, aiDraft = false): EditorQuestion {
  return {
    id: 300 + number,
    number,
    type: 'MULTIPLE_CHOICE',
    text: `What does word ${number} mean?`,
    points: 2,
    correctAnswerText: null,
    explanation: `Word ${number} means it.`,
    hint: null,
    audioUrl: null,
    imageUrl: null,
    options: [
      { id: 3000 + number * 10, text: 'Library', correct: true, feedback: null },
      { id: 3001 + number * 10, text: 'Canteen', correct: false, feedback: 'That’s 食堂.' },
    ],
    aiDraft,
  }
}

function quiz(count = 5): LessonEditor['quizzes'][number] {
  return {
    id: 3,
    hintsAllowed: true,
    questionsLocked: false,
    questions: Array.from({ length: count }, (_, index) => question(index + 1)),
  }
}

/** A complete published lesson: what A3.22 shows. */
function lesson(id = 13, overrides: Partial<LessonEditor> = {}): LessonEditor {
  const quizzes = overrides.quizzes ?? [quiz()]
  const questionCount = quizzes[0]?.questions.length ?? 0
  return draftLesson({
    lessonId: id,
    classTitle: 'Mandarin for Campus Life',
    classStatus: 'PUBLISHED',
    unitTitle: 'First Conversations',
    units: [
      { id: 70, title: 'First Conversations' },
      { id: 71, title: 'Campus Life' },
    ],
    status: 'PUBLISHED',
    title: id === 13 ? 'University Life' : `Lesson ${id}`,
    minutes: 15,
    summary: 'Campus words.',
    keyPhrases: [{ chinese: '我是大学生。', pinyin: 'wǒ shì dàxuéshēng.', meaning: 'I’m a university student.' }],
    video: { title: 'Around the campus', durationSeconds: 340 },
    activities: [
      editorActivity('FLASHCARDS', 1, 'University Life Phrases'),
      editorActivity('TRACING', 2, 'Trace 学 and 生'),
      editorActivity('MATCHING', 3, 'Campus Places'),
    ],
    quizzes,
    checklist: {
      videoAdded: true,
      keyPhrasesAdded: true,
      activitiesComplete: true,
      quizzesReady: questionCount === 5,
      pinyinComplete: true,
    },
    ...overrides,
  })
}

/** A lesson just added to a class: a draft with nothing in it yet (A3.14). */
function newLesson(): LessonEditor {
  return draftLesson({
    classTitle: 'Mandarin for Campus Life',
    classStatus: 'PUBLISHED',
    unitTitle: 'First Conversations',
    units: [
      { id: 70, title: 'First Conversations' },
      { id: 71, title: 'Campus Life' },
    ],
    minutes: 15,
    summary: 'Campus words.',
  })
}

function mountEditor(path = '/admin/content/classes/5/lessons/13', { attach = false } = {}) {
  return mountAtRoute(AdminLessonEditorPage, path, {
    attachTo: attach ? document.body : undefined,
    stubs: { AdminQuizDraftPanel: true },
  })
}

const statusLine = (wrapper: VueWrapper) => wrapper.find('[data-testid="lesson-status"]')
const publishHint = (wrapper: VueWrapper) => wrapper.find('[data-testid="publish-hint"]')
const publishButton = (wrapper: VueWrapper) => wrapper.get<HTMLButtonElement>('[data-testid="open-publish"]')
const saveDraftButton = (wrapper: VueWrapper) => wrapper.get<HTMLButtonElement>('[data-testid="save-draft"]')

describe('AdminLessonEditorPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    api.get.mockImplementation(async (id: number) => (id === 21 ? newLesson() : lesson(id)))
  })

  it('opens a new draft lesson that learners can’t see, saying what publishing needs (A3.14)', async () => {
    const { wrapper } = await mountEditor('/admin/content/classes/5/lessons/21')

    expect(wrapper.get('h1').text()).toBe('At the Hawker Stall')
    expect(wrapper.get('.breadcrumb').text()).toContain('Mandarin for Campus Life')
    // The unit is named with its place in the class, in the unit line and the Unit field.
    expect(wrapper.get('.page-header p').text()).toBe('Unit 1: First Conversations')
    expect(wrapper.findAll('#lesson-unit option').map((option) => option.text())).toEqual([
      'Unit 1: First Conversations',
      'Unit 2: Campus Life',
    ])
    expect(statusLine(wrapper).text()).toContain('Draft')
    expect(statusLine(wrapper).text()).toContain('Learners can’t see this lesson until you publish it.')
    expect(publishHint(wrapper).text()).toBe(
      'To publish, add a video, key phrases, all 3 activities and a 5-question quiz.',
    )
    expect(publishButton(wrapper).element.disabled).toBe(true)
    expect(publishButton(wrapper).attributes('aria-describedby')).toBe('lesson-publish-hint')
    expect(saveDraftButton(wrapper).element.disabled).toBe(true)
  })

  it('says how many questions the quiz still needs when the rest is there', async () => {
    api.get.mockResolvedValue(lesson(13, { status: 'DRAFT', quizzes: [quiz(4)] }))
    const { wrapper } = await mountEditor()

    expect(publishHint(wrapper).text()).toBe('Add 1 question to publish. A lesson quiz has exactly 5.')
    expect(publishButton(wrapper).element.disabled).toBe(true)
  })

  it('keeps an edited lesson with a missing title or flashcard pinyin out of the publish dialog', async () => {
    const incomplete = lesson(13, { status: 'DRAFT' })
    incomplete.activities[0]!.cards = [{ chinese: '书', pinyin: '', meaning: 'book' }]
    incomplete.checklist.pinyinComplete = false
    api.get.mockResolvedValue(incomplete)
    const { wrapper } = await mountEditor()
    await wrapper.get('#lesson-title').setValue('Revised title')
    expect(publishHint(wrapper).text()).toBe('To publish, add pinyin to every new word.')
    expect(publishButton(wrapper).element.disabled).toBe(true)

    await wrapper.get('#lesson-title').setValue(' ')
    expect(publishHint(wrapper).text()).toBe('Add a lesson title.')
    expect(publishButton(wrapper).element.disabled).toBe(true)
  })

  it('says on the unit line that learners keep the published lesson, with nothing to publish yet (A3.22)', async () => {
    const { wrapper } = await mountEditor()

    expect(wrapper.get('.page-header p').text()).toBe(
      'Unit 1: First Conversations · Published · learners keep seeing the published version until you publish your edits',
    )
    expect(statusLine(wrapper).exists()).toBe(false)
    expect(wrapper.find('[data-testid="discard-changes"]').exists()).toBe(false)
    expect(wrapper.findAll('.route-tab').map((tab) => tab.text())).toEqual([
      'Content',
      'Activities3',
      'Quiz5',
      'Preview',
    ])
    expect(publishHint(wrapper).text()).toBe('No changes to publish.')
    expect(publishButton(wrapper).element.disabled).toBe(true)
  })

  it('saves a draft of a published lesson that learners don’t see until it is published (X1.03)', async () => {
    api.saveContent.mockResolvedValue(lesson(13, { title: 'University Life 2', hasUnpublishedChanges: true }))
    const { wrapper } = await mountEditor()

    await wrapper.get('#lesson-title').setValue('  University Life 2 ')
    await buttonNamed(wrapper, 'Add phrase').trigger('click')
    expect(saveDraftButton(wrapper).element.disabled).toBe(false)
    expect(publishButton(wrapper).element.disabled).toBe(false)
    await saveDraftButton(wrapper).trigger('click')
    await flushPromises()

    expect(api.saveContent).toHaveBeenCalledExactlyOnceWith(13, {
      title: 'University Life 2',
      unitId: 70,
      minutes: 15,
      summary: 'Campus words.',
      // The blank row added last is left out.
      keyPhrases: [{ chinese: '我是大学生。', pinyin: 'wǒ shì dàxuéshēng.', meaning: 'I’m a university student.' }],
    })
    expect(wrapper.get('[data-testid="toast"]').text()).toBe('Draft saved.')
    expect(wrapper.get('.page-header p').text()).toContain('learners keep seeing the published version')
    // The saved edits can be dropped from beside Publish.
    expect(wrapper.find('.lesson-footer [data-testid="discard-changes"]').exists()).toBe(true)
    expect(publishButton(wrapper).element.disabled).toBe(false)
  })

  it('keeps the edits on the page when the draft didn’t save, and saves again from the banner (A3.32, A3.35)', async () => {
    api.saveContent.mockRejectedValueOnce({ request: {}, message: 'Network Error' })
    api.saveContent.mockResolvedValueOnce(lesson(13, { title: 'University Life 2', hasUnpublishedChanges: true }))
    const { wrapper } = await mountEditor()

    await wrapper.get('#lesson-title').setValue('University Life 2')
    await saveDraftButton(wrapper).trigger('click')
    await flushPromises()

    const banner = wrapper.get('.data-notice')
    expect(banner.text()).toContain('Your draft didn’t save.')
    expect(banner.text()).toContain('Check your connection, then try again. Your edits are still on this page.')
    expect(wrapper.get<HTMLInputElement>('#lesson-title').element.value).toBe('University Life 2')

    await wrapper.get('[data-testid="retry-save"]').trigger('click')
    await flushPromises()
    expect(api.saveContent).toHaveBeenCalledTimes(2)
    expect(wrapper.find('.data-notice').exists()).toBe(false)
    expect(wrapper.get('[data-testid="toast"]').text()).toBe('Draft saved.')
  })

  it('keeps edits typed after the draft was sent and warns before leaving them', async () => {
    const saved = deferred<LessonEditor>()
    api.saveContent.mockReturnValueOnce(saved.promise)
    const { wrapper, router } = await mountEditor()
    const title = wrapper.get<HTMLInputElement>('#lesson-title')
    await title.setValue('Submitted title')
    await saveDraftButton(wrapper).trigger('click')
    await title.setValue('Newer title')

    await router.push('/admin/content/classes/5')
    expect(wrapper.get('[data-testid="confirm-dialog"] h2').text()).toBe('Leave without saving?')
    await wrapper.get('[data-testid="confirm-dialog-cancel"]').trigger('click')

    saved.resolve(lesson(13, { title: 'Submitted title', hasUnpublishedChanges: true }))
    await flushPromises()

    expect(wrapper.get('h1').text()).toBe('Submitted title')
    expect(title.element.value).toBe('Newer title')
    expect(saveDraftButton(wrapper).element.disabled).toBe(false)
  })

  it('shows the server’s reason when it refuses the draft', async () => {
    api.saveContent.mockRejectedValue({
      response: { status: 400, data: { message: 'Pinyin is required for every key phrase.' } },
    })
    const { wrapper } = await mountEditor()

    await wrapper.get('#lesson-title').setValue('University Life 2')
    await saveDraftButton(wrapper).trigger('click')
    await flushPromises()

    expect(wrapper.get('.data-notice').text()).toContain('Pinyin is required for every key phrase.')
  })

  it('publishes a complete draft after a checklist, and says learners see it now (A3.33, A3.34)', async () => {
    api.get.mockResolvedValue(lesson(13, { status: 'DRAFT' }))
    api.publish.mockResolvedValue(lesson(13))
    // In the document, so the focus can move to the new status.
    const { wrapper } = await mountEditor(undefined, { attach: true })

    expect(publishHint(wrapper).exists()).toBe(false)
    await publishButton(wrapper).trigger('click')
    const dialog = wrapper.get('[data-testid="publish-dialog"]')
    expect(dialog.get('h2').text()).toBe('Publish “University Life”?')
    expect(dialog.findAll('.authoring-checklist li').map((row) => row.text())).toEqual([
      'Video and key phrases added',
      'All 3 activities: Flashcards, Tracing and Matching',
      'Quiz has 5 questions with correct answers; AI drafts checked',
      'Pinyin added for new words',
    ])
    expect(dialog.text()).not.toContain('saved first')
    await dialog.get('[data-testid="publish-confirm"]').trigger('click')
    await flushPromises()

    expect(api.publish).toHaveBeenCalledWith(13)
    expect(wrapper.find('[data-testid="publish-dialog"]').exists()).toBe(false)
    expect(statusLine(wrapper).text()).toContain('Published')
    expect(statusLine(wrapper).text()).toContain('Published just now. Learners see this version now.')
    // The status line says it: the unit line is the unit alone (A3.34).
    expect(wrapper.get('.page-header p').text()).toBe('Unit 1: First Conversations')
    expect(document.activeElement).toBe(statusLine(wrapper).element)
    // The class page is the next step.
    const openClass = statusLine(wrapper).get('[data-testid="open-class"]')
    expect(openClass.text()).toBe('Open class')
    expect(openClass.attributes('href')).toBe('/admin/content/classes/5')
    wrapper.unmount()
  })

  it('offers Open class only once the lesson was just published', async () => {
    api.get.mockResolvedValue(lesson(13, { status: 'DRAFT' }))
    const { wrapper } = await mountEditor()

    expect(wrapper.find('[data-testid="open-class"]').exists()).toBe(false)
  })

  it('uses the class that owns a linked lesson for its address and navigation', async () => {
    const { wrapper, router } = await mountEditor('/admin/content/classes/6/lessons/13/preview')
    expect(router.currentRoute.value.path).toBe('/admin/content/classes/5/lessons/13/preview')
    expect(wrapper.get('.breadcrumb a[href$="/classes/5"]').text()).toBe('Mandarin for Campus Life')
  })

  it('refuses a video larger than the limit of its lesson and clears the error when the lesson changes', async () => {
    api.get.mockResolvedValueOnce(lesson(13, { videoUploadMaxBytes: 1024 }))
    const { wrapper, router } = await mountEditor()
    const hint = () => wrapper.get('#lesson-video-file-hint')
    const error = () => wrapper.find('#lesson-video-file-hint + [role="alert"]')
    expect(hint().text()).toBe('MP4, WebM or MOV · up to 1 KB')

    await chooseFile(wrapper.get('#lesson-video-file'), fileWithSize('campus.mp4', 'video/mp4', 1025))
    expect(error().text()).toBe('The file is larger than 1 KB. Choose a smaller file.')
    expect(api.saveVideo).not.toHaveBeenCalled()

    await router.push('/admin/content/classes/5/lessons/14')
    await flushPromises()
    expect(error().exists()).toBe(false)
    expect(hint().text()).toBe('MP4, WebM or MOV')
  })

  it.each([
    [256 * 1024 * 1024, 'MP4, WebM or MOV · up to 256 MB'],
    [undefined, 'MP4, WebM or MOV'],
  ] as const)(
    'uploads a large MOV with a configured or omitted size limit (%s) even if its metadata stalls',
    async (videoUploadMaxBytes, hint) => {
      api.get.mockResolvedValue(lesson(13, { videoUploadMaxBytes }))
      const { wrapper } = await mountEditor()
      const picker = wrapper.get<HTMLInputElement>('#lesson-video-file')
      expect(wrapper.get('#lesson-video-file-hint').text()).toBe(hint)
      expect(picker.attributes('accept')?.split(',')).toContain('.mov')
      const title = '字'.repeat(199)
      const file = fileWithSize(`${title}𠀀.mov`, 'video/quicktime', 201 * 1024 * 1024)
      const revoke = vi.fn()
      vi.stubGlobal(
        'URL',
        class extends URL {
          static createObjectURL = () => 'blob:lesson-video'
          static revokeObjectURL = revoke
        },
      )
      vi.spyOn(HTMLMediaElement.prototype, 'load').mockImplementation(() => undefined)
      api.saveVideo.mockResolvedValue(lesson())
      vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
      try {
        await chooseFile(picker, file)
        expect(api.saveVideo).not.toHaveBeenCalled()
        await vi.runOnlyPendingTimersAsync()
        await flushPromises()

        expect(revoke).toHaveBeenCalledExactlyOnceWith('blob:lesson-video')
        expect(api.saveVideo).toHaveBeenCalledTimes(1)
        const [id, payload] = api.saveVideo.mock.calls[0] as [
          number,
          { title: string; file: File; durationSeconds: number | null },
        ]
        expect(id).toBe(13)
        expect(payload).toEqual({ title, file, durationSeconds: null })
      } finally {
        vi.useRealTimers()
        vi.unstubAllGlobals()
      }
    },
  )

  it('saves unsaved edits before it publishes them', async () => {
    const order: string[] = []
    api.saveContent.mockImplementation(async () => {
      order.push('save')
      return lesson(13, { title: 'University Life 2', hasUnpublishedChanges: true })
    })
    api.publish.mockImplementation(async () => {
      order.push('publish')
      return lesson(13, { title: 'University Life 2' })
    })
    const { wrapper } = await mountEditor()

    await wrapper.get('#lesson-title').setValue('University Life 2')
    await publishButton(wrapper).trigger('click')
    expect(wrapper.get('[data-testid="publish-dialog"]').text()).toContain('Your unsaved edits are saved first.')
    await wrapper.get('[data-testid="publish-confirm"]').trigger('click')
    await flushPromises()

    expect(order).toEqual(['save', 'publish'])
    expect(statusLine(wrapper).text()).toContain('Published just now.')
  })

  it('keeps locally edited content while a video write is pending and then saves it on the new lesson', async () => {
    const removed = deferred<LessonEditor>()
    api.removeVideo.mockReturnValueOnce(removed.promise)
    const { wrapper } = await mountEditor()
    await wrapper.get('button[aria-label="Remove the video Around the campus"]').trigger('click')
    await wrapper.get('#lesson-title').setValue('New title')
    expect(saveDraftButton(wrapper).element.disabled).toBe(true)
    await saveDraftButton(wrapper).trigger('click')
    expect(api.saveContent).not.toHaveBeenCalled()
    removed.resolve(lesson(13, { video: null, hasUnpublishedChanges: true }))
    await flushPromises()
    expect(wrapper.get<HTMLInputElement>('#lesson-title').element.value).toBe('New title')
    expect(saveDraftButton(wrapper).element.disabled).toBe(false)
    api.saveContent.mockResolvedValueOnce(lesson(13, { video: null, title: 'New title', hasUnpublishedChanges: true }))
    await saveDraftButton(wrapper).trigger('click')
    await flushPromises()
    expect(wrapper.get('h1').text()).toBe('New title')
    expect(wrapper.find('button[aria-label="Remove the video Around the campus"]').exists()).toBe(false)
  })

  it('disables other lesson tabs while a panel write is in flight and stays on the tab when it fails', async () => {
    const removed = deferred<LessonEditor>()
    api.removeVideo.mockReturnValueOnce(removed.promise)
    const { wrapper, router } = await mountEditor()

    await wrapper.get('button[aria-label="Remove the video Around the campus"]').trigger('click')
    const disabledTabs = wrapper.findAll('.route-tab[aria-disabled="true"]')
    expect(disabledTabs).toHaveLength(3)

    // Programmatic tab switch is blocked immediately rather than queued to jump later.
    await router.push('/admin/content/classes/5/lessons/13/quiz')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/admin/content/classes/5/lessons/13')

    removed.reject(new Error('offline'))
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/admin/content/classes/5/lessons/13')
    expect(wrapper.findAll('.route-tab[aria-disabled="true"]')).toHaveLength(0)
    expect(wrapper.get('[role="alert"]').text()).toBeTruthy()
  })

  it('keeps the publish dialog open with the reason when publishing is refused', async () => {
    api.get.mockResolvedValue(lesson(13, { status: 'DRAFT' }))
    api.publish.mockRejectedValue({
      response: { status: 400, data: { message: 'Add pinyin to every new word before you publish.' } },
    })
    const { wrapper } = await mountEditor()

    await publishButton(wrapper).trigger('click')
    await wrapper.get('[data-testid="publish-confirm"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="publish-dialog"] [role="alert"]').text()).toBe(
      'Add pinyin to every new word before you publish.',
    )
    expect(statusLine(wrapper).text()).toContain('Learners can’t see this lesson until you publish it.')
  })

  it('discards the unpublished changes of a published lesson after asking', async () => {
    api.get.mockResolvedValue(lesson(13, { title: 'University Life 2', hasUnpublishedChanges: true }))
    api.discardChanges.mockResolvedValue(lesson(13))
    const { wrapper } = await mountEditor()

    await wrapper.get('[data-testid="discard-changes"]').trigger('click')
    expect(wrapper.get('[data-testid="confirm-dialog"]').text()).toContain('Discard your changes?')
    await wrapper.get('[data-testid="confirm-dialog-confirm"]').trigger('click')
    await flushPromises()

    expect(api.discardChanges).toHaveBeenCalledWith(13)
    expect(wrapper.get('[data-testid="toast"]').text()).toBe('Changes discarded.')
    expect(wrapper.get('h1').text()).toBe('University Life')
    expect(wrapper.get<HTMLInputElement>('#lesson-title').element.value).toBe('University Life')
    expect(statusLine(wrapper).exists()).toBe(false)
  })

  it('asks before leaving with unsaved edits, and leaves only when told (X2.09)', async () => {
    const { wrapper, router } = await mountEditor()
    await wrapper.get('#lesson-title').setValue('University Life 2')

    // The other tabs keep the form: no question.
    await router.push('/admin/content/classes/5/lessons/13/quiz')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/admin/content/classes/5/lessons/13/quiz')

    await router.push('/admin/content/classes/5')
    await flushPromises()
    const dialog = wrapper.get('[data-testid="confirm-dialog"]')
    expect(dialog.get('h2').text()).toBe('Leave without saving?')
    expect(dialog.text()).toContain(
      'This lesson has changes you haven’t saved. Keep editing to save a draft, or leave and lose them.',
    )
    expect(wrapper.get('[data-testid="confirm-dialog-cancel"]').text()).toBe('Keep editing')
    expect(wrapper.get('[data-testid="confirm-dialog-confirm"]').text()).toBe('Leave without saving')
    await wrapper.get('[data-testid="confirm-dialog-cancel"]').trigger('click')
    expect(router.currentRoute.value.path).toBe('/admin/content/classes/5/lessons/13/quiz')

    await router.push('/admin/content/classes/5')
    await flushPromises()
    await wrapper.get('[data-testid="confirm-dialog-confirm"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/admin/content/classes/5')
    expect(api.saveContent).not.toHaveBeenCalled()
  })

  it('asks before the breadcrumb leaves for Classes with unsaved edits (X2.25)', async () => {
    const { wrapper, router } = await mountEditor()
    await wrapper.get('#lesson-title').setValue('University Life 2')

    const classes = wrapper.get('.breadcrumb a')
    expect(classes.text()).toBe('Classes')
    await classes.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/admin/content/classes/5/lessons/13')
    expect(wrapper.get('[data-testid="confirm-dialog"] h2').text()).toBe('Leave without saving?')
    await wrapper.get('[data-testid="confirm-dialog-confirm"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/admin/content')
  })

  it('deletes a question at once and puts it back in its place with Undo (A3.36)', async () => {
    const full = lesson(13, { hasUnpublishedChanges: true })
    api.get.mockResolvedValue(full)
    api.deleteQuestion.mockResolvedValue(lesson(13, { hasUnpublishedChanges: true, quizzes: [quiz(4)] }))
    api.addQuestion.mockResolvedValue(full)
    const { wrapper } = await mountEditor('/admin/content/classes/5/lessons/13/quiz')

    await wrapper.get('button[aria-label="Delete question 5"]').trigger('click')
    await flushPromises()

    expect(api.deleteQuestion).toHaveBeenCalledWith(13, 305)
    const toast = wrapper.get('[data-testid="toast"]')
    expect(toast.text()).toContain('Question 5 deleted.')
    expect(publishHint(wrapper).text()).toBe('Add 1 question to publish. A lesson quiz has exactly 5.')

    await toast.get('.toast-action').trigger('click')
    await flushPromises()
    expect(api.addQuestion).toHaveBeenCalledWith(13, 3, {
      questionType: 'MULTIPLE_CHOICE',
      questionText: 'What does word 5 mean?',
      points: 2,
      correctAnswerText: null,
      explanation: 'Word 5 means it.',
      hint: null,
      audioUrl: null,
      imageUrl: null,
      options: [
        { optionText: 'Library', correctOption: true, feedback: null },
        { optionText: 'Canteen', correctOption: false, feedback: 'That’s 食堂.' },
      ],
      aiDraft: false,
      position: 5,
    })
    expect(wrapper.findAll('[data-testid="quiz-question"]')).toHaveLength(5)
  })

  it('puts a deleted AI draft back as one, so publishing still waits for it', async () => {
    const full = lesson(13, { status: 'DRAFT' })
    full.quizzes[0]!.questions[4] = question(5, true)
    api.get.mockResolvedValue(full)
    api.deleteQuestion.mockResolvedValue(lesson(13, { status: 'DRAFT', quizzes: [quiz(4)] }))
    api.addQuestion.mockResolvedValue(full)
    const { wrapper } = await mountEditor('/admin/content/classes/5/lessons/13/quiz')

    await wrapper.get('button[aria-label="Delete question 5"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="toast"] .toast-action').trigger('click')
    await flushPromises()

    expect(api.addQuestion).toHaveBeenCalledWith(13, 3, expect.objectContaining({ aiDraft: true, position: 5 }))
    expect(publishHint(wrapper).text()).toBe('Check the AI draft (question 5) before you publish: Keep it or Edit it.')
  })

  it('waits to publish until every AI draft is kept or edited (A3.28, A3.37)', async () => {
    const drafted = lesson(13, { status: 'DRAFT' })
    drafted.quizzes[0]!.questions[3] = question(4, true)
    drafted.quizzes[0]!.questions[4] = question(5, true)
    const oneLeft = lesson(13, { status: 'DRAFT' })
    oneLeft.quizzes[0]!.questions[4] = question(5, true)
    api.get.mockResolvedValue(drafted)
    api.keepQuestion.mockResolvedValueOnce(oneLeft).mockResolvedValueOnce(lesson(13, { status: 'DRAFT' }))
    const { wrapper } = await mountEditor('/admin/content/classes/5/lessons/13/quiz')

    expect(publishHint(wrapper).text()).toBe(
      'Check the AI drafts (questions 4 and 5) before you publish: Keep or Edit each one.',
    )
    expect(publishButton(wrapper).element.disabled).toBe(true)

    await wrapper.get('button[aria-label="Keep question 4"]').trigger('click')
    await flushPromises()
    expect(api.keepQuestion).toHaveBeenLastCalledWith(13, 304)
    expect(publishHint(wrapper).text()).toBe('Check the AI draft (question 5) before you publish: Keep it or Edit it.')
    expect(publishButton(wrapper).element.disabled).toBe(true)

    await wrapper.get('button[aria-label="Keep question 5"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="ai-draft-tag"]').exists()).toBe(false)
    expect(publishHint(wrapper).exists()).toBe(false)
    expect(publishButton(wrapper).element.disabled).toBe(false)
    await publishButton(wrapper).trigger('click')
    expect(wrapper.get('[data-testid="publish-dialog"]').text()).toContain('AI drafts checked')
  })

  it('shows only the lesson still open when a save answers after another lesson was opened', async () => {
    const saved = deferred<LessonEditor>()
    api.saveContent.mockReturnValueOnce(saved.promise)
    const { wrapper, router } = await mountEditor()
    await wrapper.get('#lesson-title').setValue('University Life 2')
    await saveDraftButton(wrapper).trigger('click')

    // The save is out, so leaving does not ask.
    await router.push('/admin/content/classes/5/lessons/14')
    await flushPromises()
    expect(wrapper.get('h1').text()).toBe('Lesson 14')
    saved.resolve(lesson(13, { title: 'University Life 2', hasUnpublishedChanges: true }))
    await flushPromises()

    expect(wrapper.get('h1').text()).toBe('Lesson 14')
    expect(wrapper.find('[data-testid="toast"]').exists()).toBe(false)
  })

  it('has nothing to preview without a video and key phrases, and points to Content (A3.21)', async () => {
    const { wrapper } = await mountEditor('/admin/content/classes/5/lessons/21/preview')

    expect(wrapper.text()).toContain('Nothing to preview yet')
    expect(wrapper.text()).toContain('Add a video and key phrases to preview this lesson.')
    const goToContent = wrapper.get('.state-panel a')
    expect(goToContent.text()).toBe('Go to Content')
    expect(goToContent.attributes('href')).toBe('/admin/content/classes/5/lessons/21')
  })

  it('previews the lesson as learners will see it, with the edits typed so far and nothing that starts (A3.31)', async () => {
    const { wrapper, router } = await mountEditor()
    await wrapper.get('#lesson-title').setValue('University Life 2')

    await router.push('/admin/content/classes/5/lessons/13/preview')
    await flushPromises()

    expect(wrapper.text()).toContain('Learner view, including unpublished changes.')
    expect(wrapper.get('.lesson-preview-title').text()).toBe('University Life 2')
    expect(wrapper.text()).toContain('Mandarin for Campus Life · Unit 1: First Conversations')
    expect(wrapper.text()).toContain('3 activities · lesson quiz')
    expect(wrapper.text()).toContain('Activity 2 · Tracing')
    expect(wrapper.text()).toContain('5 questions')
    expect(buttonNamed(wrapper, 'Start quiz').element.disabled).toBe(true)
    // An older Flashcards activity without cards uses the key phrases.
    expect(wrapper.text()).toContain('Cards from the lesson’s key phrases')
  })

  it('previews the Flashcards cards: each word with its pinyin and meaning', async () => {
    const withCards = lesson()
    withCards.activities[0]!.cards = [
      { chinese: '图书馆', pinyin: 'túshūguǎn', meaning: 'library' },
      { chinese: '食堂', pinyin: 'shítáng', meaning: 'canteen' },
    ]
    api.get.mockResolvedValue(withCards)
    const { wrapper } = await mountEditor('/admin/content/classes/5/lessons/13/preview')

    const cards = wrapper.get('[data-testid="preview-cards"]')
    expect(cards.attributes('aria-label')).toBe('Cards')
    expect(cards.findAll('li').map((card) => card.text())).toEqual([
      '图书馆túshūguǎn · library',
      '食堂shítáng · canteen',
    ])
    expect(wrapper.text()).not.toContain('Cards from the lesson’s key phrases')
  })

  it('shows a lesson of an archived class read-only, with the way back to its class', async () => {
    api.get.mockResolvedValue(lesson(13, { classStatus: 'ARCHIVED', editable: false }))
    const { wrapper } = await mountEditor()

    expect(statusLine(wrapper).text()).toContain(
      'The class is archived. Restore it on the class page to edit this lesson.',
    )
    expect(wrapper.get('[data-testid="read-only-reason"]').text()).toBe('Read-only while the class is archived')
    expect(wrapper.find('[data-testid="save-draft"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="open-publish"]').exists()).toBe(false)
    expect(wrapper.get<HTMLInputElement>('#lesson-title').element.disabled).toBe(true)
    expect(wrapper.get('.lesson-footer a').attributes('href')).toBe('/admin/content/classes/5')
  })

  it('says once that the lesson was just created, and takes that out of the address', async () => {
    const { wrapper, router } = await mountEditor('/admin/content/classes/5/lessons/21?created=1')
    await flushPromises()

    expect(wrapper.get('[data-testid="toast"]').text()).toBe('Lesson created as a draft.')
    expect(router.currentRoute.value.fullPath).toBe('/admin/content/classes/5/lessons/21')
  })

  it('keeps the leave confirmation active while discardChanges is pending with unsaved edits', async () => {
    const pendingDiscard = deferred<LessonEditor>()
    api.get.mockResolvedValue(lesson(13, { status: 'PUBLISHED', hasUnpublishedChanges: true }))
    api.discardChanges.mockReturnValueOnce(pendingDiscard.promise)
    const { wrapper, router } = await mountEditor()

    await wrapper.get('#lesson-title').setValue('Unsaved draft title')
    await wrapper.get('[data-testid="discard-changes"]').trigger('click')
    await wrapper.get('[data-testid="confirm-dialog-confirm"]').trigger('click')

    // Save draft button must NOT report aria-busy while discard is running.
    expect(saveDraftButton(wrapper).attributes('aria-busy')).toBe('false')

    // Navigating away while discard is still pending must prompt to confirm leaving.
    await router.push('/admin/content/classes/5')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('admin-lesson')
    expect(wrapper.findAll('[data-testid="confirm-dialog"] h2').map((heading) => heading.text())).toContain(
      'Leave without saving?',
    )

    pendingDiscard.resolve(lesson(13, { status: 'PUBLISHED', hasUnpublishedChanges: false }))
    await flushPromises()
  })
})
