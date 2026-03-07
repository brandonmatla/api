package com.rag.api.service;

import com.rag.api.repository.RagEmbeddingRepository;
import com.rag.api.util.VectorUtil;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RagService {

    private final OllamaService ollamaService;
    private final RagEmbeddingRepository repository;

    public RagService(OllamaService ollamaService,
                      RagEmbeddingRepository repository) {

        this.ollamaService = ollamaService;
        this.repository = repository;
    }

    public String ask(String question) {

        List<Double> embedding = ollamaService.generateEmbedding(question);

        String vector = VectorUtil.toPgVector(embedding);

        List<String> context = repository.searchSimilar(vector);

        String joinedContext = String.join("\n", context);

        String prompt = """
        Responde usando únicamente el contexto proporcionado.
        Si la respuesta no está en el contexto, responde: "No se encontró información en los documentos".

        CONTEXTO:
        %s

        PREGUNTA:
        %s
        """.formatted(joinedContext, question);

        return ollamaService.generateResponse(prompt);
    }
}