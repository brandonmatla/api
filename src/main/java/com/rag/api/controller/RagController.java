package com.rag.api.controller;

import com.rag.api.dto.QuestionRequest;
import com.rag.api.service.RagService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/rag")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/ask")
    public String ask(@RequestBody QuestionRequest request) {
        long start = System.currentTimeMillis();

        System.out.println("Pregunta: "+request.getQuestion());

        String respuesta=ragService.ask(request.getQuestion());

        System.out.println("Respuesta: "+respuesta);

        long end = System.currentTimeMillis();
        System.out.println("LLM tiempo: " + (end - start)/1000 + "Seg");

        return respuesta;
    }
}