package com.geofiber.geofiber_backend.services.auth;

import com.geofiber.geofiber_backend.enums.user.UserRoleEnum;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Na subida, se não há nenhum usuário, cria o administrador inicial a partir das variáveis
 * {@code ADMIN_EMAIL}, {@code ADMIN_PASSWORD} e {@code ADMIN_NAME}. Sem elas, a aplicação não sobe.
 * Depois atribui ao administrador as linhas antigas sem criador. A atribuição é idempotente.
 */
@Component
public class BootstrapAdminRunner implements ApplicationRunner {
    private final UserService users;
    private final String email;
    private final String password;
    private final String name;

    public BootstrapAdminRunner(UserService users,
                                @Value("${geofiber.bootstrap.admin.email:}") String email,
                                @Value("${geofiber.bootstrap.admin.password:}") String password,
                                @Value("${geofiber.bootstrap.admin.name:Administrador}") String name) {
        this.users = users;
        this.email = email;
        this.password = password;
        this.name = name;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() == 0) {
            if (isBlank(email) || isBlank(password)) {
                throw new IllegalStateException(
                    "Nenhum usuário cadastrado. Defina ADMIN_EMAIL e ADMIN_PASSWORD para criar o administrador inicial.");
            }
            users.create(name, email, password, UserRoleEnum.ADMIN);
        }
        users.firstAdmin().ifPresent(admin -> users.adoptLegacyRows(admin.getId()));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
