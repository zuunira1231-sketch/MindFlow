package com.cogniflow.service;

import com.cogniflow.dto.ConversationMessageInput;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringWriter;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/** 只识别 ChatGPT 分享页打印出的、左右分栏的可选中文本 PDF。 */
@Component
public class SharedConversationPdfParser {

    public List<ConversationMessageInput> parse(byte[] pdf) throws IOException {
        if (pdf == null || pdf.length < 5 || pdf.length > 15_000_000
                || pdf[0] != '%' || pdf[1] != 'P' || pdf[2] != 'D'
                || pdf[3] != 'F' || pdf[4] != '-') {
            throw new IllegalArgumentException("请上传不超过 15 MB 的 PDF 文件");
        }
        try (PDDocument document = Loader.loadPDF(pdf)) {
            if (document.getNumberOfPages() == 0 || document.getNumberOfPages() > 400) {
                throw new IllegalArgumentException("PDF 页数必须在 1 到 400 之间");
            }
            String fullText = new PDFTextStripper().getText(document);
            if (!fullText.contains("chatgpt.com/s/")
                    && !fullText.contains("chatgpt.com/share/")) {
                throw new IllegalArgumentException(
                        "这不是从 ChatGPT 分享页打印的 PDF，或分享链接页脚未包含在文件中"
                );
            }
            List<ConversationMessageInput> messages = new ArrayList<>();
            Line previousLine = null;
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                float width = document.getPage(page).getMediaBox().getWidth();
                float height = document.getPage(page).getMediaBox().getHeight();
                PositionedStripper stripper = new PositionedStripper(width, height);
                stripper.setStartPage(page + 1);
                stripper.setEndPage(page + 1);
                stripper.writeText(document, new StringWriter());
                for (Line line : stripper.lines()) {
                    String role = line.x >= width * 0.23f ? "user" : "assistant";
                    String normalized = normalizePdfText(line.text);
                    if (!messages.isEmpty()
                            && messages.get(messages.size() - 1).getRole().equals(role)) {
                        ConversationMessageInput previous = messages.get(messages.size() - 1);
                        boolean wrapped = previousLine != null
                                && line.y > previousLine.y
                                && line.y - previousLine.y <= Math.max(24, line.height * 1.8f)
                                && isChineseBoundary(previous.getContent(), normalized);
                        previous.setContent(previous.getContent()
                                + (wrapped ? "" : "\n") + normalized);
                    } else {
                        ConversationMessageInput message = new ConversationMessageInput();
                        message.setRole(role);
                        message.setContent(normalized);
                        messages.add(message);
                    }
                    previousLine = line;
                }
                previousLine = null;
            }
            if (messages.size() < 2 || !messages.get(0).getRole().equals("user")
                    || messages.stream().noneMatch(m -> m.getRole().equals("assistant"))) {
                throw new IllegalArgumentException(
                        "未能辨认分享页 PDF 的用户和 AI 消息，请确认是从完整分享页打印的可选中文本 PDF"
                );
            }
            return messages;
        }
    }

    static String normalizePdfText(String text) {
        StringBuilder result = new StringBuilder();
        text.codePoints().forEach(codePoint -> {
            String character = new String(Character.toChars(codePoint));
            // Safari 打印的中文偶尔被编码为康熙部首／兼容汉字；仅修正这些码位，
            // 不对整段文本做 NFKC，以免改动用户对话里的代码和全角符号。
            boolean compatibilityIdeograph =
                    (codePoint >= 0x2F00 && codePoint <= 0x2FDF)
                            || (codePoint >= 0xF900 && codePoint <= 0xFAFF)
                            || (codePoint >= 0x2F800 && codePoint <= 0x2FA1F);
            // U+2ED3 属于“中日韩部首补充”，NFKC 不会把它转成“长”。
            if (codePoint == 0x2ED3) {
                result.append('长');
            } else {
                result.append(compatibilityIdeograph
                        ? Normalizer.normalize(character, Normalizer.Form.NFKC)
                        : character);
            }
        });
        return result.toString();
    }

    private static boolean isChineseBoundary(String before, String after) {
        if (before.isEmpty() || after.isEmpty()) {
            return false;
        }
        int left = before.codePointBefore(before.length());
        int right = after.codePointAt(0);
        return Character.UnicodeScript.of(left) == Character.UnicodeScript.HAN
                && Character.UnicodeScript.of(right) == Character.UnicodeScript.HAN;
    }

    private record Line(float x, float y, float height, String text) {}

    private static final class PositionedStripper extends PDFTextStripper {
        private final float width;
        private final float height;
        private final List<Line> lines = new ArrayList<>();

        private PositionedStripper(float width, float height) throws IOException {
            this.width = width;
            this.height = height;
            setSortByPosition(true);
        }

        private List<Line> lines() {
            return lines;
        }

        @Override
        protected void writeString(String text, List<TextPosition> positions) {
            if (positions.isEmpty() || text.isBlank()) {
                return;
            }
            float x = positions.get(0).getXDirAdj();
            float y = positions.get(0).getYDirAdj();
            // 分享页页眉、导航与打印页脚不属于对话。
            if (y < 100 || y > height - 45 || x < 20 || x > width - 15) {
                return;
            }
            lines.add(new Line(x, y, positions.get(0).getHeightDir(), text.strip()));
        }
    }
}
