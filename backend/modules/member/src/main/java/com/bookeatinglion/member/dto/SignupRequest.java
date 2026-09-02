package com.bookeatinglion.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password,
        @NotBlank String name,
        // members.nickname은 VARCHAR(50) NOT NULL UNIQUE (db/postgres/01-member_db.sql).
        @NotBlank @Size(max = 50) String nickname) {}
