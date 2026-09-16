# Adaptador Web v0.3

REST JSON na mesma origem. Os contratos neutros do core continuam separados dos DTOs do adaptador Web.

| Método e rota | Entrada | Resposta |
| --- | --- | --- |
| GET /api/meta | — | Versão, modos, número de perguntas |
| POST /api/rooms | nickname, practice, config opcional | token, code, playerId, state |
| POST /api/rooms/{code}/join | nickname, role | Credencial e estado |
| GET /api/rooms/{code} | Bearer | Estado personalizado |
| POST /api/rooms/{code}/start | Bearer; config opcional | Contagem regressiva |
| POST /api/rooms/{code}/plan | Bearer; stageId, card, target opcional | Estado com sua carta confirmada |
| POST /api/rooms/{code}/answer | Bearer; roundId, value (string) | receipt, state |
| POST /api/rooms/{code}/next | Bearer; anfitrião | Próxima rodada após revelação |
| POST /api/rooms/{code}/leave | Bearer | ok; revoga sessão |

Config: `{"rounds":8,"seconds":25,"category":"all","modes":["classic-trivia","quick-fire","guess-it","closest-wins"],"mosh":true}`.

Papéis: PLAYER, SPECTATOR, DISPLAY. A identidade vem do token, nunca de um ID de jogador enviado pelo cliente. Erros retornam `{"message":"Mensagem"}` com código HTTP apropriado. Tentativas repetidas não aplicam pontos novamente; roundId antigo é rejeitado.

## WebSocket

Conecte a `/ws` (wss em HTTPS). Primeira mensagem: `{"code":"ABCD","token":"credencial"}`. Receba snapshots personalizados com revision e serverTime. Envie `{"type":"ping"}` a cada poucos segundos para presença.

Tokens não aparecem em URL, convite ou broadcast. Handler exige mesma origem. Conexões anônimas expiram; até duas conexões por convidado acomodam reconexões. Cliente descarta revisões antigas e recupera o estado pela mesma credencial. O tempo do servidor decide a validade dos palpites.


## Mosh Arena

`mosh` omitido ou `true` ativa a arena; `false` preserva o fluxo clássico. Cartas: `STEADY`, `SPOTLIGHT`, `DUET`, `ALL_IN`. Exemplo de confirmação: `{"stageId":"id-da-preparacao","card":"DUET","target":"id-do-parceiro"}`. Em outras cartas, omita `target` ou envie `null`.

Fase adicional: `BACKSTAGE`, antes de cada `ROUND`. Nesse momento `round` é nulo. `mosh` contém `stageId`, `number`, `mode`, `heat`, `encore`, `energy`, `plans`, `ready` e `results`. Durante a preparação, `plans` contém apenas a escolha do próprio jogador; o saldo público dos outros também oculta o gasto para não denunciar a carta. `ready` revela somente quem confirmou. Ao abrir a pergunta, as cartas se tornam públicas.

Uma confirmação repetida idêntica é idempotente. Trocar uma carta já confirmada, usar `stageId` antigo, agir fora do prazo ou gastar sem saldo retorna erro. Espectadores/telas não podem confirmar. O tempo da pergunta só começa depois dos bastidores.

`results` aparece após o encerramento, por jogador, com `base`, `bonus`, `total`, `card`, `target`, `success` e `reason`. `reveal.deltas`, ranking, eventos e histórico já incluem os efeitos das cartas. As posições visuais nas plataformas são locais; a API continua recebendo somente a resposta final por `roundId`.
