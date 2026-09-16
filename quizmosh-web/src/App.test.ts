import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { mount, flushPromises, type VueWrapper } from "@vue/test-utils";
import App from "./App.vue";
import { state, api, enter, connected } from "./game";
import type { State } from "./types";

vi.mock("qrcode", () => ({
  default: {
    toDataURL: vi.fn().mockResolvedValue("data:image/png;base64,AA=="),
  },
}));
vi.mock("./game", async () => {
  const { ref } = await import("vue");
  return {
    state: ref(null),
    session: ref(null),
    connected: ref(true),
    connectionMode: ref("ao vivo"),
    failure: ref(""),
    clockOffset: ref(0),
    api: vi.fn(),
    enter: vi.fn(),
    accept: vi.fn(),
    forget: vi.fn(),
    resume: vi.fn(),
  };
});
let wrapper: VueWrapper | undefined;
function lobby(): State {
  return {
    code: "ABCD",
    phase: "LOBBY",
    revision: 0,
    serverTime: new Date().toISOString(),
    config: {
      rounds: 4,
      seconds: 25,
      category: "all",
      modes: ["classic-trivia"],
    },
    players: [
      {
        id: "p1",
        nickname: "Leandro",
        role: "PLAYER",
        owner: true,
        bot: false,
        online: true,
      },
    ],
    you: {
      id: "p1",
      owner: true,
      role: "PLAYER",
      canAnswer: false,
      locked: false,
      submitted: false,
      answer: null,
      guessCorrect: null,
    },
    round: null,
    reveal: null,
    ranking: [],
    transitionAt: null,
    matchId: null,
    archived: false,
  };
}
function button(text: string) {
  return wrapper!.findAll("button").find((b) => b.text().includes(text))!;
}
beforeEach(() => {
  vi.clearAllMocks();
  state.value = null;
  connected.value = true;
  localStorage.clear();
  vi.spyOn(HTMLMediaElement.prototype, "play").mockResolvedValue(undefined);
});
afterEach(() => {
  wrapper?.unmount();
  wrapper = undefined;
  vi.restoreAllMocks();
});
describe("PC player flows", () => {
  it("creates a room with chosen category and a nickname without an account", async () => {
    const result = {
      code: "ABCD",
      token: "token",
      playerId: "p1",
      state: lobby(),
    };
    vi.mocked(api).mockResolvedValue(result);
    wrapper = mount(App);
    await wrapper.get("#nickname").setValue("Leandro");
    await button("Cinema").trigger("click");
    await wrapper.get("form").trigger("submit");
    await flushPromises();
    expect(api).toHaveBeenCalledWith(
      "/rooms",
      expect.objectContaining({
        nickname: "Leandro",
        practice: false,
        config: expect.objectContaining({
          category: "cinema",
          modes: ["classic-trivia", "quick-fire", "guess-it", "closest-wins"],
        }),
      }),
    );
    expect(enter).toHaveBeenCalledWith(result);
  });
  it("supports joining as a shared display and shows API errors", async () => {
    vi.mocked(api).mockRejectedValue(new Error("Sala não encontrada."));
    wrapper = mount(App);
    await button("Entrar com código").trigger("click");
    await wrapper.get("#nickname").setValue("Tela da sala");
    await wrapper.get("#room-code").setValue("abcd");
    await wrapper.get("#join-role").setValue("DISPLAY");
    await wrapper.get("form").trigger("submit");
    await flushPromises();
    expect(api).toHaveBeenCalledWith("/rooms/ABCD/join", {
      nickname: "Tela da sala",
      role: "DISPLAY",
    });
    expect(wrapper.get('[role="alert"]').text()).toContain(
      "Sala não encontrada.",
    );
  });
  it("blocks starting alone and enables start when a friend arrives", async () => {
    state.value = lobby();
    wrapper = mount(App);
    expect(button("Começar o mosh").attributes("disabled")).toBeDefined();
    state.value.players.push({
      id: "p2",
      nickname: "Amigo",
      role: "PLAYER",
      owner: false,
      bot: false,
      online: true,
    });
    await flushPromises();
    expect(button("Começar o mosh").attributes("disabled")).toBeUndefined();
  });
  it("sends a round-bound answer and prevents double submissions while pending", async () => {
    const s = lobby();
    s.phase = "ROUND";
    s.you.canAnswer = true;
    s.round = {
      id: "round-1",
      number: 1,
      mode: "classic-trivia",
      prompt: "Uma pergunta?",
      type: "choice",
      choices: [
        { id: "A", text: "Resposta A" },
        { id: "B", text: "Resposta B" },
      ],
      clues: [],
      unit: "",
      category: "cinema",
      startedAt: new Date().toISOString(),
      endsAt: new Date(Date.now() + 25000).toISOString(),
      clueIndex: 0,
      clueCount: 0,
      answeredCount: 0,
      playerCount: 2,
    };
    state.value = s;
    let resolve!: (data: unknown) => void;
    vi.mocked(api).mockReturnValue(
      new Promise((r) => {
        resolve = r;
      }),
    );
    wrapper = mount(App);
    await button("Resposta A").trigger("click");
    await button("Resposta A").trigger("click");
    expect(api).toHaveBeenCalledTimes(1);
    expect(api).toHaveBeenCalledWith("/rooms/ABCD/answer", {
      roundId: "round-1",
      value: "A",
    });
    resolve({ receipt: { accepted: true, correct: null }, state: s });
    await flushPromises();
  });
  it("keeps the final ranking and offers rematch only to the host", async () => {
    const s = lobby();
    s.phase = "FINISHED";
    s.ranking = [
      { id: "p1", nickname: "Leandro", score: 4200, rank: 1, bot: false },
    ];
    state.value = s;
    wrapper = mount(App);
    expect(wrapper.text()).toContain("4.200");
    expect(button("Quero revanche")).toBeDefined();
    state.value.you.owner = false;
    await flushPromises();
    expect(button("Quero revanche")).toBeUndefined();
  });
  it("uses the current preparation ID when confirming a tactical card", async () => {
    const s = lobby();
    s.phase = "BACKSTAGE";
    s.config.mosh = true;
    s.transitionAt = new Date(Date.now() + 25000).toISOString();
    s.mosh = {
      stageId: "stage-1",
      number: 1,
      mode: "classic-trivia",
      heat: 0,
      encore: false,
      energy: { p1: 3 },
      plans: {},
      ready: [],
      results: {},
    };
    state.value = s;
    vi.mocked(api).mockResolvedValue(s);
    wrapper = mount(App);
    await button("Holofote").trigger("click");
    await button("Confirmar jogada").trigger("click");
    await flushPromises();
    expect(api).toHaveBeenCalledWith("/rooms/ABCD/plan", {
      stageId: "stage-1",
      card: "SPOTLIGHT",
      target: null,
    });
  });
  it("selects a platform by keyboard then submits it only on Enter", async () => {
    const s = lobby();
    s.phase = "ROUND";
    s.config.mosh = true;
    s.you.canAnswer = true;
    s.mosh = {
      stageId: "stage-1",
      number: 1,
      mode: "classic-trivia",
      heat: 0,
      encore: false,
      energy: { p1: 3 },
      plans: { p1: { card: "STEADY", target: null } },
      ready: ["p1"],
      results: {},
    };
    s.round = {
      id: "r-arena",
      number: 1,
      mode: "classic-trivia",
      prompt: "Pergunta da arena",
      type: "choice",
      choices: [
        { id: "A", text: "Primeira" },
        { id: "B", text: "Segunda" },
      ],
      clues: [],
      unit: "",
      category: "all",
      startedAt: new Date().toISOString(),
      endsAt: new Date(Date.now() + 25000).toISOString(),
      clueIndex: 0,
      clueCount: 0,
      answeredCount: 0,
      playerCount: 1,
    };
    state.value = s;
    vi.mocked(api).mockResolvedValue({ receipt: { accepted: true }, state: s });
    wrapper = mount(App);
    document.dispatchEvent(new KeyboardEvent("keydown", { key: "2" }));
    await flushPromises();
    expect(api).not.toHaveBeenCalled();
    expect(wrapper.get('[data-choice="B"]').attributes("aria-pressed")).toBe(
      "true",
    );
    document.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter" }));
    await flushPromises();
    expect(api).toHaveBeenCalledWith("/rooms/ABCD/answer", {
      roundId: "r-arena",
      value: "B",
    });
  });
});
