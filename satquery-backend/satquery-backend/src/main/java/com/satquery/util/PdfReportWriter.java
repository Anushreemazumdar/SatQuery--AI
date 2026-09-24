package com.satquery.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Minimal PDFBox helper providing automatic paging and word wrapping for simple text reports. */
public class PdfReportWriter implements AutoCloseable {

    private static final float MARGIN = 50f;
    private static final float LINE_SPACING = 1.35f;
    private static final float BODY_SIZE = 10.5f;

    private final PDDocument document = new PDDocument();
    private final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final float pageHeight = PDRectangle.A4.getHeight();
    private final float usableWidth = PDRectangle.A4.getWidth() - 2 * MARGIN;

    private PDPageContentStream content;
    private float y;

    public PdfReportWriter() throws IOException {
        newPage();
    }

    public void title(String text) throws IOException {
        drawWrapped(text, bold, 24, MARGIN, usableWidth);
    }

    public void subtitle(String text) throws IOException {
        drawWrapped(text, regular, 14, MARGIN, usableWidth);
    }

    public void heading(String text) throws IOException {
        ensureSpace(46);
        y -= 12;
        drawWrapped(text, bold, 13, MARGIN, usableWidth);
        y -= 2;
    }

    public void paragraph(String text) throws IOException {
        drawWrapped(text, regular, BODY_SIZE, MARGIN, usableWidth);
    }

    public void keyValue(String label, String value) throws IOException {
        drawWrapped(label + ": " + (value == null || value.isBlank() ? "-" : value), regular, BODY_SIZE, MARGIN, usableWidth);
    }

    public void bullet(String text) throws IOException {
        drawWrapped("- " + text, regular, BODY_SIZE, MARGIN + 14, usableWidth - 14);
    }

    public void spacer(float points) throws IOException {
        ensureSpace(points);
        y -= points;
    }

    public byte[] toBytes() throws IOException {
        if (content != null) {
            content.close();
            content = null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.save(out);
        return out.toByteArray();
    }

    @Override
    public void close() throws IOException {
        if (content != null) {
            content.close();
            content = null;
        }
        document.close();
    }

    // ---------------------------------------------------------------- internals

    private void newPage() throws IOException {
        if (content != null) {
            content.close();
        }
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        content = new PDPageContentStream(document, page);
        y = pageHeight - MARGIN;
    }

    private void ensureSpace(float needed) throws IOException {
        if (y - needed < MARGIN) {
            newPage();
        }
    }

    private void drawWrapped(String text, PDFont font, float size, float x, float width) throws IOException {
        for (String line : wrap(text, font, size, width)) {
            float lineHeight = size * LINE_SPACING;
            ensureSpace(lineHeight);
            y -= lineHeight;
            content.beginText();
            content.setFont(font, size);
            content.newLineAtOffset(x, y);
            content.showText(line);
            content.endText();
        }
    }

    private List<String> wrap(String text, PDFont font, float size, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String paragraph : sanitize(text).split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                for (String piece : breakLongWord(word, font, size, maxWidth)) {
                    String candidate = line.length() == 0 ? piece : line + " " + piece;
                    if (width(candidate, font, size) <= maxWidth) {
                        line = new StringBuilder(candidate);
                    } else {
                        if (line.length() > 0) {
                            lines.add(line.toString());
                        }
                        line = new StringBuilder(piece);
                    }
                }
            }
            lines.add(line.toString());
        }
        return lines;
    }

    private List<String> breakLongWord(String word, PDFont font, float size, float maxWidth) throws IOException {
        List<String> pieces = new ArrayList<>();
        if (width(word, font, size) <= maxWidth) {
            pieces.add(word);
            return pieces;
        }
        StringBuilder current = new StringBuilder();
        for (char c : word.toCharArray()) {
            if (width(current.toString() + c, font, size) > maxWidth && current.length() > 0) {
                pieces.add(current.toString());
                current = new StringBuilder();
            }
            current.append(c);
        }
        if (current.length() > 0) {
            pieces.add(current.toString());
        }
        return pieces;
    }

    private float width(String text, PDFont font, float size) throws IOException {
        return font.getStringWidth(text) / 1000f * size;
    }

    /** Standard-14 fonts only support WinAnsi characters; replace everything else. */
    static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (c == '\n') {
                sb.append('\n');
            } else if (c == '\r') {
                continue;
            } else if (c == '\t') {
                sb.append(' ');
            } else if ((c >= 32 && c <= 126) || (c >= 160 && c <= 255)) {
                sb.append(c);
            } else {
                sb.append('?');
            }
        }
        return sb.toString();
    }
}
