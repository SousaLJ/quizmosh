# Ludrivo Arena · beta 0.5.0

O Ludrivo passa a tratar a partida como um show coletivo. Personagens ocupam o palco, cada resposta vira uma plataforma, parceiros formam duetos e os acertos da turma energizam a plateia. A competição continua individual, mas acertar perguntas deixa de ser a única fonte de pontos.

## Jogar

Na criação da sala, **Ludrivo Arena** vem selecionado. O botão **Quiz clássico** permite jogar com as regras anteriores. Treino com bots e tela coletiva funcionam nos dois estilos.

1. **Bastidores:** antes de ver a pergunta, escolha uma carta. A primeira preparação dura até 25 segundos; as demais, 16. Ao confirmar, a carta fica travada. As cartas só são reveladas juntas. Se todos confirmarem, a rodada começa após um mínimo de 3 segundos de preparação.
2. **Palco:** nas perguntas de múltipla escolha, clique numa plataforma ou use 1–4/setas. Seu personagem se move. Você pode mudar de ideia até clicar em **Travar resposta** ou pressionar Enter. Só você vê sua posição de resposta; os outros continuam em posições neutras na sua tela.
3. **Outros modos:** as pistas aparecem progressivamente; o controle numérico permite aumentar ou diminuir o palpite em passos de 0,1, 1, 10, 100 ou 1.000. Também é possível digitar qualquer valor válido.
4. **Resultado:** os personagens comemoram, os pontos aparecem no palco e o jogo separa os pontos da resposta dos pontos da carta. O servidor avança automaticamente.

## Batidas e cartas

Cada jogador começa com 3 batidas, pode guardar até 5 e paga a carta ao confirmar. No encerramento, ganha 1 batida se acertar ou 2 se errar/não responder. A reposição maior oferece mais opções a quem ficou para trás. Repetir a mesma requisição de confirmação não gasta novamente.

| Carta | Custo | Efeito |
| --- | --- | --- |
| Na minha | 0 | Pontuação normal. Guarda energia. É a escolha automática quando o tempo acaba. |
| Holofote | 1 | Um prêmio de 600 é dividido igualmente, com arredondamento para baixo, entre quem escolheu Holofote e acertou. Um único vencedor leva 600. |
| Dueto | 1 | Escolha outro jogador: acerto dele rende 250; acerto de ambos rende 500. Se escolherem um ao outro e ambos acertarem, cada um recebe 600 extras. |
| Tudo ou nada | 2 | Acerto acrescenta novamente os pontos positivos obtidos pela resposta; erro ou ausência de resposta desconta 300. |

Escolher alguém no Dueto não obriga essa pessoa a usar a mesma carta. Ela só recebe o efeito da própria carta. Se um parceiro sair e não responder, o dueto não pontua por ele.

Em **Na mosca**, **Bate-pronto** e **Qual é a boa?**, acerto significa ter enviado uma resposta correta. Em **Quase lá**, ficar entre as duas primeiras posições por proximidade, incluindo os empates, conta como acerto para cartas e energia. Os pontos do modo original continuam iguais.

## O BIS

A energia da plateia aumenta pela proporção de acertos, até 40 pontos percentuais por rodada. Todos acertaram: +40. Metade acertou: +20. Ao alcançar 100%, **a próxima rodada** recebe o BIS, anunciado antes da escolha das cartas.

O BIS dobra apenas bônus e penalidades das cartas. Exemplo: uma resposta de 1.000 com Tudo ou nada rende 2.000 normalmente; no BIS, rende 3.000 (1.000 da resposta + 2.000 da carta). Um erro com essa carta custa 600 no BIS. A energia coletiva reinicia na preparação do BIS e volta a carregar ao final.

## Direção visual

- Palco em perspectiva, luzes móveis, caixas de som, plateia e partículas.
- Avatares vetoriais originais, com cores e acessórios derivados da identidade do jogador.
- Movimento até as plataformas, confirmação separada e feedback de resposta travada.
- Conexões visuais entre parceiros após a revelação das cartas.
- Coroa para o primeiro lugar, incluindo empates; pontuação flutuante e celebração.
- Assets gratuitos já incluídos: sons Kenney CC0, ícones Lucide e fontes Sora e Inter. Personagens e cenário são SVG/CSS próprios; não há download de arte durante a partida.
- Sem dependência de GPU 3D, CDN, conta externa ou engine nativa. Animações respeitam `prefers-reduced-motion`.

## O que ainda precisa de playtest

A implementação é funcional, mas ainda não foi testada com um grupo presencial para confirmar diversão e equilíbrio. Os valores de cartas são uma primeira calibração, não uma economia competitiva homologada.

Sugestão de sessão: 4–6 amigos, 8 rodadas em Ludrivo Arena e uma partida em Quiz clássico para comparação. Observar se todos conseguem explicar a carta escolhida, se o dueto gera interação, se o BIS acontece em momento interessante e se alguém prefere sempre a mesma carta. Ajustar números e duração a partir dessas observações.

Movimentação livre com colisões, minigames físicos, controle por gamepad, criação de avatar e versão nativa são possibilidades futuras; não estão implementados nesta beta.
