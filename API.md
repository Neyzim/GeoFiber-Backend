# GeoFiber Backend — Guia da API

API REST open source para cadastrar e documentar redes FTTH: OLTs, cabos, tubos e fibras,
caixas (CTO/CEO/DIO), bandejas, emendas, conexões, splitters, portas de atendimento e clientes.

A referência completa de cada endpoint (campos, tipos, exemplos e respostas) fica no
**Swagger**. Este guia explica o modelo, as regras e a ordem de cadastro.

| | |
|---|---|
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| Especificação OpenAPI (JSON) | `http://localhost:8080/v3/api-docs` |

O Swagger e o OpenAPI só respondem com `API_DOCS_ENABLED=true` no `.env` (desenvolvimento). Em
produção, deixe a variável sem valor: as duas rotas devolvem `404`.
| Prefixo de todos os endpoints | `/api/v1` |

## Autenticação

Todas as rotas, exceto `/api/v1/auth/login`, `/refresh`, `/logout` e a documentação, exigem o
header `Authorization: Bearer <accessToken>`.

1. `POST /api/v1/auth/login` com `{"email", "password"}` → `200` com `{"accessToken", "expiresIn"}`
   (segundos) e o cookie `geofiber_refresh`. Credenciais erradas → `401`. Depois de 5 erros em
   15 minutos para o mesmo e-mail e origem → `429` com `Retry-After` (segundos) por 15 minutos.
2. Use o `accessToken` no header. Ele vale 15 minutos.
3. `POST /api/v1/auth/refresh` (sem corpo; o navegador envia o cookie) → `200` com um novo
   `accessToken` e um cookie novo. O refresh antigo deixa de valer. Sem cookie válido → `401`.
4. `POST /api/v1/auth/logout` → `204` e o cookie é expirado.

**Para o frontend:** use `credentials: 'include'` nas chamadas (o cookie é `HttpOnly`, o JavaScript
não o lê). A origem do front precisa estar em `CORS_ALLOWED_ORIGINS`. O cookie é `Secure`, então
o navegador só o aceita por HTTPS (ou em `localhost`).

Respostas: `401` (sem token, credenciais inválidas ou sessão expirada) e `403` (usuário sem
permissão, ou que não é o dono do elemento).

**Usuários (só `ADMIN`):**
- `GET /api/v1/users` → `200` com a lista de usuários (ativos e inativos), por nome. Sem senha.
- `POST /api/v1/users` com `{"name", "email", "password", "role"}` (`ADMIN` ou `MEMBER`) → `201`.
- `PATCH /api/v1/users/{id}/active` com `{"active": false}` → desativa e revoga as sessões.
- `PUT /api/v1/users/{id}/password` com `{"password"}` → redefine e revoga as sessões.

**Dono:** cada caixa, cabo, OLT e cliente tem um criador. Só ele altera ou apaga o elemento e
os filhos dele; outros usuários leem normalmente. Ligações entre fibras podem ser mudadas por
qualquer dono dos dois cabos envolvidos.

**Configuração** (variáveis de ambiente ou `.env`):
- `GEOFIBER_JWT_SECRET`: chave de pelo menos 32 bytes. Obrigatória.
- `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `ADMIN_NAME`: usados só na primeira subida, quando não há
  usuário. Sem eles, a aplicação não sobe nessa situação.

## Como rodar

1. Suba o PostgreSQL com PostGIS: `docker compose up -d` (usa as variáveis do `.env`).
2. Configure a conexão por variáveis de ambiente (`DATABASE_URL`, `DATABASE_USERNAME`,
   `DATABASE_PASSWORD`) ou pelo `.env` (`database.link`, `postgres_username`,
   `postgres_password`). Padrão: `jdbc:postgresql://localhost:5432/geofiber`, usuário `postgres`.
3. `./gradlew bootRun`. O Flyway aplica as migrações de `src/main/resources/db/migration` e o
   Hibernate apenas valida o schema (não cria nem apaga tabelas).

Testes: `./gradlew test`. Os testes de integração sobem um PostGIS próprio via Testcontainers,
então o Docker precisa estar rodando, mas o seu banco local não é tocado.

## Modelo da rede

```
OLT ── porta PON ──(ponta A)── fibra ──(ponta B)──┐
                                                  │   dentro de uma caixa (CEO/CTO/DIO):
                                                  ├── emenda (fusão, numa bandeja)
                                                  ├── conexão por conector
                                                  └── porta de entrada de splitter
                                                          │
                                         saídas do splitter ── fibra (cascata para outra caixa)
                                                          │
                                         CTO: porta de atendimento ── cliente
```

- **Cabo**: traçado no mapa (`LineString`), capacidade de fibras e comprimento em metros
  calculado pelo PostGIS. Tem **tubos**, e cada tubo tem **fibras** numeradas no tubo e no cabo.
- **Elementos do cabo** (`/cables/{id}/attachments`): lista ordenada das caixas e OLTs por onde
  o cabo passa (`START`, `PASS_THROUGH`..., `END`). Uma fibra só pode ser ligada num elemento
  dessa lista. A continuidade é sempre declarada e **nunca deduzida pela proximidade no mapa**.
- **Pontas de fibra**: toda fibra tem as pontas **A** (lado da OLT) e **B** (lado do cliente),
  criadas junto com ela. Toda ligação (porta, emenda, conexão) usa uma ponta, e cada ponta só
  pode estar numa ligação por vez.
- **Caixa**: `CTO`, `CEO` ou `DIO`. `trayCapacity` limita as bandejas. Numa CTO,
  `powerCapacity` é o número máximo de **portas de atendimento** (numeradas de 1 até esse valor).
- **Splitter**: porta 1 é a entrada; as portas 2..N+1 são as saídas. Numa CTO, criar um
  splitter gera por padrão uma porta de atendimento por saída (`createServicePorts`).
- **Porta de atendimento** (só em CTO): onde o drop do cliente é conectado. É alimentada por
  **uma** saída de splitter da mesma caixa **ou** por uma ponta de fibra.
- **Cliente**: ligado a uma porta de atendimento livre e com fonte óptica. Tem situação
  `ACTIVE`, `BLOCKED` ou `CANCELLED` e um histórico de mudanças (`GET /customers/{id}/history`).

### Ponta usada por padrão em cada ligação

| Ligação | Ponta padrão | Campo para mudar |
|---|---|---|
| Porta de OLT | A | `fiberEnd` |
| Entrada de splitter | B | `fiberEnd` |
| Saída de splitter | A | `fiberEnd` |
| Porta de atendimento | B | `fiberEnd` |
| Emenda: fibra de entrada / de saída | B / A | `inputFiberEnd` / `outputFiberEnd` |
| Conexão por conector | obrigatório | `fiberAEnd` / `fiberBEnd` |

Mude a ponta quando o cabo tiver sido desenhado no sentido contrário ao do sinal.

### Status

- **Fibra**: `AVAILABLE` e `OCCUPIED` são automáticos, conforme as ligações. `BROKEN` e
  `RESERVED` são definidos em `PUT /tubes/{tubeId}/fibers/{fiberId}/status` (só com a fibra
  desconectada) e impedem novas ligações.
- **Portas** (OLT, splitter, atendimento): `OCCUPIED` é automático. `FREE`, `RESERVED` e `FAULTY`
  podem ser enviados no campo `status`. `FAULTY` vale mesmo com algo ligado: a porta de
  atendimento com defeito mantém o cliente e volta a `OCCUPIED` quando reparada (status `FREE` ou vazio).
- **Cliente** (`status` no `POST`/`PUT /customers`, padrão `ACTIVE`):
  - `BLOCKED`: serviço suspenso; o cliente continua na porta.
  - `CANCELLED`: a porta é liberada (fica `FREE`, ou `FAULTY` se estava com defeito) e o cliente
    continua cadastrado. Para religar, volte a `ACTIVE` informando a porta.
  - Cada mudança de situação ou de porta entra no histórico com a data e a porta do momento.
    Encerre contratos com `CANCELLED`; o `DELETE` é só para cadastro feito por engano e apaga o histórico.

## Exemplo completo: da OLT ao cliente

Coordenadas em WGS 84, com **longitude antes de latitude**. Os ids vêm das respostas anteriores.

1. **OLT e porta PON**
   ```http
   POST /api/v1/olts
   {"name":"OLT-CENTRO","model":"OLT X","location":{"longitude":-46.6333,"latitude":-23.5505}}

   POST /api/v1/olts/{oltId}/ports
   {"slotNumber":1,"portNumber":1}
   ```
2. **Caixas**: uma CEO no caminho e uma CTO com 8 portas de atendimento.
   ```http
   POST /api/v1/boxes
   {"name":"CEO-01","type":"CEO","trayCapacity":2,"location":{"longitude":-46.6320,"latitude":-23.5510}}

   POST /api/v1/boxes
   {"name":"CTO-01","type":"CTO","powerCapacity":8,"location":{"longitude":-46.6300,"latitude":-23.5520}}
   ```
3. **Cabos e seus elementos**: um alimentador da OLT até a CEO e um de distribuição da CEO até a CTO.
   ```http
   POST /api/v1/cables
   {"name":"FEEDER-01","role":"FEEDER","fiberCapacity":12,
    "coordinates":[{"longitude":-46.6333,"latitude":-23.5505},{"longitude":-46.6320,"latitude":-23.5510}]}

   PUT /api/v1/cables/{feederId}/attachments
   [{"role":"START","oltId":"{oltId}"},{"role":"END","boxId":"{ceoId}"}]

   POST /api/v1/cables
   {"name":"DIST-01","role":"DISTRIBUTION","fiberCapacity":12,
    "coordinates":[{"longitude":-46.6320,"latitude":-23.5510},{"longitude":-46.6300,"latitude":-23.5520}]}

   PUT /api/v1/cables/{distId}/attachments
   [{"role":"START","boxId":"{ceoId}"},{"role":"END","boxId":"{ctoId}"}]
   ```
4. **Tubos e fibras**: crie a estrutura do cabo de uma vez. Cada tubo é preenchido com fibras
   até a capacidade, nas cores ABNT (verde, amarelo, branco, azul…), numeradas no cabo em
   sequência. Tudo numa única transação: se algo falhar, nada é criado.
   ```http
   POST /api/v1/cables/{feederId}/structure
   {"tubes":[{"tubeNumber":1,"capacity":12,"color":"GREEN"}]}
   ```
   Faça o mesmo no cabo de distribuição. Os ids das fibras vêm na resposta ou em
   `GET /api/v1/cables/{cableId}/fibers`. Para tubos ou fibras avulsos ainda existem
   `POST /cables/{cableId}/tubes` e `POST /tubes/{tubeId}/fibers`.
5. **Ligar a fibra do alimentador à porta PON** (usa a ponta A).
   ```http
   PUT /api/v1/olts/{oltId}/ports/{portId}
   {"slotNumber":1,"portNumber":1,"fiberId":"{feederFiberId}"}
   ```
6. **Fusão na CEO**: a bandeja precisa existir antes. A posição pode ser fornecida
   (para manter fidelidade com a caixa real) ou será a próxima livre.
   ```http
   POST /api/v1/boxes/{ceoId}/trays
   {"trayNumber":1,"capacity":12}

   POST /api/v1/boxes/{ceoId}/splices
   {"trayNumber":1,"positionNumber":2,"attenuationDb":0.05,"inputFiberId":"{feederFiberId}","outputFiberId":"{distFiberId}"}
   ```
   Se omitir `positionNumber`, o sistema encontra a próxima posição livre.
7. **Splitter na CTO**: gera as portas de atendimento 1..8. Ligue a fibra de distribuição na
   entrada (porta 1 do splitter, ponta B).
   ```http
   POST /api/v1/boxes/{ctoId}/splitters
   {"ratio":"RATIO_1_8","balanced":true}

   PUT /api/v1/splitters/{splitterId}/ports/{inputPortId}
   {"fiberId":"{distFiberId}"}
   ```
8. **Cliente** numa porta de atendimento (veja os ids em `GET /api/v1/boxes/{ctoId}/service-ports`).
   ```http
   POST /api/v1/customers
   {"name":"Maria","contractCode":"CT-0001","address":"Rua A, 10",
    "location":{"longitude":-46.6298,"latitude":-23.5522},"servicePortId":"{servicePortId}"}
   ```

Para CTOs **sem splitter**, crie a porta de atendimento alimentada por uma fibra:
`POST /api/v1/boxes/{ctoId}/service-ports` com `{"portNumber":1,"fiberId":"{fiberId}"}`.
Para ligar fibras por conector (ex.: cordão num DIO), use `POST /api/v1/boxes/{boxId}/connections`.

## Para o frontend

**CORS.** A API aceita chamadas do navegador vindas de `http://localhost:4200` (padrão). Para
outras origens, defina a variável `CORS_ALLOWED_ORIGINS` com as origens separadas por vírgula
(ex.: `CORS_ALLOWED_ORIGINS=http://localhost:4200,https://mapa.exemplo.com`). O header
`Location` das respostas `201` fica visível ao frontend.

**Leituras prontas para tela.**

| Endpoint | Uso |
|---|---|
| `GET /api/v1/boxes/{boxId}/overview` | Diagrama da caixa numa chamada: `box`, `cables`, `trays`, `splices`, `splitters` (com portas), `servicePorts`, `connections`, `customers`. |
| `GET /api/v1/boxes/{boxId}/cables` / `GET /api/v1/olts/{oltId}/cables` | Cabos que chegam ao elemento, com `attachmentRole` (START, PASS_THROUGH, END). |
| `GET /api/v1/cables/{cableId}/fibers` | Todas as fibras do cabo com tubo, status e, em `ends`, a ligação de cada ponta. |

Em `ends[].link`, `type` diz a que a ponta está ligada: `OLT_PORT`, `SPLITTER_PORT`,
`SERVICE_PORT`, `FUSION` ou `CONNECTOR`. `id` é o da porta, da emenda ou da conexão, e
`elementId` é a OLT ou caixa onde a ligação está. Em `FUSION`/`CONNECTOR`, `peerFiberId` e
`peerFiberEnd` apontam para a fibra do outro lado, o que permite seguir o sinal fibra a fibra.
`link` é `null` quando a ponta está livre.

**Coordenadas.** A API usa `{"longitude": …, "latitude": …}`. Leaflet espera `[lat, lng]` e
GeoJSON espera `[lng, lat]`: converta com cuidado (pontos no oceano costumam ser ordem trocada).

**Tipos TypeScript.** Gere os modelos a partir da especificação em vez de escrevê-los à mão, por
exemplo: `npx openapi-typescript http://localhost:8080/v3/api-docs -o src/api/schema.d.ts`.

## Importação de rede (KML/KMZ)

`POST /api/v1/imports/network` recebe uma lista pronta, já lida e agrupada pelo front. O backend
não lê arquivo. Responde `201` e grava tudo numa única transação: se qualquer item falhar, nada é
gravado.

- `usageStatus` é obrigatório e vale para os itens criados.
- `elements`: `CREATE` (caixa ou OLT, com `name`, `kind` e, em caixa, `type` CTO/CEO/DIO) ou
  `MOVE` (troca só a localização de um `id` existente).
- `cables`: `CREATE` (com `name`, `role`, `fiberCapacity` e `coordinates`, no mínimo 2 pontos) ou
  `RESHAPE` (troca só o traçado de um `id` existente).
- `ref` é uma chave livre do front, única na requisição. Pontas `start`/`end` do cabo usam `ref`,
  `boxId` ou `oltId` (uma só chave; ausente é permitido).

Resposta:

```json
{
  "created": { "boxes": 180, "olts": 1, "cables": 340, "attachments": 287 },
  "moved": 2,
  "reshaped": 0,
  "ids": { "p-12": "uuid-novo", "l-3": "uuid-novo", "p-77": "uuid-existente" }
}
```

Erros: `400 "Invalid import"` traz `errors` com `ref`, `field` e `message` de cada item inválido;
`400 "Import exceeds 10000 items"`; `404` para `MOVE`/`RESHAPE` de id inexistente; `409
"Import has elements that already exist"` e `409 "Cable has connected fibers and cannot be
reshaped"`.

## Rastreio da fibra

`GET /api/v1/trace/{kind}/{id}?direction=UPSTREAM|DOWNSTREAM` devolve o caminho do sinal a partir
de um ponto da rede, em ordem: até a OLT (`UPSTREAM`) ou até os clientes (`DOWNSTREAM`). Só leitura.

- `kind`: `FIBER_END` (id da ponta), `FIBER`, `BOX`, `SPLITTER`, `SPLICE` (id da emenda) ou
  `SPLITTER_PORT`.
- `status`: `REACHED_OLT`, `REACHED_CUSTOMERS`, `BROKEN` (parou numa quebra) ou `EMPTY` (nada ligado).
- `steps`: `CABLE` (com `fiber` e `coordinates` do traçado), `BOX` (com `position` em fusão),
  `SPLITTER`, `OLT_PORT` e `SERVICE_PORT`.
- `breaks`: `reason` é `NO_CONNECTION`, `NO_SIGNAL`, `NO_CUSTOMER`, `LOOP` ou `DEPTH_LIMIT`. `at`
  traz a posição do item quebrado; quando o item não tem posição própria, usa a da caixa.
- `branches` (em splitter descendo): uma entrada por saída, na numeração 1..N. `traceRef` é o id da
  porta do splitter; chame o rastreio com `kind=SPLITTER_PORT` para ver os clientes da saída.
- `customers`: clientes alcançados pelo caminho (quando a descida chega a uma porta de atendimento).

Erros: `404` para item inicial inexistente; `400` para `kind` ou `direction` inválidos.

## Impacto de manutenção

Para um cabo, uma caixa ou uma OLT, lista os clientes que dependem dele e as caixas e OLTs no caminho
deles. Só leitura, sem parâmetros.

- `GET /api/v1/impact/cables/{id}`
- `GET /api/v1/impact/boxes/{id}`
- `GET /api/v1/impact/olts/{id}`

Resposta `200`:

- `target`: `kind` (`CABLE`, `BOX` ou `OLT`), `id`, `label` e `coordinates` (traçado para cabo, um ponto
  para caixa e OLT).
- `totals`: `customers`, `boxes` e `olts` exatos, mesmo quando a lista foi cortada. Caixas e OLTs
  excluem o próprio alvo.
- `boxes`: caixas do caminho dos clientes, por nome. Cada uma traz os seus clientes afetados.
- `olts`: OLTs do caminho, exceto a própria.
- `truncated`: `true` quando há mais de 2000 clientes afetados; a lista mostra os 2000 primeiros por nome.

Clientes cancelados não contam. Um cliente conta uma vez, mesmo que o caminho passe por várias caixas.

Erros: `404` para id inexistente; `400` para id que não é UUID.

## Erros

Todas as respostas de erro seguem o formato ProblemDetail (RFC 9457), com
`Content-Type: application/problem+json`.

```json
{
  "title": "Validation failed",
  "status": 400,
  "detail": "Invalid request content.",
  "instance": "/api/v1/boxes",
  "errors": [{ "field": "name", "message": "must not be blank" }]
}
```

As mensagens de validação seguem o idioma da requisição (`Accept-Language`); o `detail` dos
demais erros é sempre em inglês, para facilitar o tratamento no cliente.

| Status | Quando |
|---|---|
| `400` | Payload inválido (`errors` lista os campos), JSON malformado, enum desconhecido ou regra de entrada violada (ex.: splitter maior que a capacidade da CTO). |
| `404` | Id inexistente ou que não pertence ao recurso pai da URL. |
| `409` | Conflito com dados existentes; o `detail` diz qual. |

Principais `409`:

| `detail` | Causa |
|---|---|
| `Cable is not attached to this box` / `...this OLT` | A fibra é de um cabo que não tem o elemento na sua lista. |
| `Fiber end X is not available` | A ponta já está ligada a outra porta, emenda ou conexão. |
| `Fiber is BROKEN and cannot be connected` | Fibra marcada como rompida ou reservada. |
| `Cable still has fibers connected at a detached element` | Tentativa de tirar da lista um elemento onde o cabo ainda tem ligações. |
| `Splitter port already feeds a service port` | Saída de splitter já alimenta uma porta de atendimento. |
| `Service port has no optical source` / `Service port is not available` | Cliente em porta sem fonte, ocupada ou reservada. |
| `A cancelled customer cannot use a service port` | Cliente cancelado não pode ser ligado a uma porta. |
| `Tray N is full` | Bandeja sem posição livre. |
| `Position N is already occupied in this tray` | Posição fornecida já está ocupada. |
| `Position N exceeds tray capacity of M` | Posição fornecida fica fora do range 1..M. |
| `... still has ...` | Exclusão bloqueada por dependências (tubos, fibras, fusões, portas, cabos anexados). |
| `A ... with this number already exists ...` | Número duplicado de tubo, fibra, porta ou bandeja. |

## Decisões de modelagem

- Nome, descrição, UUID e status de uso comuns ficam em `NetworkAssetEntity` (superclasse sem tabela).
- A ligação de uma fibra com qualquer porta é guardada na ponta da fibra (`fiber_termination`),
  que é a única fonte da verdade. Assim a mesma ligação nunca fica registrada em dois lugares.
- Fusões e conexões por conector são `optical_connection` (tipo `FUSION` ou `CONNECTOR`) dentro
  de uma caixa. A emenda acrescenta a bandeja e a posição.
- Status e enums são gravados como texto, para os dados ficarem legíveis no PostgreSQL.
- O schema evolui só por novas migrações Flyway. Migrações já aplicadas nunca são editadas.
