package com.rag.api.repository;

import com.rag.api.entity.RagDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RagDocumentRepository extends JpaRepository<RagDocument, UUID> {
}
