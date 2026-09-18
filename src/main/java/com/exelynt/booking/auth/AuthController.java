package com.exelynt.booking.auth;

import com.exelynt.booking.auth.dto.LoginRequest;
import com.exelynt.booking.auth.dto.RefreshRequest;
import com.exelynt.booking.auth.dto.TokenResponse;
import com.exelynt.booking.security.AppUserPrincipal;
import com.exelynt.booking.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Login, refresh-token rotation and logout")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange username and password for an access/refresh token pair")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token pair issued"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate a refresh token into a new token pair; the presented token is revoked")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "New token pair issued"),
            @ApiResponse(responseCode = "401", description = "Unknown, expired or already used refresh token")
    })
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke every refresh token of the caller and blacklist the current access token")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Logged out"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token")
    })
    public ResponseEntity<Void> logout() {
        AppUserPrincipal principal = SecurityUtils.requireCurrentPrincipal();
        authService.logout(principal.getUserId(), principal.getUsername(),
                SecurityUtils.currentTokenDetails().orElse(null));
        return ResponseEntity.noContent().build();
    }
}
