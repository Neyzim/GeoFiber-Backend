# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Backend de um mapeador de redes FTTH (open source): Java 17, Spring Boot 4.1.2 / Hibernate 7, PostgreSQL + PostGIS, Flyway, Lombok, Gradle. Código em inglês; documentação, mensagens de commit e conversa com o usuário em português.

## Regras do usuário

- **Nunca commitar sem o usuário pedir.** Ele decide quando e o quê commitar.
- Branch de trabalho atual: `feature/basic_entities`; base de PR: `master`.

## Comandos

```bash
./gradlew build                      # compila e roda todos os testes (Windows: gradlew.bat)
./gradlew test                       # suíte completa — exige Docker rodando (Testcontainers)
./gradlew test --tests "com.geofiber.geofiber_backend.readmodel.ReadModelIT"            # uma classe
./gradlew test --tests "com.geofiber.geofiber_backend.readmodel.ReadModelIT.someMethod" # um método
./gradlew bootRun                    # sobe a API em :8080 (precisa do banco)
docker compose up -d                 # PostGIS local, configurado pelo .env
```

- Swagger UI: `http://localhost:8080/swagger-ui.html`; OpenAPI: `/v3/api-docs`.
- Configuração vem de `.env` (importado por `spring.config.import`) ou variáveis `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `CORS_ALLOWED_ORIGINS` (padrão `http://localhost:4200`).
- Não há linter configurado.

## Documentação de referência

- `docs/SPECS.md` — especificação funcional: regras de negócio RN-xx e requisitos técnicos RT-xx. Consulte antes de mudar comportamento e atualize-o junto.
- `API.md` — guia da API para quem consome (fluxo passo a passo, erros, seção "Para o frontend"). Atualize quando endpoints mudarem.

## Arquitetura

Pacote base `com.geofiber.geofiber_backend`, organizado **por camada e depois por tipo de elemento** (`entities/cable`, `services/cable`, `controllers/cable`, ...). Camadas: `controllers` → `services` → `repositories`, com `dto` (records de request/response) e `entities` (JPA). Controllers nunca expõem entidades.

### Banco e schema

- Schema é **só via Flyway** (`src/main/resources/db/migration`, V1..Vn); `ddl-auto=validate`. Toda mudança de entidade exige nova migration — nunca edite uma migration existente.
- Geometrias PostGIS em SRID 4326 (JTS). Conversão DTO↔geometria fica em `services/geo/GeometryMapper`; coordenadas nos DTOs são objetos `CoordinateDto {longitude, latitude}` (x = longitude).
- Parte das invariantes também é garantida no banco (checks, triggers), além dos services.
- `open-in-view=false`: carregue o que precisar dentro do `@Transactional` do service.

### Modelo de domínio (o que exige ler vários arquivos)

- **Cabos** se ligam a caixas/OLTs por uma lista ordenada de `CableAttachment` (START, PASS_THROUGH*, END). Cabo → tubos loose → fibras.
- **Fibras têm duas pontas, A e B** (A = lado da OLT). `fiber_termination` é a **única fonte da verdade** de qual ponta está ligada a qual porta (OLT, splitter, porta de atendimento). Toda ligação/troca passa por `services/termination/FiberTerminationService` (`relink`). Pontas padrão: porta OLT←A; entrada do splitter←B; saída do splitter←A; porta de atendimento←B; emenda: entrada B / saída A.
- **Conexões ópticas** (`OpticalConnection`) unem duas terminações: fusão (emenda numa bandeja) ou conector.
- **Porta de atendimento** (CTO) tem uma única fonte óptica: uma saída de splitter **ou** uma fibra. Cliente se liga à porta de atendimento.
- **Status de porta** é calculado por `services/common/PortStatusResolver`: FAULTY prevalece sobre OCCUPIED.
- **Cliente** tem status ACTIVE/BLOCKED/CANCELLED; cancelar libera a porta (exceto se FAULTY). Mudanças de status/porta são gravadas em `customer_status_history`; DELETE é só para cadastro feito por engano.
- `readmodel/` contém leituras agregadas para o frontend (cabos de uma caixa/OLT, fibras com ligação de cada ponta, visão geral da caixa) e `POST /cables/{id}/structure` (cria tubos + fibras em uma transação, cores ABNT).

### Erros e API

- Erros de negócio são lançados com `services/common/ServiceErrors` (`notFound(...)`, `conflict(...)`) e viram `ProblemDetail` (RFC 7807) em `controllers/errors/ApiExceptionHandler`: 400 validação, 404, 409 regra de negócio, 500 inesperado.
- Endpoints sob `/api/v1`. CORS em `config/CorsConfig`; OpenAPI em `config/OpenApiConfig` (adiciona respostas 400/404/409 padrão). Novos endpoints levam `@Tag`/`@Operation` e os DTOs de request, `@Schema`.

## Testes

- Testes de integração (`*IT`) estendem `support/IntegrationTest`: PostGIS real (`postgis/postgis:16-3.4`) via Testcontainers, MockMvc, e **TRUNCATE de todas as tabelas após cada teste**.
- `support/NetworkFixtures` monta a rede pela própria API (caixas, cabos, fibras, splitters...) — reutilize-o em vez de inserir dados direto.
- Testes unitários puros ficam em `services/...` (ex.: `PortStatusResolverTest`).
