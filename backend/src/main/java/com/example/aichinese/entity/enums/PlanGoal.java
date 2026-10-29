package com.example.aichinese.entity.enums;

import java.util.List;

/**
 * What a student wants to be able to do (setup step 2). The keywords match the goal to Learn units and lessons and
 * to classes by their titles, descriptions and topics: a goal is covered when a lesson or class in the plan matches.
 * "Not sure yet" has no keywords; the plan then uses the reason's default goals.
 */
public enum PlanGoal {

    INTRODUCE_MYSELF(List.of("introduce", "name", "hello", "greet", "meet")),

    GREET_CUSTOMERS(List.of("greet", "hello", "polite", "customer")),

    ORDER_FOOD(List.of("food", "drink", "canteen", "order", "menu", "eat", "café", "cafe", "restaurant", "hawker")),

    ASK_DIRECTIONS(List.of("direction", "asking the way", "where is", "the way", "map")),

    GET_AROUND(List.of("getting around", "transport", "lrt", "mrt", "metro", "taxi", "grab", "bus", "airport")),

    GO_SHOPPING(List.of("shopping", "market", "clothes", "discount", "size")),

    UNDERSTAND_PRICES(List.of("price", "paying", "money", "number", "count")),

    READ_SIGNS(List.of("sign", "read", "character", "notice")),

    SHORT_CHATS(List.of("chat", "conversation", "small talk")),

    SMALL_TALK(List.of("small talk", "conversation", "chat", "weekend")),

    TALK_ABOUT_WORK(List.of("work", "office", "job", "company", "career", "colleague")),

    WRITE_MESSAGES(List.of("write", "writing", "message", "stroke", "tracing")),

    TALK_IN_CLASS(List.of("class", "campus", "exam", "library", "school")),

    TELL_TIME(List.of("time", "date", "days of the week", "plans", "birthday", "schedule")),

    TALK_ABOUT_FAMILY(List.of("family")),

    ENJOY_MEDIA(List.of("culture", "festival", "song", "music", "story", "stories")),

    NOT_SURE(List.of());

    private final List<String> keywords;

    PlanGoal(List<String> keywords) {
        this.keywords = List.copyOf(keywords);
    }

    public List<String> keywords() {
        return keywords;
    }
}
