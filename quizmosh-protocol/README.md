# QuizMosh Protocol

Este módulo define nomes e envelopes de mensagens, mas não escolhe JSON, WebSocket, REST ou biblioteca de serialização.

O futuro `quizmosh-server` mapeará:

```text
JSON/WSS <-> protocol DTO <-> application command/view
```

Regra central: **o protocolo público nunca expõe respostas corretas antes do encerramento/reveal da rodada**.
