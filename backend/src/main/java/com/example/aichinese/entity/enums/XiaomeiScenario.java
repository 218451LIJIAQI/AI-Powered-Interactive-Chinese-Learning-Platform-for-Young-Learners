package com.example.aichinese.entity.enums;

/** The situations a student can practise with Xiaomei; {@link #situation()} is what the AI is told about it. */
public enum XiaomeiScenario {

    MEET_XIAOMEI("You meet the learner for the first time. Chat about how their day was, what they study and what they"
            + " like to do."),

    CAFE("You work at the counter of a campus café. Take the learner's order of drinks and snacks, ask about size, ice"
            + " and sugar and whether it is to eat here or take away, and say the price in ringgit (令吉)."),

    ASK_THE_WAY("You stand near an LRT station in Kuala Lumpur. The learner asks you the way to a place (the station, a"
            + " library, a food court, a bank); give simple directions with 一直走, 左边, 右边 and 旁边."),

    WEEKEND_PLANS("You are the learner's friend. Chat about plans for the weekend: what to do, where, when and with"
            + " whom, and suggest doing something together."),

    PASAR_MALAM("You run a food stall at a pasar malam (夜市) in Malaysia. The learner looks at your snacks and drinks,"
            + " asks what is good and how much it costs, and buys something."),

    CAMPUS_TOUR("You are a senior student showing the learner around the campus: the library, the canteen, the"
            + " classrooms and the hostel. Ask what they study and what they want to see.");

    private final String situation;

    XiaomeiScenario(String situation) {
        this.situation = situation;
    }

    public String situation() {
        return situation;
    }
}
