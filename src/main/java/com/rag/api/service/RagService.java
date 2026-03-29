package com.rag.api.service;

import com.rag.api.entity.RagDocument;
import com.rag.api.repository.RagDocumentRepository;
import com.rag.api.repository.RagEmbeddingRepository;
import com.rag.api.util.VectorUtil;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
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

        // 1. Generar embedding
        List<Double> embedding = ollamaService.generateEmbedding(question);
        String vector = VectorUtil.toPgVector(embedding);

        // 2. Buscar chunks similares
        List<Object[]> results = repository.searchSimilar(vector);

        // 3. Agrupar contenido por document_id 🔥 (CLAVE)
        Map<Object, String> contextByDoc = results.stream()
                .collect(Collectors.groupingBy(
                        r -> r[0], // 🔥 SIN toString()
                        Collectors.mapping(r -> r[1].toString(), Collectors.joining("\n"))
                ));

        // 4. Construir DOCUMENTOS base

        List<RagDocument> docs = contextByDoc.keySet().stream()
                .map(key -> {
                    UUID id = key instanceof UUID ? (UUID) key : UUID.fromString(key.toString());
                    return archivos.findById(id).orElse(null);
                })
                .filter(Objects::nonNull)
                .toList();

        String documentos = docs.stream()
                .map(doc -> """
                        {
                          "id": "%s",
                          "name": "%s",
                          "createdAt": "%s"
                        }
                        """.formatted(
                        doc.getId(),
                        doc.getFileName(),
                        doc.getCreatedAt()
                ))
                .collect(Collectors.joining(","));

        documentos = "[" + documentos + "]";

        // 5. Construir DOCUMENTOS + CONTENIDO (🔥 RAG real)
        String documentosConContenido = contextByDoc.entrySet()
                .stream()
                .map(entry -> {
                    Object key = entry.getKey();

                    UUID docId;

                    if (key instanceof UUID) {
                        docId = (UUID) key;
                    } else {
                        docId = UUID.fromString(key.toString());
                    }

                    var doc = archivos.findById(docId).orElse(null);
                    if (doc == null) return null;

                    return """
                                {
                                  "id": "%s",
                                  "name": "%s",
                                  "content": "%s"
                                }
                            """.formatted(
                            doc.getId(),
                            doc.getFileName(),
                            entry.getValue().replace("\"", "'")
                    );
                })
                .filter(e -> e != null)
                .collect(Collectors.joining(","));

        documentosConContenido = "[" + documentosConContenido + "]";

        // DEBUG (opcional)
        System.out.println("DOCUMENTOS:");
        System.out.println(documentos);
        System.out.println("\nDOCUMENTOS_CON_CONTENIDO:");
        System.out.println(documentosConContenido);

        // 6. Prompt final (blindado)
        String prompt = """
                Eres un asistente que responde preguntas sobre documentos.
                
                DOCUMENTOS:
                %s
                
                DOCUMENTOS_CON_CONTENIDO:
                %s
                
                REGLAS OBLIGATORIAS:
                
                1. Los nombres de archivos SOLO pueden salir del campo "name" en DOCUMENTOS.
                2. El campo "content" SOLO se usa para describir, NUNCA para crear nombres.
                3. NO inventes nombres.
                4. NO modifiques nombres.
                5. NO uses conocimiento externo.
                
                PROHIBIDO:
                
                - Generar nombres desde el content
                - Inferir nombres
                - Crear nombres similares
                - Usar rutas o textos como nombres
                
                INSTRUCCIÓN:
                
                - Si pide SOLO nombres → responde SOLO con "name"
                - Si pide nombres y descripción → responde con:
                  name - descripción basada en content
                
                FORMATO OBLIGATORIO:
                
                nombre.pdf - descripción breve
                
                (Sin números, sin texto extra, sin explicaciones)
                
                VALIDACIÓN FINAL:
                
                Antes de responder verifica:
                
                - Cada nombre existe EXACTAMENTE en DOCUMENTOS
                
                Si NO puedes cumplir todo lo anterior, responde EXACTAMENTE:
                
                No se encontró información en los documentos
                
                PREGUNTA:
                %s
                """.formatted(documentos, documentosConContenido, question);

        // 7. Ejecutar LLM
        return ollamaService.generateResponse(prompt);
    }
}