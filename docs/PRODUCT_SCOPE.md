# Product Scope

## O que é o Ludrivo

Uma plataforma de jogos sociais para reunir pessoas. A primeira experiência, Ludrivo Trivia, combina conhecimento, dedução, pistas e competição social; a marca permite novos tipos de jogo.

Promessa: **Jogos que aproximam pessoas. / Games that bring people together.**

A unidade central do produto é a **sala**.

## Cenários de uso

### Grupo casual
Amigos em uma faculdade, bar, viagem ou intervalo:

```text
celular -> cria sala -> amigos entram -> partida rápida
```

Sem Steam, sem streamer e sem cadastro obrigatório.

### Festa / sala de estar
Uma tela coletiva (Steam/TV/cast) apresenta o espetáculo e os celulares funcionam como controles.

### Online
Jogadores entram remotamente na mesma sala.

### Streaming
Streamer e audiência são um público adicional, não o centro do produto. Integrações de chat/audiência serão adapters futuros.

## Plataformas previstas

- Web/PWA
- Steam
- Android/iOS no futuro
- TV/casting no futuro
- LAN/hotspot/offline no futuro
- descoberta Bluetooth no futuro

## Monetização prevista

A monetização não faz parte do domain core.

Modelo de produto considerado:

- entrada em salas com baixa ou nenhuma fricção;
- starter gratuito;
- host/premium pago;
- packs oficiais de conteúdo;
- supporter/cosméticos;
- creator/UGC no futuro;
- recursos Pro/Event/Streaming no futuro.

As regras de acesso/licença devem ser implementadas em uma camada de entitlement/policy, não embutidas nas regras de pontuação.

## Identidade visual

Direção: marca acolhedora e expressiva, com coral, grafite, marfim e lima. Sora nos títulos e Inter na interface. Regras e arquivos em [BRAND.md](BRAND.md).

- legibilidade em TV e telas pequenas;
- UI 2D com movimento e feedback forte;
- ranking animado;
- cores + formas para identificar jogadores;
- safe areas para diferentes layouts;
- stream-safe audio quando aplicável.
