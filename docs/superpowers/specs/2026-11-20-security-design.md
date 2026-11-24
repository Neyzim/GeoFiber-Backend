# Segurança básica (autenticação e autorização por criador)

Status: implementado em 2026-11-21 (plano em `docs/superpowers/plans/2026-11-20-security.md`). Ver `SPECS.md` RT-14 e RT-15.

## Intenção

Cada equipe roda a própria cópia do GeoFiber (`docker-compose` próprio). Dentro dessa instância, é
preciso saber quem está logado e impedir que um usuário altere elementos criados por outro.

## Objetivos

- Login com e-mail e senha; a API exige token em todas as rotas de negócio.
- Cada elemento de rede e cliente guarda quem o criou (`created_by`).
- Só o criador altera ou apaga o próprio elemento. Qualquer usuário autenticado da instância lê tudo.
- Erros de permissão retornam `403` em ProblemDetail, no mesmo formato dos outros erros.

## Não objetivos

- Multi-tenant, organizações ou compartilhamento entre equipes.
- Papéis além de "criador" (sem perfil administrador nesta versão).
- SSO, recuperação de senha por e-mail, MFA.

## Anti-objetivos

- Nenhuma senha padrão ou segredo no repositório. Segredos vêm de variáveis de ambiente.

## Abordagens consideradas

1. **JWT (escolhida).** Token stateless enviado no cabeçalho `Authorization`. Combina com o front
   Angular (`localhost:4200`) e não exige estado no servidor.
2. **Sessão com cookie.** Mais simples no servidor, mas exige cuidado com CSRF e com o domínio do front.
3. **Provedor externo (Keycloak).** Robusto, mas pesado demais para um `docker-compose` de equipe.

## Desenho

- **Dependência:** `spring-boot-starter-security` e uma biblioteca JWT. Senhas com BCrypt.
- **Usuários:** tabela `app_user` (id, nome, e-mail único, hash da senha, papel `ADMIN` ou `MEMBER`,
  ativo, criado em). Migration V8. Só `ADMIN` cria, desativa e redefine senha de usuários
  (`POST /api/v1/users`, e rotas irmãs). Desativar um usuário revoga os refresh tokens dele.
- **Refresh token:** tabela `refresh_token` (id, user_id, hash do token, expira em, revogado em).
  O token é uma string aleatória longa; só o hash fica no banco. Cada uso gera um novo token e
  revoga o anterior (rotação). Logout revoga o token atual.
- **Criador:** coluna `created_by` (UUID, FK para `app_user`) em caixas, cabos, OLTs e clientes.
  Elementos filhos (fibras, tubos, splitters, conexões, bandejas) não recebem dono próprio: a
  permissão é decidida pelo elemento pai que eles pertencem.
- **Regra de escrita:** antes de alterar ou apagar, o service compara `created_by` com o usuário
  logado. Divergindo, retorna `403`.
- **Dados existentes:** linhas antigas ficam sem criador. A migration atribui essas linhas a um
  usuário inicial, criado na primeira subida a partir de variáveis de ambiente
  (`ADMIN_EMAIL`, `ADMIN_PASSWORD`).
- **Endpoints públicos:** `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh` e a documentação
  (Swagger). `POST /api/v1/auth/logout` revoga o refresh token. O resto exige token.
- **Demo de portfólio (depois):** uma flag `app.read-only=true` bloqueia escrita com `403`. Usa a
  mesma camada de permissão, então não cria regra nova.

## Decisões minhas (podem ser revistas)

- Access token JWT com validade de 15 minutos. Refresh token com validade de 14 dias, rotacionado
  a cada uso e guardado só como hash.
- Os elementos filhos seguem o dono do pai, sem coluna própria.
- Migrations antigas não são editadas; tudo entra em V8 e posteriores.

## Decisões do responsável

1. **Ligação entre donos diferentes:** qualquer um dos dois donos pode alterar ou desfazer a
   ligação. Quem cria ou altera uma ligação precisa ser dono de pelo menos um dos dois elementos.
   - Nota: se a intenção era exigir que a pessoa seja dona dos dois, a regra muda. Confirmar antes
     do plano.
2. **Quem cria usuários:** só o administrador. Por isso o papel `ADMIN` entrou no desenho.
   - Minha decisão: o administrador gerencia usuários, mas não ganha permissão de editar ou
     apagar elementos de outras pessoas. Se quiser que ele possa, é uma linha no desenho.

3. **Onde o refresh token fica no front:** cookie `httpOnly`, com `Secure`, `SameSite=Strict` e
   `Path=/api/v1/auth`. O token de acesso fica só em memória no front, nunca no `localStorage`.
   - Em desenvolvimento, o proxy do Angular encaminha `/api` para `8080`, então front e API ficam
     na mesma origem.
   - Em produção, um proxy reverso no mesmo `docker-compose` serve front e API no mesmo domínio.

## Pendente

Nada. Desenho aprovado; o próximo passo é o plano de implementação.
