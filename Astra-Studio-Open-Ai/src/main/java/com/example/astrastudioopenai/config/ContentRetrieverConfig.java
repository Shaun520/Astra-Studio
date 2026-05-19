package com.example.astrastudioopenai.config;

import com.example.astrastudioopenai.service.knowledge.RAGRetrievalService;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ContentRetrieverConfig {

  private static final Logger log = LoggerFactory.getLogger(ContentRetrieverConfig.class);

  @Autowired(required = false)
  private RAGRetrievalService ragRetrievalService;

  @Bean
  public ContentRetriever ragContentRetriever() {
    if (ragRetrievalService == null) {
      return query -> {
        log.debug("RAG ContentRetriever not available, returning empty results");
        return List.of();
      };
    }

    return new ContentRetriever() {
      @Override
      public List<Content> retrieve(Query query) {
        var chunks = ragRetrievalService.retrieve(query.text());
        return chunks.stream()
            .map(chunk -> Content.from(
                "[" + (chunk.getDocumentName() != null ? chunk.getDocumentName() : "unknown") +
                    "] " + (chunk.getContent() != null ? chunk.getContent() : chunk.getContentSnippet())))
            .toList();
      }
    };
  }
}
