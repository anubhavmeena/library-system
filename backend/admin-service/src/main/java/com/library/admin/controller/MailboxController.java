package com.library.admin.controller;

import com.library.admin.dto.InboxMessageDto;
import com.library.admin.dto.InboxSummaryDto;
import com.library.admin.dto.ReplyRequest;
import com.library.admin.service.MailboxService;
import com.library.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/inbox")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MailboxController {

    private final MailboxService mailboxService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<InboxSummaryDto>>> listMessages() {
        return ResponseEntity.ok(ApiResponse.success(mailboxService.listMessages()));
    }

    @GetMapping("/{messageNumber}")
    public ResponseEntity<ApiResponse<InboxMessageDto>> getMessage(
            @PathVariable int messageNumber) {
        return ResponseEntity.ok(ApiResponse.success(mailboxService.getMessage(messageNumber)));
    }

    @PostMapping("/{messageNumber}/reply")
    public ResponseEntity<ApiResponse<String>> reply(
            @PathVariable int messageNumber,
            @RequestBody ReplyRequest request) {
        mailboxService.replyToMessage(messageNumber, request.getBody());
        return ResponseEntity.ok(ApiResponse.success("Reply sent"));
    }

    @DeleteMapping("/{messageNumber}")
    public ResponseEntity<ApiResponse<String>> deleteMessage(
            @PathVariable int messageNumber) {
        mailboxService.deleteMessage(messageNumber);
        return ResponseEntity.ok(ApiResponse.success("Message deleted"));
    }
}
