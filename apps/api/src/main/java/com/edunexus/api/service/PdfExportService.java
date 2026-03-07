package com.edunexus.api.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

@Service
public class PdfExportService {

    public byte[] renderPdf(String title, String markdown) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            float margin = 50;
            float y = page.getMediaBox().getHeight() - margin;
            float leading = 16;
            PDFont titleFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont bodyFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            Path cjkFont = Paths.get("C:/Windows/Fonts/simsun.ttc");
            if (Files.exists(cjkFont)) {
                try (var in = Files.newInputStream(cjkFont)) {
                    bodyFont = PDType0Font.load(document, in, true);
                    titleFont = bodyFont;
                }
            }

            PDPageContentStream stream = new PDPageContentStream(document, page);
            try {
                stream.setFont(titleFont, 14);
                stream.beginText();
                stream.newLineAtOffset(margin, y);
                stream.showText(safePdfText(title, bodyFont instanceof PDType0Font));
                stream.endText();

                stream.setFont(bodyFont, 11);
                y -= leading * 2;
                for (String line : wrapLines(markdown, 95)) {
                    if (y < margin) {
                        stream.close();
                        page = new PDPage(PDRectangle.A4);
                        document.addPage(page);
                        stream = new PDPageContentStream(document, page);
                        stream.setFont(bodyFont, 11);
                        y = page.getMediaBox().getHeight() - margin;
                    }
                    stream.beginText();
                    stream.newLineAtOffset(margin, y);
                    stream.showText(safePdfText(line, bodyFont instanceof PDType0Font));
                    stream.endText();
                    y -= leading;
                }
            } finally {
                stream.close();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new RuntimeException("PDF 导出失败", ex);
        }
    }

    private List<String> wrapLines(String text, int width) {
        List<String> lines = new ArrayList<>();
        for (String raw : text.replace("\r", "").split("\n")) {
            if (raw.isEmpty()) {
                lines.add(" ");
                continue;
            }
            int start = 0;
            while (start < raw.length()) {
                int end = Math.min(raw.length(), start + width);
                lines.add(raw.substring(start, end));
                start = end;
            }
        }
        return lines;
    }

    private String safePdfText(String text, boolean cjkEnabled) {
        return cjkEnabled ? text : text.replaceAll("[^\\x20-\\x7E]", "?");
    }
}
