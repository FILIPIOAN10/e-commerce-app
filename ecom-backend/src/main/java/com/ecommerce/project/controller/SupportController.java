package com.ecommerce.project.controller;

import com.ecommerce.project.payload.support.SupportDtos.ChatRequest;
import com.ecommerce.project.payload.support.SupportDtos.ChatResponse;
import com.ecommerce.project.payload.support.SupportDtos.Conversation;
import com.ecommerce.project.payload.support.SupportDtos.SupportStatus;
import com.ecommerce.project.service.support.SupportChatService;
import com.ecommerce.project.util.AuthUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The customer chat widget's API. The customer is always the logged-in user, never a parameter. */
@Tag(name = "Support")
@RestController
@RequestMapping("/api/support")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class SupportController extends BaseController {

    private final SupportChatService supportChatService;
    private final AuthUtil authUtil;

    @GetMapping("/status")
    public ResponseEntity<SupportStatus> status() {
        return ok(new SupportStatus(supportChatService.isEnabled()));
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        return ok(supportChatService.chat(authUtil.loggedInUser(), request));
    }

    @GetMapping("/conversations/latest")
    public ResponseEntity<Conversation> latest() {
        return supportChatService.latest(authUtil.loggedInUser())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<Conversation> conversation(@PathVariable String conversationId) {
        return ok(supportChatService.conversation(authUtil.loggedInUser(), conversationId));
    }
}
