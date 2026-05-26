<template>
  <div class="page-stack teacher-content-page">
    <PageHeaderBlock
      title="Content Management"
      description="Create, edit, archive, package, and assess your teaching content from one workspace."
      icon="content"
    />

    <DataNotice
      :using-fallback="usingFallback"
      :error="pageError"
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
        @click="activeSurfaceTab = tab.key"
      >
        <IconGlyph :name="tab.icon" :size="18" />
        <span>{{ tab.label }}</span>
      </button>
    </section>

    <section class="teacher-content-layout">
      <aside class="panel teacher-course-sidebar">
        <div class="row-between teacher-sidebar-header">
          <strong>My Courses</strong>
          <button class="student-link-button" type="button" @click="reloadAll">Refresh</button>
        </div>

        <button
          class="btn btn-primary teacher-create-course"
          type="button"
          :disabled="previewReadOnly"
          @click="openPackageBuilder()"
        >
          <span class="teacher-inline-plus">+</span>
          <span>Create New Course</span>
        </button>

        <div class="teacher-course-list">
          <article
            v-for="item in displayCourses"
            :key="item.id"
            class="teacher-course-item"
            :class="{ 'is-active': item.id === selectedCourse.id }"
            @click="selectedPackageId = item.id"
          >
            <CourseSceneThumb class="teacher-course-thumb" :variant="courseThumbVariant(item.id)" />
            <div class="stack teacher-course-copy">
              <strong>{{ item.title }}</strong>
              <span class="muted">{{ item.subtitle }}</span>
              <div class="row teacher-course-meta">
                <StatusPill :label="item.statusLabel" :tone="contentStatusTone(item.statusLabel)" />
                <span class="muted">{{ item.lessonCount }} Items</span>
              </div>
            </div>
          </article>
        </div>

        <button class="teacher-view-all" type="button" @click="showAllCourses">
          <span>View All Courses</span>
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
              <span class="muted">{{ selectedCourse.lessonCount }} Items</span>
              <button
                class="student-link-button"
                type="button"
                :disabled="!selectedCourse.source || previewReadOnly"
                @click="openPackageBuilder(selectedCourse.source ?? undefined)"
              >
                Edit Course
              </button>
              <button
                class="btn btn-secondary"
                type="button"
                :disabled="!selectedCourse.source || previewReadOnly || packageBusy"
                @click="archiveSelectedPackage"
              >
                {{ packageBusy ? 'Working...' : 'Archive' }}
              </button>
            </div>
          </div>

          <div class="teacher-overview-grid">
            <div>
              <strong>Course Overview</strong>
              <p class="muted">{{ selectedCourse.description }}</p>
            </div>

            <div class="teacher-overview-meta">
              <div>
                <span class="muted">Level</span>
                <strong>{{ selectedCourse.levelLabel }}</strong>
              </div>
              <div>
                <span class="muted">Access</span>
                <strong>{{ selectedCourse.accessLabel }}</strong>
              </div>
              <div>
                <span class="muted">Last Updated</span>
                <strong>{{ selectedCourse.updatedLabel }}</strong>
              </div>
            </div>
          </div>

          <div class="row-between teacher-content-section-head">
            <strong>Course Package Items</strong>
            <button
              class="btn btn-secondary teacher-add-module"
              type="button"
              :disabled="!selectedCourse.source || previewReadOnly"
              @click="openPackageBuilder(selectedCourse.source ?? undefined)"
            >
              <span class="teacher-inline-plus">+</span>
              <span>Edit Course</span>
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
                    {{ packageItemRemovingId === item.id ? 'Removing...' : 'Remove' }}
                  </button>
                </div>
              </div>
            </article>
          </div>

          <div v-else class="teacher-builder-helper">
            <strong>No modules or lessons are attached to this course yet.</strong>
            <span class="muted"
              >Use the package item form in the builder to add modules or lessons to the selected course.</span
            >
          </div>

          <form class="panel form-grid" @submit.prevent="submitPackageItem">
            <div class="card-section-head">
              <div class="card-section-title">
                <div class="media-badge small">
                  <IconGlyph name="courses" />
                </div>
                <div class="card-section-copy">
                  <strong>Add Course Item</strong>
                  <span class="muted">Attach modules or lessons to the selected course package.</span>
                </div>
              </div>
            </div>

            <div class="form-grid two">
              <div class="field">
                <label>Item type</label>
                <select v-model="packageItemForm.itemType" class="select" aria-label="Course item type">
                  <option value="LESSON">LESSON</option>
                  <option value="MODULE">MODULE</option>
                </select>
              </div>

              <div class="field">
                <label>Display order</label>
                <input
                  v-model.number="packageItemForm.displayOrder"
                  class="input"
                  type="number"
                  min="0"
                  aria-label="Course item display order"
                />
              </div>
            </div>

            <div class="field">
              <label>Available item</label>
              <select
                v-model.number="packageItemForm.itemId"
                class="select"
                aria-label="Available course item"
                :disabled="!selectedCourse.source || previewReadOnly"
              >
                <option :value="0">Select an item</option>
                <option v-for="item in packageItemOptions" :key="item.id" :value="item.id">{{ item.title }}</option>
              </select>
            </div>

            <button
              class="btn btn-primary"
              type="submit"
              :disabled="!selectedCourse.source || previewReadOnly || submitting.packageItem"
            >
              {{ submitting.packageItem ? 'Saving...' : 'Add to Course' }}
            </button>
          </form>

          <div class="row-between teacher-content-section-head">
            <strong>Modules and Lessons</strong>
            <button
              class="btn btn-secondary teacher-add-module"
              type="button"
              :disabled="previewReadOnly"
              @click="openModuleBuilder()"
            >
              <span class="teacher-inline-plus">+</span>
              <span>Add Module</span>
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
                  <span class="muted">{{ module.lessonCount }} Lessons</span>
                  <button
                    class="student-link-button"
                    type="button"
                    :disabled="!module.source || previewReadOnly"
                    @click="openModuleBuilder(module.source ?? undefined)"
                  >
                    Edit
                  </button>
                  <button
                    class="btn btn-secondary"
                    type="button"
                    :disabled="!module.source || previewReadOnly || moduleBusyId === module.id"
                    @click="archiveModule(module.source ?? undefined)"
                  >
                    {{ moduleBusyId === module.id ? 'Working...' : 'Archive' }}
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
                      Edit
                    </button>
                    <button
                      class="btn btn-secondary"
                      type="button"
                      :disabled="!lesson.source || previewReadOnly || lessonBusyId === lesson.id"
                      @click="archiveLesson(lesson.source ?? undefined)"
                    >
                      {{ lessonBusyId === lesson.id ? 'Working...' : 'Archive' }}
                    </button>
                  </div>
                </div>
              </div>
            </article>
          </div>
        </section>

        <section v-else-if="activeSurfaceTab === 'resources'" class="panel teacher-content-card">
          <div class="row-between teacher-content-section-head">
            <strong>Recorded Lesson Resources</strong>
            <button
              class="btn btn-secondary teacher-resource-upload"
              type="button"
              :disabled="previewReadOnly"
              @click="openResourceBuilder()"
            >
              <span class="teacher-inline-plus">+</span>
              <span>Upload New Resource</span>
            </button>
          </div>

          <div class="form-grid two">
            <div class="field">
              <label>Lesson</label>
              <select v-model.number="resourceLessonId" class="select" aria-label="Recorded resource lesson">
                <option :value="0">Select a lesson</option>
                <option v-for="lesson in lessonOptions" :key="lesson.id" :value="lesson.id">{{ lesson.title }}</option>
              </select>
            </div>

            <div class="field">
              <label>Summary</label>
              <input :value="resourceSummaryLabel" class="input" aria-label="Recorded resource summary" readonly />
            </div>
          </div>

          <div v-if="loadingResourceInsights" class="teacher-builder-helper">
            <strong>Loading lesson resources...</strong>
            <span class="muted">Fetching recordings and learner progress for the selected lesson.</span>
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
                    Edit
                  </button>
                  <button
                    class="btn btn-secondary"
                    type="button"
                    :disabled="previewReadOnly || archivingResourceId === video.id"
                    @click="archiveRecordedResource(video.id)"
                  >
                    {{ archivingResourceId === video.id ? 'Working...' : 'Archive' }}
                  </button>
                </div>
              </div>

              <div class="teacher-overview-grid">
                <div>
                  <strong>Resource Details</strong>
                  <p class="muted">{{ video.description || 'No description provided.' }}</p>
                </div>

                <div class="teacher-overview-meta">
                  <div>
                    <span class="muted">Active</span>
                    <strong>{{ video.active ? 'Yes' : 'No' }}</strong>
                  </div>
                  <div>
                    <span class="muted">Updated</span>
                    <strong>{{ formatDate(video.updatedAt, shortDateOptions) }}</strong>
                  </div>
                  <div>
                    <span class="muted">Open</span>
                    <strong>{{ video.externalVideoUrl || video.streamUrl || 'Stored file' }}</strong>
                  </div>
                </div>
              </div>
            </article>
          </div>

          <div v-else class="teacher-builder-helper">
            <strong>No recorded lessons for this selection yet.</strong>
            <span class="muted"
              >Attach an external URL or uploaded video file from the builder to open this lesson for self-paced
              learning.</span
            >
          </div>

          <div class="row-between teacher-content-section-head">
            <strong>Learner Watch Progress</strong>
            <span class="muted">{{ lessonVideoProgress.length }} tracked views</span>
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
                    :label="progressRow.completed ? 'Completed' : 'Watching'"
                    :tone="progressRow.completed ? 'success' : 'warning'"
                  />
                </div>
              </div>

              <div class="teacher-overview-grid">
                <div>
                  <strong>{{ progressRow.studentEmail }}</strong>
                  <p class="muted">
                    Watched {{ formatDuration(progressRow.watchedSeconds) }} of
                    {{ formatDuration(progressRow.totalSeconds) }}.
                  </p>
                </div>

                <div class="teacher-overview-meta">
                  <div>
                    <span class="muted">Last Watched</span>
                    <strong>{{
                      progressRow.lastWatchedAt ? formatDate(progressRow.lastWatchedAt, shortDateOptions) : '--'
                    }}</strong>
                  </div>
                </div>
              </div>
            </article>
          </div>

          <div v-else class="teacher-builder-helper">
            <strong>No learner watch history yet.</strong>
            <span class="muted">Progress will appear here as soon as students start the recorded videos.</span>
          </div>
        </section>

        <section v-else-if="activeSurfaceTab === 'activity-types'" class="panel teacher-content-card">
          <div class="row-between teacher-content-section-head">
            <strong>Quiz Builder</strong>
            <button
              class="btn btn-secondary teacher-builder-launch"
              type="button"
              :disabled="previewReadOnly"
              @click="openQuizBuilder()"
            >
              <span class="teacher-inline-plus">+</span>
              <span>Create Quiz</span>
            </button>
          </div>

          <div class="form-grid two">
            <div class="field">
              <label>Lesson</label>
              <select v-model.number="quizLessonId" class="select" aria-label="Quiz lesson">
                <option :value="0">Select a lesson</option>
                <option v-for="lesson in lessonOptions" :key="lesson.id" :value="lesson.id">{{ lesson.title }}</option>
              </select>
            </div>

            <div class="field">
              <label>Summary</label>
              <input :value="quizSummaryLabel" class="input" aria-label="Quiz summary" readonly />
            </div>
          </div>

          <div v-if="loadingQuizzes" class="teacher-builder-helper">
            <strong>Loading quizzes...</strong>
            <span class="muted">Reading live quiz drafts, questions, and attempt history for the selected lesson.</span>
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
                  <button class="student-link-button" type="button" @click="selectQuiz(quiz.id)">Open</button>
                  <button
                    class="student-link-button"
                    type="button"
                    :disabled="previewReadOnly"
                    @click="openQuizBuilder(quiz)"
                  >
                    Edit
                  </button>
                  <button
                    class="btn btn-secondary"
                    type="button"
                    :disabled="previewReadOnly || quizDeleteId === quiz.id"
                    @click="deleteQuiz(quiz.id)"
                  >
                    {{ quizDeleteId === quiz.id ? 'Working...' : 'Delete' }}
                  </button>
                </div>
              </div>

              <div class="teacher-overview-grid">
                <div>
                  <strong>{{ quiz.lessonTitle }}</strong>
                  <p class="muted">{{ quiz.description || 'No description provided.' }}</p>
                </div>

                <div class="teacher-overview-meta">
                  <div>
                    <span class="muted">Questions</span>
                    <strong>{{ quiz.questions.length }}</strong>
                  </div>
                  <div>
                    <span class="muted">Points</span>
                    <strong>{{ formatNumber(quiz.totalPoints) }}</strong>
                  </div>
                  <div>
                    <span class="muted">Time Limit</span>
                    <strong>{{ quiz.timeLimitMinutes ? `${quiz.timeLimitMinutes} min` : 'None' }}</strong>
                  </div>
                </div>
              </div>
            </article>
          </div>

          <div v-else class="teacher-builder-helper">
            <strong>No quizzes for this lesson yet.</strong>
            <span class="muted">Create a quiz, then add question items and monitor attempts from this surface.</span>
          </div>

          <template v-if="selectedQuiz">
            <div class="row-between teacher-content-section-head">
              <strong>Selected Quiz Questions</strong>
              <button
                class="btn btn-secondary teacher-add-module"
                type="button"
                :disabled="previewReadOnly"
                @click="openQuestionBuilder()"
              >
                <span class="teacher-inline-plus">+</span>
                <span>Add Question</span>
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
                      Edit
                    </button>
                    <button
                      class="btn btn-secondary"
                      type="button"
                      :disabled="previewReadOnly || questionDeleteId === question.id"
                      @click="deleteQuestion(question.id)"
                    >
                      {{ questionDeleteId === question.id ? 'Working...' : 'Delete' }}
                    </button>
                  </div>
                </div>

                <div class="teacher-overview-grid">
                  <div>
                    <strong>Answer</strong>
                    <p class="muted">
                      {{ question.correctAnswerText || 'Use the options below to mark a correct answer.' }}
                    </p>
                    <p v-if="question.explanation" class="muted">{{ question.explanation }}</p>
                  </div>

                  <div class="teacher-overview-meta">
                    <div>
                      <span class="muted">Points</span>
                      <strong>{{ question.points }}</strong>
                    </div>
                    <div>
                      <span class="muted">Options</span>
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
                        :label="option.correctOption ? 'Correct' : 'Distractor'"
                        :tone="option.correctOption ? 'success' : 'info'"
                      />
                    </div>
                  </div>
                </div>
              </article>
            </div>

            <div v-else class="teacher-builder-helper">
              <strong>No question items yet.</strong>
              <span class="muted"
                >Create the quiz first, then add question text, options, points, and explanations from the
                builder.</span
              >
            </div>

            <div class="row-between teacher-content-section-head">
              <strong>Recent Attempts</strong>
              <button class="student-link-button" type="button" @click="loadQuizAttempts(selectedQuiz.id)">
                Refresh Attempts
              </button>
            </div>

            <div v-if="loadingQuizAttempts" class="teacher-builder-helper">
              <strong>Loading attempts...</strong>
              <span class="muted">Checking recent submissions and scores for this quiz.</span>
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
              <strong>No student attempts yet.</strong>
              <span class="muted">Attempt history will appear here after learners complete the selected quiz.</span>
            </div>
          </template>

          <div class="muted-divider" />

          <div class="stack">
            <strong>Supported Activity Types</strong>
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
            <strong>Learning Categories</strong>
            <span class="muted">{{ categories.length }} available</span>
          </div>

          <div v-if="categories.length" class="teacher-module-list">
            <article v-for="category in categories" :key="category.id" class="teacher-module-card">
              <div class="row-between teacher-module-head">
                <div class="row teacher-module-title-row">
                  <span class="teacher-module-index">{{ category.id }}</span>
                  <strong>{{ category.name }}</strong>
                </div>
              </div>
              <p class="muted">{{ category.description || 'No description available.' }}</p>
            </article>
          </div>

          <div v-else class="teacher-builder-helper">
            <strong>No categories available.</strong>
            <span class="muted">The platform categories endpoint did not return any live records.</span>
          </div>
        </section>

        <section v-else class="panel teacher-content-card">
          <div class="row-between teacher-content-section-head">
            <strong>Learning Levels</strong>
            <span class="muted">{{ levels.length }} available</span>
          </div>

          <div v-if="levels.length" class="teacher-module-list">
            <article v-for="level in levels" :key="level.id" class="teacher-module-card">
              <div class="row-between teacher-module-head">
                <div class="row teacher-module-title-row">
                  <span class="teacher-module-index">{{ level.id }}</span>
                  <strong>{{ level.name }}</strong>
                </div>
              </div>
              <p class="muted">{{ level.description || 'No description available.' }}</p>
            </article>
          </div>

          <div v-else class="teacher-builder-helper">
            <strong>No levels available.</strong>
            <span class="muted">The platform levels endpoint did not return any live records.</span>
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
          <strong>{{ teacherApprovalLocked ? 'Verification pending.' : 'Preview mode is read-only.' }}</strong>
          <span class="muted">
            {{
              teacherApprovalLocked
                ? teacherApprovalMessage
                : 'Sign in as an approved teacher to save content, upload files, package lessons, and manage quizzes.'
            }}
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
            Course
          </button>
          <button
            class="tab-button"
            :class="{ 'is-active': activeBuilder === 'module' }"
            type="button"
            @click="openModuleBuilder(editingModuleSource)"
          >
            Module
          </button>
          <button
            class="tab-button"
            :class="{ 'is-active': activeBuilder === 'lesson' }"
            type="button"
            @click="openLessonBuilder(editingLessonSource)"
          >
            Lesson
          </button>
          <button
            class="tab-button"
            :class="{ 'is-active': activeBuilder === 'resource' }"
            type="button"
            @click="openResourceBuilder(editingResourceSource)"
          >
            Resource
          </button>
          <button
            class="tab-button"
            :class="{ 'is-active': activeBuilder === 'quiz' }"
            type="button"
            @click="openQuizBuilder(editingQuizSource)"
          >
            Quiz
          </button>
        </div>

        <ActionFormCard
          v-if="activeBuilder === 'package'"
          :title="editingPackageId ? 'Edit Course' : 'Create New Course'"
          subtitle="Packages publish directly and can be updated until you archive them."
          icon="courses"
          :submit-label="editingPackageId ? 'Save course changes' : 'Create course'"
          submitting-label="Saving..."
          :submitting="submitting.package"
          @submit="submitPackage"
        >
          <template #header-end>
            <button class="teacher-outline-button" type="button" @click="resetPackageForm">Clear</button>
          </template>

          <div class="field">
            <label>Course title</label>
            <input v-model="packageForm.title" class="input" maxlength="200" aria-label="Course title" />
          </div>

          <div class="field">
            <label>Description</label>
            <textarea
              v-model="packageForm.description"
              class="textarea"
              maxlength="2000"
              aria-label="Course description"
            />
          </div>

          <div class="form-grid two">
            <div class="field">
              <label>Level</label>
              <select v-model.number="packageForm.levelId" class="select" aria-label="Course level">
                <option :value="0">Select a level</option>
                <option v-for="level in levels" :key="level.id" :value="level.id">{{ level.name }}</option>
              </select>
            </div>

            <div class="field">
              <label>Category</label>
              <select v-model.number="packageForm.categoryId" class="select" aria-label="Course category">
                <option :value="0">Select a category</option>
                <option v-for="category in categories" :key="category.id" :value="category.id">
                  {{ category.name }}
                </option>
              </select>
            </div>
          </div>

          <div class="form-grid two">
            <div class="field">
              <label>Learning type</label>
              <select v-model="packageForm.learningType" class="select" aria-label="Course learning type">
                <option value="LIVE">LIVE</option>
                <option value="RECORDED">RECORDED</option>
                <option value="HYBRID">HYBRID</option>
              </select>
            </div>

            <div class="field">
              <label>Access</label>
              <select v-model="packageForm.accessType" class="select" aria-label="Course access type">
                <option value="FREE">FREE</option>
                <option value="PAID">PAID</option>
              </select>
            </div>
          </div>

          <div class="form-grid two">
            <div class="field">
              <label>Price</label>
              <input
                v-model.number="packageForm.price"
                class="input"
                type="number"
                min="0"
                step="0.01"
                aria-label="Course price"
              />
            </div>

            <div class="field">
              <label>Cover image URL</label>
              <input
                v-model="packageForm.coverImageUrl"
                class="input"
                type="url"
                maxlength="255"
                aria-label="Course cover image URL"
              />
            </div>
          </div>
        </ActionFormCard>

        <ActionFormCard
          v-else-if="activeBuilder === 'module'"
          :title="editingModuleId ? 'Edit Module' : 'Add Module'"
          subtitle="Modules remain teacher-owned and editable until archived."
          icon="content"
          :submit-label="editingModuleId ? 'Save module changes' : 'Create module'"
          submitting-label="Saving..."
          :submitting="submitting.module"
          @submit="submitModule"
        >
          <template #header-end>
            <button class="teacher-outline-button" type="button" @click="resetModuleForm">Clear</button>
          </template>

          <div class="field">
            <label>Module title</label>
            <input v-model="moduleForm.title" class="input" maxlength="200" aria-label="Module title" />
          </div>

          <div class="field">
            <label>Description</label>
            <textarea
              v-model="moduleForm.description"
              class="textarea"
              maxlength="2000"
              aria-label="Module description"
            />
          </div>

          <div class="form-grid two">
            <div class="field">
              <label>Level</label>
              <select v-model.number="moduleForm.levelId" class="select" aria-label="Module level">
                <option :value="0">Select a level</option>
                <option v-for="level in levels" :key="level.id" :value="level.id">{{ level.name }}</option>
              </select>
            </div>

            <div class="field">
              <label>Category</label>
              <select v-model.number="moduleForm.categoryId" class="select" aria-label="Module category">
                <option :value="0">Select a category</option>
                <option v-for="category in categories" :key="category.id" :value="category.id">
                  {{ category.name }}
                </option>
              </select>
            </div>
          </div>

          <div class="form-grid two">
            <div class="field">
              <label>Access</label>
              <select v-model="moduleForm.accessType" class="select" aria-label="Module access type">
                <option value="FREE">FREE</option>
                <option value="PAID">PAID</option>
              </select>
            </div>

            <div class="field">
              <label>Price</label>
              <input
                v-model.number="moduleForm.price"
                class="input"
                type="number"
                min="0"
                step="0.01"
                aria-label="Module price"
              />
            </div>
          </div>

          <div class="field">
            <label>Cover image URL</label>
            <input
              v-model="moduleForm.coverImageUrl"
              class="input"
              type="url"
              maxlength="255"
              aria-label="Module cover image URL"
            />
          </div>
        </ActionFormCard>

        <ActionFormCard
          v-else-if="activeBuilder === 'lesson'"
          :title="editingLessonId ? 'Edit Lesson' : 'Create New Lesson'"
          subtitle="Lessons now support the full backend payload, including access, price, and cover metadata."
          icon="learn"
          :submit-label="editingLessonId ? 'Save lesson changes' : 'Create lesson'"
          submitting-label="Saving..."
          :submitting="submitting.lesson"
          @submit="submitLesson"
        >
          <template #header-end>
            <button class="teacher-outline-button" type="button" @click="resetLessonForm()">Clear</button>
          </template>

          <div class="field">
            <label>Lesson title</label>
            <input v-model="lessonForm.title" class="input" maxlength="200" aria-label="Lesson title" />
          </div>

          <div class="field">
            <label>Description</label>
            <textarea
              v-model="lessonForm.description"
              class="textarea"
              maxlength="1000"
              aria-label="Lesson description"
            />
          </div>

          <div class="form-grid two">
            <div class="field">
              <label>Module</label>
              <select v-model.number="lessonForm.moduleId" class="select" aria-label="Lesson module">
                <option :value="0">Select a module</option>
                <option v-for="module in liveModuleOptions" :key="module.id" :value="module.id">
                  {{ module.title }}
                </option>
              </select>
            </div>

            <div class="field">
              <label>Learning type</label>
              <select v-model="lessonForm.learningType" class="select" aria-label="Lesson learning type">
                <option value="LIVE">LIVE</option>
                <option value="RECORDED">RECORDED</option>
                <option value="HYBRID">HYBRID</option>
              </select>
            </div>
          </div>

          <div class="form-grid two">
            <div class="field">
              <label>Level</label>
              <select v-model.number="lessonForm.levelId" class="select" aria-label="Lesson level">
                <option :value="0">Select a level</option>
                <option v-for="level in levels" :key="level.id" :value="level.id">{{ level.name }}</option>
              </select>
            </div>

            <div class="field">
              <label>Category</label>
              <select v-model.number="lessonForm.categoryId" class="select" aria-label="Lesson category">
                <option :value="0">Select a category</option>
                <option v-for="category in categories" :key="category.id" :value="category.id">
                  {{ category.name }}
                </option>
              </select>
            </div>
          </div>

          <div class="form-grid two">
            <div class="field">
              <label>Access</label>
              <select v-model="lessonForm.accessType" class="select" aria-label="Lesson access type">
                <option value="FREE">FREE</option>
                <option value="PAID">PAID</option>
              </select>
            </div>

            <div class="field">
              <label>Price</label>
              <input
                v-model.number="lessonForm.price"
                class="input"
                type="number"
                min="0"
                step="0.01"
                aria-label="Lesson price"
              />
            </div>
          </div>

          <div class="form-grid two">
            <div class="field">
              <label>Estimated duration (minutes)</label>
              <input
                v-model.number="lessonForm.estimatedDurationMinutes"
                class="input"
                type="number"
                min="0"
                aria-label="Lesson estimated duration in minutes"
              />
            </div>

            <div class="field">
              <label>Cover image URL</label>
              <input
                v-model="lessonForm.coverImageUrl"
                class="input"
                type="url"
                maxlength="255"
                aria-label="Lesson cover image URL"
              />
            </div>
          </div>

          <div class="field">
            <label>Basic content</label>
            <textarea v-model="lessonForm.basicContent" class="textarea" aria-label="Lesson basic content" />
          </div>
        </ActionFormCard>

        <ActionFormCard
          v-else-if="activeBuilder === 'resource'"
          :title="editingResourceId ? 'Edit Recorded Lesson' : 'Upload Recorded Lesson'"
          subtitle="Attach either an external stream URL or an uploaded file, and optionally link it to a course package."
          icon="file"
          :submit-label="editingResourceId ? 'Save recording changes' : 'Save recording'"
          submitting-label="Saving..."
          :submitting="submitting.resource"
          @submit="submitRecordedResource"
        >
          <template #header-end>
            <button class="teacher-outline-button" type="button" @click="resetResourceForm(resourceLessonId)">
              Clear
            </button>
          </template>

          <div class="field">
            <label>Lesson</label>
            <select v-model.number="resourceForm.lessonId" class="select" aria-label="Recorded lesson linked lesson">
              <option :value="0">Select a lesson</option>
              <option v-for="lesson in lessonOptions" :key="lesson.id" :value="lesson.id">{{ lesson.title }}</option>
            </select>
          </div>

          <div class="field">
            <label>Course package (optional)</label>
            <select
              v-model.number="resourceForm.packageId"
              class="select"
              aria-label="Recorded lesson linked course package"
            >
              <option :value="0">No package link</option>
              <option v-for="item in livePackageOptions" :key="item.id" :value="item.id">{{ item.title }}</option>
            </select>
          </div>

          <div class="field">
            <label>Title</label>
            <input v-model="resourceForm.title" class="input" maxlength="200" aria-label="Recorded lesson title" />
          </div>

          <div class="field">
            <label>Description</label>
            <textarea
              v-model="resourceForm.description"
              class="textarea"
              maxlength="1000"
              aria-label="Recorded lesson description"
            />
          </div>

          <div class="form-grid two">
            <div class="field">
              <label>Source type</label>
              <select v-model="resourceForm.sourceType" class="select" aria-label="Recorded lesson source type">
                <option value="EXTERNAL_URL">EXTERNAL_URL</option>
                <option value="UPLOADED_FILE">UPLOADED_FILE</option>
              </select>
            </div>

            <div class="field">
              <label>Duration seconds</label>
              <input
                v-model.number="resourceForm.durationSeconds"
                class="input"
                type="number"
                min="0"
                aria-label="Recorded lesson duration in seconds"
              />
            </div>
          </div>

          <div v-if="resourceForm.sourceType === 'EXTERNAL_URL'" class="field">
            <label>External video URL</label>
            <input
              v-model="resourceForm.externalVideoUrl"
              class="input"
              type="url"
              maxlength="2048"
              aria-label="External video URL"
            />
          </div>

          <div v-else class="field">
            <label>Video file</label>
            <input
              ref="resourceFileInput"
              class="input"
              type="file"
              accept="video/*"
              aria-label="Recorded lesson video file"
            />
          </div>
        </ActionFormCard>

        <template v-else-if="activeBuilder === 'quiz'">
          <ActionFormCard
            :title="editingQuizId ? 'Edit Quiz' : 'Create Quiz'"
            subtitle="Each quiz stays linked to a lesson and can be updated or removed from this workspace."
            icon="practice"
            :submit-label="editingQuizId ? 'Save quiz changes' : 'Create quiz'"
            submitting-label="Saving..."
            :submitting="submitting.quiz"
            @submit="submitQuiz"
          >
            <template #header-end>
              <button class="teacher-outline-button" type="button" @click="resetQuizForm(quizLessonId)">Clear</button>
            </template>

            <div class="field">
              <label>Lesson</label>
              <select v-model.number="quizForm.lessonId" class="select" aria-label="Quiz linked lesson">
                <option :value="0">Select a lesson</option>
                <option v-for="lesson in lessonOptions" :key="lesson.id" :value="lesson.id">{{ lesson.title }}</option>
              </select>
            </div>

            <div class="field">
              <label>Quiz title</label>
              <input v-model="quizForm.title" class="input" maxlength="200" aria-label="Quiz title" />
            </div>

            <div class="field">
              <label>Description</label>
              <textarea
                v-model="quizForm.description"
                class="textarea"
                maxlength="1000"
                aria-label="Quiz description"
              />
            </div>

            <div class="field">
              <label>Time limit (minutes)</label>
              <input
                v-model.number="quizForm.timeLimitMinutes"
                class="input"
                type="number"
                min="0"
                aria-label="Quiz time limit in minutes"
              />
            </div>
          </ActionFormCard>

          <ActionFormCard
            :title="editingQuestionId ? 'Edit Question' : 'Add Question'"
            :subtitle="
              selectedQuiz ? `Active quiz: ${selectedQuiz.title}` : 'Create or open a quiz before adding questions.'
            "
            icon="practice"
            :submit-label="editingQuestionId ? 'Save question changes' : 'Add question'"
            submitting-label="Saving..."
            :submitting="submitting.question"
            @submit="submitQuestion"
          >
            <template #header-end>
              <button class="teacher-outline-button" type="button" @click="resetQuestionForm">Clear</button>
            </template>

            <div class="field">
              <label>Question type</label>
              <select v-model="questionForm.questionType" class="select" aria-label="Question type">
                <option v-for="type in questionTypeOptions" :key="type" :value="type">{{ type }}</option>
              </select>
            </div>

            <div class="field">
              <label>Question text</label>
              <textarea
                v-model="questionForm.questionText"
                class="textarea"
                maxlength="1000"
                aria-label="Question text"
              />
            </div>

            <div class="form-grid two">
              <div class="field">
                <label>Correct answer</label>
                <input
                  v-model="questionForm.correctAnswerText"
                  class="input"
                  maxlength="500"
                  aria-label="Correct answer"
                />
              </div>

              <div class="field">
                <label>Points</label>
                <input
                  v-model.number="questionForm.points"
                  class="input"
                  type="number"
                  min="1"
                  aria-label="Question points"
                />
              </div>
            </div>

            <div class="form-grid two">
              <div class="field">
                <label>Display order</label>
                <input
                  v-model.number="questionForm.displayOrder"
                  class="input"
                  type="number"
                  min="0"
                  aria-label="Question display order"
                />
              </div>

              <div class="field">
                <label>Explanation</label>
                <input
                  v-model="questionForm.explanation"
                  class="input"
                  maxlength="1000"
                  aria-label="Question explanation"
                />
              </div>
            </div>

            <div class="stack">
              <div class="row-between teacher-content-section-head">
                <strong>Answer Options</strong>
                <button class="student-link-button" type="button" @click="addQuestionOption">Add Option</button>
              </div>

              <div v-for="(option, index) in questionForm.options" :key="`option-${index}`" class="teacher-lesson-row">
                <div class="row teacher-lesson-main">
                  <span class="teacher-lesson-index">{{ index + 1 }}</span>
                  <input
                    v-model="option.optionText"
                    class="input"
                    placeholder="Option text"
                    maxlength="255"
                    :aria-label="`Answer option ${index + 1} text`"
                  />
                </div>

                <div class="row teacher-lesson-actions">
                  <label class="muted">
                    <input v-model="option.correctOption" type="checkbox" />
                    Correct
                  </label>
                  <input
                    v-model.number="option.displayOrder"
                    class="input"
                    type="number"
                    min="0"
                    :aria-label="`Answer option ${index + 1} display order`"
                  />
                  <button
                    class="btn btn-secondary"
                    type="button"
                    :disabled="questionForm.options.length <= 1"
                    @click="removeQuestionOption(index)"
                  >
                    Remove
                  </button>
                </div>
              </div>
            </div>
          </ActionFormCard>
        </template>

        <div class="muted-divider" />

        <div class="stack">
          <strong>Quick Reference</strong>
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
/**
 * Teacher content authoring hub — courses, modules, lessons,
 * quizzes, materials, video uploads, AI-assisted draft generation.
 *
 * <h2>Why one page</h2>
 * <p>The author workflow benefits from in-place context (the lesson
 * you're editing sits next to its parent module and the course's
 * existing materials). Sub-views are tabs inside this page rather
 * than separate routes so authors can switch without losing draft
 * state.</p>
 *
 * <h2>Publish gating</h2>
 * <p>Publish actions are denied when verification != APPROVED, when
 * any required lesson field is missing, and when the course
 * already has a pending admin review.</p>
 *
 * <h2>AI assistance</h2>
 * <p>Quiz / story / vocabulary generators feed into the AI helper
 * service; the page falls back to deterministic stubs when the AI
 * provider is disabled or returns an error so the author UX never
 * hard-fails.</p>
 */
import { computed, reactive, ref, watch } from 'vue'

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
import { clampPercent, formatDate, formatNumber, nonNegativeInteger, ratioPercent } from '@/utils/format'

type SurfaceTab = 'courses' | 'modules' | 'lessons' | 'categories' | 'levels' | 'resources' | 'activity-types'
type BuilderTab = 'package' | 'module' | 'lesson' | 'resource' | 'quiz'

type DisplayCourse = {
  id: number
  title: string
  subtitle: string
  description: string
  statusLabel: string
  contentStatus: ContentStatus
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

const contentTabs: Array<{ key: SurfaceTab; label: string; icon: string }> = [
  { key: 'courses', label: 'Courses', icon: 'courses' },
  { key: 'modules', label: 'Modules', icon: 'content' },
  { key: 'lessons', label: 'Lessons', icon: 'learn' },
  { key: 'categories', label: 'Categories', icon: 'rewards' },
  { key: 'levels', label: 'Levels', icon: 'star' },
  { key: 'resources', label: 'Resources', icon: 'file' },
  { key: 'activity-types', label: 'Assessments', icon: 'practice' },
]

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

const activityTypes = [
  {
    title: 'Flashcards',
    copy: 'Vocabulary and recognition practice are powered from the shared activity library.',
    icon: 'clip',
  },
  { title: 'Quizzes', copy: 'Teacher-owned quizzes are now fully editable from this page.', icon: 'quiz' },
  { title: 'Matching', copy: 'Matching games can be curated centrally in the admin activity library.', icon: 'fun' },
  { title: 'Listening', copy: 'Listening exercises connect with recorded media and AI support.', icon: 'live' },
  {
    title: 'Tracing',
    copy: 'Character tracing ties into practice progress and worksheet resources.',
    icon: 'practice',
  },
  {
    title: 'Drag and Drop',
    copy: 'Interactive sequence activities are available in the student activity library.',
    icon: 'practice',
  },
]

const resourceTypes = [
  { label: 'Images', icon: 'content' },
  { label: 'Audio', icon: 'live' },
  { label: 'Videos', icon: 'play' },
  { label: 'Documents', icon: 'file' },
]

const previewModuleRows: DisplayModuleRow[] = [
  { id: 1, title: 'Module 1 - Greetings and Introductions', lessonCount: 0, lessons: [], source: null },
  { id: 2, title: 'Module 2 - Numbers and Counting', lessonCount: 0, lessons: [], source: null },
  { id: 3, title: 'Module 3 - Family and Friends', lessonCount: 0, lessons: [], source: null },
]

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

const pageError = computed(
  () =>
    actionError.value ||
    packagesResource.error.value ||
    lessonsResource.error.value ||
    modulesResource.error.value ||
    levelsResource.error.value ||
    categoriesResource.error.value,
)

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
    description: item.description || 'No description available yet.',
    statusLabel: normalizeStatus(item.contentStatus),
    contentStatus: item.contentStatus,
    lessonCount: item.packageItems.length,
    levelLabel: item.level.name,
    accessLabel: item.accessType === 'PAID' ? formatPrice(item.price) : 'Free',
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
      title: 'No course available',
      subtitle: 'Create or select a course',
      description: 'No course data is available right now.',
      statusLabel: 'Draft',
      contentStatus: 'DRAFT',
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
    metaLabel: lesson.estimatedDurationMinutes ? `${lesson.estimatedDurationMinutes} min` : lesson.learningType,
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
    return previewModuleRows.map((module, index) => ({
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
  if (!resourceLessonId.value) return 'Choose a lesson to manage recordings'
  return `${uploadedResourceVideos.value.length} recording(s), ${lessonVideoProgress.value.length} learner progress row(s)`
})

const quizSummaryLabel = computed(() => {
  if (!quizLessonId.value) return 'Choose a lesson to manage quizzes'
  return `${quizList.value.length} quiz(zes), ${quizAttempts.value.length} recent attempt(s)`
})

const builderHeading = computed(() => {
  switch (activeBuilder.value) {
    case 'module':
      return 'Module Builder'
    case 'lesson':
      return 'Lesson Builder'
    case 'resource':
      return 'Resource Builder'
    case 'quiz':
      return 'Assessment Builder'
    default:
      return 'Course Builder'
  }
})

const builderDescription = computed(() => {
  switch (activeBuilder.value) {
    case 'module':
      return 'Edit module metadata, pricing, and placement with live backend validation.'
    case 'lesson':
      return 'Use the full lesson payload, including learning type, access, duration, and cover metadata.'
    case 'resource':
      return 'Manage recorded lessons with real uploads, external links, and learner watch insights.'
    case 'quiz':
      return 'Create quizzes, add question items, and review attempt activity without leaving this page.'
    default:
      return 'Create or revise course packages, then attach lessons and modules into sellable pathways.'
  }
})

const builderLaunchLabel = computed(() => {
  switch (activeSurfaceTab.value) {
    case 'modules':
      return 'Add Module'
    case 'lessons':
      return 'Create Lesson'
    case 'resources':
      return 'Upload Resource'
    case 'activity-types':
      return 'Create Quiz'
    default:
      return 'Create Course'
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
    if (!items.some((item) => item.id === lessonForm.moduleId)) {
      lessonForm.moduleId =
        lessonForm.moduleId && liveModuleOptions.value.some((module) => module.id === lessonForm.moduleId)
          ? lessonForm.moduleId
          : 0
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
        { optionText: 'True', correctOption: true, displayOrder: 0 },
        { optionText: 'False', correctOption: false, displayOrder: 1 },
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

function formatPrice(value: number) {
  return `RM ${Number(value || 0).toFixed(2)}`
}

function formatDuration(value: number | null | undefined) {
  const safeSeconds = nonNegativeInteger(value)
  if (safeSeconds <= 0) return '0s'
  const hours = Math.floor(safeSeconds / 3600)
  const minutes = Math.floor((safeSeconds % 3600) / 60)
  const seconds = safeSeconds % 60
  if (hours > 0) return `${hours}h ${minutes}m`
  if (minutes > 0) return `${minutes}m ${seconds}s`
  return `${seconds}s`
}

function normalizeStatus(status: ContentStatus | string) {
  switch (status) {
    case 'PUBLISHED':
      return 'Published'
    case 'APPROVED':
      return 'Approved'
    case 'ARCHIVED':
      return 'Archived'
    case 'SUBMITTED':
      return 'Submitted'
    case 'REJECTED':
      return 'Rejected'
    default:
      return 'Draft'
  }
}

function contentStatusTone(status: string): 'success' | 'warning' | 'danger' | 'info' | 'primary' {
  switch (status) {
    case 'Published':
      return 'success'
    case 'Approved':
      return 'info'
    case 'Submitted':
      return 'warning'
    case 'Rejected':
      return 'danger'
    case 'Archived':
      return 'primary'
    default:
      return 'info'
  }
}

function courseThumbVariant(id: number) {
  const thumbs = ['panda', 'family', 'pagoda', 'travel'] as const
  return thumbs[Math.max(id - 1, 0) % thumbs.length]
}

function ensureLiveWrite(feature: string) {
  if (!previewReadOnly.value) return true
  actionError.value = `${feature} is read-only while preview data is active.`
  actionSuccess.value = null
  return false
}

function clearActionFeedback() {
  actionError.value = null
  actionSuccess.value = null
}

function seedSharedContentFields<
  T extends { levelId: number; categoryId: number; accessType: 'FREE' | 'PAID'; price?: number | null },
>(target: T) {
  if (!selectedCourse.value.source) return
  target.levelId = selectedCourse.value.source.level.id
  target.categoryId = selectedCourse.value.source.category.id
  target.accessType = selectedCourse.value.source.accessType
  if ('price' in target) {
    target.price = selectedCourse.value.source.price
  }
}

function resetPackageForm() {
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

function resetModuleForm() {
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

function resetLessonForm(moduleId = 0) {
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

function resetResourceForm(lessonId = 0) {
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

function resetQuizForm(lessonId = 0) {
  editingQuizId.value = null
  quizForm.lessonId = lessonId
  quizForm.title = ''
  quizForm.description = ''
  quizForm.timeLimitMinutes = 0
}

function resetQuestionForm() {
  editingQuestionId.value = null
  questionForm.questionType = 'MULTIPLE_CHOICE'
  questionForm.questionText = ''
  questionForm.correctAnswerText = ''
  questionForm.points = 1
  questionForm.displayOrder = selectedQuiz.value?.questions.length ?? 0
  questionForm.explanation = ''
  questionForm.options = defaultQuestionOptions()
}

function openPackageBuilder(item?: CoursePackageResponse) {
  activeSurfaceTab.value = 'courses'
  activeBuilder.value = 'package'
  clearActionFeedback()

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

function openModuleBuilder(item?: LearningModuleResponse | null) {
  activeSurfaceTab.value = 'modules'
  activeBuilder.value = 'module'
  clearActionFeedback()

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

function openLessonBuilder(item?: LessonResponse | null, moduleId = 0) {
  activeSurfaceTab.value = 'lessons'
  activeBuilder.value = 'lesson'
  clearActionFeedback()

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

function openResourceBuilder(item?: RecordedLessonVideoResponse | null) {
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

function openQuizBuilder(item?: TeacherQuizResponse | null) {
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

function openQuestionBuilder(question?: TeacherQuizQuestionResponse | null) {
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

function openBuilderForActiveSurface() {
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

function showAllCourses() {
  clearActionFeedback()
  selectedPackageId.value = displayCourses.value[0]?.id ?? 0
  activeSurfaceTab.value = 'courses'
  actionSuccess.value = 'Showing all available courses from the current teacher content list.'
}

async function loadResourceInsights(lessonId: number) {
  loadingResourceInsights.value = true
  try {
    const [videos, progressRows] = await Promise.all([
      teacherApi.recordedVideos(lessonId),
      teacherApi.lessonVideoProgress(lessonId),
    ])
    uploadedResourceVideos.value = videos
    lessonVideoProgress.value = progressRows
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to load recorded lesson resources.')
  } finally {
    loadingResourceInsights.value = false
  }
}

async function loadQuizzesForLesson(lessonId: number, preferredQuizId?: number | null) {
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
    actionError.value = resolveApiError(reason, 'Unable to load lesson quizzes.')
  } finally {
    loadingQuizzes.value = false
  }
}

async function loadQuizAttempts(quizId: number) {
  loadingQuizAttempts.value = true
  try {
    quizAttempts.value = await teacherApi.quizAttempts(quizId)
    selectedQuizId.value = quizId
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to load quiz attempts.')
  } finally {
    loadingQuizAttempts.value = false
  }
}

function selectQuiz(quizId: number) {
  void loadQuizAttempts(quizId)
}

async function reloadAll() {
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

async function archiveSelectedPackage() {
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
    actionSuccess.value = 'Course archived successfully.'
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to archive this course.')
  } finally {
    packageBusy.value = false
  }
}

async function archiveModule(item?: LearningModuleResponse | null) {
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
    actionSuccess.value = 'Module archived successfully.'
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to archive this module.')
  } finally {
    moduleBusyId.value = null
  }
}

async function archiveLesson(item?: LessonResponse | null) {
  if (lessonBusyId.value) return
  if (!item || !ensureLiveWrite('Lesson management')) return
  if (!confirmTeacherContentMutation(`Archive the lesson "${item.title}"? Learners may lose access to its materials.`))
    return

  clearActionFeedback()
  lessonBusyId.value = item.id
  try {
    await teacherApi.archiveLesson(item.id)
    await Promise.all([lessonsResource.reload(), packagesResource.reload()])
    actionSuccess.value = 'Lesson archived successfully.'
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to archive this lesson.')
  } finally {
    lessonBusyId.value = null
  }
}

async function submitPackageItem() {
  if (submitting.packageItem) return
  if (!selectedCourse.value.source) {
    actionError.value = 'Select a live course package before attaching items.'
    actionSuccess.value = null
    return
  }
  if (!ensureLiveWrite('Course packaging')) return
  if (packageItemForm.itemId <= 0) {
    actionError.value = 'Choose a module or lesson to attach to this course.'
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
    actionSuccess.value = 'Course package updated successfully.'
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to add this item to the course package.')
  } finally {
    submitting.packageItem = false
  }
}

async function removePackageItem(packageItemId: number) {
  if (packageItemRemovingId.value) return
  if (!selectedCourse.value.source || !ensureLiveWrite('Course packaging')) return
  if (!confirmTeacherContentMutation('Remove this item from the course package?')) return

  clearActionFeedback()
  packageItemRemovingId.value = packageItemId
  try {
    await teacherApi.removePackageItem(selectedCourse.value.source.id, packageItemId)
    await packagesResource.reload()
    actionSuccess.value = 'Course package item removed successfully.'
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to remove this course package item.')
  } finally {
    packageItemRemovingId.value = null
  }
}

async function submitPackage() {
  if (submitting.package) return
  if (!ensureLiveWrite('Course management')) return
  if (!packageForm.title.trim()) {
    actionError.value = 'Course title is required.'
    actionSuccess.value = null
    return
  }
  if (packageForm.levelId <= 0 || packageForm.categoryId <= 0) {
    actionError.value = 'Select a level and category before saving this course.'
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
      actionSuccess.value = 'Course updated successfully.'
    } else {
      actionSuccess.value = 'Course created successfully.'
      openPackageBuilder(response)
    }
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to save this course.')
  } finally {
    submitting.package = false
  }
}

async function submitModule() {
  if (submitting.module) return
  if (!ensureLiveWrite('Module management')) return
  if (!moduleForm.title.trim()) {
    actionError.value = 'Module title is required.'
    actionSuccess.value = null
    return
  }
  if (moduleForm.levelId <= 0 || moduleForm.categoryId <= 0) {
    actionError.value = 'Select a level and category before saving this module.'
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
    actionSuccess.value = isEditing ? 'Module saved successfully.' : 'Module created successfully.'
    openModuleBuilder(response)
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to save this module.')
  } finally {
    submitting.module = false
  }
}

async function submitLesson() {
  if (submitting.lesson) return
  if (!ensureLiveWrite('Lesson management')) return
  if (!lessonForm.title.trim()) {
    actionError.value = 'Lesson title is required.'
    actionSuccess.value = null
    return
  }
  if (!lessonForm.moduleId || lessonForm.moduleId <= 0) {
    actionError.value = 'Select a module before saving this lesson.'
    actionSuccess.value = null
    return
  }
  if (lessonForm.levelId <= 0 || lessonForm.categoryId <= 0) {
    actionError.value = 'Select a level and category before saving this lesson.'
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
    actionSuccess.value = isEditing ? 'Lesson saved successfully.' : 'Lesson created successfully.'
    openLessonBuilder(response, response.moduleId ?? 0)
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to save this lesson.')
  } finally {
    submitting.lesson = false
  }
}

async function submitRecordedResource() {
  if (submitting.resource) return
  if (!ensureLiveWrite('Recorded lesson management')) return
  if (!resourceForm.lessonId || resourceForm.lessonId <= 0) {
    actionError.value = 'Select a lesson before saving a recorded lesson resource.'
    actionSuccess.value = null
    return
  }
  if (!resourceForm.title.trim()) {
    actionError.value = 'Recording title is required.'
    actionSuccess.value = null
    return
  }
  if (resourceForm.sourceType === 'EXTERNAL_URL' && !resourceForm.externalVideoUrl.trim()) {
    actionError.value = 'External video URL is required for EXTERNAL_URL sources.'
    actionSuccess.value = null
    return
  }

  const selectedFile = resourceFileInput.value?.files?.[0]
  if (resourceForm.sourceType === 'UPLOADED_FILE' && !selectedFile && !editingResourceId.value) {
    actionError.value = 'Choose a file before saving an uploaded recording.'
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
      ? 'Recorded lesson updated successfully.'
      : 'Recorded lesson created successfully.'
    openResourceBuilder(response)
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to save this recorded lesson.')
  } finally {
    submitting.resource = false
  }
}

async function archiveRecordedResource(videoId: number) {
  if (archivingResourceId.value) return
  if (!ensureLiveWrite('Recorded lesson management') || resourceLessonId.value <= 0) return
  if (!confirmTeacherContentMutation('Archive this recorded lesson resource? Learners may lose access to the video.'))
    return

  clearActionFeedback()
  archivingResourceId.value = videoId
  try {
    await teacherApi.archiveRecordedVideo(resourceLessonId.value, videoId)
    await loadResourceInsights(resourceLessonId.value)
    if (editingResourceId.value === videoId) {
      resetResourceForm(resourceLessonId.value)
    }
    actionSuccess.value = 'Recorded lesson archived successfully.'
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to archive this recorded lesson.')
  } finally {
    archivingResourceId.value = null
  }
}

async function submitQuiz() {
  if (submitting.quiz) return
  if (!ensureLiveWrite('Quiz management')) return
  if (!quizForm.lessonId || quizForm.lessonId <= 0) {
    actionError.value = 'Select a lesson before saving this quiz.'
    actionSuccess.value = null
    return
  }
  if (!quizForm.title.trim()) {
    actionError.value = 'Quiz title is required.'
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
    actionSuccess.value = isEditing ? 'Quiz updated successfully.' : 'Quiz created successfully.'
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to save this quiz.')
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

async function submitQuestion() {
  if (submitting.question) return
  if (!ensureLiveWrite('Quiz question management')) return
  if (!selectedQuiz.value) {
    actionError.value = 'Open or create a quiz before adding questions.'
    actionSuccess.value = null
    return
  }
  if (!questionForm.questionText.trim()) {
    actionError.value = 'Question text is required.'
    actionSuccess.value = null
    return
  }
  if (!questionForm.points || questionForm.points <= 0) {
    actionError.value = 'Question points must be greater than 0.'
    actionSuccess.value = null
    return
  }

  const options = buildQuestionOptions()
  if (!(questionForm.correctAnswerText ?? '').trim() && !options?.some((option) => option.correctOption)) {
    actionError.value = 'Provide either a correct answer text or mark one option as correct.'
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
    actionSuccess.value = isEditing ? 'Quiz question updated successfully.' : 'Quiz question created successfully.'
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to save this quiz question.')
  } finally {
    submitting.question = false
  }
}

async function deleteQuiz(quizId: number) {
  if (quizDeleteId.value) return
  if (!ensureLiveWrite('Quiz management')) return
  if (
    !confirmTeacherContentMutation(
      'Delete this quiz? Existing learner attempts may no longer have a visible quiz source.',
    )
  )
    return

  clearActionFeedback()
  quizDeleteId.value = quizId
  try {
    await teacherApi.deleteQuiz(quizId)
    if (selectedQuizId.value === quizId) {
      resetQuestionForm()
      selectedQuizId.value = null
    }
    await loadQuizzesForLesson(quizLessonId.value)
    actionSuccess.value = 'Quiz deleted successfully.'
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to delete this quiz.')
  } finally {
    quizDeleteId.value = null
  }
}

async function deleteQuestion(questionId: number) {
  if (questionDeleteId.value) return
  if (!ensureLiveWrite('Quiz question management')) return
  if (!confirmTeacherContentMutation('Delete this quiz question? This cannot be undone.')) return

  clearActionFeedback()
  questionDeleteId.value = questionId
  try {
    const response = await teacherApi.deleteQuizQuestion(questionId)
    quizLessonId.value = response.lessonId
    await loadQuizzesForLesson(response.lessonId, response.id)
    if (editingQuestionId.value === questionId) {
      resetQuestionForm()
    }
    actionSuccess.value = 'Quiz question deleted successfully.'
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to delete this quiz question.')
  } finally {
    questionDeleteId.value = null
  }
}

function addQuestionOption() {
  questionForm.options.push({
    optionText: '',
    correctOption: false,
    displayOrder: questionForm.options.length,
  })
}

function removeQuestionOption(index: number) {
  if (questionForm.options.length <= 1) return
  questionForm.options.splice(index, 1)
  questionForm.options.forEach((option, optionIndex) => {
    option.displayOrder = optionIndex
  })
}

function confirmTeacherContentMutation(message: string) {
  const confirmed = window.confirm(`${message}\n\nThis updates live teaching content.`)
  if (!confirmed) {
    actionSuccess.value = null
    actionError.value = null
  }

  return confirmed
}
</script>
