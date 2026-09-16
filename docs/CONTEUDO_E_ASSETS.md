# Conteúdo e assets

`quizmosh-server/src/main/resources/questions.json` contém 80 perguntas: 40 por categoria (cinema/geral), sendo 16 de múltipla escolha, 12 com pistas e 12 numéricas. Perguntas de escolha servem aos modos clássico e velocidade.

Perguntas, pistas e explicações foram redigidas para o jogo sobre fatos. Não foram copiadas de um banco comercial de quizzes. O pacote não inclui pôsteres, cenas, trilhas ou diálogos de filmes. Títulos são referências factuais às obras.

## Ampliar o catálogo

Cada entrada exige ID único, category, type, prompt e explanation:

- choice: options (quatro strings) e correctIndex (base zero).
- guess: answers (nome principal e aliases) e clues (quatro pistas progressivas).
- numeric: value (número) e unit.

Revise fatos, indique ano/edição quando necessário e inclua aliases em português e inglês. O servidor verifica capacidade antes da partida e não repete IDs na mesma partida.

## Assets externos gratuitos

| Asset | Origem | Licença |
| --- | --- | --- |
| Kenney Interface Sounds | https://kenney.nl/assets/interface-sounds | CC0 1.0 |
| Lucide | https://lucide.dev/license | ISC; derivados de Feather sob MIT |
| Outfit | https://github.com/Outfitio/Outfit-Fonts | SIL Open Font License 1.1 |

Sons utilizados: click_001.ogg, confirmation_001.ogg, error_001.ogg e bong_001.ogg. Ícones vêm de lucide-vue-next; fonte de @fontsource/outfit. Todos são servidos localmente.

Licenças completas em `quizmosh-web/public/licenses/` e na tela Créditos & assets. Preserve-as ao redistribuir. As licenças dos assets não alteram a licença do restante do projeto.

Logotipo SVG, personagens originais em SVG, cenário de arena em CSS e composição visual foram elaborados para esta implementação. Não é necessário comprar assets.
