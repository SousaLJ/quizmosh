# QuizMosh — beta jogável para PC · 0.3.0

Jogo para reunir amigos, com interface em português, quatro modos, 80 perguntas e salas sincronizadas pelo servidor Java. Esta entrega amplia o core original, preservando as regras nos módulos de domínio e aplicação.

**Jogue no navegador do PC**, sem conta ou instalação de engine. Outros computadores e celulares podem entrar na sala. O modo de treino adiciona três bots identificados para você experimentar sozinho.

## Novo: Mosh Arena

A partida agora acontece em um palco com personagens animados, plataformas de resposta, duetos, cartas secretas e energia coletiva. **Mosh Arena** vem ativado na criação da sala; **Quiz clássico** preserva o estilo anterior.

- Escolha uma carta nos bastidores: Na minha, Holofote, Dueto ou Tudo ou nada.
- Use suas batidas para arriscar pontos ou confiar no acerto de um parceiro.
- Clique numa plataforma (ou use 1–4/setas), mova seu personagem e confirme com **Travar resposta / Enter**.
- Acerte junto com a turma para carregar a plateia. Ao chegar a 100%, a próxima rodada vira **BIS**, com bônus e penalidades das cartas em dobro.
- Veja a composição da pontuação, celebrações e ranking ao final de cada rodada.

As regras, exemplos e o roteiro de playtest estão em [docs/MOSH_ARENA.md](docs/MOSH_ARENA.md). A identidade visual usa arte vetorial própria e os assets gratuitos locais já incluídos.

## Começar no Windows com Docker

1. Extraia o ZIP inteiro.
2. Instale e abra o Docker Desktop, usando containers Linux.
3. Execute `iniciar-docker.bat`. O script cria uma senha aleatória em `.env` e inicia os serviços. Na primeira vez, baixa dependências e compila o projeto.
4. Abra **http://localhost:8080**.
5. Escolha um apelido e clique em **Criar minha sala** ou **Treine com bots**. No lobby, clique em **Começar o mosh**.

Linux/macOS com Docker Compose:

```bash
bash scripts/start-docker.sh
```

Para configurar manualmente, copie `.env.example` para `.env`, substitua a senha de exemplo e execute:

```bash
docker compose up -d --build --wait
```

Os scripts preservam um `.env` existente. Para acompanhar e encerrar:

```bash
docker compose logs -f game
docker compose down
```

`down` preserva o banco. **`down -v` apaga os volumes e os resultados persistidos.**

## Começar sem Docker

O pacote inclui `releases/quizmosh.jar`, compilado com a interface e os assets.

1. Instale **Java 25** (JDK ou JRE) e confira com `java -version`.
2. Execute `iniciar-pc.bat` no Windows ou `bash scripts/start-pc.sh` no Linux/macOS.
3. Após `Started QuizMoshServer`, abra **http://localhost:8080**.

Também pode executar na raiz do projeto:

```bash
java -jar releases/quizmosh.jar
```

Esse modo usa H2 local em `data/`. Depois de baixar Java e o pacote, não depende de CDN, APIs de conteúdo ou conexão externa. Deixe o servidor aberto enquanto joga. Outra porta:

```bash
java -jar releases/quizmosh.jar --server.port=9090
```

## Jogar com amigos

- **No mesmo PC:** abra duas abas independentes ou dois navegadores; crie a sala em um e entre pelo código no outro. Cada aba mantém sua sessão.
- **Na mesma rede:** no outro dispositivo, abra `http://IP_DO_PC:8080`. Use `ipconfig` no Windows para descobrir o IPv4. Se necessário, autorize a porta na rede privada do firewall. Redes de convidados podem isolar os clientes.
- **Convite/QR:** abra primeiro pelo endereço que os convidados também conseguem acessar. O convite usa esse endereço. `localhost` funciona apenas no próprio computador.
- **Tela coletiva:** no lobby, clique em **Abrir tela coletiva** e entre com um apelido para a tela. Ela mostra perguntas e placar; os jogadores respondem nos seus dispositivos.
- **Online:** use Docker em um servidor com domínio e HTTPS; veja `docs/OPERACAO.md`.

## O que funciona

| Recurso | Comportamento |
| --- | --- |
| Salas | Código curto; 2–12 jogadores; até 24 participantes contando espectadores e telas |
| Conteúdo | 40 perguntas de cinema e 40 de conhecimentos gerais |
| Configuração | Interface: 4, 8 ou 12 rodadas; 15, 25, 40 ou 60 segundos; mistura de modos ou um específico |
| Na mosca | Múltipla escolha; acerto vale 1.000 pontos |
| Bate-pronto | Acerto vale de 500 a 1.000 pontos conforme o tempo do servidor |
| Qual é a boa? | Quatro pistas; uma tentativa por pista; acerto vale 1.000/800/600/400; erro tira 100 |
| Quase lá | Aproximação numérica; posições recebem 1.000/600/300/100; acerto exato soma 200; empates mantidos |
| Partida | Contagem regressiva, bastidores táticos, arena, timer, pistas, resultados, ranking final e revanche |
| Treino | Três bots identificados, com acertos, erros e atrasos variados |
| Reconexão | Recarregar a mesma aba recupera a identidade e a partida enquanto a sala existir |
| Anfitrião | Saída transfere a sala; após 45 segundos de ausência, um humano conectado pode assumir |
| Acessibilidade | Atalhos 1–4/setas, Enter para confirmar na arena, foco visível, som opcional e movimento reduzido |
| Persistência | Resultados concluídos no PostgreSQL (Docker) ou H2 (JAR direto) |

Gabaritos não são enviados durante uma rodada aberta. Respostas numéricas aceitam ponto ou vírgula. Palpites por pistas aceitam os aliases do catálogo, ignorando acentos, caixa e pontuação; não há correção aproximada de qualquer erro de digitação.

## Estrutura e desenvolvimento

- `quizmosh-domain`: regras originais e modos.
- `quizmosh-application`: casos de uso e projeções públicas.
- `quizmosh-protocol`: contratos neutros para futuras plataformas.
- `quizmosh-inmemory`: salas e partidas ativas.
- `quizmosh-server`: Spring Boot, REST, WebSocket, scheduler, catálogo e resultados.
- `quizmosh-web`: Vue 3, TypeScript, Vite e assets locais.
- `deployment`, `scripts`, `docs`: proxy, comandos e documentação.

Para compilar: **JDK 25**, **Node.js 22.12+** e npm. O Maven Wrapper baixa Maven 3.9.9 na primeira execução.

```bash
# Compilar o pacote e executar testes Java
bash scripts/build.sh

# No Windows, via PowerShell
./scripts/build.ps1

# Testes de interface
npm ci --prefix quizmosh-web
npm test --prefix quizmosh-web

# Apenas Java
./mvnw test
```

Com o servidor na porta 8080, execute o cliente com atualização automática:

```bash
npm run dev --prefix quizmosh-web
```

Vite usa 5173 e encaminha `/api` e `/ws` ao Java. Os scripts de build incorporam o cliente ao JAR; Docker faz isso em etapas separadas.

Para verificar uma instância de teste já iniciada:

```bash
python3 scripts/smoke.py http://127.0.0.1:8080
python3 scripts/mosh-smoke.py http://127.0.0.1:8080
node scripts/websocket-smoke.mjs http://127.0.0.1:8080
```

Esses comandos criam salas descartáveis. Os dois primeiros jogam os quatro modos no estilo clássico e Mosh Arena, incluindo cartas e BIS; o terceiro verifica autenticação e sincronização por WebSocket.

## Limites desta versão

- A entrega para PC é Web. Cliente nativo Steam/Godot, Bluetooth, apps mobile, casting nativo e monetização ficam para etapas futuras.
- Salas e partidas em andamento ficam em memória. Reiniciar encerra essas salas; resultados arquivados permanecem. Use **uma réplica** da aplicação.
- Sessões duram no máximo 12 horas; salas sem presença por 30 minutos expiram. O placar final aparece na sala e é arquivado; ainda não há tela de histórico de partidas.
- Partidas iniciadas aceitam novos espectadores/telas; os jogadores são definidos na largada. Para jogar, entre no lobby ou após o encerramento.
- Os bots acessam o catálogo no servidor para simular acertos; são adversários de treino.
- O catálogo é um starter. Veja `docs/CONTEUDO_E_ASSETS.md` para ampliá-lo.

Consulte as verificações efetivamente executadas em `docs/VALIDACAO.md`.
