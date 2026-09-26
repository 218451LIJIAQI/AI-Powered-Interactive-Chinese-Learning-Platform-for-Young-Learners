package com.example.aichinese.repository.projection;

public interface WrongAnswerCountProjection {

    String getQuestionType();

    long getAnswerCount();
}
