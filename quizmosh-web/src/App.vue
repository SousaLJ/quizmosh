<script setup lang="ts">
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
import type { Config, Tactic } from "./types";
import MoshArena from "./MoshArena.vue";
import MoshAvatar from "./MoshAvatar.vue";
import MoshBackstage from "./MoshBackstage.vue";
import { cardInfo, cards } from "./mosh";

const modes = [
  {
    id: "classic-trivia",
    name: "Na mosca",
    tag: "TRIVIA CLÁSSICA",
    description: "Quatro opções. Uma certeza. Mostre que você sabe.",
    icon: Brain,
    color: "purple",
    rule: "Uma resposta por rodada. Cada acerto vale 1.000 pontos.",
  },
  {
    id: "quick-fire",
    name: "Bate-pronto",
    tag: "VELOCIDADE",
    description: "Pensou, clicou. Aqui, cada segundo vale pontos.",
    icon: Zap,
    color: "orange",
    rule: "Uma resposta por rodada. Acerte para ganhar de 500 a 1.000 pontos: quanto mais rápido, melhor.",
  },
  {
    id: "guess-it",
    name: "Qual é a boa?",
    tag: "PISTAS E PALPITES",
    description: "Conecte as pistas e descubra antes da turma.",
    icon: Lightbulb,
    color: "green",
    rule: "Uma tentativa por pista. Acerto vale 1.000, 800, 600 ou 400 pontos conforme a pista; cada erro tira 100. Acentos e maiúsculas não atrapalham.",
  },
  {
    id: "closest-wins",
    name: "Quase lá",
    tag: "APROXIMAÇÃO",
    description: "Não precisa cravar. Chegar mais perto já conta.",
    icon: Target,
    color: "pink",
    rule: "Os mais próximos recebem 1.000, 600, 300 e 100 pontos. Empates recebem a mesma posição; acerto exato soma 200.",
  },
];
const tab = ref(
  new URLSearchParams(location.search).has("room") ? "join" : "create",
);
const nickname = ref(localStorage.getItem("quizmosh-name") || "");
const code = ref(new URLSearchParams(location.search).get("room") || "");
const role = ref(
  new URLSearchParams(location.search).has("display") ? "DISPLAY" : "PLAYER",
);
const config = ref<Config>({
  rounds: 8,
  seconds: 25,
  category: "all",
  modes: modes.map((m) => m.id),
  mosh: true,
});
const selectedMode = ref("mix");
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
  () => modes.find((m) => m.id === state.value?.round?.mode) || modes[0],
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
const invite = computed(
  () => `${location.origin}/?room=${state.value?.code || ""}`,
);
const displayLink = computed(() => invite.value + "&display=1");
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
      e instanceof Error ? e.message : "Algo deu errado. Tente novamente.";
    play("error");
  } finally {
    busy.value = false;
  }
}
async function create(practice = false) {
  if (!nickname.value.trim()) {
    error.value = "Escolha um apelido para começar.";
    document.getElementById("nickname")?.focus();
    return;
  }
  await perform(async () => {
    config.value.modes =
      selectedMode.value === "mix"
        ? modes.map((m) => m.id)
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
            "answer.already_submitted": "Você já respondeu esta rodada.",
            "answer.wait_for_next_clue": "Aguarde a próxima pista.",
            "answer.player_locked": "Você já acertou!",
          } as Record<string, string>
        )[data.receipt.messageKey] ||
        "Resposta não aceita. Confira e tente novamente.";
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
    history.replaceState({}, "", location.pathname);
  });
}
async function copy(text: string) {
  try {
    await navigator.clipboard.writeText(text);
    notify("Convite copiado!");
  } catch {
    dialog.value = "invite";
  }
}
async function fullscreen() {
  try {
    if (document.fullscreenElement) await document.exitFullscreen();
    else await document.documentElement.requestFullscreen();
  } catch {
    notify("Use F11 para alternar a tela cheia.");
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
        @click.prevent="state ? (dialog = 'leave') : undefined"
        aria-label="QuizMosh, início"
        ><span class="brand-mark"><Zap :size="24" fill="currentColor" /></span
        >quiz<span>mosh</span><sup>β</sup></a
      >
      <nav v-if="!state" class="top-nav" aria-label="Navegação principal">
        <span class="active-nav">Jogar</span
        ><button @click="dialog = 'rules'">Como funciona</button
        ><button @click="dialog = 'credits'">Créditos</button>
      </nav>
      <div v-else class="room-chip">
        <span class="live-dot" :class="{ offline: !connected }"></span
        ><span>SALA</span><strong>{{ state.code }}</strong
        ><button
          class="icon-button"
          aria-label="Copiar convite"
          @click="copy(invite)"
        >
          <Copy :size="16" />
        </button>
      </div>
      <div class="header-actions">
        <span v-if="state" class="connection-text">{{ connectionMode }}</span
        ><button
          class="icon-button"
          :aria-label="sound ? 'Desativar som' : 'Ativar som'"
          :aria-pressed="sound"
          @click="toggleSound"
        >
          <Volume2 v-if="sound" :size="20" /><VolumeX
            v-else
            :size="20"
          /></button
        ><button
          class="icon-button"
          aria-label="Tela cheia"
          @click="fullscreen"
        >
          <Maximize2 :size="19" /></button
        ><button
          v-if="state"
          class="icon-button"
          aria-label="Sair da sala"
          @click="dialog = 'leave'"
        >
          <LogOut :size="19" />
        </button>
      </div>
    </header>

    <div v-if="failure" class="network-banner" role="status">{{ failure }}</div>
    <main v-if="!state" class="home">
      <div class="hero-grid">
        <section class="hero-copy">
          <div class="eyebrow">
            <span class="live-dot"></span> O ROLÊ AGORA É AQUI
          </div>
          <h1>
            A turma reunida.<br />O caos <span>garantido.</span
            ><Sparkles class="headline-star" :size="36" />
          </h1>
          <p class="hero-description">
            Um palco. Sua turma. Ninguém joga sozinho.<br
              class="desktop-break"
            />
            Forme duetos, dispute o holofote e faça a plateia pedir BIS.
          </p>
          <div class="hero-proof">
            <span><Users :size="17" /> 2–12 jogadores</span><i></i
            ><span>Sem cadastro</span><i></i><span>100% diversão</span>
          </div>
          <div class="party-art" aria-hidden="true">
            <div class="art-orbit"></div>
            <div class="doodle plus p1">+</div>
            <div class="doodle cross">×</div>
            <div class="doodle plus p2">+</div>
            <div class="speech-sticker">
              <span>EU SEI ESSA!</span><span class="sticker-line"></span>
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
              <Trophy :size="18" /> +1.000 <span>PTS</span>
            </div>
            <div class="mini-sticker"><Gamepad2 :size="24" /></div>
            <div class="art-caption">BOAS RESPOSTAS. MELHORES HISTÓRIAS.</div>
          </div>
        </section>
        <section class="play-panel" aria-label="Começar a jogar">
          <div class="panel-top">
            <span class="small-badge"><Radio :size="13" /> BORA JOGAR</span
            ><span class="panel-number">01 / PLAY</span>
          </div>
          <h2>O próximo desafio<br />começa com você.</h2>
          <p class="muted">Chame a turma. A gente cuida das perguntas.</p>
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
              Criar sala</button
            ><button
              role="tab"
              :aria-selected="tab === 'join'"
              :class="{ selected: tab === 'join' }"
              @click="
                tab = 'join';
                error = '';
              "
            >
              Entrar com código
            </button>
          </div>
          <form @submit.prevent="tab === 'create' ? create() : join()">
            <label for="nickname">Como a turma te chama?</label>
            <div class="input-with-icon">
              <Users :size="18" /><input
                id="nickname"
                v-model="nickname"
                maxlength="24"
                autocomplete="nickname"
                placeholder="Seu apelido"
                required
                :disabled="busy"
              />
            </div>
            <template v-if="tab === 'create'">
              <label class="field-label">Qual vai ser a mistura?</label>
              <div class="category-options">
                <button
                  type="button"
                  :class="{ chosen: config.category === 'all' }"
                  @click="config.category = 'all'"
                >
                  <Sparkles :size="16" /> Tudo junto</button
                ><button
                  type="button"
                  :class="{ chosen: config.category === 'cinema' }"
                  @click="config.category = 'cinema'"
                >
                  <Film :size="16" /> Cinema</button
                ><button
                  type="button"
                  :class="{ chosen: config.category === 'geral' }"
                  @click="config.category = 'geral'"
                >
                  <Globe2 :size="16" /> Geral
                </button>
              </div>
              <button
                class="customize"
                type="button"
                :aria-expanded="advanced"
                @click="advanced = !advanced"
              >
                <span
                  >{{ config.rounds }} rodadas <i>·</i> {{ config.seconds }}s
                  por rodada <i>·</i>
                  {{ selectedMode === "mix" ? "4 modos" : "1 modo" }}</span
                ><span
                  >{{ advanced ? "Fechar" : "Ajustar" }}
                  <ChevronRight :size="14"
                /></span>
              </button>
              <div class="game-flavor" aria-label="Estilo da partida">
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
                  Quiz clássico
                </button>
              </div>
              <div v-if="advanced" class="advanced">
                <label
                  >Rodadas<select v-model.number="config.rounds">
                    <option :value="4">4 rodadas</option>
                    <option :value="8">8 rodadas</option>
                    <option :value="12">12 rodadas</option>
                  </select></label
                ><label
                  >Tempo<select v-model.number="config.seconds">
                    <option :value="15">15 segundos</option>
                    <option :value="25">25 segundos</option>
                    <option :value="40">40 segundos</option>
                    <option :value="60">60 segundos</option>
                  </select></label
                ><label class="span-two"
                  >Modo de jogo<select v-model="selectedMode">
                    <option value="mix">Mosh completo · os quatro modos</option>
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
              ><label for="room-code">Código da sala</label
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
              /><label for="join-role">Quero participar como</label
              ><select id="join-role" v-model="role">
                <option value="PLAYER">Jogador</option>
                <option value="SPECTATOR">Espectador</option>
                <option value="DISPLAY">Tela coletiva</option>
              </select></template
            >
            <p v-if="error" class="form-error" role="alert">{{ error }}</p>
            <button class="primary-button" type="submit" :disabled="busy">
              <LoaderCircle v-if="busy" class="spin" :size="20" /><span>{{
                busy
                  ? "Preparando…"
                  : tab === "create"
                    ? "Criar minha sala"
                    : "Entrar na sala"
              }}</span
              ><ArrowRight :size="21" />
            </button>
          </form>
          <button
            v-if="tab === 'create'"
            class="practice-button"
            :disabled="busy"
            @click="create(true)"
          >
            <Gamepad2 :size="18" /> Só você por aí?
            <strong>Treine com bots</strong><ArrowRight :size="15" />
          </button>
          <div class="panel-foot">
            <span class="tiny-dot"></span> No computador, no celular ou na tela
            da sala.
          </div>
        </section>
      </div>
      <section class="modes-section">
        <div class="section-heading">
          <h2>Uma sala. <span>Quatro jeitos de causar.</span></h2>
          <button class="text-button" @click="dialog = 'rules'">
            Conheça as regras <ArrowRight :size="16" />
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
              ? "PONTO DE ENCONTRO"
              : state.phase === "FINISHED"
                ? "FIM DE PARTIDA"
                : "MOSH EM ANDAMENTO"
          }}</span
        ><button class="text-button" @click="dialog = 'rules'">
          <HelpCircle :size="16" /> Regras
        </button>
      </div>
      <div
        v-if="state.mosh && state.phase !== 'COUNTDOWN'"
        class="mosh-meter"
        :class="{ 'is-encore': state.mosh.encore }"
      >
        <span>{{
          state.mosh.encore ? "✦ RODADA BIS" : "✦ ENERGIA DA PLATEIA"
        }}</span>
        <div
          class="mosh-meter-track"
          role="progressbar"
          :aria-valuenow="state.mosh.heat"
          :aria-valuemin="0"
          :aria-valuemax="100"
          aria-label="Energia coletiva"
        >
          <i :style="{ width: state.mosh.heat + '%' }"></i>
        </div>
        <b>{{ state.mosh.heat }}%</b
        ><small>{{
          state.mosh.encore
            ? "Bônus e risco das cartas ×2 nesta rodada"
            : state.mosh.heat >= 100
              ? "Próxima rodada: BIS com cartas ×2!"
              : "Os acertos da turma carregam o próximo BIS"
        }}</small>
      </div>
      <section v-if="state.phase === 'LOBBY'" class="lobby-grid">
        <div class="lobby-stage">
          <div class="eyebrow">
            <span class="live-dot"></span> TÁ QUASE TODO MUNDO AQUI
          </div>
          <h1>O mosh começa<br />com a <span>sua turma.</span></h1>
          <p class="muted">
            {{
              state.config.mosh
                ? "Um palco para acertar, arriscar e jogar junto."
                : "Compartilhe o código e prepare seus melhores palpites."
            }}
          </p>
          <MoshArena v-if="state.config.mosh" :state="state" compact />
          <div class="big-code" aria-label="Código da sala">
            {{ state.code
            }}<button
              class="icon-button"
              aria-label="Copiar link da sala"
              @click="copy(invite)"
            >
              <Copy :size="22" />
            </button>
          </div>
          <div class="lobby-settings">
            <span
              ><Sparkles :size="16" />
              {{
                state.config.category === "all"
                  ? "Tudo junto"
                  : state.config.category === "cinema"
                    ? "Cinema"
                    : "Conhecimentos gerais"
              }}</span
            ><span>{{ state.config.rounds }} rodadas</span
            ><span>{{ state.config.seconds }} segundos</span>
          </div>
          <div class="lobby-invite">
            <img :src="qr" alt="QR code para entrar na sala" />
            <div>
              <h3>Escaneou. Entrou. Jogou.</h3>
              <p>Quem vai disputar esse pódio com você?</p>
              <button class="text-button" @click="dialog = 'invite'">
                <Link :size="16" /> Compartilhar convite</button
              ><a
                :href="displayLink"
                target="_blank"
                rel="noopener"
                class="text-button"
                ><Monitor :size="16" /> Abrir tela coletiva</a
              >
            </div>
          </div>
          <p v-if="localHost" class="local-note">
            Para convidar pelo celular, abra o jogo pelo IP do PC na mesma rede.
            Veja o guia incluído no projeto.
          </p>
        </div>
        <div class="lobby-players">
          <div class="players-title">
            <h2>A turma</h2>
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
                  <span v-if="player.id === state.you.id" class="you-tag"
                    >você</span
                  ></strong
                ><small>{{
                  player.bot
                    ? "Bot de treino"
                    : player.owner
                      ? "Anfitrião"
                      : player.online
                        ? "Na área"
                        : "Reconectando"
                }}</small>
              </div>
              <Crown v-if="player.owner" class="crown" :size="19" /><Check
                v-else-if="player.online"
                :size="19"
                class="ready"
              />
            </div>
            <div v-if="playerList.length < 2" class="empty-player">
              <span>+</span> Esperando o próximo jogador…
            </div>
          </div>
          <p
            class="spectator-count"
            v-if="state.players.length > playerList.length"
          >
            <Eye :size="14" />
            {{ state.players.length - playerList.length }} acompanhando
          </p>
          <button
            v-if="state.you.owner"
            class="primary-button"
            :disabled="busy || playerList.length < 2 || !connected"
            @click="start"
          >
            <Play :size="19" fill="currentColor" /> Começar o mosh
            <ArrowRight :size="20" />
          </button>
          <p v-else class="waiting-host">
            <LoaderCircle class="spin" :size="17" /> Esperando o anfitrião
            começar
          </p>
          <p class="small-help">
            {{
              state.you.owner
                ? "Precisamos de pelo menos 2 jogadores."
                : "A partida vai aparecer aqui automaticamente."
            }}
          </p>
        </div>
      </section>

      <section v-else-if="state.phase === 'COUNTDOWN'" class="countdown-stage">
        <div class="eyebrow">TODO MUNDO PRONTO?</div>
        <h1>Que comece<br /><span>o mosh.</span></h1>
        <div class="countdown-number" :key="transition">
          {{ transition || "VAI!" }}
        </div>
        <p>
          {{ state.config.rounds }} rodadas para descobrir quem manda na turma.
        </p>
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
            ><span
              >RODADA <b>{{ state.round.number }}</b> /
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
                  ? "LUZ, CÂMERA, PALPITE"
                  : "UM POUCO DE TUDO"
              }}</span>
              <h1>{{ state.round.prompt }}</h1>
            </div>
            <div
              class="timer"
              :class="{ urgent: remaining <= 5 }"
              :style="{ '--progress': fraction + '%' }"
              role="timer"
              aria-label="Segundos restantes"
            >
              <span>{{ remaining }}<small>SEG</small></span>
            </div>
          </div>
          <p class="round-rule">{{ activeMode.rule }}</p>
          <div v-if="ownTactic" class="own-tactic">
            <b>{{ cardInfo(ownTactic.card).mark }}</b
            ><span
              >{{ cardInfo(ownTactic.card).short
              }}<template v-if="ownTactic.target">
                com
                {{
                  state.players.find((p) => p.id === ownTactic?.target)
                    ?.nickname || "seu parceiro"
                }}</template
              >{{ state.mosh?.encore ? " · BIS: bônus e risco ×2" : "" }}</span
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
              <LoaderCircle :size="14" class="spin" /> A próxima pista vem aí…
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
                ? "Seu melhor palpite (" + state.round.unit + ")"
                : "Qual é a sua resposta?"
            }}</label>
            <div
              v-if="state.mosh && state.round.type === 'numeric'"
              class="numeric-dial"
            >
              <button
                type="button"
                :disabled="!liveAnswerAllowed"
                aria-label="Diminuir palpite"
                @click="turnDial(-1)"
              >
                −
              </button>
              <select
                v-model.number="dialStep"
                aria-label="Passo do controle numérico"
                :disabled="!liveAnswerAllowed"
              >
                <option :value="1">Passo: 1</option>
                <option :value="10">Passo: 10</option>
                <option :value="100">Passo: 100</option>
                <option :value="1000">Passo: 1.000</option>
                <option :value="0.1">Passo: 0,1</option>
              </select>
              <button
                type="button"
                :disabled="!liveAnswerAllowed"
                aria-label="Aumentar palpite"
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
                    ? 'Digite um número'
                    : 'Digite seu palpite'
                "
                autocomplete="off"
              /><button
                class="primary-button"
                :disabled="!liveAnswerAllowed || !answer.trim()"
              >
                <span>Mandar palpite</span><ArrowRight :size="19" />
              </button>
            </div>
          </form>
          <div v-if="state.you.role !== 'PLAYER'" class="answer-status">
            <Monitor :size="20" /><span
              >Acompanhe a disputa. Os jogadores respondem nos próprios
              dispositivos.</span
            >
          </div>
          <div v-else-if="state.you.locked" class="answer-status success">
            <Check :size="20" /><span>{{
              state.round.type === "guess"
                ? "Acertou! Agora é só esperar a turma."
                : "Resposta enviada! O resultado vem no fim da rodada."
            }}</span>
          </div>
          <div v-else-if="state.you.submitted" class="answer-status">
            <Lightbulb :size="20" /><span
              >Ainda não foi dessa vez. −100 pontos. Tente com a próxima
              pista.</span
            >
          </div>
          <div class="question-foot">
            <span
              ><Users :size="16" /> {{ state.round.answeredCount }} de
              {{ state.round.playerCount }} já responderam</span
            ><span v-if="state.round.type === 'choice'"
              >{{ state.mosh ? "Mover" : "Atalhos" }} <kbd>1</kbd><kbd>2</kbd
              ><kbd>3</kbd><kbd>4</kbd
              ><template v-if="state.mosh">
                · Travar <kbd>Enter</kbd></template
              ></span
            ><span v-else-if="state.round.type === 'guess'"
              >Pista {{ state.round.clueIndex + 1 }} de
              {{ state.round.clueCount }}</span
            >
          </div>
        </div>
        <aside class="scoreboard">
          <div class="players-title">
            <h2><Trophy :size="18" /> Na disputa</h2>
            <span>PTS</span>
          </div>
          <TransitionGroup name="ranking" tag="div"
            ><div
              v-for="player in state.ranking"
              :key="player.id"
              class="score-row"
              :class="{ isyou: player.id === state.you.id }"
            >
              <span class="rank-number">{{ player.rank }}</span>
              <MoshAvatar
                v-if="state.mosh"
                class="score-avatar"
                :id="player.id"
              />
              <div>
                <strong>{{ player.nickname }}</strong
                ><small v-if="player.id === state.you.id"
                  >Você tá no jogo</small
                >
              </div>
              <b>{{ player.score.toLocaleString("pt-BR") }}</b>
            </div></TransitionGroup
          >
          <div class="scoreboard-bottom">
            <Sparkles :size="22" />
            <p>A próxima resposta<br />pode mudar tudo.</p>
          </div>
        </aside>
      </section>

      <section
        v-else-if="state.phase === 'REVEAL' && state.reveal"
        class="results-grid"
      >
        <div class="reveal-card">
          <MoshArena v-if="state.mosh" :state="state" compact />
          <span class="small-badge"
            >RODADA {{ state.reveal.number }} · RESULTADO</span
          >
          <div class="reveal-check"><Check :size="36" /></div>
          <p class="muted">{{ state.reveal.prompt }}</p>
          <h1>{{ state.reveal.answer }}</h1>
          <p class="explanation">{{ state.reveal.explanation }}</p>
          <div v-if="ownBreakdown" class="mosh-breakdown">
            <strong>{{ ownBreakdown.reason }}</strong>
            <p>
              {{ ownBreakdown.base.toLocaleString("pt-BR") }} da resposta ·
              {{ ownBreakdown.bonus >= 0 ? "+" : ""
              }}{{ ownBreakdown.bonus.toLocaleString("pt-BR") }} da carta
              {{ cardInfo(ownBreakdown.card).name
              }}{{ state.mosh?.encore ? " (BIS ×2)" : "" }}
            </p>
          </div>
          <div
            v-if="state.you.role === 'PLAYER'"
            class="delta"
            :class="{ negative: ownDelta < 0 }"
          >
            {{ ownDelta > 0 ? "+" : "" }}{{ ownDelta.toLocaleString("pt-BR") }}
            <span>pontos para você</span>
          </div>
          <div class="next-round">
            <LoaderCircle class="spin" :size="17" /> Próxima rodada em
            {{ transition }}s<button
              v-if="state.you.owner"
              class="text-button"
              :disabled="busy"
              @click="
                perform(async () =>
                  accept(await api(`/rooms/${state!.code}/next`, {}, 'POST')),
                )
              "
            >
              Bora! <ArrowRight :size="17" />
            </button>
          </div>
        </div>
        <aside class="scoreboard reveal-scoreboard">
          <div class="players-title">
            <h2><Trophy :size="18" /> Olha o placar</h2>
            <span>PTS</span>
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
                  }}{{ state.reveal.deltas[player.id] || 0 }} nesta
                  rodada</small
                >
              </div>
              <b>{{ player.score.toLocaleString("pt-BR") }}</b>
            </div></TransitionGroup
          >
        </aside>
      </section>

      <section v-else-if="state.phase === 'FINISHED'" class="final-stage">
        <MoshArena v-if="state.mosh" :state="state" compact />
        <div v-if="ownBreakdown" class="mosh-breakdown">
          <strong>Última rodada · {{ ownBreakdown.reason }}</strong>
          <p>
            {{ ownBreakdown.base.toLocaleString("pt-BR") }} da resposta ·
            {{ ownBreakdown.bonus >= 0 ? "+" : ""
            }}{{ ownBreakdown.bonus.toLocaleString("pt-BR") }} da carta{{
              state.mosh?.encore ? " (BIS ×2)" : ""
            }}
          </p>
        </div>
        <div class="confetti" aria-hidden="true">
          <i v-for="n in 18" :key="n" :style="{ '--i': n }"></i>
        </div>
        <span class="small-badge"
          ><Sparkles :size="14" /> ESSE MOSH FOI BOM!</span
        ><Trophy class="final-trophy" :size="52" />
        <h1>{{ winners }}</h1>
        <p class="winner-sub">
          {{
            state.ranking.filter((p) => p.rank === 1).length > 1
              ? "Dividiram o topo do pódio!"
              : "No topo do pódio. E das provocações."
          }}
        </p>
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
              <small v-if="player.id === state.you.id">você</small></strong
            ><b
              >{{ player.score.toLocaleString("pt-BR") }} <small>pts</small></b
            >
          </div>
        </div>
        <div v-if="state.reveal" class="final-answer">
          <span>ÚLTIMA RODADA</span><strong>{{ state.reveal.answer }}</strong>
          <p>{{ state.reveal.explanation }}</p>
        </div>
        <button
          v-if="state.you.owner"
          class="primary-button rematch"
          :disabled="busy || !connected || playerList.length < 2"
          @click="start"
        >
          <RotateCcw :size="19" /> Quero revanche <ArrowRight :size="20" />
        </button>
        <p v-else class="waiting-host">
          O anfitrião pode começar uma revanche.
        </p>
        <button class="text-button" @click="dialog = 'leave'">
          Voltar ao início
        </button>
      </section>
      <p v-if="error" class="form-error game-error" role="alert">{{ error }}</p>
    </main>

    <footer>
      <span><span class="tiny-logo">✳</span> FEITO PARA JUNTAR GENTE.</span>
      <div>
        <button @click="dialog = 'credits'">Créditos & assets</button><i>·</i
        ><span>QuizMosh Arena beta 0.3</span>
      </div>
    </footer>
    <Transition name="toast"
      ><div v-if="toast" class="toast" role="status">
        <Check :size="18" />{{ toast }}
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
            ? 'Como jogar'
            : dialog === 'credits'
              ? 'Créditos'
              : dialog === 'leave'
                ? 'Sair da sala'
                : 'Convidar amigos'
        "
      >
        <button
          class="icon-button modal-close"
          aria-label="Fechar"
          autofocus
          @click="dialog = ''"
        >
          <X :size="22" />
        </button>
        <template v-if="dialog === 'rules'"
          ><span class="eyebrow">O GUIA DO MOSH</span>
          <h2>Chega junto.<br />Joga do seu jeito.</h2>
          <p class="muted">
            Crie uma sala, convide a turma pelo código e deixe o anfitrião dar a
            largada. As rodadas avançam automaticamente.
          </p>
          <div class="mosh-rules">
            <p>
              <strong>Mosh Arena:</strong> escolha uma carta nos bastidores
              antes de conhecer a pergunta. Comece com 3 batidas; acerto devolve
              1, erro devolve 2, até 5. Uma escolha confirmada não muda. Sem
              escolha, vale “Na minha”.
            </p>
            <p v-for="card in cards" :key="card.id">
              <strong
                >{{ card.mark }} {{ card.name }} ({{ card.cost }}):</strong
              >
              {{ card.description }}
            </p>
            <p>
              <strong>BIS:</strong> os acertos carregam a energia coletiva (até
              40% por rodada). Ao chegar a 100%, a próxima rodada dobra apenas
              os bônus e as perdas das cartas. No Quase lá, as duas primeiras
              posições, incluindo empates, contam como acerto.
            </p>
            <p>
              <strong>Controle seu personagem:</strong> clique numa plataforma
              ou use 1–4 / setas para mover. Confirme em “Travar resposta” ou
              Enter. Você pode trocar de plataforma antes de confirmar. Cada um
              vê apenas a própria escolha.
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
            Tempo esgotado: sem pontos pela resposta; as cartas ainda são
            resolvidas. Os empates são mantidos no placar. Se a conexão cair,
            recarregue esta mesma aba para voltar à partida.
          </p></template
        >
        <template v-else-if="dialog === 'credits'"
          ><span class="eyebrow">GENTE QUE FAZ ACONTECER</span>
          <h2>Créditos & assets</h2>
          <p class="muted">
            Os assets deste jogo são gratuitos e estão incluídos no pacote.
          </p>
          <div class="credit-row">
            <strong>Kenney · Interface Sounds</strong
            ><span>Efeitos sonoros · CC0</span
            ><a href="/licenses/kenney.txt" target="_blank" rel="noopener"
              >Ver licença <ArrowRight :size="14"
            /></a>
          </div>
          <div class="credit-row">
            <strong>Lucide</strong><span>Ícones · ISC / MIT</span
            ><a href="/licenses/lucide.txt" target="_blank" rel="noopener"
              >Ver licença <ArrowRight :size="14"
            /></a>
          </div>
          <div class="credit-row">
            <strong>Outfit</strong
            ><span>Tipografia · SIL Open Font License</span
            ><a href="/licenses/outfit.txt" target="_blank" rel="noopener"
              >Ver licença <ArrowRight :size="14"
            /></a>
          </div>
          <p class="small-help">
            Ilustrações da interface e textos das 80 perguntas criados para o
            QuizMosh. Nomes de filmes são referências às respectivas obras.
          </p></template
        >
        <template v-else-if="dialog === 'invite'"
          ><span class="eyebrow">CHAMA A TURMA</span>
          <h2>Tem lugar no mosh.</h2>
          <img class="invite-qr" :src="qr" alt="QR code do convite" />
          <p class="invite-code">{{ state?.code }}</p>
          <label for="invite-link">Link da sala</label
          ><input
            id="invite-link"
            :value="invite"
            readonly
            @focus="($event.target as HTMLInputElement).select()"
          /><button class="primary-button" @click="copy(invite)">
            <Copy :size="18" /> Copiar convite
          </button>
          <p v-if="localHost" class="small-help">
            Este endereço funciona neste computador. Para convidar pela rede,
            acesse primeiro pelo IP do PC.
          </p></template
        >
        <template v-else-if="dialog === 'leave'"
          ><span class="eyebrow">ATÉ O PRÓXIMO MOSH</span>
          <h2>Sair da sala?</h2>
          <p class="muted">
            Você deixa esta partida. Se for o anfitrião, outro jogador assume a
            sala.
          </p>
          <p v-if="error" class="form-error" role="alert">{{ error }}</p>
          <div class="leave-actions">
            <button class="secondary-button" @click="dialog = ''">
              Continuar jogando</button
            ><button class="primary-button" :disabled="busy" @click="leave">
              Sair da sala <LogOut :size="18" />
            </button></div
        ></template>
      </section>
    </div>
  </div>
</template>
