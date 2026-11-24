# Segurança — especificação para o frontend

Este documento diz o que o front precisa implementar para usar a autenticação, os papéis e a regra
de dono do backend. Os contratos completos dos demais endpoints estão em `API.md`. As regras de
negócio estão em `docs/SPECS.md` (RT-14 e RT-15).

## 1. Resumo

- Login com e-mail e senha. O backend devolve um **access token** (JWT, 15 min) no corpo e grava o
  **refresh token** (14 dias) num cookie `httpOnly`.
- Toda chamada à API, exceto as de `/api/v1/auth/*`, leva `Authorization: Bearer <accessToken>`.
- Quando o access token expira, o front pede um novo com `POST /api/v1/auth/refresh`. O cookie vai
  sozinho, e o front não lê nem guarda o refresh token.
- Dois papéis: `ADMIN` (gerencia usuários) e `MEMBER` (opera a rede).
- Cada caixa, cabo, OLT e cliente tem um **dono** (quem criou). Só o dono altera ou apaga. Todos
  leem.

## 2. Configuração do cliente HTTP

Todas as chamadas à API precisam enviar cookies entre origens:

```ts
fetch(url, { credentials: 'include', headers: { Authorization: `Bearer ${accessToken}` } });
```

Com axios: `withCredentials: true`. Sem isso o navegador não envia nem recebe o cookie de refresh.

- A origem do front precisa estar em `CORS_ALLOWED_ORIGINS` no backend. O padrão é
  `http://localhost:4200`.
- O cookie é `Secure`: o navegador só o aceita por HTTPS, ou em `http://localhost`.
- Os métodos liberados pelo CORS são `GET, POST, PUT, PATCH, DELETE, OPTIONS`. `PATCH` foi
  liberado nesta versão; antes disso a ativação de usuário era bloqueada no navegador.

## 3. Onde guardar o access token

- **Em memória** (variável do app ou estado do store). Não usar `localStorage` nem `sessionStorage`:
  um script injetado leria o token.
- Ao recarregar a página, a memória some. Nesse caso o front chama `/refresh` no início (ver §4.1).
- O refresh token nunca é lido pelo JavaScript. Ele só existe no cookie.

## 4. Fluxo de sessão

### 4.1 Inicialização da aplicação

1. Chamar `POST /api/v1/auth/refresh` (sem corpo).
2. Se `200`: guardar `accessToken` em memória e agendar a renovação (§4.3). Entrar na aplicação.
3. Se `401`: mostrar a tela de login.

### 4.2 Login

1. `POST /api/v1/auth/login` com `{ "email", "password" }`.
2. `200`: guardar `accessToken`, agendar a renovação, ir para a tela inicial. O cookie já foi gravado
   pelo navegador.
3. `401`: mostrar `Credenciais inválidas ou sessão expirada.` (o texto vem do backend).
4. `400`: mostrar os erros de campo (§6).

### 4.3 Renovação proativa

Renovar `expiresIn - 60` segundos depois de cada login ou refresh. O `expiresIn` vem em segundos
(900 na configuração atual). Cada refresh devolve um access token novo e um cookie novo.

### 4.4 Renovação reativa (401 numa chamada à API)

Quando uma chamada a `/api/v1/**` (fora de `/auth/*`) responde `401`:

1. Se já existe um refresh em andamento, aguardar esse mesmo refresh. Não disparar outro.
2. Senão, chamar `POST /api/v1/auth/refresh`.
3. Se o refresh der `200`, repetir a chamada original uma vez, com o novo token.
4. Se o refresh der `401`, limpar a sessão em memória e ir para o login. Mostrar a mensagem
   `Sua sessão expirou. Entre novamente.`

Não repetir a chamada mais de uma vez: se der `401` de novo, tratar como sessão encerrada.

**Por que o único refresh em andamento importa:** o backend rotaciona o refresh a cada uso. Dois
refreshes simultâneos com o mesmo cookie fazem o segundo ser tratado como reuso, e o backend então
encerra todas as sessões do usuário.

### 4.5 Logout

1. `POST /api/v1/auth/logout`. Responde `204` mesmo sem sessão.
2. Limpar o access token da memória e ir para o login. Não é preciso mexer no cookie: o backend
   já o expirou.

### 4.6 Sessão encerrada pelo administrador

Se um administrador desativar a conta ou trocar a senha:

- O próximo refresh falha com `401`, e o front volta ao login (§4.4).
- O access token atual continua aceito por até 15 minutos. Isso é esperado; não tentar contornar
  na UI.
- Um login com conta desativada também dá `401`, com a mesma mensagem de credenciais inválidas.

## 5. Contratos dos endpoints de autenticação e usuários

Todas as respostas de erro são `application/problem+json` (RFC 9457).

| Método | Caminho | Quem | Corpo de entrada | Sucesso | Erros |
|---|---|---|---|---|---|
| POST | `/api/v1/auth/login` | público | `{ "email": string, "password": string }` | `200` `{ "accessToken", "expiresIn" }` + `Set-Cookie: geofiber_refresh` | `400` validação, `401` credenciais |
| POST | `/api/v1/auth/refresh` | público (usa o cookie) | nenhum | `200` igual ao login, com cookie novo | `401` |
| POST | `/api/v1/auth/logout` | público | nenhum | `204` (cookie expirado) | — |
| GET | `/api/v1/users` | ADMIN | nenhum | `200` lista de `UserResponse`, por nome, incluindo inativos | `401`, `403` |
| POST | `/api/v1/users` | ADMIN | `{ "name", "email", "password", "role": "ADMIN"\|"MEMBER" }` | `201` `UserResponse` | `400` validação ou senha curta, `401`, `403`, `409` e-mail existente |
| PATCH | `/api/v1/users/{id}/active` | ADMIN | `{ "active": boolean }` | `200` `UserResponse` | `401`, `403`, `404`, `409` próprio usuário |
| PUT | `/api/v1/users/{id}/password` | ADMIN | `{ "password": string }` | `204` | `400` senha curta, `401`, `403`, `404` |

`UserResponse`: `{ "id": uuid, "name": string, "email": string, "role": "ADMIN"|"MEMBER", "active": boolean }`.
Nunca vem senha nem hash.

Efeitos colaterais que o front deve refletir na UI:

- Desativar um usuário encerra as sessões dele. Se o administrador estiver desativando a si mesmo,
  a operação nem acontece (`409`).
- Redefinir a senha também encerra as sessões do usuário alvo.

## 6. Erros e como exibir

### 6.1 Formato

```json
{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 400,
  "detail": "Invalid request content.",
  "errors": [{ "field": "email", "message": "must be a well-formed email address" }]
}
```

(A mensagem de `errors` é um exemplo: o texto vem do Bean Validation.) `errors` aparece só em `400` de validação. Mostrar a mensagem de cada campo junto ao input. Quando a
mensagem vier em inglês do Bean Validation, o front pode trocar por um texto próprio, usando `field`.

### 6.2 Tabela de erros de autenticação e autorização

| Status | Quando | `detail` exato | Ação do front |
|---|---|---|---|
| 401 | sem token, token inválido, token expirado | `Autenticação necessária.` | renovação reativa (§4.4) |
| 401 | login com credenciais erradas, conta desativada, refresh inválido | `Credenciais inválidas ou sessão expirada.` | mostrar no login; no refresh, ir ao login |
| 403 | perfil `MEMBER` chamando rota de ADMIN | `Acesso negado.` | esconder a tela; se chegar aqui, mostrar aviso |
| 403 | tentar alterar elemento de outro dono | `Só o criador pode alterar este elemento.` | mostrar aviso (§7) |
| 403 | ligar ou desligar fibras sem ser dono de nenhum dos dois cabos | `Só o dono de um dos elementos pode alterar esta ligação.` | mostrar aviso |
| 409 | ADMIN tentando desativar a própria conta | `Você não pode desativar a própria conta.` | aviso |
| 409 | e-mail já cadastrado | `E-mail já cadastrado.` | erro no campo e-mail |
| 400 | senha com menos de 8 caracteres | `A senha precisa ter pelo menos 8 caracteres.` | erro no campo senha |
| 404 | usuário inexistente | `Usuário não encontrado.` | aviso |

Os textos de `detail` vêm do backend em português. O front pode usá-los diretamente ou trocar por
mensagens próprias, desde que o `status` continue sendo a base da decisão.

## 7. Papéis e dono na interface

### 7.1 Matriz

| Ação | MEMBER | ADMIN |
|---|---|---|
| Ver redes, caixas, cabos, clientes e relatórios | sim | sim |
| Criar caixa, cabo, OLT, cliente, ligação | sim | sim |
| Alterar ou apagar um elemento **que criou** | sim | sim |
| Alterar ou apagar um elemento **de outro dono** | não | não |
| Gerenciar usuários (criar, ativar/desativar, senha) | não | sim |

Administrador **não** tem poder extra sobre elementos de outras pessoas. O papel ADMIN só serve
para usuários.

### 7.2 Como o front deve tratar o dono hoje

As respostas de caixa, cabo e OLT **não trazem o criador** (ainda — ver §10). Portanto o front
não consegue saber de quem é cada elemento. Consequências:

- Mostrar os botões de editar e excluir para todos os elementos.
- Ao receber `403` com `Só o criador pode alterar este elemento.`, mostrar um aviso claro, sem
  apagar o que o usuário digitou no formulário.
- Não usar a ausência de botão como única proteção. A regra é do backend.

Para uma tela de usuário, o front pode extrair `sub` (id) e `role` do access token para exibir ou
esconder áreas de ADMIN. Essa leitura é só para a interface: nunca decidir acesso no cliente.

### 7.3 Telas de administração

- Lista de usuários: `GET /api/v1/users` (ver a tabela da §5). Mostrar nome, e-mail, papel e
  situação (ativo/inativo). Não há paginação: a lista é a equipe inteira.
- Cadastro de usuário: formulário com nome, e-mail, senha (mínimo 8 caracteres) e papel.
- Ativar ou desativar: confirmação antes. Não mostrar a opção para a própria conta.
- Redefinir senha: confirmação antes, informando que as sessões do usuário serão encerradas.

## 8. Sessão no navegador: casos de borda

- **Duas abas abertas:** cada aba tem a própria cópia do access token. Ao renovar numa aba, a outra
  continua com o token antigo até a próxima chamada dar `401`. O refresh é compartilhado pelo cookie,
  então ela renova sem pedir senha. Se uma aba fizer logout, a outra só descobre no próximo `401`.
- **Rede caiu durante o refresh:** não tratar como sessão encerrada. Só `401` do refresh encerra a
  sessão. Erro de rede deve mostrar uma mensagem de conexão e tentar de novo.
- **Página recarregada no meio de um formulário:** o rascunho se perde, a não ser que o front
  guarde o rascunho à parte. Não é segurança, é UX.
- **Cookie não chega (ex.: front em outro domínio sem `credentials`):** o refresh dá `401` mesmo
  com login recente. Conferir `credentials: 'include'` e a origem no CORS.

## 9. Checklist de teste do front

- [ ] Login correto leva à tela inicial e grava o access token só em memória.
- [ ] Login com senha errada mostra a mensagem sem revelar se o e-mail existe.
- [ ] Recarregar a página mantém a sessão (via refresh).
- [ ] `401` numa chamada dispara um único refresh, mesmo com várias chamadas simultâneas.
- [ ] Refresh que falha leva ao login com a mensagem de sessão expirada.
- [ ] Logout limpa a memória e volta ao login; o cookie some.
- [ ] MEMBER não vê a área de usuários; se chegar lá por URL, recebe `403` tratado.
- [ ] Ativar usuário pelo ADMIN funciona no navegador (teste de `PATCH` com CORS).
- [ ] Desativar a própria conta mostra o `409` e não encerra a sessão.
- [ ] `403` de dono mostra aviso e preserva o formulário.
- [ ] Validação de e-mail e senha curta mostra o erro no campo correto.

## 10. Pendências do backend que afetam o front

Estas são lacunas conhecidas. Vale decidir antes de o front começar a tela de usuários:

1. **Respostas de caixa, cabo e OLT não trazem o criador.** Sem isso, o front não esconde o botão de
   editar de quem não é dono. Proposta: incluir `createdBy` (id) ou um booleano `ownedByMe`. Esta
   escolha altera o contrato da API, por isso não foi feita sem confirmação.
2. **Access token válido por até 15 minutos após desativar o usuário.** Aceito por decisão de
   produto: as sessões de refresh são encerradas na hora, e o acesso restante dura no máximo o
   token que o técnico já tem.
3. **Sem limite de tentativas no login.** Afeta o front só na UX (pode aparecer `429` no futuro).

## 11. Referências

- `API.md` — seção "Autenticação", e os demais endpoints.
- `docs/SPECS.md` — RT-14 (autenticação e papéis) e RT-15 (dono por criador).
- `docs/superpowers/specs/2026-11-20-security-design.md` — desenho aprovado.
