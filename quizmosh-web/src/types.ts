export type Config = {
  rounds: number;
  seconds: number;
  category: string;
  modes: string[];
  mosh?: boolean;
  questionLanguage?: "pt-BR" | "en";
  contentScope?: "ALL" | "GLOBAL" | "REGIONAL";
  questionRegion?: "BR";
};
export type Player = {
  id: string;
  nickname: string;
  role: string;
  owner: boolean;
  bot: boolean;
  online: boolean;
  answered?: boolean;
};
export type Card = "STEADY" | "SPOTLIGHT" | "DUET" | "ALL_IN";
export type Tactic = { card: Card; target: string | null };
export type Mosh = {
  stageId: string;
  number: number;
  mode: string;
  heat: number;
  encore: boolean;
  energy: Record<string, number>;
  plans: Record<string, Tactic>;
  ready: string[];
  results: Record<
    string,
    Tactic & {
      base: number;
      bonus: number;
      total: number;
      success: boolean;
      reason: string;
      reasonKey?: string;
    }
  >;
};
export type Rank = {
  id: string;
  nickname: string;
  score: number;
  rank: number;
  bot: boolean;
};
export type Round = {
  id: string;
  number: number;
  mode: string;
  prompt: string;
  type: string;
  choices: { id: string; text: string }[];
  clues: string[];
  unit: string;
  category: string;
  startedAt: string;
  endsAt: string;
  clueIndex: number;
  clueCount: number;
  answeredCount: number;
  playerCount: number;
};
export type State = {
  code: string;
  phase: string;
  revision: number;
  serverTime: string;
  config: Config;
  players: Player[];
  you: {
    id: string;
    owner: boolean;
    role: string;
    canAnswer: boolean;
    locked: boolean;
    submitted: boolean;
    answer: string | null;
    guessCorrect: boolean | null;
  };
  round: Round | null;
  reveal: {
    roundId: string;
    number: number;
    prompt: string;
    answer: string;
    explanation: string;
    deltas: Record<string, number>;
    correctOption: string | null;
  } | null;
  ranking: Rank[];
  transitionAt: string | null;
  matchId: string | null;
  archived: boolean;
  mosh?: Mosh | null;
};
export type Session = { token: string; code: string; playerId: string };

export type CatalogInventory = {
  language: string;
  category: string;
  scope: string;
  region: string;
  counts: Record<"choice" | "guess" | "numeric", number>;
};

export type CatalogCategory = {
  id: string;
  names: Record<string, string>;
};
