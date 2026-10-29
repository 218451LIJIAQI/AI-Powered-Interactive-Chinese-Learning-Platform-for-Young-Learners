package com.example.aichinese.config.seed;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

/**
 * A small one-page PDF built in memory, handed to the real upload pipeline (FileStorageService) so demo verification
 * documents are stored, validated and served exactly like uploaded ones.
 */
final class DemoDocumentFile implements MultipartFile {

    private final String fileName;
    private final byte[] content;

    private DemoDocumentFile(String fileName, byte[] content) {
        this.fileName = fileName;
        this.content = content;
    }

    /** A PDF whose page shows {@code lines} (ASCII), named {@code fileName}. */
    static DemoDocumentFile pdf(String fileName, List<String> lines) {
        StringBuilder text = new StringBuilder("BT /F1 18 Tf 72 760 Td 24 TL");
        for (String line : lines) {
            text.append(" (").append(line.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")).append(") '");
        }
        text.append(" ET");
        String stream = text.toString();
        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R"
                        + " /Resources << /Font << /F1 5 0 R >> >> >>",
                "<< /Length " + stream.length() + " >>\nstream\n" + stream + "\nendstream",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");

        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        write(pdf, "%PDF-1.4\n");
        for (int index = 0; index < objects.size(); index++) {
            offsets.add(pdf.size());
            write(pdf, (index + 1) + " 0 obj\n" + objects.get(index) + "\nendobj\n");
        }
        int xref = pdf.size();
        StringBuilder table = new StringBuilder("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
        offsets.forEach(offset -> table.append(String.format("%010d 00000 n \n", offset)));
        table.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF\n");
        write(pdf, table.toString());
        return new DemoDocumentFile(fileName, pdf.toByteArray());
    }

    private static void write(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(StandardCharsets.US_ASCII));
    }

    @Override
    public String getName() {
        return "file";
    }

    @Override
    public String getOriginalFilename() {
        return fileName;
    }

    @Override
    public String getContentType() {
        return "application/pdf";
    }

    @Override
    public boolean isEmpty() {
        return content.length == 0;
    }

    @Override
    public long getSize() {
        return content.length;
    }

    @Override
    public byte[] getBytes() {
        return content.clone();
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(content);
    }

    @Override
    public void transferTo(File destination) throws IOException {
        Files.write(destination.toPath(), content);
    }
}
