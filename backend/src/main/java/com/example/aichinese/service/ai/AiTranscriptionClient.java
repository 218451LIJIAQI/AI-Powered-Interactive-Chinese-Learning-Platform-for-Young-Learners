package com.example.aichinese.service.ai;

/** Speech to text for pronunciation checks: what SparkBot heard in a recording, as Chinese text. */
@FunctionalInterface
public interface AiTranscriptionClient {

    /** The failure summary of a recording in which no speech could be written down. */
    String NOTHING_HEARD = "The recording had no speech that could be written down.";

    AiProviderResult transcribe(AiAudioInput audio);
}
