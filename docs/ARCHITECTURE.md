> Atualização 0.5.0: consulte [ACCOUNTS_PRIVACY_GROWTH.md](ACCOUNTS_PRIVACY_GROWTH.md) para contas, segurança, privacidade e operação atuais. O restante descreve o funcionamento base do jogo.

# Arquitetura da beta PC

O core foi preservado. Room, Match, GameRound e os quatro GameMode decidem regras e pontuação. O cliente não decide acertos nem altera o placar.

## Caminho de uma jogada

1. O cliente envia `roundId` e palpite por REST com seu token de convidado.
2. O servidor resolve a identidade pelo token e adquire o lock da sala.
3. QuizMoshApplicationService encaminha a resposta ao modo do core.
4. O core valida tempo, tipo, elegibilidade e tentativas e aplica a política de feedback.
5. O servidor envia projeções específicas por WebSocket. Cada pessoa recebe apenas a própria resposta; o gabarito aparece somente após o encerramento.

## Adaptadores

- GameService: sessões, lock por sala, fases, presença, bots e DTOs. Um Clock substituível permite testar expiração sem esperas artificiais.
- Catalog: perguntas privadas do servidor, seleção aleatória sem repetição na partida e verificação de capacidade antes da largada.
- ApiController: REST. Tokens aleatórios de 256 bits vinculados a participante e sala, válidos por até 12 horas.
- RoomSocket: autenticação na primeira mensagem, push personalizado e heartbeat. Tokens ficam fora das URLs. Reconexão automática e consulta REST recuperam o estado.
- ResultArchive: resultados via JDBC e migrations Flyway. PostgreSQL no Compose; H2 no JAR. Não recupera partidas ativas após restart.
- RequestGuard: limites de requisições, tamanho de corpo, verificação de origem da API e cabeçalhos de segurança.

## Fases

`LOBBY → COUNTDOWN → BACKSTAGE → ROUND → REVEAL → BACKSTAGE ... → FINISHED`

No estilo Quiz clássico, a etapa BACKSTAGE é omitida.

O scheduler inicia a preparação após 3 segundos, libera pistas, fecha pelo tempo e avança após 7 segundos de resultado. O anfitrião pode antecipar o avanço depois da revelação. A revanche cria outra partida com placar zerado.

## Ajustes no core

- startMatch valida/constrói Match antes de alterar Room, evitando deixar a sala em estado inválido se a construção falhar.
- Room.transferOwnership permite transferir para um jogador elegível após ausência prolongada.

O adaptador guarda a referência à última partida porque Room.finishMatch libera a sala para outra partida. Isso mantém o placar final sem acoplar transporte ao domínio.

## Hospedagem

Caddy recebe HTTP/HTTPS e encaminha HTTP/WebSocket ao Java. Java serve a interface e acessa PostgreSQL. Somente o proxy publica portas; o banco usa rede interna. Java executa como usuário sem privilégios, com filesystem somente leitura e /tmp temporário.

Use uma réplica. Redis, recuperação de estado ativo e coordenação distribuída são evoluções futuras. O limite configurável de 250 salas é uma proteção, não uma capacidade comprovada em teste de carga.


## Camada Mosh

`MoshSession`, no domínio, mantém batidas, cartas, energia coletiva e resolução determinística dos bônus. `RoundScoringPolicy`, porta opcional da aplicação, transforma o resultado base antes de `Match.completeCurrentRound`. Assim, histórico, totais e eventos concordam; os quatro modos originais continuam calculando a pontuação das respostas.

`GameService` controla o prazo dos bastidores e expõe projeções privadas da escolha e do gasto. A pergunta só é sorteada ao abrir a rodada. Bots escolhem cartas antes desse sorteio, sem acesso ao resultado futuro. Revanche instancia uma nova MoshSession.

No cliente, MoshArena renderiza personagens SVG e o cenário CSS; MoshBackstage cuida das cartas. A movimentação pelas plataformas é local e não revela respostas aos adversários. A confirmação usa a mesma API autoritativa Java das respostas anteriores.
