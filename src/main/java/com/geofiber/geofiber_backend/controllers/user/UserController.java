package com.geofiber.geofiber_backend.controllers.user;

import com.geofiber.geofiber_backend.dto.user.CreateUserRequest;
import com.geofiber.geofiber_backend.dto.user.ResetPasswordRequest;
import com.geofiber.geofiber_backend.dto.user.SetActiveRequest;
import com.geofiber.geofiber_backend.dto.user.UserResponse;
import com.geofiber.geofiber_backend.security.CurrentUserProvider;
import com.geofiber.geofiber_backend.services.auth.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Gestão de usuários da instância. Só administradores. */
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Usuários", description = "Cadastro, ativação e senha dos usuários da equipe. Só administradores.")
public class UserController {
    private final UserService userService;
    private final CurrentUserProvider currentUser;

    public UserController(UserService userService, CurrentUserProvider currentUser) {
        this.userService = userService;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(summary = "Lista os usuários", description = "Todos os usuários, ativos e inativos, por nome. Sem senha.")
    public List<UserResponse> list() {
        return userService.list().stream().map(UserResponse::from).toList();
    }

    @PostMapping
    @Operation(summary = "Cadastra um usuário", description = "Cria um MEMBER ou outro ADMIN. E-mail precisa ser único.")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse body = UserResponse.from(
            userService.create(request.name(), request.email(), request.password(), request.role()));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PatchMapping("/{id}/active")
    @Operation(summary = "Ativa ou desativa um usuário",
        description = "Desativar revoga as sessões do usuário. Não é possível desativar a própria conta.")
    public UserResponse setActive(@PathVariable UUID id, @Valid @RequestBody SetActiveRequest request) {
        return UserResponse.from(userService.setActive(currentUser.current().id(), id, request.active()));
    }

    @PutMapping("/{id}/password")
    @Operation(summary = "Redefine a senha", description = "Troca a senha e encerra as sessões abertas do usuário.")
    public ResponseEntity<Void> resetPassword(@PathVariable UUID id, @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request.password());
        return ResponseEntity.noContent().build();
    }
}
