import { ref } from "vue";
import pt from "./messages/pt-BR.json";
import en from "./messages/en.json";

export type Locale = "pt-BR" | "en";
export const supportedLocales: Locale[] = ["pt-BR", "en"];
export const messages: Record<Locale, Record<string, string>> = {
  "pt-BR": pt,
  en,
};
/** Browser preferences are suggestions, never geolocation or the player's identity. */
export function browserPreferences(
  languages: readonly string[] = navigator.languages?.length
    ? navigator.languages
    : [navigator.language],
): { locale: Locale; region: string | null } {
  let language: Locale = "en";
  for (const tag of languages) {
    const base = tag.toLowerCase().split("-")[0];
    if (base === "pt" || base === "en") {
      language = base === "pt" ? "pt-BR" : "en";
      break;
    }
  }
  let region: string | null = null;
  try {
    region = new Intl.Locale(languages[0] || "en").region || null;
  } catch {
    /* Invalid browser tags fall back to global content. */
  }
  return { locale: language, region };
}
function preferred(): Locale {
  try {
    const saved =
      sessionStorage.getItem("quizmosh-locale") ||
      localStorage.getItem("quizmosh-locale");
    if (saved === "en" || saved === "pt-BR") return saved;
  } catch {
    /* Storage can be unavailable in private contexts. */
  }
  return browserPreferences().locale;
}
export function preferredContentScope(): "ALL" | "GLOBAL" | "REGIONAL" {
  try {
    const saved = localStorage.getItem("quizmosh-content-scope");
    if (saved === "ALL" || saved === "GLOBAL" || saved === "REGIONAL")
      return saved;
  } catch {
    /* Browser suggestion still works without storage. */
  }
  return browserPreferences().region === "BR" ? "ALL" : "GLOBAL";
}
export const locale = ref<Locale>(preferred());
export function setLocale(value: string) {
  locale.value = value === "en" ? "en" : "pt-BR";
  document.documentElement.lang = locale.value;
  try {
    sessionStorage.setItem("quizmosh-locale", locale.value);
    localStorage.setItem("quizmosh-locale", locale.value);
  } catch {
    /* The in-memory preference still works. */
  }
}
export function hasMessage(key: string): boolean {
  return (
    Object.hasOwn(messages[locale.value], key) ||
    Object.hasOwn(messages["pt-BR"], key)
  );
}
export function t(key: string, params: Record<string, unknown> = {}): string {
  const text = messages[locale.value][key] ?? messages["pt-BR"][key] ?? key;
  if (typeof text !== "string") return key;
  return text.replace(/\{(\w+)\}/g, (match, name) =>
    params[name] === undefined ? match : String(params[name]),
  );
}
export function formatNumber(value: number): string {
  return new Intl.NumberFormat(locale.value).format(value);
}
setLocale(locale.value);
