package com.exelynt.booking.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(@NotBlank(message = "refreshToken is required") String refreshToken) {

    @Override
    public String toString() {
        return "RefreshRequest{refreshToken='[PROTECTED]'}";
    }
}
