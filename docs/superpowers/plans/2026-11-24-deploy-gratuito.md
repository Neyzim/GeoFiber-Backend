# Publicação gratuita (Supabase + Render + Netlify) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deixar backend e front prontos para subir de graça: banco no Supabase, backend (Docker) no Render, front na Netlify com proxy de `/api/*`.

**Architecture:** O backend ganha duas propriedades vindas do ambiente (`PORT`, `DB_POOL_SIZE`), um `Dockerfile` e um blueprint `render.yaml`. O front ganha um `public/_redirects` que faz a Netlify encaminhar `/api/*` ao Render (mesmo domínio para o navegador, cookie `SameSite=Strict` continua valendo). Um documento novo descreve a montagem nos três serviços.

**Tech Stack:** Java 17, Spring Boot 4.1.2, Gradle, Docker (eclipse-temurin 17), Render Blueprint, Netlify `_redirects`, Angular (`@angular/build:application`).

**Spec:** `docs/superpowers/specs/2026-11-24-deploy-gratuito-design.md`

## Global Constraints

- **Não commitar.** Regra do projeto (CLAUDE.md): o usuário decide quando e o quê commitar. Onde o modelo de plano diria "Commit", aqui é "deixar as mudanças no working tree".
- O ambiente local não muda: sem `PORT` → porta 8080; sem `DB_POOL_SIZE` → pool 10.
- Nenhuma senha/segredo em arquivo versionado (`render.yaml` declara as variáveis secretas com `sync: false`).
- `API_DOCS_ENABLED` não é definido no Render (Swagger fica 404).
- Memória do Render gratuito: 512 MB → `JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC"`.
- Conexão Supabase: pooler em **modo sessão**, porta **5432**, usuário `postgres.<ref>`, `?sslmode=require`. Nunca a porta 6543 (modo transação).
- Documentação e comentários em português; código em inglês.
- Ajuste sobre o design (decisão do planejamento): Supabase em **East US (North Virginia)** e Render em **Virginia**, lado a lado — cada requisição faz várias consultas, então a distância backend↔banco pesa mais que navegador↔backend. Registrar no documento.

## Review Focus

1. Container sobe com Flyway + Hibernate dentro de 512 MB sem ser morto (OOM) → verificado no Task 2 com `docker run --memory=512m`.
2. `gradlew` está como `100644` no Git: o clone do Render não o executa diretamente → o `Dockerfile` chama `sh ./gradlew` (Task 2).
3. Recarregar uma rota do app (`/caixas/...`) na Netlify tem de servir `index.html`, e a regra de `/api/*` tem de vir **antes** do `/*` → verificado no Task 3 olhando o arquivo no `dist`.
4. Usar a string do pooler em modo transação (6543) ou a conexão direta (IPv6) por engano faz a subida falhar → aviso explícito no documento (Task 4).
5. Projeto Supabase pausado após 7 dias → backend não sobe (erro de conexão nos logs do Render); o documento diz como reativar (Task 4).

---

### Task 1: Porta e pool de conexões vindos do ambiente

**Files:**
- Modify: `src/main/resources/application.properties` (após o bloco `spring.datasource.*`)
- Test: `src/test/java/com/geofiber/geofiber_backend/config/HostingPropertiesIT.java`

**Interfaces:**
- Produces: variáveis de ambiente `PORT` (→ `server.port`, padrão `8080`) e `DB_POOL_SIZE` (→ `spring.datasource.hikari.maximum-pool-size`, padrão `10`), usadas pelos Tasks 2 e 4.

- [ ] **Step 1: Escrever o teste que falha**

Dois testes em classes aninhadas (ou duas classes) que estendem `support/IntegrationTest`:

```java
// sem variáveis: valores padrão do desenvolvimento local
@Autowired Environment env; @Autowired DataSource dataSource;
assertThat(env.getProperty("server.port")).isEqualTo("8080");
assertThat(((HikariDataSource) dataSource).getMaximumPoolSize()).isEqualTo(10);

// @TestPropertySource(properties = {"PORT=9123", "DB_POOL_SIZE=3"})
assertThat(env.getProperty("server.port")).isEqualTo("9123");
assertThat(((HikariDataSource) dataSource).getMaximumPoolSize()).isEqualTo(3);
```

Nomes: `usesLocalDefaultsWithoutHostingVariables`, `readsPortAndPoolSizeFromTheEnvironment`.

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests "com.geofiber.geofiber_backend.config.HostingPropertiesIT"`
Expected: FAIL (`server.port` é `null`; pool não é 3).

- [ ] **Step 3: Implementar**

Em `application.properties`, com um comentário curto em português (Render injeta `PORT`; pooler do Supabase limita conexões):

```properties
server.port=${PORT:8080}
spring.datasource.hikari.maximum-pool-size=${DB_POOL_SIZE:10}
```

- [ ] **Step 4: Rodar e ver passar**

Run: o mesmo comando do Step 2. Expected: PASS. Depois `./gradlew test` inteiro: PASS.

- [ ] **Step 5: Deixar no working tree (não commitar)**

---

### Task 2: Imagem Docker e blueprint do Render

**Files:**
- Create: `Dockerfile`
- Create: `.dockerignore`
- Create: `render.yaml`

**Interfaces:**
- Consumes: `PORT`, `DB_POOL_SIZE` (Task 1).
- Produces: serviço Render `geofiber-backend` (URL esperada `https://geofiber-backend.onrender.com`, usada no Task 3), imagem que escuta em `$PORT`.

- [ ] **Step 1: `Dockerfile` multi-stage**

- Estágio `build`: `eclipse-temurin:17-jdk`; copia `gradlew`, `gradle/`, `build.gradle`, `settings.gradle` primeiro e roda `sh ./gradlew dependencies --no-daemon` (cache de camada); depois copia `src/` e roda `sh ./gradlew bootJar -x test --no-daemon`.
- Estágio final: `eclipse-temurin:17-jre`; usuário sem privilégios (`useradd --system geofiber`); copia `build/libs/geofiber-backend-0.0.1-SNAPSHOT.jar` como `/app/app.jar`; `ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC"`; `EXPOSE 8080`; `ENTRYPOINT ["java","-jar","/app/app.jar"]`.

- [ ] **Step 2: `.dockerignore`**

Exclui: `.git`, `.gradle`, `build`, `data`, `.env`, `*.env`, `.idea`, `.vscode`, `docs`, `deploy`.

- [ ] **Step 3: `render.yaml`**

Um serviço `type: web`, `name: geofiber-backend`, `runtime: docker`, `plan: free`, `region: virginia`, `branch: master`, `autoDeploy: true`. `envVars`:
- com valor: `DB_POOL_SIZE=5`, `SERVER_FORWARD_HEADERS_STRATEGY=native`, `ADMIN_NAME=Administrador`;
- `sync: false` (preenchidas no painel): `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `GEOFIBER_JWT_SECRET`, `CORS_ALLOWED_ORIGINS`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`.
Sem `healthCheckPath` (não há endpoint GET público; o Render usa a checagem de porta). Comentário no topo apontando para `docs/PUBLICAR-GRATUITO.md`.

- [ ] **Step 4: Construir a imagem**

Run: `docker build -t geofiber-backend:local .`
Expected: build termina sem erro.

- [ ] **Step 5: Rodar com o limite do Render contra o banco local**

Com `docker compose up -d` (PostGIS local) rodando:

```bash
docker run --rm --memory=512m -p 9090:10000 -e PORT=10000 -e DB_POOL_SIZE=5 \
  -e DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/geofiber \
  -e DATABASE_USERNAME=<do .env> -e DATABASE_PASSWORD=<do .env> \
  -e GEOFIBER_JWT_SECRET=$(openssl rand -base64 48) geofiber-backend:local
```

Expected: log `Tomcat started on port 10000` e `Started GeofiberBackendApplication`, sem `OutOfMemoryError` nem container morto (`docker ps -a` não mostra `Exited (137)`). Em outro terminal: `curl -i -X POST localhost:9090/api/v1/auth/login -H 'Content-Type: application/json' -d '{}'` → resposta `400` (a API responde). `curl -i localhost:9090/v3/api-docs` → `404`.

- [ ] **Step 6: Deixar no working tree (não commitar)**

---

### Task 3: Proxy da Netlify no front (repositório `../GeoFiber`)

**Files:**
- Create: `../GeoFiber/public/_redirects`
- Modify: `../GeoFiber/README.md` (seção "Publicar": um parágrafo sobre a Netlify apontando para `geofiber-backend/docs/PUBLICAR-GRATUITO.md`)

**Interfaces:**
- Consumes: URL do Render `https://geofiber-backend.onrender.com` (Task 2).

- [ ] **Step 1: Criar `public/_redirects`** — exatamente, nesta ordem:

```
/api/*  https://geofiber-backend.onrender.com/api/:splat  200
/*      /index.html                                       200
```

- [ ] **Step 2: Verificar que o build publica o arquivo**

Run (em `../GeoFiber`): `npm run build`
Expected: build ok e `dist/geofiber/browser/_redirects` existe com as duas linhas, `/api/*` primeiro.

- [ ] **Step 3: Atualizar o README do front** (parágrafo da Netlify: build `npm run build`, publicar `dist/geofiber/browser`, trocar a URL do `_redirects` se o nome do serviço no Render for outro).

- [ ] **Step 4: Deixar no working tree (não commitar)**

---

### Task 4: Documento de publicação gratuita

**Files:**
- Create: `docs/PUBLICAR-GRATUITO.md`
- Modify: `docs/PUBLICAR.md` (no topo: uma linha "Para testar de graça (Supabase + Render + Netlify), veja `PUBLICAR-GRATUITO.md`.")
- Modify: `CLAUDE.md` (linha "Publicação": acrescentar `docs/PUBLICAR-GRATUITO.md`)

**Interfaces:**
- Consumes: tudo dos Tasks 1–3 (nomes de variáveis, `render.yaml`, `_redirects`).

- [ ] **Step 1: Escrever `docs/PUBLICAR-GRATUITO.md`**, em português, com estas seções:
  1. **Como fica no ar** — diagrama em texto: navegador → Netlify (front + proxy `/api/*`) → Render (backend) → Supabase (Postgres/PostGIS). Para quem é (testes, uso leve) e quando migrar para o `PUBLICAR.md`.
  2. **Supabase** — criar projeto na região East US (North Virginia) e o porquê; senha forte; em *Connect* copiar a string do **Session pooler** (porta 5432) e convertê-la para `jdbc:postgresql://aws-0-<região>.pooler.supabase.com:5432/postgres?sslmode=require`, usuário `postgres.<ref>`. Aviso: **não** usar *Direct connection* (IPv6) nem *Transaction pooler* (6543). PostGIS e tabelas são criados pelo Flyway na primeira subida.
  3. **Render** — subir o código para o GitHub; *New → Blueprint* apontando para o repositório (lê `render.yaml`); preencher as variáveis `sync: false` (tabela com valor de cada uma; `GEOFIBER_JWT_SECRET` via `openssl rand -base64 48`; `CORS_ALLOWED_ORIGINS` = endereço da Netlify). Primeira subida: acompanhar os logs (Flyway V1..V8, admin criado); depois remover `ADMIN_PASSWORD`.
  4. **Netlify** — importar o repositório do front; build `npm run build`; publish `dist/geofiber/browser`; conferir a URL no `_redirects`.
  5. **Conferência** — checklist: entrar, recarregar e continuar conectado; abrir `/caixas/...` direto e recarregar; `https://<render>/v3/api-docs` → 404.
  6. **Limitações do plano gratuito** — cold start de ~30–60 s após 15 min parado; Supabase pausa após 7 dias sem uso (como reativar no painel; o Render só volta depois); banco até 500 MB; sem backup baixável → `pg_dump -Fc` manual pela string do pooler; limite de login pode ver o IP da Netlify (dois proxies); a URL `*.onrender.com` responde direto (Swagger desligado, login exige senha).

- [ ] **Step 2: Atualizar `docs/PUBLICAR.md` e `CLAUDE.md`** com as linhas descritas em *Files*.

- [ ] **Step 3: Verificar** — `./gradlew build` passa; todos os nomes de variável no documento batem com `render.yaml` e `application.properties` (`grep -o '[A-Z_]\{6,\}' docs/PUBLICAR-GRATUITO.md | sort -u` comparado à lista do `render.yaml`).

- [ ] **Step 4: Deixar no working tree (não commitar)**
