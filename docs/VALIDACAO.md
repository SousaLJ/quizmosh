# Validação da beta PC 0.3.0

Verificação realizada em 16/09/2026. Não confundir testes locais com homologação em produção.

| Verificação | Resultado |
| --- | --- |
| Maven verify, Java 25, seis módulos | Aprovado |
| Testes Java | 26 aprovados, zero falhas e erros |
| Testes de interface Vue em DOM simulado | 11 aprovados |
| TypeScript e build de produção Vite | Aprovados |
| JAR executável com cliente e assets locais | Compilado e inicializado com sucesso |
| Partida clássica por HTTP real | Quatro modos, dois jogadores e uma tela coletiva concluídos |
| Mosh Arena por HTTP real | Quatro modos, dueto recíproco, holofote dividido, aposta, BIS, totais finais e revanche aprovados |
| WebSocket real | Autenticação, atualização personalizada e rejeição de token inválido aprovadas |
| Docker Compose | Estrutura da versão 0.2 preservada; configuração anteriormente validada pelo Compose v2.39.4 |
| Scripts shell | Sintaxe validada com bash -n |

## Regras verificadas

- Projeções públicas não contêm respostas corretas nem pistas futuras.
- Um participante não recebe a resposta privada de outro.
- Palpites concorrentes/repetidos são aplicados uma única vez.
- Respostas com roundId antigo são rejeitadas.
- Espectadores e displays não pontuam.
- Apenas o anfitrião inicia/avança; ausência ou saída permite transferência.
- Expiração de rodada e progresso das pistas são controlados pelo servidor.
- Os quatro modos concluem a partida; placar final permanece disponível após recarregar.
- Revanche gera outra partida e zera o placar.
- As configurações de 12 rodadas têm conteúdo suficiente nos três filtros e quatro modos.
- Migrations e gravação de resultados são idempotentes no banco H2 em modo PostgreSQL.
- Falha no banco preserva o placar e o relatório pendente para nova tentativa, inclusive após revanche.
- Origem externa, payload inválido e ausência de credencial são rejeitados pela API.

## Novas regras e controles verificados

- Cartas e valor gasto pelos adversários ficam ocultos durante os bastidores, inclusive na tela coletiva.
- Confirmação idêntica repetida é idempotente; trocar carta confirmada, agir como espectador e usar preparação antiga são rejeitados.
- Reposição de batidas, teto de energia, saldo insuficiente, parceiro inválido e ausência de resposta são tratados pelo domínio.
- Dueto recíproco, dueto com erro próprio, holofote compartilhado, aposta e BIS alteram os pontos conforme as regras.
- BIS é anunciado antes das escolhas e dobra tanto os bônus quanto o risco da aposta.
- Preparação e partida avançam mesmo com jogador ausente; treino com bots completa os quatro modos.
- Pontuação final é igual à soma dos resultados; revanche reinicia cartas, energia e placar.
- Componentes impedem cartas sem saldo e dueto sem parceiro; plataforma exige confirmação separada.
- Interface envia stageId e roundId corretos; atalhos selecionam sem responder até Enter.

## Limites da verificação

O ambiente não disponibilizou um daemon Docker. A composição foi validada, mas as imagens e os containers não foram construídos/executados aqui. O fluxo completo do JAR foi executado com H2; PostgreSQL e emissão de certificados pelo Caddy precisam do ambiente de hospedagem. Há workflow de CI preparado para construir/iniciar o Compose e executar os três testes de comunicação. Esse workflow não foi executado aqui.

O navegador remoto bloqueou o acesso à prévia local. A interface foi compilada, verificada pelo TypeScript e exercitada em onze testes de componentes; não foi possível concluir inspeção visual em navegador real neste ambiente.

Não houve playtest com um grupo de pessoas; a diversão e o equilíbrio das cartas precisam dessa validação.

Os scripts Windows foram preparados, mas não executados em Windows. Não foi realizado teste de carga, homologação Steam, recuperação de partidas ativas após restart ou teste em dispositivos Bluetooth.

## Reproduzir

```bash
./mvnw verify
npm ci --prefix quizmosh-web
npm test --prefix quizmosh-web
npm run build --prefix quizmosh-web
# Com uma instância de teste já iniciada:
python3 scripts/smoke.py http://127.0.0.1:8080
python3 scripts/mosh-smoke.py http://127.0.0.1:8080
node scripts/websocket-smoke.mjs http://127.0.0.1:8080
```

O relatório de Maven usa o versionamento original dos módulos, 0.1.0-SNAPSHOT; a versão do produto e do cliente desta entrega é beta 0.3.0.
