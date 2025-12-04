package com.agnostik.bot_runner.dto;

import lombok.Data;

@Data
public class AuthenticationResponseDTO {
    private String token;
    private Long userId;
    private String username;
}

