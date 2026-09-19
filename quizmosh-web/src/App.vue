<script setup lang="ts">
import {
  t,
  locale,
  setLocale,
  formatNumber,
  preferredContentScope,
} from "./i18n";
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import {
  Zap,
  ArrowRight,
  Users,
  Play,
  Check,
  Crown,
  Copy,
  Monitor,
  Volume2,
  VolumeX,
  Maximize2,
  X,
  Sparkles,
  Target,
  Brain,
  Lightbulb,
  Film,
  Globe2,
  Trophy,
  LogOut,
  Link,
  ChevronRight,
  Gamepad2,
  Radio,
  HelpCircle,
  LoaderCircle,
  RotateCcw,
  Eye,
} from "lucide-vue-next";
import QRCode from "qrcode";
import {
  state,
  session,
  connected,
  connectionMode,
  failure,
  clockOffset,
  api,
  enter,
  accept,
  forget,
  resume,
} from "./game";
import type { Config, Tactic, CatalogInventory } from "./types";
import MoshArena from "./MoshArena.vue";
import MoshAvatar from "./MoshAvatar.vue";
import MoshBackstage from "./MoshBackstage.vue";
import { cardInfo, cards } from "./mosh";
import ProductPanels from "./ProductPanels.vue";
import PublicPage from "./PublicPage.vue";
import Sharing from "./Sharing.vue";
import {initializeProduct,account,requireHost,invitedRoom,track,preferences} from "./product";
import "./product.css";
function goHome() { location.assign('/'); }
const publicPage = ['privacy','terms','cookies','how-to-play'].find(p=>location.pathname==='/'+p || location.pathname==='/'+p+'/') || '';


const modes = computed(() => [
  {
    id: "classic-trivia",
    name: t("ui.bullseye"),
    tag: t("ui.classicTrivia"),
    description: t("ui.fourOptionsOneRightAnswerShowWhat"),
    icon: Brain,
    color: "purple",
    rule: t("ui.oneAnswerPerRoundEachCorrectAnswer"),
  },
  {
    id: "quick-fire",
    name: t("ui.quickfire"),
    tag: t("ui.speed"),
    description: t("ui.thinkFastClickFastEverySecondCounts"),
    icon: Zap,
    color: "orange",
    rule: t("ui.oneAnswerPerRoundCorrectAnswersEarn"),
  },
  {
    id: "guess-it",
    name: t("ui.whatSTheAnswer"),
    tag: t("ui.cluesAndGuesses"),
    description: t("ui.connectTheCluesAndBeatYourFriends"),
    icon: Lightbulb,
    color: "green",
    rule: t("ui.oneAttemptPerClueACorrectAnswer"),
  },
  {
    id: "closest-wins",
    name: t("ui.closeEnough"),
    tag: t("ui.estimation"),
    description: t("ui.youDonTNeedToBeExact"),
    icon: Target,
    color: "pink",
    rule: t("ui.theClosestGuessesEarn1000600"),
  },
]);
const tab = ref(
  invitedRoom() ? "join" : "create",
);
const nickname = ref(localStorage.getItem("quizmosh-name") || "");
const code = ref(invitedRoom());
const role = ref(
  new URLSearchParams(location.search).has("display") ? "DISPLAY" : "PLAYER",
);
const config = ref<Config>({
  rounds: 8,
  seconds: 25,
  category: "all",
  modes: modes.value.map((m) => m.id),
  mosh: true,
  questionLanguage: locale.value,
  contentScope: preferredContentScope(),
  questionRegion: "BR",
});
watch(
  () => config.value.contentScope,
  (scope) => {
    try {
      localStorage.setItem("quizmosh-content-scope", scope || "GLOBAL");
    } catch {
      /* This room still uses the selected scope. */
    }
  },
);
const selectedMode = ref("mix");
const inventory = ref<CatalogInventory[]>([]);
const catalogUnavailable = ref(false);
const availableContent = computed(() =>
  inventory.value.find(
    (item) =>
      item.language === config.value.questionLanguage &&
      item.category === config.value.category &&
      item.scope === config.value.contentScope &&
      item.region === config.value.questionRegion,
  ),
);
const contentShortage = computed(() => {
  const available = availableContent.value;
  if (!available) return null;
  const selected =
    selectedMode.value === "mix"
      ? modes.value.map((mode) => mode.id)
      : [selectedMode.value];
  const required = { choice: 0, guess: 0, numeric: 0 };
  for (let round = 0; round < config.value.rounds; round++) {
    const mode = selected[round % selected.length];
    required[
      mode === "guess-it"
        ? "guess"
        : mode === "closest-wins"
          ? "numeric"
          : "choice"
    ]++;
  }
  for (const type of ["choice", "guess", "numeric"] as const) {
    if (available.counts[type] < required[type])
      return {
        type: t("content." + type),
        available: available.counts[type],
        required: required[type],
      };
  }
  return null;
});
async function loadCatalog() {
  try {
    const metadata = await api("/meta");
    inventory.value = metadata?.catalog || [];
    catalogUnavailable.value = !inventory.value.length;
  } catch {
    catalogUnavailable.value = true;
  }
}
function questionLanguageName(language?: string) {
  return t(language === "en" ? "language.english" : "language.portuguese");
}

const errorArguments = ref<Record<string, unknown>>({});
const busy = ref(false),
  error = ref(""),
  toast = ref(""),
  dialog = ref(""),
  advanced = ref(false);
const answer = ref(""),
  sound = ref(localStorage.getItem("quizmosh-sound") !== "off");
const platform = ref("");
const dialStep = ref(1);
const ownTactic = computed(() => state.value?.mosh?.plans[state.value.you.id]);
const ownBreakdown = computed(
  () => state.value?.mosh?.results[state.value.you.id],
);
const now = ref(Date.now()),
  qr = ref("");
let clock: ReturnType<typeof setInterval>,
  toastTimer: ReturnType<typeof setTimeout>;
const sounds: Record<string, HTMLAudioElement> = {};
const activeMode = computed(
  () =>
    modes.value.find((m) => m.id === state.value?.round?.mode) ||
    modes.value[0],
);
const playerList = computed(
  () => state.value?.players.filter((p) => p.role === "PLAYER") || [],
);
const currentPlayer = computed(() =>
  state.value?.players.find((p) => p.id === state.value?.you.id),
);
const correctedNow = computed(() => now.value + clockOffset.value);
const remaining = computed(
  () =>
    Math.max(
      0,
      Math.ceil(
        (Date.parse(state.value?.round?.endsAt || "") - correctedNow.value) /
          1000,
      ),
    ) || 0,
);
const transition = computed(
  () =>
    Math.max(
      0,
      Math.ceil(
        (Date.parse(state.value?.transitionAt || "") - correctedNow.value) /
          1000,
      ),
    ) || 0,
);
const fraction = computed(() =>
  Math.min(100, (remaining.value / (state.value?.config.seconds || 25)) * 100),
);
const invitePath = ref("");
const invite = computed(() => location.origin + (invitePath.value || `/join/${state.value?.code || ""}`));
async function prepareInvite() {
  if (!state.value) return;
  try { invitePath.value = (await api('/shares',{code:state.value.code})).path; }
  catch { invitePath.value = `/join/${state.value.code}`; }
}
async function copyInvite() { await prepareInvite(); await copy(invite.value); }
watch(()=>state.value?.code,()=>{invitePath.value="";});
watch(()=>preferences.value.analytics,()=>{invitePath.value="";});
watch(dialog, value=>{if(value==='invite') void prepareInvite();});
const displayLink = computed(() => invite.value + (invite.value.includes("?") ? "&" : "?") + "display=1");
const localHost = computed(() =>
  ["localhost", "127.0.0.1", "[::1]"].includes(location.hostname),
);
const ownDelta = computed(
  () => state.value?.reveal?.deltas[state.value.you.id] || 0,
);
const winners = computed(
  () =>
    state.value?.ranking
      .filter((p) => p.rank === 1)
      .map((p) => p.nickname)
      .join(" & ") || "",
);
const isDisplay = computed(() => state.value?.you.role === "DISPLAY");
const liveAnswerAllowed = computed(
  () =>
    state.value?.you.canAnswer &&
    connected.value &&
    remaining.value > 0 &&
    !busy.value,
);

function play(name: string) {
  if (!sound.value) return;
  const a =
    sounds[name] ?? (sounds[name] = new Audio(`/assets/${name}_001.ogg`));
  a.volume = 0.35;
  a.currentTime = 0;
  void a.play().catch(() => {});
}
function notify(text: string) {
  toast.value = text;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (toast.value = ""), 3200);
}
function toggleSound() {
  sound.value = !sound.value;
  localStorage.setItem("quizmosh-sound", sound.value ? "on" : "off");
  if (sound.value) play("click");
}
async function perform(work: () => Promise<void>) {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  try {
    await work();
  } catch (e) {
    error.value =
      e instanceof Error
        ? (e as Error & { code?: string }).code || e.message
        : "ui.somethingWentWrongPleaseTryAgain";
    errorArguments.value =
      e instanceof Error
        ? (e as Error & { arguments?: Record<string, unknown> }).arguments || {}
        : {};
    play("error");
  } finally {
    busy.value = false;
  }
}
async function create(practice = false) {
  if (!requireHost()) return;
  if (contentShortage.value) return;
  if (!nickname.value.trim()) {
    error.value = "ui.chooseANicknameToGetStarted";
    document.getElementById("nickname")?.focus();
    return;
  }
  await perform(async () => {
    config.value.modes =
      selectedMode.value === "mix"
        ? modes.value.map((m) => m.id)
        : [selectedMode.value];
    const data = await api("/rooms", {
      nickname: nickname.value.trim(),
      practice,
      config: config.value,
    });
    enter(data);
    localStorage.setItem("quizmosh-name", nickname.value.trim());
    play("confirmation");
  });
}
async function join() {
  void track("JOIN_GAME_CLICKED");
  await perform(async () => {
    const data = await api(`/rooms/${code.value.trim().toUpperCase()}/join`, {
      nickname: nickname.value.trim(),
      role: role.value,
    });
    enter(data);
    localStorage.setItem("quizmosh-name", nickname.value.trim());
    play("confirmation");
  });
}
async function start() {
  await perform(async () => {
    accept(await api(`/rooms/${state.value!.code}/start`, state.value!.config));
    play("bong");
  });
}
async function submit(value = answer.value) {
  if (!liveAnswerAllowed.value || !value.trim()) return;
  const round = state.value!.round!;
  await perform(async () => {
    const data = await api(`/rooms/${state.value!.code}/answer`, {
      roundId: round.id,
      value: value.trim(),
    });
    accept(data.state);
    if (data.receipt.accepted)
      play(data.receipt.correct === false ? "error" : "click");
    else
      error.value =
        (
          {
            "answer.already_submitted": "ui.youHaveAlreadyAnsweredThisRound",
            "answer.wait_for_next_clue": "ui.waitForTheNextClue",
            "answer.player_locked": "ui.youAlreadyGotItRight",
          } as Record<string, string>
        )[data.receipt.messageKey] || "ui.answerNotAcceptedCheckItAndTry";
  });
}
function selectPlatform(value: string) {
  if (!liveAnswerAllowed.value) return;
  platform.value = value;
  play("click");
}
async function commitTactic(plan: Tactic) {
  await perform(async () => {
    accept(
      await api(`/rooms/${state.value!.code}/plan`, {
        stageId: state.value!.mosh!.stageId,
        ...plan,
      }),
    );
    play("click");
  });
}
function turnDial(direction: number) {
  if (!liveAnswerAllowed.value) return;
  const value = Number(answer.value.replace(",", ".")) || 0;
  answer.value = String(
    Math.max(
      -999999999,
      Math.min(
        999999999,
        Math.round((value + direction * dialStep.value) * 10000) / 10000,
      ),
    ),
  );
  play("click");
}
async function leave() {
  await perform(async () => {
    if (session.value)
      await api(`/rooms/${session.value.code}/leave`, {}, "POST");
    forget();
    dialog.value = "";
    answer.value = "";
    error.value = "";
    history.replaceState({}, "", "/");
  });
}
async function copy(text: string) {
  try {
    await navigator.clipboard.writeText(text);
    notify("ui.inviteCopied");
  } catch {
    dialog.value = "invite";
  }
}
async function fullscreen() {
  try {
    if (document.fullscreenElement) await document.exitFullscreen();
    else await document.documentElement.requestFullscreen();
  } catch {
    notify("ui.useF11ToToggleFullScreen");
  }
}
function keyboard(e: KeyboardEvent) {
  if (e.key === "Escape") {
    dialog.value = "";
    return;
  }
  if (
    dialog.value ||
    /INPUT|SELECT|TEXTAREA/.test((e.target as HTMLElement)?.tagName)
  )
    return;
  if (
    state.value?.mosh &&
    liveAnswerAllowed.value &&
    state.value.round?.type === "choice"
  ) {
    const choices = state.value.round.choices;
    if (["ArrowRight", "ArrowDown", "ArrowLeft", "ArrowUp"].includes(e.key)) {
      e.preventDefault();
      const index = choices.findIndex((c) => c.id === platform.value);
      const direction = ["ArrowRight", "ArrowDown"].includes(e.key) ? 1 : -1;
      selectPlatform(
        choices[(index + direction + choices.length) % choices.length].id,
      );
      return;
    }
    if (e.key === "Enter" && platform.value) {
      const control = (e.target as HTMLElement)?.closest?.("button,a");
      if (control && control.getAttribute("data-choice") !== platform.value)
        return;
      e.preventDefault();
      void submit(platform.value);
      return;
    }
  }
  if (
    liveAnswerAllowed.value &&
    state.value?.round?.type === "choice" &&
    "1234".includes(e.key)
  ) {
    const option = state.value.round.choices[Number(e.key) - 1];
    if (option) {
      e.preventDefault();
      if (state.value.mosh) selectPlatform(option.id);
      else void submit(option.id);
    }
  }
}
watch(
  () => state.value?.round?.id,
  (id, previous) => {
    answer.value = "";
    platform.value = "";
    dialStep.value = 1;
    error.value = "";
    if (id && id !== previous) play("bong");
  },
);
watch(
  () => state.value?.round?.clueIndex,
  () => {
    answer.value = "";
    error.value = "";
  },
);
watch(
  () => state.value?.phase,
  (phase, previous) => {
    if (phase === "BACKSTAGE" && state.value?.mosh?.encore) play("bong");
    if (phase === "FINISHED" || (phase === "REVEAL" && previous === "ROUND"))
      play("confirmation");
  },
);
watch(
  invite,
  async (url) => {
    qr.value = await QRCode.toDataURL(url, {
      width: 220,
      margin: 1,
      color: { dark: "#191b26", light: "#ffffff" },
    });
  },
  { immediate: true },
);
onMounted(() => {
  void initializeProduct();
  if (!state.value) void loadCatalog();
  void resume();
  clock = setInterval(() => (now.value = Date.now()), 150);
  document.addEventListener("keydown", keyboard);
});
onUnmounted(() => {
  clearInterval(clock);
  clearTimeout(toastTimer);
  document.removeEventListener("keydown", keyboard);
});
</script>

<template>
  <div
    class="app-shell"
    :class="{ 'display-mode': isDisplay, 'mosh-game': state?.config.mosh }"
  >
    <header class="header">
      <a
        class="brand"
        href="/"
        @click.prevent="state ? (dialog = 'leave') : goHome()"
        :aria-label="t('ui.quizmoshHome')"
        ><span class="brand-mark"><Zap :size="24" fill="currentColor" /></span
        >quiz<span>mosh</span><sup>β</sup></a
      >
      <nav v-if="!state" class="top-nav" :aria-label="t('ui.mainNavigation')">
        <span class="active-nav"> {{ t("ui.play") }} </span
        ><button @click="dialog = 'rules'">{{ t("ui.howItWorks") }}</button
        ><button @click="dialog = 'credits'">{{ t("ui.credits") }}</button>
      </nav>
      <div v-else class="room-chip">
        <span class="live-dot" :class="{ offline: !connected }"></span
        ><span> {{ t("ui.room") }} </span><strong>{{ state.code }}</strong
        ><button
          class="icon-button"
          :aria-label="t('ui.copyInvite')"
          @click="copyInvite"
        >
          <Copy :size="16" />
        </button>
      </div>
      <div class="header-actions">
        <label class="language-switch" :title="t('language.personal')">
          <Globe2 :size="16" aria-hidden="true" />
          <span class="sr-only">{{ t("language.interface") }}</span>
          <select
            id="interface-language"
            :value="locale"
            @change="setLocale(($event.target as HTMLSelectElement).value)"
          >
            <option value="pt-BR" lang="pt-BR">PT</option>
            <option value="en" lang="en">EN</option>
          </select>
        </label>
        <span v-if="state" class="connection-text">{{ connectionMode }}</span
        ><button
          class="icon-button"
          :aria-label="sound ? t('ui.muteSound') : t('ui.enableSound')"
          :aria-pressed="sound"
          @click="toggleSound"
        >
          <Volume2 v-if="sound" :size="20" /><VolumeX
            v-else
            :size="20"
          /></button
        ><button
          class="icon-button"
          :aria-label="t('ui.fullScreen')"
          data-action="fullscreen"
          @click="fullscreen"
        >
          <Maximize2 :size="19" /></button
        ><button
          v-if="state"
          class="icon-button"
          :aria-label="t('ui.leaveRoom')"
          @click="dialog = 'leave'"
        >
          <LogOut :size="19" />
        </button>
      </div>
    </header>

    <div v-if="failure" class="network-banner" role="status">{{ failure }}</div>
    <PublicPage v-if="publicPage" :page="publicPage" />
    <main v-else-if="!state" class="home">
      <div class="hero-grid">
        <section class="hero-copy">
          <div class="eyebrow">
            <span class="live-dot"></span> {{ t("ui.thePartyStartsHere") }}
          </div>
          <h1>
            {{ t("ui.yourFriendsTogether") }} <br />
            {{ t("ui.theChaos") }} <span> {{ t("ui.isGuaranteed") }} </span
            ><Sparkles class="headline-star" :size="36" />
          </h1>
          <p class="hero-description">
            {{ t("ui.oneStageYourFriendsNobodyPlaysAlone") }}
            <br class="desktop-break" />
            {{ t("ui.formDuetsClaimTheSpotlightAndMake") }}
          </p>
          <div class="product-actions"><button @click="tab='join';track('JOIN_GAME_CLICKED');">{{t('product.joinGame')}}</button><button @click="tab='create';requireHost();">{{t('product.createGame')}}</button></div>
          <div class="hero-proof">
            <span><Users :size="17" /> {{ t("ui.212Players") }} </span><i></i
            ><span> {{ t("ui.noSignUp") }} </span><i></i
            ><span> {{ t("ui.100Fun") }} </span>
          </div>
          <div class="party-art" aria-hidden="true">
            <div class="art-orbit"></div>
            <div class="doodle plus p1">+</div>
            <div class="doodle cross">×</div>
            <div class="doodle plus p2">+</div>
            <div class="speech-sticker">
              <span> {{ t("ui.iKnowThisOne") }} </span
              ><span class="sticker-line"></span>
            </div>
            <div class="mascot mascot-lime">
              <div class="mascot-eyes"><b></b><b></b></div>
              <div class="mascot-mouth"></div>
              <span class="mascot-cheek"></span>
            </div>
            <div class="mascot mascot-purple">
              <Zap :size="42" fill="currentColor" />
              <div class="mascot-eyes"><b></b><b></b></div>
            </div>
            <div class="score-sticker">
              <Trophy :size="18" /> +{{ formatNumber(1000) }}
              <span> {{ t("ui.pts") }} </span>
            </div>
            <div class="mini-sticker"><Gamepad2 :size="24" /></div>
            <div class="art-caption">
              {{ t("ui.greatAnswersEvenBetterStories") }}
            </div>
          </div>
        </section>
        <section class="play-panel" :aria-label="t('ui.startPlaying')">
          <div class="panel-top">
            <span class="small-badge"
              ><Radio :size="13" /> {{ t("ui.letSPlay") }} </span
            ><span class="panel-number">01 / PLAY</span>
          </div>
          <h2>
            {{ t("ui.theNextChallenge") }} <br />
            {{ t("ui.startsWithYou") }}
          </h2>
          <p class="muted">{{ t("ui.bringYourFriendsWeLlBringThe") }}</p>
          <div class="tabs" role="tablist">
            <button
              role="tab"
              :aria-selected="tab === 'create'"
              :class="{ selected: tab === 'create' }"
              @click="
                tab = 'create';
                error = '';
              "
            >
              {{ t("ui.createRoom") }}</button
            ><button
              role="tab"
              :aria-selected="tab === 'join'"
              :class="{ selected: tab === 'join' }"
              @click="
                tab = 'join';
                error = '';
              "
            >
              {{ t("ui.joinWithCode") }}
            </button>
          </div>
          <p v-if="invitedRoom()" class="invite-context">{{t('product.invited',{code:code})}}</p>
          <form @submit.prevent="tab === 'create' ? create() : join()">
            <label for="nickname">
              {{ t("ui.whatDoYourFriendsCallYou") }}
            </label>
            <div class="input-with-icon">
              <Users :size="18" /><input
                id="nickname"
                v-model="nickname"
                maxlength="24"
                autocomplete="nickname"
                :placeholder="t('ui.yourNickname')"
                required
                :disabled="busy"
              />
            </div>
            <template v-if="tab === 'create'">
              <label class="field-label"> {{ t("ui.whatSInTheMix") }} </label>
              <div class="category-options">
                <button
                  type="button"
                  :class="{ chosen: config.category === 'all' }"
                  @click="config.category = 'all'"
                >
                  <Sparkles :size="16" /> {{ t("ui.aBitOfEverything") }}</button
                ><button
                  type="button"
                  :class="{ chosen: config.category === 'cinema' }"
                  @click="config.category = 'cinema'"
                >
                  <Film :size="16" /> {{ t("ui.movies") }}</button
                ><button
                  type="button"
                  :class="{ chosen: config.category === 'geral' }"
                  @click="config.category = 'geral'"
                >
                  <Globe2 :size="16" /> {{ t("ui.general") }}
                </button>
              </div>
              <div class="content-settings">
                <label for="question-language"
                  >{{ t("language.questions") }}
                  <select
                    id="question-language"
                    v-model="config.questionLanguage"
                  >
                    <option value="pt-BR">
                      {{ t("language.portuguese") }}
                    </option>
                    <option value="en">{{ t("language.english") }}</option>
                  </select>
                </label>
                <label for="content-scope"
                  >{{ t("content.label") }}
                  <select id="content-scope" v-model="config.contentScope">
                    <option value="ALL">{{ t("content.ALL") }}</option>
                    <option value="GLOBAL">{{ t("content.GLOBAL") }}</option>
                    <option value="REGIONAL">
                      {{ t("content.REGIONAL") }}
                    </option>
                  </select>
                </label>
              </div>
              <p class="content-hint">{{ t("language.shared") }}</p>
              <p
                v-if="availableContent"
                class="catalog-count"
                aria-live="polite"
              >
                {{ t("content.inventory", availableContent.counts) }}
              </p>
              <p v-if="catalogUnavailable" class="content-hint">
                {{ t("content.unavailable") }}
              </p>
              <p v-if="contentShortage" class="form-error" role="status">
                {{ t("content.shortage", contentShortage) }}
              </p>
              <button
                class="customize"
                type="button"
                :aria-expanded="advanced"
                @click="advanced = !advanced"
              >
                <span
                  >{{ config.rounds }} {{ t("ui.rounds") }} <i>·</i>
                  {{ config.seconds }} {{ t("ui.sPerRound") }} <i>·</i>
                  {{
                    selectedMode === "mix" ? t("ui.4Modes") : t("ui.1Mode")
                  }}</span
                ><span
                  >{{ advanced ? t("ui.close") : t("ui.customize") }}
                  <ChevronRight :size="14"
                /></span>
              </button>
              <div class="game-flavor" :aria-label="t('ui.matchStyle')">
                <button
                  type="button"
                  :class="{ active: config.mosh }"
                  :aria-pressed="config.mosh"
                  @click="config.mosh = true"
                >
                  <Sparkles :size="16" /> Mosh Arena
                </button>
                <button
                  type="button"
                  :class="{ active: !config.mosh }"
                  :aria-pressed="!config.mosh"
                  @click="config.mosh = false"
                >
                  {{ t("ui.classicQuiz") }}
                </button>
              </div>
              <div v-if="advanced" class="advanced">
                <label>
                  {{ t("ui.rounds2") }}
                  <select v-model.number="config.rounds">
                    <option :value="4">{{ t("ui.4Rounds") }}</option>
                    <option :value="8">{{ t("ui.8Rounds") }}</option>
                    <option :value="12">{{ t("ui.12Rounds") }}</option>
                  </select></label
                ><label>
                  {{ t("ui.time") }}
                  <select v-model.number="config.seconds">
                    <option :value="15">{{ t("ui.15Seconds") }}</option>
                    <option :value="25">{{ t("ui.25Seconds") }}</option>
                    <option :value="40">{{ t("ui.40Seconds") }}</option>
                    <option :value="60">{{ t("ui.60Seconds") }}</option>
                  </select></label
                ><label class="span-two">
                  {{ t("ui.gameMode") }}
                  <select v-model="selectedMode">
                    <option value="mix">
                      {{ t("ui.fullMoshAllFourModes") }}
                    </option>
                    <option
                      v-for="mode in modes"
                      :key="mode.id"
                      :value="mode.id"
                    >
                      {{ mode.name }}
                    </option>
                  </select></label
                >
              </div>
            </template>
            <template v-else
              ><label for="room-code"> {{ t("ui.roomCode") }} </label
              ><input
                id="room-code"
                v-model="code"
                class="code-input"
                placeholder="ABCD"
                maxlength="8"
                minlength="4"
                autocapitalize="characters"
                autocomplete="off"
                required
              /><label for="join-role"> {{ t("ui.iWantToJoinAs") }} </label
              ><select id="join-role" v-model="role">
                <option value="PLAYER">{{ t("ui.player") }}</option>
                <option value="SPECTATOR">{{ t("ui.spectator") }}</option>
                <option value="DISPLAY">{{ t("ui.sharedScreen") }}</option>
              </select></template
            >
            <p v-if="error" class="form-error" role="alert">
              {{ t(error, errorArguments) }}
            </p>
            <button
              class="primary-button"
              type="submit"
              @click="tab === 'create' && !account ? ($event.preventDefault(), requireHost()) : undefined"
              :disabled="busy || (tab === 'create' && !!contentShortage)"
            >
              <LoaderCircle v-if="busy" class="spin" :size="20" /><span>{{
                busy
                  ? t("ui.gettingReady")
                  : tab === "create"
                    ? t(account ? "ui.createMyRoom" : "product.signIn")
                    : t("ui.joinRoom")
              }}</span
              ><ArrowRight :size="21" />
            </button>
          </form>
          <button
            v-if="tab === 'create'"
            class="practice-button"
            :disabled="busy || !!contentShortage"
            @click="create(true)"
          >
            <Gamepad2 :size="18" /> {{ t("ui.playingSolo") }}
            <strong> {{ t("ui.practiceWithBots") }} </strong
            ><ArrowRight :size="15" />
          </button>
          <div class="panel-foot">
            <span class="tiny-dot"></span>
            {{ t("ui.onYourComputerPhoneOrSharedScreen") }}
          </div>
        </section>
      </div>
      <section class="modes-section">
        <div class="section-heading">
          <h2>
            {{ t("ui.oneRoom") }}
            <span> {{ t("ui.fourWaysToShakeItUp") }} </span>
          </h2>
          <button class="text-button" @click="dialog = 'rules'">
            {{ t("ui.learnTheRules") }} <ArrowRight :size="16" />
          </button>
        </div>
        <div class="mode-grid">
          <button
            v-for="(mode, index) in modes"
            :key="mode.id"
            class="mode-card"
            :class="mode.color"
            @click="dialog = 'rules'"
          >
            <div class="mode-card-top">
              <span class="mode-icon"
                ><component :is="mode.icon" :size="24" /></span
              ><span class="mode-index">0{{ index + 1 }}</span>
            </div>
            <span class="mode-tag">{{ mode.tag }}</span>
            <h3>{{ mode.name }}</h3>
            <p>{{ mode.description }}</p>
            <ArrowRight class="mode-arrow" :size="19" />
          </button>
        </div>
      </section>
    </main>

    <main v-else class="game-main">
      <div class="game-breadcrumb">
        <span
          ><Gamepad2 :size="16" />
          {{
            state.phase === "LOBBY"
              ? t("ui.meetingPoint")
              : state.phase === "FINISHED"
                ? t("ui.matchOver")
                : t("ui.moshInProgress")
          }}</span
        ><button class="text-button" @click="dialog = 'rules'">
          <HelpCircle :size="16" /> {{ t("ui.rules") }}
        </button>
      </div>
      <div
        v-if="state.mosh && state.phase !== 'COUNTDOWN'"
        class="mosh-meter"
        :class="{ 'is-encore': state.mosh.encore }"
      >
        <span>{{
          state.mosh.encore ? t("ui.encoreRound") : t("ui.crowdEnergy")
        }}</span>
        <div
          class="mosh-meter-track"
          role="progressbar"
          :aria-valuenow="state.mosh.heat"
          :aria-valuemin="0"
          :aria-valuemax="100"
          :aria-label="t('ui.collectiveEnergy')"
        >
          <i :style="{ width: state.mosh.heat + '%' }"></i>
        </div>
        <b>{{ state.mosh.heat }}%</b
        ><small>{{
          state.mosh.encore
            ? t("ui.cardBonusesAndRisks2ThisRound")
            : state.mosh.heat >= 100
              ? t("ui.nextRoundEncoreWithCards2")
              : t("ui.yourTeamSCorrectAnswersChargeThe")
        }}</small>
      </div>
      <section v-if="state.phase === 'LOBBY'" class="lobby-grid">
        <div class="lobby-stage">
          <div class="eyebrow">
            <span class="live-dot"></span> {{ t("ui.almostEveryoneIsHere") }}
          </div>
          <h1>
            {{ t("ui.theMoshStarts") }} <br />
            {{ t("ui.with") }} <span> {{ t("ui.yourFriends") }} </span>
          </h1>
          <p class="muted">
            {{
              state.config.mosh
                ? t("ui.aStageToAnswerTakeRisksAnd")
                : t("ui.shareTheCodeAndGetYourBest")
            }}
          </p>
          <MoshArena v-if="state.config.mosh" :state="state" compact />
          <div class="big-code" :aria-label="t('ui.roomCode')">
            {{ state.code
            }}<button
              class="icon-button"
              :aria-label="t('ui.copyRoomLink')"
              @click="copyInvite"
            >
              <Copy :size="22" />
            </button>
          </div>
          <div class="lobby-content">
            <span
              ><Globe2 :size="15" />{{
                questionLanguageName(state.config.questionLanguage)
              }}</span
            >
            <span>{{
              t("content." + (state.config.contentScope || "ALL"))
            }}</span>
            <small>{{ t("language.shared") }}</small>
          </div>
          <div class="lobby-settings">
            <span
              ><Sparkles :size="16" />
              {{
                state.config.category === "all"
                  ? t("ui.aBitOfEverything")
                  : state.config.category === "cinema"
                    ? t("ui.movies")
                    : t("ui.generalKnowledge")
              }}</span
            ><span>{{ state.config.rounds }} {{ t("ui.rounds") }} </span
            ><span>{{ state.config.seconds }} {{ t("ui.seconds") }} </span>
          </div>
          <div class="lobby-invite">
            <img :src="qr" :alt="t('ui.qrCodeToJoinTheRoom')" />
            <div>
              <h3>{{ t("ui.scanJoinPlay") }}</h3>
              <p>{{ t("ui.whoWillChallengeYouForThePodium") }}</p>
              <button class="text-button" @click="dialog = 'invite'">
                <Link :size="16" /> {{ t("ui.shareInvite") }}</button
              ><a
                :href="displayLink"
                target="_blank"
                rel="noopener"
                class="text-button"
                ><Monitor :size="16" /> {{ t("ui.openSharedScreen") }}
              </a>
            </div>
          </div>
          <p v-if="localHost" class="local-note">
            {{ t("ui.toInviteFriendsOnTheirPhonesOpen") }}
          </p>
        </div>
        <div class="lobby-players">
          <div class="players-title">
            <h2>{{ t("ui.theCrew") }}</h2>
            <span>{{ playerList.length }} / 12</span>
          </div>
          <div class="player-list">
            <div
              v-for="(player, index) in playerList"
              :key="player.id"
              class="player-row"
            >
              <MoshAvatar
                v-if="state.config.mosh"
                class="score-avatar"
                :id="player.id"
              />
              <span v-else class="avatar" :class="'avatar-' + (index % 6)">{{
                player.nickname.slice(0, 2).toUpperCase()
              }}</span>
              <div>
                <strong
                  >{{ player.nickname }}
                  <span v-if="player.id === state.you.id" class="you-tag">
                    {{ t("ui.you") }}
                  </span></strong
                ><small>{{
                  player.bot
                    ? t("ui.practiceBot")
                    : player.owner
                      ? t("ui.host")
                      : player.online
                        ? t("ui.hereAndReady")
                        : t("ui.reconnecting")
                }}</small>
              </div>
              <Crown v-if="player.owner" class="crown" :size="19" /><Check
                v-else-if="player.online"
                :size="19"
                class="ready"
              />
            </div>
            <div v-if="playerList.length < 2" class="empty-player">
              <span>+</span> {{ t("ui.waitingForTheNextPlayer") }}
            </div>
          </div>
          <p
            class="spectator-count"
            v-if="state.players.length > playerList.length"
          >
            <Eye :size="14" />
            {{ state.players.length - playerList.length }}
            {{ t("ui.watching") }}
          </p>
          <button
            v-if="state.you.owner"
            class="primary-button"
            :disabled="busy || playerList.length < 2 || !connected"
            @click="start"
          >
            <Play :size="19" fill="currentColor" /> {{ t("ui.startTheMosh") }}
            <ArrowRight :size="20" />
          </button>
          <p v-else class="waiting-host">
            <LoaderCircle class="spin" :size="17" />
            {{ t("ui.waitingForTheHostToStart") }}
          </p>
          <p class="small-help">
            {{
              state.you.owner
                ? t("ui.weNeedAtLeast2Players")
                : t("ui.theMatchWillAppearHereAutomatically")
            }}
          </p>
        </div>
      </section>

      <section v-else-if="state.phase === 'COUNTDOWN'" class="countdown-stage">
        <div class="eyebrow">{{ t("ui.everyoneReady") }}</div>
        <h1>
          {{ t("ui.letThe") }} <br /><span> {{ t("ui.moshBegin") }} </span>
        </h1>
        <div class="countdown-number" :key="transition">
          {{ transition || t("ui.go") }}
        </div>
        <p>{{ state.config.rounds }} {{ t("ui.roundsToFindOutWhoRunsThe") }}</p>
      </section>

      <MoshBackstage
        v-else-if="state.phase === 'BACKSTAGE'"
        :key="state.mosh?.stageId"
        :state="state"
        :seconds="transition"
        :disabled="busy || !connected"
        @commit="commitTactic"
      />

      <section
        v-else-if="state.phase === 'ROUND' && state.round"
        class="round-grid"
      >
        <div class="question-area">
          <div class="round-topline">
            <span class="mode-pill" :class="activeMode.color"
              ><component :is="activeMode.icon" :size="17" />
              {{ activeMode.name }}</span
            ><span>
              {{ t("ui.round") }} <b>{{ state.round.number }}</b> /
              {{ state.config.rounds }}</span
            >
          </div>
          <div class="round-progress">
            <span
              :style="{
                width: `${(state.round.number / state.config.rounds) * 100}%`,
              }"
            ></span>
          </div>
          <div class="question-heading">
            <div>
              <span class="question-category">{{
                state.round.category === "cinema"
                  ? t("ui.lightsCameraGuess")
                  : t("ui.aBitOfEverything2")
              }}</span>
              <h1>{{ state.round.prompt }}</h1>
            </div>
            <div
              class="timer"
              :class="{ urgent: remaining <= 5 }"
              :style="{ '--progress': fraction + '%' }"
              role="timer"
              :aria-label="t('ui.secondsRemaining')"
            >
              <span
                >{{ remaining }}<small> {{ t("ui.sec") }} </small></span
              >
            </div>
          </div>
          <p class="round-rule">{{ activeMode.rule }}</p>
          <div v-if="ownTactic" class="own-tactic">
            <b>{{ cardInfo(ownTactic.card).mark }}</b
            ><span
              >{{ cardInfo(ownTactic.card).short
              }}<template v-if="ownTactic.target">
                {{ t("ui.with2") }}
                {{
                  state.players.find((p) => p.id === ownTactic?.target)
                    ?.nickname || t("ui.yourPartner")
                }}</template
              >{{
                state.mosh?.encore ? t("ui.encoreBonusesAndRisks2") : ""
              }}</span
            >
          </div>
          <MoshArena
            v-if="state.mosh"
            :state="state"
            :selected="platform"
            :can-answer="liveAnswerAllowed"
            :compact="state.round.type !== 'choice'"
            @select="selectPlatform"
            @confirm="submit(platform)"
          />
          <div v-if="state.round.type === 'guess'" class="clues">
            <div
              v-for="(clue, index) in state.round.clues"
              :key="index"
              class="clue"
            >
              <span>0{{ index + 1 }}</span>
              <p>{{ clue }}</p>
              <Sparkles
                v-if="index === state.round.clues.length - 1"
                :size="18"
              />
            </div>
            <div
              v-if="state.round.clues.length < state.round.clueCount"
              class="next-clue"
            >
              <LoaderCircle :size="14" class="spin" />
              {{ t("ui.theNextClueIsComing") }}
            </div>
          </div>
          <div
            v-if="state.round.type === 'choice' && !state.mosh"
            class="choices"
          >
            <button
              v-for="(choice, index) in state.round.choices"
              :key="choice.id"
              :disabled="!liveAnswerAllowed"
              class="choice"
              :class="[
                'choice-' + index,
                { picked: state.you.answer === choice.id },
              ]"
              @click="submit(choice.id)"
            >
              <span class="choice-key">{{ index + 1 }}</span
              ><strong>{{ choice.text }}</strong
              ><Check v-if="state.you.answer === choice.id" :size="23" />
            </button>
          </div>
          <form
            v-else-if="
              state.you.role === 'PLAYER' && state.round.type !== 'choice'
            "
            class="answer-form"
            @submit.prevent="submit()"
          >
            <label for="answer-field">{{
              state.round.type === "numeric"
                ? t("ui.yourBestGuess") + state.round.unit + ")"
                : t("ui.whatSYourAnswer")
            }}</label>
            <div
              v-if="state.mosh && state.round.type === 'numeric'"
              class="numeric-dial"
            >
              <button
                type="button"
                :disabled="!liveAnswerAllowed"
                :aria-label="t('ui.decreaseGuess')"
                @click="turnDial(-1)"
              >
                −
              </button>
              <select
                v-model.number="dialStep"
                :aria-label="t('ui.numericControlStep')"
                :disabled="!liveAnswerAllowed"
              >
                <option :value="1">{{ t("ui.step1") }}</option>
                <option :value="10">{{ t("ui.step10") }}</option>
                <option :value="100">{{ t("ui.step100") }}</option>
                <option :value="1000">{{ t("ui.step1000") }}</option>
                <option :value="0.1">{{ t("ui.step01") }}</option>
              </select>
              <button
                type="button"
                :disabled="!liveAnswerAllowed"
                :aria-label="t('ui.increaseGuess')"
                @click="turnDial(1)"
              >
                +
              </button>
            </div>
            <div>
              <input
                id="answer-field"
                v-model="answer"
                :disabled="!liveAnswerAllowed"
                :inputmode="state.round.type === 'numeric' ? 'decimal' : 'text'"
                :maxlength="state.round.type === 'numeric' ? 16 : 160"
                :placeholder="
                  state.round.type === 'numeric'
                    ? t('ui.enterANumber')
                    : t('ui.enterYourGuess')
                "
                autocomplete="off"
              /><button
                class="primary-button"
                :disabled="!liveAnswerAllowed || !answer.trim()"
              >
                <span> {{ t("ui.sendGuess") }} </span><ArrowRight :size="19" />
              </button>
            </div>
          </form>
          <div v-if="state.you.role !== 'PLAYER'" class="answer-status">
            <Monitor :size="20" /><span>
              {{ t("ui.watchTheGamePlayersAnswerOnTheir") }}
            </span>
          </div>
          <div v-else-if="state.you.locked" class="answer-status success">
            <Check :size="20" /><span>{{
              state.round.type === "guess"
                ? t("ui.correctNowWaitForYourFriends")
                : t("ui.answerLockedTheResultAppearsAtThe")
            }}</span>
          </div>
          <div v-else-if="state.you.submitted" class="answer-status">
            <Lightbulb :size="20" /><span>
              {{ t("ui.notThisTime100PointsTryAgain") }}
            </span>
          </div>
          <div class="question-foot">
            <span
              ><Users :size="16" /> {{ state.round.answeredCount }}
              {{ t("ui.of") }} {{ state.round.playerCount }}
              {{ t("ui.haveAnswered") }} </span
            ><span v-if="state.round.type === 'choice'"
              >{{ state.mosh ? t("ui.move") : t("ui.shortcuts") }} <kbd>1</kbd
              ><kbd>2</kbd><kbd>3</kbd><kbd>4</kbd
              ><template v-if="state.mosh">
                {{ t("ui.lock") }} <kbd>Enter</kbd></template
              ></span
            ><span v-else-if="state.round.type === 'guess'">
              {{ t("ui.clue") }} {{ state.round.clueIndex + 1 }}
              {{ t("ui.of") }} {{ state.round.clueCount }}</span
            >
          </div>
        </div>
        <aside class="scoreboard">
          <div class="players-title">
            <h2><Trophy :size="18" /> {{ t("ui.inTheGame") }}</h2>
            <span> {{ t("ui.pts") }} </span>
          </div>
          <TransitionGroup name="ranking" tag="div"
            ><div
              v-for="player in state.ranking"
              :key="player.id"
              class="score-row"
              :class="{
                isyou: player.id === state.you.id,
                'arena-score-row': !!state.mosh,
              }"
            >
              <span class="rank-number">{{ player.rank }}</span>
              <MoshAvatar
                v-if="state.mosh"
                class="score-avatar"
                :id="player.id"
              />
              <div>
                <strong>{{ player.nickname }}</strong
                ><small v-if="player.id === state.you.id">
                  {{ t("ui.youReInTheGame") }}
                </small>
              </div>
              <b>{{ player.score.toLocaleString(locale) }}</b>
            </div></TransitionGroup
          >
          <div class="scoreboard-bottom">
            <Sparkles :size="22" />
            <p>
              {{ t("ui.theNextAnswer") }} <br />
              {{ t("ui.couldChangeEverything") }}
            </p>
          </div>
        </aside>
      </section>

      <section
        v-else-if="state.phase === 'REVEAL' && state.reveal"
        class="results-grid"
      >
        <div class="reveal-card">
          <MoshArena v-if="state.mosh" :state="state" compact />
          <span class="small-badge">
            {{ t("ui.round") }} {{ state.reveal.number }} {{ t("ui.result") }}
          </span>
          <div class="reveal-check"><Check :size="36" /></div>
          <p class="muted">{{ state.reveal.prompt }}</p>
          <h1>{{ state.reveal.answer }}</h1>
          <p class="explanation">{{ state.reveal.explanation }}</p>
          <div v-if="ownBreakdown" class="mosh-breakdown">
            <strong>{{
              ownBreakdown.reasonKey
                ? t(ownBreakdown.reasonKey)
                : ownBreakdown.reason
            }}</strong>
            <p>
              {{ ownBreakdown.base.toLocaleString(locale) }}
              {{ t("ui.fromYourAnswer") }}
              {{ ownBreakdown.bonus >= 0 ? "+" : ""
              }}{{ ownBreakdown.bonus.toLocaleString(locale) }}
              {{ t("ui.fromYourCard") }} {{ cardInfo(ownBreakdown.card).name
              }}{{ state.mosh?.encore ? t("ui.encore2") : "" }}
            </p>
          </div>
          <div
            v-if="state.you.role === 'PLAYER'"
            class="delta"
            :class="{ negative: ownDelta < 0 }"
          >
            {{ ownDelta > 0 ? "+" : "" }}{{ ownDelta.toLocaleString(locale) }}
            <span> {{ t("ui.pointsForYou") }} </span>
          </div>
          <div class="next-round">
            <LoaderCircle class="spin" :size="17" /> {{ t("ui.nextRoundIn") }}
            {{ transition }} {{ t("ui.s") }}
            <button
              v-if="state.you.owner"
              class="text-button"
              :disabled="busy"
              @click="
                perform(async () =>
                  accept(await api(`/rooms/${state!.code}/next`, {}, 'POST')),
                )
              "
            >
              {{ t("ui.letSGo") }} <ArrowRight :size="17" />
            </button>
          </div>
        </div>
        <aside class="scoreboard reveal-scoreboard">
          <div class="players-title">
            <h2><Trophy :size="18" /> {{ t("ui.checkTheScores") }}</h2>
            <span> {{ t("ui.pts") }} </span>
          </div>
          <TransitionGroup name="ranking" tag="div"
            ><div
              v-for="player in state.ranking"
              :key="player.id"
              class="score-row"
              :class="{ isyou: player.id === state.you.id }"
            >
              <span class="rank-number">{{ player.rank }}</span>
              <div>
                <strong>{{ player.nickname }}</strong
                ><small class="gain"
                  >{{ (state.reveal.deltas[player.id] || 0) > 0 ? "+" : ""
                  }}{{ state.reveal.deltas[player.id] || 0 }}
                  {{ t("ui.thisRound") }}
                </small>
              </div>
              <b>{{ player.score.toLocaleString(locale) }}</b>
            </div></TransitionGroup
          >
        </aside>
      </section>

      <section v-else-if="state.phase === 'FINISHED'" class="final-stage">
        <MoshArena v-if="state.mosh" :state="state" compact />
        <div v-if="ownBreakdown" class="mosh-breakdown">
          <strong>
            {{ t("ui.lastRound") }}
            {{
              ownBreakdown.reasonKey
                ? t(ownBreakdown.reasonKey)
                : ownBreakdown.reason
            }}</strong
          >
          <p>
            {{ ownBreakdown.base.toLocaleString(locale) }}
            {{ t("ui.fromYourAnswer") }} {{ ownBreakdown.bonus >= 0 ? "+" : ""
            }}{{ ownBreakdown.bonus.toLocaleString(locale) }}
            {{ t("ui.fromYourCard") }}
            {{ state.mosh?.encore ? t("ui.encore2") : "" }}
          </p>
        </div>
        <div class="confetti" aria-hidden="true">
          <i v-for="n in 18" :key="n" :style="{ '--i': n }"></i>
        </div>
        <span class="small-badge"
          ><Sparkles :size="14" /> {{ t("ui.thatWasAGreatMosh") }} </span
        ><Trophy class="final-trophy" :size="52" />
        <h1>{{ winners }}</h1>
        <p class="winner-sub">
          {{
            state.ranking.filter((p) => p.rank === 1).length > 1
              ? t("ui.theyShareTheTopSpot")
              : t("ui.topOfThePodiumFirstInBragging")
          }}
        </p>
        <Sharing :code="state.code" :ranking="state.ranking" />
        <div class="final-ranking">
          <div
            v-for="player in state.ranking"
            :key="player.id"
            class="final-row"
            :class="{
              winner: player.rank === 1,
              isyou: player.id === state.you.id,
            }"
          >
            <span class="final-position"
              ><Crown v-if="player.rank === 1" :size="22" /><template v-else>{{
                player.rank
              }}</template></span
            ><strong
              >{{ player.nickname }}
              <small v-if="player.id === state.you.id">
                {{ t("ui.you") }}
              </small></strong
            ><b
              >{{ player.score.toLocaleString(locale) }}
              <small> {{ t("ui.pts2") }} </small></b
            >
          </div>
        </div>
        <div v-if="state.reveal" class="final-answer">
          <span> {{ t("ui.lastRound2") }} </span
          ><strong>{{ state.reveal.answer }}</strong>
          <p>{{ state.reveal.explanation }}</p>
        </div>
        <button
          v-if="state.you.owner"
          class="primary-button rematch"
          :disabled="busy || !connected || playerList.length < 2"
          @click="start"
        >
          <RotateCcw :size="19" /> {{ t("ui.rematchPlease") }}
          <ArrowRight :size="20" />
        </button>
        <p v-else class="waiting-host">{{ t("ui.theHostCanStartARematch") }}</p>
        <button class="text-button" @click="dialog = 'leave'">
          {{ t("ui.backToHome") }}
        </button>
      </section>
      <p v-if="error" class="form-error game-error" role="alert">
        {{ t(error, errorArguments) }}
      </p>
    </main>

    <section v-if="!state && !publicPage" class="product-steps"><article v-for="n in 4" :key="n"><b>0{{n}}</b><p>{{t('product.step'+n)}}</p></article></section>
    <ProductPanels />
    <nav class="product-links" :aria-label="t('product.legalLinks')"><a href="/how-to-play">{{t('ui.howItWorks')}}</a><a href="/privacy">{{t('product.privacy')}}</a><a href="/terms">{{t('product.terms')}}</a><a href="/cookies">{{t('product.cookies')}}</a></nav>
    <footer>
      <span
        ><span class="tiny-logo">✳</span>
        {{ t("ui.madeToBringPeopleTogether") }}
      </span>
      <div>
        <button @click="dialog = 'credits'">{{ t("ui.creditsAssets") }}</button
        ><i>·</i><span> {{ t("ui.quizmoshArenaBeta04") }} </span>
      </div>
    </footer>
    <Transition name="toast"
      ><div v-if="toast" class="toast" role="status">
        <Check :size="18" />{{ t(toast) }}
      </div></Transition
    >
    <div
      v-if="dialog"
      class="modal-backdrop"
      @click.self="dialog = ''"
      @keydown.esc="dialog = ''"
    >
      <section
        class="modal"
        role="dialog"
        aria-modal="true"
        :aria-label="
          dialog === 'rules'
            ? t('ui.howToPlay')
            : dialog === 'credits'
              ? t('ui.credits')
              : dialog === 'leave'
                ? t('ui.leaveRoom')
                : t('ui.inviteFriends')
        "
      >
        <button
          class="icon-button modal-close"
          :aria-label="t('ui.close')"
          autofocus
          @click="dialog = ''"
        >
          <X :size="22" />
        </button>
        <template v-if="dialog === 'rules'"
          ><span class="eyebrow"> {{ t("ui.theMoshGuide") }} </span>
          <h2>
            {{ t("ui.joinIn") }} <br />
            {{ t("ui.playYourWay") }}
          </h2>
          <p class="muted">{{ t("ui.createARoomInviteYourFriendsWith") }}</p>
          <div class="mosh-rules">
            <p>
              <strong>Mosh Arena:</strong>
              {{ t("ui.chooseACardBackstageBeforeSeeingThe") }}
            </p>
            <p v-for="card in cards" :key="card.id">
              <strong
                >{{ card.mark }} {{ card.name }} ({{ card.cost }}):</strong
              >
              {{ card.description }}
            </p>
            <p>
              <strong> {{ t("ui.encore") }} </strong>
              {{ t("ui.correctAnswersChargeCollectiveEnergyUpTo") }}
            </p>
            <p>
              <strong> {{ t("ui.controlYourCharacter") }} </strong>
              {{ t("ui.clickAPlatformOrUse14Arrow") }}
            </p>
          </div>
          <div v-for="mode in modes" :key="mode.id" class="rule-row">
            <span class="mode-icon" :class="mode.color"
              ><component :is="mode.icon" :size="22"
            /></span>
            <div>
              <h3>{{ mode.name }}</h3>
              <p>{{ mode.rule }}</p>
            </div>
          </div>
          <p class="small-help">
            {{ t("ui.timeUpNoPointsForYourAnswer") }}
          </p></template
        >
        <template v-else-if="dialog === 'credits'"
          ><span class="eyebrow"> {{ t("ui.thePeopleBehindIt") }} </span>
          <h2>{{ t("ui.creditsAssets") }}</h2>
          <p class="muted">{{ t("ui.theGameSAssetsAreFreeAnd") }}</p>
          <div class="credit-row">
            <strong>Kenney · Interface Sounds</strong
            ><span> {{ t("ui.soundEffectsCc0") }} </span
            ><a href="/licenses/kenney.txt" target="_blank" rel="noopener">
              {{ t("ui.viewLicense") }} <ArrowRight :size="14"
            /></a>
          </div>
          <div class="credit-row">
            <strong>Lucide</strong><span> {{ t("ui.iconsIscMit") }} </span
            ><a href="/licenses/lucide.txt" target="_blank" rel="noopener">
              {{ t("ui.viewLicense") }} <ArrowRight :size="14"
            /></a>
          </div>
          <div class="credit-row">
            <strong>Outfit</strong
            ><span> {{ t("ui.typographySilOpenFontLicense") }} </span
            ><a href="/licenses/outfit.txt" target="_blank" rel="noopener">
              {{ t("ui.viewLicense") }} <ArrowRight :size="14"
            /></a>
          </div>
          <p class="small-help">
            {{ t("ui.interfaceIllustrationsAndQuestionTextCreatedFor") }}
          </p></template
        >
        <template v-else-if="dialog === 'invite'"
          ><span class="eyebrow"> {{ t("ui.bringYourFriends") }} </span>
          <h2>{{ t("ui.thereSRoomInTheMosh") }}</h2>
          <Sharing v-if="state" :code="state.code" />
          <img class="invite-qr" :src="qr" :alt="t('ui.inviteQrCode')" />
          <p class="invite-code">{{ state?.code }}</p>
          <label for="invite-link"> {{ t("ui.roomLink") }} </label
          ><input
            id="invite-link"
            :value="invite"
            readonly
            @focus="($event.target as HTMLInputElement).select()"
          /><button class="primary-button" @click="copyInvite">
            <Copy :size="18" /> {{ t("ui.copyInvite") }}
          </button>
          <p v-if="localHost" class="small-help">
            {{ t("ui.thisAddressWorksOnThisComputerTo") }}
          </p></template
        >
        <template v-else-if="dialog === 'leave'"
          ><span class="eyebrow"> {{ t("ui.untilTheNextMosh") }} </span>
          <h2>{{ t("ui.leaveTheRoom") }}</h2>
          <p class="muted">{{ t("ui.youWillLeaveThisMatchIfYou") }}</p>
          <p v-if="error" class="form-error" role="alert">
            {{ t(error, errorArguments) }}
          </p>
          <div class="leave-actions">
            <button class="secondary-button" @click="dialog = ''">
              {{ t("ui.keepPlaying") }}</button
            ><button class="primary-button" :disabled="busy" @click="leave">
              {{ t("ui.leaveRoom") }} <LogOut :size="18" />
            </button></div
        ></template>
      </section>
    </div>
  </div>
</template>
