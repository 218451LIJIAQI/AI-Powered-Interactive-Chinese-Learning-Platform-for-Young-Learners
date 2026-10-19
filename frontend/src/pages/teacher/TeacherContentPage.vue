<template>
  <div class="page-stack teacher-content-page">
    <PageHeaderBlock :title="t('teacherContent.title')" :description="t('teacherContent.description')" icon="content" />

    <DataNotice :using-fallback="usingFallback" :error="noticeError" :success="actionSuccess" />

    <TeacherApprovalLockPanel
      v-if="teacherApprovalLocked"
      :title="teacherApprovalTitle"
      :message="teacherApprovalMessage"
    />

    <template v-else>
      <nav
        class="tabs tabs--underline teacher-content-tabs"
        role="tablist"
        :aria-label="t('teacherContent.sectionsLabel')"
      >
        <button
          v-for="tab in contentTabs"
          :id="`teacher-content-tab-${tab.key}`"
          :key="tab.key"
          class="tab-button"
          type="button"
          role="tab"
          :aria-selected="tab.key === activeSurfaceTab"
          aria-controls="teacher-content-panel"
          @click="selectSurfaceTab(tab.key)"
        >
          <IconGlyph :name="tab.icon" :size="16" />
          <span>{{ tab.label }}</span>
        </button>
      </nav>

      <section class="teacher-content-layout">
        <aside class="panel teacher-course-sidebar" :aria-labelledby="'teacher-course-sidebar-title'">
          <div class="card-header teacher-course-sidebar-head">
            <h2 id="teacher-course-sidebar-title" class="card-title">{{ t('teacherContent.myCourses') }}</h2>
            <button class="btn btn-ghost btn-sm" type="button" @click="reloadAll">
              <IconGlyph name="refresh" :size="16" />
              <span>{{ t('teacherContent.refresh') }}</span>
            </button>
          </div>

          <button
            class="btn btn-primary teacher-create-course"
            type="button"
            :disabled="previewReadOnly"
            @click="openPackageBuilder()"
          >
            <IconGlyph name="plus" :size="16" />
            {{ t('teacherContent.createCourse') }}
          </button>

          <StatePanel
            v-if="packagesResource.loading.value && !displayCourses.length"
            tone="loading"
            :title="t('teacherContent.loadingCourses')"
            :message="t('teacherContent.loadingCoursesHint')"
            :skeleton="false"
            compact
          />

          <StatePanel
            v-else-if="packagesResource.error.value && !displayCourses.length"
            tone="error"
            icon="alert"
            :title="t('teacherContent.loadErrorTitle')"
            :message="packagesResource.error.value"
            compact
          >
            <template #actions>
              <button class="btn btn-primary" type="button" @click="reloadAll">
                {{ t('teacherContent.retry') }}
              </button>
            </template>
          </StatePanel>

          <div v-else-if="displayCourses.length" class="teacher-course-list">
            <article
              v-for="item in displayCourses"
              :key="item.id"
              class="teacher-course-item"
              :class="{ 'is-active': item.id === selectedCourse.id }"
              role="button"
              tabindex="0"
              :aria-pressed="item.id === selectedCourse.id"
              @click="selectCourse(item.id)"
              @keydown.enter.prevent="selectCourse(item.id)"
              @keydown.space.prevent="selectCourse(item.id)"
            >
              <CourseSceneThumb
                class="teacher-course-thumb"
                :size="52"
                :variant="courseThumbVariant(item.id)"
                aria-hidden="true"
              />
              <div class="teacher-course-copy">
                <strong>{{ item.title }}</strong>
                <span class="teacher-course-subtitle">{{ item.subtitle }}</span>
                <div class="teacher-course-meta">
                  <ContentStatusBadge :status="item.status" :show-reason="false" />
                  <span>{{ t('teacherContent.items', { count: item.lessonCount }) }}</span>
                </div>
              </div>
            </article>
          </div>

          <StatePanel
            v-else
            icon="courses"
            :title="t('teacherContent.noCourse')"
            :message="t('teacherContent.selectOrCreateCourse')"
            compact
          />
        </aside>

        <div
          id="teacher-content-panel"
          class="teacher-content-main"
          role="tabpanel"
          :aria-labelledby="`teacher-content-tab-${activeSurfaceTab}`"
        >
          <section v-if="showCourseWorkspace" class="panel teacher-content-card">
            <header class="teacher-content-summary">
              <CourseSceneThumb
                class="teacher-course-thumb"
                :size="72"
                :variant="courseThumbVariant(selectedCourse.id)"
                aria-hidden="true"
              />

              <div class="teacher-content-summary-copy">
                <h2 class="teacher-content-title">{{ selectedCourse.title }}</h2>
                <div class="teacher-content-summary-meta">
                  <ContentStatusBadge :status="selectedCourse.status" :show-reason="false" />
                  <span>{{ selectedCourse.subtitle }}</span>
                  <span aria-hidden="true" class="teacher-content-dot" />
                  <span>{{ t('teacherContent.items', { count: selectedCourse.lessonCount }) }}</span>
                </div>
              </div>

              <div class="list-action-row teacher-content-summary-actions">
                <button
                  class="btn btn-secondary btn-sm"
                  type="button"
                  :disabled="!selectedCourse.source || previewReadOnly || isTeacherEditLocked(selectedCourse.status)"
                  @click="openPackageBuilder(selectedCourse.source ?? undefined)"
                >
                  <IconGlyph name="pen" :size="16" />
                  <span>{{ t('teacherContent.editCourse') }}</span>
                </button>
                <button
                  class="btn btn-ghost btn-sm"
                  type="button"
                  :disabled="
                    !selectedCourse.source ||
                    previewReadOnly ||
                    packageBusy ||
                    isTeacherEditLocked(selectedCourse.status)
                  "
                  :aria-busy="packageBusy"
                  @click="archiveSelectedPackage"
                >
                  {{ packageArchiveLabel }}
                </button>
                <button
                  v-if="selectedCourse.source && canSubmitForReview(selectedCourse.status)"
                  class="btn btn-primary btn-sm"
                  type="button"
                  :disabled="previewReadOnly || reviewSubmittingKey !== null"
                  data-testid="submit-course-review"
                  @click="submitForReview('package', selectedCourse.source)"
                >
                  {{ submitReviewLabel('package', selectedCourse.id, selectedCourse.status) }}
                </button>
              </div>
            </header>

            <ContentReviewNotice
              v-if="selectedCourse.source"
              :status="selectedCourse.status"
              :rejection-reason="selectedCourse.rejectionReason"
            />

            <div class="teacher-overview-grid">
              <div class="teacher-overview-copy">
                <h3 class="teacher-content-subtitle">{{ t('teacherContent.courseOverview') }}</h3>
                <p>{{ selectedCourse.description }}</p>
              </div>

              <dl class="teacher-overview-meta">
                <div>
                  <dt>{{ t('teacherContent.level') }}</dt>
                  <dd>{{ selectedCourse.levelLabel }}</dd>
                </div>
                <div>
                  <dt>{{ t('teacherContent.learningType') }}</dt>
                  <dd>{{ selectedCourse.learningTypeLabel }}</dd>
                </div>
                <div>
                  <dt>{{ t('teacherContent.lastUpdated') }}</dt>
                  <dd>{{ selectedCourse.updatedLabel }}</dd>
                </div>
              </dl>
            </div>

            <section class="teacher-content-section" aria-labelledby="teacher-course-items-title">
              <div class="teacher-content-section-head">
                <h3 id="teacher-course-items-title" class="teacher-content-subtitle">
                  {{ t('teacherContent.packageItems') }}
                </h3>
                <button
                  class="btn btn-secondary btn-sm"
                  type="button"
                  :disabled="!selectedCourse.source || previewReadOnly || courseLocked"
                  @click="openPackageBuilder(selectedCourse.source ?? undefined)"
                >
                  <IconGlyph name="pen" :size="16" />
                  <span>{{ t('teacherContent.editCourse') }}</span>
                </button>
              </div>

              <div v-if="selectedCourse.packageItems.length" class="teacher-module-list">
                <article v-for="item in selectedCourse.packageItems" :key="item.id" class="teacher-content-row">
                  <span class="teacher-content-index">{{ item.displayOrder }}</span>
                  <div class="teacher-content-row-main">
                    <strong :id="`teacher-package-item-${item.id}`">{{ item.titleSnapshot }}</strong>
                    <div class="teacher-content-row-meta">
                      <span class="tag">{{ packageItemTypeLabel(item.itemType) }}</span>
                    </div>
                  </div>
                  <div class="list-action-row teacher-content-row-actions">
                    <button
                      class="btn btn-ghost btn-sm"
                      type="button"
                      :aria-describedby="`teacher-package-item-${item.id}`"
                      :disabled="
                        !selectedCourse.source || previewReadOnly || courseLocked || packageItemRemovingId === item.id
                      "
                      :aria-busy="packageItemRemovingId === item.id"
                      @click="removePackageItem(item.id)"
                    >
                      {{ packageItemRemoveLabel(item.id) }}
                    </button>
                  </div>
                </article>
              </div>

              <StatePanel
                v-else
                icon="courses"
                :title="t('teacherContent.noPackageItems')"
                :message="t('teacherContent.noPackageItemsHint')"
                compact
              />

              <form class="teacher-inline-form" @submit.prevent="submitPackageItem">
                <div class="teacher-inline-form-head">
                  <span class="media-badge small" aria-hidden="true"><IconGlyph name="courses" /></span>
                  <div class="card-section-copy">
                    <strong>{{ t('teacherContent.addCourseItem') }}</strong>
                    <span class="muted">{{ t('teacherContent.addCourseItemHint') }}</span>
                  </div>
                </div>

                <div class="teacher-inline-form-fields">
                  <div class="field">
                    <label class="field-label" for="teacher-package-item-type">{{
                      t('teacherContent.itemType')
                    }}</label>
                    <select id="teacher-package-item-type" v-model="packageItemForm.itemType" class="select">
                      <option value="LESSON">{{ t('teacherContent.lesson') }}</option>
                      <option value="MODULE">{{ t('teacherContent.module') }}</option>
                    </select>
                  </div>

                  <div class="field teacher-inline-form-wide">
                    <label class="field-label" for="teacher-package-item-id">
                      {{ t('teacherContent.availableItem') }}
                    </label>
                    <select
                      id="teacher-package-item-id"
                      v-model.number="packageItemForm.itemId"
                      class="select"
                      :disabled="!selectedCourse.source || previewReadOnly || courseLocked"
                    >
                      <option :value="0">{{ t('teacherContent.selectItem') }}</option>
                      <option v-for="item in packageItemOptions" :key="item.id" :value="item.id">
                        {{ item.title }}
                      </option>
                    </select>
                  </div>

                  <div class="field">
                    <label class="field-label" for="teacher-package-item-order">
                      {{ t('teacherContent.displayOrder') }}
                    </label>
                    <input
                      id="teacher-package-item-order"
                      v-model.number="packageItemForm.displayOrder"
                      class="input"
                      type="number"
                      min="0"
                    />
                  </div>
                </div>

                <div class="form-actions">
                  <button
                    class="btn btn-secondary"
                    type="submit"
                    :disabled="!selectedCourse.source || previewReadOnly || courseLocked || submitting.packageItem"
                    :aria-busy="submitting.packageItem"
                  >
                    {{ packageItemSubmitLabel }}
                  </button>
                </div>
              </form>
            </section>

            <section class="teacher-content-section" aria-labelledby="teacher-modules-title">
              <div class="teacher-content-section-head">
                <h3 id="teacher-modules-title" class="teacher-content-subtitle">
                  {{ t('teacherContent.modulesLessons') }}
                </h3>
                <button
                  class="btn btn-secondary btn-sm"
                  type="button"
                  :disabled="previewReadOnly"
                  @click="openModuleBuilder()"
                >
                  <IconGlyph name="plus" :size="14" />
                  {{ t('teacherContent.addModule') }}
                </button>
              </div>

              <div class="teacher-module-list">
                <article v-for="module in moduleRows" :key="module.id" class="teacher-module-card">
                  <div class="teacher-content-row teacher-content-row--module">
                    <span class="teacher-content-index">{{ module.id }}</span>
                    <div class="teacher-content-row-main">
                      <strong :id="`teacher-module-${module.id}`">{{ module.title }}</strong>
                      <div class="teacher-content-row-meta">
                        <ContentStatusBadge
                          v-if="module.source"
                          :status="module.source.contentStatus"
                          :show-reason="false"
                        />
                        <span>{{ t('teacherContent.lessonCount', { count: module.lessonCount }) }}</span>
                      </div>
                    </div>
                    <div class="list-action-row teacher-content-row-actions">
                      <button
                        class="btn btn-secondary btn-sm"
                        type="button"
                        :aria-describedby="`teacher-module-${module.id}`"
                        :disabled="
                          !module.source || previewReadOnly || isTeacherEditLocked(module.source.contentStatus)
                        "
                        @click="openModuleBuilder(module.source ?? undefined)"
                      >
                        {{ t('teacherContent.edit') }}
                      </button>
                      <button
                        v-if="module.source && canSubmitForReview(module.source.contentStatus)"
                        class="btn btn-outline btn-sm"
                        type="button"
                        :aria-describedby="`teacher-module-${module.id}`"
                        :disabled="previewReadOnly || reviewSubmittingKey !== null"
                        @click="submitForReview('module', module.source)"
                      >
                        {{ submitReviewLabel('module', module.id, module.source.contentStatus) }}
                      </button>
                      <button
                        class="btn btn-ghost btn-sm"
                        type="button"
                        :aria-describedby="`teacher-module-${module.id}`"
                        :disabled="
                          !module.source ||
                          previewReadOnly ||
                          moduleBusyId === module.id ||
                          isTeacherEditLocked(module.source.contentStatus)
                        "
                        :aria-busy="moduleBusyId === module.id"
                        @click="archiveModule(module.source ?? undefined)"
                      >
                        {{ moduleArchiveLabel(module.id) }}
                      </button>
                    </div>
                  </div>

                  <ContentReviewNotice
                    v-if="module.source?.contentStatus === 'REJECTED'"
                    :status="module.source.contentStatus"
                    :rejection-reason="module.source.rejectionReason"
                  />

                  <ol v-if="module.lessons.length" class="teacher-lesson-list">
                    <li v-for="lesson in module.lessons" :key="lesson.id" class="teacher-lesson-item">
                      <div class="teacher-content-row teacher-content-row--nested">
                        <span class="teacher-content-index">{{ lesson.order }}</span>
                        <div class="teacher-content-row-main">
                          <strong :id="`teacher-lesson-${lesson.id}`">{{ lesson.title }}</strong>
                          <div class="teacher-content-row-meta">
                            <ContentStatusBadge :status="lesson.status" :show-reason="false" />
                            <span>{{ lesson.categoryLabel }}</span>
                            <span aria-hidden="true" class="teacher-content-dot" />
                            <span>{{ lesson.metaLabel }}</span>
                          </div>
                        </div>
                        <div class="list-action-row teacher-content-row-actions">
                          <button
                            class="btn btn-secondary btn-sm"
                            type="button"
                            :aria-describedby="`teacher-lesson-${lesson.id}`"
                            :disabled="!lesson.source || previewReadOnly || isTeacherEditLocked(lesson.status)"
                            @click="openLessonBuilder(lesson.source ?? undefined, lesson.moduleId)"
                          >
                            {{ t('teacherContent.edit') }}
                          </button>
                          <button
                            v-if="lesson.source && canSubmitForReview(lesson.status)"
                            class="btn btn-outline btn-sm"
                            type="button"
                            :aria-describedby="`teacher-lesson-${lesson.id}`"
                            :disabled="previewReadOnly || reviewSubmittingKey !== null"
                            @click="submitForReview('lesson', lesson.source)"
                          >
                            {{ submitReviewLabel('lesson', lesson.id, lesson.status) }}
                          </button>
                          <button
                            class="btn btn-ghost btn-sm"
                            type="button"
                            :aria-describedby="`teacher-lesson-${lesson.id}`"
                            :disabled="
                              !lesson.source ||
                              previewReadOnly ||
                              lessonBusyId === lesson.id ||
                              isTeacherEditLocked(lesson.status)
                            "
                            :aria-busy="lessonBusyId === lesson.id"
                            @click="archiveLesson(lesson.source ?? undefined)"
                          >
                            {{ lessonArchiveLabel(lesson.id) }}
                          </button>
                        </div>
                      </div>
                      <ContentReviewNotice
                        v-if="lesson.status === 'REJECTED'"
                        :status="lesson.status"
                        :rejection-reason="lesson.rejectionReason"
                      />
                    </li>
                  </ol>
                </article>
              </div>
            </section>
          </section>

          <section v-else-if="activeSurfaceTab === 'resources'" class="panel teacher-content-card">
            <div class="teacher-content-section-head">
              <h2 class="teacher-content-subtitle">{{ t('teacherContent.recordedResources') }}</h2>
              <button
                class="btn btn-secondary btn-sm"
                type="button"
                :disabled="previewReadOnly"
                @click="openResourceBuilder()"
              >
                <IconGlyph name="upload" :size="16" />
                <span>{{ t('teacherContent.uploadResource') }}</span>
              </button>
            </div>

            <div class="form-grid two teacher-content-filters">
              <div class="field">
                <label class="field-label" for="teacher-resource-lesson">{{ t('teacherContent.lesson') }}</label>
                <select id="teacher-resource-lesson" v-model.number="resourceLessonId" class="select">
                  <option :value="0">{{ t('teacherContent.selectLesson') }}</option>
                  <option v-for="lesson in lessonOptions" :key="lesson.id" :value="lesson.id">
                    {{ lesson.title }}
                  </option>
                </select>
              </div>

              <div class="field">
                <label class="field-label" for="teacher-resource-summary">{{ t('teacherContent.summary') }}</label>
                <input id="teacher-resource-summary" :value="resourceSummaryLabel" class="input" readonly />
              </div>
            </div>

            <StatePanel
              v-if="loadingResourceInsights"
              tone="loading"
              :title="t('teacherContent.loadingResources')"
              :message="t('teacherContent.loadingResourcesHint')"
              :skeleton="false"
              compact
            />

            <div v-else-if="uploadedResourceVideos.length" class="teacher-module-list">
              <article v-for="video in uploadedResourceVideos" :key="video.id" class="teacher-module-card">
                <div class="teacher-content-row">
                  <span class="teacher-content-index">{{ video.sortOrder }}</span>
                  <div class="teacher-content-row-main">
                    <strong :id="`teacher-resource-${video.id}`">{{ video.title }}</strong>
                    <div class="teacher-content-row-meta">
                      <span>{{ sourceTypeLabel(video.sourceType) }}</span>
                      <span aria-hidden="true" class="teacher-content-dot" />
                      <span>{{ formatDuration(video.durationSeconds) }}</span>
                    </div>
                  </div>
                  <div class="list-action-row teacher-content-row-actions">
                    <button
                      class="btn btn-secondary btn-sm"
                      type="button"
                      :aria-describedby="`teacher-resource-${video.id}`"
                      :disabled="previewReadOnly"
                      @click="openResourceBuilder(video)"
                    >
                      {{ t('teacherContent.edit') }}
                    </button>
                    <button
                      class="btn btn-ghost btn-sm"
                      type="button"
                      :aria-describedby="`teacher-resource-${video.id}`"
                      :disabled="previewReadOnly || archivingResourceId === video.id"
                      :aria-busy="archivingResourceId === video.id"
                      @click="archiveRecordedResource(video.id)"
                    >
                      {{ resourceArchiveLabel(video.id) }}
                    </button>
                  </div>
                </div>

                <div class="teacher-overview-grid is-compact">
                  <div class="teacher-overview-copy">
                    <h3 class="teacher-content-label">{{ t('teacherContent.resourceDetails') }}</h3>
                    <p>{{ descriptionLabel(video.description) }}</p>
                  </div>

                  <dl class="teacher-overview-meta">
                    <div>
                      <dt>{{ t('teacherContent.active') }}</dt>
                      <dd>{{ yesNoLabel(video.active) }}</dd>
                    </div>
                    <div>
                      <dt>{{ t('teacherContent.updatedLabel') }}</dt>
                      <dd>{{ formatDate(video.updatedAt, shortDateOptions) }}</dd>
                    </div>
                    <div class="teacher-overview-meta-wide">
                      <dt>{{ t('teacherContent.open') }}</dt>
                      <dd>{{ video.externalVideoUrl || video.streamUrl || t('teacherContent.storedFile') }}</dd>
                    </div>
                  </dl>
                </div>
              </article>
            </div>

            <StatePanel
              v-else
              icon="play"
              :title="t('teacherContent.noRecordings')"
              :message="t('teacherContent.noRecordingsHint')"
              compact
            />

            <section class="teacher-content-section" aria-labelledby="teacher-watch-progress-title">
              <div class="teacher-content-section-head">
                <h3 id="teacher-watch-progress-title" class="teacher-content-subtitle">
                  {{ t('teacherContent.watchProgress') }}
                </h3>
                <span class="tag">{{ t('teacherContent.trackedViews', { count: lessonVideoProgress.length }) }}</span>
              </div>

              <div v-if="lessonVideoProgress.length" class="teacher-module-list">
                <article
                  v-for="progressRow in lessonVideoProgress"
                  :key="`${progressRow.studentId}-${progressRow.recordedVideoId}`"
                  class="teacher-content-row teacher-content-row--card"
                >
                  <span class="teacher-content-index is-wide">{{ clampPercent(progressRow.progressPercentage) }}%</span>
                  <div class="teacher-content-row-main">
                    <strong>{{ progressRow.studentName }}</strong>
                    <div class="teacher-content-row-meta">
                      <span>{{ progressRow.recordedVideoTitle }}</span>
                      <span aria-hidden="true" class="teacher-content-dot" />
                      <span>
                        {{
                          t('teacherContent.watched', {
                            watched: formatDuration(progressRow.watchedSeconds),
                            total: formatDuration(progressRow.totalSeconds),
                          })
                        }}
                      </span>
                      <span aria-hidden="true" class="teacher-content-dot" />
                      <span
                        >{{ t('teacherContent.lastWatched') }}: {{ lastWatchedLabel(progressRow.lastWatchedAt) }}</span
                      >
                    </div>
                  </div>
                  <div class="list-action-row teacher-content-row-actions">
                    <StatusPill
                      :label="videoProgressStatusLabel(progressRow.completed)"
                      :tone="videoProgressStatusTone(progressRow.completed)"
                    />
                  </div>
                </article>
              </div>

              <StatePanel
                v-else
                icon="history"
                :title="t('teacherContent.noWatchHistory')"
                :message="t('teacherContent.noWatchHistoryHint')"
                compact
              />
            </section>
          </section>

          <section v-else-if="activeSurfaceTab === 'activity-types'" class="panel teacher-content-card">
            <div class="teacher-content-section-head">
              <h2 class="teacher-content-subtitle">{{ t('teacherContent.quizBuilder') }}</h2>
              <button
                class="btn btn-secondary btn-sm"
                type="button"
                :disabled="previewReadOnly"
                @click="openQuizBuilder()"
              >
                <IconGlyph name="plus" :size="14" />
                {{ t('teacherContent.createQuiz') }}
              </button>
            </div>

            <div class="form-grid two teacher-content-filters">
              <div class="field">
                <label class="field-label" for="teacher-quiz-lesson">{{ t('teacherContent.lesson') }}</label>
                <select id="teacher-quiz-lesson" v-model.number="quizLessonId" class="select">
                  <option :value="0">{{ t('teacherContent.selectLesson') }}</option>
                  <option v-for="lesson in lessonOptions" :key="lesson.id" :value="lesson.id">
                    {{ lesson.title }}
                  </option>
                </select>
              </div>

              <div class="field">
                <label class="field-label" for="teacher-quiz-summary">{{ t('teacherContent.summary') }}</label>
                <input id="teacher-quiz-summary" :value="quizSummaryLabel" class="input" readonly />
              </div>
            </div>

            <StatePanel
              v-if="loadingQuizzes"
              tone="loading"
              :title="t('teacherContent.loadingQuizzes')"
              :message="t('teacherContent.loadingQuizzesHint')"
              :skeleton="false"
              compact
            />

            <div v-else-if="quizList.length" class="teacher-module-list">
              <article
                v-for="quiz in quizList"
                :key="quiz.id"
                class="teacher-module-card"
                :class="{ 'is-selected': quiz.id === selectedQuiz?.id }"
              >
                <div class="teacher-content-row">
                  <span class="teacher-content-index">{{ quiz.questions.length }}</span>
                  <div class="teacher-content-row-main">
                    <strong :id="`teacher-quiz-${quiz.id}`">{{ quiz.title }}</strong>
                    <div class="teacher-content-row-meta">
                      <ContentStatusBadge :status="quiz.contentStatus" :show-reason="false" />
                      <LearningStageTag :stage="quiz.learningStage" show-unset />
                    </div>
                  </div>
                  <div class="list-action-row teacher-content-row-actions">
                    <button
                      class="btn btn-ghost btn-sm"
                      type="button"
                      :aria-describedby="`teacher-quiz-${quiz.id}`"
                      :aria-pressed="quiz.id === selectedQuiz?.id"
                      @click="selectQuiz(quiz.id)"
                    >
                      {{ t('teacherContent.open') }}
                    </button>
                    <button
                      class="btn btn-secondary btn-sm"
                      type="button"
                      :aria-describedby="`teacher-quiz-${quiz.id}`"
                      :disabled="previewReadOnly || isTeacherEditLocked(quiz.contentStatus)"
                      @click="openQuizBuilder(quiz)"
                    >
                      {{ t('teacherContent.edit') }}
                    </button>
                    <button
                      v-if="canSubmitForReview(quiz.contentStatus)"
                      class="btn btn-outline btn-sm"
                      type="button"
                      :aria-describedby="`teacher-quiz-${quiz.id}`"
                      :disabled="previewReadOnly || reviewSubmittingKey !== null || !quiz.questions.length"
                      :title="quiz.questions.length ? undefined : t('teacherContent.quizSubmitHint')"
                      data-testid="submit-quiz-review"
                      @click="submitForReview('quiz', quiz)"
                    >
                      {{ submitReviewLabel('quiz', quiz.id, quiz.contentStatus) }}
                    </button>
                    <button
                      class="btn btn-ghost btn-sm"
                      type="button"
                      :aria-describedby="`teacher-quiz-${quiz.id}`"
                      :disabled="previewReadOnly || quizArchiveId === quiz.id || quiz.contentStatus === 'SUBMITTED'"
                      :aria-busy="quizArchiveId === quiz.id"
                      @click="archiveQuiz(quiz.id)"
                    >
                      {{ quizArchiveLabel(quiz.id) }}
                    </button>
                  </div>
                </div>

                <ContentReviewNotice
                  v-if="quiz.contentStatus === 'REJECTED'"
                  :status="quiz.contentStatus"
                  :rejection-reason="quiz.rejectionReason"
                />
                <p v-if="canSubmitForReview(quiz.contentStatus) && !quiz.questions.length" class="field-hint">
                  {{ t('teacherContent.quizSubmitHint') }}
                </p>

                <div class="teacher-overview-grid is-compact">
                  <div class="teacher-overview-copy">
                    <h3 class="teacher-content-label">{{ quiz.lessonTitle }}</h3>
                    <p>{{ descriptionLabel(quiz.description) }}</p>
                  </div>

                  <dl class="teacher-overview-meta">
                    <div>
                      <dt>{{ t('teacherContent.questions') }}</dt>
                      <dd>{{ quiz.questions.length }}</dd>
                    </div>
                    <div>
                      <dt>{{ t('teacherContent.points') }}</dt>
                      <dd>{{ formatNumber(quiz.totalPoints) }}</dd>
                    </div>
                    <div>
                      <dt>{{ t('teacherContent.timeLimit') }}</dt>
                      <dd>{{ quizTimeLimitLabel(quiz.timeLimitMinutes) }}</dd>
                    </div>
                  </dl>
                </div>
              </article>
            </div>

            <StatePanel
              v-else
              icon="quiz"
              :title="t('teacherContent.noQuizzes')"
              :message="t('teacherContent.noQuizzesHint')"
              compact
            />

            <template v-if="selectedQuiz">
              <section class="teacher-content-section" aria-labelledby="teacher-selected-questions-title">
                <div class="teacher-content-section-head">
                  <h3 id="teacher-selected-questions-title" class="teacher-content-subtitle">
                    {{ t('teacherContent.selectedQuestions') }}
                  </h3>
                  <button
                    class="btn btn-secondary btn-sm"
                    type="button"
                    :disabled="previewReadOnly || selectedQuizLocked"
                    @click="openQuestionBuilder()"
                  >
                    <IconGlyph name="plus" :size="14" />
                    {{ t('teacherContent.addQuestion') }}
                  </button>
                </div>

                <div v-if="selectedQuiz.questions.length" class="teacher-module-list">
                  <article v-for="question in selectedQuiz.questions" :key="question.id" class="teacher-module-card">
                    <div class="teacher-content-row">
                      <span class="teacher-content-index">{{ question.displayOrder }}</span>
                      <div class="teacher-content-row-main">
                        <strong :id="`teacher-question-${question.id}`">{{ question.questionText }}</strong>
                        <div class="teacher-content-row-meta">
                          <span class="tag">{{ questionTypeLabel(question.questionType) }}</span>
                          <span>{{ t('teacherContent.points') }}: {{ question.points }}</span>
                          <span aria-hidden="true" class="teacher-content-dot" />
                          <span>{{ t('teacherContent.options') }}: {{ question.options.length }}</span>
                        </div>
                      </div>
                      <div class="list-action-row teacher-content-row-actions">
                        <button
                          class="btn btn-secondary btn-sm"
                          type="button"
                          :aria-describedby="`teacher-question-${question.id}`"
                          :disabled="previewReadOnly || selectedQuizLocked"
                          @click="openQuestionBuilder(question)"
                        >
                          {{ t('teacherContent.edit') }}
                        </button>
                        <button
                          class="btn btn-danger-soft btn-sm"
                          type="button"
                          :aria-describedby="`teacher-question-${question.id}`"
                          :disabled="previewReadOnly || selectedQuizLocked || questionDeleteId === question.id"
                          :aria-busy="questionDeleteId === question.id"
                          @click="deleteQuestion(question.id)"
                        >
                          {{ questionDeleteLabel(question.id) }}
                        </button>
                      </div>
                    </div>

                    <div class="teacher-question-answer">
                      <h4 class="teacher-content-label">{{ t('teacherContent.answer') }}</h4>
                      <p>{{ questionAnswerLabel(question) }}</p>
                      <p v-if="question.explanation">{{ question.explanation }}</p>
                      <p v-if="question.hint" data-testid="quiz-question-hint">
                        {{ t('teacherContent.hintLine', { hint: question.hint }) }}
                      </p>
                    </div>

                    <ol v-if="question.options.length" class="teacher-lesson-list">
                      <li
                        v-for="option in question.options"
                        :key="option.id"
                        class="teacher-content-row teacher-content-row--nested"
                      >
                        <span class="teacher-content-index">{{ option.displayOrder }}</span>
                        <div class="teacher-content-row-main">
                          <strong>{{ option.optionText }}</strong>
                        </div>
                        <div class="list-action-row teacher-content-row-actions">
                          <StatusPill
                            :label="questionOptionStatusLabel(option.correctOption)"
                            :tone="questionOptionStatusTone(option.correctOption)"
                          />
                        </div>
                      </li>
                    </ol>
                  </article>
                </div>

                <StatePanel
                  v-else
                  icon="quiz"
                  :title="t('teacherContent.noQuestions')"
                  :message="t('teacherContent.noQuestionsHint')"
                  compact
                />
              </section>

              <section class="teacher-content-section" aria-labelledby="teacher-attempts-title">
                <div class="teacher-content-section-head">
                  <h3 id="teacher-attempts-title" class="teacher-content-subtitle">
                    {{ t('teacherContent.recentAttempts') }}
                  </h3>
                  <button
                    class="btn btn-ghost btn-sm"
                    type="button"
                    :aria-busy="loadingQuizAttempts"
                    @click="loadQuizAttempts(selectedQuiz.id)"
                  >
                    <IconGlyph name="refresh" :size="16" />
                    <span>{{ t('teacherContent.refreshAttempts') }}</span>
                  </button>
                </div>

                <StatePanel
                  v-if="loadingQuizAttempts"
                  tone="loading"
                  :title="t('teacherContent.loadingAttempts')"
                  :message="t('teacherContent.loadingAttemptsHint')"
                  :skeleton="false"
                  compact
                />

                <div v-else-if="quizAttempts.length" class="teacher-module-list">
                  <article
                    v-for="attempt in quizAttempts"
                    :key="attempt.attemptId"
                    class="teacher-content-row teacher-content-row--card"
                  >
                    <span class="teacher-content-index is-wide">{{ attemptPercent(attempt) }}%</span>
                    <div class="teacher-content-row-main">
                      <strong>{{ attempt.studentName }}</strong>
                      <div class="teacher-content-row-meta">
                        <span>{{ attempt.score }} / {{ attempt.maxScore }}</span>
                        <span aria-hidden="true" class="teacher-content-dot" />
                        <span data-testid="quiz-attempt-hints">
                          {{ t('teacherContent.attemptHintsUsed', attemptHintCount(attempt.hintsUsed)) }}
                        </span>
                        <span aria-hidden="true" class="teacher-content-dot" />
                        <span>{{ formatDate(attempt.submittedAt, shortDateOptions) }}</span>
                      </div>
                    </div>
                  </article>
                </div>

                <StatePanel
                  v-else
                  icon="history"
                  :title="t('teacherContent.noAttempts')"
                  :message="t('teacherContent.noAttemptsHint')"
                  compact
                />
              </section>
            </template>

            <section class="teacher-content-section" aria-labelledby="teacher-activity-types-title">
              <div class="teacher-content-section-head">
                <h3 id="teacher-activity-types-title" class="teacher-content-subtitle">
                  {{ t('teacherContent.supportedTypes') }}
                </h3>
                <RouterLink class="btn btn-secondary btn-sm" :to="{ name: 'teacher-activities' }">
                  {{ t('teacherContent.manageActivities') }}
                </RouterLink>
              </div>
              <ul class="teacher-activity-grid">
                <li v-for="tool in activityTypes" :key="tool.title" class="teacher-activity-card">
                  <span class="teacher-activity-icon" aria-hidden="true"
                    ><IconGlyph :name="tool.icon" :size="20"
                  /></span>
                  <div class="teacher-activity-copy">
                    <strong>{{ tool.title }}</strong>
                    <span>{{ tool.copy }}</span>
                  </div>
                </li>
              </ul>
            </section>
          </section>

          <section v-else-if="activeSurfaceTab === 'categories'" class="panel teacher-content-card">
            <div class="teacher-content-section-head">
              <h2 class="teacher-content-subtitle">{{ t('teacherContent.learningCategories') }}</h2>
              <span class="tag">{{ t('teacherContent.available', { count: categories.length }) }}</span>
            </div>

            <ul v-if="categories.length" class="teacher-module-list">
              <li
                v-for="category in categories"
                :key="category.id"
                class="teacher-content-row teacher-content-row--card"
              >
                <span class="teacher-content-index">{{ category.id }}</span>
                <div class="teacher-content-row-main">
                  <strong>{{ category.name }}</strong>
                  <p>{{ descriptionLabel(category.description, t('teacherContent.noDescription')) }}</p>
                </div>
              </li>
            </ul>

            <StatePanel
              v-else
              icon="rewards"
              :title="t('teacherContent.noCategories')"
              :message="t('teacherContent.noCategoriesHint')"
              compact
            />
          </section>

          <section v-else class="panel teacher-content-card">
            <div class="teacher-content-section-head">
              <h2 class="teacher-content-subtitle">{{ t('teacherContent.learningLevels') }}</h2>
              <span class="tag">{{ t('teacherContent.available', { count: levels.length }) }}</span>
            </div>

            <ul v-if="levels.length" class="teacher-module-list">
              <li v-for="level in levels" :key="level.id" class="teacher-content-row teacher-content-row--card">
                <span class="teacher-content-index">{{ level.id }}</span>
                <div class="teacher-content-row-main">
                  <strong>{{ level.name }}</strong>
                  <p>{{ descriptionLabel(level.description, t('teacherContent.noDescription')) }}</p>
                </div>
              </li>
            </ul>

            <StatePanel
              v-else
              icon="star"
              :title="t('teacherContent.noLevels')"
              :message="t('teacherContent.noLevelsHint')"
              compact
            />
          </section>
        </div>

        <aside ref="builderPanel" class="panel teacher-builder-sidebar" aria-labelledby="teacher-builder-title">
          <div class="card-section-head teacher-builder-header">
            <div class="card-section-title">
              <span class="media-badge small" aria-hidden="true"><IconGlyph name="content" /></span>
              <div class="card-section-copy">
                <h2 id="teacher-builder-title" class="card-title">{{ builderHeading }}</h2>
                <span class="muted">{{ builderDescription }}</span>
              </div>
            </div>
          </div>

          <DataNotice v-if="previewReadOnly" :info="readOnlyMessage" :info-title="readOnlyTitle" />

          <button
            class="btn btn-secondary btn-block"
            type="button"
            :disabled="previewReadOnly"
            @click="openBuilderForActiveSurface"
          >
            {{ builderLaunchLabel }}
          </button>

          <div v-if="activeBuilder" class="tabs teacher-builder-tabs" role="tablist" :aria-label="builderHeading">
            <button
              v-for="tab in builderTabs"
              :id="`teacher-builder-tab-${tab.key}`"
              :key="tab.key"
              class="tab-button"
              type="button"
              role="tab"
              :aria-selected="activeBuilder === tab.key"
              aria-controls="teacher-builder-form"
              @click="tab.open"
            >
              {{ tab.label }}
            </button>
          </div>

          <div
            v-if="activeBuilder"
            id="teacher-builder-form"
            class="teacher-builder-forms"
            role="tabpanel"
            :aria-labelledby="`teacher-builder-tab-${activeBuilder}`"
          >
            <p class="field-hint teacher-builder-required-note">
              <span class="required-mark" aria-hidden="true">*</span> {{ t('teacherContent.requiredNote') }}
            </p>

            <p
              v-if="actionError"
              id="teacher-builder-feedback"
              class="inline-feedback is-error teacher-builder-feedback"
              role="alert"
            >
              {{ actionError }}
            </p>

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
              :disabled="isTeacherEditLocked(editingPackageSource?.contentStatus)"
              @submit="submitPackage"
            >
              <template #header-end>
                <button class="btn btn-ghost btn-sm" type="button" @click="resetPackageForm">
                  {{ t('teacherContent.clear') }}
                </button>
              </template>

              <ContentReviewNotice
                v-if="editingPackageSource"
                :status="editingPackageSource.contentStatus"
                :rejection-reason="editingPackageSource.rejectionReason"
              />

              <div class="field">
                <label class="field-label" for="teacher-course-title">
                  {{ t('teacherContent.courseTitle') }}<span class="required-mark" aria-hidden="true">*</span>
                </label>
                <input
                  id="teacher-course-title"
                  v-model="packageForm.title"
                  class="input"
                  maxlength="200"
                  aria-required="true"
                  :aria-invalid="builderFieldInvalid('courseTitleRequired')"
                  :aria-describedby="
                    builderFieldInvalid('courseTitleRequired') ? 'teacher-builder-feedback' : undefined
                  "
                />
              </div>

              <div class="field">
                <label class="field-label" for="teacher-course-description">
                  {{ t('teacherContent.descriptionLabel') }}
                </label>
                <textarea
                  id="teacher-course-description"
                  v-model="packageForm.description"
                  class="textarea"
                  maxlength="2000"
                />
              </div>

              <div class="form-grid two">
                <div class="field">
                  <label class="field-label" for="teacher-course-level">
                    {{ t('teacherContent.level') }}<span class="required-mark" aria-hidden="true">*</span>
                  </label>
                  <select
                    id="teacher-course-level"
                    v-model.number="packageForm.levelId"
                    class="select"
                    aria-required="true"
                  >
                    <option :value="0">{{ t('teacherContent.selectLevel') }}</option>
                    <option v-for="level in levels" :key="level.id" :value="level.id">{{ level.name }}</option>
                  </select>
                </div>

                <div class="field">
                  <label class="field-label" for="teacher-course-category">
                    {{ t('teacherContent.category') }}<span class="required-mark" aria-hidden="true">*</span>
                  </label>
                  <select
                    id="teacher-course-category"
                    v-model.number="packageForm.categoryId"
                    class="select"
                    aria-required="true"
                  >
                    <option :value="0">{{ t('teacherContent.selectCategory') }}</option>
                    <option v-for="category in categories" :key="category.id" :value="category.id">
                      {{ category.name }}
                    </option>
                  </select>
                </div>

                <div class="field">
                  <label class="field-label" for="teacher-course-learning-type">
                    {{ t('teacherContent.learningType') }}
                  </label>
                  <select id="teacher-course-learning-type" v-model="packageForm.learningType" class="select">
                    <option value="LIVE">{{ t('teacherContent.live') }}</option>
                    <option value="RECORDED">{{ t('teacherContent.recorded') }}</option>
                    <option value="HYBRID">{{ t('teacherContent.hybrid') }}</option>
                  </select>
                </div>

                <div class="field">
                  <label class="field-label" for="teacher-course-cover">{{ t('teacherContent.coverUrl') }}</label>
                  <input
                    id="teacher-course-cover"
                    v-model="packageForm.coverImageUrl"
                    class="input"
                    type="url"
                    maxlength="255"
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
              :disabled="isTeacherEditLocked(editingModuleSource?.contentStatus)"
              @submit="submitModule"
            >
              <template #header-end>
                <button class="btn btn-ghost btn-sm" type="button" @click="resetModuleForm">
                  {{ t('teacherContent.clear') }}
                </button>
              </template>

              <ContentReviewNotice
                v-if="editingModuleSource"
                :status="editingModuleSource.contentStatus"
                :rejection-reason="editingModuleSource.rejectionReason"
              />

              <div class="field">
                <label class="field-label" for="teacher-module-title">
                  {{ t('teacherContent.moduleTitle') }}<span class="required-mark" aria-hidden="true">*</span>
                </label>
                <input
                  id="teacher-module-title"
                  v-model="moduleForm.title"
                  class="input"
                  maxlength="200"
                  aria-required="true"
                  :aria-invalid="builderFieldInvalid('moduleTitleRequired')"
                  :aria-describedby="
                    builderFieldInvalid('moduleTitleRequired') ? 'teacher-builder-feedback' : undefined
                  "
                />
              </div>

              <div class="field">
                <label class="field-label" for="teacher-module-description">
                  {{ t('teacherContent.descriptionLabel') }}
                </label>
                <textarea
                  id="teacher-module-description"
                  v-model="moduleForm.description"
                  class="textarea"
                  maxlength="2000"
                />
              </div>

              <div class="form-grid two">
                <div class="field">
                  <label class="field-label" for="teacher-module-level">
                    {{ t('teacherContent.level') }}<span class="required-mark" aria-hidden="true">*</span>
                  </label>
                  <select
                    id="teacher-module-level"
                    v-model.number="moduleForm.levelId"
                    class="select"
                    aria-required="true"
                  >
                    <option :value="0">{{ t('teacherContent.selectLevel') }}</option>
                    <option v-for="level in levels" :key="level.id" :value="level.id">{{ level.name }}</option>
                  </select>
                </div>

                <div class="field">
                  <label class="field-label" for="teacher-module-category">
                    {{ t('teacherContent.category') }}<span class="required-mark" aria-hidden="true">*</span>
                  </label>
                  <select
                    id="teacher-module-category"
                    v-model.number="moduleForm.categoryId"
                    class="select"
                    aria-required="true"
                  >
                    <option :value="0">{{ t('teacherContent.selectCategory') }}</option>
                    <option v-for="category in categories" :key="category.id" :value="category.id">
                      {{ category.name }}
                    </option>
                  </select>
                </div>
              </div>

              <div class="field">
                <label class="field-label" for="teacher-module-cover">{{ t('teacherContent.coverUrl') }}</label>
                <input
                  id="teacher-module-cover"
                  v-model="moduleForm.coverImageUrl"
                  class="input"
                  type="url"
                  maxlength="255"
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
              :disabled="isTeacherEditLocked(editingLessonSource?.contentStatus)"
              @submit="submitLesson"
            >
              <template #header-end>
                <button class="btn btn-ghost btn-sm" type="button" @click="resetLessonForm()">
                  {{ t('teacherContent.clear') }}
                </button>
              </template>

              <ContentReviewNotice
                v-if="editingLessonSource"
                :status="editingLessonSource.contentStatus"
                :rejection-reason="editingLessonSource.rejectionReason"
              />

              <div class="field">
                <label class="field-label" for="teacher-lesson-title">
                  {{ t('teacherContent.lessonTitle') }}<span class="required-mark" aria-hidden="true">*</span>
                </label>
                <input
                  id="teacher-lesson-title"
                  v-model="lessonForm.title"
                  class="input"
                  maxlength="200"
                  aria-required="true"
                  :aria-invalid="builderFieldInvalid('lessonTitleRequired')"
                  :aria-describedby="
                    builderFieldInvalid('lessonTitleRequired') ? 'teacher-builder-feedback' : undefined
                  "
                />
              </div>

              <div class="field">
                <label class="field-label" for="teacher-lesson-description">
                  {{ t('teacherContent.descriptionLabel') }}
                </label>
                <textarea
                  id="teacher-lesson-description"
                  v-model="lessonForm.description"
                  class="textarea"
                  maxlength="1000"
                />
              </div>

              <div class="form-grid two">
                <div class="field">
                  <label class="field-label" for="teacher-lesson-module">
                    {{ t('teacherContent.module') }}<span class="required-mark" aria-hidden="true">*</span>
                  </label>
                  <select
                    id="teacher-lesson-module"
                    v-model.number="lessonForm.moduleId"
                    class="select"
                    aria-required="true"
                    :aria-invalid="builderFieldInvalid('selectModuleError')"
                    :aria-describedby="
                      builderFieldInvalid('selectModuleError') ? 'teacher-builder-feedback' : undefined
                    "
                  >
                    <option :value="0">{{ t('teacherContent.selectModule') }}</option>
                    <option v-for="module in liveModuleOptions" :key="module.id" :value="module.id">
                      {{ module.title }}
                    </option>
                  </select>
                </div>

                <div class="field">
                  <label class="field-label" for="teacher-lesson-learning-type">
                    {{ t('teacherContent.learningType') }}
                  </label>
                  <select id="teacher-lesson-learning-type" v-model="lessonForm.learningType" class="select">
                    <option value="LIVE">{{ t('teacherContent.live') }}</option>
                    <option value="RECORDED">{{ t('teacherContent.recorded') }}</option>
                    <option value="HYBRID">{{ t('teacherContent.hybrid') }}</option>
                  </select>
                </div>

                <div class="field">
                  <label class="field-label" for="teacher-lesson-level">
                    {{ t('teacherContent.level') }}<span class="required-mark" aria-hidden="true">*</span>
                  </label>
                  <select
                    id="teacher-lesson-level"
                    v-model.number="lessonForm.levelId"
                    class="select"
                    aria-required="true"
                  >
                    <option :value="0">{{ t('teacherContent.selectLevel') }}</option>
                    <option v-for="level in levels" :key="level.id" :value="level.id">{{ level.name }}</option>
                  </select>
                </div>

                <div class="field">
                  <label class="field-label" for="teacher-lesson-category">
                    {{ t('teacherContent.category') }}<span class="required-mark" aria-hidden="true">*</span>
                  </label>
                  <select
                    id="teacher-lesson-category"
                    v-model.number="lessonForm.categoryId"
                    class="select"
                    aria-required="true"
                  >
                    <option :value="0">{{ t('teacherContent.selectCategory') }}</option>
                    <option v-for="category in categories" :key="category.id" :value="category.id">
                      {{ category.name }}
                    </option>
                  </select>
                </div>

                <div class="field">
                  <label class="field-label" for="teacher-lesson-minutes">
                    {{ t('teacherContent.estimatedMinutes') }}
                  </label>
                  <input
                    id="teacher-lesson-minutes"
                    v-model.number="lessonForm.estimatedDurationMinutes"
                    class="input"
                    type="number"
                    min="0"
                  />
                </div>

                <div class="field">
                  <label class="field-label" for="teacher-lesson-cover">{{ t('teacherContent.coverUrl') }}</label>
                  <input
                    id="teacher-lesson-cover"
                    v-model="lessonForm.coverImageUrl"
                    class="input"
                    type="url"
                    maxlength="255"
                  />
                </div>
              </div>

              <div class="field">
                <label class="field-label" for="teacher-lesson-content">{{ t('teacherContent.basicContent') }}</label>
                <textarea
                  id="teacher-lesson-content"
                  v-model="lessonForm.basicContent"
                  class="textarea teacher-builder-textarea-lg"
                  maxlength="10000"
                />
              </div>
            </ActionFormCard>

            <ActionFormCard
              v-else-if="activeBuilder === 'resource'"
              :title="
                editingResourceId ? t('teacherContent.editRecordingTitle') : t('teacherContent.uploadRecordingTitle')
              "
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
                <button class="btn btn-ghost btn-sm" type="button" @click="resetResourceForm(resourceLessonId)">
                  {{ t('teacherContent.clear') }}
                </button>
              </template>

              <div class="field">
                <label class="field-label" for="teacher-recording-lesson">
                  {{ t('teacherContent.lesson') }}<span class="required-mark" aria-hidden="true">*</span>
                </label>
                <select
                  id="teacher-recording-lesson"
                  v-model.number="resourceForm.lessonId"
                  class="select"
                  :disabled="Boolean(editingResourceId)"
                  aria-required="true"
                  :aria-invalid="builderFieldInvalid('selectLessonRecording')"
                  :aria-describedby="
                    builderFieldInvalid('selectLessonRecording') ? 'teacher-builder-feedback' : undefined
                  "
                >
                  <option :value="0">{{ t('teacherContent.selectLesson') }}</option>
                  <option v-for="lesson in lessonOptions" :key="lesson.id" :value="lesson.id">
                    {{ lesson.title }}
                  </option>
                </select>
              </div>

              <div class="field">
                <label class="field-label" for="teacher-recording-package">
                  {{ t('teacherContent.packageOptional') }}
                </label>
                <select id="teacher-recording-package" v-model.number="resourceForm.packageId" class="select">
                  <option :value="0">{{ t('teacherContent.noPackageLink') }}</option>
                  <option v-for="item in livePackageOptions" :key="item.id" :value="item.id">{{ item.title }}</option>
                </select>
              </div>

              <div class="field">
                <label class="field-label" for="teacher-recording-title">
                  {{ t('teacherContent.titleLabel') }}<span class="required-mark" aria-hidden="true">*</span>
                </label>
                <input
                  id="teacher-recording-title"
                  v-model="resourceForm.title"
                  class="input"
                  maxlength="200"
                  aria-required="true"
                  :aria-invalid="builderFieldInvalid('recordingTitleRequired')"
                  :aria-describedby="
                    builderFieldInvalid('recordingTitleRequired') ? 'teacher-builder-feedback' : undefined
                  "
                />
              </div>

              <div class="field">
                <label class="field-label" for="teacher-recording-description">
                  {{ t('teacherContent.descriptionLabel') }}
                </label>
                <textarea
                  id="teacher-recording-description"
                  v-model="resourceForm.description"
                  class="textarea"
                  maxlength="1000"
                />
              </div>

              <div class="form-grid two">
                <div class="field">
                  <label class="field-label" for="teacher-recording-source">{{ t('teacherContent.sourceType') }}</label>
                  <select id="teacher-recording-source" v-model="resourceForm.sourceType" class="select">
                    <option value="EXTERNAL_URL">{{ t('teacherContent.externalSource') }}</option>
                    <option value="UPLOADED_FILE">{{ t('teacherContent.uploadSource') }}</option>
                  </select>
                </div>

                <div class="field">
                  <label class="field-label" for="teacher-recording-duration">
                    {{ t('teacherContent.durationSeconds') }}
                  </label>
                  <input
                    id="teacher-recording-duration"
                    v-model.number="resourceForm.durationSeconds"
                    class="input"
                    type="number"
                    min="0"
                  />
                </div>
              </div>

              <div v-if="resourceForm.sourceType === 'EXTERNAL_URL'" class="field">
                <label class="field-label" for="teacher-recording-url">
                  {{ t('teacherContent.externalVideoUrl') }}<span class="required-mark" aria-hidden="true">*</span>
                </label>
                <input
                  id="teacher-recording-url"
                  v-model="resourceForm.externalVideoUrl"
                  class="input"
                  type="url"
                  maxlength="500"
                  aria-required="true"
                  :aria-invalid="builderFieldInvalid('externalUrlRequired')"
                  :aria-describedby="
                    builderFieldInvalid('externalUrlRequired') ? 'teacher-builder-feedback' : undefined
                  "
                />
              </div>

              <div v-else class="field">
                <label class="field-label" for="teacher-recording-file">
                  {{ t('teacherContent.videoFile') }}<span class="required-mark" aria-hidden="true">*</span>
                </label>
                <input
                  id="teacher-recording-file"
                  ref="resourceFileInput"
                  class="input"
                  type="file"
                  accept="video/*"
                  aria-required="true"
                  :aria-invalid="builderFieldInvalid('fileRequired')"
                  :aria-describedby="builderFieldInvalid('fileRequired') ? 'teacher-builder-feedback' : undefined"
                />
              </div>
            </ActionFormCard>

            <template v-else-if="activeBuilder === 'quiz'">
              <TeacherAiDraftPanel
                mode="quiz"
                :disabled="previewReadOnly && !auth.isPreviewSession"
                @quiz-draft="applyQuizDraft"
                @forbidden="markApprovalLocked"
              />

              <ActionFormCard
                :title="editingQuizId ? t('teacherContent.editQuizTitle') : t('teacherContent.createQuiz')"
                :subtitle="t('teacherContent.quizFormHint')"
                icon="practice"
                :submit-label="
                  editingQuizId ? t('teacherContent.saveQuizChanges') : t('teacherContent.createQuizAction')
                "
                :submitting-label="t('teacherContent.saving')"
                :submitting="submitting.quiz"
                :disabled="isTeacherEditLocked(editingQuizSource?.contentStatus)"
                @submit="submitQuiz"
              >
                <template #header-end>
                  <button class="btn btn-ghost btn-sm" type="button" @click="resetQuizForm(quizLessonId)">
                    {{ t('teacherContent.clear') }}
                  </button>
                </template>

                <ContentReviewNotice
                  v-if="editingQuizSource"
                  :status="editingQuizSource.contentStatus"
                  :rejection-reason="editingQuizSource.rejectionReason"
                />

                <div class="field">
                  <label class="field-label" for="teacher-quiz-form-lesson">
                    {{ t('teacherContent.lesson') }}<span class="required-mark" aria-hidden="true">*</span>
                  </label>
                  <select
                    id="teacher-quiz-form-lesson"
                    v-model.number="quizForm.lessonId"
                    class="select"
                    :disabled="Boolean(editingQuizId)"
                    aria-required="true"
                    :aria-invalid="builderFieldInvalid('selectLessonQuiz')"
                    :aria-describedby="builderFieldInvalid('selectLessonQuiz') ? 'teacher-builder-feedback' : undefined"
                  >
                    <option :value="0">{{ t('teacherContent.selectLesson') }}</option>
                    <option v-for="lesson in quizLessonOptions" :key="lesson.id" :value="lesson.id">
                      {{ lesson.title }}
                    </option>
                  </select>
                </div>

                <div class="field">
                  <label class="field-label" for="teacher-quiz-title">
                    {{ t('teacherContent.quizTitle') }}<span class="required-mark" aria-hidden="true">*</span>
                  </label>
                  <input
                    id="teacher-quiz-title"
                    v-model="quizForm.title"
                    class="input"
                    maxlength="200"
                    aria-required="true"
                    :aria-invalid="builderFieldInvalid('quizTitleRequired')"
                    :aria-describedby="
                      builderFieldInvalid('quizTitleRequired') ? 'teacher-builder-feedback' : undefined
                    "
                  />
                </div>

                <div class="field">
                  <label class="field-label" for="teacher-quiz-description">
                    {{ t('teacherContent.descriptionLabel') }}
                  </label>
                  <textarea
                    id="teacher-quiz-description"
                    v-model="quizForm.description"
                    class="textarea"
                    maxlength="1000"
                  />
                </div>

                <div class="form-grid two">
                  <div class="field">
                    <label class="field-label" for="teacher-quiz-time-limit">
                      {{ t('teacherContent.timeLimitMinutes') }}
                    </label>
                    <input
                      id="teacher-quiz-time-limit"
                      v-model.number="quizForm.timeLimitMinutes"
                      class="input"
                      type="number"
                      min="0"
                    />
                  </div>

                  <div class="field">
                    <label class="field-label" for="teacher-quiz-stage">{{ t('governance.stageLabel') }}</label>
                    <select
                      id="teacher-quiz-stage"
                      v-model="quizForm.learningStage"
                      class="select"
                      aria-describedby="teacher-quiz-stage-hint"
                      data-testid="quiz-learning-stage"
                    >
                      <option value="">{{ t('governance.stageNotSet') }}</option>
                      <option v-for="stage in LEARNING_STAGES" :key="stage" :value="stage">
                        {{ t(`governance.${learningStageMessageKey(stage)}`) }}
                      </option>
                    </select>
                  </div>
                </div>
                <p id="teacher-quiz-stage-hint" class="field-hint">{{ t('governance.stageHint') }}</p>
              </ActionFormCard>

              <section
                v-if="aiQuestionDrafts.length"
                class="teacher-ai-question-drafts"
                aria-labelledby="teacher-ai-question-drafts-title"
                data-testid="ai-question-drafts"
              >
                <div class="teacher-content-section-head">
                  <h3 id="teacher-ai-question-drafts-title" class="teacher-content-label">
                    {{ t('teacherAiDraft.draftQuestions') }}
                  </h3>
                  <AiResultLabel />
                </div>
                <p class="field-hint">{{ t('teacherAiDraft.draftQuestionsHint') }}</p>
                <article
                  v-for="(draft, index) in aiQuestionDrafts"
                  :key="`ai-draft-${index}`"
                  class="teacher-content-row teacher-content-row--nested"
                >
                  <div class="teacher-content-row-main">
                    <strong>{{ draft.questionText }}</strong>
                    <div class="teacher-content-row-meta">
                      <span>{{ draft.options.map((option) => option.optionText).join(' / ') }}</span>
                    </div>
                  </div>
                  <div class="list-action-row teacher-content-row-actions">
                    <button
                      class="btn btn-secondary btn-sm"
                      type="button"
                      :disabled="selectedQuizLocked"
                      @click="loadDraftQuestion(index)"
                    >
                      {{ t('teacherAiDraft.loadIntoBuilder') }}
                    </button>
                    <button class="btn btn-ghost btn-sm" type="button" @click="discardDraftQuestion(index)">
                      {{ t('teacherAiDraft.discard') }}
                    </button>
                  </div>
                </article>
              </section>

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
                :disabled="selectedQuizLocked"
                submit-variant="secondary"
                @submit="submitQuestion"
              >
                <template #header-end>
                  <button class="btn btn-ghost btn-sm" type="button" @click="resetQuestionForm">
                    {{ t('teacherContent.clear') }}
                  </button>
                </template>

                <p v-if="selectedQuizLocked" class="field-hint">{{ t('teacherContent.questionsLocked') }}</p>

                <div class="field">
                  <label class="field-label" for="teacher-question-type">{{ t('teacherContent.questionType') }}</label>
                  <select id="teacher-question-type" v-model="questionForm.questionType" class="select">
                    <option v-for="type in questionTypeOptions" :key="type" :value="type">
                      {{ questionTypeLabel(type) }}
                    </option>
                  </select>
                </div>

                <div class="field">
                  <label class="field-label" for="teacher-question-text">
                    {{ t('teacherContent.questionText') }}<span class="required-mark" aria-hidden="true">*</span>
                  </label>
                  <textarea
                    id="teacher-question-text"
                    v-model="questionForm.questionText"
                    class="textarea"
                    maxlength="2000"
                    aria-required="true"
                    :aria-invalid="builderFieldInvalid('questionRequired')"
                    :aria-describedby="builderFieldInvalid('questionRequired') ? 'teacher-builder-feedback' : undefined"
                  />
                </div>

                <div class="form-grid two">
                  <div class="field">
                    <label class="field-label" for="teacher-question-answer">
                      {{ t('teacherContent.correctAnswer') }}
                    </label>
                    <input
                      id="teacher-question-answer"
                      v-model="questionForm.correctAnswerText"
                      class="input"
                      maxlength="1000"
                    />
                  </div>

                  <div class="field">
                    <label class="field-label" for="teacher-question-points">
                      {{ t('teacherContent.points') }}<span class="required-mark" aria-hidden="true">*</span>
                    </label>
                    <input
                      id="teacher-question-points"
                      v-model.number="questionForm.points"
                      class="input"
                      type="number"
                      min="1"
                      aria-required="true"
                      :aria-invalid="builderFieldInvalid('pointsRequired')"
                      :aria-describedby="builderFieldInvalid('pointsRequired') ? 'teacher-builder-feedback' : undefined"
                    />
                  </div>

                  <div class="field">
                    <label class="field-label" for="teacher-question-order">{{
                      t('teacherContent.displayOrder')
                    }}</label>
                    <input
                      id="teacher-question-order"
                      v-model.number="questionForm.displayOrder"
                      class="input"
                      type="number"
                      min="0"
                    />
                  </div>

                  <div class="field">
                    <label class="field-label" for="teacher-question-explanation">
                      {{ t('teacherContent.explanation') }}
                    </label>
                    <input
                      id="teacher-question-explanation"
                      v-model="questionForm.explanation"
                      class="input"
                      maxlength="2000"
                    />
                  </div>
                </div>

                <div class="field">
                  <label class="field-label" for="teacher-question-hint">{{ t('teacherContent.hintLabel') }}</label>
                  <textarea
                    id="teacher-question-hint"
                    v-model="questionForm.hint"
                    class="textarea teacher-builder-textarea-sm"
                    rows="2"
                    :maxlength="QUESTION_HINT_MAX_LENGTH"
                    :placeholder="t('teacherContent.hintPlaceholder')"
                    aria-describedby="teacher-question-hint-note teacher-question-hint-counter"
                    data-testid="quiz-question-hint-input"
                  />
                  <div class="teacher-hint-meta">
                    <small
                      id="teacher-question-hint-note"
                      class="teacher-hint-note"
                      :class="{ 'is-highlighted': selectedQuizIndependent }"
                      data-testid="quiz-question-hint-note"
                    >
                      {{
                        selectedQuizIndependent
                          ? t('teacherContent.hintIndependentActive')
                          : t('teacherContent.hintIndependentNote')
                      }}
                    </small>
                    <small id="teacher-question-hint-counter" class="field-hint">
                      {{
                        t('teacherContent.hintCounter', { count: questionHintLength, max: QUESTION_HINT_MAX_LENGTH })
                      }}
                    </small>
                  </div>
                </div>

                <fieldset class="form-section teacher-question-options">
                  <legend class="form-section-title">{{ t('teacherContent.answerOptions') }}</legend>

                  <div
                    v-for="(option, index) in questionForm.options"
                    :key="`option-${index}`"
                    class="teacher-question-option"
                  >
                    <span class="teacher-content-index">{{ index + 1 }}</span>
                    <input
                      v-model="option.optionText"
                      class="input teacher-question-option-text"
                      :placeholder="t('teacherContent.optionText')"
                      maxlength="1000"
                      :aria-label="t('teacherContent.optionTextAria', { number: index + 1 })"
                    />
                    <div class="teacher-question-option-controls">
                      <label class="choice">
                        <input v-model="option.correctOption" type="checkbox" />
                        <span>{{ t('teacherContent.correct') }}</span>
                      </label>
                      <input
                        v-model.number="option.displayOrder"
                        class="input teacher-question-option-order"
                        type="number"
                        min="0"
                        :aria-label="t('teacherContent.optionOrderAria', { number: index + 1 })"
                      />
                      <button
                        class="btn btn-ghost btn-sm"
                        type="button"
                        :disabled="questionForm.options.length <= 1"
                        @click="removeQuestionOption(index)"
                      >
                        <IconGlyph name="trash" :size="14" />
                        {{ t('teacherContent.remove') }}
                      </button>
                    </div>
                  </div>

                  <button
                    class="btn btn-secondary btn-sm teacher-question-add-option"
                    type="button"
                    @click="addQuestionOption"
                  >
                    <IconGlyph name="plus" :size="14" />
                    {{ t('teacherContent.addOption') }}
                  </button>
                </fieldset>
              </ActionFormCard>
            </template>
          </div>

          <div class="teacher-builder-reference">
            <h3 class="teacher-content-label">{{ t('teacherContent.quickReference') }}</h3>
            <ul class="teacher-resource-grid">
              <li v-for="item in resourceTypes" :key="item.label" class="teacher-resource-chip">
                <IconGlyph :name="item.icon" :size="16" />
                <span>{{ item.label }}</span>
              </li>
            </ul>
          </div>
        </aside>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
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
import AiResultLabel from '@/components/ai/AiResultLabel.vue'
import TeacherAiDraftPanel from '@/components/ai/TeacherAiDraftPanel.vue'
import CourseSceneThumb from '@/components/common/CourseSceneThumb.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import ContentReviewNotice from '@/components/content/ContentReviewNotice.vue'
import ContentStatusBadge from '@/components/content/ContentStatusBadge.vue'
import LearningStageTag from '@/components/content/LearningStageTag.vue'
import ActionFormCard from '@/components/dashboard/ActionFormCard.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import TeacherApprovalLockPanel from '@/components/teacher/TeacherApprovalLockPanel.vue'
import { useResource } from '@/composables/useResource'
import { useStatusLabels } from '@/composables/useStatusLabels'
import { useTeacherAccess } from '@/composables/useTeacherAccess'
import { categories as demoCategories, levels as demoLevels, teacherCoursePackages, teacherLessons } from '@/demo/data'
import { contentGovernanceMessages } from '@/plugins/contentGovernanceMessages'
import { humanizeEnumValue } from '@/plugins/statusLabelMessages'
import { teacherAiDraftMessages } from '@/plugins/teacherAiDraftMessages'
import { teacherContentMessages } from '@/plugins/teacherContentMessages'
import { useAuthStore } from '@/stores/auth'
import type {
  AiQuizGeneratorResponse,
  ContentStatus,
  CoursePackageResponse,
  LearningStage,
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
import { quizDraftToQuestionDrafts, type QuizQuestionDraft } from '@/utils/aiDraft'
import { resolveApiError } from '@/utils/apiError'
import { canSubmitForReview, editReturnsToReview, isTeacherEditLocked } from '@/utils/contentStatus'
import { clampPercent, formatDate, formatNumber, nonNegativeInteger, ratioPercent } from '@/utils/format'
import { LEARNING_STAGES, learningStageMessageKey } from '@/utils/learningStage'

type SurfaceTab = 'courses' | 'modules' | 'lessons' | 'categories' | 'levels' | 'resources' | 'activity-types'
type BuilderTab = 'package' | 'module' | 'lesson' | 'resource' | 'quiz'
type ReviewKind = 'package' | 'module' | 'lesson' | 'quiz'
type ReviewableItem = { id: number; title: string; contentStatus: ContentStatus }

type DisplayCourse = {
  id: number
  title: string
  subtitle: string
  description: string
  status: ContentStatus
  rejectionReason: string | null
  lessonCount: number
  levelLabel: string
  learningTypeLabel: string
  updatedLabel: string
  packageItems: PackageItemResponse[]
  source: CoursePackageResponse | null
}

type DisplayLessonRow = {
  id: number
  title: string
  order: number
  categoryLabel: string
  status: ContentStatus
  rejectionReason: string | null
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
    en: {
      teacherContent: teacherContentMessages.en,
      governance: contentGovernanceMessages.en,
      teacherAiDraft: teacherAiDraftMessages.en,
    },
    ms: {
      teacherContent: teacherContentMessages.ms,
      governance: contentGovernanceMessages.ms,
      teacherAiDraft: teacherAiDraftMessages.ms,
    },
    'zh-CN': {
      teacherContent: teacherContentMessages['zh-CN'],
      governance: contentGovernanceMessages['zh-CN'],
      teacherAiDraft: teacherAiDraftMessages['zh-CN'],
    },
  },
})
const { statusLabel } = useStatusLabels()
const { teacherApprovalLocked, teacherApprovalTitle, teacherApprovalMessage, guardTeacherRequest, markApprovalLocked } =
  useTeacherAccess()

const packagesResource = useResource(
  () => (teacherApprovalLocked.value ? Promise.resolve([]) : guardTeacherRequest(() => teacherApi.packages())),
  teacherCoursePackages,
)
const lessonsResource = useResource(
  () => (teacherApprovalLocked.value ? Promise.resolve([]) : guardTeacherRequest(() => teacherApi.lessons())),
  teacherLessons,
)
const modulesResource = useResource(
  () => (teacherApprovalLocked.value ? Promise.resolve([]) : guardTeacherRequest(() => teacherApi.modules())),
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
  { title: t('teacherContent.activityStory'), copy: t('teacherContent.activityStoryHint'), icon: 'content' },
])

const resourceTypes = computed(() => [
  { label: t('teacherContent.images'), icon: 'content' },
  { label: t('teacherContent.videos'), icon: 'play' },
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
const quizArchiveId = ref<number | null>(null)
const questionDeleteId = ref<number | null>(null)

const loadingResourceInsights = ref(false)
const uploadedResourceVideos = ref<RecordedLessonVideoResponse[]>([])
const lessonVideoProgress = ref<LessonVideoProgressSummaryResponse[]>([])

const loadingQuizzes = ref(false)
const loadingQuizAttempts = ref(false)
const quizList = ref<TeacherQuizResponse[]>([])
const quizAttempts = ref<TeacherQuizAttemptSummaryResponse[]>([])
const selectedQuizId = ref<number | null>(null)
// Request counters: when the teacher switches lesson / quiz again, a slower earlier response is ignored.
let resourceInsightsRequest = 0
let quizListRequest = 0
let quizAttemptsRequest = 0
/** Quiz to keep selected when saving a quiz moves the list to another lesson (see submitQuiz). */
let pendingPreferredQuizId: number | null = null

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
  coverImageUrl: '',
})

const moduleForm = reactive<TeacherModulePayload>({
  title: '',
  description: '',
  levelId: 0,
  categoryId: 0,
  coverImageUrl: '',
})

const lessonForm = reactive<TeacherLessonPayload>({
  title: '',
  description: '',
  moduleId: 0,
  levelId: 0,
  categoryId: 0,
  learningType: 'HYBRID',
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

const quizForm = reactive<
  Omit<TeacherQuizPayload, 'learningStage'> & { lessonId: number; learningStage: LearningStage | '' }
>({
  lessonId: 0,
  title: '',
  description: '',
  timeLimitMinutes: 0,
  learningStage: '',
})
const aiQuestionDrafts = ref<QuizQuestionDraft[]>([])
const reviewSubmittingKey = ref<string | null>(null)

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
  hint: '',
})

/** Optional per-question hint (D8, <= 1000 chars); students never see it on INDEPENDENT quizzes. */
const QUESTION_HINT_MAX_LENGTH = 1000
const questionHintLength = computed(() => (questionForm.hint ?? '').length)
const selectedQuizIndependent = computed(() => selectedQuiz.value?.learningStage === 'INDEPENDENT')

function attemptPercent(attempt: TeacherQuizAttemptSummaryResponse): number {
  return typeof attempt.percentage === 'number' && Number.isFinite(attempt.percentage)
    ? clampPercent(attempt.percentage)
    : ratioPercent(attempt.score, attempt.maxScore)
}

function attemptHintCount(hintsUsed: number | null | undefined): number {
  return Math.max(0, Math.floor(Number(hintsUsed) || 0))
}

const usingFallback = computed(
  () =>
    packagesResource.usingFallback.value ||
    lessonsResource.usingFallback.value ||
    modulesResource.usingFallback.value ||
    levelsResource.usingFallback.value ||
    categoriesResource.usingFallback.value,
)
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

// With no courses loaded, the course list panel shows the load error and its retry reloads every request, so the
// notice stays quiet (as on the catalog). Otherwise the notice reports whichever request failed.
const pageError = computed(() =>
  packagesResource.error.value && !displayCourses.value.length
    ? null
    : packagesResource.error.value ||
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
    status: item.contentStatus,
    rejectionReason: item.rejectionReason ?? null,
    lessonCount: item.packageItems.length,
    levelLabel: item.level.name,
    learningTypeLabel: learningTypeLabel(item.learningType),
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
      status: 'DRAFT',
      rejectionReason: null,
      lessonCount: 0,
      levelLabel: '--',
      learningTypeLabel: '--',
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
    status: lesson.contentStatus,
    rejectionReason: lesson.rejectionReason ?? null,
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
/** New quizzes need an editable (non-archived) lesson; the read-only filters keep archived lessons visible. */
const quizLessonOptions = computed(() =>
  (lessonsResource.data.value ?? [])
    .filter((lesson) => lesson.contentStatus !== 'ARCHIVED' || lesson.id === quizForm.lessonId)
    .map((lesson) => ({ id: lesson.id, title: lesson.title })),
)

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
const selectedQuizLocked = computed(() => isTeacherEditLocked(selectedQuiz.value?.contentStatus))
const courseLocked = computed(() => isTeacherEditLocked(selectedCourse.value.status))

const resourceSummaryLabel = computed(() => {
  if (!resourceLessonId.value) return t('teacherContent.chooseLessonRecordings')
  return t('teacherContent.resourceSummary', {
    videos: uploadedResourceVideos.value.length,
    progress: lessonVideoProgress.value.length,
  })
})

const quizSummaryLabel = computed(() => {
  if (!quizLessonId.value) return t('teacherContent.chooseLessonQuizzes')
  // A5 #10: count the quizzes' questions (not the attempts of the selected quiz).
  return t('teacherContent.quizSummary', {
    quizzes: quizList.value.length,
    questions: quizList.value.reduce((sum, quiz) => sum + quiz.questions.length, 0),
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

const builderTabs = computed<Array<{ key: BuilderTab; label: string; open: () => void }>>(() => [
  {
    key: 'package',
    label: t('teacherContent.course'),
    open: () => openPackageBuilder(editingPackageSource.value ?? selectedCourse.value.source ?? undefined),
  },
  { key: 'module', label: t('teacherContent.module'), open: () => openModuleBuilder(editingModuleSource.value) },
  { key: 'lesson', label: t('teacherContent.lesson'), open: () => openLessonBuilder(editingLessonSource.value) },
  {
    key: 'resource',
    label: t('teacherContent.resource'),
    open: () => openResourceBuilder(editingResourceSource.value),
  },
  { key: 'quiz', label: t('teacherContent.quiz'), open: () => openQuizBuilder(editingQuizSource.value) },
])

/**
 * On one-column layouts the builder sits below the workspace, so bring it into view when a form is opened from
 * the workspace (not when the teacher is already working inside the builder, e.g. after saving).
 */
const builderPanel = ref<HTMLElement | null>(null)

function revealBuilder(): void {
  if (typeof window === 'undefined' || !window.matchMedia?.('(max-width: 900px)').matches) return
  if (builderPanel.value?.contains(document.activeElement)) return
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  void nextTick(() =>
    builderPanel.value?.scrollIntoView({ behavior: reduceMotion ? 'auto' : 'smooth', block: 'start' }),
  )
}

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
      resourceInsightsRequest++
      loadingResourceInsights.value = false
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
    quizAttemptsRequest++
    loadingQuizAttempts.value = false
    quizAttempts.value = []
    resetQuestionForm()
    if (lessonId > 0 && !lessonsResource.usingFallback.value) {
      void loadQuizzesForLesson(lessonId, pendingPreferredQuizId)
    } else {
      quizListRequest++
      loadingQuizzes.value = false
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

function packageItemTypeLabel(type: PackageItemType): string {
  return type === 'MODULE' ? t('teacherContent.module') : t('teacherContent.lesson')
}

function sourceTypeLabel(type: string): string {
  if (type === 'EXTERNAL_URL') return t('teacherContent.externalSource')
  if (type === 'UPLOADED_FILE') return t('teacherContent.uploadSource')
  return humanizeEnumValue(type)
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

function quizArchiveLabel(quizId: number): string {
  return quizArchiveId.value === quizId ? t('teacherContent.working') : t('teacherContent.archive')
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

function questionTypeLabel(type: string): string {
  return statusLabel('questionType', type)
}

function submitReviewLabel(kind: ReviewKind, id: number, status: ContentStatus): string {
  if (reviewSubmittingKey.value === `${kind}:${id}`) return t('governance.submitting')
  return status === 'REJECTED' ? t('governance.resubmitForReview') : t('governance.submitForReview')
}

/**
 * Review-state guard for every teacher save (D24): SUBMITTED / ARCHIVED items are read-only, and saving a
 * PUBLISHED item needs confirmation because it returns to SUBMITTED and is hidden from students.
 */
function confirmEditAllowed(item: ReviewableItem | null | undefined): boolean {
  if (!item) return true
  if (isTeacherEditLocked(item.contentStatus)) {
    actionSuccess.value = null
    actionError.value =
      item.contentStatus === 'SUBMITTED' ? t('governance.underReviewLocked') : t('governance.archivedLocked')
    return false
  }
  if (editReturnsToReview(item.contentStatus)) {
    return window.confirm(t('governance.confirmEditPublished', { title: item.title }))
  }
  return true
}

function quizSubmitProblem(quiz: TeacherQuizResponse): string | null {
  if (!quiz.questions.length) return t('teacherContent.quizNeedsQuestion')
  const optionQuestionWithoutSingleAnswer = quiz.questions.some(
    (question) =>
      question.options.length > 0 && question.options.filter((option) => Boolean(option.correctOption)).length !== 1,
  )
  if (optionQuestionWithoutSingleAnswer) return t('teacherContent.quizNeedsOneCorrect')
  const totalPoints = quiz.questions.reduce((sum, question) => sum + Math.max(0, question.points || 0), 0)
  if (totalPoints <= 0 && (quiz.totalPoints ?? 0) <= 0) return t('teacherContent.quizNeedsPoints')
  return null
}

async function submitForReview(kind: ReviewKind, item: ReviewableItem): Promise<void> {
  if (reviewSubmittingKey.value) return
  if (!ensureLiveWrite()) return
  if (!canSubmitForReview(item.contentStatus)) return
  if (kind === 'quiz') {
    const problem = quizSubmitProblem(item as TeacherQuizResponse)
    if (problem) {
      actionSuccess.value = null
      actionError.value = problem
      return
    }
  }
  if (!window.confirm(t('governance.confirmSubmit', { title: item.title }))) return

  clearActionFeedback()
  reviewSubmittingKey.value = `${kind}:${item.id}`
  try {
    switch (kind) {
      case 'package':
        await guardTeacherRequest(() => teacherApi.submitPackage(item.id))
        await packagesResource.reload()
        break
      case 'module':
        await guardTeacherRequest(() => teacherApi.submitModule(item.id))
        await modulesResource.reload()
        break
      case 'lesson':
        await guardTeacherRequest(() => teacherApi.submitLesson(item.id))
        await lessonsResource.reload()
        break
      case 'quiz': {
        const response = await guardTeacherRequest(() => teacherApi.submitQuiz(item.id))
        await loadQuizzesForLesson(response.lessonId, response.id)
        break
      }
    }
    actionSuccess.value = t('governance.submittedSuccess', { title: item.title })
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('governance.submitError'))
  } finally {
    reviewSubmittingKey.value = null
  }
}

/** Fills the quiz form and queues AI draft questions for the teacher to edit; nothing is saved (D6). */
function applyQuizDraft(draft: AiQuizGeneratorResponse): void {
  clearActionFeedback()
  if (!editingQuizId.value || !quizForm.title.trim()) {
    quizForm.title = draft.title
  }
  if (!(quizForm.description ?? '').trim() && draft.topic) {
    quizForm.description = draft.topic
  }
  aiQuestionDrafts.value = quizDraftToQuestionDrafts(draft)
}

function loadDraftQuestion(index: number): void {
  const draft = aiQuestionDrafts.value[index]
  if (!draft) return
  clearActionFeedback()
  editingQuestionId.value = null
  questionForm.questionType = draft.questionType
  questionForm.questionText = draft.questionText
  questionForm.correctAnswerText = draft.options.length ? '' : draft.correctAnswerText
  questionForm.points = draft.points
  questionForm.displayOrder = selectedQuiz.value?.questions.length ?? 0
  questionForm.explanation = draft.explanation
  questionForm.hint = ''
  questionForm.options = draft.options.length
    ? draft.options.map((option) => ({ ...option }))
    : defaultQuestionOptions()
  aiQuestionDrafts.value = aiQuestionDrafts.value.filter((_, draftIndex) => draftIndex !== index)
  actionSuccess.value = t('teacherAiDraft.loadedIntoBuilder')
}

function discardDraftQuestion(index: number): void {
  aiQuestionDrafts.value = aiQuestionDrafts.value.filter((_, draftIndex) => draftIndex !== index)
}

function courseThumbVariant(id: number): 'panda' | 'people' | 'pagoda' | 'travel' {
  const thumbs = ['panda', 'people', 'pagoda', 'travel'] as const
  return thumbs[Math.max(id - 1, 0) % thumbs.length]
}

/** True while the builder feedback shows this validation message, so the field it is about is marked invalid. */
function builderFieldInvalid(messageKey: string): boolean {
  return actionError.value === t(`teacherContent.${messageKey}`)
}

function ensureLiveWrite(): boolean {
  if (!previewReadOnly.value) return true
  actionError.value = t('teacherContent.readOnlyFeature')
  actionSuccess.value = null
  return false
}

function clearActionFeedback(): void {
  actionError.value = null
  actionSuccess.value = null
}

function seedSharedContentFields<T extends { levelId: number; categoryId: number }>(target: T): void {
  if (!selectedCourse.value.source) return
  target.levelId = selectedCourse.value.source.level.id
  target.categoryId = selectedCourse.value.source.category.id
}

function resetPackageForm(): void {
  editingPackageId.value = null
  packageForm.title = ''
  packageForm.description = ''
  packageForm.levelId = selectedCourse.value.source?.level.id ?? 0
  packageForm.categoryId = selectedCourse.value.source?.category.id ?? 0
  packageForm.learningType = selectedCourse.value.source?.learningType ?? 'HYBRID'
  packageForm.coverImageUrl = selectedCourse.value.source?.coverImageUrl ?? ''
}

function resetModuleForm(): void {
  editingModuleId.value = null
  moduleForm.title = ''
  moduleForm.description = ''
  moduleForm.levelId = 0
  moduleForm.categoryId = 0
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
  quizForm.learningStage = ''
}

function resetQuestionForm(): void {
  editingQuestionId.value = null
  questionForm.questionType = 'MULTIPLE_CHOICE'
  questionForm.questionText = ''
  questionForm.correctAnswerText = ''
  questionForm.points = 1
  questionForm.displayOrder = selectedQuiz.value?.questions.length ?? 0
  questionForm.explanation = ''
  questionForm.hint = ''
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
  revealBuilder()
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
  packageForm.coverImageUrl = item.coverImageUrl ?? ''
}

function openModuleBuilder(item?: LearningModuleResponse | null, preserveFeedback = false): void {
  activeSurfaceTab.value = 'modules'
  activeBuilder.value = 'module'
  revealBuilder()
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
  moduleForm.coverImageUrl = item.coverImageUrl ?? ''
}

function openLessonBuilder(item?: LessonResponse | null, moduleId = 0, preserveFeedback = false): void {
  activeSurfaceTab.value = 'lessons'
  activeBuilder.value = 'lesson'
  revealBuilder()
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
  lessonForm.estimatedDurationMinutes = item.estimatedDurationMinutes ?? 0
  lessonForm.basicContent = item.basicContent ?? ''
  lessonForm.coverImageUrl = item.coverImageUrl ?? ''
}

function openResourceBuilder(item?: RecordedLessonVideoResponse | null): void {
  activeSurfaceTab.value = 'resources'
  activeBuilder.value = 'resource'
  revealBuilder()
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
  revealBuilder()
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
  quizForm.learningStage = item.learningStage ?? ''
  selectQuiz(item.id)
}

function openQuestionBuilder(question?: TeacherQuizQuestionResponse | null): void {
  activeSurfaceTab.value = 'activity-types'
  activeBuilder.value = 'quiz'
  revealBuilder()
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
  questionForm.hint = question.hint ?? ''
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

async function loadResourceInsights(lessonId: number): Promise<void> {
  const request = ++resourceInsightsRequest
  loadingResourceInsights.value = true
  try {
    const [videos, progressRows] = await Promise.all([
      teacherApi.recordedVideos(lessonId),
      teacherApi.lessonVideoProgress(lessonId),
    ])
    if (request !== resourceInsightsRequest) return
    uploadedResourceVideos.value = videos
    lessonVideoProgress.value = progressRows
  } catch (reason) {
    if (request !== resourceInsightsRequest) return
    actionError.value = resolveApiError(reason, t('teacherContent.loadResourcesError'))
  } finally {
    if (request === resourceInsightsRequest) loadingResourceInsights.value = false
  }
}

async function loadQuizzesForLesson(lessonId: number, preferredQuizId?: number | null): Promise<void> {
  const request = ++quizListRequest
  loadingQuizzes.value = true
  try {
    const items = await teacherApi.lessonQuizzes(lessonId)
    if (request !== quizListRequest) return
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
      quizAttemptsRequest++
      loadingQuizAttempts.value = false
      quizAttempts.value = []
    }
  } catch (reason) {
    if (request === quizListRequest) actionError.value = resolveApiError(reason, t('teacherContent.loadQuizzesError'))
  } finally {
    if (request === quizListRequest) loadingQuizzes.value = false
  }
}

async function loadQuizAttempts(quizId: number): Promise<void> {
  const request = ++quizAttemptsRequest
  loadingQuizAttempts.value = true
  try {
    const attempts = await teacherApi.quizAttempts(quizId)
    if (request !== quizAttemptsRequest) return
    quizAttempts.value = attempts
    selectedQuizId.value = quizId
  } catch (reason) {
    if (request === quizAttemptsRequest)
      actionError.value = resolveApiError(reason, t('teacherContent.loadAttemptsError'))
  } finally {
    if (request === quizAttemptsRequest) loadingQuizAttempts.value = false
  }
}

function selectQuiz(quizId: number): void {
  if (selectedQuizId.value !== quizId) {
    selectedQuizId.value = quizId
    quizAttempts.value = []
    // A question being edited belongs to the previous quiz; saving it under this quiz would edit the wrong one.
    resetQuestionForm()
  }
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
  if (!selectedCourse.value.source || !ensureLiveWrite()) return
  if (!confirmTeacherContentMutation(t('teacherContent.confirmArchiveCourse', { title: selectedCourse.value.title })))
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
  if (!item || !ensureLiveWrite()) return
  if (!confirmTeacherContentMutation(t('teacherContent.confirmArchiveModule', { title: item.title }))) return

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
  if (!item || !ensureLiveWrite()) return
  if (!confirmTeacherContentMutation(t('teacherContent.confirmArchiveLesson', { title: item.title }))) return

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
  if (!ensureLiveWrite()) return
  if (packageItemForm.itemId <= 0) {
    actionError.value = t('teacherContent.choosePackageItem')
    actionSuccess.value = null
    return
  }
  if (!confirmEditAllowed(selectedCourse.value.source)) return

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
  if (!selectedCourse.value.source || !ensureLiveWrite()) return
  // Removing an item is an edit: locked while in review / archived, and a PUBLISHED course returns to review (D24).
  if (!confirmEditAllowed(selectedCourse.value.source)) return
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
  if (!ensureLiveWrite()) return
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
  if (!confirmEditAllowed(editingPackageId.value ? editingPackageSource.value : null)) return

  clearActionFeedback()
  submitting.package = true
  try {
    const isEditing = Boolean(editingPackageId.value)
    const payload: TeacherPackagePayload = {
      ...packageForm,
      title: packageForm.title.trim(),
      description: (packageForm.description ?? '').trim() || null,
      coverImageUrl: (packageForm.coverImageUrl ?? '').trim() || null,
    }

    const response = editingPackageId.value
      ? await teacherApi.updatePackage(editingPackageId.value, payload)
      : await teacherApi.createPackage(payload)

    await packagesResource.reload()
    selectedPackageId.value = response.id
    if (isEditing) {
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
  if (!ensureLiveWrite()) return
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
  if (!confirmEditAllowed(editingModuleId.value ? editingModuleSource.value : null)) return

  clearActionFeedback()
  submitting.module = true
  try {
    const isEditing = Boolean(editingModuleId.value)
    const payload: TeacherModulePayload = {
      ...moduleForm,
      title: moduleForm.title.trim(),
      description: (moduleForm.description ?? '').trim() || null,
      coverImageUrl: (moduleForm.coverImageUrl ?? '').trim() || null,
    }

    const response = editingModuleId.value
      ? await teacherApi.updateModule(editingModuleId.value, payload)
      : await teacherApi.createModule(payload)

    await modulesResource.reload()
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
  if (!ensureLiveWrite()) return
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
  if (!confirmEditAllowed(editingLessonId.value ? editingLessonSource.value : null)) return

  clearActionFeedback()
  submitting.lesson = true
  try {
    const isEditing = Boolean(editingLessonId.value)
    const payload: TeacherLessonPayload = {
      ...lessonForm,
      title: lessonForm.title.trim(),
      description: (lessonForm.description ?? '').trim() || null,
      moduleId: lessonForm.moduleId,
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
  if (!ensureLiveWrite()) return
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
    // Captured up front: changing resourceLessonId below resets the form (and editingResourceId) via its watcher.
    const isEditing = Boolean(editingResourceId.value)
    const formData = new FormData()
    formData.append('title', resourceForm.title.trim())
    formData.append('description', resourceForm.description.trim())
    formData.append('sourceType', resourceForm.sourceType)
    formData.append('sortOrder', String(editingResourceSource.value?.sortOrder ?? uploadedResourceVideos.value.length))
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

    if (resourceLessonId.value !== response.lessonId) {
      // The resourceLessonId watcher reloads the insights for the new lesson.
      resourceLessonId.value = response.lessonId
    } else {
      await loadResourceInsights(response.lessonId)
    }
    actionSuccess.value = isEditing ? t('teacherContent.recordingUpdated') : t('teacherContent.recordingCreated')
    openResourceBuilder(response)
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('teacherContent.saveRecordingError'))
  } finally {
    submitting.resource = false
  }
}

async function archiveRecordedResource(videoId: number): Promise<void> {
  if (archivingResourceId.value) return
  if (!ensureLiveWrite() || resourceLessonId.value <= 0) return
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
  if (!ensureLiveWrite()) return
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
  if (!confirmEditAllowed(editingQuizId.value ? editingQuizSource.value : null)) return

  clearActionFeedback()
  submitting.quiz = true
  try {
    const isEditing = Boolean(editingQuizId.value)
    const payload: TeacherQuizPayload = {
      title: quizForm.title.trim(),
      description: (quizForm.description ?? '').trim() || null,
      timeLimitMinutes: quizForm.timeLimitMinutes && quizForm.timeLimitMinutes > 0 ? quizForm.timeLimitMinutes : 0,
      learningStage: quizForm.learningStage || null,
    }

    const response = editingQuizId.value
      ? await teacherApi.updateQuiz(editingQuizId.value, payload)
      : await teacherApi.createQuiz(quizForm.lessonId, payload)

    // If the lesson changes, the quizLessonId watcher starts a newer list load (superseding the one below);
    // it must also keep the saved quiz selected.
    pendingPreferredQuizId = response.id
    quizLessonId.value = response.lessonId
    await loadQuizzesForLesson(response.lessonId, response.id)
    pendingPreferredQuizId = null
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
  if (!ensureLiveWrite()) return
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
  const hint = (questionForm.hint ?? '').trim()
  if (hint.length > QUESTION_HINT_MAX_LENGTH) {
    actionError.value = t('teacherContent.hintTooLong', { max: QUESTION_HINT_MAX_LENGTH })
    actionSuccess.value = null
    return
  }
  if (!confirmEditAllowed(selectedQuiz.value)) return

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
      hint: hint || null,
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

async function archiveQuiz(quizId: number): Promise<void> {
  if (quizArchiveId.value) return
  if (!ensureLiveWrite()) return
  if (!confirmTeacherContentMutation(t('teacherContent.confirmArchiveQuiz'))) return

  clearActionFeedback()
  quizArchiveId.value = quizId
  try {
    await teacherApi.archiveQuiz(quizId)
    if (selectedQuizId.value === quizId) {
      resetQuestionForm()
      selectedQuizId.value = null
    }
    await loadQuizzesForLesson(quizLessonId.value)
    actionSuccess.value = t('teacherContent.quizArchived')
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('teacherContent.archiveQuizError'))
  } finally {
    quizArchiveId.value = null
  }
}

async function deleteQuestion(questionId: number): Promise<void> {
  if (questionDeleteId.value) return
  if (!ensureLiveWrite()) return
  // Deleting a question of a PUBLISHED quiz sends the quiz back to review, like any other edit (D24).
  if (!confirmEditAllowed(selectedQuiz.value)) return
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

<style scoped>
/* Page-level tabs: one scrollable row on every width (seven sections never wrap into two rows). */
.teacher-content-tabs {
  flex-wrap: nowrap;
  overflow-x: auto;
  scrollbar-width: none;
}

.teacher-content-tabs::-webkit-scrollbar {
  display: none;
}

.teacher-content-tabs .tab-button {
  flex: 0 0 auto;
}

/* Workbench: courses | workspace | builder. ≤ 1200px: courses become a strip above workspace + builder.
   ≤ 900px: one column (courses, workspace, builder). */
.teacher-content-layout {
  display: grid;
  grid-template-columns: minmax(200px, 240px) minmax(0, 1fr) minmax(300px, 340px);
  grid-template-areas: 'courses main builder';
  gap: var(--grid-gap);
  align-items: start;
}

.teacher-course-sidebar {
  grid-area: courses;
  display: grid;
  gap: var(--space-3);
  min-width: 0;
  padding: var(--space-4);
}

.teacher-content-main {
  grid-area: main;
  display: grid;
  gap: var(--grid-gap);
  min-width: 0;
}

.teacher-builder-sidebar {
  grid-area: builder;
  position: sticky;
  top: calc(var(--topbar-height) + var(--space-4));
  display: grid;
  gap: var(--space-4);
  min-width: 0;
  max-height: calc(100dvh - var(--topbar-height) - var(--space-8));
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: var(--space-4);
}

/* Course list */
.teacher-course-sidebar-head {
  align-items: center;
}

.teacher-course-list {
  display: grid;
  gap: var(--space-2);
  min-width: 0;
}

.teacher-course-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
  padding: var(--space-2-5);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-card);
  cursor: pointer;
  transition:
    border-color var(--duration-fast) var(--ease-standard),
    background var(--duration-fast) var(--ease-standard);
}

.teacher-course-item:hover {
  border-color: var(--border-interactive);
  background: var(--surface-hover);
}

.teacher-course-item.is-active {
  border-color: var(--color-primary-300);
  background: var(--surface-selected);
}

.teacher-course-copy {
  display: grid;
  gap: var(--space-0-5);
  min-width: 0;
}

.teacher-course-copy strong {
  color: var(--text-primary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-snug);
  overflow-wrap: anywhere;
}

.teacher-course-subtitle,
.teacher-course-meta {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
}

.teacher-course-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-1) var(--space-2);
  margin-top: var(--space-1);
}

/* Workspace card */
.teacher-content-card {
  display: grid;
  gap: var(--space-5);
  min-width: 0;
}

.teacher-content-summary {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: var(--space-4);
}

.teacher-content-summary-copy {
  display: grid;
  gap: var(--space-1-5);
  min-width: 0;
}

.teacher-content-title {
  margin: 0;
  color: var(--text-primary);
  font-family: var(--font-display);
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-tight);
  letter-spacing: var(--letter-spacing-tight);
  overflow-wrap: anywhere;
}

.teacher-content-summary-meta,
.teacher-content-row-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-1) var(--space-2);
  min-width: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.teacher-content-summary-actions {
  grid-column: 1 / -1;
  justify-content: flex-start;
}

.teacher-content-dot {
  width: 4px;
  height: 4px;
  border-radius: var(--radius-round);
  background: var(--color-ink-300);
}

.teacher-content-subtitle,
.teacher-content-label {
  margin: 0;
  color: var(--text-primary);
  font-family: var(--font-display);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-snug);
}

.teacher-content-subtitle {
  font-size: var(--font-size-lg);
}

.teacher-content-label {
  font-size: var(--font-size-base);
}

.teacher-overview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 240px), 1fr));
  gap: var(--space-4) var(--space-6);
  padding-block: var(--space-4);
  border-block: 1px solid var(--border-subtle);
}

.teacher-overview-grid.is-compact {
  padding: var(--space-3) 0 0;
  border-bottom: 0;
}

.teacher-overview-copy {
  display: grid;
  gap: var(--space-1-5);
  min-width: 0;
}

.teacher-overview-copy p,
.teacher-question-answer p,
.teacher-content-row-main p {
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-relaxed);
  overflow-wrap: anywhere;
}

.teacher-overview-meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(96px, 1fr));
  gap: var(--space-3);
  margin: 0;
  align-content: start;
}

.teacher-overview-meta > div {
  display: grid;
  gap: var(--space-0-5);
  min-width: 0;
  padding: var(--space-2-5) var(--space-3);
  border-radius: var(--radius-sm);
  background: var(--surface-sunken);
}

.teacher-overview-meta-wide {
  grid-column: 1 / -1;
}

.teacher-overview-meta dt {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
}

.teacher-overview-meta dd {
  margin: 0;
  color: var(--text-primary);
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-bold);
  overflow-wrap: anywhere;
}

/* Sections and rows */
.teacher-content-section {
  display: grid;
  gap: var(--space-3);
  min-width: 0;
}

.teacher-content-section-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2) var(--space-3);
  min-width: 0;
}

.teacher-content-filters {
  align-items: end;
}

.teacher-module-list,
.teacher-lesson-list,
.teacher-activity-grid,
.teacher-resource-grid {
  margin: 0;
  padding: 0;
  list-style: none;
}

.teacher-module-list {
  display: grid;
  gap: var(--space-2-5);
  min-width: 0;
}

.teacher-module-card {
  display: grid;
  gap: var(--space-3);
  min-width: 0;
  padding: var(--space-3);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-card);
}

.teacher-module-card.is-selected {
  border-color: var(--color-primary-300);
  box-shadow: inset 3px 0 0 var(--color-primary-500);
}

/* A row wraps its actions under the title when there is no room: no media query needed. */
.teacher-content-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2) var(--space-3);
  min-width: 0;
}

.teacher-content-row--card {
  padding: var(--space-3);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-card);
}

.teacher-content-row--nested {
  padding: var(--space-2-5) var(--space-3);
  border-radius: var(--radius-md);
  background: var(--surface-subtle);
}

.teacher-content-row-main {
  display: grid;
  flex: 1 1 200px;
  gap: var(--space-1);
  min-width: 0;
}

.teacher-content-row-main > strong {
  color: var(--text-primary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-snug);
  overflow-wrap: anywhere;
}

.teacher-content-row--module .teacher-content-row-main > strong {
  font-size: var(--font-size-base);
}

.teacher-content-row-actions {
  flex: 0 1 auto;
  margin-left: auto;
}

.teacher-content-index {
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  min-width: var(--control-height-sm);
  height: var(--control-height-sm);
  padding-inline: var(--space-1-5);
  border-radius: var(--radius-sm);
  background: var(--color-primary-50);
  color: var(--color-primary-700);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
}

.teacher-content-index.is-wide {
  min-width: var(--space-12);
}

.teacher-lesson-list {
  display: grid;
  gap: var(--space-1-5);
}

.teacher-lesson-item {
  display: grid;
  gap: var(--space-1-5);
}

.teacher-question-answer {
  display: grid;
  gap: var(--space-1);
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: var(--surface-sunken);
}

/* Add-course-item form (flat, inside the workspace card) */
.teacher-inline-form {
  display: grid;
  gap: var(--space-4);
  padding: var(--space-4);
  border: 1px dashed var(--border-default);
  border-radius: var(--radius-lg);
  background: var(--surface-subtle);
}

.teacher-inline-form-head {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
}

.teacher-inline-form-fields {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3) var(--space-4);
}

.teacher-inline-form-fields > .field {
  flex: 1 1 140px;
}

.teacher-inline-form-fields > .teacher-inline-form-wide {
  flex: 2 1 240px;
}

/* Activity type reference grid */
.teacher-activity-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 220px), 1fr));
  gap: var(--space-2-5);
}

.teacher-activity-card {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  min-width: 0;
  padding: var(--space-3);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-card);
}

.teacher-activity-icon {
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  width: var(--control-height-md);
  height: var(--control-height-md);
  border-radius: var(--radius-md);
  background: var(--color-primary-50);
  color: var(--color-primary-600);
}

.teacher-activity-copy {
  display: grid;
  gap: var(--space-0-5);
  min-width: 0;
}

.teacher-activity-copy strong {
  font-size: var(--font-size-md);
}

.teacher-activity-copy span {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

/* Builder */
.teacher-builder-header .card-title {
  font-size: var(--font-size-base);
}

.teacher-builder-tabs {
  flex-wrap: nowrap;
  width: 100%;
  overflow-x: auto;
  scrollbar-width: none;
}

.teacher-builder-tabs .tab-button {
  flex: 1 0 auto;
  padding-inline: var(--space-2);
  font-size: var(--font-size-sm);
}

.teacher-builder-forms {
  display: grid;
  gap: var(--space-5);
  min-width: 0;
}

/* ActionFormCard renders a .panel; inside the builder the forms sit flat and are separated by dividers. */
.teacher-builder-forms > .panel {
  padding: 0;
  border: 0;
  border-radius: 0;
  box-shadow: none;
}

.teacher-builder-forms > form.panel + form.panel,
.teacher-builder-forms > .teacher-ai-question-drafts + form.panel {
  padding-top: var(--space-5);
  border-top: 1px solid var(--border-subtle);
}

.teacher-builder-required-note {
  margin: 0;
}

.teacher-builder-feedback {
  margin: 0;
}

.teacher-builder-textarea-lg {
  min-height: calc(var(--control-height-lg) * 4);
}

.teacher-builder-textarea-sm {
  min-height: calc(var(--control-height-md) * 2);
}

.teacher-ai-question-drafts {
  display: grid;
  gap: var(--space-2-5);
}

.teacher-hint-meta {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: var(--space-1) var(--space-3);
}

.teacher-hint-note {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

/* Highlighted when the selected quiz is INDEPENDENT: students will not see the hint (D8). */
.teacher-hint-note.is-highlighted {
  padding: var(--space-0-5) var(--space-2);
  border-radius: var(--radius-xs);
  background: var(--color-warning-soft);
  color: var(--color-warning-text);
  font-weight: var(--font-weight-bold);
}

.teacher-question-options {
  gap: var(--space-2-5);
}

.teacher-question-option {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2-5);
  border-radius: var(--radius-md);
  background: var(--surface-subtle);
}

.teacher-question-option-text {
  flex: 1 1 160px;
}

.teacher-question-option-controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  margin-left: auto;
}

.teacher-question-option-order {
  width: calc(var(--control-height-md) * 2);
}

.teacher-question-add-option {
  justify-self: start;
}

.teacher-builder-reference {
  display: grid;
  gap: var(--space-2-5);
  padding-top: var(--space-4);
  border-top: 1px solid var(--border-subtle);
}

.teacher-resource-grid {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.teacher-resource-chip {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1-5);
  min-height: var(--control-height-sm);
  padding: 0 var(--space-3);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-pill);
  background: var(--surface-subtle);
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
}

@media (max-width: 1200px) {
  .teacher-content-layout {
    grid-template-columns: minmax(0, 1fr) minmax(280px, 320px);
    grid-template-areas:
      'courses courses'
      'main builder';
  }

  .teacher-course-sidebar {
    grid-template-columns: minmax(0, 1fr) auto;
    align-items: center;
  }

  .teacher-course-sidebar > :not(.teacher-create-course, .teacher-course-sidebar-head) {
    grid-column: 1 / -1;
  }

  .teacher-course-sidebar-head {
    justify-content: flex-start;
  }

  /* Courses scroll sideways as a strip of cards. */
  .teacher-course-list {
    grid-auto-flow: column;
    grid-auto-columns: minmax(220px, 260px);
    overflow-x: auto;
    padding-bottom: var(--space-1);
    scroll-snap-type: x proximity;
  }

  .teacher-course-item {
    scroll-snap-align: start;
  }
}

@media (max-width: 900px) {
  .teacher-content-layout {
    grid-template-columns: minmax(0, 1fr);
    grid-template-areas:
      'courses'
      'main'
      'builder';
  }

  .teacher-builder-sidebar {
    position: static;
    max-height: none;
    overflow: visible;
    scroll-margin-top: calc(var(--topbar-height) + var(--space-4));
  }
}

@media (max-width: 640px) {
  .teacher-course-sidebar {
    grid-template-columns: minmax(0, 1fr);
  }

  .teacher-content-summary {
    grid-template-columns: minmax(0, 1fr);
  }

  .teacher-content-summary > .teacher-course-thumb {
    display: none;
  }

  /* Actions drop under the title: line them up with the title, not the index badge. */
  .teacher-content-index ~ .teacher-content-row-actions {
    justify-content: flex-start;
    margin-left: calc(var(--control-height-sm) + var(--space-3));
  }
}
</style>
