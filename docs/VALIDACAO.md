# Validação Ludrivo · beta 0.5.0

Verificação local realizada em 20/09/2026, antes da publicação do rebranding. A versão do produto e de `/api/meta` é `0.5.0`; os módulos Maven continuam em `0.1.0-SNAPSHOT`.

| Verificação | Resultado |
| --- | --- |
| Maven verify em Java 25 | 31 testes aprovados, zero falhas, erros ou testes ignorados |
| Vue/Vitest | 18 testes aprovados |
| TypeScript e build Vite de produção | Aprovados, incluindo fontes locais e novos assets |
| Partida clássica por HTTP real | Quatro modos, dois jogadores e display, permissões, respostas, reconexão, revanche e transferência de anfitrião aprovados |
| Ludrivo Arena por HTTP real | Inglês + pacote Brasil, quatro modos, cartas, dueto, BIS, sigilo e pontuação aprovados |
| WebSocket real | Autenticação, broadcast personalizado e rejeição de sessão inválida aprovados |
| Metadados e assets no JAR | Nome/versão, logo, favicon, imagem social, manifesto e licenças respondem corretamente |
| Navegador Chromium real | PT/EN, título/descrição, preferências anteriores, criação com bots, lobby, bastidores e confirmação de resposta na Arena aprovados |
| Layout | Home PT/EN em 1440 e 390 px e EN em 320 px; lobby em 1440, 390 e 320 px; Arena em 1440 e 390 px, sem rolagem horizontal |
| Console e rede do navegador | Nenhum erro de JavaScript ou recurso com HTTP 4xx/5xx no fluxo verificado |

O teste local usou H2 em memória. Docker/Compose/PostgreSQL são cobertos pelo workflow do repositório; esta tabela não antecipa o resultado desse workflow nem do deploy Railway. Não foram executados testes de carga, Windows, Safari ou playtest com pessoas nesta atualização. O rebranding mantém contratos de jogo, armazenamento de preferências e identificadores técnicos compatíveis.

## Histórico: beta PC 0.4.0

Verificação local realizada em 17/09/2026. A branch prepara uma atualização da beta 0.3; este relatório não representa homologação da nova versão no Railway.

| Verificação | Resultado |
| --- | --- |
| Maven verify em Java 25 | 31 testes aprovados, zero falhas/erros/skips |
| Vue/Vitest em DOM simulado | 18 testes aprovados |
| TypeScript e build de produção Vite | Aprovados |
| Partida clássica por HTTP real | Português, quatro modos, dois jogadores e display; concluída |
| Mosh Arena por HTTP real | Inglês + pacote Brasil, quatro modos, dueto, holofote, aposta, BIS e revanche; concluída |
| Idiomas diferentes entre participantes | Mesmo conteúdo da sala nas requisições pt-BR/en |
| WebSocket real | Autenticação, broadcast personalizado e rejeição de credencial inválida aprovados |
| JAR executável com cliente local | Inicialização e entrega do cliente via HTTP aprovadas |
| Diff | Sem erros de whitespace |

## O que foi verificado

- Idioma sugerido pela lista do navegador; região cultural derivada apenas de país explicitamente indicado na preferência principal.
- Navegadores pt-BR/en-BR sugerem global + Brasil; pt-PT e demais regiões começam em global. Override manual é lembrado.
- Trocar a interface não altera idioma/região das perguntas nem perde nome digitado ou carta selecionada.
- Mensagens PT/EN têm chaves correspondentes e valores não vazios; parâmetros, pontos e estado de conexão acompanham o idioma.
- HTTP envia Accept-Language, recebe erros estruturados, traduz autenticação/origem inválida e preserva configuração da sala.
- Catálogo carrega 92 IDs com versões nos dois idiomas; filtros globais/regionais conseguem 12 rodadas únicas no mix padrão.
- Clássico e velocidade compartilham a capacidade de perguntas de alternativas. Cinema regional e outros conjuntos insuficientes são recusados antes do início.
- Aliases de ambos os idiomas são aceitos; resposta revelada usa o idioma da sala. Exclusão funciona pelo ID canônico.
- Perguntas, pistas e explicações são compartilhadas; projeções públicas e inventário não expõem gabaritos ou respostas privadas.
- Os testes anteriores de pontuação, quatro modos, expiração, reconexão, transferência de anfitrião, cartas secretas, energia, BIS, idempotência e arquivo de resultados continuam passando.

## Limites

O navegador remoto retornou `ERR_BLOCKED_BY_CLIENT` ao acessar a prévia local. O avatar/placar foi corrigido no CSS (dimensões explícitas, grid com pontos abaixo do nome e badge de pontuação) e o cliente compilou, mas ainda precisa de inspeção visual em navegador real, inclusive com 12 jogadores e nomes longos. Testes de componentes não comprovam a geometria do layout.

O ambiente local não forneceu daemon Docker. A estrutura Docker/Railway existente foi preservada; o workflow do repositório executa build, Compose/PostgreSQL e testes reais de comunicação. O teste local usou H2. O resultado de CI deve ser consultado no pull request.

Ainda é necessário playtest com pessoas para avaliar diversão/equilíbrio e revisão editorial humana da equivalência cultural das traduções. Não foram realizados testes Windows, Steam, carga, Bluetooth ou recuperação de partidas ativas após reinício.

## Reproduzir

```bash
./mvnw verify
npm ci --prefix quizmosh-web
npm test --prefix quizmosh-web
npm run build --prefix quizmosh-web
# Instância descartável de teste já iniciada:
python3 scripts/smoke.py http://127.0.0.1:8080
python3 scripts/mosh-smoke.py http://127.0.0.1:8080 en REGIONAL
node scripts/websocket-smoke.mjs http://127.0.0.1:8080
```

Maven mantém o versionamento dos módulos `0.1.0-SNAPSHOT`; a versão do produto, metadados e cliente é `0.4.0`.
