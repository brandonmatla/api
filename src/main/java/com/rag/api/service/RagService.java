package com.rag.api.service;

import com.rag.api.entity.RagDocument;
import com.rag.api.repository.RagDocumentRepository;
import com.rag.api.repository.RagEmbeddingRepository;
import com.rag.api.util.VectorUtil;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagService {

    private final OllamaService ollamaService;
    private final RagEmbeddingRepository repository;
    private final RagDocumentRepository archivos;

    public RagService(OllamaService ollamaService,
                      RagEmbeddingRepository repository,
                      RagDocumentRepository archivos) {

        this.ollamaService = ollamaService;
        this.repository = repository;
        this.archivos = archivos;
    }

    public String ask(String question) {

        // 🔹 1. Embedding
        List<Double> embedding = ollamaService.generateEmbedding(question);
        String vector = VectorUtil.toPgVector(embedding);

        // 🔹 2. Buscar contexto (LIMITADO)
        List<Object[]> results = repository.searchSimilar(vector);

        String context = results.stream()
                .limit(5) // 🔥 menos ruido
                .map(r -> {
                    String content = r[1].toString();

                    // 🔥 recorte duro
                    if (content.length() > 300) {
                        content = content.substring(0, 300);
                    }

                    return content;
                })
                .collect(Collectors.joining("\n---\n"));

        // 🔹 3. Obtener documentos reales
        List<RagDocument> docs = archivos.findAll();

        List<String> nombresValidos = docs.stream()
                .map(RagDocument::getFileName)
                .toList();

        String documentos = docs.stream()
                .map(doc -> """
                        {
                          "name": "%s"
                        }
                        """.formatted(doc.getFileName()))
                .collect(Collectors.joining(","));

        documentos = "[" + documentos + "]";

        // 🔹 4. PROMPT (simplificado y más fuerte)
        String prompt = """
                TE LLAMAS Braney.
                Responde usando SOLO los nombres EXACTOS de DOCUMENTOS.

                DOCUMENTOS:
                %s

                CONTEXTO:
                %s

                REGLAS:

                - SOLO puedes usar nombres de DOCUMENTOS
                - NO inventar nombres
                - NO explicar nada fuera del formato
                - Si no puedes responder → responde EXACTAMENTE:
                  No se encontró información en los documentos

                FORMATO (solo si aplica):
                
                archivo.pdf - descripción breve

                PREGUNTA:
                %s
                """.formatted(documentos, context, question);

        // 🔹 5. Ejecutar modelo
        String respuesta = ollamaService.generateResponse(prompt);

        System.out.println("\nRESPUESTA RAW:\n" + respuesta);

        // 🔥 6. VALIDACIÓN (CLAVE TOTAL)
        boolean contieneValido = nombresValidos.stream()
                .anyMatch(respuesta::contains);

        if (!contieneValido) {
            return "No se encontró información en los documentos";
        }

        return respuesta;
    }
}