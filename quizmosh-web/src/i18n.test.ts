import { afterEach, describe, expect, it, vi } from "vitest";
import {
  browserPreferences,
  preferredContentScope,
  setLocale,
  t,
  messages,
  formatNumber,
} from "./i18n";
import { cards } from "./mosh";
import { api, connectionMode } from "./game";

afterEach(() => {
  vi.unstubAllGlobals();
  localStorage.clear();
  sessionStorage.clear();
  setLocale("pt-BR");
});
describe("individual language and cultural preferences", () => {
  it("uses supported browser languages and only an explicit country as a cultural suggestion", () => {
    expect(browserPreferences(["pt-BR"])).toEqual({
      locale: "pt-BR",
      region: "BR",
    });
    expect(browserPreferences(["en-BR"])).toEqual({
      locale: "en",
      region: "BR",
    });
    expect(browserPreferences(["pt-PT"])).toEqual({
      locale: "pt-BR",
      region: "PT",
    });
    expect(browserPreferences(["fr-FR", "en-US"])).toEqual({
      locale: "en",
      region: "FR",
    });
    expect(browserPreferences(["pt"])).toEqual({
      locale: "pt-BR",
      region: null,
    });
    expect(browserPreferences(["invalid_tag"])).toEqual({
      locale: "en",
      region: null,
    });
    vi.stubGlobal("navigator", { languages: ["pt-BR"], language: "pt-BR" });
    expect(preferredContentScope()).toBe("ALL");
    vi.stubGlobal("navigator", { languages: ["pt-PT"], language: "pt-PT" });
    expect(preferredContentScope()).toBe("GLOBAL");
    localStorage.setItem("quizmosh-content-scope", "REGIONAL");
    expect(preferredContentScope()).toBe("REGIONAL");
  });
  it("keeps complete matching message keys and interpolates values without losing punctuation", () => {
    expect(Object.keys(messages.en).sort()).toEqual(
      Object.keys(messages["pt-BR"]).sort(),
    );
    for (const bundle of Object.values(messages))
      for (const value of Object.values(bundle))
        expect(value.trim()).not.toBe("");
    setLocale("en");
    expect(t("content.inventory", { choice: 7, guess: 4, numeric: 5 })).toBe(
      "7 multiple choice · 4 clues · 5 estimates",
    );
    expect(formatNumber(12345)).toBe("12,345");
    expect(document.documentElement.lang).toBe("en");
    expect(sessionStorage.getItem("quizmosh-locale")).toBe("en");
    expect(cards[1].name).toBe("Spotlight");
    expect(connectionMode.value).toBe("connecting");
    setLocale("pt-BR");
    expect(cards[1].name).toBe("Holofote");
    expect(connectionMode.value).toBe("conectando");
    expect(formatNumber(12345)).toBe("12.345");
  });
  it("sends the individual language on HTTP requests without changing the room configuration", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue({ ok: true, json: async () => ({}) });
    vi.stubGlobal("fetch", fetchMock);
    setLocale("en");
    const config = {
      questionLanguage: "pt-BR",
      contentScope: "REGIONAL",
      questionRegion: "BR",
    };
    await api("/rooms", { config });
    const request = fetchMock.mock.calls[0][1];
    expect(request.headers["Accept-Language"]).toBe("en");
    expect(JSON.parse(request.body).config).toEqual(config);
  });
  it("retains error codes and parameters for translation after the response", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue({
          ok: false,
          status: 400,
          json: async () => ({
            code: "error.catalogCapacity",
            arguments: { available: 1, required: 4 },
            message: "Not enough questions.",
          }),
        }),
    );
    setLocale("en");
    await expect(api("/rooms", {})).rejects.toMatchObject({
      status: 400,
      code: "error.catalogCapacity",
      arguments: { available: 1, required: 4 },
    });
  });
});
