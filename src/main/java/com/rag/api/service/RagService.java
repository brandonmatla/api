package com.rag.api.service;

import com.rag.api.repository.RagDocumentRepository;
import com.rag.api.repository.RagEmbeddingRepository;
import com.rag.api.util.VectorUtil;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
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
        String documentos = archivos.findAll()
                .stream()
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

                REGLA CRÍTICA:

                Cada objeto contiene:
                - name → nombre real del archivo
                - content → contenido del documento

                PROHIBIDO ABSOLUTO:

                - Inventar nombres de archivos
                - Crear nombres basados en números (ej: 4.14)
                - Generar rutas (ej: documentos/xxx)
                - Usar nombres que no estén en DOCUMENTOS

                Si haces eso, la respuesta es incorrecta.

                INSTRUCCIÓN:

                - Si pide nombres → usa SOLO "name"
                - Si pide descripción → usa "content" del MISMO documento

                FORMATO:

                archivo.pdf - descripción breve

                REGLAS:

                - NO inventar
                - NO usar conocimiento externo
                - NO explicar de más
                - SOLO usar los datos proporcionados

                VALIDACIÓN:
                
                Antes de responder, verifica:
                
                - ¿El nombre existe EXACTAMENTE en DOCUMENTOS?
                - Si no existe → NO lo uses
                
                Si no hay información suficiente → responde:
                
                "No se encontró información en los documentos"
                
                FORMATO ESTRICTO (OBLIGATORIO):
                
                Cada línea debe cumplir EXACTAMENTE:
                
                nombre.pdf - descripción
                
                PROHIBIDO:
                - listas numeradas
                - texto antes o después
                - explicaciones
                
                REGLA DE DECISIÓN:
                
                Antes de responder, debes verificar:
                
                1. ¿Existe contenido REAL en DOCUMENTOS_CON_CONTENIDO?
                2. ¿Ese contenido describe claramente el archivo?
                
                SI LA RESPUESTA ES NO:
                
                Responde EXACTAMENTE:
                
                No se encontró información en los documentos
                
                NO intentes completar.
                NO adivines.
                NO generes contenido.

                PREGUNTA:
                %s
                """.formatted(documentos, documentosConContenido, question);

        // 7. Ejecutar LLM
        return ollamaService.generateResponse(prompt);
    }
}