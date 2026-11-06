package com.example.aichinese.entity.enums;

import java.util.List;

/**
 * What a student wants to be able to do (setup step 2). The keywords match the goal to Learn units and lessons and
 * to classes by their titles, descriptions and themes: a goal is covered when a lesson or class in the plan matches.
 * Learn lessons are named after the language they teach, so a goal also lists the lesson that teaches its patterns
 * ("to be called" for introducing yourself, "want" for ordering). "Not sure yet" has no keywords; the plan then uses
 * the reason's default goals.
 */
public enum PlanGoal {

    INTRODUCE_MYSELF(List.of("introduce", "name", "hello", "greet", "meet", "to be called")),

    GREET_CUSTOMERS(List.of("greet", "hello", "polite", "customer", "first words")),

    ORDER_FOOD(List.of("food", "drink", "canteen", "ordering", "order food", "menu", "eat", "café", "cafe", "restaurant",
            "hawker", "want")),

    ASK_DIRECTIONS(List.of("direction", "asking the way", "where is", "the way", "your way", "map", "question words")),

    GET_AROUND(List.of("getting around", "get around", "transport", "lrt", "mrt", "metro", "taxi", "grab", "bus", "airport",
            "question words")),

    GO_SHOPPING(List.of("shopping", "market", "clothes", "discount", "size", "measure words")),

    UNDERSTAND_PRICES(List.of("price", "paying", "money", "number", "count")),

    READ_SIGNS(List.of("sign", "read", "character", "notice")),

    SHORT_CHATS(List.of("chat", "conversation", "small talk", "questions")),

    SMALL_TALK(List.of("small talk", "conversation", "chat", "weekend", "questions")),

    TALK_ABOUT_WORK(List.of("work", "office", "job", "company", "career", "colleague")),

    WRITE_MESSAGES(List.of("write", "writing", "message", "stroke", "tracing")),

    TALK_IN_CLASS(List.of("class", "campus", "exam", "library", "school", "study")),

    TELL_TIME(List.of("time", "date", "days of the week", "plans", "birthday", "schedule")),

    TALK_ABOUT_FAMILY(List.of("family", "have and my")),

    ENJOY_MEDIA(List.of("culture", "festival", "song", "music", "story", "stories", "film")),

    NOT_SURE(List.of());

    private final List<String> keywords;

    PlanGoal(List<String> keywords) {
        this.keywords = List.copyOf(keywords);
    }

    public List<String> keywords() {
        return keywords;
    }
}
