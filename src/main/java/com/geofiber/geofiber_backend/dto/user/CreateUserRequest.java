package com.geofiber.geofiber_backend.dto.user;

import com.geofiber.geofiber_backend.enums.user.UserRoleEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateUserRequest(
    @Schema(example = "Maria Silva") @NotBlank String name,
    @Schema(example = "maria@equipe.com") @NotBlank @Email String email,
    @Schema(example = "senha-com-dez-chars") @NotBlank String password,
    @NotNull UserRoleEnum role
) {
}
