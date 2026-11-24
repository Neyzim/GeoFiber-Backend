# Publicar de graça — Supabase + Render + Netlify

Para **testar e mostrar** o GeoFiber sem pagar servidor. Para uso real pelos técnicos (sempre ligado,
banco fechado para a internet), use o `docs/PUBLICAR.md`. Design: `docs/superpowers/specs/2026-11-24-deploy-gratuito-design.md`.

## 1. Como fica no ar

```
navegador ──https──▶ Netlify ──┬─▶ arquivos do front (dist/geofiber/browser)
                               └─▶ /api/*  →  Render (backend, Docker)  →  Supabase (PostgreSQL + PostGIS)
```

- O navegador só enxerga o endereço da Netlify. O arquivo `public/_redirects` do front encaminha
  `/api/*` para o Render, então o cookie da sessão (`Secure`, `SameSite=Strict`) funciona como no
  `PUBLICAR.md`, sem CORS.
- Supabase em **East US (North Virginia)** e Render em **Virginia**, lado a lado. Cada requisição faz
  várias consultas ao banco: a distância backend ↔ banco pesa mais que a distância navegador ↔ backend
  (o Render gratuito não tem região no Brasil).

## 2. Supabase (banco)

1. Crie um projeto em <https://supabase.com>, região **East US (North Virginia)**. Guarde a senha do
   banco (forte, gerada pelo próprio painel).
2. No projeto, botão **Connect** → aba **Session pooler**. A string mostrada é parecida com:

   ```
   postgresql://postgres.abcdefghijkl:[YOUR-PASSWORD]@aws-0-us-east-1.pooler.supabase.com:5432/postgres
   ```

   Dela saem as variáveis do Render:

   | Variável | Valor |
   |---|---|
   | `DATABASE_URL` | `jdbc:postgresql://aws-0-us-east-1.pooler.supabase.com:5432/postgres?sslmode=require` (host da sua string) |
   | `DATABASE_USERNAME` | `postgres.abcdefghijkl` (com o ponto e a referência do projeto) |
   | `DATABASE_PASSWORD` | a senha do banco |

> **Não use** *Direct connection* (`db.<ref>.supabase.co`, só IPv6: o Render não alcança) nem
> *Transaction pooler* (porta **6543**: quebra o Flyway e os prepared statements). Só **Session pooler,
> porta 5432**.

Não é preciso criar tabelas nem ativar o PostGIS: na primeira subida o Flyway roda V1..Vn, e a V1 cria a
extensão.

## 3. Render (backend)

1. Suba o código para o GitHub. O `render.yaml` publica a branch **`master`**: o deploy acontece quando
   ela recebe o código (merge da branch de trabalho).
2. Em <https://render.com>: **New → Blueprint**, escolha o repositório. O Render lê o `render.yaml` e
   cria o serviço `geofiber-backend` (Docker, plano free, Virginia).
3. Preencha as variáveis marcadas para o painel:

   | Variável | Valor |
   |---|---|
   | `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | da seção 2 |
   | `GEOFIBER_JWT_SECRET` | `openssl rand -base64 48` — nunca a do desenvolvimento |
   | `CORS_ALLOWED_ORIGINS` | endereço da Netlify, ex.: `https://geofiber.netlify.app` |
   | `ADMIN_EMAIL` / `ADMIN_PASSWORD` | administrador criado na primeira subida |

   Já vêm do `render.yaml`: `DB_POOL_SIZE=5` (o pooler do Supabase limita conexões),
   `SERVER_FORWARD_HEADERS_STRATEGY=native`, `ADMIN_NAME`. **Não** crie `API_DOCS_ENABLED`.
4. Acompanhe os **Logs** da primeira subida: migrações do Flyway, administrador criado e
   `Started GeofiberBackendApplication`.
5. Depois de entrar uma vez, **apague `ADMIN_PASSWORD`** das variáveis.

Se o nome `geofiber-backend` estiver ocupado, o Render dá outro endereço `*.onrender.com`: troque-o no
`public/_redirects` do front.

## 4. Netlify (front)

1. Em <https://netlify.com>: **Add new site → Import an existing project**, repositório do front.
2. Build command `npm run build`; publish directory `dist/geofiber/browser`.
3. O `public/_redirects` vai junto no build. Confira nele o endereço do Render.
4. Copie o endereço do site para `CORS_ALLOWED_ORIGINS` no Render.

## 5. Conferência

- [ ] Acordar o backend (seção 6) e entrar pelo endereço da Netlify.
- [ ] Recarregar a página e continuar conectado (o cookie passou pelo proxy).
- [ ] Abrir direto uma rota do app (ex.: `/caixas/...`) e recarregar: o app carrega, não dá 404.
- [ ] `https://geofiber-backend.onrender.com/v3/api-docs` e `/swagger-ui.html` respondem `404`.

## 6. Limitações do plano gratuito

- **Cold start.** O Render desliga o backend após 15 min sem requisições; a volta leva ~1 min. O proxy
  da Netlify desiste antes disso (~26 s), então a primeira chamada pelo site pode falhar com erro 502/504.
  Antes de usar, **acorde o backend** abrindo `https://geofiber-backend.onrender.com/api/v1/boxes` no
  navegador e espere qualquer resposta (`401` é o esperado); depois use o site normalmente.
- **Supabase pausa** o projeto após 7 dias sem uso. O backend passa a falhar ao subir (erro de conexão
  nos logs do Render). Reative no painel do Supabase (**Restore project**) e, no Render,
  **Manual Deploy → Restart service**.
- Banco até **500 MB**; o Render dá 512 MB de memória (a imagem limita o heap da JVM a 60%).
- **Sem backup baixável** no plano gratuito. Faça à mão, com um `pg_dump` da mesma versão maior do
  Postgres do projeto (veja em *Settings → Infrastructure*), usando a string do Session pooler:

  ```bash
  docker run --rm -v "$PWD:/out" postgres:17 pg_dump -Fc -f /out/geofiber-AAAA-MM-DD.dump \
    "postgresql://postgres.<ref>:<senha>@aws-0-us-east-1.pooler.supabase.com:5432/postgres?sslmode=require"
  ```

- **Limite de login** (5 erros em 15 min por e-mail + origem): atrás de dois proxies (Netlify → Render),
  a origem vista pode ser a da Netlify. Para testes, a contagem por e-mail basta.
- O endereço `*.onrender.com` responde direto da internet, sem passar pela Netlify. Swagger fica
  desligado e toda a API exige login, mas não há o filtro do nginx do `PUBLICAR.md`.
- O banco do Supabase aceita conexões da internet (protegido por senha e SSL), ao contrário do
  `PUBLICAR.md`. Use senha forte e não a reaproveite.
