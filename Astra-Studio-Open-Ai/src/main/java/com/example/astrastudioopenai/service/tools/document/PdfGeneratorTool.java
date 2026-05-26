package com.example.astrastudioopenai.service.tools.document;

import com.example.astrastudioopenai.service.tools.ToolExecutionException;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PdfGeneratorTool {
    private static final Logger logger = LoggerFactory.getLogger(PdfGeneratorTool.class);

    private static final float MARGIN = 50;
    private static final float LINE_HEIGHT = 14f;
    private static final float FONT_SIZE_TITLE = 24f;
    private static final float FONT_SIZE_H1 = 18f;
    private static final float FONT_SIZE_H2 = 14f;
    private static final float FONT_SIZE_BODY = 11f;

    private static final int CACHE_MAX_SIZE = 50;
    private static final long CACHE_TTL_MS = 30 * 60 * 1000; // 30分钟

    @Value("${pdf-generator.output-directory:./generated-pdfs}")
    private String outputDirectory;

    private final ConcurrentHashMap<String, CachedPdfResult> pdfCache = new ConcurrentHashMap<>();

    @Tool("从Markdown内容生成PDF文档，返回PDF文件信息")
    public PdfGenerationResult generateFromMarkdown(
            @P("Markdown格式的内容文本") String content,
            @P("PDF生成选项（可选）") PdfOptions options) throws ToolExecutionException {

        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("内容不能为空");
        }

        if (options == null) {
            options = PdfOptions.defaults();
        }

        String cacheKey = computeCacheKey(content, options);

        CachedPdfResult cached = pdfCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            logger.info("📦 PDF 缓存命中: {} (节省重复生成)", cached.result.getFileName());
            return cached.result;
        }

        logger.info("Generating PDF from Markdown: {} chars, options={}", content.length(), options);

        try {
            Path outputPath = ensureOutputDirectory();
            String fileName = "document_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                    + "_" + UUID.randomUUID().toString().substring(0, 8) + ".pdf";
            Path filePath = outputPath.resolve(fileName);

            byte[] pdfBytes = generatePdfBytes(content, options);

            Files.write(filePath, pdfBytes);

            int pageCount = countPages(content);

            PdfGenerationResult result = new PdfGenerationResult(
                    true,
                    "✅ PDF 文档已成功生成！\n\n" +
                            "📄 **文件名**: " + fileName + "\n" +
                            "📊 **页数**: " + pageCount + " 页\n" +
                            "📏 **纸张大小**: " + options.getPageSize() + "\n" +
                            "💾 **文件大小**: " + formatFileSize(pdfBytes.length) + "\n" +
                            "📍 **保存路径**: " + filePath.toAbsolutePath() + "\n\n" +
                            "💡 提示: 您可以通过 API `/api/tools/pdfGeneratorTool/download?fileName=" + fileName + "` 下载此文件",
                    fileName,
                    pdfBytes.length,
                    pageCount,
                    options.getPageSize().name(),
                    "/api/tools/pdfGeneratorTool/download?fileName=" + fileName);

            logger.info("PDF generated successfully: {}, size: {}", fileName, pdfBytes.length);

            cacheResult(cacheKey, result);
            return result;

        } catch (IOException | ToolExecutionException e) {
            throw new ToolExecutionException("generate_pdf", "PDF生成失败: " + e.getMessage(), e);
        }
    }

    private byte[] generatePdfBytes(String content, PdfOptions options) throws IOException, ToolExecutionException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(getPageSize(options));
            document.addPage(page);

            PDFont font = loadChineseFont(document, options.getFontFamily());

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                renderMarkdownToPdf(contentStream, document, page, content, font, options);
                addWatermarkIfPresent(contentStream, page, options);
                addHeaderFooterIfPresent(contentStream, page, options);
            }

            document.save(outputStream);
        }

        return outputStream.toByteArray();
    }

    private Path ensureOutputDirectory() throws IOException {
        Path dir = Paths.get(outputDirectory);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
            logger.info("Created output directory: {}", dir.toAbsolutePath());
        }
        return dir;
    }

    private int countPages(String markdownContent) {
        String[] lines = markdownContent.split("\n");
        int estimatedPages = 1;
        int linesPerPage = 40;

        for (String line : lines) {
            if (line.trim().startsWith("# "))
                estimatedPages++;
            else if (!line.trim().isEmpty()) {
                if (estimatedPages * linesPerPage < lines.length) {
                    estimatedPages++;
                }
            }
        }

        return Math.max(1, Math.min(estimatedPages, 100));
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    private PDRectangle getPageSize(PdfOptions options) {
        return switch (options.getPageSize()) {
            case A4 -> PDRectangle.A4;
            case LETTER -> PDRectangle.LETTER;
            case A3 -> PDRectangle.A3;
        };
    }

    private PDFont loadChineseFont(PDDocument document, String fontFamily) throws IOException, ToolExecutionException {
        String[] ttfFonts = {
                "C:/Windows/Fonts/msyh.ttf",
                "C:/Windows/Fonts/msyhbd.ttf",
                "/fonts/" + fontFamily + ".ttf",
                "C:/Windows/Fonts/" + fontFamily + ".ttf",
                "C:/Windows/Fonts/simhei.ttf",
                "C:/Windows/Fonts/simsun.ttc,0",
                "C:/Windows/Fonts/arial.ttf",
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
        };

        for (String fontPath : ttfFonts) {
            Path path = Paths.get(fontPath);
            if (Files.exists(path)) {
                logger.info("Loading TTF font from: {}", fontPath);
                try {
                    return PDType0Font.load(document, path.toFile());
                } catch (Exception e) {
                    logger.warn("Failed to load font {}: {}", fontPath, e.getMessage());
                }
            }
        }

        logger.warn("No TTF font found, trying classpath resources");

        java.io.InputStream[] fontStreams = {
                getClass().getResourceAsStream("/fonts/SimSun.ttf"),
                getClass().getResourceAsStream("/fonts/noto.ttf"),
                getClass().getResourceAsStream("/fonts/DejaVuSans.ttf")
        };

        for (java.io.InputStream fontStream : fontStreams) {
            if (fontStream != null) {
                try {
                    logger.info("Loading embedded TTF font from classpath");
                    return PDType0Font.load(document, fontStream);
                } catch (Exception e) {
                    logger.warn("Failed to load embedded font: {}", e.getMessage());
                }
            }
        }

        throw new ToolExecutionException("generate_pdf",
                "无法加载有效的TTF字体文件。\n\n" +
                        "请确保以下任一位置有 .ttf 格式的字体文件:\n" +
                        "1. C:\\Windows\\Fonts\\simhei.ttf (黑体)\n" +
                        "2. C:\\Windows\\Fonts\\msyh.ttf (微软雅黑)\n" +
                        "3. 项目 resources/fonts/ 目录下放置 .ttf 字体文件\n\n" +
                        "注意: PDFBox 不支持 .ttc 格式的字体集合文件！\n" +
                        "当前搜索的字体名称: " + fontFamily,
                null);
    }

    private void renderMarkdownToPdf(PDPageContentStream stream, PDDocument doc, PDPage page,
            String markdown, PDFont font, PdfOptions options) throws IOException {
        float yPosition = page.getMediaBox().getHeight() - MARGIN;
        float xPosition = MARGIN;
        float maxWidth = page.getMediaBox().getWidth() - 2 * MARGIN;

        String cleaned = preprocessMarkdown(markdown);
        logger.info("Preprocessed Markdown (first 200 chars): {}",
                cleaned.substring(0, Math.min(200, cleaned.length())));
        String[] lines = cleaned.split("\n");

        for (String line : lines) {
            String trimmedLine = line.trim();

            if (trimmedLine.isEmpty()) {
                yPosition -= LINE_HEIGHT / 2;
                continue;
            }

            if (trimmedLine.startsWith("# ")) {
                yPosition = checkAndAddNewPage(stream, doc, page, yPosition, FONT_SIZE_TITLE * 2);
                stream.setFont(font, FONT_SIZE_TITLE);
                stream.beginText();
                stream.newLineAtOffset(xPosition, yPosition);
                stream.showText(sanitizeForPdf(stripMarkdownFormatting(trimmedLine.substring(2))));
                stream.endText();
                yPosition -= FONT_SIZE_TITLE * 2;
            } else if (trimmedLine.startsWith("## ")) {
                yPosition = checkAndAddNewPage(stream, doc, page, yPosition, FONT_SIZE_H1 * 1.5f);
                stream.setFont(font, FONT_SIZE_H1);
                stream.beginText();
                stream.newLineAtOffset(xPosition, yPosition);
                stream.showText(sanitizeForPdf(stripMarkdownFormatting(trimmedLine.substring(3))));
                stream.endText();
                yPosition -= FONT_SIZE_H1 * 1.5f;
            } else if (trimmedLine.startsWith("### ")) {
                yPosition = checkAndAddNewPage(stream, doc, page, yPosition, FONT_SIZE_H2 * 1.3f);
                stream.setFont(font, FONT_SIZE_H2);
                stream.beginText();
                stream.newLineAtOffset(xPosition, yPosition);
                stream.showText(sanitizeForPdf(stripMarkdownFormatting(trimmedLine.substring(4))));
                stream.endText();
                yPosition -= FONT_SIZE_H2 * 1.3f;
            } else if (trimmedLine.startsWith("- ") || trimmedLine.startsWith("* ")) {
                yPosition = checkAndAddNewPage(stream, doc, page, yPosition, LINE_HEIGHT);
                stream.setFont(font, FONT_SIZE_BODY);
                stream.beginText();
                stream.newLineAtOffset(xPosition + 15, yPosition);
                String text = sanitizeForPdf(stripMarkdownFormatting(trimmedLine.substring(2)));
                stream.showText("- " + wrapText(text, font, maxWidth - 20, FONT_SIZE_BODY));
                stream.endText();
                yPosition -= LINE_HEIGHT;
            } else {
                yPosition = checkAndAddNewPage(stream, doc, page, yPosition, LINE_HEIGHT);
                stream.setFont(font, FONT_SIZE_BODY);
                stream.beginText();
                stream.newLineAtOffset(xPosition, yPosition);
                String cleanText = sanitizeForPdf(stripMarkdownFormatting(trimmedLine));
                stream.showText(wrapText(cleanText, font, maxWidth, FONT_SIZE_BODY));
                stream.endText();
                yPosition -= LINE_HEIGHT;
            }
        }
    }

    private String preprocessMarkdown(String markdown) {
        String result = markdown;

        result = result.replace("<br>", "\n");
        result = result.replace("<br/>", "\n");
        result = result.replace("<br />", "\n");
        result = result.replace("</p>", "\n\n");
        result = result.replaceAll("<p[^>]*>", "");
        result = result.replaceAll("<strong[^>]*>", "**");
        result = result.replaceAll("</strong>", "**");
        result = result.replaceAll("<b[^>]*>", "**");
        result = result.replaceAll("</b>", "**");
        result = result.replaceAll("<em[^>]*>", "*");
        result = result.replaceAll("</em>", "*");
        result = result.replaceAll("<i[^>]*>", "*");
        result = result.replaceAll("</i>", "*");
        result = result.replaceAll("<code[^>]*>", "`");
        result = result.replaceAll("</code>", "`");
        result = result.replaceAll("<a[^>]*href=\"([^\"]*)\"[^>]*>([^<]*)</a>", "$2 ($1)");
        result = result.replaceAll("<li[^>]*>", "\n- ");
        result = result.replaceAll("</li>", "");
        result = result.replaceAll("<ul[^>]*>", "\n");
        result = result.replaceAll("</ul>", "");
        result = result.replaceAll("<ol[^>]*>", "\n");
        result = result.replaceAll("</ol>", "");
        result = result.replaceAll("<h[1-6][^>]*>", "\n\n");
        result = result.replaceAll("</h[1-6]>", "\n");
        result = result.replaceAll("<table[^>]*>", "\n");
        result = result.replaceAll("</table>", "\n");
        result = result.replaceAll("<tr[^>]*>", "\n");
        result = result.replaceAll("</tr>", "");
        result = result.replaceAll("<t[dh][^>]*>", " | ");
        result = result.replaceAll("</t[dh]>", "");
        result = result.replaceAll("<[^>]+>", "");

        result = convertMarkdownTables(result);
        result = convertMarkdownLists(result);

        return result;
    }

    private String convertMarkdownTables(String text) {
        String[] lines = text.split("\n");
        StringBuilder sb = new StringBuilder();
        boolean inTable = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                if (!inTable) {
                    sb.append("\n");
                    inTable = true;
                }

                String[] cells = trimmed.split("\\|");

                if (isTableSeparator(cells)) {
                    continue;
                }

                StringBuilder rowText = new StringBuilder();
                for (int i = 1; i < cells.length - 1; i++) {
                    String cell = cells[i].trim();
                    if (cell.isEmpty())
                        cell = "-";
                    if (rowText.length() > 0)
                        rowText.append("  |  ");
                    rowText.append(stripMarkdownFormatting(cell));
                }
                sb.append("  ").append(rowText.toString()).append("\n");
            } else {
                if (inTable) {
                    sb.append("\n");
                    inTable = false;
                }
                sb.append(line).append("\n");
            }
        }

        return sb.toString().trim();
    }

    private boolean isTableSeparator(String[] cells) {
        for (int i = 1; i < cells.length - 1; i++) {
            String cell = cells[i].trim();
            if (!cell.matches("^[-:]+$")) {
                return false;
            }
        }
        return true;
    }

    private String convertMarkdownLists(String text) {
        String[] lines = text.split("\n");
        StringBuilder sb = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.matches("^(\\s*)([-*+]|\\d+\\.)(\\s+).*$")) {
                int indent = line.length() - line.stripLeading().length();
                String prefix = "   ".repeat(Math.max(0, indent / 2));
                String content = trimmed.replaceFirst("^([-*+]|\\d+\\.)(\\s+)", "");

                if (trimmed.matches("^\\d+\\..*")) {
                    String num = trimmed.split("\\.", 2)[0];
                    sb.append(prefix).append(num).append(". ").append(content).append("\n");
                } else {
                    sb.append(prefix).append("- ").append(content).append("\n");
                }
            } else {
                sb.append(line).append("\n");
            }
        }

        return sb.toString().trim();
    }

    private String stripMarkdownFormatting(String text) {
        String result = text;
        result = result.replaceAll("\\*\\*(.+?)\\*\\*", "$1");
        result = result.replaceAll("\\*(.+?)\\*", "$1");
        result = result.replaceAll("`(.+?)`", "$1");
        result = result.replaceAll("\\[(.+?)\\]\\(.+?\\)", "$1");
        return result.trim();
    }

    private String sanitizeForPdf(String text) {
        if (text == null)
            return "";

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            switch (c) {
                case '\u2022':
                    sb.append("-");
                    break;
                case '\u2023':
                    sb.append("-");
                    break;
                case '\u25B6':
                    sb.append(">");
                    break;
                case '\u25C0':
                    sb.append("<");
                    break;
                case '\u25CF':
                    sb.append("*");
                    break;
                case '\u25CB':
                    sb.append("o");
                    break;
                case '\u2713':
                    sb.append("[v]");
                    break;
                case '\u2717':
                    sb.append("[x]");
                    break;
                case '\u2605':
                    sb.append("*");
                    break;
                case '\u2606':
                    sb.append("*");
                    break;
                case '\u2665':
                    sb.append("♥");
                    break;
                case '\u2666':
                    sb.append("♦");
                    break;
                case '\u2663':
                    sb.append("♣");
                    break;
                case '\u2660':
                    sb.append("♠");
                    break;
                case '\u2190':
                    sb.append("<-");
                    break;
                case '\u2192':
                    sb.append("->");
                    break;
                case '\u2191':
                    sb.append("^");
                    break;
                case '\u2193':
                    sb.append("v");
                    break;
                case '\u21D0':
                    sb.append("<=");
                    break;
                case '\u21D2':
                    sb.append("=>");
                    break;
                case '\u21E0':
                    sb.append("<=");
                    break;
                case '\u21E4':
                    sb.append("<=");
                    break;
                case '\u21E5':
                    sb.append("=>");
                    break;
                case '\u21E8':
                    sb.append("=>");
                    break;
                case '\u2500':
                    sb.append("-");
                    break;
                case '\u2502':
                    sb.append("|");
                    break;
                case '\u250C':
                    sb.append("+");
                    break;
                case '\u2510':
                    sb.append("+");
                    break;
                case '\u2514':
                    sb.append("+");
                    break;
                case '\u2518':
                    sb.append("+");
                    break;
                case '\u251C':
                    sb.append("+");
                    break;
                case '\u2524':
                    sb.append("+");
                    break;
                case '\u2534':
                    sb.append("+");
                    break;
                case '\u252C':
                    sb.append("+");
                    break;
                case '\u253C':
                    sb.append("+");
                    break;
                case '\u2550':
                    sb.append("=");
                    break;
                case '\u2551':
                    sb.append("|");
                    break;
                case '\u2264':
                    sb.append("<=");
                    break;
                case '\u2265':
                    sb.append(">=");
                    break;
                case '\u2260':
                    sb.append("!=");
                    break;
                case '\u00A0':
                    sb.append(" ");
                    break;
                case '\u200B':
                    break;
                case '\u200D':
                    break;
                case '\uFEFF':
                    break;
                default:
                    if (c >= 32 && c < 127) {
                        sb.append(c);
                    } else if (c >= 0x4E00 && c <= 0x9FFF) {
                        sb.append(c);
                    } else if (c >= 0x3000 && c <= 0x303F) {
                        sb.append(c);
                    } else if (c >= 0xFF00 && c <= 0xFFEF) {
                        sb.append(c);
                    } else if (c > 127) {
                        sb.append(c);
                    } else {
                        sb.append(' ');
                    }
            }
        }
        return sb.toString();
    }

    private float checkAndAddNewPage(PDPageContentStream stream, PDDocument doc,
            PDPage currentPage, float currentY, float requiredSpace) throws IOException {
        if (currentY < MARGIN + requiredSpace) {
            PDPage newPage = new PDPage(currentPage.getMediaBox());
            doc.addPage(newPage);
            currentY = newPage.getMediaBox().getHeight() - MARGIN;
        }
        return currentY;
    }

    private String wrapText(String text, PDFont font, float maxWidth, float fontSize) throws IOException {
        StringBuilder result = new StringBuilder();
        StringBuilder currentLine = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            currentLine.append(c);

            float width = font.getStringWidth(currentLine.toString()) / 1000 * fontSize;
            if (width > maxWidth && currentLine.length() > 1) {
                currentLine.deleteCharAt(currentLine.length() - 1);
                result.append(currentLine.toString()).append("\n");
                currentLine = new StringBuilder();
                currentLine.append(c);
            }
        }

        result.append(currentLine);
        return result.toString();
    }

    private void addWatermarkIfPresent(PDPageContentStream stream, PDPage page, PdfOptions options)
            throws IOException {
        if (options.getWatermark() != null && !options.getWatermark().isEmpty()) {
            stream.setLineWidth(0.5f);
            float centerX = page.getMediaBox().getWidth() / 2;
            float centerY = page.getMediaBox().getHeight() / 2;
            stream.beginText();
            stream.newLineAtOffset(centerX - 100, centerY);
            stream.showText(sanitizeForPdf(options.getWatermark()));
            stream.endText();
        }
    }

    private void addHeaderFooterIfPresent(PDPageContentStream stream, PDPage page, PdfOptions options)
            throws IOException {
        float pageWidth = page.getMediaBox().getWidth();

        if (options.getHeaderText() != null) {
            stream.beginText();
            stream.newLineAtOffset(MARGIN, page.getMediaBox().getHeight() - 30);
            stream.showText(sanitizeForPdf(options.getHeaderText()));
            stream.endText();
        }

        if (options.getFooterText() != null) {
            stream.beginText();
            stream.newLineAtOffset(MARGIN, 30);
            stream.showText(sanitizeForPdf(options.getFooterText()));
            stream.endText();
        }
    }

    private String computeCacheKey(String content, PdfOptions options) {
        try {
            String input = content + "|" + options.getPageSize() + "|" + options.getFontFamily()
                    + "|" + options.getMargin();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(content.hashCode());
        }
    }

    private void cacheResult(String cacheKey, PdfGenerationResult result) {
        if (pdfCache.size() >= CACHE_MAX_SIZE) {
            String oldestKey = null;
            long oldestTime = Long.MAX_VALUE;
            for (var entry : pdfCache.entrySet()) {
                if (entry.getValue().createdAt < oldestTime) {
                    oldestTime = entry.getValue().createdAt;
                    oldestKey = entry.getKey();
                }
            }
            if (oldestKey != null) {
                pdfCache.remove(oldestKey);
            }
        }
        pdfCache.put(cacheKey, new CachedPdfResult(result));
    }

    private static class CachedPdfResult {
        final PdfGenerationResult result;
        final long createdAt;

        CachedPdfResult(PdfGenerationResult result) {
            this.result = result;
            this.createdAt = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > CACHE_TTL_MS;
        }
    }
}