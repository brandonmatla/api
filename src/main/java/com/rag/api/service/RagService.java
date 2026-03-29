package com.rag.api.service;

import com.rag.api.entity.RagDocument;
import com.rag.api.repository.RagDocumentRepository;
import com.rag.api.repository.RagEmbeddingRepository;
import com.rag.api.util.VectorUtil;
import org.springframework.stereotype.Service;

import java.util.*;
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

        // 🔹 Detectar tipo de pregunta
        String q = question.toLowerCase();

        boolean preguntaSobreArchivos =
                q.contains("archivo") ||
                        q.contains("documento") ||
                        q.contains("pdf");

        // 🔹 1. Embedding
        List<Double> embedding = ollamaService.generateEmbedding(question);
        String vector = VectorUtil.toPgVector(embedding);

        // 🔹 2. Buscar contexto
        List<Object[]> results = repository.searchSimilar(vector);

        if (results.isEmpty()) {
            return "No se encontró información en los documentos";
        }

        // 🔹 3. Obtener documentos
        List<RagDocument> docs = archivos.findAll();

        Map<String, String> docMap = docs.stream()
                .collect(Collectors.toMap(
                        doc -> doc.getId().toString(),
                        RagDocument::getFileName
                ));

        List<String> nombresValidos = docs.stream()
                .map(RagDocument::getFileName)
                .toList();

        // 🔹 4. Construir contexto
        String context = results.stream()
                .limit(5)
                .map(r -> {
                    String documentId = r[0].toString();
                    String content = r[1].toString();

                    if (content.length() > 300) {
                        content = content.substring(0, 300);
                    }

                    String nombre = docMap.getOrDefault(documentId, "desconocido");

                    return nombre + ": " + content;
                })
                .collect(Collectors.joining("\n---\n"));

        // 🔹 5. Lista documentos
        String documentos = docs.stream()
                .map(doc -> """
                        {
                          "name": "%s"
                        }
                        """.formatted(doc.getFileName()))
                .collect(Collectors.joining(","));

        documentos = "[" + documentos + "]";

        // 🔥 6. PROMPT DINÁMICO
        String prompt;

        if (preguntaSobreArchivos) {

            // 👉 FORMATO ESTRICTO SOLO PARA ARCHIVOS
            prompt = """
                    Responde usando SOLO la información del CONTEXTO.

                    DOCUMENTOS:
                    %s

                    CONTEXTO:
                    %s

                    INSTRUCCIONES:

                    - Usa SOLO nombres EXACTOS de DOCUMENTOS
                    - NO inventes nombres
                    - Describe brevemente el contenido

                    FORMATO:

                    archivo.pdf - descripción breve

                    Si no hay información suficiente:
                    responde EXACTAMENTE:

                    No se encontró información en los documentos

                    Responde usando SOLO la información del CONTEXTO.
                    PREGUNTA:
                    %s
                    """.formatted(documentos, context, question);

        } else {

            // 👉 RESPUESTA LIBRE (sin formato forzado)
            prompt = """
                    Responde usando SOLO la información del CONTEXTO.

                    CONTEXTO:
                    %s

                    INSTRUCCIONES:

                    - Responde de forma natural
                    - NO inventes información
                    - NO uses conocimiento externo

                    Si no hay información suficiente:
                    responde EXACTAMENTE:

                    No se encontró información en los documentos
                
                    Responde usando SOLO la información del CONTEXTO.
                    
                    PREGUNTA:
                    %s
                    """.formatted(context, question);
        }

        // 🔹 7. Ejecutar modelo
        String respuesta = ollamaService.generateResponse(prompt);

        // 🔥 8. Validación SOLO si es pregunta de archivos
        if (preguntaSobreArchivos) {

            boolean contieneValido = nombresValidos.stream()
                    .anyMatch(nombre -> respuesta.trim().startsWith(nombre));

            if (!contieneValido) {
                return "No se encontró información en los documentos";
            }
        }

        return respuesta;
    }
}