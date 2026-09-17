import { setLocale } from "./i18n";
import { beforeEach, describe, it, expect } from "vitest";
import { mount } from "@vue/test-utils";
import MoshArena from "./MoshArena.vue";
import MoshBackstage from "./MoshBackstage.vue";
import type { State } from "./types";

function room(): State {
  return {
    code: "TEST",
    phase: "BACKSTAGE",
    revision: 1,
    serverTime: new Date().toISOString(),
    config: {
      rounds: 4,
      seconds: 25,
      category: "all",
      modes: ["classic-trivia"],
      mosh: true,
    },
    players: ["a", "b"].map((id, i) => ({
      id,
      nickname: i ? "Parceiro" : "Você",
      role: "PLAYER",
      owner: !i,
      bot: false,
      online: true,
    })),
    you: {
      id: "a",
      owner: true,
      role: "PLAYER",
      canAnswer: true,
      locked: false,
      submitted: false,
      answer: null,
      guessCorrect: null,
    },
    round: null,
    reveal: null,
    ranking: [],
    transitionAt: null,
    matchId: "m",
    archived: false,
    mosh: {
      stageId: "s1",
      number: 1,
      mode: "classic-trivia",
      heat: 40,
      encore: false,
      energy: { a: 3, b: 3 },
      plans: {},
      ready: [],
      results: {},
    },
  };
}
beforeEach(() => setLocale("pt-BR"));
describe("Mosh controls", () => {
  it("requires a partner before committing duet and emits its identity", async () => {
    const w = mount(MoshBackstage, {
      props: { state: room(), seconds: 20, disabled: false },
    });
    await w.findAll(".tactic-card")[2].trigger("click");
    expect(
      w.get(".backstage-action button").attributes("disabled"),
    ).toBeDefined();
    await w.get("#duet-partner").setValue("b");
    await w.get(".backstage-action button").trigger("click");
    expect(w.emitted("commit")).toEqual([[{ card: "DUET", target: "b" }]]);
    w.unmount();
  });
  it("prevents unaffordable cards, repeated plans, and spectator actions", async () => {
    const s = room();
    s.mosh!.energy.a = 0;
    const w = mount(MoshBackstage, {
      props: { state: s, seconds: 20, disabled: false },
    });
    expect(w.findAll(".tactic-card")[3].attributes("disabled")).toBeDefined();
    s.mosh!.plans.a = { card: "STEADY", target: null };
    await w.setProps({ state: { ...s } });
    expect(w.find(".backstage-action button").exists()).toBe(false);
    s.you.id = "screen";
    s.you.role = "DISPLAY";
    await w.setProps({ state: { ...s } });
    expect(w.find(".tactic-cards").exists()).toBe(false);
    w.unmount();
  });
  it("moves onto a platform before sending a separate confirmation", async () => {
    const s = room();
    s.phase = "ROUND";
    s.round = {
      id: "r1",
      number: 1,
      mode: "classic-trivia",
      prompt: "Pergunta",
      type: "choice",
      choices: [
        { id: "A", text: "Primeira" },
        { id: "B", text: "Segunda" },
      ],
      clues: [],
      unit: "",
      category: "all",
      startedAt: "",
      endsAt: "",
      clueIndex: 0,
      clueCount: 0,
      answeredCount: 0,
      playerCount: 2,
    };
    const w = mount(MoshArena, {
      props: { state: s, canAnswer: true, selected: "" },
    });
    expect(w.get(".lock-answer").attributes("disabled")).toBeDefined();
    await w.findAll(".answer-platform")[1].trigger("click");
    expect(w.emitted("select")).toEqual([["B"]]);
    expect(w.emitted("confirm")).toBeUndefined();
    await w.setProps({ selected: "B" });
    expect(w.findAll(".answer-platform")[1].attributes("aria-pressed")).toBe(
      "true",
    );
    await w.get(".lock-answer").trigger("click");
    expect(w.emitted("confirm")).toHaveLength(1);
    s.you.locked = true;
    s.you.answer = "B";
    await w.setProps({ state: { ...s }, canAnswer: false });
    expect(w.get(".lock-answer").attributes("disabled")).toBeDefined();
    w.unmount();
  });
  it("shows encore and keeps other players on neutral ground while answering", () => {
    const s = room();
    s.mosh!.encore = true;
    const w = mount(MoshArena, { props: { state: s } });
    expect(w.classes()).toContain("encore");
    expect(w.text()).toContain("RISCO E BÔNUS ×2");
    expect(w.findAll(".arena-player")).toHaveLength(2);
    w.unmount();
  });
});
