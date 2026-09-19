# QuizMosh 0.5.0 — contas, privacidade e crescimento

## Arquitetura

O domínio continua independente de Spring. `User`, `UserIdentity(provider, subject)` e `Entitlement.HOST_ROOM` vivem no domínio; `AccountRepository` e `AnalyticsPort` são portas da aplicação. `JdbcAccounts`, `AccountSessions`, `ProductPrivacy` e Spring Security são adaptadores do servidor. A implementação mantém a arquitetura e as regras dos quatro modos, Mosh Arena e BIS.

| Conceito | Persistência / responsabilidade |
|---|---|
| Guest | Apelido e bearer de participação por sala, em memória, por até 12h |
| User / Profile | `users`, `user_profiles`; ID interno e apelido editável |
| OAuth identity | `user_identities`; chave única provider + subject; uma conta comporta várias identidades |
| Account session | `user_sessions`; somente hash SHA-256 do token aleatório de 256 bits; expiração absoluta de 12h |
| Room / Match ativos | Memória, uma réplica; identidade de conta não substitui o bearer da sala |
| Resultados concluídos | `match_results`, migration V1 existente; equivale ao arquivo de completed matches, sem duplicar tabelas |
| Consentimento | `consents` (histórico por categoria/versão/data) e `privacy_preferences` |
| Referral | `referral_links`, validade de um dia, emitido somente com consentimento |
| Analytics | `analytics_events`, adaptador próprio, sem dependência de fornecedor externo |

## Autenticação e autorização

1. `/api/account` informa provedores habilitados, conta atual, preferências e token CSRF. Não retorna tokens OAuth nem token de sessão.
2. `/oauth2/authorization/google` ou `/oauth2/authorization/discord` inicia authorization code com state e PKCE. Google usa OIDC, validando assinatura, issuer, audience e nonce; Discord usa OAuth e consulta `/users/@me`.
3. O servidor resolve a identidade, cria a conta se necessário e emite `QM_ACCOUNT` (HttpOnly, SameSite=Lax, Secure por padrão). Não persiste access tokens, refresh tokens ou ID tokens. A sessão transitória OAuth é invalidada após login.
4. Apenas uma conta existente com HOST_ROOM pode fazer POST `/api/rooms`, com CSRF válido. Limites: três salas simultâneas e dez criações por hora por conta, além do limite global e da proteção por IP.
5. Convidados continuam entrando por POST `/api/rooms/{code}/join`, sem conta. Jogadas, reconexão e WebSocket usam o bearer original. Transferência de anfitrião dentro de uma sala não equivale a criar outra sala.

Não há associação automática por e-mail. Solicitamos somente `openid` (Google) ou `identify` (Discord), sem e-mail ou lista de contatos. Vincular outro provedor a uma conta existente precisará de uma futura operação explícita com autenticação de ambos. O schema já permite isso.

Logout revoga a sessão atual; `revoke-all` remove todas; exclusão de conta remove sessões, identidades, perfil, preferências e analytics da conta. Sessões de participação são independentes: sair da conta não expulsa um jogador da partida. Resultados antigos usam IDs/apelidos de partida e expiram em 30 dias; não são apresentados como histórico pessoal da conta nesta versão.

## Consentimento e minimização

Analytics fica desativado até autorização. O servidor verifica a preferência vigente a cada gravação, independentemente do frontend. A fila é limitada a 256 eventos, usa um único worker e descarta excesso; falhas de telemetry não impedem jogadas. Revogação e gravação usam a mesma exclusão mútua, com commit antes da liberação. Não são coletados IP, e-mail, respostas, apelidos ou user agent nos eventos.

`QM_PRIVACY` é uma capacidade aleatória necessária para lembrar a escolha; seu identificador derivado de convidado usa namespace distinto dos IDs de contas. `QM_REF` só é definido quando há consentimento de analytics. O estado CSRF é necessário à segurança. Apelido, idioma, som e filtro cultural salvos pelo usuário continuam preferências funcionais.

Categorias: necessary, analytics, advertising e personalization. As duas últimas ainda não ativam funcionalidades. A escolha inclui versão `2026-09-17`. Ao entrar numa conta, uma escolha explícita do navegador é preservada, inclusive rejeição. Sem uma escolha anterior, não se presume consentimento novo.

Retenção automática: sessões 12h; links de atribuição 1 dia; analytics 90 dias; preferências 180 dias; comprovantes de consentimento 365 dias; resultados de partidas 30 dias. A limpeza é horária, portanto pode haver até uma hora adicional antes da remoção física. Contas duram até exclusão. Exportação retorna dados da conta, identidades, preferências, consentimentos e eventos, sem segredos de sessão.

Isso é uma base técnica de privacidade, não certificação de conformidade. Antes do lançamento geral, o responsável deve definir/publicar controlador, canal privado de direitos, bases legais e regras de menores/transferência internacional, além de verificar subprocessadores e backups. Os textos PT/EN indicam o caráter beta; não inventam uma empresa, endereço ou contato.

## Compartilhamento e SEO

`/join/CODE` preenche o código automaticamente; `/?room=CODE` permanece compatível. Convites suportam copiar, Web Share, WhatsApp e QR. Compartilhar resultados gera texto ou PNG local; nada é publicado automaticamente. O usuário é informado de que os apelidos aparecerão. Instagram pode receber o PNG pelo compartilhamento de arquivo ou download; Discord e X recebem o texto/link por compartilhamento genérico.

Cinco páginas públicas são pré-renderizadas no build: `/`, `/how-to-play`, `/privacy`, `/terms`, `/cookies`. HTML inicial contém texto indexável, title, description, canonical, Open Graph, Twitter card e imagem PNG. A landing usa microdados Schema.org VideoGame. `robots.txt` e `sitemap.xml` são gerados para o domínio configurado. URLs de convite recebem noindex; não há indexação de salas ou dados pessoais. A SPA continua responsável pela interação. Pré-renderização inicial é PT-BR; PT/EN continuam disponíveis na interface, incluindo políticas.

## Analytics e limites das métricas

Eventos do cliente aceitos: LANDING_VIEWED, JOIN_GAME_CLICKED, CREATE_GAME_CLICKED, LOGIN_STARTED, SHARE_CREATED, SHARE_OPENED. Eventos de conta, salas, jogadas e partidas são produzidos pelo servidor. Um cliente não pode declarar MATCH_COMPLETED pela API de eventos. Eventos do navegador são sinais não confiáveis para faturamento, rankings ou recompensas.

Eventos de partida são por participante consentido; conte DISTINCT match_id para partidas e DISTINCT subject_id para pessoas ativas. Guests representam navegadores, não pessoas verificadas. Transferir do guest para conta pode criar dois sujeitos; não fazemos fingerprinting para unir identidades. Consentimento, bloqueios, fila cheia e modo offline causam subcontagem. SHARE_CREATED mede criação de convite/card, não comprova entrega ou leitura por aplicativos externos. SHARE_OPENED só observa quem aceita analytics. Os números não devem ser divulgados como CTR total ou coeficiente viral de toda a população.

Consultas iniciais PostgreSQL, apenas para operadores autorizados:

```sql
SELECT date_trunc('day', occurred_at) AS day,
       count(DISTINCT subject_id) AS active_subjects
FROM analytics_events GROUP BY 1 ORDER BY 1;

SELECT event_type, count(*) FROM analytics_events
WHERE occurred_at >= CURRENT_TIMESTAMP - INTERVAL '30 days'
GROUP BY event_type;

SELECT match_id,
       max(occurred_at) FILTER (WHERE event_type='MATCH_COMPLETED') -
       min(occurred_at) FILTER (WHERE event_type='MATCH_STARTED') AS duration
FROM analytics_events WHERE match_id IS NOT NULL GROUP BY match_id;
```

Referências técnicas: [Spring Security OAuth login](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/core.html), [CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html), [orientações ANPD sobre cookies](https://www.gov.br/anpd/pt-br/centrais-de-conteudo/materiais-educativos-e-publicacoes/guia-orientativo-cookies-e-protecao-de-dados-pessoais.pdf).
