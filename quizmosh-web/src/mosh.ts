import { t, locale } from "./i18n";
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
    get name() {
      return t("ui.keepItCool");
    },
    cost: 0,
    mark: "●",
    get description() {
      return t("ui.saveYourBeatsYourAnswerEarnsThe");
    },
    get short() {
      return t("ui.savingEnergy");
    },
  },
  {
    id: "SPOTLIGHT",
    get name() {
      return t("ui.spotlight");
    },
    cost: 1,
    mark: "✦",
    get description() {
      return t("ui.gotItRightSplit600ExtraPoints");
    },
    get short() {
      return t("ui.chasingTheSpotlight");
    },
  },
  {
    id: "DUET",
    get name() {
      return t("ui.duet");
    },
    cost: 1,
    mark: "∞",
    get description() {
      return t("ui.yourPartnerGotItRight250Both");
    },
    get short() {
      return t("ui.playingADuet");
    },
  },
  {
    id: "ALL_IN",
    get name() {
      return t("ui.allIn");
    },
    cost: 2,
    mark: "ϟ",
    get description() {
      return t("ui.gotItRightEarnYourPositiveAnswer");
    },
    get short() {
      return t("ui.bettingBig");
    },
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
