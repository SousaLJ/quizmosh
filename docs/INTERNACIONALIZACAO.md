# Idiomas e contexto cultural · beta 0.4

## Comportamento para jogadores

| Preferência inicial | Interface sugerida | Conteúdo sugerido ao criar sala |
| --- | --- | --- |
| pt-BR | Português (Brasil) | Global + Brasil |
| en-BR | Inglês | Global + Brasil |
| en-US / en-GB | Inglês | Global |
| pt-PT | Português (Brasil), tradução disponível | Global |
| Idioma sem tradução | Primeiro idioma suportado na lista do navegador; inglês como fallback | Global, salvo região BR explícita na preferência principal |

A indicação regional é uma sugestão cultural, não uma localização verificada. Não usamos IP, GPS, geolocalização nem perfil pessoal. Uma escolha manual sempre tem prioridade. PT/EN no cabeçalho muda somente a interface, inclusive durante a rodada, sem perder respostas em edição ou cartas selecionadas. O idioma fica em sessionStorage por aba, com localStorage como preferência inicial para novas abas. O filtro cultural manual é lembrado para a criação de novas salas.

Em multiplayer, o anfitrião escolhe o conteúdo para o grupo: global, global + Brasil ou regional brasileiro. Jogadores com culturas diferentes podem usar interfaces diferentes e jogar o mesmo pacote global. Entrar na sala não muda suas perguntas. A primeira edição possui apenas o pacote regional BR; não traduzir textos brasileiros e marcá-los como cultura de outro país.

## Arquivos de mensagens

- Cliente: `quizmosh-web/src/messages/pt-BR.json` e `en.json`, chaves estáveis e parâmetros `{nome}`; acesso por `t(chave, argumentos)` em `i18n.ts`.
- Servidor: `messages_pt_BR.properties`, `messages_en.properties`; `messages.properties` é o fallback em português. UTF-8, sem serviço externo de tradução.
- Erros HTTP: código e argumentos, mais uma mensagem compatível com `Accept-Language`.
- Resultado de cartas: o domínio produz `mosh.result.*`; cliente traduz `reasonKey`. O campo `reason` permanece como fallback para clientes 0.3.
- Nomes próprios dos jogadores e texto de perguntas não são traduzidos pelo seletor da interface. Números de pontos seguem o idioma individual.

Novas mensagens exigem entradas correspondentes nos dois idiomas. Os testes verificam chaves, valores não vazios, parâmetros, persistência e mudança de idioma sem reiniciar a jogada.

## Catálogo

Os dois JSONs privados do servidor contêm 92 IDs iguais. `regions: []` marca conhecimentos de alcance global; `regions: ["BR"]` marca conteúdo brasileiro. Tradução não muda a região. Nesta edição “global” significa conhecimento geral e cultura popular internacional; a classificação deve continuar recebendo revisão editorial.

O servidor valida correspondência estrutural: IDs, tipo, categoria, região, índice correto, valor numérico e quantidade de pistas/alternativas. Isso não substitui revisão humana de fatos e equivalência cultural. O primeiro alias é a resposta exibida; aliases de ambos os idiomas são aceitos, com a normalização do core.

| Conjunto | Alternativas | Pistas | Estimativas | Total |
| --- | ---: | ---: | ---: | ---: |
| Global | 29 | 24 | 23 | 76 |
| Brasil | 7 | 4 | 5 | 16 |
| Global + Brasil | 36 | 28 | 28 | 92 |

Todos os conjuntos suportam 12 rodadas no mix padrão com todas as categorias. Nem toda combinação de categoria e modo isolado tem essa capacidade; a criação mostra a falta e o servidor também recusa, evitando esgotamento no meio da partida.

## Adicionar um idioma ou região

1. Para idioma, adicionar as mensagens do cliente/servidor e a variante de cada ID canônico; registrar o idioma em `i18n.ts`, `Catalog`, `GameService` e `MatchSettings`.
2. Para região, criar perguntas originais e suas traduções, marcar o código regional e registrar suporte nas validações e no inventário do servidor. Disponibilizar a opção na configuração do cliente.
3. Revisar fatos, progressão das pistas, aliases e termos culturais com pessoas familiarizadas com a região. Texto traduzido não equivale a adaptação cultural.
4. Executar os testes de catálogo, HTTP e interface. Atualizar o inventário/contagens documentados e exercitar os quatro modos sem repetição de IDs.
