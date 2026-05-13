# Proposal Diagram Pack

## Project Title
AI-Powered Interactive Chinese Learning Platform for Young Learners

## Introduction
This document presents the proposal version of the diagrams for the project.  
All diagrams are based on the actual planned scope of the platform, but they are written from a proposal perspective, which means the system is presented as a designed and proposed solution rather than a completed implementation.  

The diagrams are intentionally simplified to make them:
- easy to understand
- suitable for academic proposal writing
- complete enough to explain the planned system clearly
- consistent with the real project scope, roles, modules, and workflows

## 1. Conceptual Framework Diagram

```mermaid
flowchart LR
    A["Student Learning Needs"] --> E["AI-Powered Interactive Chinese Learning Platform"]
    B["Teacher Educational Content"] --> E
    C["AI-Assisted Learning Support"] --> E
    D["Administrative Monitoring and Control"] --> E

    E --> F["Interactive Chinese Learning Experience"]
    E --> G["Improved Student Engagement and Progress"]
    E --> H["Organized Teacher Content Management"]
    E --> I["Safer and More Structured Learning Environment"]
```

## 2. Development Methodology Flow Diagram

```mermaid
flowchart TB
    A["Requirement Analysis"] --> B["Problem Identification and Planning"]
    B --> C["System Design"]
    C --> D["Frontend Development"]
    C --> E["Backend Development"]
    D --> F["System Integration"]
    E --> F
    F --> G["Testing and Refinement"]
    G --> H["Documentation and Final Proposal Preparation"]
    G -. feedback .-> C
```

## 3. System Architecture Diagram

```mermaid
flowchart LR
    A["Users<br/>Guest / Student / Teacher / Admin"] --> B["Web Application Frontend<br/>Vue 3"]
    B --> C["Backend API Layer<br/>Spring Boot"]
    C --> D["MySQL Database"]
    C --> E["AI Service"]
    C --> F["Payment Service"]
    C --> G["Email and Notification Service"]
    C --> H["File Storage"]
```

## 4. System Context Diagram

```mermaid
flowchart TB
    Guest["Guest"] --> Platform["Chinese Learning Platform"]
    Student["Student"] --> Platform
    Teacher["Teacher"] --> Platform
    Admin["Admin"] --> Platform

    Platform --> AI["AI Provider"]
    Platform --> Payment["Payment Provider"]
    Platform --> Email["Email Service"]
    Platform --> Meeting["External Meeting Platform"]
```

## 5. Overall Use Case Diagram

```mermaid
flowchart LR
    Guest["Guest"]
    Student["Student"]
    Teacher["Teacher"]
    Admin["Admin"]

    UC1(("Register Account"))
    UC2(("Login"))
    UC3(("Browse Courses"))
    UC4(("Learn Lessons"))
    UC5(("Attempt Quiz"))
    UC6(("Use AI Learning Tools"))
    UC7(("View Progress and Rewards"))
    UC8(("Make Payment / Subscribe"))
    UC9(("Create Teaching Content"))
    UC10(("Manage Live and Recorded Classes"))
    UC11(("Upload Verification Documents"))
    UC12(("Approve or Reject Teachers"))
    UC13(("Approve or Moderate Content"))
    UC14(("Manage Reports, Reviews, and Support"))
    UC15(("View Analytics"))

    Guest --> UC1
    Guest --> UC2
    Guest --> UC3

    Student --> UC2
    Student --> UC4
    Student --> UC5
    Student --> UC6
    Student --> UC7
    Student --> UC8

    Teacher --> UC2
    Teacher --> UC9
    Teacher --> UC10
    Teacher --> UC11
    Teacher --> UC15

    Admin --> UC12
    Admin --> UC13
    Admin --> UC14
    Admin --> UC15
```

## 6. Entity Relationship Diagram

This proposal ERD includes only the most important entities needed to explain the planned system clearly.

```mermaid
erDiagram
    USER {
        bigint user_id PK
        string full_name
        string email
        string password_hash
        string role
    }

    STUDENT_PROFILE {
        bigint student_id PK
        bigint user_id FK
        int total_points
        int progress_percent
    }

    TEACHER_PROFILE {
        bigint teacher_id PK
        bigint user_id FK
        string verification_status
        string bio
    }

    LESSON {
        bigint lesson_id PK
        bigint teacher_id FK
        string title
        string content_status
        string learning_type
    }

    QUIZ {
        bigint quiz_id PK
        bigint lesson_id FK
        string title
    }

    QUIZ_ATTEMPT {
        bigint attempt_id PK
        bigint quiz_id FK
        bigint student_id FK
        int score
    }

    COURSE_PACKAGE {
        bigint package_id PK
        bigint teacher_id FK
        string title
        string access_type
    }

    LIVE_CLASS {
        bigint live_class_id PK
        bigint teacher_id FK
        bigint lesson_id FK
        datetime start_time
    }

    PAYMENT_RECORD {
        bigint payment_id PK
        bigint student_id FK
        decimal amount
        string payment_status
    }

    NOTIFICATION {
        bigint notification_id PK
        bigint user_id FK
        string title
        string status
    }

    REPORT {
        bigint report_id PK
        bigint user_id FK
        string target_type
        string report_status
    }

    REVIEW {
        bigint review_id PK
        bigint user_id FK
        string target_type
        int rating
    }

    SUPPORT_TICKET {
        bigint ticket_id PK
        bigint user_id FK
        string subject
        string ticket_status
    }

    USER ||--|| STUDENT_PROFILE : has
    USER ||--|| TEACHER_PROFILE : has
    TEACHER_PROFILE ||--o{ LESSON : creates
    LESSON ||--o{ QUIZ : contains
    STUDENT_PROFILE ||--o{ QUIZ_ATTEMPT : makes
    QUIZ ||--o{ QUIZ_ATTEMPT : receives
    TEACHER_PROFILE ||--o{ COURSE_PACKAGE : creates
    TEACHER_PROFILE ||--o{ LIVE_CLASS : manages
    LESSON ||--o{ LIVE_CLASS : supports
    STUDENT_PROFILE ||--o{ PAYMENT_RECORD : makes
    USER ||--o{ NOTIFICATION : receives
    USER ||--o{ REPORT : creates
    USER ||--o{ REVIEW : writes
    USER ||--o{ SUPPORT_TICKET : submits
```

## 7. Component Diagram

```mermaid
flowchart TB
    subgraph Frontend
        A["Public Interface"]
        B["Student Interface"]
        C["Teacher Interface"]
        D["Admin Interface"]
    end

    subgraph Backend
        E["Authentication Module"]
        F["Learning Content Module"]
        G["AI Support Module"]
        H["Payment and Access Module"]
        I["Management and Monitoring Module"]
    end

    subgraph Storage
        J["Database"]
        K["File Storage"]
    end

    A --> E
    B --> E
    B --> F
    B --> G
    B --> H
    C --> E
    C --> F
    C --> I
    D --> E
    D --> I

    E --> J
    F --> J
    F --> K
    G --> J
    H --> J
    I --> J
```

## 8. Deployment Diagram

```mermaid
flowchart TB
    U["User Browser"] --> FE["Frontend Application"]
    FE --> BE["Backend Application"]
    BE --> DB["MySQL Database"]
    BE --> AI["AI Provider"]
    BE --> PAY["Payment Provider"]
    BE --> SMTP["Email Service"]
    BE --> FS["File Storage"]
```

## 9. Activity Diagram - Student Learning Flow

```mermaid
flowchart TB
    A([Start]) --> B["Student logs in"]
    B --> C["Student selects a lesson"]
    C --> D["System displays lesson content"]
    D --> E["Student completes learning activity"]
    E --> F["Student attempts quiz"]
    F --> G["System calculates result"]
    G --> H["System updates progress and rewards"]
    H --> I["Student may use AI tutor for additional help"]
    I --> J([End])
```

## 10. Activity Diagram - Teacher Content Creation Flow

```mermaid
flowchart TB
    A([Start]) --> B["Teacher logs in"]
    B --> C["Teacher creates module, lesson, or package"]
    C --> D["Teacher adds lesson content"]
    D --> E["Teacher adds activities or quizzes"]
    E --> F["Teacher saves content as draft"]
    F --> G["Teacher submits content for admin review"]
    G --> H([End])
```

## 11. Activity Diagram - Administrator Teacher Verification Flow

```mermaid
flowchart TB
    A([Start]) --> B["Admin logs in"]
    B --> C["Admin opens teacher application"]
    C --> D["Admin reviews uploaded documents"]
    D --> E{"Documents valid?"}
    E -- Yes --> F["Approve teacher"]
    E -- No --> G["Reject teacher"]
    F --> H["System updates teacher status"]
    G --> H
    H --> I["System sends notification"]
    I --> J([End])
```

## 12. Sequence Diagram - User Login

```mermaid
sequenceDiagram
    actor User
    participant Frontend
    participant Backend
    participant Database

    User->>Frontend: Enter login details
    Frontend->>Backend: Send login request
    Backend->>Database: Validate user account
    Database-->>Backend: Return user record
    Backend-->>Frontend: Return authentication result
    Frontend-->>User: Redirect to role-based dashboard
```

## 13. Sequence Diagram - AI Chat Tutor Request

```mermaid
sequenceDiagram
    actor Student
    participant Frontend
    participant Backend
    participant AIService
    participant Database

    Student->>Frontend: Enter AI tutor question
    Frontend->>Backend: Send AI tutor request
    Backend->>AIService: Forward prompt
    AIService-->>Backend: Return AI response
    Backend->>Database: Save usage log
    Backend-->>Frontend: Return answer
    Frontend-->>Student: Display AI response
```

## 14. Sequence Diagram - Quiz Attempt

```mermaid
sequenceDiagram
    actor Student
    participant Frontend
    participant Backend
    participant Database

    Student->>Frontend: Submit quiz answers
    Frontend->>Backend: Send attempt data
    Backend->>Database: Save attempt and evaluate answers
    Database-->>Backend: Return score and result
    Backend-->>Frontend: Return quiz outcome
    Frontend-->>Student: Show score and feedback
```

## 15. Sequence Diagram - Teacher Content Creation

```mermaid
sequenceDiagram
    actor Teacher
    participant Frontend
    participant Backend
    participant Database

    Teacher->>Frontend: Enter lesson or package information
    Frontend->>Backend: Send content data
    Backend->>Database: Save draft content
    Database-->>Backend: Confirm save
    Backend-->>Frontend: Return content result
    Frontend-->>Teacher: Show saved draft
```

## 16. Sequence Diagram - Admin Teacher Verification

```mermaid
sequenceDiagram
    actor Admin
    participant Frontend
    participant Backend
    participant Database
    participant Notification

    Admin->>Frontend: Approve or reject teacher
    Frontend->>Backend: Send verification decision
    Backend->>Database: Update teacher verification status
    Database-->>Backend: Confirm update
    Backend->>Notification: Create notification
    Backend-->>Frontend: Return updated teacher status
    Frontend-->>Admin: Display verification result
```

## 17. State Machine Diagram - Teacher Verification Status

```mermaid
stateDiagram-v2
    [*] --> Pending
    Pending --> Approved : Admin approval
    Pending --> Rejected : Admin rejection
    Rejected --> Pending : Teacher resubmission
    Approved --> [*]
    Rejected --> [*]
```

## 18. Site Map / Navigation Flow Diagram

```mermaid
flowchart TB
    Home["Home"]

    Home --> Login["Login"]
    Home --> Register["Register"]
    Home --> Courses["Course Discovery"]
    Home --> FAQ["FAQ"]
    Home --> Legal["Terms / Privacy / Safety"]
    Home --> TeacherProfile["Teacher Public Profile"]

    Login --> StudentDashboard["Student Dashboard"]
    Login --> TeacherDashboard["Teacher Dashboard"]
    Login --> AdminDashboard["Admin Dashboard"]

    StudentDashboard --> StudentLessons["Lessons"]
    StudentDashboard --> StudentAI["AI Tools"]
    StudentDashboard --> StudentProgress["Progress"]
    StudentDashboard --> StudentRewards["Rewards"]
    StudentDashboard --> StudentPayments["Payments"]
    StudentDashboard --> StudentSupport["Support"]

    TeacherDashboard --> TeacherProfilePage["Profile"]
    TeacherDashboard --> TeacherVerification["Verification"]
    TeacherDashboard --> TeacherContent["Content Management"]
    TeacherDashboard --> TeacherClasses["Live and Recorded Classes"]
    TeacherDashboard --> TeacherAI["AI Assistant"]
    TeacherDashboard --> TeacherEarnings["Earnings"]

    AdminDashboard --> AdminTeachers["Teacher Approval"]
    AdminDashboard --> AdminContent["Content Approval"]
    AdminDashboard --> AdminReports["Reports and Reviews"]
    AdminDashboard --> AdminSupport["Support Management"]
    AdminDashboard --> AdminAnalytics["Analytics"]
```

## 19. Selected UI Wireframes

### 19.1 Home Page Wireframe

```text
+-------------------------------------------------------------------+
| Header: Logo | Home | Courses | FAQ | Login | Register            |
+-------------------------------------------------------------------+
| Hero Section                                                      |
| AI-Powered Chinese Learning for Young Learners                    |
| Short project introduction                                        |
| Buttons: Explore Courses | Get Started                            |
+-------------------------------------------------------------------+
| Feature Section                                                   |
| Interactive Lessons | AI Tutor | Teacher Support | Progress Track |
+-------------------------------------------------------------------+
| Footer                                                            |
+-------------------------------------------------------------------+
```

### 19.2 Student Dashboard Wireframe

```text
+-------------------------------------------------------------------+
| Sidebar: Dashboard | Lessons | AI Tools | Progress | Rewards      |
+-------------------------------------------------------------------+
| Welcome Card                                                      |
| Student name | Learning summary | Continue Learning button        |
+-------------------------------------------------------------------+
| Summary Cards                                                     |
| Progress | Points | Streak | Badges                              |
+-------------------------------------------------------------------+
| Main Content                                                      |
| Recommended lessons                                               |
| Recent quiz results                                               |
| Quick AI support                                                  |
+-------------------------------------------------------------------+
```

### 19.3 Teacher Content Management Wireframe

```text
+-------------------------------------------------------------------+
| Sidebar: Dashboard | Profile | Verification | Lessons | Packages  |
+-------------------------------------------------------------------+
| Page Title: Lesson / Content Editor                               |
+-------------------------------------------------------------------+
| Form Section                                                      |
| Title                                                             |
| Description                                                       |
| Level | Category | Learning Type | Access Type                    |
| Main Content                                                      |
| Save Draft | Submit                                               |
+-------------------------------------------------------------------+
```

### 19.4 Admin Teacher Verification Wireframe

```text
+-------------------------------------------------------------------+
| Sidebar: Dashboard | Teacher Approval | Reports | Support         |
+-------------------------------------------------------------------+
| Verification Detail Page                                          |
| Teacher Name                                                      |
| Email                                                             |
| Verification Status                                               |
| Uploaded Documents                                                |
| Buttons: Approve | Reject                                         |
+-------------------------------------------------------------------+
```

## 20. Gantt Chart

This chart presents a proposal-style project schedule for planning purposes.

```mermaid
gantt
    title Proposed Project Timeline
    dateFormat  YYYY-MM-DD

    section Planning
    Requirement Analysis          :a1, 2026-01-01, 10d
    Literature Review             :a2, 2026-01-11, 10d

    section Design
    System Design                 :b1, 2026-01-21, 12d
    Database Design               :b2, 2026-02-02, 8d
    User Interface Design         :b3, 2026-02-10, 8d

    section Development
    Frontend Development          :c1, 2026-02-18, 20d
    Backend Development           :c2, 2026-02-18, 20d
    AI Integration                :c3, 2026-03-10, 12d
    Payment and Management Module :c4, 2026-03-22, 10d

    section Testing
    Integration Testing           :d1, 2026-04-01, 10d
    Bug Fixing and Improvement    :d2, 2026-04-11, 10d

    section Final Stage
    Documentation                 :e1, 2026-04-21, 7d
    Final Review                  :e2, 2026-04-28, 7d
```

## 21. Work Breakdown Structure Diagram / Table

| WBS | Task | Description |
|---|---|---|
| 1.0 | Project Planning | Define project scope, objectives, and requirements |
| 2.0 | System Analysis and Design | Design system structure, data model, and interfaces |
| 2.1 | Interface Design | Prepare page layouts and wireframes |
| 2.2 | Database Design | Define key entities and relationships |
| 3.0 | Frontend Development | Develop interfaces for guest, student, teacher, and admin |
| 4.0 | Backend Development | Develop authentication, lesson, AI, and management functions |
| 5.0 | AI Integration | Add AI tutor and AI-based learning support |
| 6.0 | Payment and Access Control | Add payment, subscription, and access features |
| 7.0 | Testing and Evaluation | Perform system testing and refinement |
| 8.0 | Documentation | Prepare proposal, report, and diagrams |

## 22. Quiz Scoring and Reward Awarding Flowchart

```mermaid
flowchart TB
    A([Start]) --> B["Student submits quiz answers"]
    B --> C["System checks all answers"]
    C --> D["System calculates quiz score"]
    D --> E["System stores quiz attempt"]
    E --> F{"Is the result satisfactory?"}
    F -- Yes --> G["Update student progress"]
    F -- No --> H["Store result for later review"]
    G --> I["Award points"]
    I --> J{"Is the score high?"}
    J -- Yes --> K["Award bonus points or badge"]
    J -- No --> L["Keep normal reward"]
    H --> L
    K --> M["Display quiz result"]
    L --> M
    M --> N([End])
```

