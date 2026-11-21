# GeoFiber Backend — Guia da API

API REST open source para cadastrar e documentar redes FTTH: OLTs, cabos, tubos e fibras,
caixas (CTO/CEO/DIO), bandejas, emendas, conexões, splitters, portas de atendimento e clientes.

A referência completa de cada endpoint (campos, tipos, exemplos e respostas) fica no
**Swagger**. Este guia explica o modelo, as regras e a ordem de cadastro.

| | |
|---|---|
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| Especificação OpenAPI (JSON) | `http://localhost:8080/v3/api-docs` |
| Prefixo de todos os endpoints | `/api/v1` |

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
4. **Tubos e fibras** (repita para as fibras que quiser documentar).
   ```http
   POST /api/v1/cables/{feederId}/tubes
   {"tubeNumber":1,"capacity":12,"color":"GREEN"}

   POST /api/v1/tubes/{feederTubeId}/fibers
   {"numberInTube":1,"absoluteNumber":1,"color":"GREEN"}
   ```
   Faça o mesmo no cabo de distribuição.
5. **Ligar a fibra do alimentador à porta PON** (usa a ponta A).
   ```http
   PUT /api/v1/olts/{oltId}/ports/{portId}
   {"slotNumber":1,"portNumber":1,"fiberId":"{feederFiberId}"}
   ```
6. **Fusão na CEO**: a bandeja precisa existir antes.
   ```http
   POST /api/v1/boxes/{ceoId}/trays
   {"trayNumber":1,"capacity":12}

   POST /api/v1/boxes/{ceoId}/splices
   {"trayNumber":1,"attenuationDb":0.05,"inputFiberId":"{feederFiberId}","outputFiberId":"{distFiberId}"}
   ```
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
