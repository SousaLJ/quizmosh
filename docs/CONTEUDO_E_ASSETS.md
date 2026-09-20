# Conteúdo e assets

O catálogo contém 92 perguntas canônicas: 40 de cinema e 52 de conhecimentos gerais; 36 de múltipla escolha, 28 com pistas e 28 numéricas. São 76 globais e 16 regionais brasileiras. Perguntas de escolha servem aos modos clássico e velocidade, compartilhando o mesmo conjunto sem repetição de ID.

`quizmosh-server/src/main/resources/questions.json` contém português do Brasil; `questions.en.json` contém as mesmas 92 perguntas em inglês. Cada variante preserva ID, categoria, tipo, regiões, ordem das alternativas, gabarito e valor numérico.

Perguntas, pistas e explicações foram redigidas para o jogo sobre fatos. Não foram copiadas de um banco comercial de quizzes. O pacote não inclui pôsteres, cenas, trilhas ou diálogos de filmes. Títulos são referências factuais às obras.

## Ampliar o catálogo

Cada entrada exige ID único, category, type, prompt, explanation e regions (`[]` para global, `["BR"]` para regional brasileiro):

- choice: options (quatro strings) e correctIndex (base zero).
- guess: answers (nome principal e aliases) e clues (quatro pistas progressivas).
- numeric: value (número) e unit.

Cadastre a versão correspondente nos dois arquivos. O servidor recusa traduções incompletas ou alterações estruturais do gabarito na inicialização. O primeiro alias de cada idioma é usado na revelação, e os aliases de ambos são aceitos nos palpites. Veja também `INTERNACIONALIZACAO.md`.

Revise fatos, indique ano/edição quando necessário e inclua aliases em português e inglês. O servidor verifica capacidade antes da partida e não repete IDs na mesma partida.

## Assets externos gratuitos

| Asset | Origem | Licença |
| --- | --- | --- |
| Kenney Interface Sounds | https://kenney.nl/assets/interface-sounds | CC0 1.0 |
| Lucide | https://lucide.dev/license | ISC; derivados de Feather sob MIT |
| Sora | https://github.com/sora-xor/sora-font | SIL Open Font License 1.1 |
| Inter | https://github.com/rsms/inter | SIL Open Font License 1.1 |

Sons utilizados: click_001.ogg, confirmation_001.ogg, error_001.ogg e bong_001.ogg. Ícones vêm de lucide-vue-next; fontes de @fontsource/sora e @fontsource/inter. Todos são servidos localmente.

Licenças completas em `quizmosh-web/public/licenses/` e na tela Créditos & assets. Preserve-as ao redistribuir. As licenças dos assets não alteram a licença do restante do projeto.

Logotipo e símbolo SVG, ícones e imagem de compartilhamento em `quizmosh-web/public/brand/` seguem [BRAND.md](BRAND.md). O lettering do logo usa contornos derivados de Sora, com a licença incluída. Personagens originais em SVG, cenário de arena em CSS e composição visual foram elaborados para esta implementação. Não é necessário comprar assets.
