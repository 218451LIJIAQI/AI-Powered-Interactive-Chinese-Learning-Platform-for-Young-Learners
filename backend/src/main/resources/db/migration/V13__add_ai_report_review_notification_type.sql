alter table notifications
    modify column notification_type enum (
        'ADMIN_TASK',
        'AI_REPORT_REVIEW',
        'CONTENT_REVIEW',
        'LIVE_CLASS_REMINDER',
        'MODERATION_ACTION',
        'PAYMENT_STATUS',
        'PAYOUT_STATUS',
        'PROGRESS_UPDATE',
        'QUIZ_RESULT',
        'REFUND_STATUS',
        'REWARD_EARNED',
        'SYSTEM',
        'TEACHER_APPROVAL',
        'TEACHER_FEEDBACK'
    ) not null;
