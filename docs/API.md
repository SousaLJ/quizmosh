# Adaptador Web v0.4

REST JSON na mesma origem. Os contratos neutros do core continuam separados dos DTOs do adaptador Web.

| Método e rota | Entrada | Resposta |
| --- | --- | --- |
| GET /api/meta | — | Versão, modos, idiomas, regiões e inventário público por filtro |
| POST /api/rooms | nickname, practice, config opcional | token, code, playerId, state |
| POST /api/rooms/{code}/join | nickname, role | Credencial e estado |
| GET /api/rooms/{code} | Bearer | Estado personalizado |
| POST /api/rooms/{code}/start | Bearer; config opcional | Contagem regressiva |
| POST /api/rooms/{code}/plan | Bearer; stageId, card, target opcional | Estado com sua carta confirmada |
| POST /api/rooms/{code}/answer | Bearer; roundId, value (string) | receipt, state |
| POST /api/rooms/{code}/next | Bearer; anfitrião | Próxima rodada após revelação |
| POST /api/rooms/{code}/leave | Bearer | ok; revoga sessão |

Config: `{"rounds":8,"seconds":25,"category":"all","modes":["classic-trivia","quick-fire","guess-it","closest-wins"],"mosh":true,"questionLanguage":"pt-BR","contentScope":"ALL","questionRegion":"BR"}`.

Papéis: PLAYER, SPECTATOR, DISPLAY. A identidade vem do token, nunca de um ID de jogador enviado pelo cliente. Erros retornam `{"code":"error.auth","arguments":{},"message":"Mensagem"}` com código HTTP apropriado. Tentativas repetidas não aplicam pontos novamente; roundId antigo é rejeitado.

## WebSocket

Conecte a `/ws` (wss em HTTPS). Primeira mensagem: `{"code":"ABCD","token":"credencial"}`. Receba snapshots personalizados com revision e serverTime. Envie `{"type":"ping"}` a cada poucos segundos para presença.

Tokens não aparecem em URL, convite ou broadcast. Handler exige mesma origem. Conexões anônimas expiram; até duas conexões por convidado acomodam reconexões. Cliente descarta revisões antigas e recupera o estado pela mesma credencial. O tempo do servidor decide a validade dos palpites.


## Ludrivo Arena

`mosh` omitido ou `true` ativa a arena; `false` preserva o fluxo clássico. Cartas: `STEADY`, `SPOTLIGHT`, `DUET`, `ALL_IN`. Exemplo de confirmação: `{"stageId":"id-da-preparacao","card":"DUET","target":"id-do-parceiro"}`. Em outras cartas, omita `target` ou envie `null`.

Fase adicional: `BACKSTAGE`, antes de cada `ROUND`. Nesse momento `round` é nulo. `mosh` contém `stageId`, `number`, `mode`, `heat`, `encore`, `energy`, `plans`, `ready` e `results`. Durante a preparação, `plans` contém apenas a escolha do próprio jogador; o saldo público dos outros também oculta o gasto para não denunciar a carta. `ready` revela somente quem confirmou. Ao abrir a pergunta, as cartas se tornam públicas.

Uma confirmação repetida idêntica é idempotente. Trocar uma carta já confirmada, usar `stageId` antigo, agir fora do prazo ou gastar sem saldo retorna erro. Espectadores/telas não podem confirmar. O tempo da pergunta só começa depois dos bastidores.

`results` aparece após o encerramento, por jogador, com `base`, `bonus`, `total`, `card`, `target`, `success`, `reasonKey` e `reason` (fallback em português para clientes anteriores). `reveal.deltas`, ranking, eventos e histórico já incluem os efeitos das cartas. As posições visuais nas plataformas são locais; a API continua recebendo somente a resposta final por `roundId`.

## Idioma e regiões

`Accept-Language` seleciona as mensagens de erro da requisição: português do Brasil ou inglês, incluindo variantes como `en-US`. Não muda a configuração da sala. O cliente usa `code` e `arguments` para retraduzir um erro já recebido se o jogador trocar de idioma.

Config aceita `questionLanguage` (`pt-BR` ou `en`), `contentScope` (`ALL`, `GLOBAL`, `REGIONAL`) e `questionRegion` (`BR`, única região com pacote nesta versão). Valores omitidos preservam a compatibilidade: `pt-BR`, `ALL`, `BR`. Em `GLOBAL`, a região não restringe perguntas globais; `ALL` reúne globais e a região selecionada; `REGIONAL` exige uma marca regional correspondente.

`GET /api/meta` fornece `questionLanguages`, `questionRegions`, `contentScopes` e `catalog`: lista de `{language, category, scope, region, counts:{choice,guess,numeric}}`. Não inclui enunciados, pistas ou gabaritos. `questions` conta IDs canônicos, sem duplicar traduções.

Criação e início validam a demanda do ciclo de modos por tipo de pergunta. Clássico e velocidade consomem a mesma reserva de alternativas. Capacidade insuficiente retorna HTTP 400 com `error.catalogCapacity` e argumentos `available`/`required`; não abre partida parcial. Cinema regional, por exemplo, tem apenas uma pergunta de escolha nesta edição.

`round.language` e `round.regions` descrevem o conteúdo. O idioma/região sugerido pelo navegador fica no cliente e não altera silenciosamente uma sala ao entrar. Perguntas, pistas, resposta revelada e explicação são iguais para todos os participantes da sala.
