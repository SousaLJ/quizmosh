import type { Card } from "./types";
export const cards: {
  id: Card;
  name: string;
  cost: number;
  mark: string;
  description: string;
  short: string;
}[] = [
  {
    id: "STEADY",
    name: "Na minha",
    cost: 0,
    mark: "●",
    description: "Guarde suas batidas. Sua resposta vale os pontos normais.",
    short: "Guardando energia",
  },
  {
    id: "SPOTLIGHT",
    name: "Holofote",
    cost: 1,
    mark: "✦",
    description:
      "Acertou? Divida 600 pontos extras com quem também acertar usando esta carta. Se for só você, leva tudo.",
    short: "Disputando o holofote",
  },
  {
    id: "DUET",
    name: "Dueto",
    cost: 1,
    mark: "∞",
    description:
      "Seu parceiro acertou? +250. Vocês dois? +500. Escolheram um ao outro e acertaram? +600 para cada.",
    short: "Tocando em dueto",
  },
  {
    id: "ALL_IN",
    name: "Tudo ou nada",
    cost: 2,
    mark: "ϟ",
    description:
      "Acertou? Ganhe de novo os pontos positivos da resposta. Errou ou não respondeu? −300.",
    short: "Apostando alto",
  },
];
export const cardInfo = (id?: Card) =>
  cards.find((c) => c.id === id) || cards[0];
export function avatarColor(id: string) {
  const hash = Array.from(id).reduce(
    (n, c) => (n * 31 + c.charCodeAt(0)) >>> 0,
    0,
  );
  return ["#c4f36b", "#b8a0ff", "#ff9c76", "#6fe4dc", "#ff9bd4", "#ffdd75"][
    hash % 6
  ];
}
