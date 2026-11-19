# Conclusão da Fase 1 — Fundação técnica e domínio

## Intenção

Fechar a Fase 1 do roadmap: um backend capaz de cadastrar e persistir a infraestrutura FTTH
sem depender dos dados fictícios do frontend, com a API documentada (Swagger + `API.md`).
O projeto é open source, então a API precisa ser compreensível por quem chega de fora.

## Estado atual (19/11/2026)

Já existe e funciona: OLT e portas PON, cabos (geometria e comprimento via PostGIS), tubos,
fibras com pontas A/B, caixas (CTO/CEO/DIO), bandejas, splitters com portas, emendas por
fusão, clientes ligados a saídas de splitter. Migrações Flyway V1–V3. Sem Swagger; `API.md`
parcial; formato de erro inconsistente; nada commitado ainda.

## Objetivos

1. **Portas de atendimento da CTO** (abordagem B, escolhida pelo usuário).
2. **Cabo ligado aos elementos** por onde passa, e o backend usando isso para impedir
   conexões incompatíveis.
3. **Conexões por conector**, além da fusão.
4. **CRUD completo** dos elementos que hoje só têm criação/listagem.
5. **Erros padronizados** em ProblemDetail (RFC 9457) com detalhes úteis.
6. **Swagger/OpenAPI** e `API.md` revisado, feitos por último.
7. **Testes de integração** dos fluxos principais contra PostGIS real.

## Não objetivos (ficam para fases seguintes)

- Postes, reserva técnica, busca por área do mapa, KMZ/KML (Fase 2).
- Geração automática de tubos/fibras por código de cores (Fase 3).
- Fiber trace, análise de impacto, orçamento óptico (Fase 4).
- ONU/ONT e modelo genérico de equipamentos: a OLT é o único equipamento ativo da Fase 1.
- Autenticação/autorização.

## Anti-objetivos

- Inferir continuidade óptica por proximidade no mapa. Toda ligação é explícita.
- Duas fontes da verdade para a mesma ligação física (o erro corrigido na V3).
- Alterar migrações já aplicadas (V1–V3); mudanças de schema só em novas migrações.

## Abordagens consideradas (portas de atendimento)

- **A. Cliente liga em porta de splitter ou em ponta de fibra.** Menor mudança, mas não
  registra a identificação usada em campo ("CTO-12, porta 3").
- **B. CTO com portas de atendimento numeradas; cliente sempre liga numa delas.** Escolhida.
  Uniformiza CTO com e sem splitter, bate com a etiqueta da caixa e simplifica o
  "rastrear por CTO" da Fase 4.

## Design

### 1. Portas de atendimento (`service_port`)

- Tabela `service_port`: `id`, `box_id`, `port_number` (único por caixa), `status`
  (`PortStatusEnum`), `splitter_port_id` (único, opcional), `label` opcional.
- Só caixas do tipo `CTO` têm portas de atendimento (400 nas demais).
- `network_box.power_capacity` passa a significar a quantidade máxima de portas de
  atendimento da CTO (nome do campo na API mantido, documentado).
- Uma porta é alimentada por **uma** destas fontes, ou nenhuma:
  - uma saída de splitter da mesma caixa (`splitter_port_id`);
  - uma ponta de fibra (`fiber_termination.service_port_id`, nova coluna única, seguindo o
    padrão das portas de OLT/splitter). Ponta padrão: B.
  - O banco garante que não haja as duas ao mesmo tempo (trigger ou checagem no serviço +
    teste).
- Cliente: `customer.splitter_port_id` é substituído por `customer.service_port_id` (único).
  A migração cria uma porta de atendimento para cada cliente já ligado a splitter e move a
  ligação.
- Ao criar um splitter em uma CTO, a requisição aceita `createServicePorts` (padrão `true`
  em CTO): gera uma porta de atendimento por saída, numeradas na sequência das existentes,
  respeitando `powerCapacity`.
- Uma saída de splitter alimenta uma porta de atendimento **ou** uma fibra (cascata), não
  ambos. A regra atual "saída atende cliente ou fibra" passa a ser essa.
- Status da porta: OCCUPIED quando há cliente; FREE/RESERVED/FAULTY definidos pelo usuário
  (mesmo `PortStatusResolver`). Porta sem fonte pode existir (planejamento), mas não aceita
  cliente (409).
- Endpoints: `POST/GET /boxes/{boxId}/service-ports`, `PUT/DELETE
  /boxes/{boxId}/service-ports/{portId}`. Cliente passa a enviar `servicePortId`.

### 2. Cabo ligado aos elementos (`cable_attachment`)

- Tabela `cable_attachment`: `cable_id`, `sequence` (ordem ao longo do cabo), `box_id` ou
  `olt_id` (exatamente um), `role` (`START`, `PASS_THROUGH`, `END`). Único por
  (`cable_id`, elemento).
- Endpoints: `PUT /cables/{id}/attachments` (substitui a lista inteira) e `GET`.
- Regras de compatibilidade aplicadas pelo backend (409 quando violadas):
  - fibra em porta de OLT: o cabo da fibra precisa estar ligado àquela OLT;
  - fibra em porta de splitter, porta de atendimento ou emenda: o cabo precisa estar
    ligado à caixa;
  - emenda: as duas fibras precisam ter cabo ligado à caixa;
  - remover um elemento da lista de um cabo é bloqueado se ainda houver ligações de fibras
    daquele cabo naquele elemento.
- Geometria não é validada contra a posição das caixas (sem inferência por proximidade).

### 3. Conexões por conector

- `OpticalConnectionTypeEnum` ganha `CONNECTOR` (nova migração ajusta o CHECK).
- `POST/GET/DELETE /boxes/{boxId}/connections`: liga duas pontas de fibra numa caixa via
  conector (ex.: cordão no DIO), mesmas validações de disponibilidade e de cabo ligado.
  Atenuação opcional. Emendas continuam no endpoint próprio (fusão + bandeja).
- Para isso `optical_connection` ganha `box_id` e `attenuation_db` (nulos para as fusões
  antigas são preenchidos a partir da emenda).

### 4. CRUD completo

- `PUT` e `DELETE` para tubos, fibras, bandejas, splitters e portas de OLT; `DELETE` para
  portas de atendimento e conexões.
- Exclusões são bloqueadas com 409 e mensagem clara quando há dependências (ex.: fibra
  ligada, bandeja com emendas, splitter com porta ocupada). Exclusão de fibra apaga as
  pontas (cascade já existe).
- Fibra ganha `PUT .../status` para marcar `BROKEN`/`RESERVED` (bloqueado se conectada).
- Mudar capacidade não pode ficar abaixo do que já está em uso.

### 5. Erros

- `spring.mvc.problemdetails.enabled=true` e um `@RestControllerAdvice` que devolve
  ProblemDetail em todos os casos: validação (com lista `errors` de campo + mensagem),
  JSON malformado, 404, 409.
- 409 de banco traduz o nome da constraint violada para mensagem legível (mapa de
  constraints conhecidas; fallback genérico).

### 6. Documentação

- `springdoc-openapi-starter-webmvc-ui` na versão compatível com Spring Boot 4 (3.x).
  Swagger UI em `/swagger-ui.html`, spec em `/v3/api-docs`.
- Tags por tipo concreto, `@Operation`/`@Schema` nos controllers e DTOs com descrições em
  português, exemplos nos principais payloads, respostas de erro documentadas.
- `API.md` reescrito como guia: como rodar, modelo de domínio (diagrama em texto),
  convenções (pontas A/B, portas de atendimento, regras de compatibilidade), fluxo
  completo de cadastro de uma rede de exemplo, formato de erro e link para o Swagger.

### 7. Testes

- Testcontainers com imagem `postgis/postgis` para testes de integração: fluxo completo
  OLT → cabo → caixas → emenda → splitter → porta de atendimento → cliente, e os casos de
  409 principais. Testes unitários para regras isoladas.
- `GeofiberBackendApplicationTests` passa a usar o container, não o banco local.

## Restrições

- Java 17 (toolchain), Spring Boot 4.1, PostgreSQL + PostGIS, Flyway, Hibernate
  `ddl-auto=validate`.
- Pacotes por tipo funcional e, dentro, por tipo concreto (`service_port` fica em
  `.../serviceport/`, `cable_attachment` em `.../cable/`).

## Decisões do construtor (calls registradas)

- `powerCapacity` reaproveitado como capacidade de portas de atendimento.
- ONU/equipamentos genéricos fora da Fase 1.
- Commit: não será feito automaticamente; o usuário decide quando commitar.
