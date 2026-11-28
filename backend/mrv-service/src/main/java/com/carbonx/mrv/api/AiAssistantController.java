package com.carbonx.mrv.api;

import com.carbonx.mrv.api.dto.MrvDtos.ChatRequest;
import com.carbonx.mrv.api.dto.MrvDtos.ChatResponse;
import com.carbonx.mrv.application.port.in.MrvQueryUseCase;
import com.carbonx.mrv.application.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Carbon assistant used by the SPA AiChatWidget; guarded by the {@code aiChat} rate limiter. */
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "MRV — AI Assistant")
public class AiAssistantController {

    private final MrvQueryUseCase mrvQueryUseCase;

    @PostMapping("/chat")
    @PreAuthorize("hasAnyRole('EV_OWNER','COMPANY','CVA','ADMIN')")
    @Operation(summary = "Ask the CarbonX assistant about MRV, VM0038 or the current workflow")
    public ChatResponse chat(@AuthenticationPrincipal AuthenticatedUser principal,
                             @Valid @RequestBody ChatRequest request) {
        AuthenticatedUser user = PrincipalGuard.require(principal);
        String answer = mrvQueryUseCase.askCarbonAssistant(user.userId(), user.role(), request.message());
        return new ChatResponse(answer);
    }
}
