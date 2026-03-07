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
@Table(name = "rag_documents")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RagDocument {

    @Id
    private UUID id;

    private String fileName;
    private String filePath;
    private String fileHash;
    private Long fileSize;
    private String status;
    private LocalDateTime createdAt;


}