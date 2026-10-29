package com.example.aichinese.service.ai;

/**
 * A pronunciation recording held in memory while it is scored; it is never written to storage.
 *
 * @param fileName the sanitised original name, whose extension tells the speech-to-text service the format
 */
public record AiAudioInput(byte[] data, String fileName, String contentType) {
}
