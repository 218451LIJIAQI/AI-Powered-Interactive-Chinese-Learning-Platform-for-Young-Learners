package com.example.aichinese.config.seed;

import java.util.List;

/**
 * The accounts of the demo profile (the same people as in the low-fidelity prototype). Module seeders that run after
 * {@link DemoContentSeeder} look accounts up by these e-mails. Every student shares {@code DEMO_STUDENT_PASSWORD}
 * and every teacher (applicant included) shares {@code DEMO_TEACHER_PASSWORD}; the admin is the default admin.
 */
public final class DemoPersonas {

    /** The main student persona (her progress drives the student journey). */
    public static final Persona STUDENT = new Persona("student@fyp.local", "Amira Hassan");
    /** Classmates in Li Wei's "Mandarin for Beginners" class. */
    public static final Persona CHEN_HAO = new Persona("chen.hao@fyp.local", "Chen Hao");
    public static final Persona ZHANG_YI = new Persona("zhang.yi@fyp.local", "Zhang Yi");
    public static final Persona NUR_AISYAH = new Persona("nur.aisyah@fyp.local", "Nur Aisyah");

    /** The main approved teacher persona. */
    public static final Persona TEACHER = new Persona("teacher@fyp.local", "Li Wei");
    public static final Persona WONG_SIEW_LING = new Persona("wong.siewling@fyp.local", "Wong Siew Ling");
    public static final Persona JOHN_LIM = new Persona("john.lim@fyp.local", "John Lim");
    public static final Persona TAN_MEI_FONG = new Persona("tan.meifong@fyp.local", "Tan Mei Fong");

    /** A teacher applicant whose verification is not decided yet. */
    public static final Persona APPLICANT = new Persona("applicant@fyp.local", "Mei Lin");

    public static final List<Persona> CLASSMATES = List.of(CHEN_HAO, ZHANG_YI, NUR_AISYAH);
    public static final List<Persona> OTHER_TEACHERS = List.of(WONG_SIEW_LING, JOHN_LIM, TAN_MEI_FONG);

    private DemoPersonas() {
    }

    public record Persona(String email, String displayName) {
    }
}
