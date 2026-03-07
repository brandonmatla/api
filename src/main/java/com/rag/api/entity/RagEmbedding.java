package com.rag.api.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "rag_embeddings")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RagEmbedding {

    @Id
    private UUID id;

    private UUID documentId;
    private Integer chunkIndex;
    private String content;
    private LocalDateTime createdAt;


}