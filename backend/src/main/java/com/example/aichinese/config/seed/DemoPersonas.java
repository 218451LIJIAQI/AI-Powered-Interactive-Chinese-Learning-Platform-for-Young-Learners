package com.example.aichinese.config.seed;

import java.util.List;

/**
 * The accounts of the demo profile (the same people as in the low-fidelity prototype). Module seeders that run after
 * {@link DemoContentSeeder} look accounts up by these e-mails. Every learner shares {@code DEMO_STUDENT_PASSWORD}; the
 * admin is the default admin, who made the demo classes.
 */
public final class DemoPersonas {

    /** The main learner persona (her progress drives the learner journey). */
    public static final Persona STUDENT = new Persona("student@fyp.local", "Amira Hassan");
    /** Classmates in "Mandarin for Campus Life". */
    public static final Persona CHEN_HAO = new Persona("chen.hao@fyp.local", "Chen Hao");
    public static final Persona ZHANG_YI = new Persona("zhang.yi@fyp.local", "Zhang Yi");
    /** In "Shopping and Prices" and "Everyday Conversations". */
    public static final Persona NUR_AISYAH = new Persona("nur.aisyah@fyp.local", "Nur Aisyah");

    /** A learner an admin deactivated: the account cannot sign in. */
    public static final Persona ZHANG_YUXI = new Persona("zhang.yuxi@fyp.local", "Zhang Yuxi");

    public static final List<Persona> OTHER_STUDENTS = List.of(CHEN_HAO, ZHANG_YI, NUR_AISYAH);

    private DemoPersonas() {
    }

    public record Persona(String email, String displayName) {
    }
}
