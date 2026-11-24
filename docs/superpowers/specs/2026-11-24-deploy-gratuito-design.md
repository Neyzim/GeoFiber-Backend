# Publicação gratuita (Supabase + Render + Netlify) — design curto

Data: 2026-11-24 · Tamanho: mudança pequena (dois repositórios: `geofiber-backend` e `GeoFiber`)

## Intenção

Colocar o GeoFiber no ar **sem pagar servidor**, para o próprio autor testar e mostrar. Não é a
publicação de produção para técnicos; essa continua sendo a do `docs/PUBLICAR.md` (VM + nginx).

## Objetivos, não-objetivos, anti-objetivos

- **Objetivos:** banco no Supabase, backend no Render e front na Netlify, tudo no plano gratuito; login
  funcionando (cookie de sessão) e passo a passo documentado.
- **Não-objetivos:** domínio próprio, sempre ligado (sem cold start), backup automático, várias instâncias,
  usuário de banco separado do `postgres` do Supabase.
- **Anti-objetivos:** quebrar o desenvolvimento local (`.env` + docker compose) ou o caminho do
  `PUBLICAR.md`; colocar senha ou segredo no repositório; ligar Swagger em produção.

## Restrições conhecidas

- O Render gratuito desliga após 15 min sem uso (primeira requisição ~30–60 s), tem 512 MB de RAM e
  injeta a porta em `PORT`. Não tem Java nativo: precisa de `Dockerfile`.
- Supabase gratuito: conexão direta só IPv6 → usar o **pooler em modo sessão** (porta 5432, usuário
  `postgres.<ref-do-projeto>`, `sslmode=require`). Modo transação (6543) fica de fora (Flyway e prepared
  statements). O pooler limita conexões → pool do Hikari pequeno. Projeto pausa após 7 dias sem uso;
  banco até 500 MB.
- O cookie de refresh é `Secure`, `SameSite=Strict`, `Path=/api/v1/auth`: exige mesmo domínio e https.

## Abordagens consideradas

- **A) Supabase + Render + Netlify (escolhida).** Grátis de verdade, quase sem mudança de código. Custa
  cold start e pausas do plano gratuito — aceitável para quem só está testando.
- **B) VM Always Free da Oracle Cloud.** É o `PUBLICAR.md` como está, sempre ligado; exige cartão e cuidar
  do servidor. Fica para quando houver uso real.
- Descartadas: Fly.io e Railway (só crédito de teste), Koyeb (equivalente ao Render, menos documentado).

## Design

**Backend (`geofiber-backend`)**
- `Dockerfile` multi-stage: build com JDK 17 (`./gradlew bootJar -x test`), roda com JRE 17;
  `-XX:MaxRAMPercentage=60 -XX:+UseSerialGC` para caber em 512 MB. Mais um `.dockerignore`.
- `application.properties`: `server.port=${PORT:8080}` e
  `spring.datasource.hikari.maximum-pool-size=${DB_POOL_SIZE:10}` (no Render: 5). Local não muda.
- `render.yaml` (blueprint): serviço web Docker, plano free, variáveis declaradas sem valor
  (`sync: false`) para serem preenchidas no painel.
- Variáveis no Render: `DATABASE_URL` (pooler, `?sslmode=require`), `DATABASE_USERNAME`,
  `DATABASE_PASSWORD`, `GEOFIBER_JWT_SECRET`, `CORS_ALLOWED_ORIGINS` (endereço da Netlify),
  `ADMIN_EMAIL/PASSWORD/NAME` só na primeira subida, `SERVER_FORWARD_HEADERS_STRATEGY=native`,
  `DB_POOL_SIZE=5`. `API_DOCS_ENABLED` não definido.
- PostGIS: a V1 já faz `CREATE EXTENSION IF NOT EXISTS postgis` e o usuário `postgres` do Supabase pode
  criá-la. O Flyway aplica V1..V8 na primeira subida.

**Front (`GeoFiber`)**
- `public/_redirects` da Netlify: `/api/*` → `https://<servico>.onrender.com/api/:splat` com status 200
  (proxy, mesmo domínio para o navegador) e `/*` → `/index.html` 200 (rotas do Angular).
- Build `npm run build`, publicar `dist/geofiber/browser`. O build de produção já chama `/api/v1`.

**Documentação**
- Novo `docs/PUBLICAR-GRATUITO.md` no backend: criar contas e projetos (Supabase em São Paulo), onde
  achar a string do pooler, variáveis, primeira subida, conferência (login + recarregar, Swagger 404),
  limitações do plano gratuito e backup manual com `pg_dump`. Link para ele no `PUBLICAR.md`.

## Deixado para quem implementa

- Health check do Render: usar um endpoint existente que responda sem login, se houver; senão, nenhum.
- Limite de login atrás de dois proxies (Netlify → Render): o IP visto pode ser o da Netlify; aceitável
  para testes, registrar como limitação no documento.
- Criar as contas e conectar o GitHub ao Render/Netlify é com o autor; o código só sobe quando ele fizer
  o commit e o push (regra do projeto).
