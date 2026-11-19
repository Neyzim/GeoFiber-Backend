# Conclusão da Fase 1 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fechar a Fase 1: erros padronizados, cabo ligado aos elementos, portas de atendimento da CTO, conexões por conector, CRUD completo, Swagger e `API.md`.

**Architecture:** Spring Boot em camadas (controllers → services → repositories → entities), pacotes por tipo funcional e dentro por tipo concreto. Toda ligação de fibra passa por `FiberTerminationService`; toda checagem de "cabo ligado ao elemento" passa por `CableAttachmentService`. Schema só muda por novas migrações Flyway (V4+).

**Tech Stack:** Java 17, Spring Boot 4.1.2, Spring Data JPA/Hibernate (`ddl-auto=validate`), PostgreSQL + PostGIS, Flyway, Lombok, JUnit 5, MockMvc, Testcontainers, springdoc-openapi 3.x.

**Spec:** `docs/superpowers/specs/2026-11-19-fase1-conclusao-design.md`

## Global Constraints

- Pacote base `com.geofiber.geofiber_backend`; pacotes por função e depois por tipo concreto (`.../serviceport/`, `cable_attachment` em `.../cable/`).
- Não editar `V1`–`V3`. Novas migrações: `V4__cable_attachments.sql`, `V5__service_ports.sql`, `V6__connector_connections.sql`.
- Erros sempre como ProblemDetail; 404 recurso inexistente, 400 payload/regra de entrada, 409 conflito com estado existente.
- Mensagens de erro em inglês (padrão já usado); documentação (`@Schema`, `@Operation`, `API.md`) em português.
- Pontas de fibra: A = lado da OLT, B = lado do cliente. Padrões: OLT→A, entrada de splitter→B, saída de splitter→A, porta de atendimento→B, emenda entrada/saída→B/A.
- Nunca inferir ligação por proximidade geográfica.
- **Sem commits**: o usuário decide quando commitar. Onde o fluxo TDD pediria commit, apenas rode a suíte completa.
- Comando de teste: `./gradlew test` (Docker precisa estar rodando para Testcontainers).

## Review Focus

1. Splitter criado numa CTO cujo `powerCapacity` não comporta mais portas de atendimento → 400, e nada é gravado (nem splitter, nem portas).
2. Remover da lista de um cabo uma caixa onde ainda há fibras desse cabo ligadas (porta, emenda, conexão) → 409, lista inalterada.
3. Trocar a caixa de tipo `CTO` para outro tipo enquanto tem portas de atendimento → 409.
4. Excluir splitter cuja saída alimenta porta de atendimento com cliente → 409.
5. Número de porta/bandeja/tubo duplicado → 409 com mensagem que cita o recurso (não a mensagem genérica).

---

### Task 1: Infra de testes de integração + erros padronizados

**Files:**
- Modify: `build.gradle`
- Modify: `src/main/resources/application.properties`
- Rewrite: `src/main/java/com/geofiber/geofiber_backend/controllers/errors/ApiExceptionHandler.java`
- Create: `src/main/java/com/geofiber/geofiber_backend/controllers/errors/ConstraintMessages.java`
- Create: `src/test/java/com/geofiber/geofiber_backend/support/PostgisContainerConfig.java`
- Create: `src/test/java/com/geofiber/geofiber_backend/support/IntegrationTest.java`
- Create: `src/test/java/com/geofiber/geofiber_backend/support/NetworkFixtures.java`
- Modify: `src/test/java/com/geofiber/geofiber_backend/GeofiberBackendApplicationTests.java`
- Test: `src/test/java/com/geofiber/geofiber_backend/controllers/errors/ApiErrorsIT.java`

**Interfaces:**
- Produces:
  - `PostgisContainerConfig` (`@TestConfiguration(proxyBeanMethods = false)`): bean `@ServiceConnection PostgreSQLContainer` com `DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres")`.
  - `abstract class IntegrationTest`: `@SpringBootTest @AutoConfigureMockMvc @Import(PostgisContainerConfig.class)`; campos protegidos `MockMvc mvc`, `JdbcTemplate jdbc`, `NetworkFixtures fx`; `@AfterEach void cleanDatabase()` que faz `TRUNCATE ... CASCADE` em todas as tabelas de `public` exceto `flyway_schema_history` e `spatial_ref_sys`.
  - `NetworkFixtures(MockMvc mvc)` com métodos que fazem POST e devolvem o `UUID` criado: `olt()`, `cable(int fiberCapacity)`, `tube(UUID cableId, int number, int capacity)`, `fiber(UUID tubeId, int numberInTube, int absoluteNumber)`, `box(String type, Integer powerCapacity, Integer trayCapacity)`, `tray(UUID boxId, int number, int capacity)`, `oltPort(UUID oltId, int slot, int port)`, e `String postJson(String url, String body, int expectedStatus)` / `putJson(...)` / `delete(String url, int expectedStatus)` devolvendo o corpo.
  - ProblemDetail de validação com propriedade `errors`: lista de `{ "field": String, "message": String }`.
  - `ConstraintMessages.describe(String constraintName): Optional<String>`.

- [ ] **Step 1: Dependências.** Em `build.gradle` adicione `testImplementation 'org.springframework.boot:spring-boot-testcontainers'` e o módulo PostgreSQL do Testcontainers gerenciado pelo Boot 4 (`org.testcontainers:testcontainers-postgresql`; se o Boot gerenciar Testcontainers 1.x, use `org.testcontainers:postgresql`). Remova `com.bedatadriven:jackson-datatype-jts` (não usado, Jackson 2). Rode `./gradlew dependencies --configuration testRuntimeClasspath -q | grep testcontainers` e confirme que resolve.
- [ ] **Step 2: Infra.** Crie `PostgisContainerConfig`, `IntegrationTest`, `NetworkFixtures`. Faça `GeofiberBackendApplicationTests` estender `IntegrationTest`.
- [ ] **Step 3: Testes de erro que falham** em `ApiErrorsIT extends IntegrationTest`:
  - `validationErrorListsFields`: POST `/api/v1/boxes` com `{"type":"CTO"}` → 400, `$.title` presente, `$.errors[*].field` contém `name` e `location`.
  - `malformedJsonIsProblemDetail`: POST `/api/v1/boxes` com `{` → 400, `content-type` `application/problem+json`.
  - `unknownIdIs404ProblemDetail`: GET `/api/v1/boxes/{uuid aleatório}` → 404, `application/problem+json`, `$.detail` = `"Box not found"`.
  - `duplicateTubeNumberHasSpecificMessage`: dois POST de tubo número 1 no mesmo cabo → segundo 409, `$.detail` = `"A tube with this number already exists in the cable"`.
  - `invalidEnumIs400`: POST box com `"type":"XYZ"` → 400.
- [ ] **Step 4: Rode** `./gradlew test --tests '*ApiErrorsIT'` → FAIL (formato/mensagens).
- [ ] **Step 5: Implemente.** `spring.mvc.problemdetails.enabled=true` no `application.properties`. `ApiExceptionHandler extends ResponseEntityExceptionHandler`: sobrescreva `handleMethodArgumentNotValid` adicionando `errors`; trate `DataIntegrityViolationException` extraindo o nome da constraint (`ConstraintViolationException#getConstraintName` do Hibernate na cadeia de causas) e usando `ConstraintMessages.describe`, fallback `"The requested change conflicts with existing infrastructure data."`; trate `ResponseStatusException` mantendo `reason` como `detail`. `ConstraintMessages` mapeia ao menos: `uq_loose_tube_number`, `uq_fiber_absolute_number`, `uq_fiber_tube_number`, `uq_olt_port_position`, `uq_splitter_port_number`, `uq_tray_box_number`, `customer_contract_code_key` ("A customer with this contract code already exists"), e as constraints novas das Tasks 2–4 (cada task adiciona as suas). Mensagem do tubo exatamente como no teste.
- [ ] **Step 6: Rode** `./gradlew test` → todos PASS.

### Task 2: Cabo ligado aos elementos (`cable_attachment`)

**Files:**
- Create: `src/main/resources/db/migration/V4__cable_attachments.sql`
- Create: `entities/cable/CableAttachmentEntity.java`, `enums/cable/CableAttachmentRoleEnum.java` (`START`, `PASS_THROUGH`, `END`)
- Create: `repositories/cable/CableAttachmentRepository.java`
- Create: `dto/cable/CableAttachmentRequest.java`, `dto/cable/CableAttachmentResponse.java`
- Create: `services/cable/CableAttachmentService.java`, `controllers/cable/CableAttachmentController.java`
- Modify: `services/olt/OltPortService.java`, `services/splitter/SplitterService.java`, `services/splice/SpliceService.java`, `controllers/errors/ConstraintMessages.java`
- Modify (testes existentes que ligam fibras precisam anexar o cabo antes)
- Test: `src/test/java/com/geofiber/geofiber_backend/cable/CableAttachmentIT.java`

(Caminhos relativos a `src/main/java/com/geofiber/geofiber_backend/`.)

**Interfaces:**
- Consumes: `NetworkFixtures` (Task 1).
- Produces:
  - Tabela `cable_attachment(id UUID PK, cable_id FK not null, sequence_number INT > 0 not null, role VARCHAR(16) not null, box_id FK null, olt_id FK null, CHECK exatamente um de box_id/olt_id)`; únicos `uq_cable_attachment_box (cable_id, box_id)`, `uq_cable_attachment_olt (cable_id, olt_id)`, `uq_cable_attachment_sequence (cable_id, sequence_number)`; índices em `box_id` e `olt_id`.
  - `record CableAttachmentRequest(@NotNull CableAttachmentRoleEnum role, UUID boxId, UUID oltId)`; corpo do PUT é `List<@Valid CableAttachmentRequest>` na ordem do cabo (sequence = índice + 1).
  - `record CableAttachmentResponse(Integer sequenceNumber, CableAttachmentRoleEnum role, UUID boxId, UUID oltId, String elementName)`.
  - Endpoints `PUT /api/v1/cables/{cableId}/attachments` (substitui tudo) e `GET` igual.
  - `CableAttachmentService.requireAttachedToBox(UUID cableId, UUID boxId): void` e `requireAttachedToOlt(UUID cableId, UUID oltId): void`, lançando 409 `"Cable is not attached to this box"` / `"Cable is not attached to this OLT"`.
  - `FiberTerminationRepository` ganha a consulta usada para checar se há ligações de fibras do cabo em um elemento (portas de OLT da OLT, portas de splitter de splitters da caixa, emendas/conexões na caixa).

- [ ] **Step 1: Testes que falham** em `CableAttachmentIT`:
  - `replacesAndListsAttachmentsInOrder`: PUT `[START olt, PASS_THROUGH ceo, END cto]` → 200; GET devolve 3 itens com `sequenceNumber` 1,2,3 e `elementName`.
  - `rejectsAttachmentWithBothOrNoElement` → 400.
  - `rejectsListWithoutExactlyOneStartAndOneEnd` → 400 (regras: lista vazia permitida; não vazia exige 1 START primeiro e 1 END último; PASS_THROUGH só no meio).
  - `oltPortRejectsFiberFromUnattachedCable` → 409 `"Cable is not attached to this OLT"`.
  - `spliceRejectsFiberFromCableNotAttachedToBox` → 409 `"Cable is not attached to this box"`.
  - `splitterPortRejectsFiberFromCableNotAttachedToBox` → 409.
  - `cannotDetachBoxWhileFiberIsConnectedThere` (Review Focus 2): emenda na caixa, PUT sem a caixa → 409 `"Cable still has fibers connected at a detached element"`; GET mantém a lista anterior.
- [ ] **Step 2: Rode** `./gradlew test --tests '*CableAttachmentIT'` → FAIL.
- [ ] **Step 3: Implemente** migração, entidade, repositório, DTOs, serviço, controller. Chame `requireAttachedTo*` em `OltPortService.apply` (quando `fiberId` não nulo), `SplitterService.updatePort` e `SpliceService.create` (ambas as fibras), antes de `relink`/`getAvailable`. Adicione as constraints novas a `ConstraintMessages` (`uq_cable_attachment_*` → `"Element is already attached to this cable"`).
- [ ] **Step 4:** Ajuste testes existentes que ligam fibras para anexar o cabo primeiro. Rode `./gradlew test` → PASS.

### Task 3: Portas de atendimento da CTO (`service_port`)

**Files:**
- Create: `src/main/resources/db/migration/V5__service_ports.sql`
- Create: `entities/serviceport/ServicePortEntity.java`, `repositories/serviceport/ServicePortRepository.java`
- Create: `dto/serviceport/ServicePortRequest.java`, `dto/serviceport/ServicePortResponse.java`
- Create: `services/serviceport/ServicePortService.java`, `controllers/serviceport/ServicePortController.java`
- Modify: `entities/termination/FiberTerminationEntity.java` (campo `servicePort`), `repositories/termination/FiberTerminationRepository.java`, `services/termination/FiberTerminationService.java`
- Modify: `entities/customer/CustomerEntity.java`, `dto/customer/CustomerRequest.java`, `dto/customer/CustomerResponse.java`, `services/customer/CustomerService.java`, `repositories/customer/CustomerRepository.java`
- Modify: `dto/splitter/SplitterRequest.java`, `dto/splitter/SplitterPortResponse.java`, `services/splitter/SplitterService.java`, `services/box/BoxService.java`, `controllers/errors/ConstraintMessages.java`
- Test: `src/test/java/com/geofiber/geofiber_backend/serviceport/ServicePortIT.java`

**Interfaces:**
- Consumes: `CableAttachmentService.requireAttachedToBox` (Task 2), `FiberTerminationService.relink/getAvailable/release`.
- Produces:
  - V5: tabela `service_port(id, box_id FK not null, port_number INT > 0, label VARCHAR(80), status VARCHAR(16) not null, splitter_port_id UUID UNIQUE FK null)`, `uq_service_port_box_number (box_id, port_number)`; `fiber_termination.service_port_id UUID UNIQUE FK null` e o CHECK `ck_termination_single_port` recriado para no máximo um entre `olt_port_id`, `splitter_port_id`, `service_port_id`; trigger que rejeita (ERRCODE 23514) porta de atendimento com `splitter_port_id` e fibra ao mesmo tempo, e saída de splitter que alimenta porta de atendimento e fibra ao mesmo tempo; `customer.service_port_id UUID UNIQUE FK`; migração de dados: para cada cliente com `splitter_port_id`, cria `service_port` na caixa do splitter com próximo número livre, `splitter_port_id` daquele cliente, status `OCCUPIED`, e move o cliente; depois `DROP COLUMN customer.splitter_port_id`.
  - `record ServicePortRequest(@NotNull @Positive Integer portNumber, @Size(max=80) String label, PortStatusEnum status, UUID splitterPortId, UUID fiberId, FiberEndEnum fiberEnd)` — `splitterPortId` e `fiberId` mutuamente exclusivos (400).
  - `record ServicePortResponse(UUID id, UUID boxId, Integer portNumber, String label, PortStatusEnum status, UUID splitterPortId, UUID fiberId, FiberEndEnum fiberEnd, UUID customerId)`.
  - Endpoints `POST/GET /api/v1/boxes/{boxId}/service-ports`, `PUT/DELETE /api/v1/boxes/{boxId}/service-ports/{portId}`.
  - `ServicePortService.createForSplitterOutputs(BoxAssetEntity box, List<SplitterPortEntity> outputs): void` usado por `SplitterService.create`.
  - `SplitterRequest` ganha `Boolean createServicePorts` (null = true se a caixa for CTO; true em caixa não-CTO → 400).
  - `CustomerRequest.connectedPortId` → `servicePortId`; `CustomerResponse.connectedPortId` → `servicePortId` e ganha `boxId`.
  - `SplitterPortResponse.customerId` → `servicePortId`.

- [ ] **Step 1: Testes que falham** em `ServicePortIT`:
  - `splitterInCtoGeneratesOneServicePortPerOutput`: CTO `powerCapacity` 8, splitter 1:8 → GET service-ports tem 8 portas, números 1..8, cada uma com `splitterPortId` da saída correspondente.
  - `splitterBeyondCapacityIsRejectedAtomically` (Review Focus 1): CTO `powerCapacity` 4, splitter 1:8 → 400 `"Box supports at most 4 service ports"`; GET splitters e service-ports vazios.
  - `servicePortsOnlyInCto`: POST em caixa CEO → 400 `"Only CTO boxes have service ports"`.
  - `servicePortFedByFiber`: porta com `fiberId` (cabo anexado) → 201, `fiberEnd` `"B"`; a fibra fica `OCCUPIED`.
  - `servicePortRejectsBothSources` → 400.
  - `customerConnectsToServicePortAndMovesBetweenPorts`: cliente na porta 1 → porta 1 `OCCUPIED`; PUT cliente para porta 2 → porta 1 `FREE`, porta 2 `OCCUPIED`.
  - `customerRejectsPortWithoutSource` → 409 `"Service port has no optical source"`.
  - `splitterOutputFeedingServicePortCannotTakeFiber`: PUT da saída com `fiberId` → 409 `"Splitter port already feeds a service port"`.
  - `cannotChangeCtoTypeWithServicePorts` (Review Focus 3): PUT box com `type` CEO → 409.
  - `deletingServicePortWithCustomerIsRejected` → 409.
- [ ] **Step 2: Rode** `./gradlew test --tests '*ServicePortIT'` → FAIL.
- [ ] **Step 3: Implemente** V5, entidade, repositório, DTOs, serviço e controller. `FiberTerminationService.release` limpa `servicePort`; `getAvailable` considera `servicePort` ocupado. `CustomerService` passa a usar porta de atendimento (mesma lógica de liberar/ocupar). `SplitterService.create` chama `createForSplitterOutputs` quando aplicável, na mesma transação. `BoxService.update` bloqueia troca de tipo de CTO com portas. `ConstraintMessages`: `uq_service_port_box_number` → `"A service port with this number already exists in the box"`.
- [ ] **Step 4:** Atualize testes/fixtures que usavam `connectedPortId`. Rode `./gradlew test` → PASS.

### Task 4: Conexões por conector

**Files:**
- Create: `src/main/resources/db/migration/V6__connector_connections.sql`
- Modify: `enums/OpticalConnectionTypeEnum.java` (+ `CONNECTOR`), `entities/connection/OpticalConnectionEntity.java` (+ `box`, `attenuationDb`)
- Modify: `services/termination/FiberTerminationService.java` (`fuse` → `connect`), `services/splice/SpliceService.java`
- Create: `dto/connection/ConnectionRequest.java`, `dto/connection/ConnectionResponse.java`, `services/connection/ConnectionService.java`, `controllers/connection/ConnectionController.java`
- Modify: `repositories/connection/OpticalConnectionRepository.java`
- Test: `src/test/java/com/geofiber/geofiber_backend/connection/ConnectionIT.java`

**Interfaces:**
- Consumes: `CableAttachmentService.requireAttachedToBox`, `FiberTerminationService.getAvailable/release`.
- Produces:
  - V6: `optical_connection` ganha `box_id UUID FK` e `attenuation_db DOUBLE PRECISION CHECK (>= 0)`; preenche `box_id`/`attenuation_db` das fusões a partir de `splice`; `box_id SET NOT NULL`; CHECK de `connection_type` passa a `IN ('FUSION','CONNECTOR')`.
  - `FiberTerminationService.connect(FiberTerminationEntity first, FiberTerminationEntity second, OpticalConnectionTypeEnum type, BoxAssetEntity box, Double attenuationDb): OpticalConnectionEntity` (substitui `fuse`; `SpliceService` passa `FUSION`).
  - `record ConnectionRequest(@NotNull UUID fiberAId, @NotNull FiberEndEnum fiberAEnd, @NotNull UUID fiberBId, @NotNull FiberEndEnum fiberBEnd, @DecimalMin("0.0") Double attenuationDb)`.
  - `record ConnectionResponse(UUID id, UUID boxId, OpticalConnectionTypeEnum type, UUID fiberAId, FiberEndEnum fiberAEnd, UUID fiberBId, FiberEndEnum fiberBEnd, Double attenuationDb)`.
  - Endpoints `POST/GET /api/v1/boxes/{boxId}/connections`, `DELETE /api/v1/boxes/{boxId}/connections/{connectionId}`. GET lista só `CONNECTOR`; DELETE de uma `FUSION` → 409 `"Fusions are removed through the splice endpoint"`.

- [ ] **Step 1: Testes que falham** em `ConnectionIT`:
  - `connectsTwoFiberEndsWithConnector`: 201, `type` `"CONNECTOR"`, ambas as fibras `OCCUPIED`.
  - `rejectsSameFiber` → 400; `rejectsEndAlreadySpliced` → 409 `"Fiber end B is not available"`; `rejectsCableNotAttached` → 409.
  - `deleteReleasesEnds`: DELETE → 204, fibras voltam a `AVAILABLE`.
  - `cannotDeleteFusionHere` → 409.
  - `spliceStillWorksAndReportsBox`: emenda existente aparece com `boxId` (verificar via SQL `jdbc` que `optical_connection.box_id` foi preenchido).
- [ ] **Step 2: Rode** `./gradlew test --tests '*ConnectionIT'` → FAIL.
- [ ] **Step 3: Implemente** V6, enum, entidade, `connect`, serviço, controller.
- [ ] **Step 4: Rode** `./gradlew test` → PASS.

### Task 5: CRUD completo

**Files:**
- Modify: controllers e services de `cable` (tubo, fibra), `tray`, `splitter`, `olt` (porta), `box`, `cable`, `olt`
- Create: `dto/cable/FiberStatusRequest.java`
- Test: `src/test/java/com/geofiber/geofiber_backend/crud/CrudIT.java`

**Interfaces:**
- Produces (todos 404 para id inexistente/de outro pai, 409 com mensagem quando houver dependência):
  - `PUT/DELETE /api/v1/cables/{cableId}/tubes/{tubeId}` — capacidade não pode ficar abaixo do maior `numberInTube` existente nem estourar o cabo; DELETE só sem fibras (`"Tube still has fibers"`).
  - `PUT/DELETE /api/v1/tubes/{tubeId}/fibers/{fiberId}` — PUT altera números/cor; DELETE só se nenhuma ponta ligada (`"Fiber is connected"`).
  - `PUT /api/v1/tubes/{tubeId}/fibers/{fiberId}/status` com `record FiberStatusRequest(@NotNull FiberStatusEnum status)`; aceita `AVAILABLE`, `BROKEN`, `RESERVED`; `OCCUPIED` → 400; fibra conectada → 409.
  - `PUT/DELETE /api/v1/boxes/{boxId}/trays/{trayId}` — capacidade ≥ maior posição usada; DELETE só sem emendas.
  - `PUT/DELETE /api/v1/boxes/{boxId}/splitters/{splitterId}` — PUT só `balanced` (razão imutável: 400 se mudar); DELETE bloqueado se alguma porta tiver fibra ou alimentar porta de atendimento com cliente (Review Focus 4: `"Splitter still has connections"`); senão apaga portas de atendimento geradas sem cliente e as portas do splitter.
  - `DELETE /api/v1/olts/{oltId}/ports/{portId}` — só sem fibra.
  - `DELETE` de cabo/caixa/OLT: 409 com mensagem do recurso dependente em vez do genérico (`"Cable still has tubes"`, `"Box still has splitters, trays, service ports or attached cables"`, `"OLT still has ports or attached cables"`); DELETE de cabo apaga seus `cable_attachment`.

- [ ] **Step 1: Testes que falham** em `CrudIT`: um teste por endpoint acima com o caminho feliz e o caso de bloqueio, usando exatamente as mensagens listadas.
- [ ] **Step 2: Rode** `./gradlew test --tests '*CrudIT'` → FAIL.
- [ ] **Step 3: Implemente** endpoints e regras.
- [ ] **Step 4: Rode** `./gradlew test` → PASS.

### Task 6: Swagger + `API.md`

**Files:**
- Modify: `build.gradle` (`org.springdoc:springdoc-openapi-starter-webmvc-ui`, versão 3.x mais recente compatível com Boot 4.1 — confirme no Maven Central)
- Create: `src/main/java/com/geofiber/geofiber_backend/config/OpenApiConfig.java`
- Modify: todos os controllers (`@Tag`, `@Operation`, `@ApiResponse` 400/404/409 com `ProblemDetail`) e DTOs (`@Schema` com descrição e exemplo nos campos principais)
- Rewrite: `API.md`
- Test: `src/test/java/com/geofiber/geofiber_backend/docs/OpenApiIT.java`

**Interfaces:**
- Produces: `GET /v3/api-docs`, Swagger UI em `/swagger-ui.html`; bean `OpenAPI` com título `"GeoFiber API"`, descrição em português, licença do projeto (ler `LICENSE` se existir; senão omitir).

- [ ] **Step 1: Teste que falha** `OpenApiIT.exposesAllResourceGroups`: GET `/v3/api-docs` → 200; `$.info.title` = `"GeoFiber API"`; `$.paths` contém `/api/v1/boxes/{boxId}/service-ports`, `/api/v1/cables/{cableId}/attachments`, `/api/v1/boxes/{boxId}/connections`; `$.tags[*].name` contém `Caixas`, `Cabos`, `OLTs`, `Clientes`, `Splitters`, `Portas de atendimento`, `Emendas`, `Conexões`, `Bandejas`.
- [ ] **Step 2: Rode** → FAIL. **Step 3:** adicione dependência, config e anotações. **Step 4:** rode `./gradlew test` → PASS.
- [ ] **Step 5: Reescreva `API.md`** com: como rodar (Docker + `.env`), link do Swagger, modelo de domínio em diagrama de texto (OLT → cabo → caixa → splitter → porta de atendimento → cliente), convenções (pontas A/B e padrões, `powerCapacity` = portas de atendimento, cabo anexado), fluxo completo de cadastro com payloads reais na ordem correta, formato de erro com exemplo de `errors`, tabela de regras de 409.
- [ ] **Step 6: Verificação manual:** `./gradlew bootRun`, abrir `/swagger-ui.html`, executar o fluxo do `API.md` por curl contra o banco local; apagar os dados de teste criados.
