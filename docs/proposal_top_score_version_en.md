# Proposed Design and Development of an AI-Powered Interactive Chinese Learning Platform for Young Learners in Malaysia

## Front Page

Use an APA-style student title page and replace the title on your current cover page with the following:

**Proposed Design and Development of an AI-Powered Interactive Chinese Learning Platform for Young Learners in Malaysia**

Student Name: Li Jiaqi  
Matric Number: 218451  
Programme: Bachelor of Software Engineering with Honours  
Department: Department of Software Engineering and Information System  
Faculty: Faculty of Computer Science and Information Technology  
Institution: Universiti Putra Malaysia  
Supervisor: Dr. Azrina Binti Kamaruddin  
Course: Final Year Project / Project Proposal  
Submission Date: 13 May 2026  
Semester: Semester 2, 2025/2026

## 1.0 Introduction

### 1.1 Background of the Study

Mandarin Chinese has become increasingly important as a language of communication, culture, and economic relevance in Asia and beyond. In Malaysia, Mandarin learning takes place within a multilingual social environment in which learners may already use Bahasa Melayu, English, and one or more community languages in their daily lives. For many children, exposure to Mandarin may occur through national-type schools, tuition centres, enrichment programmes, private learning platforms, or family and community settings. As a result, Mandarin learning in Malaysia is not merely a linguistic activity; it is also shaped by multilingual classroom realities, varying levels of prior exposure, and the need for accessible instructional support across languages.

For young beginners, Mandarin is often challenging because several foundational skills must be learned together. These include Hanyu Pinyin, tone discrimination and production, vocabulary acquisition, listening comprehension, introductory sentence formation, and early Chinese character recognition and writing. Recent studies indicate that learners of Mandarin may experience difficulty with content complexity, cognitive burden, character learning, and motivation, especially when the instructional sequence is not sufficiently scaffolded or when learners receive limited timely feedback. In a primary-level learning context, these challenges can be more pronounced because children require short, structured, and developmentally appropriate learning tasks rather than adult-oriented instructional materials.

At the same time, digital technologies continue to reshape language education. Web-based learning environments, mobile learning, and AI-assisted educational tools have created new possibilities for personalised support, interactive practice, and flexible access to learning resources. In the Malaysian context, recent studies suggest that technology-supported Mandarin learning can be beneficial when it strengthens teacher support, learner motivation, and meaningful interaction with content. However, many digital learning tools remain fragmented across separate functions such as vocabulary drill, handwriting practice, pronunciation practice, or teacher communication. This fragmentation may be inconvenient for young learners and difficult for teachers to manage.

Artificial intelligence has further expanded the possibilities of educational technology, particularly in the areas of adaptive feedback, explanation support, content drafting, and learner assistance. Nevertheless, recent research also highlights concerns about ethics, age appropriateness, overdependence on AI, and the continuing importance of teacher judgement. For a child-oriented learning system, AI should therefore be used in a bounded and supervised manner rather than as an autonomous replacement for teachers.

Against this background, this study proposes the design and development of an AI-powered interactive Chinese learning platform for young learners in Malaysia. The proposed platform is intended to integrate beginner Mandarin learning content, interactive practice, teacher-supported workflows, multilingual access, and bounded AI assistance in a single web-based prototype. The study is positioned as a Software Engineering Final Year Project that seeks to connect educational need, system design, and prototype evaluation within a realistic academic scope.

### 1.2 Motivation of the Study

This study is motivated by four main considerations. First, beginner Mandarin learners in Malaysia often face multiple learning demands at the same time, including Pinyin, tones, vocabulary, listening, and character-related skills. Because young learners are still developing self-regulation, language awareness, and sustained attention, they may require clearer sequencing, shorter tasks, and stronger scaffolding than older learners. A child-friendly platform that structures learning into manageable steps may therefore provide practical support.

Second, current digital learning experiences are often fragmented. A learner may need one tool for vocabulary practice, another for pronunciation exposure, another for handwriting or tracing, and a separate communication channel for guidance from teachers. Such fragmentation may interrupt learning flow, reduce consistency, and make it more difficult for teachers or guardians to monitor progress. This motivates the need for a more integrated platform in which key beginner activities can be accessed in one coherent environment.

Third, teacher involvement remains essential in the education of young learners. Mandarin teachers may spend substantial time preparing materials, monitoring progress, and providing repetitive explanations or feedback. A system that supports teacher-created content, controlled publishing, progress tracking, and limited AI-assisted drafting could reduce routine workload and allow teachers to focus more on learner encouragement, supervision, and meaningful feedback.

Fourth, AI now offers realistic opportunities for educational support, but these opportunities must be applied carefully. In this proposal, AI is not treated as a replacement for teachers or as a high-stakes assessment engine. Instead, it is treated as a bounded support mechanism that may provide beginner-friendly hints, short explanations, example sentences, simple practice prompts, and draft teaching materials subject to teacher review. This approach is both technically feasible for an undergraduate project and educationally more responsible for a child-oriented context.

Accordingly, the study is motivated not only by the technical possibility of building a full-stack educational platform, but also by the educational value of designing a system that responds to the needs of young beginner Mandarin learners in Malaysia. The project seeks to show how software engineering principles can be applied to a real educational problem through a research-informed and ethically bounded prototype.

### 1.3 Project Overview

This proposed project seeks to design, develop, and evaluate a web-based prototype named the AI-Powered Interactive Chinese Learning Platform for Young Learners. The prototype is intended for beginner Mandarin learners aged 7 to 12 in Malaysia and is designed with four main user categories in mind: Students, Teachers, Administrators, and Guests.

For students, the proposed prototype will provide structured beginner-level Mandarin learning content that may include Pinyin, basic vocabulary, listening exercises, simple sentence patterns, and introductory Chinese character practice. The learning experience is expected to include interactive activities such as flashcards, quizzes, matching games, listening-based tasks, and tracing-oriented activities. To encourage regular engagement, the prototype will also include reward mechanisms such as points, badges, progress indicators, levels, and streaks.

For teachers, the proposed system will include functions for content creation, activity preparation, student progress monitoring, and structured feedback support. Teacher accounts are expected to be verified before they are allowed to publish or manage learning content, so that the instructional workflow remains controlled and appropriate for young learners. For administrators, the prototype will include user management, teacher verification, content moderation, report handling, audit logging, and basic analytics.

The proposed platform will also include bounded AI-assisted features. These may include a child-friendly AI tutor, a vocabulary helper, sentence practice assistance, simple writing or pronunciation guidance at prototype level, and AI-assisted drafting support for teachers. All such features are intended to operate as supportive tools rather than authoritative teaching or assessment mechanisms.

From a technical perspective, the prototype is proposed as a full-stack web application built using Vue 3, TypeScript, and related frontend technologies on the client side, together with Spring Boot, Java, JWT-based security, and MySQL on the server side. The system will integrate with an external AI API through the backend so that sensitive credentials remain protected. Overall, the project is intended to demonstrate the feasibility of combining child-friendly learning design, role-based educational workflows, multilingual access, and bounded AI support in a single prototype that is suitable for Final Year Project evaluation.

## 2.0 Problem Statement

### 2.1 Problem Identification

Despite the growing availability of online Chinese learning resources, several important issues remain insufficiently addressed for young beginner learners in multilingual contexts such as Malaysia.

The first issue is cognitive and linguistic complexity. Beginner Mandarin learners must often learn Pinyin, tones, vocabulary, listening, and introductory Chinese characters within a relatively short period. For children aged 7 to 12, this can create substantial difficulty because they are still developing working memory, metalinguistic awareness, and independent learning strategies. When too many new elements are introduced at once, learners may become confused, demotivated, or overly dependent on memorisation rather than meaningful practice.

The second issue is the limited availability of integrated formative feedback. Many online resources provide static notes, prerecorded videos, or drill-based exercises, but they may not provide sufficiently timely or supportive feedback when a learner misunderstands a tone, confuses a word, or struggles with a tracing or sentence task. For young learners, the absence of immediate and comprehensible feedback may allow errors to persist and may reduce confidence during practice.

The third issue is platform fragmentation. In practice, learners may need multiple applications or websites to cover different beginner Mandarin skills, while teachers may need separate tools for communication, content management, progress monitoring, or lesson coordination. This fragmented environment can interrupt learning continuity, increase switching effort, and make it harder for teachers, institutions, or guardians to obtain a clear view of learner progress.

The fourth issue is the teacher workload associated with beginner support. Young learners often need repeated explanation, carefully sequenced materials, and ongoing encouragement. Without system support, teachers may spend a substantial amount of time preparing content, monitoring participation, responding to routine questions, and checking simple practice tasks. This may reduce the time available for higher-value instructional support.

The fifth issue concerns the use of general-purpose AI with children. Although AI has promising educational applications, AI tools designed for general users are not automatically suitable for young learners. Concerns include age-appropriate language, safety, teacher oversight, consistency of explanations, and the risk of overreliance on AI-generated responses. Therefore, there is a need for a more bounded and educationally supervised form of AI assistance.

Taken together, these issues suggest a clear need for an integrated, child-appropriate, and teacher-supported web platform for beginner Mandarin learning in Malaysia. Within the scope reviewed, there appears to be limited work that combines structured beginner Mandarin content, interactive practice, multilingual support, teacher workflows, moderation controls, and bounded AI assistance in one coherent prototype targeted at young learners.

### 2.2 Problem Justification

The proposed study is justified on educational, technological, and contextual grounds.

From an educational perspective, young beginner Mandarin learners require carefully sequenced instruction because the language introduces multiple unfamiliar elements at once. Literature on cognitive load and young learner development suggests that instructional environments should reduce unnecessary complexity, provide manageable learning steps, and support repeated guided practice. A system that organises beginner Mandarin content into structured modules and short interactive tasks is therefore educationally justified.

From a motivational perspective, young learners are more likely to remain engaged when learning activities are interactive, rewarding, and supported by positive adult guidance. Research on game-based learning, learner engagement, and teacher-student relationships suggests that motivation is strengthened when learners receive encouraging feedback, clear goals, and visible progress. A platform that integrates interactive activities with points, badges, progress indicators, and teacher-supported workflows is thus justified as a child-oriented learning environment rather than a purely informational content repository.

From a technological perspective, existing digital tools often address only part of the beginner Mandarin learning process. Some focus mainly on vocabulary, some on handwriting or character practice, and some on self-paced mobile drills. Many mainstream tools are also designed primarily for individual use rather than teacher-managed or institution-oriented use. This creates a practical gap for a prototype that integrates both learner-facing and teacher-facing functions within one system.

From an AI-in-education perspective, recent literature shows that AI can support explanation, personalisation, feedback, and content generation, but it should be used carefully in educational settings and especially carefully with children. The proposed study is justified because it adopts a bounded AI strategy: AI will provide low-stakes supportive assistance while teachers remain responsible for approval, supervision, and instructional judgement. This makes the proposed prototype both technically feasible and pedagogically more responsible.

Finally, the Malaysian context provides an additional justification. Learners in Malaysia often navigate a multilingual environment in which English or Bahasa Melayu may function as bridge languages while Mandarin is being learned as an additional language. A multilingual interface and teacher-guided workflow may therefore improve accessibility and usability for local learners and educators. For these reasons, the proposed system is justified as a context-sensitive educational technology prototype rather than a generic language-learning application.

### 2.3 Impact of the Problem

If the identified problems are not addressed, several negative educational and practical consequences may continue.

For students, the continued use of fragmented and weakly scaffolded tools may contribute to low motivation, confusion over tones and vocabulary, poor learning continuity, and reduced willingness to practise. Young learners may also develop recurring errors if they receive insufficient formative feedback during early practice. In the longer term, this may weaken confidence and persistence in Mandarin learning.

For teachers, the absence of an integrated support platform may increase the time required for routine instructional tasks such as preparing repetitive materials, tracking learner completion, responding to similar questions, and monitoring progress across separate channels. This may reduce the amount of time available for meaningful pedagogical intervention, especially in tuition or enrichment settings where teacher time is limited.

For administrators and educational providers, fragmented tools may reduce visibility into learner activity, content quality, moderation needs, and user support requests. This may make it harder to maintain a consistent and child-appropriate learning environment.

For the broader academic and design context, the lack of integrated child-oriented prototypes means that there are fewer practical examples of how beginner Mandarin learning, role-based educational workflows, multilingual access, and bounded AI support can be combined responsibly in one system. The proposed study therefore has value not only in addressing a local educational need, but also in contributing a research-informed prototype model for similar future work.

## 3.0 Objectives

### 3.1 General Objective

The general objective of this study is to design, develop, and evaluate a web-based prototype of an AI-powered interactive Mandarin learning platform that supports beginner learners aged 7 to 12 through structured content, child-friendly interaction, bounded AI assistance, teacher-supported workflows, and multilingual accessibility within the Malaysian context.

### 3.2 Specific Objectives

The specific objectives of the study are as follows:

1. To analyse the instructional and usability needs of young beginner Mandarin learners and the workflow needs of teachers and administrators in a Malaysian multilingual learning environment.
2. To design the proposed system architecture, database structure, user roles, interface flow, and major modules for Student, Teacher, Administrator, and Guest users.
3. To develop a full-stack web-based prototype using Vue 3, Spring Boot, and MySQL with secure authentication, role-based access control, and teacher verification.
4. To implement structured beginner Mandarin learning content and interactive activities that support Pinyin, vocabulary, listening, simple sentence practice, and introductory Chinese character learning.
5. To integrate bounded AI-assisted support through an external AI API for learner hints, short explanations, simple practice prompts, and teacher-side drafting assistance under controlled conditions.
6. To evaluate the prototype in terms of technical feasibility, usability, perceived learning support, and child-appropriate design through expert review, selected user testing, and questionnaire-based feedback.

## 4.0 Scope of the Study

### 4.1 System Scope

This study proposes a web-based full-stack prototype intended for academic development, testing, and demonstration. The proposed system scope includes the following modules:

1. **Authentication, role-based access, and teacher verification.**  
   The system will support student and teacher registration, secure login, JWT-based authentication, password reset support, and role-based access control. Teacher accounts will be subject to a verification workflow before gaining permission to publish or manage instructional content.

2. **Learning content management.**  
   Verified teachers and administrators will be able to create and manage categories, levels, modules, lessons, activities, and supporting media resources for beginner Mandarin learning.

3. **Interactive learning activities.**  
   The student learning experience will include activities such as flashcards, quizzes, matching tasks, listening exercises, and introductory tracing or writing-oriented practice.

4. **AI-assisted learning support.**  
   The prototype will integrate an external AI API to provide bounded features such as hints, brief explanations, vocabulary assistance, sentence practice prompts, and teacher-side drafting support. AI output is intended to support learning rather than replace teacher judgement.

5. **Progress tracking and reward mechanisms.**  
   The system will record activity attempts, progress summaries, and selected mastery-related indicators, while also providing motivational elements such as points, badges, streaks, and level-based progression.

6. **Teacher support features.**  
   The system will include content authoring, progress monitoring, student feedback support, and selected class-management functions for teachers.

7. **Administrator features.**  
   The system will include user management, teacher verification, moderation functions, report and support handling, basic audit logging, and platform-level visibility functions.

8. **Multilingual and auxiliary features.**  
   The interface will support English, Bahasa Melayu, and Mandarin Chinese. The prototype may also include notifications, public guidance pages, recorded-learning support, external live-learning links, and sandbox financial workflows where appropriate for demonstration.

The following items are explicitly outside the scope of this study:

1. Training of a custom AI model.
2. Development of an in-house video-conferencing application.
3. Real production-grade payment, payout, and refund processing.
4. Production-level legal compliance, cybersecurity hardening, and commercial deployment preparation.
5. Native iOS and Android mobile application development.
6. High-accuracy production speech recognition or pronunciation scoring.

### 4.2 User Scope

The proposed prototype focuses on four direct user groups and one indirect stakeholder group.

1. **Students.**  
   Students are young beginner Mandarin learners, approximately 7 to 12 years old, who will use the system for structured lessons, interactive activities, AI-assisted practice, and progress tracking.

2. **Teachers.**  
   Teachers are Mandarin educators, tutors, or trainee educators who will create and manage learning content, monitor learner progress, and provide feedback. Teacher publishing rights will depend on verification.

3. **Administrators.**  
   Administrators are responsible for teacher verification, user management, moderation, support handling, and selected analytics or oversight functions.

4. **Guests.**  
   Guests may access selected public content such as the landing page, platform information, and general guidance pages, but they will not access protected learning records or role-restricted features.

5. **Parents and guardians.**  
   Parents or guardians are treated as indirect stakeholders rather than a formal system role. Their role in this study is limited to supervision and consent support where child testing is involved.

### 4.3 Dataset Scope

The dataset scope of the proposed study is limited to the data necessary to support prototype development and evaluation.

1. **Instructional content dataset.**  
   The prototype will use a bounded set of beginner Mandarin learning materials prepared or curated for demonstration purposes. These may include Pinyin items, beginner vocabulary, simple sentence examples, listening prompts, quiz items, tracing references, and multilingual interface text. The content is intended for prototype use and not for large-scale curriculum coverage.

2. **System operational dataset.**  
   The system is expected to store user account data, role information, teacher verification status, lesson and activity data, quiz attempts, progress records, reward records, notification records, support or report entries, and selected audit logs required for system operation.

3. **Evaluation dataset.**  
   During prototype testing, the study may collect questionnaire responses, expert feedback notes, observation notes, task-completion results, and anonymised usage logs. These data will be used only for evaluation and improvement of the prototype.

4. **AI interaction dataset.**  
   AI-assisted features will process only limited learner prompts, teacher prompts, and lesson-related context required to generate supportive responses. The study does not involve the training of a proprietary AI model, the collection of large-scale conversational corpora, or unrestricted data scraping.

5. **Excluded data.**  
   The study will not intentionally collect unnecessary biometric data, real financial transaction data, sensitive medical data, or large-scale personal datasets unrelated to the prototype objectives. If child participants are involved in evaluation, only minimal data necessary for consented testing will be collected, and privacy precautions will be followed.

## 5.0 Literature Review

### 5.1 Introduction to Literature Review

This chapter reviews the literature that informs the design of the proposed platform. The review focuses on beginner Mandarin learning challenges, technology-enhanced Chinese language instruction, AI-assisted language learning, gamification and interactive learning for young learners, teacher support and scaffolding, and theoretical models relevant to child-oriented educational system design. The purpose of the review is not only to justify the proposed features, but also to identify the research and design gap that the study intends to address.

### 5.2 Related Works / Previous Studies

#### 5.2.1 Mandarin Learning Challenges and Motivation

Recent literature suggests that Mandarin presents distinctive challenges for beginners because learners must simultaneously engage with pronunciation, tones, vocabulary, listening, and Chinese characters. Studies on Mandarin learners in Malaysia and other Asian learning contexts indicate that content difficulty, character learning, and motivational decline may affect learning quality. These findings are relevant to the present study because young learners are likely to require even more structured and motivating support than older learners.

#### 5.2.2 Online and Technology-Enhanced Chinese Instruction

Research on online and technology-enhanced Chinese instruction suggests that digital environments can support learner interaction, flexible access, and targeted practice when the learning design is pedagogically structured. In the Malaysian context, recent research indicates that technology integration in Mandarin learning is promising when teacher support and learner motivation are preserved. However, the literature also suggests that many digital solutions focus on isolated learning functions rather than coherent support for young beginners.

#### 5.2.3 AI-Assisted Language Learning

Recent studies and systematic reviews show that AI can support personalisation, explanation, feedback, and content generation in education. In language learning, AI-powered chatbots and related tools have shown value in low-pressure practice and learner engagement. At the same time, the literature consistently warns that AI use should be interpreted carefully in educational settings, especially in relation to ethics, accuracy, overdependence, and teacher readiness. This supports the present study's decision to position AI as bounded assistance rather than autonomous instruction.

#### 5.2.4 Game-Based Learning, Gamification, and Serious Games

Recent research indicates that game-based learning and gamification can support engagement, motivation, and sustained participation among young learners when the game mechanics remain aligned with educational goals. In language learning and character-related tasks, structured digital interaction may make repetitive practice more appealing and less intimidating. These findings justify the use of progress indicators, rewards, and simple interactive practice activities in the proposed platform.

#### 5.2.5 Teacher-Student Interaction and Scaffolding

The literature on teacher support and scaffolding suggests that young learners benefit from guided instruction, responsive help, and gradually reduced support over time. Even in technology-supported environments, teacher involvement remains important in sustaining motivation, correcting misunderstanding, and shaping a positive learning experience. This supports the inclusion of teacher verification, teacher-managed content, and teacher feedback functions in the proposed system.

#### 5.2.6 Cognitive and Developmental Considerations for Young Learners

Young learners differ from older learners in terms of attention span, working memory, self-regulation, and response to instructional design. For this reason, child-oriented digital platforms should provide shorter tasks, simple navigation, limited cognitive overload, and clear multimodal guidance. This consideration directly informs the proposed platform's interface, content sequencing, and AI response constraints.

### 5.3 Theoretical Framework / Models

The proposed study is guided by four complementary perspectives.

**Cognitive Load Theory.**  
This perspective supports the design of short, structured, and low-clutter learning tasks so that beginner learners are not overwhelmed by too many unfamiliar elements at once. In the proposed platform, this theory justifies gradual sequencing, clear instructions, limited interface complexity, and task decomposition.

**Zone of Proximal Development and scaffolding.**  
The platform is intended to support learners through guided tasks, hints, teacher feedback, and AI-assisted prompts that remain within an appropriate difficulty range. Support is expected to help learners perform tasks that they may not yet complete independently, while gradually encouraging greater autonomy.

**Motivation and gamified engagement.**  
The study assumes that young learners are more likely to sustain practice when the environment provides visible progress, encouragement, and enjoyable interaction. Reward mechanisms such as badges, points, streaks, and progress indicators are therefore not merely cosmetic features; they are part of the motivational design strategy.

**Bounded AI support in education.**  
The proposed system adopts the principle that AI should function as a support tool under educational supervision. This framework justifies the use of AI for hints, explanation, drafting, and low-stakes practice while maintaining teacher oversight, content review, and prototype-level safety boundaries.

Together, these four perspectives provide the conceptual foundation for the proposed system. They link the learner problem context to the intended design choices and explain why the prototype is structured around scaffolding, engagement, multilingual accessibility, and controlled AI integration.

### 5.4 Existing Systems / Competitive Landscape

Several existing systems were reviewed to understand the practical landscape of beginner Chinese learning technologies. Widely known platforms such as Duolingo, HelloChinese, ChineseSkill, LingoDeer, and Skritter demonstrate valuable strengths, including gamified practice, pronunciation support, structured drills, and character-writing activities. However, these systems also reveal several limitations in relation to the current study.

First, many existing platforms are primarily designed for self-paced individual learning rather than teacher-managed learning workflows. Second, they are generally not designed specifically for Malaysian multilingual educational settings in which English and Bahasa Melayu may function as bridge languages. Third, many mainstream products do not combine learner practice, teacher verification, content moderation, and administrator oversight in one coherent environment. Fourth, AI-assisted functions in mainstream systems are not always presented within a bounded child-oriented framework that emphasises teacher supervision.

For these reasons, the competitor analysis supports the proposed study not by claiming that current systems are ineffective, but by showing that the present project has a different design focus: a child-oriented, multilingual, role-based, teacher-supported web prototype tailored to the Malaysian context.

### 5.5 Research Gap Analysis

The literature and system review indicate four main gaps.

**Population gap.**  
Much of the available literature on Chinese language learning technology focuses on higher education, adult learners, or general second-language contexts. There appears to be relatively less integrated work focused specifically on young beginner Mandarin learners in multilingual environments such as Malaysia.

**Integration gap.**  
Many studies and existing tools focus on isolated aspects of learning such as pronunciation, vocabulary, character practice, or chatbot interaction. Fewer studies appear to combine these needs into one coherent prototype with structured progression and beginner-oriented design.

**Pedagogical workflow gap.**  
There is limited emphasis on teacher-managed workflows, verification, moderation, and feedback structures in many learner-facing language applications. Yet these elements are important for young learners, whose learning typically requires supervision, guidance, and content appropriateness controls.

**Bounded AI design gap.**  
Although AI in education is a fast-growing area, there is still a need for more practical models of how AI can be integrated into child-oriented language learning systems in a limited, supervised, and pedagogically responsible manner. This is particularly relevant where AI is expected to support, rather than replace, human teaching.

Therefore, the proposed study seeks to address a meaningful research and design gap by combining structured beginner Mandarin content, multilingual accessibility, teacher-supported learning workflows, gamified practice, and bounded AI support in a single Malaysian-oriented FYP prototype.

### 5.6 Summary

The reviewed literature supports the need for a child-friendly, research-informed, and teacher-supported Mandarin learning prototype. The findings suggest that young beginner learners need stronger scaffolding, integrated feedback, and sustained motivation, while teachers need more manageable content and monitoring workflows. The literature also indicates that AI may be useful when it is carefully bounded and educationally supervised. These findings provide a clear rationale for the proposed platform and establish the academic basis for the study's design decisions.

## 6.0 Methodology

### 6.1 Introduction

This chapter explains the methodology proposed for the design, development, and evaluation of the system. The methodology addresses the overall research design, development approach, data collection methods, system design plan, verification and validation strategy, and evaluation methods. Because this document is a proposal, all activities described in this chapter refer to planned rather than completed work.

### 6.2 Research Design

This study adopts Design and Development Research (DDR) as its overall research design. DDR is appropriate because the study is centred on the creation and evaluation of an educational technology artefact intended to address a practical learning problem. In this case, the artefact is a web-based prototype for beginner Mandarin learning among young learners in Malaysia.

The proposed DDR process will take place in four stages. The first stage is problem analysis and requirement identification. This stage will draw on literature review, selected system review, and consultation with relevant educational stakeholders where feasible. The purpose is to identify learner needs, instructional design considerations, and the required role-based system functions.

The second stage is design. During this stage, the proposed architecture, database structure, user roles, core modules, interface flow, and main diagrams will be prepared. Design decisions will be guided by cognitive load considerations, child-oriented usability, multilingual accessibility, scaffolding principles, and bounded AI design.

The third stage is prototype development. The prototype will be developed incrementally so that core functions can be implemented and refined in manageable stages. This is consistent with the practical constraints of a Final Year Project and supports iterative improvement based on testing or supervisor feedback.

The fourth stage is evaluation and refinement. The study will examine whether the proposed prototype is technically feasible, usable, educationally plausible, and appropriate for young learners within the limited scope of an FYP prototype. The study does not seek to prove long-term language mastery outcomes. Rather, it seeks to evaluate whether the proposed design is coherent, workable, and supported by early feedback.

### 6.3 Development Methodology

Within the DDR framework, the implementation strategy will follow Agile incremental development. This approach is appropriate because the proposed platform consists of multiple interrelated modules that can be implemented in a staged manner, reviewed, and improved over time.

The proposed development plan is organised into six increments:

1. **Foundation increment.**  
   Project setup, database baseline, environment configuration, routing structure, and basic interface scaffolding.

2. **Security and role-management increment.**  
   Registration, login, authentication, JWT handling, role-based access control, dashboards, and teacher verification workflow.

3. **Learning content increment.**  
   Content management structures for categories, modules, lessons, activities, and media support.

4. **Interactive learning increment.**  
   Student-facing flashcards, quizzes, matching tasks, listening activities, and introductory tracing-related functions.

5. **AI and progress-support increment.**  
   Bounded AI assistance, progress tracking, reward mechanisms, and selected teacher-side drafting or support functions.

6. **Administration, evaluation, and refinement increment.**  
   Moderation functions, support workflows, multilingual polishing, auxiliary prototype features, verification, validation, documentation, and final refinements.

This staged approach reduces development risk and keeps the project realistic for an undergraduate timeframe. It also ensures that the essential learning experience and security-related workflows are prioritised before lower-priority auxiliary features.

### 6.4 Data Collection Methods

The study proposes to collect data for two main purposes: requirement gathering and prototype evaluation.

1. **Literature review.**  
   Recent journal articles, systematic reviews, and selected educational technology studies will be analysed to identify learner challenges, design principles, and system requirements relevant to beginner Mandarin learning.

2. **Expert or educator consultation.**  
   Where feasible, the study will seek informal or semi-structured consultation with one to three Mandarin teachers, tutors, or education-related reviewers using purposive sampling. The aim is to gather practical insight into learner difficulties, appropriate activities, and teacher-side workflow needs.

3. **Questionnaire-based evaluation.**  
   A short Likert-scale questionnaire will be used during evaluation to capture feedback on usability, clarity, motivation, perceived learning support, and suitability for young learners.

4. **Task-based usability testing.**  
   Selected participants will be asked to complete representative tasks such as registration, lesson access, activity completion, or teacher-side content handling. Observation notes will be recorded to identify confusion points and usability barriers.

5. **Prototype usage data.**  
   The system may record limited operational data such as quiz attempts, progress records, reward records, and selected AI assistance requests. These records will be used only for prototype evaluation, debugging, and design improvement.

Where direct involvement of children is feasible and approved, appropriate parental or guardian consent and supervision will be required. If direct child testing is not feasible within the FYP timeline, the study may use proxy users such as university students, older students, or educators for early usability evaluation. This limitation will be acknowledged explicitly in the report.

### 6.5 System Design (Architecture, Tools, Technologies)

The proposed system will adopt a three-tier web architecture consisting of the presentation tier, application tier, and data tier.

The presentation tier will be implemented using Vue 3, Vite, and TypeScript. Element Plus will support the user interface, Pinia will manage application state, Vue Router will manage navigation, Axios will support API communication, and Vue I18n will enable multilingual access in English, Bahasa Melayu, and Mandarin Chinese.

The application tier will be developed using Spring Boot with Java 21. Spring Security and JWT will be used for authentication and authorisation, while Spring Data JPA will manage data persistence. The backend will also act as a controlled gateway for AI API requests so that API keys and sensitive configuration remain server-side.

The data tier will use MySQL as the main relational database. The database is expected to store user accounts, role data, teacher verification records, lesson structures, activity records, progress history, notifications, support data, and selected audit logs. Docker Compose may be used to simplify local database setup during development.

The selected technology stack is appropriate because it supports full-stack web development, secure role-based workflows, structured educational content, and realistic prototype integration with external services.

### 6.6 Software Verification and Validation

Software verification and validation will be planned to ensure that the proposed prototype is both technically correct and appropriate for its intended context.

**Verification** will focus on whether the system is built according to the planned specifications. The following activities are proposed:

1. Backend unit testing for selected services and business logic.
2. Integration testing for authentication, role-based access, teacher verification, content workflows, and progress updates.
3. API testing under normal, error, and edge-case conditions.
4. Frontend build checks, route guard checks, form validation checks, and selected component-level checks.
5. Database verification of relationships, seed data, and data consistency.
6. Basic prototype security checks related to password handling, JWT flow, server-side AI proxying, and input validation.

**Validation** will focus on whether the prototype is useful and appropriate for its intended users. The following validation activities are proposed:

1. Task-based testing with selected users or proxy users.
2. Expert or teacher review of content suitability and child-oriented design.
3. Questionnaire-based feedback on usability and perceived support.
4. Observation of task difficulties and confusion points during testing.
5. Requirement checklist mapping between objectives, scope, and implemented functions.

### 6.7 Evaluation Methods

The proposed evaluation focuses on feasibility and early design quality rather than long-term learning outcomes. Three dimensions will be examined.

1. **Technical feasibility evaluation.**  
   This evaluation will examine whether core system functions operate as intended, including authentication, role-based access, teacher verification, content management, activity completion, AI request handling, progress recording, and moderation workflows. Results may be reported through test outcomes, defect logs, and completion status of planned core functions.

2. **Usability evaluation.**  
   Usability will be assessed through task completion, observation notes, and questionnaire responses. Key criteria will include navigation clarity, ease of use, interface appropriateness, and task understandability. Descriptive statistics such as mean scores and frequency summaries may be used to interpret questionnaire responses.

3. **Perceived learning support evaluation.**  
   This evaluation will examine whether reviewers perceive the platform as helpful for beginner Mandarin learning. Relevant aspects include clarity of explanations, usefulness of activities, motivational value of rewards, supportiveness of feedback, and suitability of bounded AI features for young learners.

Open-ended comments from expert review or user testing will be analysed thematically at a simple descriptive level to identify recurring strengths, weaknesses, and improvement points. The evaluation will not claim that the system definitively improves long-term Mandarin proficiency. Instead, it will assess whether the proposed platform is a feasible and educationally plausible prototype within the Final Year Project scope.

### 6.8 Summary

In summary, the methodology combines DDR with Agile incremental development so that the study remains academically grounded while also being realistic for an FYP implementation context. The proposed methods are intended to support requirement analysis, design, incremental development, verification, validation, and early-stage evaluation of the prototype.

## 7.0 Return on Investment (ROI) / Return on Value (ROV)

### 7.1 Cost-Benefit Analysis

As this project is an academic software prototype rather than a commercial product, Return on Value (ROV) is more appropriate than profit-based ROI. The primary purpose of the study is to create educational, technical, and academic value through a working prototype and research-informed design.

The direct incremental cost of the proposed prototype is expected to remain relatively low because most development tools are free or open source. A realistic Malaysian prototype-level cost estimate may be summarised as follows:

- Development software and frameworks: RM0 using free or community tools.
- Local database and containerised setup: RM0.
- Optional demo hosting or deployment support: RM0 to RM60.
- External AI API testing budget: approximately RM50 to RM120 depending on usage.
- Miscellaneous contingency cost: approximately RM30 to RM50.

Based on this estimate, the direct prototype-related out-of-pocket cost is expected to remain in the approximate range of **RM80 to RM230**, excluding the student's own device and existing internet access. The main project investment is therefore time, including requirement analysis, design, development, testing, evaluation, and documentation.

The anticipated benefit of this investment is not immediate commercial return, but the creation of a working FYP prototype, reusable technical artefacts, structured design knowledge, and a platform concept that may inform future educational technology development in the Malaysian Mandarin-learning context.

### 7.2 Expected Benefits

The expected benefits of the proposed project may be divided into tangible and intangible benefits.

**Tangible benefits** include the development of a working full-stack prototype, role-based workflows, multilingual interface components, AI-assisted support flows, database and API structures, system diagrams, evaluation instruments, and project documentation. These outputs may be reused, demonstrated, or extended in future work.

**Intangible benefits** include a clearer understanding of how beginner Mandarin learning needs can be translated into software requirements, how AI can be bounded responsibly in an educational setting, and how software engineering practice can be applied to a child-oriented multilingual learning problem. The project may also provide academic value by offering a structured prototype model for similar FYP or educational technology initiatives.

### 7.3 Value Contribution to Stakeholders

The proposed prototype is expected to contribute value to multiple stakeholder groups.

1. **Students.**  
   Students may benefit from structured learning progression, interactive practice, motivational rewards, and more accessible support during beginner Mandarin learning.

2. **Teachers.**  
   Teachers may benefit from content management, student progress visibility, and bounded AI-assisted drafting that can reduce repetitive preparation work.

3. **Administrators and educational providers.**  
   Administrators may benefit from clearer oversight of teacher approval, content moderation, reports, and system activity. Tuition centres or educational providers may view the prototype as a model for integrated beginner language support.

4. **Parents and guardians.**  
   Although not direct system users in this study, parents and guardians may benefit indirectly from a more structured and child-conscious learning environment.

5. **Academic evaluators and future researchers.**  
   The study may serve as a useful case of how a Software Engineering FYP can integrate educational theory, system design, and bounded AI support into one coherent prototype proposal.

## 8.0 Project Planning

### 8.1 Work Breakdown Structure (WBS)

The proposed Work Breakdown Structure is aligned with the DDR and Agile methodology described in Chapter 6. The project may be divided into five major work packages:

1. **Planning and analysis.**  
   Problem definition, literature review, requirement analysis, and proposal preparation.

2. **System design.**  
   Architecture design, database design, use case analysis, interface planning, and diagram preparation.

3. **Core prototype development.**  
   Authentication, role-based access, teacher verification, dashboards, content management, learning activities, and AI-assisted support.

4. **Evaluation and refinement.**  
   Verification, testing, usability evaluation, expert review, debugging, and prototype improvement.

5. **Documentation and presentation.**  
   Final report writing, user guide preparation, demonstration planning, and presentation rehearsal.

This WBS should be mapped directly to the detailed table and diagram in your report so that the planning chapter clearly reflects the methodology chapter.

### 8.2 Gantt Chart

The project is planned over approximately twenty-four weeks. The early phase will focus on literature review, problem analysis, requirement identification, and proposal preparation. This will be followed by the system design phase, including architecture planning, database design, interface flow design, and diagram preparation.

The middle phase will focus on incremental prototype development. Core security and role-management features will be prioritised first, followed by content management, interactive student activities, AI-assisted support, progress tracking, teacher and administrator functions, multilingual support, and selected auxiliary features.

The later phase will focus on verification, validation, evaluation, bug fixing, documentation, and presentation preparation. The schedule remains indicative and may be adjusted according to supervisor feedback, development realities, and evaluation constraints.

## 9.0 Conclusion

### 9.1 Summary of Proposal

This proposal presents a plan for the design, development, and evaluation of an AI-powered interactive Chinese learning platform for young learners in Malaysia. The study is motivated by the challenges faced by beginner Mandarin learners, including cognitive overload, fragmented digital tools, limited formative feedback, teacher workload, and the need for child-appropriate AI support.

To respond to these challenges, the proposed study introduces a web-based prototype that integrates structured beginner Mandarin content, interactive activities, multilingual access, teacher-managed workflows, bounded AI assistance, progress tracking, and basic administrative oversight. The project is positioned as a Software Engineering Final Year Project rather than a commercial system, and it explicitly limits its scope to a realistic prototype.

The proposal also outlines a clear methodology based on Design and Development Research with Agile incremental implementation. The evaluation plan focuses on technical feasibility, usability, perceived learning support, and child-appropriate design. Overall, the proposal demonstrates how educational need, software engineering practice, and responsible AI integration can be brought together in one coherent and academically defensible project.

### 9.2 Expected Contributions

The expected contributions of the study may be summarised in four areas.

1. **Academic contribution.**  
   The study contributes a research-informed proposal that connects Mandarin learning challenges, child-oriented design, scaffolding, multilingual accessibility, and bounded AI support in one educational technology context.

2. **Technical contribution.**  
   The project is expected to produce a reusable full-stack prototype architecture, database structure, security workflow, and module design that may inform future educational web applications.

3. **Design contribution.**  
   The proposed system offers a coherent design model for integrating teacher support, learner interaction, moderation, progress tracking, and AI assistance in a child-oriented Mandarin learning environment.

4. **Practical contribution.**  
   The study is expected to deliver a demonstrable prototype and a set of evaluation instruments that can support future extension, refinement, or adaptation in related educational settings.

In summary, the proposed project is expected to contribute a feasible, context-sensitive, and academically grounded prototype model for beginner Mandarin learning in Malaysia rather than a claim of final commercial readiness or proven long-term language mastery.

## 10.0 References

Keep your reference list in APA style, but revise it using the following rules:

1. Prioritise 2021-2026 empirical journal articles wherever possible.
2. Retain only a small number of foundational older citations in the theoretical framework if necessary.
3. Keep competitor websites only for system comparison, not as research evidence.
4. Ensure in-text citations and reference entries fully match.

## 11.0 Appendices

Your current appendices can remain, but strengthen them as follows:

- **Appendix A:** keep the detailed weekly timeline and ensure it matches the Gantt chart exactly.
- **Appendix B:** present the teacher interview items under clear dimensions such as learner difficulty, teacher workflow, child safety, and AI suitability.
- **Appendix C:** keep the proposed diagram list and ensure all diagrams are labelled as planned artefacts for the final report.
