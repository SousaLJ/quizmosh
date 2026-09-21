# Conteúdo e assets

O catálogo contém 600 perguntas canônicas, com 100 em cada categoria: Cinema, Conhecimentos gerais, Futebol, Videogames, Cultura pop e Música. Há 294 perguntas de alternativas, 151 com pistas e 155 numéricas; 419 globais e 181 brasileiras. Alternativas atendem aos modos clássico e velocidade, compartilhando IDs sem repetição na partida.

A fonte editorial bilíngue da carga inicial está em `content/catalog/v1/`. `scripts/catalog.py` valida os dados e gera `V3__seed_question_catalog.sql`. O servidor lê exclusivamente o banco após o Flyway aplicar as migrações; os JSONs editoriais não são empacotados no JAR nem na interface. O inventário e os nomes das categorias chegam ao cliente por `/api/meta`, sem gabaritos.

Perguntas, pistas e explicações foram redigidas para o jogo sobre fatos. Não foram copiadas de um banco comercial de quizzes. O pacote não inclui pôsteres, cenas, trilhas, letras de músicas ou diálogos de filmes. Títulos são referências factuais às obras.

## Ampliar o catálogo

Cada entrada exige ID único, category, type, prompt, explanation e regions (`[]` para global, `["BR"]` para regional brasileiro):

- choice: options (quatro strings) e correctIndex (base zero).
- guess: answers (nome principal e aliases) e clues (quatro pistas progressivas).
- numeric: value (número) e unit.

Novas cargas e correções exigem uma nova migração versionada, sem modificar V1, V2 ou V3 depois de aplicadas. Cadastre a versão correspondente nos dois idiomas; veja [CATALOGO_BANCO.md](CATALOGO_BANCO.md). O servidor recusa traduções incompletas ou alterações estruturais do gabarito na inicialização. O primeiro alias de cada idioma é usado na revelação, e os aliases de ambos são aceitos nos palpites. Veja também `INTERNACIONALIZACAO.md`.

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
