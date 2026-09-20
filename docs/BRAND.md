# Ludrivo · identidade de marca

Ludrivo é a marca da plataforma de jogos sociais. É um nome criado: `lud-` evoca o universo lúdico; `-rivo` dá ritmo e identidade sonora. Não é uma palavra inglesa nem uma tradução literal. Pronúncia proposta: **lu-DRI-vo**. A marca não depende de quiz, tema ou mecânica específica.

| Elemento | Português | English |
| --- | --- | --- |
| Marca | Ludrivo | Ludrivo |
| Slogan | A turma faz o jogo. | Your crew makes the game. |
| Promessa | Jogos que aproximam pessoas. | Games that bring people together. |
| Experiência atual | Ludrivo Trivia | Ludrivo Trivia |
| Estilo de partida | Ludrivo Arena | Ludrivo Arena |

Nesta versão, Trivia reúne quatro modos de quiz. Arena é um estilo de partida dentro dessa experiência. Novos jogos poderão usar a marca principal com um nome descritivo próprio. A interface só anuncia experiências disponíveis.

## Sistema visual

| Cor | Código | Uso |
| --- | --- | --- |
| Coral | `#FF654A` | Símbolo, destaque e ações principais |
| Grafite | `#181B24` | Fundo e texto sobre coral/lima |
| Marfim | `#FFF8EE` | Texto e wordmark sobre fundo escuro |
| Lima | `#D9F56B` | Energia, confirmação e acentos |

Sora 600–800 em títulos; Inter 400–800 na interface. As fontes são servidas localmente, com as respectivas licenças SIL OFL em `public/licenses/`. Azul e rosa são cores auxiliares para distinguir modos e participantes; cor nunca substitui texto, forma ou estado acessível.

O símbolo combina um **L** arredondado com uma peça diagonal destacada, sugerindo movimento e encontro entre peças. O wordmark usa `ludrivo` em minúsculas. Os SVGs contêm contornos, sem depender de fontes instaladas.

- `public/brand/ludrivo-logo.svg`: logo para fundos escuros.
- `public/brand/ludrivo-logo-dark.svg`: logo para fundos claros.
- `public/brand/ludrivo-symbol.svg` e `ludrivo-symbol-dark.svg`: símbolo isolado.
- `public/favicon.svg` e `public/brand/ludrivo-icon-*.png`: ícones de navegador/dispositivo.
- `public/brand/ludrivo-social.svg` e `.png`: imagem bilíngue de compartilhamento, 1200 × 630.

Preservar as proporções, a separação entre as peças e uma margem livre mínima equivalente à largura da haste do L. Não aplicar sombras, gradientes ou deformação ao logo. Usar o símbolo quando o espaço não permitir ler o nome.

## Linguagem e aplicação

Tom direto, acolhedor e convidativo. Falar de pessoas e da experiência compartilhada. Em inglês, usar frases naturais, sem tradução literal obrigatória. Evitar promessas de novos jogos já disponíveis, superioridade sem comprovação ou garantia de diversão.

O título, a descrição e os metadados sociais acompanham o seletor PT/EN. O HTML inicial usa inglês para compartilhadores que não executam JavaScript; a imagem social inclui os dois slogans. Os links absolutos da imagem em `index.html` usam o domínio Railway atual e precisam ser atualizados caso o domínio público mude.

Os nomes técnicos `quizmosh-*`, pacotes `io.quizmosh`, banco de dados, chaves de armazenamento e campos de protocolo `mosh` permanecem por compatibilidade. Isso preserva preferências e integrações. Novas mensagens visíveis usam Ludrivo, e `/api/meta` informa a marca e a versão do produto.
