import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { setAccessToken } from '@/api/http'
import { stubSpeech } from '@/test/fakeSpeech'
import { buttonNamed, deferred } from '@/test/helpers'
import { answerCheck, hintSettings, practiceItem, practiceResult } from '@/test/learnFixtures'
import type { AnswerCheck, HintStep } from '@/types/learn'

import {
  enterSpelling,
  hintStep,
  listeningSet,
  menuSet,
  mountPlayer,
  sentencesSet,
  typingSet,
} from './practicePlayerTestHelpers'

const api = vi.hoisted(() => ({ check: vi.fn(), complete: vi.fn(), hint: vi.fn(), candidates: vi.fn() }))
const hintApi = vi.hoisted(() => ({ settings: vi.fn(), report: vi.fn() }))

vi.mock('@/api/learn', () => ({ practiceApi: api, hintApi }))

import AnswerPlayer from './AnswerPlayer.vue'

describe('AnswerPlayer', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    Object.values(hintApi).forEach((fn) => fn.mockReset())
    // A new sign-in: the shared hint settings are loaded again.
    setAccessToken(null)
    hintApi.settings.mockResolvedValue(hintSettings({ mode: 'OFF' }))
    api.complete.mockResolvedValue(practiceResult({ type: 'LISTENING' }))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  describe('Drag & Drop', () => {
    it('builds the sentence by click or by dragging, resets it, and is answered with how sure the student is', async () => {
      api.check.mockResolvedValue(
        answerCheck({ correctAnswer: '我去图书馆。', pinyin: 'Wǒ qù túshūguǎn.', meaning: null }),
      )
      const wrapper = await mountPlayer(AnswerPlayer, sentencesSet)
      const sure = () => wrapper.get('[data-testid="answer-sure"]')

      expect(sure().attributes('disabled')).toBeDefined()
      expect(wrapper.get('[data-testid="answer-guess"]').attributes('disabled')).toBeDefined()
      await wrapper.get('[data-testid="tile-1"]').trigger('click')
      expect(wrapper.get('[data-testid="build-answer"]').text()).toContain('我')
      await wrapper.get('[data-testid="reset"]').trigger('click')
      expect(wrapper.find('[data-testid="build-answer"] .learn-tile').exists()).toBe(false)

      // Dragging a tile to the sentence places it, dragging it back to the bank takes it out.
      expect(wrapper.get('[data-testid="tile-2"]').attributes('draggable')).toBe('true')
      await wrapper.get('[data-testid="tile-2"]').trigger('dragstart')
      await wrapper.get('[data-testid="build-answer"]').trigger('drop')
      expect(wrapper.get('[data-testid="build-answer"]').text()).toContain('去')
      await wrapper.get('[data-testid="build-answer"] .learn-tile').trigger('dragstart')
      await wrapper.get('.learn-build-bank').trigger('drop')
      expect(wrapper.find('[data-testid="build-answer"] .learn-tile').exists()).toBe(false)

      for (const tile of ['tile-1', 'tile-2', 'tile-0']) await wrapper.get(`[data-testid="${tile}"]`).trigger('click')
      expect(wrapper.text()).toContain('How sure are you?')
      await sure().trigger('click')
      await flushPromises()

      expect(api.check).toHaveBeenCalledWith('DRAG_DROP', 11, {
        itemKey: '1',
        answer: '我 去 图书馆',
        confidence: 'SURE',
      })
      const bar = wrapper.get('[data-testid="check-bar"]')
      expect(bar.text()).toContain('Correct!')
      expect(bar.text()).toContain('Wǒ qù túshūguǎn.')
      expect(bar.text()).toContain('+15 XP')
      expect(wrapper.find('[data-testid="reset"]').exists()).toBe(false)
    })

    it('starts the next sentence with an empty answer', async () => {
      api.check.mockResolvedValue(answerCheck())
      const wrapper = await mountPlayer(AnswerPlayer, {
        ...sentencesSet,
        items: [
          practiceItem('1', { meaning: 'I go.', tiles: ['我', '去'] }),
          practiceItem('2', { meaning: 'You go.', tiles: ['你', '去'] }),
        ],
      })

      await wrapper.get('[data-testid="tile-0"]').trigger('click')
      await wrapper.get('[data-testid="tile-1"]').trigger('click')
      await wrapper.get('[data-testid="answer-sure"]').trigger('click')
      await flushPromises()
      expect(document.activeElement).toBe(wrapper.get('[data-testid="continue"]').element)
      await wrapper.get('[data-testid="continue"]').trigger('click')
      await flushPromises()

      expect(wrapper.find('[data-testid="build-answer"] .learn-tile').exists()).toBe(false)
      expect(wrapper.get('[data-testid="tile-0"]').attributes('disabled')).toBeUndefined()
      expect(wrapper.get('[data-testid="answer-sure"]').attributes('disabled')).toBeDefined()
      // Continue went away with the sentence answered: the next sentence's instruction takes the focus.
      expect(document.activeElement).toBe(wrapper.get('[data-testid="question-stage"] h2').element)
    })

    it('does not accept an unrelated drop after a sentence drag is cancelled', async () => {
      const wrapper = await mountPlayer(AnswerPlayer, sentencesSet)
      await wrapper.get('[data-testid="tile-1"]').trigger('dragstart')
      await wrapper.get('[data-testid="tile-1"]').trigger('dragend')
      await wrapper.get('[data-testid="build-answer"]').trigger('drop')

      expect(wrapper.find('[data-testid="build-answer"] .learn-tile').exists()).toBe(false)
      expect(wrapper.get('[data-testid="answer-sure"]').attributes('disabled')).toBeDefined()
    })

    it('keeps the submitted sentence visible while its check is pending', async () => {
      const checked = deferred<AnswerCheck>()
      api.check.mockReturnValue(checked.promise)
      const wrapper = await mountPlayer(AnswerPlayer, sentencesSet)
      await wrapper.get('[data-testid="tile-1"]').trigger('click')
      await wrapper.get('[data-testid="answer-guess"]').trigger('click')
      await wrapper.get('[data-testid="reset"]').trigger('click')

      expect(wrapper.get('[data-testid="build-answer"]').text()).toContain('我')
      checked.resolve(answerCheck({ correct: false, correctAnswer: '我去' }))
      await flushPromises()
      await wrapper.get('[data-testid="continue"]').trigger('click')
      await flushPromises()
      expect(api.complete).toHaveBeenCalledWith('DRAG_DROP', 11, [{ itemKey: '1', answer: '我' }], undefined)
    })
  })

  describe('Listening', () => {
    it('checks only once the student says how sure they are; wrong and sure leads the Mistakes list', async () => {
      api.check.mockResolvedValue(answerCheck({ correct: false, correctAnswer: '一杯茶', explanation: 'Chá is tea.' }))
      const wrapper = await mountPlayer(AnswerPlayer, listeningSet)

      await wrapper.get('[data-testid="option-1"]').trigger('click')
      expect(api.check).not.toHaveBeenCalled()
      expect(wrapper.find('[data-testid="skip"]').exists()).toBe(true)
      await wrapper.get('[data-testid="answer-sure"]').trigger('click')
      await flushPromises()

      expect(api.check).toHaveBeenCalledWith('LISTENING', 12, {
        itemKey: '0',
        answer: '一碗米饭',
        confidence: 'SURE',
      })
      const bar = wrapper.get('[data-testid="check-bar"]')
      expect(bar.classes()).toContain('is-wrong')
      expect(bar.text()).toContain('Not quite, and you were sure.')
      expect(bar.text()).toContain('一杯茶')
      expect(bar.text()).toContain('It goes to the top of your Mistakes list.')
      expect(bar.text()).toContain('Chá is tea.')
      expect(bar.text()).not.toContain('XP')
    })

    it('brings a right guess back once more at the end of the run, grading only its first answer', async () => {
      api.check.mockResolvedValue(answerCheck({ correct: true, correctAnswer: 'Menu', pinyin: null, meaning: null }))
      api.complete.mockResolvedValue(practiceResult({ type: 'LISTENING' }))
      const wrapper = await mountPlayer(AnswerPlayer, menuSet)
      expect(wrapper.text()).toContain('Question 1 of 2')

      await wrapper.get('[data-testid="option-1"]').trigger('click')
      await wrapper.get('[data-testid="answer-guess"]').trigger('click')
      await flushPromises()
      const bar = wrapper.get('[data-testid="check-bar"]')
      expect(bar.text()).toContain('Correct, counted as a guess.')
      expect(bar.text()).toContain('It comes back once more to make sure.')
      expect(wrapper.text()).toContain('Question 1 of 3')

      await wrapper.get('[data-testid="continue"]').trigger('click')
      await wrapper.get('[data-testid="option-1"]').trigger('click')
      await wrapper.get('[data-testid="answer-sure"]').trigger('click')
      await flushPromises()
      await wrapper.get('[data-testid="continue"]').trigger('click')
      expect(wrapper.text()).toContain('Question 3 of 3')
      expect(wrapper.get('[data-testid="once-more"]').text()).toBe('Once more, to make sure')
      expect(wrapper.text()).toContain('Question 1')

      await wrapper.get('[data-testid="option-1"]').trigger('click')
      await wrapper.get('[data-testid="answer-sure"]').trigger('click')
      await flushPromises()
      expect(wrapper.get('[data-testid="check-bar"]').text()).not.toContain('comes back')
      await wrapper.get('[data-testid="continue"]').trigger('click')
      await flushPromises()
      expect(api.complete).toHaveBeenCalledWith(
        'LISTENING',
        14,
        [
          { itemKey: '1', answer: 'Menu' },
          { itemKey: '2', answer: 'Menu' },
        ],
        undefined,
      )
      // Both answered, and nothing went to Mistakes: the done card says so.
      expect(wrapper.emitted('finished')![0]![2]).toEqual({ answered: 2, mistakes: 0 })
    })

    it('counts every check that went to Mistakes, and the first answers given, for the done card', async () => {
      api.check
        .mockResolvedValueOnce(answerCheck({ correct: false, correctAnswer: 'Menu' }))
        .mockResolvedValueOnce(answerCheck({ correct: false, correctAnswer: 'Menu' }))
      api.complete.mockResolvedValue(practiceResult({ type: 'LISTENING', correctCount: 0, allCorrect: false }))
      const wrapper = await mountPlayer(AnswerPlayer, menuSet)

      await wrapper.get('[data-testid="option-0"]').trigger('click')
      await wrapper.get('[data-testid="answer-guess"]').trigger('click')
      await flushPromises()
      expect(wrapper.get('[data-testid="check-bar"]').text()).toContain('It was Menu')
      expect(wrapper.get('[data-testid="check-bar"]').text()).toContain('It’s on your Mistakes list.')
      await wrapper.get('[data-testid="continue"]').trigger('click')
      // A skip whose audio played (here: there is none to play) is a mistake too.
      await wrapper.get('[data-testid="option-0"]').trigger('click')
      await wrapper.get('[data-testid="skip"]').trigger('click')
      await flushPromises()
      expect(api.check).toHaveBeenLastCalledWith('LISTENING', 14, { itemKey: '2', answer: null, confidence: null })
      expect(wrapper.get('[data-testid="check-bar"]').text()).toContain('Skipped.')
      expect(wrapper.find('.learn-option.is-wrong').exists()).toBe(false)
      expect(wrapper.find('.learn-option[aria-pressed="true"]').exists()).toBe(false)
      await wrapper.get('[data-testid="continue"]').trigger('click')
      await flushPromises()

      expect(wrapper.emitted('finished')![0]![2]).toEqual({ answered: 1, mistakes: 2 })
    })

    describe('audio that does not play', () => {
      it.each(['no speech API', 'Cantonese only'])('shows the answer and counts no mistake with %s', async (device) => {
        const speech = device === 'Cantonese only' ? stubSpeech([{ lang: 'zh-HK' }]) : null
        api.check.mockResolvedValue(
          answerCheck({ correct: false, correctAnswer: '一杯茶', pinyin: 'yì bēi chá', meaning: 'a cup of tea' }),
        )
        api.complete.mockResolvedValue(practiceResult({ type: 'LISTENING', correctCount: 0, allCorrect: false }))
        const wrapper = await mountPlayer(AnswerPlayer, listeningSet)

        expect(wrapper.get('[data-testid="audio-failed"] [role="alert"]').text()).toBe('Audio didn’t load')
        const retry = wrapper.find('[data-testid="audio-retry"]')
        expect(retry.exists()).toBe(Boolean(speech))
        if (speech) {
          await retry.trigger('click')
          expect(speech.speak).not.toHaveBeenCalled()
        }

        await wrapper.get('[data-testid="skip"]').trigger('click')
        await flushPromises()
        expect(api.check).toHaveBeenCalledWith('LISTENING', 12, {
          itemKey: '0',
          answer: null,
          confidence: null,
          audioFailed: true,
        })
        const bar = wrapper.get('[data-testid="check-bar"]')
        expect(bar.get('.practice-feedback-title').text()).toBe('Skipped.')
        expect(bar.text()).toContain('It was 一杯茶 · yì bēi chá · a cup of tea')
        expect(bar.get('.practice-feedback-note').text()).toBe('The audio didn’t play, so it isn’t counted.')
        expect(bar.text()).not.toContain('Mistakes')

        await wrapper.get('[data-testid="continue"]').trigger('click')
        await flushPromises()
        expect(api.complete).toHaveBeenCalledWith('LISTENING', 12, [{ itemKey: '0', answer: null }], undefined)
        expect(wrapper.emitted('finished')![0]![2]).toEqual({ answered: 0, mistakes: 0 })
      })

      it('says so with Retry when the speech fails before it starts; a skip counts again once Retry plays', async () => {
        const synthesis = stubSpeech()
        api.check.mockResolvedValue(answerCheck({ correct: false, correctAnswer: '一杯茶' }))
        const wrapper = await mountPlayer(AnswerPlayer, listeningSet)
        expect(wrapper.find('[data-testid="audio-failed"]').exists()).toBe(false)

        await wrapper.get('.learn-listen-play').trigger('click')
        expect(synthesis.utterance().text).toBe('一杯茶')
        synthesis.utterance().onerror?.()
        await flushPromises()
        expect(wrapper.get('[data-testid="audio-failed"]').text()).toContain('Audio didn’t load')
        expect(wrapper.find('.learn-listen-play').exists()).toBe(false)

        synthesis.setVoices([{ lang: 'cmn' }])
        await wrapper.get('[data-testid="audio-retry"]').trigger('click')
        expect(synthesis.speak).toHaveBeenCalledTimes(2)
        synthesis.utterance().onstart?.()
        await flushPromises()
        expect(wrapper.find('[data-testid="audio-failed"]').exists()).toBe(false)
        expect(wrapper.find('.learn-listen-play').exists()).toBe(true)

        // Losing the voice later prevents replay, but the learner already heard this item.
        synthesis.setVoices([{ lang: 'yue-HK' }])
        await flushPromises()
        await wrapper.get('[data-testid="audio-retry"]').trigger('click')
        expect(synthesis.speak).toHaveBeenCalledTimes(2)
        await wrapper.get('[data-testid="skip"]').trigger('click')
        await flushPromises()
        expect(api.check).toHaveBeenCalledWith('LISTENING', 12, { itemKey: '0', answer: null, confidence: null })
        expect(wrapper.get('.practice-feedback-note').text()).toBe('It’s on your Mistakes list.')
      })

      it('does not take a line cut off by the next one for audio that failed', async () => {
        const synthesis = stubSpeech()
        const wrapper = await mountPlayer(AnswerPlayer, listeningSet)

        await wrapper.get('.learn-listen-play').trigger('click')
        const first = synthesis.utterance()
        await wrapper.get('.learn-listen-slow').trigger('click')
        // Slow cancels the normal line before it starts.
        first.onerror?.()
        synthesis.utterance().onstart?.()
        synthesis.utterance().onend?.()
        await flushPromises()

        expect(wrapper.find('[data-testid="audio-failed"]').exists()).toBe(false)
        expect(synthesis.utterance().rate).toBe(0.55)
      })
    })
  })

  describe('Typing', () => {
    beforeEach(() => {
      vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    })

    it('offers the words the pinyin spells, chooses one by its number and asks how sure the student is', async () => {
      api.candidates.mockResolvedValue(['写', '谢', '谢谢', '写写'])
      api.check.mockResolvedValue(
        answerCheck({ correct: false, correctAnswer: '谢谢', pinyin: 'xièxie', meaning: 'Thank you' }),
      )
      const wrapper = await mountPlayer(AnswerPlayer, typingSet)
      expect(wrapper.text()).toContain('Word 1 of 1')
      expect(wrapper.text()).toContain('“Thank you”')

      await enterSpelling(wrapper, 'xiexie')
      expect(api.candidates).toHaveBeenCalledWith(15, '0', 'xiexie')
      expect(wrapper.findAll('[data-testid^="candidate-"]').map((chip) => chip.text())).toEqual([
        '1写',
        '2谢',
        '3谢谢',
        '4写写',
      ])

      expect(wrapper.get('[data-testid="answer-sure"]').attributes('disabled')).toBeDefined()
      await wrapper.get('[data-testid="typing-input"]').trigger('keydown', { key: '2' })
      expect(wrapper.get('[data-testid="candidate-1"]').attributes('aria-pressed')).toBe('true')
      await wrapper.get('[data-testid="answer-sure"]').trigger('click')
      await flushPromises()

      expect(api.check).toHaveBeenCalledWith('TYPING', 15, {
        itemKey: '0',
        answer: '谢',
        confidence: 'SURE',
      })
      expect(wrapper.get('[data-testid="candidate-1"]').classes()).toContain('is-wrong')
      expect(wrapper.get('[data-testid="candidate-2"]').classes()).toContain('is-correct')
      expect(wrapper.get('[data-testid="check-bar"]').text()).toContain('Not quite, and you were sure.')
    })

    it('offers words only for the spelling in the field, not one changed or cleared during its lookup', async () => {
      const wrapper = await mountPlayer(AnswerPlayer, typingSet)
      const input = wrapper.get('[data-testid="typing-input"]')
      const offered = () => wrapper.findAll('[data-testid^="candidate-"]').map((chip) => chip.text())

      // Cleared while the words of “xie” are on their way.
      const cleared = deferred<string[]>()
      api.candidates.mockReturnValue(cleared.promise)
      await input.setValue('xie')
      vi.advanceTimersByTime(300)
      await input.setValue('')
      cleared.resolve(['写', '谢'])
      await flushPromises()
      expect(api.candidates).toHaveBeenCalledWith(15, '0', 'xie')
      expect(offered()).toEqual([])

      // Typed on while they are on their way: only the new spelling's words, once its own lookup answers.
      const earlier = deferred<string[]>()
      api.candidates.mockReturnValue(earlier.promise)
      await input.setValue('xie')
      vi.advanceTimersByTime(300)
      await input.setValue('xiexie')
      earlier.resolve(['写', '谢'])
      await flushPromises()
      expect(offered()).toEqual([])

      api.candidates.mockResolvedValue(['谢谢'])
      vi.advanceTimersByTime(300)
      await flushPromises()
      expect(api.candidates).toHaveBeenLastCalledWith(15, '0', 'xiexie')
      expect(offered()).toEqual(['1谢谢'])

      await input.setValue('xie')
      expect(offered()).toEqual([])
      await input.trigger('keydown', { key: '1' })
      expect(wrapper.get('[data-testid="answer-sure"]').attributes('disabled')).toBeDefined()
    })

    it('says when a spelling makes no word', async () => {
      api.candidates.mockResolvedValue([])
      const wrapper = await mountPlayer(AnswerPlayer, typingSet)
      await enterSpelling(wrapper, 'xiexi')
      expect(wrapper.text()).toContain('No word is spelt “xiexi”. Check the spelling.')
      expect(wrapper.find('[data-testid="candidates"]').exists()).toBe(false)
    })

    it('looks up authored pinyin up to the full 200-character limit', async () => {
      api.candidates.mockResolvedValue(['你好'])
      const wrapper = await mountPlayer(AnswerPlayer, typingSet)
      const input = wrapper.get<HTMLInputElement>('[data-testid="typing-input"]')
      expect(input.element.maxLength).toBe(200)

      const pinyin = 'nihao'.repeat(40)
      await enterSpelling(wrapper, pinyin)
      expect(api.candidates).toHaveBeenCalledExactlyOnceWith(15, '0', pinyin)
      expect(wrapper.get('[data-testid="candidate-0"]').text()).toContain('你好')
    })

    it('retries a failed candidate lookup without changing the spelling and leaves composing keys to the input', async () => {
      api.candidates.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(['写', '谢'])
      const wrapper = await mountPlayer(AnswerPlayer, typingSet)
      await enterSpelling(wrapper, 'xie')
      expect(wrapper.get('[role="alert"]').text()).toBe('Couldn’t load the words for this spelling.')
      await buttonNamed(wrapper, 'Try again').trigger('click')
      await flushPromises()
      expect(api.candidates).toHaveBeenLastCalledWith(15, '0', 'xie')
      const input = wrapper.get('[data-testid="typing-input"]')
      await input.trigger('keydown', { key: '2', isComposing: true })
      expect(wrapper.get('[data-testid="candidate-1"]').attributes('aria-pressed')).toBe('false')
      await input.trigger('keydown', { key: '2' })
      expect(wrapper.get('[data-testid="candidate-1"]').attributes('aria-pressed')).toBe('true')
    })

    it('takes back a chosen word that a hint then rules out', async () => {
      hintApi.settings.mockResolvedValue(hintSettings({ mode: 'ON_REQUEST' }))
      api.candidates.mockResolvedValue(['写写', '谢谢'])
      api.hint.mockResolvedValue(hintStep({ words: ['写写'], ruledOut: ['写写'] }))
      const wrapper = await mountPlayer(AnswerPlayer, typingSet)
      await enterSpelling(wrapper, 'xiexie')
      await wrapper.get('[data-testid="typing-input"]').trigger('keydown', { key: '1' })
      expect(wrapper.get('[data-testid="candidate-0"]').attributes('aria-pressed')).toBe('true')

      await wrapper.get('[data-testid="hint-show"]').trigger('click')
      await flushPromises()

      expect(wrapper.get('[data-testid="candidate-0"]').attributes('disabled')).toBeDefined()
      expect(wrapper.get('[data-testid="candidate-0"]').attributes('aria-pressed')).toBe('false')
      expect(wrapper.get('[data-testid="answer-guess"]').attributes('disabled')).toBeDefined()
    })

    it('names the set’s XP on the answer that earns it, as the other answered sets do', async () => {
      api.candidates.mockResolvedValue(['写写', '谢谢'])
      api.check.mockResolvedValue(answerCheck({ correct: true, correctAnswer: '谢谢', pinyin: 'xièxie' }))
      const wrapper = await mountPlayer(AnswerPlayer, typingSet)
      await enterSpelling(wrapper, 'xiexie')
      await wrapper.get('[data-testid="typing-input"]').trigger('keydown', { key: '2' })
      await wrapper.get('[data-testid="answer-sure"]').trigger('click')
      await flushPromises()

      // The only word, right: the run earns the set's XP.
      expect(wrapper.get('[data-testid="check-bar"]').text()).toContain('+10 XP')
    })

    it('Reset empties the field and the chosen word, as Reset does in the other activities', async () => {
      api.candidates.mockResolvedValue(['写写', '谢谢'])
      const wrapper = await mountPlayer(AnswerPlayer, typingSet)
      const reset = () => wrapper.get('[data-testid="reset"]')
      expect(reset().text()).toBe('Reset')
      expect(reset().attributes('aria-disabled')).toBe('true')

      await enterSpelling(wrapper, 'xiexie')
      await wrapper.get('[data-testid="typing-input"]').trigger('keydown', { key: '2' })
      expect(reset().attributes('aria-disabled')).toBe('false')

      await reset().trigger('click')
      expect((wrapper.get('[data-testid="typing-input"]').element as HTMLInputElement).value).toBe('')
      expect(wrapper.find('[data-testid="candidates"]').exists()).toBe(false)
      expect(wrapper.get('[data-testid="answer-sure"]').attributes('disabled')).toBeDefined()
      expect(document.activeElement).toBe(wrapper.get('[data-testid="typing-input"]').element)
    })
  })

  describe('Hints and reporting', () => {
    async function startWithPendingHint(perSet: number) {
      hintApi.settings.mockResolvedValue(hintSettings({ mode: 'ON_REQUEST', perSet }))
      const late = deferred<HintStep>()
      api.hint.mockReturnValueOnce(late.promise)
      api.check.mockResolvedValue(answerCheck({ correct: false, correctAnswer: 'Menu' }))
      const wrapper = await mountPlayer(AnswerPlayer, menuSet)
      await wrapper.get('[data-testid="hint-show"]').trigger('click')
      return { wrapper, late }
    }

    it('offers a hint only after the level’s wait, then rules out an answer and makes the answer a guess', async () => {
      vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
      hintApi.settings.mockResolvedValue(hintSettings({ mode: 'AUTOMATIC', delaySeconds: 15 }))
      api.hint.mockResolvedValue(hintStep())
      api.check.mockResolvedValue(answerCheck({ correct: true, correctAnswer: 'Menu' }))
      const wrapper = await mountPlayer(AnswerPlayer, menuSet)

      vi.advanceTimersByTime(14_000)
      await flushPromises()
      expect(wrapper.find('[data-testid="hint-offer"]').exists()).toBe(false)
      vi.advanceTimersByTime(1_000)
      await flushPromises()
      expect(wrapper.get('[data-testid="hint-offer"]').text()).toContain('Stuck? SparkBot can give you a hint.')

      await wrapper.get('[data-testid="hint-show"]').trigger('click')
      await flushPromises()
      // With the run it belongs to, as the server signed it: the server counts a run's hints against the level's limit.
      expect(api.hint).toHaveBeenCalledWith(
        'LISTENING',
        14,
        '1',
        expect.objectContaining({ runStartedAt: menuSet.runStartedAt, runSignature: menuSet.runSignature }),
      )
      const card = wrapper.get('[data-testid="hint-card"]')
      expect(card.get('.hint-panel-title').text()).toBe('SparkBot hint')
      expect(card.text()).toContain('AI can make mistakes.')
      expect(card.text()).toContain('It isn’t answer 4: Cup.')
      // That the answer is now a guess is said once, where the learner answers.
      expect(card.text()).not.toContain('counts as a guess')
      expect(card.get('.hint-panel-note').text()).toBe('Hints never cost XP.')
      expect(wrapper.get('[data-testid="option-3"]').attributes('disabled')).toBeDefined()
      expect(wrapper.get('[data-testid="option-3"]').classes()).toContain('is-ruled-out')
      // Until an answer is picked the footer only asks, with both buttons off.
      expect(wrapper.get('.practice-confidence-label').text()).toBe('How sure are you?')
      expect(wrapper.get('[data-testid="answer-guess"]').attributes('disabled')).toBeDefined()

      await wrapper.get('[data-testid="option-1"]').trigger('click')
      expect(wrapper.get('[data-testid="answer-guess"]').attributes('disabled')).toBeUndefined()
      expect(wrapper.get('[data-testid="answer-sure"]').attributes('disabled')).toBeDefined()
      expect(wrapper.get('.practice-confidence-label').text()).toBe('Hint used · counts as a guess')
      await wrapper.get('[data-testid="answer-guess"]').trigger('click')
      await flushPromises()
      expect(api.check).toHaveBeenCalledWith('LISTENING', 14, {
        itemKey: '1',
        answer: 'Menu',
        confidence: 'GUESS',
      })
    })

    it('offers a hint from the start in “Only when I ask”, and none when hints are off', async () => {
      hintApi.settings.mockResolvedValue(hintSettings({ mode: 'ON_REQUEST', perSet: 3 }))
      const asked = await mountPlayer(AnswerPlayer, menuSet)
      expect(asked.get('[data-testid="hint-offer"]').text()).toContain('Want a hint? Ask SparkBot whenever you like.')
      expect(asked.get('[data-testid="hint-offer"]').text()).toContain('3 hints left in this set')

      setAccessToken(null)
      hintApi.settings.mockResolvedValue(hintSettings({ mode: 'OFF' }))
      const off = await mountPlayer(AnswerPlayer, menuSet)
      expect(off.find('[data-testid="hint-offer"]').exists()).toBe(false)
    })

    it('says why the server refused a hint and offers none more for that question', async () => {
      hintApi.settings.mockResolvedValue(hintSettings({ mode: 'ON_REQUEST', perSet: 2 }))
      api.hint.mockRejectedValueOnce({
        response: { status: 400, data: { success: false, message: 'There are no hints left in this set.' } },
      })
      const wrapper = await mountPlayer(AnswerPlayer, menuSet)
      await wrapper.get('[data-testid="hint-show"]').trigger('click')
      await flushPromises()

      expect(wrapper.get('[data-testid="hint-failed"]').text()).toBe('There are no hints left in this set.')
      expect(wrapper.find('[data-testid="hint-offer"]').exists()).toBe(false)
      expect(api.hint).toHaveBeenCalledTimes(1)
    })

    it('keeps a hint that arrives after its question was left off the next question', async () => {
      const { wrapper, late } = await startWithPendingHint(3)

      // While the hint is on its way the answer waits for it: it decides whether the answer can still be Sure.
      await wrapper.get('[data-testid="option-1"]').trigger('click')
      expect(wrapper.get('[data-testid="answer-guess"]').attributes('disabled')).toBeDefined()
      expect(wrapper.get('[data-testid="answer-sure"]').attributes('disabled')).toBeDefined()

      // The first question is skipped and left before its hint (ruling out “Cup”) arrives.
      await wrapper.get('[data-testid="skip"]').trigger('click')
      await flushPromises()
      await wrapper.get('[data-testid="continue"]').trigger('click')
      expect(wrapper.text()).toContain('Question 2 of 2')
      late.resolve(hintStep())
      await flushPromises()

      expect(wrapper.find('[data-testid="hint-card"]').exists()).toBe(false)
      expect(wrapper.get('[data-testid="option-3"]').attributes('disabled')).toBeUndefined()
      await wrapper.get('[data-testid="option-3"]').trigger('click')
      expect(wrapper.get('[data-testid="answer-sure"]').attributes('disabled')).toBeUndefined()
      // The server gave that hint, so it still counts against the set's hints.
      expect(wrapper.get('[data-testid="hint-offer"]').text()).toContain('2 hints left in this set')
    })

    it('takes the offer back when the hint of the question left uses up the set’s hints', async () => {
      const { wrapper, late } = await startWithPendingHint(1)

      await wrapper.get('[data-testid="skip"]').trigger('click')
      await flushPromises()
      await wrapper.get('[data-testid="continue"]').trigger('click')
      expect(wrapper.get('[data-testid="hint-offer"]').text()).toContain('1 hint left in this set')

      late.resolve(hintStep())
      await flushPromises()
      expect(wrapper.find('[data-testid="hint-offer"]').exists()).toBe(false)
      expect(api.hint).toHaveBeenCalledOnce()
    })

    it('lets the next question be answered while the hint of the question left is still on its way', async () => {
      const { wrapper, late } = await startWithPendingHint(3)

      await wrapper.get('[data-testid="skip"]').trigger('click')
      await flushPromises()
      await wrapper.get('[data-testid="continue"]').trigger('click')
      expect(wrapper.text()).toContain('Question 2 of 2')

      await wrapper.get('[data-testid="option-1"]').trigger('click')
      expect(wrapper.get('[data-testid="answer-sure"]').attributes('disabled')).toBeUndefined()
      expect(wrapper.get('[data-testid="hint-show"]').attributes('disabled')).toBeUndefined()
      late.resolve(hintStep())
      await flushPromises()
      expect(wrapper.find('[data-testid="hint-card"]').exists()).toBe(false)
    })

    it('stops scheduling hints when the player closes during a request', async () => {
      vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
      hintApi.settings.mockResolvedValue(hintSettings({ mode: 'AUTOMATIC', delaySeconds: 15 }))
      const pending = deferred<HintStep>()
      api.hint.mockReturnValueOnce(pending.promise)
      const wrapper = await mountPlayer(AnswerPlayer, menuSet)
      vi.advanceTimersByTime(15_000)
      await flushPromises()
      await wrapper.get('[data-testid="hint-show"]').trigger('click')
      expect(api.hint).toHaveBeenCalledOnce()
      wrapper.unmount()

      pending.resolve(hintStep())
      await flushPromises()
      expect(vi.getTimerCount()).toBe(0)
    })

    it('offers a hint again after one that did not arrive', async () => {
      hintApi.settings.mockResolvedValue(hintSettings({ mode: 'ON_REQUEST' }))
      api.hint.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(hintStep())
      const wrapper = await mountPlayer(AnswerPlayer, menuSet)
      await wrapper.get('[data-testid="hint-show"]').trigger('click')
      await flushPromises()
      expect(wrapper.get('[data-testid="hint-failed"]').text()).toBe('The hint could not be loaded. Please try again.')

      await wrapper.get('[data-testid="hint-show"]').trigger('click')
      await flushPromises()
      expect(wrapper.get('[data-testid="hint-card"]').text()).toContain('It isn’t answer 4: Cup.')
    })

    it('reports a hint as an AI answer, and only a sent report makes the hint say Reported', async () => {
      hintApi.settings.mockResolvedValue(hintSettings({ mode: 'ON_REQUEST' }))
      api.hint.mockResolvedValueOnce(hintStep())
      hintApi.report.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(undefined)
      const wrapper = await mountPlayer(AnswerPlayer, menuSet)
      await wrapper.get('[data-testid="hint-show"]').trigger('click')
      await flushPromises()

      // "AI can make mistakes." and a quiet Report at the end of the card's title row.
      const report = () => wrapper.get<HTMLButtonElement>('[data-testid="hint-report"]')
      expect(wrapper.get('.hint-panel-head .hint-panel-ai').text()).toBe('AI can make mistakes.')
      expect(report().text()).toBe('Report')
      expect(report().attributes('aria-label')).toBe('Report this hint')
      expect(report().classes()).toEqual(expect.arrayContaining(['btn-ghost', 'btn-sm']))

      // (The stubbed teleport renders the dialog anew on every update, so its parts are looked up each time.)
      const dialog = () => wrapper.get('[data-testid="hint-report-dialog"]')
      const send = async () => {
        await dialog().get('[data-testid="hint-report-form"]').trigger('submit')
        await flushPromises()
      }
      await report().trigger('click')
      await flushPromises()
      expect(dialog().get('h2').text()).toBe('Report this hint')

      // A failed send leaves the hint reportable (the dialog's own handling is in HintReportDialog.test).
      await dialog().get('textarea').setValue('Cup is the right answer.')
      await send()
      expect(report().element.disabled).toBe(false)

      await send()
      expect(hintApi.report).toHaveBeenCalledTimes(2)
      const sent = hintApi.report.mock.calls[1]![0] as {
        reasonCategory: string
        description: string
        promptSnapshot: string
        aiResponseSnapshot: string
      }
      expect(sent.reasonCategory).toBe('INCORRECT_CONTENT')
      expect(sent.description).toBe('Cup is the right answer.')
      expect(sent.promptSnapshot).toMatch(/^LISTENING #14 \/ 1 · \//)
      expect(sent.aiResponseSnapshot).toBe('It isn’t answer 4: Cup.')

      // Closed, confirmed by the shared toast, and this hint cannot be reported twice.
      expect(wrapper.find('[data-testid="hint-report-dialog"]').exists()).toBe(false)
      expect(wrapper.get('[data-testid="toast"]').text()).toBe(
        'Report sent. An admin replies in Ask SparkBot › My reports.',
      )
      expect(report().text()).toBe('Reported')
      expect(report().element.disabled).toBe(true)
      expect(document.activeElement).toBe(wrapper.get('[data-testid="hint-card"]').element)
      // A question has one hint: nothing further is offered after it.
      expect(wrapper.find('[data-testid="hint-next"]').exists()).toBe(false)
    })
  })
})
