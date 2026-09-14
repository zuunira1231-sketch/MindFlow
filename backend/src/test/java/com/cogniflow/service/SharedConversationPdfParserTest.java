package com.cogniflow.service;

import com.cogniflow.dto.ConversationMessageInput;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedConversationPdfParserTest {

    @Test
    void normalizesOnlyCompatibilityIdeographs() {
        assertEquals("用色白小长", SharedConversationPdfParser.normalizePdfText("⽤⾊⽩⼩⻓"));
        assertEquals("ａ＝１", SharedConversationPdfParser.normalizePdfText("ａ＝１"));
    }

    @Test
    void parsesPositionedMessagesAndSkipsHeaderFooter() throws Exception {
        byte[] pdf;
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream =
                         new PDPageContentStream(document, page)) {
                write(stream, 30, 800, "Printed title");
                write(stream, 170, 680, "User question");
                write(stream, 30, 630, "Assistant answer");
                write(stream, 450, 580, "Next question");
                write(stream, 30, 530, "Next answer");
                write(stream, 30, 15, "https://chatgpt.com/s/ignored");
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            pdf = output.toByteArray();
        }
        List<ConversationMessageInput> messages =
                new SharedConversationPdfParser().parse(pdf);
        assertEquals(4, messages.size());
        assertEquals("User question", messages.get(0).getContent());
        assertEquals("Assistant answer", messages.get(1).getContent());
        assertEquals("Next question", messages.get(2).getContent());
        assertEquals("Next answer", messages.get(3).getContent());
    }

    private void write(PDPageContentStream stream, float x, float y,
                       String text) throws Exception {
        stream.beginText();
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        stream.newLineAtOffset(x, y);
        stream.showText(text);
        stream.endText();
    }

    @Test
    void parsesPrintedSharePageWhenSampleIsProvided() throws Exception {
        String sample = System.getProperty("mindflow.samplePdf");
        if (sample == null) {
            return;
        }
        byte[] pdf = Files.readAllBytes(Path.of(sample));
        List<ConversationMessageInput> messages =
                new SharedConversationPdfParser().parse(pdf);
        assertEquals(4, messages.size());
        assertEquals("user", messages.get(0).getRole());
        assertEquals("assistant", messages.get(1).getRole());
        assertEquals("user", messages.get(2).getRole());
        assertEquals("assistant", messages.get(3).getRole());
        assertTrue(messages.get(0).getContent().contains("天空"));
        assertTrue(messages.get(0).getContent().contains("天空通常"));
        assertTrue(messages.get(1).getContent().contains("进我们的眼睛"));
        assertTrue(messages.get(1).getContent().contains("波长较短"));
        assertTrue(messages.get(3).getContent().contains("云"));
        assertTrue(messages.get(3).getContent().contains("眼睛"));
        assertTrue(messages.stream().noneMatch(m -> m.getContent().contains("chatgpt.com/s/")));
    }
}
