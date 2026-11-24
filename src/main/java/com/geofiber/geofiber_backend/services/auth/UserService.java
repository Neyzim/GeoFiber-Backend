package com.geofiber.geofiber_backend.services.auth;

import com.geofiber.geofiber_backend.entities.user.AppUserEntity;
import com.geofiber.geofiber_backend.enums.user.UserRoleEnum;
import com.geofiber.geofiber_backend.repositories.user.AppUserRepository;
import com.geofiber.geofiber_backend.repositories.user.RefreshTokenRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.geofiber.geofiber_backend.services.common.ServiceErrors.badRequest;
import static com.geofiber.geofiber_backend.services.common.ServiceErrors.conflict;

@Service
@Transactional
public class UserService {
    static final int MIN_PASSWORD_LENGTH = 8;

    /** Tabelas cujo criador pode estar vazio em linhas anteriores à V8. Nomes fixos, nunca vindos do usuário. */
    private static final List<String> OWNED_TABLES = List.of("network_box", "network_cable", "network_olt", "customer");

    private final AppUserRepository users;
    private final RefreshTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;

    public UserService(AppUserRepository users, RefreshTokenRepository tokens,
                       PasswordEncoder passwordEncoder, JdbcTemplate jdbc) {
        this.users = users;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
    }

    /** Todos os usuários, por nome. Inclui inativos. */
    @Transactional(readOnly = true)
    public List<AppUserEntity> list() {
        return users.findAllByOrderByNameAsc();
    }

    /** Ativa ou desativa um usuário. Desativar revoga todas as sessões dele. */
    public AppUserEntity setActive(UUID actorId, UUID targetId, boolean active) {
        AppUserEntity user = findById(targetId);
        if (!active && actorId.equals(targetId)) {
            throw conflict("Você não pode desativar a própria conta.");
        }
        user.setActive(active);
        if (!active) {
            revokeSessions(user.getId());
        }
        return user;
    }

    /** Troca a senha e encerra as sessões abertas com a senha antiga. */
    public void resetPassword(UUID targetId, String rawPassword) {
        AppUserEntity user = findById(targetId);
        validatePassword(rawPassword);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        revokeSessions(user.getId());
    }

    @Transactional(readOnly = true)
    public AppUserEntity findById(UUID id) {
        return users.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));
    }

    private void revokeSessions(UUID userId) {
        tokens.revokeAllByUserId(userId, Instant.now());
    }

    private static void validatePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw badRequest("A senha precisa ter pelo menos " + MIN_PASSWORD_LENGTH + " caracteres.");
        }
    }

    @Transactional(readOnly = true)
    public long count() {
        return users.count();
    }

    public AppUserEntity create(String name, String email, String rawPassword, UserRoleEnum role) {
        validatePassword(rawPassword);
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            throw conflict("E-mail já cadastrado.");
        }
        AppUserEntity user = new AppUserEntity();
        user.setName(name);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        return users.save(user);
    }

    @Transactional(readOnly = true)
    public Optional<AppUserEntity> firstAdmin() {
        return users.findFirstByRoleOrderByCreatedAtAsc(UserRoleEnum.ADMIN);
    }

    /** Atribui ao usuário as linhas sem criador (criadas antes do controle de acesso). */
    public void adoptLegacyRows(UUID ownerId) {
        for (String table : OWNED_TABLES) {
            jdbc.update("UPDATE " + table + " SET created_by = ? WHERE created_by IS NULL", ownerId);
        }
    }
}
