<template>
  <div class="page-stack teacher-content-page">
    <PageHeaderBlock :title="t('teacherContent.title')" :description="t('teacherContent.description')" icon="content" />

    <DataNotice
      :using-fallback="usingFallback"
      :error="noticeError"
      :success="actionSuccess"
      :detail="teacherContentDetail"
    />

    <section class="panel teacher-content-toolbar">
      <button
        v-for="tab in contentTabs"
        :key="tab.key"
        class="teacher-content-tab"
        :class="{ 'is-active': tab.key === activeSurfaceTab }"
        type="button"
        @click="selectSurfaceTab(tab.key)"
      >
        <IconGlyph :name="tab.icon" :size="18" />
        <span>{{ tab.label }}</span>
      </button>
    </section>

    <section class="teacher-content-layout">
      <aside class="panel teacher-course-sidebar">
        <div class="row-between teacher-sidebar-header">
          <strong>{{ t('teacherContent.myCourses') }}</strong>
          <button class="student-link-button" type="button" @click="reloadAll">
            {{ t('teacherContent.refresh') }}
          </button>
        </div>

        <button
          class="btn btn-primary teacher-create-course"
          type="button"
          :disabled="previewReadOnly"
          @click="openPackageBuilder()"
        >
          <span class="teacher-inline-plus">+</span>
          <span>{{ t('teacherContent.createCourse') }}</span>
        </button>

        <div class="teacher-course-list">
          <article
            v-for="item in displayCourses"
            :key="item.id"
            class="teacher-course-item"
            :class="{ 'is-active': item.id === selectedCourse.id }"
            @click="selectCourse(item.id)"
          >
            <CourseSceneThumb class="teacher-course-thumb" :variant="courseThumbVariant(item.id)" />
            <div class="stack teacher-course-copy">
              <strong>{{ item.title }}</strong>
              <span class="muted">{{ item.subtitle }}</span>
              <div class="row teacher-course-meta">
                <StatusPill :label="item.statusLabel" :tone="contentStatusTone(item.statusLabel)" />
                <span class="muted">{{ t('teacherContent.items', { count: item.lessonCount }) }}</span>
              </div>
            </div>
          </article>
        </div>

        <button class="teacher-view-all" type="button" @click="showAllCourses">
          <span>{{ t('teacherContent.viewAllCourses') }}</span>
          <span class="teacher-view-all-arrow">></span>
        </button>
      </aside>

      <div class="teacher-content-main">
        <section v-if="showCourseWorkspace" class="panel teacher-content-card">
          <div class="row-between teacher-content-summary">
            <div class="row teacher-content-summary-left">
              <CourseSceneThumb
                class="teacher-course-thumb teacher-course-thumb--large"
                :size="84"
                :variant="courseThumbVariant(selectedCourse.id)"
              />

              <div class="stack teacher-content-summary-copy">
                <h2 class="teacher-content-title">{{ selectedCourse.title }}</h2>
                <div class="row teacher-content-summary-meta">
                  <span class="muted">{{ selectedCourse.subtitle }}</span>
                  <StatusPill
                    :label="selectedCourse.statusLabel"
                    :tone="contentStatusTone(selectedCourse.statusLabel)"
                  />
                </div>
              </div>
            </div>

            <div class="row teacher-content-summary-right">
              <span class="muted">{{ t('teacherContent.items', { count: selectedCourse.lessonCount }) }}</span>
              <button
                class="student-link-button"
                type="button"
                :disabled="!selectedCourse.source || previewReadOnly"
                @click="openPackageBuilder(selectedCourse.source ?? undefined)"
              >
                {{ t('teacherContent.editCourse') }}
              </button>
              <button
                class="btn btn-secondary"
                type="button"
                :disabled="!selectedCourse.source || previewReadOnly || packageBusy"
                @click="archiveSelectedPackage"
              >
                {{ packageArchiveLabel }}
              </button>
            </div>
          </div>

          <div class="teacher-overview-grid">
            <div>
              <strong>{{ t('teacherContent.courseOverview') }}</strong>
              <p class="muted">{{ selectedCourse.description }}</p>
            </div>

            <div class="teacher-overview-meta">
              <div>
                <span class="muted">{{ t('teacherContent.level') }}</span>
                <strong>{{ selectedCourse.levelLabel }}</strong>
              </div>
              <div>
                <span class="muted">{{ t('teacherContent.access') }}</span>
                <strong>{{ selectedCourse.accessLabel }}</strong>
              </div>
              <div>
                <span class="muted">{{ t('teacherContent.lastUpdated') }}</span>
                <strong>{{ selectedCourse.updatedLabel }}</strong>
              </div>
            </div>
          </div>

          <div class="row-between teacher-content-section-head">
            <strong>{{ t('teacherContent.packageItems') }}</strong>
            <button
              class="btn btn-secondary teacher-add-module"
              type="button"
              :disabled="!selectedCourse.source || previewReadOnly"
              @click="openPackageBuilder(selectedCourse.source ?? undefined)"
            >
              <span class="teacher-inline-plus">+</span>
              <span>{{ t('teacherContent.editCourse') }}</span>
            </button>
          </div>

          <div v-if="selectedCourse.packageItems.length" class="teacher-module-list">
            <article v-for="item in selectedCourse.packageItems" :key="item.id" class="teacher-module-card">
              <div class="row-between teacher-module-head">
                <div class="row teacher-module-title-row">
                  <span class="teacher-module-index">{{ item.displayOrder }}</span>
                  <strong>{{ item.titleSnapshot }}</strong>
                </div>

                <div class="row teacher-module-right">
                  <span class="muted">{{ item.itemType }}</span>
                  <button
                    class="btn btn-secondary"
                    type="button"
                    :disabled="!selectedCourse.source || previewReadOnly || packageItemRemovingId === item.id"
                    @click="removePackageItem(item.id)"
                  >
                    {{ packageItemRemoveLabel(item.id) }}
                  </button>
                </div>
              </div>
            </article>
          </div>

          <div v-else class="teacher-builder-helper">
            <strong>{{ t('teacherContent.noPackageItems') }}</strong>
            <span class="muted">{{ t('teacherContent.noPackageItemsHint') }}</span>
          </div>

          <form class="panel form-grid" @submit.prevent="submitPackageItem">
            <div class="card-section-head">
              <div class="card-section-title">
                <div class="media-badge small">
                  <IconGlyph name="courses" />
                </div>
                <div class="card-section-copy">
                  <strong>{{ t('teacherContent.addCourseItem') }}</strong>
                  <span class="muted">{{ t('teacherContent.addCourseItemHint') }}</span>
                </div>
              </div>
            </div>

            <div class="form-grid two">
              <div class="field">
                <label>{{ t('teacherContent.itemType') }}</label>
                <select v-model="packageItemForm.itemType" class="select" :aria-label="t('teacherContent.itemType')">
                  <option value="LESSON">{{ t('teacherContent.lesson') }}</option>
                  <option value="MODULE">{{ t('teacherContent.module') }}</option>
                </select>
              </div>

              <div class="field">
                <label>{{ t('teacherContent.displayOrder') }}</label>
                <input
                  v-model.number="packageItemForm.displayOrder"
                  class="input"
                  type="number"
                  min="0"
                  :aria-label="t('teacherContent.displayOrder')"
                />
              </div>
            </div>

            <div class="field">
              <label>{{ t('teacherContent.availableItem') }}</label>
              <select
                v-model.number="packageItemForm.itemId"
                class="select"
                :aria-label="t('teacherContent.availableItem')"
                :disabled="!selectedCourse.source || previewReadOnly"
              >
                <option :value="0">{{ t('teacherContent.selectItem') }}</option>
                <option v-for="item in packageItemOptions" :key="item.id" :value="item.id">{{ item.title }}</option>
              </select>
            </div>

            <button
              class="btn btn-primary"
              type="submit"
              :disabled="!selectedCourse.source || previewReadOnly || submitting.packageItem"
            >
              {{ packageItemSubmitLabel }}
            </button>
          </form>

          <div class="row-between teacher-content-section-head">
            <strong>{{ t('teacherContent.modulesLessons') }}</strong>
            <button
              class="btn btn-secondary teacher-add-module"
              type="button"
              :disabled="previewReadOnly"
              @click="openModuleBuilder()"
            >
              <span class="teacher-inline-plus">+</span>
              <span>{{ t('teacherContent.addModule') }}</span>
            </button>
          </div>

          <div class="teacher-module-list">
            <article
              v-for="module in moduleRows"
              :key="module.id"
              class="teacher-module-card"
              :class="{ 'is-open': module.lessons.length }"
            >
              <div class="row-between teacher-module-head">
                <div class="row teacher-module-title-row">
                  <span class="teacher-module-index">{{ module.id }}</span>
                  <strong>{{ module.title }}</strong>
                </div>

                <div class="row teacher-module-right">
                  <span class="muted">{{ t('teacherContent.lessonCount', { count: module.lessonCount }) }}</span>
                  <button
                    class="student-link-button"
                    type="button"
                    :disabled="!module.source || previewReadOnly"
                    @click="openModuleBuilder(module.source ?? undefined)"
                  >
                    {{ t('teacherContent.edit') }}
                  </button>
                  <button
                    class="btn btn-secondary"
                    type="button"
                    :disabled="!module.source || previewReadOnly || moduleBusyId === module.id"
                    @click="archiveModule(module.source ?? undefined)"
                  >
                    {{ moduleArchiveLabel(module.id) }}
                  </button>
                </div>
              </div>

              <div v-if="module.lessons.length" class="teacher-lesson-list">
                <div v-for="lesson in module.lessons" :key="lesson.id" class="teacher-lesson-row">
                  <div class="row teacher-lesson-main">
                    <span class="teacher-lesson-index">{{ lesson.order }}</span>
                    <strong>{{ lesson.title }}</strong>
                    <span class="teacher-lesson-dot" />
                    <span class="muted">{{ lesson.categoryLabel }}</span>
                    <span class="muted">{{ lesson.metaLabel }}</span>
                  </div>

                  <div class="row teacher-lesson-actions">
                    <StatusPill :label="lesson.statusLabel" :tone="contentStatusTone(lesson.statusLabel)" />
                    <button
                      class="student-link-button"
                      type="button"
                      :disabled="!lesson.source || previewReadOnly"
                      @click="openLessonBuilder(lesson.source ?? undefined, lesson.moduleId)"
                    >
                      {{ t('teacherContent.edit') }}
                    </button>
                    <button
                      class="btn btn-secondary"
                      type="button"
                      :disabled="!lesson.source || previewReadOnly || lessonBusyId === lesson.id"
                      @click="archiveLesson(lesson.source ?? undefined)"
                    >
                      {{ lessonArchiveLabel(lesson.id) }}
                    </button>
                  </div>
                </div>
              </div>
            </article>
          </div>
        </section>

        <section v-else-if="activeSurfaceTab === 'resources'" class="panel teacher-content-card">
          <div class="row-between teacher-content-section-head">
            <strong>{{ t('teacherContent.recordedResources') }}</strong>
            <button
              class="btn btn-secondary teacher-resource-upload"
              type="button"
              :disabled="previewReadOnly"
              @click="openResourceBuilder()"
            >
              <span class="teacher-inline-plus">+</span>
              <span>{{ t('teacherContent.uploadResource') }}</span>
            </button>
          </div>

          <div class="form-grid two">
            <div class="field">
              <label>{{ t('teacherContent.lesson') }}</label>
              <select v-model.number="resourceLessonId" class="select" :aria-label="t('teacherContent.lesson')">
                <option :value="0">{{ t('teacherContent.selectLesson') }}</option>
                <option v-for="lesson in lessonOptions" :key="lesson.id" :value="lesson.id">{{ lesson.title }}</option>
              </select>
            </div>

            <div class="field">
              <label>{{ t('teacherContent.summary') }}</label>
              <input :value="resourceSummaryLabel" class="input" :aria-label="t('teacherContent.summary')" readonly />
            </div>
          </div>

          <div v-if="loadingResourceInsights" class="teacher-builder-helper">
            <strong>{{ t('teacherContent.loadingResources') }}</strong>
            <span class="muted">{{ t('teacherContent.loadingResourcesHint') }}</span>
          </div>

          <div v-else-if="uploadedResourceVideos.length" class="teacher-module-list">
            <article v-for="video in uploadedResourceVideos" :key="video.id" class="teacher-module-card">
              <div class="row-between teacher-module-head">
                <div class="row teacher-module-title-row">
                  <span class="teacher-module-index">{{ video.sortOrder }}</span>
                  <strong>{{ video.title }}</strong>
                </div>

                <div class="row teacher-module-right">
                  <span class="muted">{{ video.sourceType }} / {{ formatDuration(video.durationSeconds) }}</span>
                  <button
                    class="student-link-button"
                    type="button"
                    :disabled="previewReadOnly"
                    @click="openResourceBuilder(video)"
                  >
                    {{ t('teacherContent.edit') }}
                  </button>
                  <button
                    class="btn btn-secondary"
                    type="button"
                    :disabled="previewReadOnly || archivingResourceId === video.id"
                    @click="archiveRecordedResource(video.id)"
                  >
                    {{ resourceArchiveLabel(video.id) }}
                  </button>
                </div>
              </div>

              <div class="teacher-overview-grid">
                <div>
                  <strong>{{ t('teacherContent.resourceDetails') }}</strong>
                  <p class="muted">{{ descriptionLabel(video.description) }}</p>
                </div>

                <div class="teacher-overview-meta">
                  <div>
                    <span class="muted">{{ t('teacherContent.active') }}</span>
                    <strong>{{ yesNoLabel(video.active) }}</strong>
                  </div>
                  <div>
                    <span class="muted">{{ t('teacherContent.updatedLabel') }}</span>
                    <strong>{{ formatDate(video.updatedAt, shortDateOptions) }}</strong>
                  </div>
                  <div>
                    <span class="muted">{{ t('teacherContent.open') }}</span>
                    <strong>{{ video.externalVideoUrl || video.streamUrl || t('teacherContent.storedFile') }}</strong>
                  </div>
                </div>
              </div>
            </article>
          </div>

          <div v-else class="teacher-builder-helper">
            <strong>{{ t('teacherContent.noRecordings') }}</strong>
            <span class="muted">{{ t('teacherContent.noRecordingsHint') }}</span>
          </div>

          <div class="row-between teacher-content-section-head">
            <strong>{{ t('teacherContent.watchProgress') }}</strong>
            <span class="muted">{{ t('teacherContent.trackedViews', { count: lessonVideoProgress.length }) }}</span>
          </div>

          <div v-if="lessonVideoProgress.length" class="teacher-module-list">
            <article
              v-for="progressRow in lessonVideoProgress"
              :key="`${progressRow.studentId}-${progressRow.recordedVideoId}`"
              class="teacher-module-card"
            >
              <div class="row-between teacher-module-head">
                <div class="row teacher-module-title-row">
                  <span class="teacher-module-index">{{ clampPercent(progressRow.progressPercentage) }}%</span>
                  <strong>{{ progressRow.studentName }}</strong>
                </div>

                <div class="row teacher-module-right">
                  <span class="muted">{{ progressRow.recordedVideoTitle }}</span>
                  <StatusPill
                    :label="videoProgressStatusLabel(progressRow.completed)"
                    :tone="videoProgressStatusTone(progressRow.completed)"
                  />
                </div>
              </div>

              <div class="teacher-overview-grid">
                <div>
                  <strong>{{ progressRow.studentEmail }}</strong>
                  <p class="muted">
                    {{
                      t('teacherContent.watched', {
                        watched: formatDuration(progressRow.watchedSeconds),
                        total: formatDuration(progressRow.totalSeconds),
                      })
                    }}
                  </p>
                </div>

                <div class="teacher-overview-meta">
                  <div>
                    <span class="muted">{{ t('teacherContent.lastWatched') }}</span>
                    <strong>{{ lastWatchedLabel(progressRow.lastWatchedAt) }}</strong>
                  </div>
                </div>
              </div>
            </article>
          </div>

          <div v-else class="teacher-builder-helper">
            <strong>{{ t('teacherContent.noWatchHistory') }}</strong>
            <span class="muted">{{ t('teacherContent.noWatchHistoryHint') }}</span>
          </div>
        </section>

        <section v-else-if="activeSurfaceTab === 'activity-types'" class="panel teacher-content-card">
          <div class="row-between teacher-content-section-head">
            <strong>{{ t('teacherContent.quizBuilder') }}</strong>
            <button
              class="btn btn-secondary teacher-builder-launch"
              type="button"
              :disabled="previewReadOnly"
              @click="openQuizBuilder()"
            >
              <span class="teacher-inline-plus">+</span>
              <span>{{ t('teacherContent.createQuiz') }}</span>
            </button>
          </div>

          <div class="form-grid two">
            <div class="field">
              <label>{{ t('teacherContent.lesson') }}</label>
              <select v-model.number="quizLessonId" class="select" :aria-label="t('teacherContent.lesson')">
                <option :value="0">{{ t('teacherContent.selectLesson') }}</option>
                <option v-for="lesson in lessonOptions" :key="lesson.id" :value="lesson.id">{{ lesson.title }}</option>
              </select>
            </div>

            <div class="field">
              <label>{{ t('teacherContent.summary') }}</label>
              <input :value="quizSummaryLabel" class="input" :aria-label="t('teacherContent.summary')" readonly />
            </div>
          </div>

          <div v-if="loadingQuizzes" class="teacher-builder-helper">
            <strong>{{ t('teacherContent.loadingQuizzes') }}</strong>
            <span class="muted">{{ t('teacherContent.loadingQuizzesHint') }}</span>
          </div>

          <div v-else-if="quizList.length" class="teacher-module-list">
            <article v-for="quiz in quizList" :key="quiz.id" class="teacher-module-card">
              <div class="row-between teacher-module-head">
                <div class="row teacher-module-title-row">
                  <span class="teacher-module-index">{{ quiz.questions.length }}</span>
                  <strong>{{ quiz.title }}</strong>
                </div>

                <div class="row teacher-module-right">
                  <StatusPill
                    :label="normalizeStatus(quiz.contentStatus)"
                    :tone="contentStatusTone(normalizeStatus(quiz.contentStatus))"
                  />
                  <button class="student-link-button" type="button" @click="selectQuiz(quiz.id)">
                    {{ t('teacherContent.open') }}
                  </button>
                  <button
                    class="student-link-button"
                    type="button"
                    :disabled="previewReadOnly"
                    @click="openQuizBuilder(quiz)"
                  >
                    {{ t('teacherContent.edit') }}
                  </button>
                  <button
                    class="btn btn-secondary"
                    type="button"
                    :disabled="previewReadOnly || quizDeleteId === quiz.id"
                    @click="deleteQuiz(quiz.id)"
                  >
                    {{ quizDeleteLabel(quiz.id) }}
                  </button>
                </div>
              </div>

              <div class="teacher-overview-grid">
                <div>
                  <strong>{{ quiz.lessonTitle }}</strong>
                  <p class="muted">{{ descriptionLabel(quiz.description) }}</p>
                </div>

                <div class="teacher-overview-meta">
                  <div>
                    <span class="muted">{{ t('teacherContent.questions') }}</span>
                    <strong>{{ quiz.questions.length }}</strong>
                  </div>
                  <div>
                    <span class="muted">{{ t('teacherContent.points') }}</span>
                    <strong>{{ formatNumber(quiz.totalPoints) }}</strong>
                  </div>
                  <div>
                    <span class="muted">{{ t('teacherContent.timeLimit') }}</span>
                    <strong>{{ quizTimeLimitLabel(quiz.timeLimitMinutes) }}</strong>
                  </div>
                </div>
              </div>
            </article>
          </div>

          <div v-else class="teacher-builder-helper">
            <strong>{{ t('teacherContent.noQuizzes') }}</strong>
            <span class="muted">{{ t('teacherContent.noQuizzesHint') }}</span>
          </div>

          <template v-if="selectedQuiz">
            <div class="row-between teacher-content-section-head">
              <strong>{{ t('teacherContent.selectedQuestions') }}</strong>
              <button
                class="btn btn-secondary teacher-add-module"
                type="button"
                :disabled="previewReadOnly"
                @click="openQuestionBuilder()"
              >
                <span class="teacher-inline-plus">+</span>
                <span>{{ t('teacherContent.addQuestion') }}</span>
              </button>
            </div>

            <div v-if="selectedQuiz.questions.length" class="teacher-module-list">
              <article v-for="question in selectedQuiz.questions" :key="question.id" class="teacher-module-card">
                <div class="row-between teacher-module-head">
                  <div class="row teacher-module-title-row">
                    <span class="teacher-module-index">{{ question.displayOrder }}</span>
                    <strong>{{ question.questionText }}</strong>
                  </div>

                  <div class="row teacher-module-right">
                    <span class="muted">{{ question.questionType }}</span>
                    <button
                      class="student-link-button"
                      type="button"
                      :disabled="previewReadOnly"
                      @click="openQuestionBuilder(question)"
                    >
                      {{ t('teacherContent.edit') }}
                    </button>
                    <button
                      class="btn btn-secondary"
                      type="button"
                      :disabled="previewReadOnly || questionDeleteId === question.id"
                      @click="deleteQuestion(question.id)"
                    >
                      {{ questionDeleteLabel(question.id) }}
                    </button>
                  </div>
                </div>

                <div class="teacher-overview-grid">
                  <div>
                    <strong>{{ t('teacherContent.answer') }}</strong>
                    <p class="muted">
                      {{ questionAnswerLabel(question) }}
                    </p>
                    <p v-if="question.explanation" class="muted">{{ question.explanation }}</p>
                  </div>

                  <div class="teacher-overview-meta">
                    <div>
                      <span class="muted">{{ t('teacherContent.points') }}</span>
                      <strong>{{ question.points }}</strong>
                    </div>
                    <div>
                      <span class="muted">{{ t('teacherContent.options') }}</span>
                      <strong>{{ question.options.length }}</strong>
                    </div>
                  </div>
                </div>

                <div v-if="question.options.length" class="teacher-lesson-list">
                  <div v-for="option in question.options" :key="option.id" class="teacher-lesson-row">
                    <div class="row teacher-lesson-main">
                      <span class="teacher-lesson-index">{{ option.displayOrder }}</span>
                      <strong>{{ option.optionText }}</strong>
                    </div>

                    <div class="row teacher-lesson-actions">
                      <StatusPill
                        :label="questionOptionStatusLabel(option.correctOption)"
                        :tone="questionOptionStatusTone(option.correctOption)"
                      />
                    </div>
                  </div>
                </div>
              </article>
            </div>

            <div v-else class="teacher-builder-helper">
              <strong>{{ t('teacherContent.noQuestions') }}</strong>
              <span class="muted">{{ t('teacherContent.noQuestionsHint') }}</span>
            </div>

            <div class="row-between teacher-content-section-head">
              <strong>{{ t('teacherContent.recentAttempts') }}</strong>
              <button class="student-link-button" type="button" @click="loadQuizAttempts(selectedQuiz.id)">
                {{ t('teacherContent.refreshAttempts') }}
              </button>
            </div>

            <div v-if="loadingQuizAttempts" class="teacher-builder-helper">
              <strong>{{ t('teacherContent.loadingAttempts') }}</strong>
              <span class="muted">{{ t('teacherContent.loadingAttemptsHint') }}</span>
            </div>

            <div v-else-if="quizAttempts.length" class="teacher-module-list">
              <article v-for="attempt in quizAttempts" :key="attempt.attemptId" class="teacher-module-card">
                <div class="row-between teacher-module-head">
                  <div class="row teacher-module-title-row">
                    <span class="teacher-module-index">{{ ratioPercent(attempt.score, attempt.maxScore) }}%</span>
                    <strong>{{ attempt.studentName }}</strong>
                  </div>

                  <div class="row teacher-module-right">
                    <span class="muted">{{ attempt.score }} / {{ attempt.maxScore }}</span>
                    <span class="muted">{{ formatDate(attempt.submittedAt, shortDateOptions) }}</span>
                  </div>
                </div>
              </article>
            </div>

            <div v-else class="teacher-builder-helper">
              <strong>{{ t('teacherContent.noAttempts') }}</strong>
              <span class="muted">{{ t('teacherContent.noAttemptsHint') }}</span>
            </div>
          </template>

          <div class="muted-divider" />

          <div class="stack">
            <strong>{{ t('teacherContent.supportedTypes') }}</strong>
            <div class="teacher-activity-grid">
              <div v-for="tool in activityTypes" :key="tool.title" class="teacher-activity-card">
                <span class="teacher-activity-icon"><IconGlyph :name="tool.icon" :size="22" /></span>
                <div class="stack u-gap-2">
                  <strong>{{ tool.title }}</strong>
                  <span class="muted">{{ tool.copy }}</span>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section v-else-if="activeSurfaceTab === 'categories'" class="panel teacher-content-card">
          <div class="row-between teacher-content-section-head">
            <strong>{{ t('teacherContent.learningCategories') }}</strong>
            <span class="muted">{{ t('teacherContent.available', { count: categories.length }) }}</span>
          </div>

          <div v-if="categories.length" class="teacher-module-list">
            <article v-for="category in categories" :key="category.id" class="teacher-module-card">
              <div class="row-between teacher-module-head">
                <div class="row teacher-module-title-row">
                  <span class="teacher-module-index">{{ category.id }}</span>
                  <strong>{{ category.name }}</strong>
                </div>
              </div>
              <p class="muted">{{ descriptionLabel(category.description, t('teacherContent.noDescription')) }}</p>
            </article>
          </div>

          <div v-else class="teacher-builder-helper">
            <strong>{{ t('teacherContent.noCategories') }}</strong>
            <span class="muted">{{ t('teacherContent.noCategoriesHint') }}</span>
          </div>
        </section>

        <section v-else class="panel teacher-content-card">
          <div class="row-between teacher-content-section-head">
            <strong>{{ t('teacherContent.learningLevels') }}</strong>
            <span class="muted">{{ t('teacherContent.available', { count: levels.length }) }}</span>
          </div>

          <div v-if="levels.length" class="teacher-module-list">
            <article v-for="level in levels" :key="level.id" class="teacher-module-card">
              <div class="row-between teacher-module-head">
                <div class="row teacher-module-title-row">
                  <span class="teacher-module-index">{{ level.id }}</span>
                  <strong>{{ level.name }}</strong>
                </div>
              </div>
              <p class="muted">{{ descriptionLabel(level.description, t('teacherContent.noDescription')) }}</p>
            </article>
          </div>

          <div v-else class="teacher-builder-helper">
            <strong>{{ t('teacherContent.noLevels') }}</strong>
            <span class="muted">{{ t('teacherContent.noLevelsHint') }}</span>
          </div>
        </section>
      </div>

      <aside class="panel teacher-builder-sidebar">
        <div class="row teacher-builder-header">
          <div class="media-badge small">CM</div>
          <div class="stack u-gap-2">
            <strong>{{ builderHeading }}</strong>
            <span class="muted">{{ builderDescription }}</span>
          </div>
        </div>

        <div v-if="previewReadOnly" class="teacher-builder-helper">
          <strong>{{ readOnlyTitle }}</strong>
          <span class="muted">
            {{ readOnlyMessage }}
          </span>
        </div>

        <button
          class="btn btn-secondary btn-block teacher-builder-launch"
          type="button"
          :disabled="previewReadOnly"
          @click="openBuilderForActiveSurface"
        >
          <span class="teacher-inline-plus">+</span>
          <span>{{ builderLaunchLabel }}</span>
        </button>

        <div v-if="activeBuilder" class="teacher-builder-tabs">
          <button
            class="tab-button"
            :class="{ 'is-active': activeBuilder === 'package' }"
            type="button"
            @click="openPackageBuilder(editingPackageSource ?? selectedCourse.source ?? undefined)"
          >
            {{ t('teacherContent.course') }}
          </button>
          <button
            class="tab-button"
            :class="{ 'is-active': activeBuilder === 'module' }"
            type="button"
            @click="openModuleBuilder(editingModuleSource)"
          >
            {{ t('teacherContent.module') }}
          </button>
          <button
            class="tab-button"
            :class="{ 'is-active': activeBuilder === 'lesson' }"
            type="button"
            @click="openLessonBuilder(editingLessonSource)"
          >
            {{ t('teacherContent.lesson') }}
          </button>
          <button
            class="tab-button"
            :class="{ 'is-active': activeBuilder === 'resource' }"
            type="button"
            @click="openResourceBuilder(editingResourceSource)"
          >
            {{ t('teacherContent.resource') }}
          </button>
          <button
            class="tab-button"
            :class="{ 'is-active': activeBuilder === 'quiz' }"
            type="button"
            @click="openQuizBuilder(editingQuizSource)"
          >
            {{ t('teacherContent.quiz') }}
          </button>
        </div>

        <ActionFormCard
          v-if="activeBuilder === 'package'"
          :title="editingPackageId ? t('teacherContent.editCourseTitle') : t('teacherContent.createCourseTitle')"
          :subtitle="t('teacherContent.courseFormHint')"
          icon="courses"
          :submit-label="
            editingPackageId ? t('teacherContent.saveCourseChanges') : t('teacherContent.createCourseAction')
          "
          :submitting-label="t('teacherContent.saving')"
          :submitting="submitting.package"
          @submit="submitPackage"
        >
          <template #header-end>
            <button class="teacher-outline-button" type="button" @click="resetPackageForm">
              {{ t('teacherContent.clear') }}
            </button>
          </template>

          <div class="field">
            <label>{{ t('teacherContent.courseTitle') }}</label>
            <input
              v-model="packageForm.title"
              class="input"
              maxlength="200"
              :aria-label="t('teacherContent.courseTitle')"
            />
          </div>

          <div class="field">
            <label>{{ t('teacherContent.descriptionLabel') }}</label>
            <textarea
              v-model="packageForm.description"
              class="textarea"
              maxlength="2000"
              :aria-label="t('teacherContent.descriptionLabel')"
            />
          </div>

          <div class="form-grid two">
            <div class="field">
              <label>{{ t('teacherContent.level') }}</label>
              <select v-model.number="packageForm.levelId" class="select" :aria-label="t('teacherContent.level')">
                <option :value="0">{{ t('teacherContent.selectLevel') }}</option>
                <option v-for="level in levels" :key="level.id" :value="level.id">{{ level.name }}</option>
              </select>
            </div>

            <div class="field">
              <label>{{ t('teacherContent.category') }}</label>
              <select v-model.number="packageForm.categoryId" class="select" :aria-label="t('teacherContent.category')">
                <option :value="0">{{ t('teacherContent.selectCategory') }}</option>
                <option v-for="category in categories" :key="category.id" :value="category.id">
                  {{ category.name }}
                </option>
              </select>
            </div>
          </div>

          <div class="form-grid two">
            <div class="field">
              <label>{{ t('teacherContent.learningType') }}</label>
              <select v-model="packageForm.learningType" class="select" :aria-label="t('teacherContent.learningType')">
                <option value="LIVE">{{ t('teacherContent.live') }}</option>
                <option value="RECORDED">{{ t('teacherContent.recorded') }}</option>
                <option value="HYBRID">{{ t('teacherContent.hybrid') }}</option>
              </select>
            </div>

            <div class="field">
              <label>{{ t('teacherContent.access') }}</label>
              <select v-model="packageForm.accessType" class="select" :aria-label="t('teacherContent.access')">
                <option value="FREE">{{ t('teacherContent.free') }}</option>
                <option value="PAID">{{ t('teacherContent.paid') }}</option>
              </select>
            </div>
          </div>

          <div class="form-grid two">
            <div class="field">
              <label>{{ t('teacherContent.price') }}</label>
              <input
                v-model.number="packageForm.price"
                class="input"
                type="number"
                min="0"
                step="0.01"
                :aria-label="t('teacherContent.price')"
              />
            </div>

            <div class="field">
              <label>{{ t('teacherContent.coverUrl') }}</label>
              <input
                v-model="packageForm.coverImageUrl"
                class="input"
                type="url"
                maxlength="255"
                :aria-label="t('teacherContent.coverUrl')"
              />
            </div>
          </div>
        </ActionFormCard>

        <ActionFormCard
          v-else-if="activeBuilder === 'module'"
          :title="editingModuleId ? t('teacherContent.editModuleTitle') : t('teacherContent.addModule')"
          :subtitle="t('teacherContent.moduleFormHint')"
          icon="content"
          :submit-label="editingModuleId ? t('teacherContent.saveModuleChanges') : t('teacherContent.createModule')"
          :submitting-label="t('teacherContent.saving')"
          :submitting="submitting.module"
          @submit="submitModule"
        >
          <template #header-end>
            <button class="teacher-outline-button" type="button" @click="resetModuleForm">
              {{ t('teacherContent.clear') }}
            </button>
          </template>

          <div class="field">
            <label>{{ t('teacherContent.moduleTitle') }}</label>
            <input
              v-model="moduleForm.title"
              class="input"
              maxlength="200"
              :aria-label="t('teacherContent.moduleTitle')"
            />
          </div>

          <div class="field">
            <label>{{ t('teacherContent.descriptionLabel') }}</label>
            <textarea
              v-model="moduleForm.description"
              class="textarea"
              maxlength="2000"
              :aria-label="t('teacherContent.descriptionLabel')"
            />
          </div>

          <div class="form-grid two">
            <div class="field">
              <label>{{ t('teacherContent.level') }}</label>
              <select v-model.number="moduleForm.levelId" class="select" :aria-label="t('teacherContent.level')">
                <option :value="0">{{ t('teacherContent.selectLevel') }}</option>
                <option v-for="level in levels" :key="level.id" :value="level.id">{{ level.name }}</option>
              </select>
            </div>

            <div class="field">
              <label>{{ t('teacherContent.category') }}</label>
              <select v-model.number="moduleForm.categoryId" class="select" :aria-label="t('teacherContent.category')">
                <option :value="0">{{ t('teacherContent.selectCategory') }}</option>
                <option v-for="category in categories" :key="category.id" :value="category.id">
                  {{ category.name }}
                </option>
              </select>
            </div>
          </div>

          <div class="form-grid two">
            <div class="field">
              <label>{{ t('teacherContent.access') }}</label>
              <select v-model="moduleForm.accessType" class="select" :aria-label="t('teacherContent.access')">
                <option value="FREE">{{ t('teacherContent.free') }}</option>
                <option value="PAID">{{ t('teacherContent.paid') }}</option>
              </select>
            </div>

            <div class="field">
              <label>{{ t('teacherContent.price') }}</label>
              <input
                v-model.number="moduleForm.price"
                class="input"
                type="number"
                min="0"
                step="0.01"
                :aria-label="t('teacherContent.price')"
              />
            </div>
          </div>

          <div class="field">
            <label>{{ t('teacherContent.coverUrl') }}</label>
            <input
              v-model="moduleForm.coverImageUrl"
              class="input"
              type="url"
              maxlength="255"
              :aria-label="t('teacherContent.coverUrl')"
            />
          </div>
        </ActionFormCard>

        <ActionFormCard
          v-else-if="activeBuilder === 'lesson'"
          :title="editingLessonId ? t('teacherContent.editLessonTitle') : t('teacherContent.createLessonTitle')"
          :subtitle="t('teacherContent.lessonFormHint')"
          icon="learn"
          :submit-label="editingLessonId ? t('teacherContent.saveLessonChanges') : t('teacherContent.createLesson')"
          :submitting-label="t('teacherContent.saving')"
          :submitting="submitting.lesson"
          @submit="submitLesson"
        >
          <template #header-end>
            <button class="teacher-outline-button" type="button" @click="resetLessonForm()">
              {{ t('teacherContent.clear') }}
            </button>
          </template>

          <div class="field">
            <label>{{ t('teacherContent.lessonTitle') }}</label>
            <input
              v-model="lessonForm.title"
              class="input"
              maxlength="200"
              :aria-label="t('teacherContent.lessonTitle')"
            />
          </div>

          <div class="field">
            <label>{{ t('teacherContent.descriptionLabel') }}</label>
            <textarea
              v-model="lessonForm.description"
              class="textarea"
              maxlength="1000"
              :aria-label="t('teacherContent.descriptionLabel')"
            />
          </div>

          <div class="form-grid two">
            <div class="field">
              <label>{{ t('teacherContent.module') }}</label>
              <select v-model.number="lessonForm.moduleId" class="select" :aria-label="t('teacherContent.module')">
                <option :value="0">{{ t('teacherContent.selectModule') }}</option>
                <option v-for="module in liveModuleOptions" :key="module.id" :value="module.id">
                  {{ module.title }}
                </option>
              </select>
            </div>

            <div class="field">
              <label>{{ t('teacherContent.learningType') }}</label>
              <select v-model="lessonForm.learningType" class="select" :aria-label="t('teacherContent.learningType')">
                <option value="LIVE">{{ t('teacherContent.live') }}</option>
                <option value="RECORDED">{{ t('teacherContent.recorded') }}</option>
                <option value="HYBRID">{{ t('teacherContent.hybrid') }}</option>
              </select>
            </div>
          </div>

          <div class="form-grid two">
            <div class="field">
              <label>{{ t('teacherContent.level') }}</label>
              <select v-model.number="lessonForm.levelId" class="select" :aria-label="t('teacherContent.level')">
                <option :value="0">{{ t('teacherContent.selectLevel') }}</option>
                <option v-for="level in levels" :key="level.id" :value="level.id">{{ level.name }}</option>
              </select>
            </div>

            <div class="field">
              <label>{{ t('teacherContent.category') }}</label>
              <select v-model.number="lessonForm.categoryId" class="select" :aria-label="t('teacherContent.category')">
                <option :value="0">{{ t('teacherContent.selectCategory') }}</option>
                <option v-for="category in categories" :key="category.id" :value="category.id">
                  {{ category.name }}
                </option>
              </select>
            </div>
          </div>

          <div class="form-grid two">
            <div class="field">
              <label>{{ t('teacherContent.access') }}</label>
              <select v-model="lessonForm.accessType" class="select" :aria-label="t('teacherContent.access')">
                <option value="FREE">{{ t('teacherContent.free') }}</option>
                <option value="PAID">{{ t('teacherContent.paid') }}</option>
              </select>
            </div>

            <div class="field">
              <label>{{ t('teacherContent.price') }}</label>
              <input
                v-model.number="lessonForm.price"
                class="input"
                type="number"
                min="0"
                step="0.01"
                :aria-label="t('teacherContent.price')"
              />
            </div>
          </div>

          <div class="form-grid two">
            <div class="field">
              <label>{{ t('teacherContent.estimatedMinutes') }}</label>
              <input
                v-model.number="lessonForm.estimatedDurationMinutes"
                class="input"
                type="number"
                min="0"
                :aria-label="t('teacherContent.estimatedMinutes')"
              />
            </div>

            <div class="field">
              <label>{{ t('teacherContent.coverUrl') }}</label>
              <input
                v-model="lessonForm.coverImageUrl"
                class="input"
                type="url"
                maxlength="255"
                :aria-label="t('teacherContent.coverUrl')"
              />
            </div>
          </div>

          <div class="field">
            <label>{{ t('teacherContent.basicContent') }}</label>
            <textarea
              v-model="lessonForm.basicContent"
              class="textarea"
              :aria-label="t('teacherContent.basicContent')"
            />
          </div>
        </ActionFormCard>

        <ActionFormCard
          v-else-if="activeBuilder === 'resource'"
          :title="editingResourceId ? t('teacherContent.editRecordingTitle') : t('teacherContent.uploadRecordingTitle')"
          :subtitle="t('teacherContent.recordingFormHint')"
          icon="file"
          :submit-label="
            editingResourceId ? t('teacherContent.saveRecordingChanges') : t('teacherContent.saveRecording')
          "
          :submitting-label="t('teacherContent.saving')"
          :submitting="submitting.resource"
          @submit="submitRecordedResource"
        >
          <template #header-end>
            <button class="teacher-outline-button" type="button" @click="resetResourceForm(resourceLessonId)">
              {{ t('teacherContent.clear') }}
            </button>
          </template>

          <div class="field">
            <label>{{ t('teacherContent.lesson') }}</label>
            <select v-model.number="resourceForm.lessonId" class="select" :aria-label="t('teacherContent.lesson')">
              <option :value="0">{{ t('teacherContent.selectLesson') }}</option>
              <option v-for="lesson in lessonOptions" :key="lesson.id" :value="lesson.id">{{ lesson.title }}</option>
            </select>
          </div>

          <div class="field">
            <label>{{ t('teacherContent.packageOptional') }}</label>
            <select
              v-model.number="resourceForm.packageId"
              class="select"
              :aria-label="t('teacherContent.packageOptional')"
            >
              <option :value="0">{{ t('teacherContent.noPackageLink') }}</option>
              <option v-for="item in livePackageOptions" :key="item.id" :value="item.id">{{ item.title }}</option>
            </select>
          </div>

          <div class="field">
            <label>{{ t('teacherContent.titleLabel') }}</label>
            <input
              v-model="resourceForm.title"
              class="input"
              maxlength="200"
              :aria-label="t('teacherContent.titleLabel')"
            />
          </div>

          <div class="field">
            <label>{{ t('teacherContent.descriptionLabel') }}</label>
            <textarea
              v-model="resourceForm.description"
              class="textarea"
              maxlength="1000"
              :aria-label="t('teacherContent.descriptionLabel')"
            />
          </div>

          <div class="form-grid two">
            <div class="field">
              <label>{{ t('teacherContent.sourceType') }}</label>
              <select v-model="resourceForm.sourceType" class="select" :aria-label="t('teacherContent.sourceType')">
                <option value="EXTERNAL_URL">{{ t('teacherContent.externalSource') }}</option>
                <option value="UPLOADED_FILE">{{ t('teacherContent.uploadSource') }}</option>
              </select>
            </div>

            <div class="field">
              <label>{{ t('teacherContent.durationSeconds') }}</label>
              <input
                v-model.number="resourceForm.durationSeconds"
                class="input"
                type="number"
                min="0"
                :aria-label="t('teacherContent.durationSeconds')"
              />
            </div>
          </div>

          <div v-if="resourceForm.sourceType === 'EXTERNAL_URL'" class="field">
            <label>{{ t('teacherContent.externalVideoUrl') }}</label>
            <input
              v-model="resourceForm.externalVideoUrl"
              class="input"
              type="url"
              maxlength="2048"
              :aria-label="t('teacherContent.externalVideoUrl')"
            />
          </div>

          <div v-else class="field">
            <label>{{ t('teacherContent.videoFile') }}</label>
            <input
              ref="resourceFileInput"
              class="input"
              type="file"
              accept="video/*"
              :aria-label="t('teacherContent.videoFile')"
            />
          </div>
        </ActionFormCard>

        <template v-else-if="activeBuilder === 'quiz'">
          <ActionFormCard
            :title="editingQuizId ? t('teacherContent.editQuizTitle') : t('teacherContent.createQuiz')"
            :subtitle="t('teacherContent.quizFormHint')"
            icon="practice"
            :submit-label="editingQuizId ? t('teacherContent.saveQuizChanges') : t('teacherContent.createQuizAction')"
            :submitting-label="t('teacherContent.saving')"
            :submitting="submitting.quiz"
            @submit="submitQuiz"
          >
            <template #header-end>
              <button class="teacher-outline-button" type="button" @click="resetQuizForm(quizLessonId)">
                {{ t('teacherContent.clear') }}
              </button>
            </template>

            <div class="field">
              <label>{{ t('teacherContent.lesson') }}</label>
              <select v-model.number="quizForm.lessonId" class="select" :aria-label="t('teacherContent.lesson')">
                <option :value="0">{{ t('teacherContent.selectLesson') }}</option>
                <option v-for="lesson in lessonOptions" :key="lesson.id" :value="lesson.id">{{ lesson.title }}</option>
              </select>
            </div>

            <div class="field">
              <label>{{ t('teacherContent.quizTitle') }}</label>
              <input
                v-model="quizForm.title"
                class="input"
                maxlength="200"
                :aria-label="t('teacherContent.quizTitle')"
              />
            </div>

            <div class="field">
              <label>{{ t('teacherContent.descriptionLabel') }}</label>
              <textarea
                v-model="quizForm.description"
                class="textarea"
                maxlength="1000"
                :aria-label="t('teacherContent.descriptionLabel')"
              />
            </div>

            <div class="field">
              <label>{{ t('teacherContent.timeLimitMinutes') }}</label>
              <input
                v-model.number="quizForm.timeLimitMinutes"
                class="input"
                type="number"
                min="0"
                :aria-label="t('teacherContent.timeLimitMinutes')"
              />
            </div>
          </ActionFormCard>

          <ActionFormCard
            :title="editingQuestionId ? t('teacherContent.editQuestionTitle') : t('teacherContent.addQuestion')"
            :subtitle="
              selectedQuiz
                ? t('teacherContent.activeQuiz', { title: selectedQuiz.title })
                : t('teacherContent.questionPrerequisite')
            "
            icon="practice"
            :submit-label="
              editingQuestionId ? t('teacherContent.saveQuestionChanges') : t('teacherContent.addQuestionAction')
            "
            :submitting-label="t('teacherContent.saving')"
            :submitting="submitting.question"
            @submit="submitQuestion"
          >
            <template #header-end>
              <button class="teacher-outline-button" type="button" @click="resetQuestionForm">
                {{ t('teacherContent.clear') }}
              </button>
            </template>

            <div class="field">
              <label>{{ t('teacherContent.questionType') }}</label>
              <select v-model="questionForm.questionType" class="select" :aria-label="t('teacherContent.questionType')">
                <option v-for="type in questionTypeOptions" :key="type" :value="type">{{ type }}</option>
              </select>
            </div>

            <div class="field">
              <label>{{ t('teacherContent.questionText') }}</label>
              <textarea
                v-model="questionForm.questionText"
                class="textarea"
                maxlength="1000"
                :aria-label="t('teacherContent.questionText')"
              />
            </div>

            <div class="form-grid two">
              <div class="field">
                <label>{{ t('teacherContent.correctAnswer') }}</label>
                <input
                  v-model="questionForm.correctAnswerText"
                  class="input"
                  maxlength="500"
                  :aria-label="t('teacherContent.correctAnswer')"
                />
              </div>

              <div class="field">
                <label>{{ t('teacherContent.points') }}</label>
                <input
                  v-model.number="questionForm.points"
                  class="input"
                  type="number"
                  min="1"
                  :aria-label="t('teacherContent.points')"
                />
              </div>
            </div>

            <div class="form-grid two">
              <div class="field">
                <label>{{ t('teacherContent.displayOrder') }}</label>
                <input
                  v-model.number="questionForm.displayOrder"
                  class="input"
                  type="number"
                  min="0"
                  :aria-label="t('teacherContent.displayOrder')"
                />
              </div>

              <div class="field">
                <label>{{ t('teacherContent.explanation') }}</label>
                <input
                  v-model="questionForm.explanation"
                  class="input"
                  maxlength="1000"
                  :aria-label="t('teacherContent.explanation')"
                />
              </div>
            </div>

            <div class="stack">
              <div class="row-between teacher-content-section-head">
                <strong>{{ t('teacherContent.answerOptions') }}</strong>
                <button class="student-link-button" type="button" @click="addQuestionOption">
                  {{ t('teacherContent.addOption') }}
                </button>
              </div>

              <div v-for="(option, index) in questionForm.options" :key="`option-${index}`" class="teacher-lesson-row">
                <div class="row teacher-lesson-main">
                  <span class="teacher-lesson-index">{{ index + 1 }}</span>
                  <input
                    v-model="option.optionText"
                    class="input"
                    :placeholder="t('teacherContent.optionText')"
                    maxlength="255"
                    :aria-label="t('teacherContent.optionTextAria', { number: index + 1 })"
                  />
                </div>

                <div class="row teacher-lesson-actions">
                  <label class="muted">
                    <input v-model="option.correctOption" type="checkbox" />
                    {{ t('teacherContent.correct') }}
                  </label>
                  <input
                    v-model.number="option.displayOrder"
                    class="input"
                    type="number"
                    min="0"
                    :aria-label="t('teacherContent.optionOrderAria', { number: index + 1 })"
                  />
                  <button
                    class="btn btn-secondary"
                    type="button"
                    :disabled="questionForm.options.length <= 1"
                    @click="removeQuestionOption(index)"
                  >
                    {{ t('teacherContent.remove') }}
                  </button>
                </div>
              </div>
            </div>
          </ActionFormCard>
        </template>

        <div class="muted-divider" />

        <div class="stack">
          <strong>{{ t('teacherContent.quickReference') }}</strong>
          <div class="teacher-resource-grid">
            <div v-for="item in resourceTypes" :key="item.label" class="teacher-resource-chip">
              <span><IconGlyph :name="item.icon" :size="18" /></span>
              <strong>{{ item.label }}</strong>
            </div>
          </div>
        </div>
      </aside>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import { publicApi, teacherApi } from '@/api/services'
import type {
  TeacherLessonPayload,
  TeacherModulePayload,
  TeacherPackagePayload,
  TeacherQuizPayload,
  TeacherQuizQuestionPayload,
  TeacherQuizOptionPayload,
} from '@/api/services'
import CourseSceneThumb from '@/components/common/CourseSceneThumb.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import ActionFormCard from '@/components/dashboard/ActionFormCard.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useResource } from '@/composables/useResource'
import { useTeacherAccess } from '@/composables/useTeacherAccess'
import { categories as demoCategories, levels as demoLevels, teacherCoursePackages, teacherLessons } from '@/demo/data'
import { teacherContentMessages } from '@/plugins/teacherContentMessages'
import { useAuthStore } from '@/stores/auth'
import type {
  ContentStatus,
  CoursePackageResponse,
  LessonResponse,
  LessonVideoProgressSummaryResponse,
  LearningModuleResponse,
  PackageItemResponse,
  PackageItemType,
  QuizQuestionType,
  RecordedLessonVideoResponse,
  TeacherQuizAttemptSummaryResponse,
  TeacherQuizQuestionResponse,
  TeacherQuizResponse,
} from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import {
  clampPercent,
  formatCurrency,
  formatDate,
  formatNumber,
  nonNegativeInteger,
  ratioPercent,
} from '@/utils/format'

type SurfaceTab = 'courses' | 'modules' | 'lessons' | 'categories' | 'levels' | 'resources' | 'activity-types'
type BuilderTab = 'package' | 'module' | 'lesson' | 'resource' | 'quiz'

type DisplayCourse = {
  id: number
  title: string
  subtitle: string
  description: string
  statusLabel: string
  lessonCount: number
  levelLabel: string
  accessLabel: string
  updatedLabel: string
  packageItems: PackageItemResponse[]
  source: CoursePackageResponse | null
}

type DisplayLessonRow = {
  id: number
  title: string
  order: number
  categoryLabel: string
  statusLabel: string
  metaLabel: string
  moduleId: number
  source: LessonResponse | null
}

type DisplayModuleRow = {
  id: number
  title: string
  lessonCount: number
  lessons: DisplayLessonRow[]
  source: LearningModuleResponse | null
}

type QuestionOptionDraft = {
  optionText: string
  correctOption: boolean
  displayOrder: number
}

const auth = useAuthStore()
const { t } = useI18n({
  messages: {
    en: { teacherContent: teacherContentMessages.en },
    ms: { teacherContent: teacherContentMessages.ms },
    zh: { teacherContent: teacherContentMessages.zh },
  },
})
const { teacherApprovalLocked, teacherApprovalMessage } = useTeacherAccess()

const packagesResource = useResource(
  () => (teacherApprovalLocked.value ? Promise.resolve([]) : teacherApi.packages()),
  teacherCoursePackages,
)
const lessonsResource = useResource(
  () => (teacherApprovalLocked.value ? Promise.resolve([]) : teacherApi.lessons()),
  teacherLessons,
)
const modulesResource = useResource(
  () => (teacherApprovalLocked.value ? Promise.resolve([]) : teacherApi.modules()),
  [],
)
const levelsResource = useResource(() => publicApi.getLevels(), demoLevels)
const categoriesResource = useResource(() => publicApi.getCategories(), demoCategories)

const previewReadOnly = computed(
  () =>
    teacherApprovalLocked.value ||
    (auth.isPreviewSession && !auth.isAuthenticated) ||
    packagesResource.usingFallback.value ||
    lessonsResource.usingFallback.value ||
    modulesResource.usingFallback.value ||
    levelsResource.usingFallback.value ||
    categoriesResource.usingFallback.value,
)
const levels = computed(() => levelsResource.data.value ?? (levelsResource.usingFallback.value ? demoLevels : []))
const categories = computed(
  () => categoriesResource.data.value ?? (categoriesResource.usingFallback.value ? demoCategories : []),
)
const livePackages = computed(() => (packagesResource.usingFallback.value ? [] : (packagesResource.data.value ?? [])))
const liveLessons = computed(() => (lessonsResource.usingFallback.value ? [] : (lessonsResource.data.value ?? [])))
const liveModules = computed(() => (modulesResource.usingFallback.value ? [] : (modulesResource.data.value ?? [])))

const contentTabs = computed<Array<{ key: SurfaceTab; label: string; icon: string }>>(() => [
  { key: 'courses', label: t('teacherContent.courses'), icon: 'courses' },
  { key: 'modules', label: t('teacherContent.modules'), icon: 'content' },
  { key: 'lessons', label: t('teacherContent.lessons'), icon: 'learn' },
  { key: 'categories', label: t('teacherContent.categories'), icon: 'rewards' },
  { key: 'levels', label: t('teacherContent.levels'), icon: 'star' },
  { key: 'resources', label: t('teacherContent.resources'), icon: 'file' },
  { key: 'activity-types', label: t('teacherContent.assessments'), icon: 'practice' },
])

const questionTypeOptions: QuizQuestionType[] = [
  'MULTIPLE_CHOICE',
  'TRUE_FALSE',
  'FILL_BLANK',
  'MATCH_WORD_MEANING',
  'MATCH_WORD_IMAGE',
  'LISTEN_CHOOSE',
  'REARRANGE_SENTENCE',
  'CHOOSE_PINYIN',
  'CHOOSE_TONE',
  'CHARACTER_MEANING',
]

const activityTypes = computed(() => [
  {
    title: t('teacherContent.activityFlashcards'),
    copy: t('teacherContent.activityFlashcardsHint'),
    icon: 'clip',
  },
  { title: t('teacherContent.activityQuizzes'), copy: t('teacherContent.activityQuizzesHint'), icon: 'quiz' },
  { title: t('teacherContent.activityMatching'), copy: t('teacherContent.activityMatchingHint'), icon: 'fun' },
  { title: t('teacherContent.activityListening'), copy: t('teacherContent.activityListeningHint'), icon: 'live' },
  {
    title: t('teacherContent.activityTracing'),
    copy: t('teacherContent.activityTracingHint'),
    icon: 'practice',
  },
  {
    title: t('teacherContent.activityDrag'),
    copy: t('teacherContent.activityDragHint'),
    icon: 'practice',
  },
])

const resourceTypes = computed(() => [
  { label: t('teacherContent.images'), icon: 'content' },
  { label: t('teacherContent.audio'), icon: 'live' },
  { label: t('teacherContent.videos'), icon: 'play' },
  { label: t('teacherContent.documents'), icon: 'file' },
])

const previewModuleRows = computed<DisplayModuleRow[]>(() => [
  { id: 1, title: t('teacherContent.demoModuleOne'), lessonCount: 0, lessons: [], source: null },
  { id: 2, title: t('teacherContent.demoModuleTwo'), lessonCount: 0, lessons: [], source: null },
  { id: 3, title: t('teacherContent.demoModuleThree'), lessonCount: 0, lessons: [], source: null },
])

const shortDateOptions = { month: 'short', day: 'numeric', year: 'numeric' } as const

const activeSurfaceTab = ref<SurfaceTab>('courses')
const activeBuilder = ref<BuilderTab | null>(null)
const selectedPackageId = ref(0)
const resourceLessonId = ref(0)
const quizLessonId = ref(0)

const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)

const packageBusy = ref(false)
const moduleBusyId = ref<number | null>(null)
const lessonBusyId = ref<number | null>(null)
const packageItemRemovingId = ref<number | null>(null)
const archivingResourceId = ref<number | null>(null)
const quizDeleteId = ref<number | null>(null)
const questionDeleteId = ref<number | null>(null)

const loadingResourceInsights = ref(false)
const uploadedResourceVideos = ref<RecordedLessonVideoResponse[]>([])
const lessonVideoProgress = ref<LessonVideoProgressSummaryResponse[]>([])

const loadingQuizzes = ref(false)
const loadingQuizAttempts = ref(false)
const quizList = ref<TeacherQuizResponse[]>([])
const quizAttempts = ref<TeacherQuizAttemptSummaryResponse[]>([])
const selectedQuizId = ref<number | null>(null)

const editingPackageId = ref<number | null>(null)
const editingModuleId = ref<number | null>(null)
const editingLessonId = ref<number | null>(null)
const editingResourceId = ref<number | null>(null)
const editingQuizId = ref<number | null>(null)
const editingQuestionId = ref<number | null>(null)

const resourceFileInput = ref<HTMLInputElement | null>(null)

const submitting = reactive({
  package: false,
  module: false,
  lesson: false,
  resource: false,
  quiz: false,
  question: false,
  packageItem: false,
})

const packageForm = reactive<TeacherPackagePayload>({
  title: '',
  description: '',
  levelId: 0,
  categoryId: 0,
  learningType: 'HYBRID',
  accessType: 'FREE',
  price: 0,
  coverImageUrl: '',
})

const moduleForm = reactive<TeacherModulePayload>({
  title: '',
  description: '',
  levelId: 0,
  categoryId: 0,
  accessType: 'FREE',
  price: 0,
  coverImageUrl: '',
})

const lessonForm = reactive<TeacherLessonPayload>({
  title: '',
  description: '',
  moduleId: 0,
  levelId: 0,
  categoryId: 0,
  learningType: 'HYBRID',
  accessType: 'FREE',
  price: 0,
  estimatedDurationMinutes: 0,
  basicContent: '',
  coverImageUrl: '',
})

const packageItemForm = reactive({
  itemType: 'LESSON' as PackageItemType,
  itemId: 0,
  displayOrder: 0,
})

const resourceForm = reactive({
  lessonId: 0,
  packageId: 0,
  title: '',
  description: '',
  sourceType: 'EXTERNAL_URL',
  externalVideoUrl: '',
  durationSeconds: 0,
})

const quizForm = reactive<TeacherQuizPayload & { lessonId: number }>({
  lessonId: 0,
  title: '',
  description: '',
  timeLimitMinutes: 0,
})

const questionForm = reactive<
  TeacherQuizQuestionPayload & {
    options: QuestionOptionDraft[]
  }
>({
  questionType: 'MULTIPLE_CHOICE',
  questionText: '',
  correctAnswerText: '',
  points: 1,
  displayOrder: 0,
  options: defaultQuestionOptions(),
  explanation: '',
})

const usingFallback = computed(
  () =>
    packagesResource.usingFallback.value ||
    lessonsResource.usingFallback.value ||
    modulesResource.usingFallback.value ||
    levelsResource.usingFallback.value ||
    categoriesResource.usingFallback.value,
)
const teacherContentDetail = computed(() => (teacherApprovalLocked.value ? teacherApprovalMessage.value : null))
const packageArchiveLabel = computed(() =>
  packageBusy.value ? t('teacherContent.working') : t('teacherContent.archive'),
)
const packageItemSubmitLabel = computed(() =>
  submitting.packageItem ? t('teacherContent.saving') : t('teacherContent.addToCourse'),
)
const readOnlyTitle = computed(() =>
  teacherApprovalLocked.value ? t('teacherContent.verificationPending') : t('teacherContent.previewReadOnly'),
)
const readOnlyMessage = computed(() =>
  teacherApprovalLocked.value ? teacherApprovalMessage.value : t('teacherContent.readOnlyHint'),
)

const pageError = computed(
  () =>
    packagesResource.error.value ||
    lessonsResource.error.value ||
    modulesResource.error.value ||
    levelsResource.error.value ||
    categoriesResource.error.value,
)
const noticeError = computed(() => actionError.value || pageError.value)

const showCourseWorkspace = computed(
  () =>
    activeSurfaceTab.value === 'courses' ||
    activeSurfaceTab.value === 'modules' ||
    activeSurfaceTab.value === 'lessons',
)

const displayCourses = computed<DisplayCourse[]>(() => {
  const items = packagesResource.data.value ?? []
  return items.map((item) => ({
    id: item.id,
    title: item.title,
    subtitle: item.category.name,
    description: item.description || t('teacherContent.noDescriptionYet'),
    statusLabel: normalizeStatus(item.contentStatus),
    lessonCount: item.packageItems.length,
    levelLabel: item.level.name,
    accessLabel: item.accessType === 'PAID' ? formatPrice(item.price) : t('teacherContent.free'),
    updatedLabel: formatDate(item.updatedAt, shortDateOptions),
    packageItems: item.packageItems,
    source: packagesResource.usingFallback.value ? null : item,
  }))
})

const selectedCourse = computed<DisplayCourse>(() => {
  return (
    displayCourses.value.find((item) => item.id === selectedPackageId.value) ??
    displayCourses.value[0] ?? {
      id: 0,
      title: t('teacherContent.noCourse'),
      subtitle: t('teacherContent.selectOrCreateCourse'),
      description: t('teacherContent.noCourseData'),
      statusLabel: t('teacherContent.statusDraft'),
      lessonCount: 0,
      levelLabel: '--',
      accessLabel: '--',
      updatedLabel: '--',
      packageItems: [],
      source: null,
    }
  )
})

const lessonRows = computed<DisplayLessonRow[]>(() => {
  const items = lessonsResource.data.value ?? []

  return items.map((lesson, index) => ({
    id: lesson.id,
    title: lesson.title,
    order: index + 1,
    categoryLabel: lesson.category.name,
    statusLabel: normalizeStatus(lesson.contentStatus),
    metaLabel: lesson.estimatedDurationMinutes
      ? t('teacherContent.minutes', { count: lesson.estimatedDurationMinutes })
      : learningTypeLabel(lesson.learningType),
    moduleId: lesson.moduleId ?? 0,
    source: lessonsResource.usingFallback.value ? null : lesson,
  }))
})

const moduleRows = computed<DisplayModuleRow[]>(() => {
  const items = modulesResource.data.value ?? []

  if (items.length) {
    return items.map((module) => {
      const relatedLessons = lessonRows.value.filter((lesson) => lesson.moduleId === module.id)
      return {
        id: module.id,
        title: module.title,
        lessonCount: relatedLessons.length,
        lessons: relatedLessons,
        source: modulesResource.usingFallback.value ? null : module,
      }
    })
  }

  if (modulesResource.usingFallback.value || lessonsResource.usingFallback.value) {
    return previewModuleRows.value.map((module, index) => ({
      ...module,
      lessonCount: index === 0 ? lessonRows.value.length : 0,
      lessons: index === 0 ? lessonRows.value : [],
    }))
  }

  return []
})

const lessonOptions = computed(() => {
  const items = lessonsResource.data.value ?? []
  return items.map((lesson) => ({ id: lesson.id, title: lesson.title }))
})

const liveModuleOptions = computed(() =>
  liveModules.value
    .filter((module) => module.contentStatus !== 'ARCHIVED')
    .map((module) => ({ id: module.id, title: module.title })),
)

const livePackageOptions = computed(() =>
  livePackages.value
    .filter((item) => item.contentStatus !== 'ARCHIVED')
    .map((item) => ({ id: item.id, title: item.title })),
)

const packageItemOptions = computed(() => {
  const existingPairs = new Set(selectedCourse.value.packageItems.map((item) => `${item.itemType}:${item.itemId}`))

  if (packageItemForm.itemType === 'MODULE') {
    return liveModules.value
      .filter((item) => item.contentStatus !== 'ARCHIVED')
      .filter((item) => !existingPairs.has(`MODULE:${item.id}`))
      .map((item) => ({ id: item.id, title: item.title }))
  }

  return liveLessons.value
    .filter((item) => item.contentStatus !== 'ARCHIVED')
    .filter((item) => !existingPairs.has(`LESSON:${item.id}`))
    .map((item) => ({ id: item.id, title: item.title }))
})

const selectedQuiz = computed(() => quizList.value.find((item) => item.id === selectedQuizId.value) ?? null)

const resourceSummaryLabel = computed(() => {
  if (!resourceLessonId.value) return t('teacherContent.chooseLessonRecordings')
  return t('teacherContent.resourceSummary', {
    videos: uploadedResourceVideos.value.length,
    progress: lessonVideoProgress.value.length,
  })
})

const quizSummaryLabel = computed(() => {
  if (!quizLessonId.value) return t('teacherContent.chooseLessonQuizzes')
  return t('teacherContent.quizSummary', {
    quizzes: quizList.value.length,
    questions: quizAttempts.value.length,
  })
})

const builderHeading = computed(() => {
  switch (activeBuilder.value) {
    case 'module':
      return t('teacherContent.moduleBuilder')
    case 'lesson':
      return t('teacherContent.lessonBuilder')
    case 'resource':
      return t('teacherContent.resourceBuilder')
    case 'quiz':
      return t('teacherContent.assessmentBuilder')
    default:
      return t('teacherContent.courseBuilder')
  }
})

const builderDescription = computed(() => {
  switch (activeBuilder.value) {
    case 'module':
      return t('teacherContent.moduleBuilderHint')
    case 'lesson':
      return t('teacherContent.lessonBuilderHint')
    case 'resource':
      return t('teacherContent.resourceBuilderHint')
    case 'quiz':
      return t('teacherContent.assessmentBuilderHint')
    default:
      return t('teacherContent.courseBuilderHint')
  }
})

const builderLaunchLabel = computed(() => {
  switch (activeSurfaceTab.value) {
    case 'modules':
      return t('teacherContent.addModule')
    case 'lessons':
      return t('teacherContent.createLesson')
    case 'resources':
      return t('teacherContent.uploadResourceAction')
    case 'activity-types':
      return t('teacherContent.createQuiz')
    default:
      return t('teacherContent.createCourseAction')
  }
})

const editingPackageSource = computed(
  () => livePackages.value.find((item) => item.id === editingPackageId.value) ?? null,
)
const editingModuleSource = computed(() => liveModules.value.find((item) => item.id === editingModuleId.value) ?? null)
const editingLessonSource = computed(() => liveLessons.value.find((item) => item.id === editingLessonId.value) ?? null)
const editingResourceSource = computed(
  () => uploadedResourceVideos.value.find((item) => item.id === editingResourceId.value) ?? null,
)
const editingQuizSource = computed(() => quizList.value.find((item) => item.id === editingQuizId.value) ?? null)

watch(
  displayCourses,
  (items) => {
    if (!items.length) {
      selectedPackageId.value = 0
      return
    }
    if (!items.some((item) => item.id === selectedPackageId.value)) {
      selectedPackageId.value = items[0].id
    }
  },
  { immediate: true },
)

watch(
  lessonOptions,
  (items) => {
    const firstId = items[0]?.id ?? 0
    if (!items.some((item) => item.id === resourceLessonId.value)) {
      resourceLessonId.value = firstId
    }
    if (!items.some((item) => item.id === quizLessonId.value)) {
      quizLessonId.value = firstId
    }
    if (lessonForm.moduleId && !liveModuleOptions.value.some((module) => module.id === lessonForm.moduleId)) {
      lessonForm.moduleId = 0
    }
  },
  { immediate: true },
)

watch(
  resourceLessonId,
  (lessonId) => {
    resetResourceForm(lessonId)
    if (lessonId > 0 && !lessonsResource.usingFallback.value) {
      void loadResourceInsights(lessonId)
    } else {
      uploadedResourceVideos.value = []
      lessonVideoProgress.value = []
    }
  },
  { immediate: true },
)

watch(
  quizLessonId,
  (lessonId) => {
    resetQuizForm(lessonId)
    selectedQuizId.value = null
    quizAttempts.value = []
    resetQuestionForm()
    if (lessonId > 0 && !lessonsResource.usingFallback.value) {
      void loadQuizzesForLesson(lessonId)
    } else {
      quizList.value = []
    }
  },
  { immediate: true },
)

watch(
  () => questionForm.questionType,
  (type) => {
    if (type === 'TRUE_FALSE' && questionForm.options.every((option) => !option.optionText.trim())) {
      questionForm.options = [
        { optionText: t('teacherContent.trueLabel'), correctOption: true, displayOrder: 0 },
        { optionText: t('teacherContent.falseLabel'), correctOption: false, displayOrder: 1 },
      ]
    }
  },
)

function defaultQuestionOptions(): QuestionOptionDraft[] {
  return [
    { optionText: '', correctOption: false, displayOrder: 0 },
    { optionText: '', correctOption: false, displayOrder: 1 },
  ]
}

function formatPrice(value: number): string {
  return formatCurrency(value, 'MYR')
}

function formatDuration(value: number | null | undefined): string {
  const safeSeconds = nonNegativeInteger(value)
  if (safeSeconds <= 0) return '0s'
  const hours = Math.floor(safeSeconds / 3600)
  const minutes = Math.floor((safeSeconds % 3600) / 60)
  const seconds = safeSeconds % 60
  if (hours > 0) return `${hours}h ${minutes}m`
  if (minutes > 0) return `${minutes}m ${seconds}s`
  return `${seconds}s`
}

function descriptionLabel(
  value: string | null | undefined,
  fallback = t('teacherContent.noDescriptionProvided'),
): string {
  return value || fallback
}

function yesNoLabel(value: boolean): string {
  return value ? t('teacherContent.yes') : t('teacherContent.no')
}

function learningTypeLabel(value: string): string {
  const keys: Record<string, string> = {
    LIVE: 'live',
    RECORDED: 'recorded',
    HYBRID: 'hybrid',
  }
  const key = keys[value]
  return key ? t(`teacherContent.${key}`) : value
}

function packageItemRemoveLabel(packageItemId: number): string {
  return packageItemRemovingId.value === packageItemId ? t('teacherContent.removing') : t('teacherContent.remove')
}

function moduleArchiveLabel(moduleId: number): string {
  return moduleBusyId.value === moduleId ? t('teacherContent.working') : t('teacherContent.archive')
}

function lessonArchiveLabel(lessonId: number): string {
  return lessonBusyId.value === lessonId ? t('teacherContent.working') : t('teacherContent.archive')
}

function resourceArchiveLabel(videoId: number): string {
  return archivingResourceId.value === videoId ? t('teacherContent.working') : t('teacherContent.archive')
}

function quizDeleteLabel(quizId: number): string {
  return quizDeleteId.value === quizId ? t('teacherContent.working') : t('teacherContent.deleteAction')
}

function questionDeleteLabel(questionId: number): string {
  return questionDeleteId.value === questionId ? t('teacherContent.working') : t('teacherContent.deleteAction')
}

function videoProgressStatusLabel(completed: boolean): string {
  return completed ? t('teacherContent.completed') : t('teacherContent.watching')
}

function videoProgressStatusTone(completed: boolean): 'success' | 'warning' {
  return completed ? 'success' : 'warning'
}

function lastWatchedLabel(value: string | null | undefined): string {
  return value ? formatDate(value, shortDateOptions) : '--'
}

function quizTimeLimitLabel(value: number | null | undefined): string {
  return value ? t('teacherContent.minutes', { count: value }) : t('teacherContent.none')
}

function questionAnswerLabel(question: TeacherQuizQuestionResponse): string {
  return question.correctAnswerText || t('teacherContent.answerFallback')
}

function questionOptionStatusLabel(correct: boolean | null | undefined): string {
  return correct ? t('teacherContent.correct') : t('teacherContent.distractor')
}

function questionOptionStatusTone(correct: boolean | null | undefined): 'success' | 'info' {
  return correct ? 'success' : 'info'
}

function normalizeStatus(status: ContentStatus | string): string {
  switch (status) {
    case 'PUBLISHED':
      return t('teacherContent.statusPublished')
    case 'APPROVED':
      return t('teacherContent.statusApproved')
    case 'ARCHIVED':
      return t('teacherContent.statusArchived')
    case 'SUBMITTED':
      return t('teacherContent.statusSubmitted')
    case 'REJECTED':
      return t('teacherContent.statusRejected')
    default:
      return t('teacherContent.statusDraft')
  }
}

function contentStatusTone(status: string): 'success' | 'warning' | 'danger' | 'info' | 'primary' {
  switch (status) {
    case t('teacherContent.statusPublished'):
      return 'success'
    case t('teacherContent.statusApproved'):
      return 'info'
    case t('teacherContent.statusSubmitted'):
      return 'warning'
    case t('teacherContent.statusRejected'):
      return 'danger'
    case t('teacherContent.statusArchived'):
      return 'primary'
    default:
      return 'info'
  }
}

function courseThumbVariant(id: number): 'panda' | 'family' | 'pagoda' | 'travel' {
  const thumbs = ['panda', 'family', 'pagoda', 'travel'] as const
  return thumbs[Math.max(id - 1, 0) % thumbs.length]
}

function ensureLiveWrite(feature: string): boolean {
  void feature
  if (!previewReadOnly.value) return true
  actionError.value = t('teacherContent.readOnlyFeature')
  actionSuccess.value = null
  return false
}

function clearActionFeedback(): void {
  actionError.value = null
  actionSuccess.value = null
}

function seedSharedContentFields<
  T extends { levelId: number; categoryId: number; accessType: 'FREE' | 'PAID'; price?: number | null },
>(target: T): void {
  if (!selectedCourse.value.source) return
  target.levelId = selectedCourse.value.source.level.id
  target.categoryId = selectedCourse.value.source.category.id
  target.accessType = selectedCourse.value.source.accessType
  if ('price' in target) {
    target.price = selectedCourse.value.source.price
  }
}

function resetPackageForm(): void {
  editingPackageId.value = null
  packageForm.title = ''
  packageForm.description = ''
  packageForm.levelId = selectedCourse.value.source?.level.id ?? 0
  packageForm.categoryId = selectedCourse.value.source?.category.id ?? 0
  packageForm.learningType = selectedCourse.value.source?.learningType ?? 'HYBRID'
  packageForm.accessType = selectedCourse.value.source?.accessType ?? 'FREE'
  packageForm.price = selectedCourse.value.source?.price ?? 0
  packageForm.coverImageUrl = selectedCourse.value.source?.coverImageUrl ?? ''
}

function resetModuleForm(): void {
  editingModuleId.value = null
  moduleForm.title = ''
  moduleForm.description = ''
  moduleForm.levelId = 0
  moduleForm.categoryId = 0
  moduleForm.accessType = 'FREE'
  moduleForm.price = 0
  moduleForm.coverImageUrl = ''
  seedSharedContentFields(moduleForm)
}

function resetLessonForm(moduleId = 0): void {
  editingLessonId.value = null
  lessonForm.title = ''
  lessonForm.description = ''
  lessonForm.moduleId = moduleId > 0 ? moduleId : 0
  lessonForm.levelId = 0
  lessonForm.categoryId = 0
  lessonForm.learningType = 'HYBRID'
  lessonForm.accessType = 'FREE'
  lessonForm.price = 0
  lessonForm.estimatedDurationMinutes = 0
  lessonForm.basicContent = ''
  lessonForm.coverImageUrl = ''
  seedSharedContentFields(lessonForm)
}

function resetResourceForm(lessonId = 0): void {
  editingResourceId.value = null
  resourceForm.lessonId = lessonId
  resourceForm.packageId = 0
  resourceForm.title = ''
  resourceForm.description = ''
  resourceForm.sourceType = 'EXTERNAL_URL'
  resourceForm.externalVideoUrl = ''
  resourceForm.durationSeconds = 0
  if (resourceFileInput.value) {
    resourceFileInput.value.value = ''
  }
}

function resetQuizForm(lessonId = 0): void {
  editingQuizId.value = null
  quizForm.lessonId = lessonId
  quizForm.title = ''
  quizForm.description = ''
  quizForm.timeLimitMinutes = 0
}

function resetQuestionForm(): void {
  editingQuestionId.value = null
  questionForm.questionType = 'MULTIPLE_CHOICE'
  questionForm.questionText = ''
  questionForm.correctAnswerText = ''
  questionForm.points = 1
  questionForm.displayOrder = selectedQuiz.value?.questions.length ?? 0
  questionForm.explanation = ''
  questionForm.options = defaultQuestionOptions()
}

function selectSurfaceTab(tab: SurfaceTab): void {
  activeSurfaceTab.value = tab
}

function selectCourse(courseId: number): void {
  selectedPackageId.value = courseId
}

function openPackageBuilder(item?: CoursePackageResponse, preserveFeedback = false): void {
  activeSurfaceTab.value = 'courses'
  activeBuilder.value = 'package'
  if (!preserveFeedback) clearActionFeedback()

  if (!item) {
    resetPackageForm()
    return
  }

  editingPackageId.value = item.id
  packageForm.title = item.title
  packageForm.description = item.description ?? ''
  packageForm.levelId = item.level.id
  packageForm.categoryId = item.category.id
  packageForm.learningType = item.learningType
  packageForm.accessType = item.accessType
  packageForm.price = item.price
  packageForm.coverImageUrl = item.coverImageUrl ?? ''
}

function openModuleBuilder(item?: LearningModuleResponse | null, preserveFeedback = false): void {
  activeSurfaceTab.value = 'modules'
  activeBuilder.value = 'module'
  if (!preserveFeedback) clearActionFeedback()

  if (!item) {
    resetModuleForm()
    return
  }

  editingModuleId.value = item.id
  moduleForm.title = item.title
  moduleForm.description = item.description ?? ''
  moduleForm.levelId = item.level.id
  moduleForm.categoryId = item.category.id
  moduleForm.accessType = item.accessType
  moduleForm.price = item.price
  moduleForm.coverImageUrl = item.coverImageUrl ?? ''
}

function openLessonBuilder(item?: LessonResponse | null, moduleId = 0, preserveFeedback = false): void {
  activeSurfaceTab.value = 'lessons'
  activeBuilder.value = 'lesson'
  if (!preserveFeedback) clearActionFeedback()

  if (!item) {
    resetLessonForm(moduleId)
    return
  }

  editingLessonId.value = item.id
  lessonForm.title = item.title
  lessonForm.description = item.description ?? ''
  lessonForm.moduleId = item.moduleId ?? moduleId
  lessonForm.levelId = item.level.id
  lessonForm.categoryId = item.category.id
  lessonForm.learningType = item.learningType
  lessonForm.accessType = item.accessType
  lessonForm.price = item.price
  lessonForm.estimatedDurationMinutes = item.estimatedDurationMinutes ?? 0
  lessonForm.basicContent = item.basicContent ?? ''
  lessonForm.coverImageUrl = item.coverImageUrl ?? ''
}

function openResourceBuilder(item?: RecordedLessonVideoResponse | null): void {
  activeSurfaceTab.value = 'resources'
  activeBuilder.value = 'resource'
  clearActionFeedback()

  if (!item) {
    resetResourceForm(resourceLessonId.value)
    return
  }

  resourceLessonId.value = item.lessonId
  editingResourceId.value = item.id
  resourceForm.lessonId = item.lessonId
  resourceForm.packageId = item.packageId ?? 0
  resourceForm.title = item.title
  resourceForm.description = item.description ?? ''
  resourceForm.sourceType = item.sourceType
  resourceForm.externalVideoUrl = item.externalVideoUrl ?? ''
  resourceForm.durationSeconds = item.durationSeconds ?? 0
  if (resourceFileInput.value) {
    resourceFileInput.value.value = ''
  }
}

function openQuizBuilder(item?: TeacherQuizResponse | null): void {
  activeSurfaceTab.value = 'activity-types'
  activeBuilder.value = 'quiz'
  clearActionFeedback()

  if (!item) {
    resetQuizForm(quizLessonId.value)
    return
  }

  quizLessonId.value = item.lessonId
  editingQuizId.value = item.id
  quizForm.lessonId = item.lessonId
  quizForm.title = item.title
  quizForm.description = item.description ?? ''
  quizForm.timeLimitMinutes = item.timeLimitMinutes ?? 0
  selectedQuizId.value = item.id
}

function openQuestionBuilder(question?: TeacherQuizQuestionResponse | null): void {
  activeSurfaceTab.value = 'activity-types'
  activeBuilder.value = 'quiz'
  clearActionFeedback()

  if (!question) {
    resetQuestionForm()
    return
  }

  editingQuestionId.value = question.id
  questionForm.questionType = question.questionType
  questionForm.questionText = question.questionText
  questionForm.correctAnswerText = question.correctAnswerText ?? ''
  questionForm.points = question.points
  questionForm.displayOrder = question.displayOrder
  questionForm.explanation = question.explanation ?? ''
  questionForm.options = question.options.length
    ? question.options.map((option) => ({
        optionText: option.optionText,
        correctOption: Boolean(option.correctOption),
        displayOrder: option.displayOrder,
      }))
    : defaultQuestionOptions()
}

function openBuilderForActiveSurface(): void {
  switch (activeSurfaceTab.value) {
    case 'modules':
      openModuleBuilder()
      return
    case 'lessons':
      openLessonBuilder(undefined, lessonForm.moduleId || 0)
      return
    case 'resources':
      openResourceBuilder()
      return
    case 'activity-types':
      openQuizBuilder()
      return
    default:
      openPackageBuilder()
  }
}

function showAllCourses(): void {
  clearActionFeedback()
  selectedPackageId.value = displayCourses.value[0]?.id ?? 0
  activeSurfaceTab.value = 'courses'
  actionSuccess.value = t('teacherContent.showingAll')
}

async function loadResourceInsights(lessonId: number): Promise<void> {
  loadingResourceInsights.value = true
  try {
    const [videos, progressRows] = await Promise.all([
      teacherApi.recordedVideos(lessonId),
      teacherApi.lessonVideoProgress(lessonId),
    ])
    uploadedResourceVideos.value = videos
    lessonVideoProgress.value = progressRows
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('teacherContent.loadResourcesError'))
  } finally {
    loadingResourceInsights.value = false
  }
}

async function loadQuizzesForLesson(lessonId: number, preferredQuizId?: number | null): Promise<void> {
  loadingQuizzes.value = true
  try {
    const items = await teacherApi.lessonQuizzes(lessonId)
    quizList.value = items
    const nextId =
      preferredQuizId && items.some((item) => item.id === preferredQuizId)
        ? preferredQuizId
        : selectedQuizId.value && items.some((item) => item.id === selectedQuizId.value)
          ? selectedQuizId.value
          : (items[0]?.id ?? null)
    selectedQuizId.value = nextId
    if (nextId) {
      await loadQuizAttempts(nextId)
    } else {
      quizAttempts.value = []
    }
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('teacherContent.loadQuizzesError'))
  } finally {
    loadingQuizzes.value = false
  }
}

async function loadQuizAttempts(quizId: number): Promise<void> {
  loadingQuizAttempts.value = true
  try {
    quizAttempts.value = await teacherApi.quizAttempts(quizId)
    selectedQuizId.value = quizId
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('teacherContent.loadAttemptsError'))
  } finally {
    loadingQuizAttempts.value = false
  }
}

function selectQuiz(quizId: number): void {
  void loadQuizAttempts(quizId)
}

async function reloadAll(): Promise<void> {
  clearActionFeedback()
  await Promise.all([
    packagesResource.reload(),
    lessonsResource.reload(),
    modulesResource.reload(),
    levelsResource.reload(),
    categoriesResource.reload(),
  ])

  if (resourceLessonId.value > 0 && !lessonsResource.usingFallback.value) {
    await loadResourceInsights(resourceLessonId.value)
  }
  if (quizLessonId.value > 0 && !lessonsResource.usingFallback.value) {
    await loadQuizzesForLesson(quizLessonId.value, selectedQuizId.value)
  }
}

async function archiveSelectedPackage(): Promise<void> {
  if (packageBusy.value) return
  if (!selectedCourse.value.source || !ensureLiveWrite('Course management')) return
  if (
    !confirmTeacherContentMutation(
      `Archive the course "${selectedCourse.value.title}"? Students may lose access to this course package.`,
    )
  )
    return

  clearActionFeedback()
  packageBusy.value = true
  try {
    await teacherApi.archivePackage(selectedCourse.value.source.id)
    await packagesResource.reload()
    actionSuccess.value = t('teacherContent.archivedCourse')
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('teacherContent.archiveCourseError'))
  } finally {
    packageBusy.value = false
  }
}

async function archiveModule(item?: LearningModuleResponse | null): Promise<void> {
  if (moduleBusyId.value) return
  if (!item || !ensureLiveWrite('Module management')) return
  if (
    !confirmTeacherContentMutation(
      `Archive the module "${item.title}"? Linked lessons may become harder for learners to find.`,
    )
  )
    return

  clearActionFeedback()
  moduleBusyId.value = item.id
  try {
    await teacherApi.archiveModule(item.id)
    await Promise.all([modulesResource.reload(), packagesResource.reload()])
    actionSuccess.value = t('teacherContent.archivedModule')
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('teacherContent.archiveModuleError'))
  } finally {
    moduleBusyId.value = null
  }
}

async function archiveLesson(item?: LessonResponse | null): Promise<void> {
  if (lessonBusyId.value) return
  if (!item || !ensureLiveWrite('Lesson management')) return
  if (!confirmTeacherContentMutation(`Archive the lesson "${item.title}"? Learners may lose access to its materials.`))
    return

  clearActionFeedback()
  lessonBusyId.value = item.id
  try {
    await teacherApi.archiveLesson(item.id)
    await Promise.all([lessonsResource.reload(), packagesResource.reload()])
    actionSuccess.value = t('teacherContent.archivedLesson')
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('teacherContent.archiveLessonError'))
  } finally {
    lessonBusyId.value = null
  }
}

async function submitPackageItem(): Promise<void> {
  if (submitting.packageItem) return
  if (!selectedCourse.value.source) {
    actionError.value = t('teacherContent.selectLiveCourse')
    actionSuccess.value = null
    return
  }
  if (!ensureLiveWrite('Course packaging')) return
  if (packageItemForm.itemId <= 0) {
    actionError.value = t('teacherContent.choosePackageItem')
    actionSuccess.value = null
    return
  }

  clearActionFeedback()
  submitting.packageItem = true
  try {
    await teacherApi.addPackageItem(selectedCourse.value.source.id, {
      itemType: packageItemForm.itemType,
      itemId: packageItemForm.itemId,
      displayOrder: packageItemForm.displayOrder,
    })
    await packagesResource.reload()
    packageItemForm.itemId = 0
    packageItemForm.displayOrder = selectedCourse.value.packageItems.length + 1
    actionSuccess.value = t('teacherContent.packageUpdated')
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('teacherContent.addPackageItemError'))
  } finally {
    submitting.packageItem = false
  }
}

async function removePackageItem(packageItemId: number): Promise<void> {
  if (packageItemRemovingId.value) return
  if (!selectedCourse.value.source || !ensureLiveWrite('Course packaging')) return
  if (!confirmTeacherContentMutation(t('teacherContent.confirmRemoveItem'))) return

  clearActionFeedback()
  packageItemRemovingId.value = packageItemId
  try {
    await teacherApi.removePackageItem(selectedCourse.value.source.id, packageItemId)
    await packagesResource.reload()
    actionSuccess.value = t('teacherContent.packageItemRemoved')
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('teacherContent.removePackageItemError'))
  } finally {
    packageItemRemovingId.value = null
  }
}

async function submitPackage(): Promise<void> {
  if (submitting.package) return
  if (!ensureLiveWrite('Course management')) return
  if (!packageForm.title.trim()) {
    actionError.value = t('teacherContent.courseTitleRequired')
    actionSuccess.value = null
    return
  }
  if (packageForm.levelId <= 0 || packageForm.categoryId <= 0) {
    actionError.value = t('teacherContent.selectCourseTaxonomy')
    actionSuccess.value = null
    return
  }

  clearActionFeedback()
  submitting.package = true
  try {
    const isEditing = Boolean(editingPackageId.value)
    const payload: TeacherPackagePayload = {
      ...packageForm,
      title: packageForm.title.trim(),
      description: (packageForm.description ?? '').trim() || null,
      price: packageForm.accessType === 'FREE' ? 0 : Number(packageForm.price || 0),
      coverImageUrl: (packageForm.coverImageUrl ?? '').trim() || null,
    }

    const response = editingPackageId.value
      ? await teacherApi.updatePackage(editingPackageId.value, payload)
      : await teacherApi.createPackage(payload)

    await packagesResource.reload()
    selectedPackageId.value = response.id
    if (isEditing) {
      editingPackageId.value = response.id
      actionSuccess.value = t('teacherContent.courseUpdated')
    } else {
      actionSuccess.value = t('teacherContent.courseCreated')
      openPackageBuilder(response, true)
    }
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('teacherContent.saveCourseError'))
  } finally {
    submitting.package = false
  }
}

async function submitModule(): Promise<void> {
  if (submitting.module) return
  if (!ensureLiveWrite('Module management')) return
  if (!moduleForm.title.trim()) {
    actionError.value = t('teacherContent.moduleTitleRequired')
    actionSuccess.value = null
    return
  }
  if (moduleForm.levelId <= 0 || moduleForm.categoryId <= 0) {
    actionError.value = t('teacherContent.selectModuleTaxonomy')
    actionSuccess.value = null
    return
  }

  clearActionFeedback()
  submitting.module = true
  try {
    const isEditing = Boolean(editingModuleId.value)
    const payload: TeacherModulePayload = {
      ...moduleForm,
      title: moduleForm.title.trim(),
      description: (moduleForm.description ?? '').trim() || null,
      price: moduleForm.accessType === 'FREE' ? 0 : Number(moduleForm.price || 0),
      coverImageUrl: (moduleForm.coverImageUrl ?? '').trim() || null,
    }

    const response = editingModuleId.value
      ? await teacherApi.updateModule(editingModuleId.value, payload)
      : await teacherApi.createModule(payload)

    await modulesResource.reload()
    editingModuleId.value = response.id
    actionSuccess.value = isEditing ? t('teacherContent.moduleSaved') : t('teacherContent.moduleCreated')
    openModuleBuilder(response, true)
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('teacherContent.saveModuleError'))
  } finally {
    submitting.module = false
  }
}

async function submitLesson(): Promise<void> {
  if (submitting.lesson) return
  if (!ensureLiveWrite('Lesson management')) return
  if (!lessonForm.title.trim()) {
    actionError.value = t('teacherContent.lessonTitleRequired')
    actionSuccess.value = null
    return
  }
  if (!lessonForm.moduleId || lessonForm.moduleId <= 0) {
    actionError.value = t('teacherContent.selectModuleError')
    actionSuccess.value = null
    return
  }
  if (lessonForm.levelId <= 0 || lessonForm.categoryId <= 0) {
    actionError.value = t('teacherContent.selectLessonTaxonomy')
    actionSuccess.value = null
    return
  }

  clearActionFeedback()
  submitting.lesson = true
  try {
    const isEditing = Boolean(editingLessonId.value)
    const payload: TeacherLessonPayload = {
      ...lessonForm,
      title: lessonForm.title.trim(),
      description: (lessonForm.description ?? '').trim() || null,
      moduleId: lessonForm.moduleId,
      price: lessonForm.accessType === 'FREE' ? 0 : Number(lessonForm.price || 0),
      estimatedDurationMinutes:
        lessonForm.estimatedDurationMinutes && lessonForm.estimatedDurationMinutes > 0
          ? lessonForm.estimatedDurationMinutes
          : 0,
      basicContent: (lessonForm.basicContent ?? '').trim() || null,
      coverImageUrl: (lessonForm.coverImageUrl ?? '').trim() || null,
    }

    const response = editingLessonId.value
      ? await teacherApi.updateLesson(editingLessonId.value, payload)
      : await teacherApi.createLesson(payload)

    await Promise.all([lessonsResource.reload(), modulesResource.reload()])
    editingLessonId.value = response.id
    actionSuccess.value = isEditing ? t('teacherContent.lessonSaved') : t('teacherContent.lessonCreated')
    openLessonBuilder(response, response.moduleId ?? 0, true)
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('teacherContent.saveLessonError'))
  } finally {
    submitting.lesson = false
  }
}

async function submitRecordedResource(): Promise<void> {
  if (submitting.resource) return
  if (!ensureLiveWrite('Recorded lesson management')) return
  if (!resourceForm.lessonId || resourceForm.lessonId <= 0) {
    actionError.value = t('teacherContent.selectLessonRecording')
    actionSuccess.value = null
    return
  }
  if (!resourceForm.title.trim()) {
    actionError.value = t('teacherContent.recordingTitleRequired')
    actionSuccess.value = null
    return
  }
  if (resourceForm.sourceType === 'EXTERNAL_URL' && !resourceForm.externalVideoUrl.trim()) {
    actionError.value = t('teacherContent.externalUrlRequired')
    actionSuccess.value = null
    return
  }

  const selectedFile = resourceFileInput.value?.files?.[0]
  if (resourceForm.sourceType === 'UPLOADED_FILE' && !selectedFile && !editingResourceId.value) {
    actionError.value = t('teacherContent.fileRequired')
    actionSuccess.value = null
    return
  }

  clearActionFeedback()
  submitting.resource = true
  try {
    const formData = new FormData()
    formData.append('title', resourceForm.title.trim())
    formData.append('description', resourceForm.description.trim())
    formData.append('sourceType', resourceForm.sourceType)
    formData.append('sortOrder', String(uploadedResourceVideos.value.length))
    if (resourceForm.packageId > 0) {
      formData.append('packageId', String(resourceForm.packageId))
    }
    const durationSeconds = nonNegativeInteger(resourceForm.durationSeconds)
    if (durationSeconds > 0) {
      formData.append('durationSeconds', String(durationSeconds))
    }
    if (resourceForm.sourceType === 'EXTERNAL_URL') {
      formData.append('externalVideoUrl', resourceForm.externalVideoUrl.trim())
    }
    if (resourceForm.sourceType === 'UPLOADED_FILE' && selectedFile) {
      formData.append('file', selectedFile)
    }

    const response = editingResourceId.value
      ? await teacherApi.updateRecordedVideo(resourceForm.lessonId, editingResourceId.value, formData)
      : await teacherApi.createRecordedVideo(resourceForm.lessonId, formData)

    resourceLessonId.value = response.lessonId
    await loadResourceInsights(response.lessonId)
    actionSuccess.value = editingResourceId.value
      ? t('teacherContent.recordingUpdated')
      : t('teacherContent.recordingCreated')
    openResourceBuilder(response)
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('teacherContent.saveRecordingError'))
  } finally {
    submitting.resource = false
  }
}

async function archiveRecordedResource(videoId: number): Promise<void> {
  if (archivingResourceId.value) return
  if (!ensureLiveWrite('Recorded lesson management') || resourceLessonId.value <= 0) return
  if (!confirmTeacherContentMutation(t('teacherContent.confirmArchiveRecording'))) return

  clearActionFeedback()
  archivingResourceId.value = videoId
  try {
    await teacherApi.archiveRecordedVideo(resourceLessonId.value, videoId)
    await loadResourceInsights(resourceLessonId.value)
    if (editingResourceId.value === videoId) {
      resetResourceForm(resourceLessonId.value)
    }
    actionSuccess.value = t('teacherContent.recordingArchived')
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('teacherContent.archiveRecordingError'))
  } finally {
    archivingResourceId.value = null
  }
}

async function submitQuiz(): Promise<void> {
  if (submitting.quiz) return
  if (!ensureLiveWrite('Quiz management')) return
  if (!quizForm.lessonId || quizForm.lessonId <= 0) {
    actionError.value = t('teacherContent.selectLessonQuiz')
    actionSuccess.value = null
    return
  }
  if (!quizForm.title.trim()) {
    actionError.value = t('teacherContent.quizTitleRequired')
    actionSuccess.value = null
    return
  }

  clearActionFeedback()
  submitting.quiz = true
  try {
    const isEditing = Boolean(editingQuizId.value)
    const payload: TeacherQuizPayload = {
      title: quizForm.title.trim(),
      description: (quizForm.description ?? '').trim() || null,
      timeLimitMinutes: quizForm.timeLimitMinutes && quizForm.timeLimitMinutes > 0 ? quizForm.timeLimitMinutes : 0,
    }

    const response = editingQuizId.value
      ? await teacherApi.updateQuiz(editingQuizId.value, payload)
      : await teacherApi.createQuiz(quizForm.lessonId, payload)

    quizLessonId.value = response.lessonId
    await loadQuizzesForLesson(response.lessonId, response.id)
    openQuizBuilder(response)
    actionSuccess.value = isEditing ? t('teacherContent.quizUpdated') : t('teacherContent.quizCreated')
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('teacherContent.saveQuizError'))
  } finally {
    submitting.quiz = false
  }
}

function buildQuestionOptions(): TeacherQuizOptionPayload[] | null {
  const options = questionForm.options
    .map((option, index) => ({
      optionText: option.optionText.trim(),
      correctOption: option.correctOption,
      displayOrder: option.displayOrder ?? index,
    }))
    .filter((option) => option.optionText)

  return options.length ? options : null
}

async function submitQuestion(): Promise<void> {
  if (submitting.question) return
  if (!ensureLiveWrite('Quiz question management')) return
  if (!selectedQuiz.value) {
    actionError.value = t('teacherContent.openQuizFirst')
    actionSuccess.value = null
    return
  }
  if (!questionForm.questionText.trim()) {
    actionError.value = t('teacherContent.questionRequired')
    actionSuccess.value = null
    return
  }
  if (!questionForm.points || questionForm.points <= 0) {
    actionError.value = t('teacherContent.pointsRequired')
    actionSuccess.value = null
    return
  }

  const options = buildQuestionOptions()
  if (!(questionForm.correctAnswerText ?? '').trim() && !options?.some((option) => option.correctOption)) {
    actionError.value = t('teacherContent.correctAnswerRequired')
    actionSuccess.value = null
    return
  }

  clearActionFeedback()
  submitting.question = true
  try {
    const isEditing = Boolean(editingQuestionId.value)
    const payload: TeacherQuizQuestionPayload = {
      questionType: questionForm.questionType,
      questionText: questionForm.questionText.trim(),
      correctAnswerText: (questionForm.correctAnswerText ?? '').trim() || null,
      points: questionForm.points,
      displayOrder: questionForm.displayOrder,
      options,
      explanation: (questionForm.explanation ?? '').trim() || null,
    }

    const response = editingQuestionId.value
      ? await teacherApi.updateQuizQuestion(editingQuestionId.value, payload)
      : await teacherApi.addQuizQuestion(selectedQuiz.value.id, payload)

    quizLessonId.value = response.lessonId
    await loadQuizzesForLesson(response.lessonId, response.id)
    resetQuestionForm()
    actionSuccess.value = isEditing ? t('teacherContent.questionUpdated') : t('teacherContent.questionCreated')
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('teacherContent.saveQuestionError'))
  } finally {
    submitting.question = false
  }
}

async function deleteQuiz(quizId: number): Promise<void> {
  if (quizDeleteId.value) return
  if (!ensureLiveWrite('Quiz management')) return
  if (!confirmTeacherContentMutation(t('teacherContent.confirmDeleteQuiz'))) return

  clearActionFeedback()
  quizDeleteId.value = quizId
  try {
    await teacherApi.deleteQuiz(quizId)
    if (selectedQuizId.value === quizId) {
      resetQuestionForm()
      selectedQuizId.value = null
    }
    await loadQuizzesForLesson(quizLessonId.value)
    actionSuccess.value = t('teacherContent.quizDeleted')
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('teacherContent.deleteQuizError'))
  } finally {
    quizDeleteId.value = null
  }
}

async function deleteQuestion(questionId: number): Promise<void> {
  if (questionDeleteId.value) return
  if (!ensureLiveWrite('Quiz question management')) return
  if (!confirmTeacherContentMutation(t('teacherContent.confirmDeleteQuestion'))) return

  clearActionFeedback()
  questionDeleteId.value = questionId
  try {
    const response = await teacherApi.deleteQuizQuestion(questionId)
    quizLessonId.value = response.lessonId
    await loadQuizzesForLesson(response.lessonId, response.id)
    if (editingQuestionId.value === questionId) {
      resetQuestionForm()
    }
    actionSuccess.value = t('teacherContent.questionDeleted')
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('teacherContent.deleteQuestionError'))
  } finally {
    questionDeleteId.value = null
  }
}

function addQuestionOption(): void {
  questionForm.options.push({
    optionText: '',
    correctOption: false,
    displayOrder: questionForm.options.length,
  })
}

function removeQuestionOption(index: number): void {
  if (questionForm.options.length <= 1) return
  questionForm.options.splice(index, 1)
  questionForm.options.forEach((option, optionIndex) => {
    option.displayOrder = optionIndex
  })
}

function confirmTeacherContentMutation(message: string): boolean {
  const confirmed = window.confirm(`${message}\n\n${t('teacherContent.liveChangeWarning')}`)
  if (!confirmed) {
    actionSuccess.value = null
    actionError.value = null
  }

  return confirmed
}
</script>
