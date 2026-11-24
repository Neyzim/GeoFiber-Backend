# GeoFiber Backend — Especificação (Fase 1)

Especificação funcional do backend tal como implementado ao fim da Fase 1. Define **o que** o
sistema faz e **quais regras** garante. A referência de cada endpoint (payloads e respostas)
fica no Swagger (`/swagger-ui.html`) e o guia de uso no [`API.md`](../API.md).

- **Status:** Fase 1 concluída (base técnica e domínio).
- **Stack:** Java 17, Spring Boot 4.1, Spring Data JPA / Hibernate 7, PostgreSQL 16 + PostGIS 3.4,
  Flyway, springdoc-openapi 3.2, Testcontainers.
- **Projeto open source**: a API não assume dados fictícios do frontend; toda a rede é cadastrada
  e persistida por ela.

Convenção: **RN** = regra de negócio, **RT** = requisito técnico. Mensagens entre aspas são o
`detail` exato devolvido pela API.

---

## 1. Escopo

### 1.1 Dentro da Fase 1
- Cadastro completo (criar, listar, buscar, editar, excluir) de OLTs, portas PON, cabos, tubos,
  fibras, caixas (CTO/CEO/DIO), bandejas, emendas, conexões por conector, splitters, portas de
  atendimento e clientes.
- Ligações ópticas explícitas entre pontas de fibra e portas, com status calculados.
- Lista ordenada dos elementos por onde cada cabo passa.
- Erros padronizados (ProblemDetail, RFC 9457) e documentação OpenAPI.
- Testes de integração contra PostGIS real.

### 1.2 Fora da Fase 1 (não-objetivos)
- Autenticação, autorização e multiusuário.
- Paginação e busca por área do mapa.
- Cálculo de orçamento de potência / atenuação de ponta a ponta.
- Validação geométrica entre o traçado do cabo e a posição das caixas.
- Dedução de ligações por proximidade no mapa (**anti-objetivo**: continuidade é sempre declarada).

---

## 2. Modelo de domínio

```
OLT ─< porta PON
Cabo ─< tubo loose ─< fibra ─< ponta (A, B)
Cabo ─< elemento do cabo (START, PASS_THROUGH*, END) → caixa | OLT
Caixa ─< bandeja ─< emenda (FUSION)
Caixa ─< conexão (CONNECTOR)
Caixa ─< splitter ─< porta de splitter (1 entrada, N saídas)
CTO ─< porta de atendimento ─ cliente
```

| Entidade | Atributos principais | Enums |
|---|---|---|
| OLT | nome, modelo, localização (Point) | `InfrastructureStatus`: PLANNED, INSTALLED, ACTIVE, DAMAGED, DEACTIVATED |
| Porta PON | slot, porta, status | `PortStatus`: FREE, OCCUPIED, RESERVED, FAULTY |
| Cabo | nome, função, traçado (LineString), capacidade de fibras, comprimento (m) | `CableRole`: BACKBONE, FEEDER, DISTRIBUTION, DROP, INTERCONNECTION |
| Tubo loose | número, capacidade, cor | `OpticalColor` (12 cores ABNT/TIA) |
| Fibra | número no tubo, número absoluto, cor, status | `FiberStatus`: AVAILABLE, OCCUPIED, BROKEN, RESERVED |
| Ponta de fibra | A ou B, status, porta ligada | `FiberEnd`: A, B · `TerminationStatus`: AVAILABLE, CONNECTED, BROKEN, RESERVED |
| Caixa | nome, tipo, localização, `powerCapacity`, `trayCapacity` | `BoxType`: CTO, CEO, DIO |
| Bandeja | número, capacidade (posições) | — |
| Emenda / Conexão | duas pontas, caixa, atenuação (dB); emenda tem bandeja e posição | `OpticalConnectionType`: FUSION, CONNECTOR |
| Splitter | razão, balanceado | `SplitterRatio`: 1:2, 1:4, 1:8, 1:16, 1:32, 1:64 |
| Porta de splitter | número, direção, atenuação, status | `PortDirection`: INPUT, OUTPUT |
| Porta de atendimento | número, rótulo, status, fonte óptica | — |
| Cliente | nome, código de contrato (único), endereço, localização, situação | `CustomerStatus`: ACTIVE, BLOCKED, CANCELLED |
| Histórico do cliente | situação, porta de atendimento, data da mudança | — |

Todos os ativos de rede (OLT, cabo, caixa) compartilham nome, descrição, UUID e status de uso
(`NetworkAssetEntity`, superclasse sem tabela). Coordenadas em WGS 84 (SRID 4326), sempre
**longitude antes de latitude**.

---

## 3. Regras de negócio

### 3.1 Pontas de fibra (fonte única das ligações)
- **RN-01** Toda fibra nasce com exatamente duas pontas: **A** (lado da OLT) e **B** (lado do
  cliente). Garantido no banco por trigger diferido.
- **RN-02** A ligação entre fibra e porta (OLT, splitter, atendimento) é gravada **somente** na
  ponta. Nenhuma porta guarda a fibra.
- **RN-03** Uma ponta participa de no máximo uma ligação. Senão: `409 "Fiber end X is not available"`.
- **RN-04** Fibra BROKEN ou RESERVED não pode ser ligada: `409 "Fiber is BROKEN and cannot be connected"`.
- **RN-05** Status da fibra é automático: OCCUPIED se alguma ponta está ligada, AVAILABLE ao
  liberar todas. BROKEN/RESERVED são definidos manualmente (RN-33).
- **RN-06** Pontas padrão quando `fiberEnd` não é informado:

  | Ligação | Ponta |
  |---|---|
  | Porta de OLT | A |
  | Entrada de splitter | B |
  | Saída de splitter | A |
  | Porta de atendimento | B |
  | Emenda: entrada / saída | B / A |
  | Conexão por conector | obrigatória |

### 3.2 Elementos do cabo
- **RN-07** A lista é ordenada: um `START`, zero ou mais `PASS_THROUGH`, um `END`.
  Erros (400): `"A cable needs a START and an END element"`,
  `"Attachments must be START, then PASS_THROUGH elements, then END"`.
- **RN-08** Cada elemento referencia exatamente uma caixa **ou** uma OLT:
  `400 "Each attachment must reference exactly one of boxId or oltId"`.
- **RN-09** Um elemento aparece uma única vez no cabo: `400 "An element can appear only once in the cable"`.
- **RN-10** A lista é substituída por inteiro (`PUT`). Remover um elemento onde o cabo ainda tem
  fibras ligadas (porta, emenda, conexão ou porta de atendimento) é bloqueado e a lista não muda:
  `409 "Cable still has fibers connected at a detached element"`.
- **RN-11** Uma fibra só pode ser ligada numa caixa ou OLT presente na lista do seu cabo:
  `409 "Cable is not attached to this box"` / `"Cable is not attached to this OLT"`.

### 3.3 Cabos, tubos e fibras
- **RN-12** A soma das capacidades dos tubos não ultrapassa a capacidade do cabo:
  `400 "Tube capacities exceed the cable fiber capacity of N"`.
- **RN-13** O número da fibra no tubo não ultrapassa a capacidade do tubo, e o número absoluto
  não ultrapassa a do cabo: `400 "Fiber number exceeds its cable or tube capacity"`.
- **RN-14** Números de tubo (por cabo) e de fibra (por tubo e por cabo) são únicos: `409` com
  mensagem nomeando o recurso.
- **RN-15** O comprimento do cabo é calculado pelo PostGIS a partir do traçado (metros, geodésico).
- **RN-37** `POST /cables/{id}/structure` cria vários tubos e preenche cada um até a capacidade,
  numa única transação. Fibras recebem as cores ABNT na ordem do enum (verde, amarelo, branco,
  azul, vermelho, violeta, marrom, rosa, preto, cinza, laranja, turquesa; recomeça a cada 12) e
  numeração absoluta contínua após as fibras já existentes no cabo. Qualquer violação de RN-12 a
  RN-14 desfaz a operação inteira. Lista vazia: 400.

### 3.4 Caixas, bandejas e emendas
- **RN-16** `trayCapacity` limita o número da bandeja: `400 "Box supports at most N trays"`.
- **RN-17** Emenda exige bandeja existente. Se `positionNumber` for fornecido, ocupa aquela posição
  (validação: dentro do range 1..capacity e disponível); se omitido, ocupa a **menor posição livre**.
  Sem posição livre: `409 "Tray N is full"`. Posição ocupada: `409 "Position N is already occupied in this tray"`.
  Posição fora do range: `400 "Position N exceeds tray capacity of M"`.
- **RN-18** Emenda liga duas fibras diferentes: `400 "Input and output fibers must be different"`.
- **RN-19** Excluir uma emenda libera as duas pontas.

### 3.5 Conexões por conector
- **RN-20** Liga duas pontas de fibras diferentes dentro de uma caixa:
  `400 "A connection must join two different fibers"`.
- **RN-21** O endpoint de conexões lista e remove só conexões CONNECTOR. Fusões são removidas
  pela emenda: `409 "Fusions are removed through the splice endpoint"`.

### 3.6 Splitters
- **RN-22** A porta 1 é a entrada; as portas 2..N+1 são as saídas (N = razão).
- **RN-23** A razão é imutável: `400 "Splitter ratio cannot be changed"`. Só `balanced` é editável.
- **RN-24** Numa CTO, criar um splitter gera por padrão uma porta de atendimento por saída
  (`createServicePorts`, padrão `true` em CTO e `false` nas demais), numeradas após a maior
  existente. Se não couberem em `powerCapacity`, nada é criado: `400 "Box supports at most N service ports"`.
- **RN-25** Saída que alimenta uma porta de atendimento não recebe fibra:
  `409 "Splitter port already feeds a service port"`.

### 3.7 Portas de atendimento (só em CTO)
- **RN-26** Existem apenas em CTO: `400 "Only CTO boxes have service ports"`. Uma CTO com portas
  de atendimento não muda de tipo: `409 "Box has service ports and must remain a CTO"`.
- **RN-27** Numeradas de 1 até `powerCapacity`; número único por caixa.
- **RN-28** A fonte óptica é **uma** saída de splitter da mesma caixa **ou** uma ponta de fibra,
  nunca as duas (verificado no serviço e por trigger no banco):
  - `400 "A service port takes either a splitter port or a fiber, not both"`
  - `400 "Splitter port must be an output of a splitter in this box"`
  - `409 "Splitter port already feeds a fiber"` / `"Splitter port already feeds a service port"`
- **RN-29** A fonte pode ser trocada (fibra ↔ splitter) mesmo com cliente ligado; a fonte
  anterior é liberada. Deixar sem fonte uma porta com cliente: `409 "Service port is serving a customer"`.
- **RN-30** Excluir porta com cliente: `409 "Service port is serving a customer"`.

### 3.8 Clientes
- **RN-31** O cliente é ligado a uma porta de atendimento (opcional). A porta precisa ter fonte
  óptica e estar livre: `409 "Service port has no optical source"` / `"Service port is not available"`.
- **RN-32** Mudar o cliente de porta libera a porta anterior. O código de contrato é único.
- **RN-35** Situação do cliente (padrão ACTIVE):
  - **BLOCKED**: serviço suspenso; o cliente continua ocupando a porta.
  - **CANCELLED**: a porta é liberada e o cliente continua cadastrado. Ligar um cliente cancelado
    a uma porta: `409 "A cancelled customer cannot use a service port"`. Para religar, volte a ACTIVE
    ou BLOCKED informando a porta.
- **RN-36** Toda mudança de situação ou de porta grava uma linha no histórico do cliente (situação,
  porta daquele momento, data), consultado em `GET /customers/{id}/history`. Encerrar contrato é
  sempre CANCELLED; `DELETE` existe só para cadastro feito por engano e apaga o histórico junto.

### 3.9 Status manuais
- **RN-33** `PUT /tubes/{tubeId}/fibers/{fiberId}/status` aceita AVAILABLE, BROKEN, RESERVED,
  apenas com a fibra desconectada (`409 "Fiber is connected"`). OCCUPIED é rejeitado:
  `400 "OCCUPIED is set automatically by connections"`.
- **RN-34** Portas (OLT, splitter, atendimento) aceitam FREE, RESERVED, FAULTY no campo `status`;
  OCCUPIED é aplicado automaticamente quando há ligação. **FAULTY vale mesmo com ligação**: uma
  porta de atendimento com defeito mantém o cliente; ao ser reparada (status FREE ou vazio) volta a
  OCCUPIED. Porta FAULTY continua FAULTY quando o cliente sai e não aceita cliente novo.

### 3.10 Edição e exclusão com dependências
Capacidades não podem ficar abaixo do que já está em uso (400) e exclusões são bloqueadas
enquanto houver dependentes (409). Nada é apagado em cascata, exceto a lista de elementos do
cabo e as portas de atendimento geradas por um splitter excluído.

| Recurso | Edição bloqueada (400) | Exclusão bloqueada (409) |
|---|---|---|
| Cabo | `"Cable fiber capacity is below the total capacity of its tubes"` | `"Cable still has tubes"` |
| Tubo | `"Tube capacity is below its highest fiber number"` | `"Tube still has fibers"` |
| Fibra | número além da capacidade (RN-13) | `"Fiber is connected"` |
| Caixa | `powerCapacity` abaixo da maior porta de atendimento; `"Box has trays numbered above the new tray capacity"` | `"Box still has splitters, trays, service ports, connections or attached cables"` |
| Bandeja | `"Tray capacity is below its highest used position"` | `"Tray still has splices"` |
| Splitter | RN-23 | `"Splitter still has connections"` (porta com fibra ou porta de atendimento com cliente) |
| OLT | — | `"OLT still has ports or attached cables"` |
| Porta PON | — | `"OLT port is connected"` |
| Porta de atendimento | RN-29 | RN-30 |

---

## 4. Requisitos técnicos

- **RT-01 API REST** sob `/api/v1`, JSON. Recursos filhos aninhados no pai
  (`/cables/{id}/tubes`, `/boxes/{id}/splitters` …); id de outro pai devolve 404.
- **RT-02 Erros** sempre em ProblemDetail (`application/problem+json`):
  - 400 com lista `errors[{field, message}]` em validação de payload; também JSON malformado e
    enum desconhecido.
  - 404 para recurso inexistente (`"Box not found"` …).
  - 409 para violação de unicidade/constraint, com mensagem por nome de constraint
    (`ConstraintMessages`) ou genérica.
  - 500 para erro inesperado, sem expor detalhes internos.
  - O `detail` é em inglês; mensagens de validação seguem `Accept-Language`.
- **RT-03 Persistência:** schema evolui só por migrações Flyway (V1–V7); migrações aplicadas nunca
  são editadas. Hibernate em `ddl-auto=validate`; `open-in-view=false`.
- **RT-04 Integridade no banco** (além do serviço): unicidades de numeração, CHECKs de enums e de
  "uma única porta por ponta", trigger diferido de duas pontas por fibra, trigger de fonte única
  da porta de atendimento, `termination_a_id < termination_b_id` em `optical_connection`.
- **RT-05 Geometria:** PostGIS, SRID 4326 (Point para OLT/caixa/cliente, LineString para cabo).
- **RT-06 Documentação:** OpenAPI em `/v3/api-docs` e Swagger UI em `/swagger-ui.html`, com tags e
  resumos em português; guia em `API.md`. Desligados por padrão: só respondem com
  `API_DOCS_ENABLED=true` (máquina de desenvolvimento); em produção, `404`.
- **RT-07 Testes:** `./gradlew test`. Integração com Testcontainers (`postgis/postgis:18-3.7`),
  tabelas truncadas após cada teste; o banco local não é tocado. 74 testes na conclusão da Fase 1.
- **RT-08 Transações:** cada operação de escrita é atômica; falha de regra no meio da operação
  desfaz tudo (ex.: splitter sem capacidade não deixa splitter nem portas).
- **RT-09 CORS:** liberado em `/api/**` para as origens de `geofiber.cors.allowed-origins`
  (variável `CORS_ALLOWED_ORIGINS`, padrão `http://localhost:4200`); outras origens recebem 403
  no preflight. Header `Location` exposto.
- **RT-10 Leituras agregadas para o frontend** (consultas em lote, sem N+1):
  - `GET /boxes/{id}/overview`: caixa, cabos anexados, bandejas, emendas, splitters, portas de
    atendimento, conexões e clientes.
  - `GET /boxes/{id}/cables` e `GET /olts/{id}/cables`: cabos anexados com o papel do elemento.
  - `GET /cables/{id}/fibers`: fibras com tubo, status e a ligação de cada ponta (tipo, id,
    elemento e, em fusão/conector, a fibra do outro lado).
- **RT-11 Importação de rede** (`POST /imports/network`): o front envia uma lista pronta (caixas,
  OLTs, cabos e pontas). O backend não lê arquivo e grava tudo numa transação: qualquer falha
  desfaz a importação inteira. Não apaga nada e não cria fibra, tubo, fusão, splitter, porta ou
  cliente. Limites: até 10.000 itens (`400 "Import exceeds 10000 items"`).
  - Duplicado: `CREATE` de caixa ou OLT cujo tipo e nome normalizado já existem na rede, ou
    repetidos na própria importação → `409 "Import has elements that already exist"`. Cabos não
    entram nesta regra.
  - Nome normalizado: sem acento, minúsculas, `-` e `_` viram espaço, pontas aparadas.
  - `RESHAPE` de cabo com alguma ponta de fibra ligada (porta, splitter, atendimento ou conexão
    óptica, inclusive fusão) → `409 "Cable has connected fibers and cannot be reshaped"`.
  - Pontas `start`/`end` do cabo: `ref` (elemento `CREATE` desta importação), `boxId` ou `oltId`
    (existentes). Cabo `CREATE` sem ponta fica sem ligação física. Não cria `PASS_THROUGH`.
- **RT-12 Rastreio da fibra** (`GET /trace/{kind}/{id}?direction=UPSTREAM|DOWNSTREAM`): só leitura.
  Anda pela cadeia fibra → ligação (porta, conexão óptica ou nada) e para em OLT, cliente ou quebra.
  Cada ponta tem uma única ligação, então só há escolha na entrada de splitter, onde a descida
  devolve o resumo das saídas (`branches`) e não lista os clientes. Limites: cada ponto é visitado
  uma vez (`LOOP`) e o caminho para em 50 passos (`DEPTH_LIMIT`).
  - Partida por caixa: a primeira entrada de splitter ligada a uma fibra; sem ela, a primeira
    conexão da caixa; sem nada, `EMPTY`.
  - Saída de splitter sem cliente, com porta de atendimento vazia, conta como `NO_CUSTOMER` no
    caminho e como `FREE` no resumo (não é quebra de rede).
  - Posição de quebra: o equipamento onde a ponta está ligada; ponta sem ligação usa o elemento de
    início (A) ou de fim (B) do cabo.
- **RT-13 Impacto de manutenção** (`GET /impact/cables/{id}`, `/impact/boxes/{id}`, `/impact/olts/{id}`):
  só leitura. Um cliente depende do item quando o caminho UPSTREAM dele (o mesmo do RT-12) passa por
  ele: cabo pelo trecho de fibra; caixa por qualquer elemento dentro dela (incluindo a porta de
  atendimento do cliente); OLT pela porta PON. Cliente cancelado não conta. Sem rota alternativa.
  Um cliente conta uma vez. `totals` são exatos; a lista de clientes para em 2000 (`truncated`).
- **RT-14 Autenticação e papéis** (`/api/v1/auth/*`, `/api/v1/users/*`): access token JWT (HS256,
  15 min) no header `Authorization: Bearer`. Refresh token opaco de 14 dias em cookie
  `geofiber_refresh` (HttpOnly, Secure, SameSite=Strict, Path=/api/v1/auth); no banco fica só o
  SHA-256, e cada uso rotaciona o token. Reuso de refresh revogado derruba todas as sessões do
  usuário. Login com mensagem única para e-mail inexistente e senha errada. Papéis `ADMIN` e
  `MEMBER`; só `ADMIN` cria, desativa e redefine a senha de usuários. Desativar ou redefinir senha
  revoga as sessões de refresh; o access token atual vale até expirar (no máximo 15 min). Senha com
  pelo menos 8 caracteres. Admin não desativa a própria conta (`409`). Na primeira subida, sem
  usuários, o administrador é criado a partir de `ADMIN_EMAIL`/`ADMIN_PASSWORD`/`ADMIN_NAME`.
  Limite de login (`LoginAttemptLimiter`, em memória, por instância): 5 senhas erradas em 15 min
  para o mesmo e-mail e endereço de origem bloqueiam esse par por 15 min → `429` com `Retry-After`
  (segundos) e `detail` "Muitas tentativas de entrar. Espere 15 minutos e tente de novo.". Login
  certo zera a contagem. Atrás de proxy reverso, `server.forward-headers-strategy=native` para usar o
  IP do cliente.
- **RT-15 Dono por criador**: caixa, cabo, OLT e cliente guardam o criador (`created_by`). Só o
  dono altera ou apaga o elemento (`403 "Só o criador pode alterar este elemento."`); filhos
  (tubos, fibras, bandejas, portas, splitters, anexos, emendas) seguem o dono do pai. Ligações entre
  fibras (conexões e emendas) podem ser criadas ou desfeitas por qualquer dono dos dois cabos.
  Ligação fibra↔porta (OLT, atendimento, saída de splitter) segue o dono da caixa ou da OLT da
  porta. Leitura é aberta a qualquer usuário autenticado. Administrador não ganha permissão sobre
  elementos de outras pessoas. Elementos existentes antes da migração ficam com o primeiro
  administrador. O importador de rede exige dono para mover ou remodelar elementos já existentes.

---

## 5. Limitações conhecidas (para fases seguintes)

- A migração V5 converteu clientes ligados a splitters em portas de atendimento sem checar o
  tipo da caixa nem `powerCapacity`, numerando por ordem de UUID (sem impacto: não havia dados
  em produção).
- O erro do trigger de fonte única cai na mensagem 409 genérica.
- Listagens sem paginação e com consultas por item (N+1) em clientes, conexões e splitters.
- Numeração "maior + 1" e o trigger de fonte única não são protegidos contra requisições
  simultâneas (conflitos de numeração viram 409 pelas constraints).
- Portas de atendimento geradas por splitter não preenchem lacunas na numeração.
- Sem limite de tentativas no login. O access token não é revogado ao desativar um usuário
  (vale até 15 min).

## 6. Referências

- Design da conclusão da Fase 1: [`superpowers/specs/2026-11-19-fase1-conclusao-design.md`](superpowers/specs/2026-11-19-fase1-conclusao-design.md)
- Plano de implementação: [`superpowers/plans/2026-11-19-fase1-conclusao.md`](superpowers/plans/2026-11-19-fase1-conclusao.md)
- Guia da API: [`../API.md`](../API.md)
