package com.rag.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class OllamaService {

    private final RestTemplate restTemplate;

    @Value("${ollama.url}")
    private String ollamaUrl;

    @Value("${ollama.model}")
    private String model;

    @Value("${ollama.embedding.model}")
    private String embeddingModel;

    public OllamaService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public List<Double> generateEmbedding(String text) {

        String url = ollamaUrl + "/api/embeddings";

        Map<String, Object> body = new HashMap<>();
        body.put("model", embeddingModel);
        body.put("prompt", text);

        ResponseEntity<Map> response =
                restTemplate.postForEntity(url, body, Map.class);

        return (List<Double>) response.getBody().get("embedding");
    }

    public String generateResponse(String prompt) {



        String url = ollamaUrl + "/api/generate";

        Map<String, Object> options = new HashMap<>();
        options.put("temperature", 0.0);
        options.put("num_predict", 200); // limite de tokens
        options.put("num_ctx", 2048);
        options.put("repeat_penalty", 1.2);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("prompt", prompt);
        body.put("stream", false);
        body.put("options", options);

        ResponseEntity<Map> response =
                restTemplate.postForEntity(url, body, Map.class);



        return response.getBody().get("response").toString();
    }
}