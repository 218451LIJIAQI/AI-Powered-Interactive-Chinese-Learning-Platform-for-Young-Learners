package com.example.aichinese.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.ai.XiaomeiResponses;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.XiaomeiMemoryService;

import lombok.RequiredArgsConstructor;

/**
 * What Talk with Xiaomei remembers about the signed-in learner ({@code /api/v1/ai/conversation/memory}, STUDENT only like
 * the rest of the AI Helper): the notes of "Xiaomei will remember" in the chat summary, "Forget" and its Undo. Every
 * answer is the learner's own notes, the plan's goal first; nobody else's notes can be read or changed.
 */
@RestController
@RequestMapping("/api/v1/ai/conversation/memory")
@RequiredArgsConstructor
public class XiaomeiMemoryController {

    private final XiaomeiMemoryService memoryService;

    @GetMapping
    public ApiResponse<List<XiaomeiResponses.MemoryNote>> notes(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success("Xiaomei's notes retrieved successfully.", memoryService.notes(currentUser));
    }

    /** "Forget": the note leaves Xiaomei's memory at once; answers with the notes she still remembers. */
    @DeleteMapping("/{noteId}")
    public ApiResponse<List<XiaomeiResponses.MemoryNote>> forget(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long noteId) {
        return ApiResponse.success("Xiaomei will forget this.", memoryService.forget(currentUser, noteId));
    }

    /** The Undo of "Forget": the note comes back while the Undo window lasts. */
    @PostMapping("/{noteId}/restore")
    public ApiResponse<List<XiaomeiResponses.MemoryNote>> restore(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long noteId) {
        return ApiResponse.success("Xiaomei remembers this again.", memoryService.restore(currentUser, noteId));
    }
}
