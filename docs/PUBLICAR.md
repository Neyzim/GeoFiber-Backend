# Publicar o GeoFiber — instruções para o backend

Este documento diz o que configurar no servidor para colocar o backend no ar junto do frontend, sem
expor dados nem detalhes internos. As regras da API estão em `API.md`; os requisitos técnicos em
`docs/SPECS.md` (RT-06 e RT-14). O lado do front está no `README.md` do frontend, seção "Publicar".

> Para testar de graça (Supabase + Render + Netlify), veja `docs/PUBLICAR-GRATUITO.md`.

## 1. Como fica no ar

```
navegador ──https──▶ proxy (nginx, Caddy…) ──┬─▶ arquivos do front (dist/geofiber/browser)
                                              └─▶ /api/  →  backend em 127.0.0.1:8080  →  PostgreSQL (só local)
```

- Front e API no **mesmo endereço** (`https://geofiber.seudominio.com.br` e `.../api/v1`). O build de
  produção do front já chama `/api/v1`. Assim o cookie da sessão (`Secure`, `SameSite=Strict`,
  `Path=/api/v1/auth`) funciona e não há CORS entre domínios.
- Só o proxy fica aberto para a internet (portas 80 e 443). Backend e banco escutam só na própria
  máquina.

## 2. Variáveis de ambiente de produção

Use variáveis do sistema (ou um `.env` fora do repositório, legível só pelo usuário do serviço).
Modelo pronto: `deploy/backend.env.example` → copie para `/etc/geofiber/backend.env` (`chmod 600`).

| Variável | Valor em produção |
|---|---|
| `DATABASE_URL` | `jdbc:postgresql://127.0.0.1:5432/geofiber` |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | usuário próprio da aplicação, senha forte (não use `postgres`) |
| `GEOFIBER_JWT_SECRET` | chave aleatória de 48 bytes ou mais: `openssl rand -base64 48`. Nunca a mesma do desenvolvimento. Trocar a chave derruba todas as sessões. |
| `CORS_ALLOWED_ORIGINS` | o endereço do site, ex.: `https://geofiber.seudominio.com.br` |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` / `ADMIN_NAME` | só na **primeira** subida (cria o administrador quando não há usuários). Depois, remova a senha do ambiente. |
| `API_DOCS_ENABLED` | **não defina** (ou `false`). Swagger e `/v3/api-docs` respondem `404`. |
| `SERVER_ADDRESS` | `127.0.0.1` (o backend não aceita conexão de fora, só do proxy) |
| `SERVER_FORWARD_HEADERS_STRATEGY` | `native`: o backend passa a ler o IP real do cliente no `X-Forwarded-For` do proxy. O limite de login (5 erros em 15 min por e-mail + origem) depende disso; sem ele, todos parecem vir do proxy e a contagem fica só por e-mail. |

> Na máquina de desenvolvimento, `API_DOCS_ENABLED=true` no `.env` (o `npm run api:types` do front lê
> `/v3/api-docs`). Já está no `.env.example`.

## 3. Banco de dados

- **Não publique a porta do Postgres.** O `docker-compose.yml` usa `ports: ${postgres_ports}`. Use
  `postgres_ports=127.0.0.1:5432:5432` (só a máquina; já é o valor do `.env.example`). Com
  `5432:5432`, o banco fica aberto para a internet — o Docker ignora o firewall da máquina (ufw).
- A imagem está fixada em `postgis/postgis:18-3.7` no `docker-compose.yml`. Não volte para
  `postgis/postgis` sem versão: uma atualização automática pode mudar o PostgreSQL de versão e o banco
  não sobe (os dados de uma versão maior não abrem em outra). Para trocar de versão maior, faça
  `pg_dump` → banco novo → `pg_restore`.
  - Os testes (Testcontainers, `support/PostgisContainerConfig`) usam a mesma imagem. Ao trocar a
    versão aqui, troque lá também.
- Crie um usuário próprio para a aplicação (o `postgres_username` do compose é o superusuário):

  ```sql
  CREATE USER geofiber_app WITH PASSWORD '<senha forte>';
  GRANT ALL ON SCHEMA public TO geofiber_app;      -- o Flyway cria as tabelas com ele
  ALTER DATABASE geofiber OWNER TO geofiber_app;
  ```

  A extensão PostGIS é criada pela migração V1; se o usuário da aplicação não puder criá-la, rode
  `CREATE EXTENSION IF NOT EXISTS postgis;` uma vez como superusuário antes da primeira subida.
- O Flyway aplica as migrações ao subir (`ddl-auto=validate`). Faça backup antes de publicar uma
  versão com migração nova.
- Backup diário: `pg_dump -Fc geofiber > geofiber-AAAA-MM-DD.dump`, guardado fora do servidor.

## 4. Gerar e rodar o backend

```bash
./gradlew bootJar                       # gera build/libs/geofiber-backend-0.0.1-SNAPSHOT.jar (Java 17)
java -jar build/libs/geofiber-backend-0.0.1-SNAPSHOT.jar
```

Rode como serviço com um usuário sem privilégios, reinício automático e as variáveis da seção 2.
Com systemd, a unidade pronta está em `deploy/geofiber-backend.service`:

```bash
sudo useradd --system --home /opt/geofiber --shell /usr/sbin/nologin geofiber
sudo mkdir -p /opt/geofiber /etc/geofiber
sudo cp build/libs/geofiber-backend-0.0.1-SNAPSHOT.jar /opt/geofiber/geofiber-backend.jar
sudo install -o geofiber -g geofiber -m 600 deploy/backend.env.example /etc/geofiber/backend.env
sudo nano /etc/geofiber/backend.env                     # preencher
sudo cp deploy/geofiber-backend.service /etc/systemd/system/
sudo systemctl daemon-reload && sudo systemctl enable --now geofiber-backend
journalctl -u geofiber-backend -f                       # acompanhar a subida (Flyway, admin)
```

Para publicar uma versão nova: backup do banco, copiar o jar novo por cima e
`sudo systemctl restart geofiber-backend`. O limite de login fica em memória: zera ao reiniciar e vale para uma instância
só. Com mais de uma instância, cada uma conta separado.

## 5. Proxy reverso (exemplo com nginx)

```nginx
server {
    listen 80;
    server_name geofiber.seudominio.com.br;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name geofiber.seudominio.com.br;

    ssl_certificate     /etc/letsencrypt/live/geofiber.seudominio.com.br/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/geofiber.seudominio.com.br/privkey.pem;

    add_header Strict-Transport-Security "max-age=31536000" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-Frame-Options "DENY" always;
    add_header Referrer-Policy "same-origin" always;
    server_tokens off;

    root /var/www/geofiber/browser;      # conteúdo de dist/geofiber/browser do front

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        client_max_body_size 20m;        # importação de KMZ grande vira um JSON grande
    }

    # Só a API: nada de Swagger nem actuator pelo proxy, mesmo que alguém ligue por engano.
    location ~ ^/(swagger-ui|v3/api-docs|actuator) { return 404; }

    location / {
        try_files $uri $uri/ /index.html;   # rotas do app (/entrar, /importar, /caixas/...)
    }
}
```

O certificado pode vir do Let's Encrypt (`certbot --nginx`). Sem https, o navegador recusa o cookie
`Secure` e ninguém consegue continuar conectado.

## 6. O que já foi feito no código (revisão de 2026-11-22)

- Swagger e OpenAPI desligados por padrão (`springdoc.*` em `application.properties`, chave
  `API_DOCS_ENABLED`). Teste: `docs/OpenApiDisabledIT`.
- Limite de login: `security/LoginAttemptLimiter` + `controllers/auth/TooManyLoginAttemptsException`;
  5 senhas erradas em 15 min → `429` com `Retry-After` por 15 min. Testes: `LoginAttemptLimiterTest`
  e `AuthIT.fiveWrongPasswordsBlockTheLoginForFifteenMinutes`.
- Já estava certo: erros 500 sem detalhe interno, cookie `HttpOnly`/`Secure`/`SameSite=Strict`,
  refresh guardado só como SHA-256, mensagem única para e-mail inexistente e senha errada, `.env`
  fora do Git, usuários só para `ADMIN`.

## 7. Conferência antes de abrir para os técnicos

- [ ] `docker ps` mostra o banco como `127.0.0.1:5432->5432/tcp` (não `0.0.0.0`).
- [ ] `https://…/swagger-ui.html` e `https://…/v3/api-docs` respondem `404`.
- [ ] `http://…` redireciona para `https://…`.
- [ ] De outra máquina, a porta `5432` (banco) e a `8080` (backend) **não** respondem.
- [ ] Entrar, recarregar a página e continuar conectado (o cookie funcionou).
- [ ] 6 senhas erradas seguidas mostram "Muitas tentativas de entrar…"; outro usuário continua entrando.
- [ ] `GEOFIBER_JWT_SECRET` e a senha do banco são diferentes das do desenvolvimento.
- [ ] `ADMIN_PASSWORD` saiu do ambiente depois da primeira subida.
- [ ] Backup do banco rodou e foi restaurado uma vez num banco de teste.
