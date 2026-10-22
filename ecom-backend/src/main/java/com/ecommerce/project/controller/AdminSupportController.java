package com.ecommerce.project.controller;

import com.ecommerce.project.payload.support.SupportDtos.ChatResponse;
import com.ecommerce.project.payload.support.SupportDtos.Conversation;
import com.ecommerce.project.payload.support.SupportDtos.Decision;
import com.ecommerce.project.payload.support.SupportDtos.PendingRefund;
import com.ecommerce.project.service.support.AdminSupportService;
import com.ecommerce.project.service.support.SupportPilotApi.ChatOut;
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

import java.util.List;

/** The AI refund queue: refunds the agent paused on, waiting for a person. */
@Tag(name = "Support (admin)")
@RestController
@RequestMapping("/api/admin/support")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSupportController extends BaseController {

    private final AdminSupportService adminSupportService;
    private final AuthUtil authUtil;

    @GetMapping("/approvals")
    public ResponseEntity<List<PendingRefund>> pending() {
        return ok(adminSupportService.pendingRefunds());
    }

    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<Conversation> transcript(@PathVariable String conversationId) {
        return ok(adminSupportService.transcript(conversationId));
    }

    @PostMapping("/approvals/{conversationId}")
    public ResponseEntity<ChatResponse> decide(@PathVariable String conversationId,
                                               @Valid @RequestBody Decision decision) {
        // The admin's username goes to SupportPilot's audit trail and on to the return's admin note.
        ChatOut out = adminSupportService.decide(conversationId, decision.decision(), decision.note(),
                authUtil.loggedInUser().getUserName());
        return ok(new ChatResponse(out.conversationId(), out.status(), out.reply()));
    }
}
