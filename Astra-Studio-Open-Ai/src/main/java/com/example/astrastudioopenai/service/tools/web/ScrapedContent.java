package com.example.astrastudioopenai.service.tools.web;

import lombok.Getter;

import java.util.*;
@Getter
public class ScrapedContent {

    private final String title;
    private final String text;
    private final Map<String, String> metadata;
    private final List<String> links;

    public ScrapedContent(String title, String text, Map<String, String> metadata, List<String> links) {
        this.title = title != null ? title : "";
        this.text = text != null ? text : "";
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        this.links = links != null ? new ArrayList<>(links) : new ArrayList<>();
    }
    public Map<String, String> getMetadata() {
        return Collections.unmodifiableMap(metadata);
    }

    public List<String> getLinks() {
        return Collections.unmodifiableList(links);
    }

    @Override
    public String toString() {
        return "ScrapedContent{" +
                "title='" + title + '\'' +
                ", textLength=" + text.length() +
                ", metadataCount=" + metadata.size() +
                ", linkCount=" + links.size() +
                '}';
    }
}
