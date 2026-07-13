package com.gitinsight.gitinsight_backend.controllers;

import com.gitinsight.gitinsight_backend.dto.ChatRequest;
import com.gitinsight.gitinsight_backend.services.ChatService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/chat")
    public String chat(@RequestBody ChatRequest request) {
        return chatService.askCodebase(request.getQuestion(), request.getUrl());
    }
}
//package com.gitinsight.gitinsight_backend.controllers;
//
//import com.gitinsight.gitinsight_backend.services.ChatService;
//import org.springframework.web.bind.annotation.*;
//
//@RestController
//@RequestMapping("/api/chat")
//public class ChatController {
//
//    private final ChatService chatService;
//
//    public ChatController(ChatService chatService) {
//        this.chatService = chatService;
//    }
//
//    @PostMapping
//    public String chatWithCodebase(@RequestBody String question) {
//        return chatService.askCodebase(question);
//    }
//}