package com.example.aichinese.support;

import java.nio.charset.StandardCharsets;

public final class TestUploadFixtures {

    private static final byte[] MINIMAL_PDF =
            "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] MINIMAL_PNG = {
            (byte) 0x89, 0x50, 0x4E, 0x47,
            0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D,
            0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01,
            0x00, 0x00, 0x00, 0x01
    };
    private static final byte[] MINIMAL_WEBM = {
            0x1A, 0x45, (byte) 0xDF, (byte) 0xA3,
            0x01, 0x00, 0x00, 0x00,
            0x77, 0x65, 0x62, 0x6D,
            0x42, (byte) 0x82, (byte) 0x84, 0x77,
            0x65, 0x62, 0x6D
    };
    private static final byte[] MINIMAL_MP4 = {
            0x00, 0x00, 0x00, 0x18,
            0x66, 0x74, 0x79, 0x70,
            0x69, 0x73, 0x6F, 0x6D,
            0x00, 0x00, 0x02, 0x00,
            0x69, 0x73, 0x6F, 0x6D,
            0x6D, 0x70, 0x34, 0x31
    };

    private TestUploadFixtures() {
    }

    public static byte[] minimalPdf() {
        return MINIMAL_PDF.clone();
    }

    public static byte[] minimalPng() {
        return MINIMAL_PNG.clone();
    }

    public static byte[] minimalWebm() {
        return MINIMAL_WEBM.clone();
    }

    public static byte[] minimalMp4() {
        return MINIMAL_MP4.clone();
    }
}
