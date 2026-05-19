package com.example.astrastudioopenai.service.knowledge;

import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URL;

@Service
public class DocumentParserService {

    private static final Logger log = LoggerFactory.getLogger(DocumentParserService.class);

    private final AutoDetectParser parser = new AutoDetectParser();

    public String parseToText(String fileUrl) {
        try (InputStream is = new URL(fileUrl).openStream()) {
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            parser.parse(is, handler, metadata, context);
            String text = handler.toString().replaceAll("\\r\\n", "\n").replaceAll("\\r", "\n");
            log.info("Parsed document from url={}, content length={}", fileUrl, text.length());
            return text;
        } catch (Exception e) {
            log.error("Failed to parse document from url={}", fileUrl, e);
            throw new RuntimeException("Document parsing failed: " + e.getMessage(), e);
        }
    }

    public String detectFileType(String fileUrl) {
        if (fileUrl == null) return "txt";
        String lower = fileUrl.toLowerCase();
        if (lower.endsWith(".pdf")) return "pdf";
        if (lower.endsWith(".docx") || lower.endsWith(".doc")) return "docx";
        if (lower.endsWith(".md")) return "md";
        if (lower.endsWith(".txt")) return "txt";
        return "unknown";
    }
}
