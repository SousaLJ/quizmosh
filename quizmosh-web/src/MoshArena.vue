<script setup lang="ts">
import { computed } from "vue";
import type { State } from "./types";
import MoshAvatar from "./MoshAvatar.vue";
import { avatarColor, cardInfo } from "./mosh";
const props = defineProps<{
  state: State;
  selected?: string;
  canAnswer?: boolean;
  compact?: boolean;
}>();
const emit = defineEmits<{ select: [value: string]; confirm: [] }>();
const players = computed(() =>
  props.state.players.filter((p) => p.role === "PLAYER"),
);
const choices = computed(() =>
  props.state.phase === "ROUND" && props.state.round?.type === "choice"
    ? props.state.round.choices
    : [],
);
const revealed = computed(() =>
  ["REVEAL", "FINISHED"].includes(props.state.phase),
);
const duets = computed(() => {
  if (props.state.phase === "BACKSTAGE") return [];
  const seen = new Set<string>();
  return Object.entries(props.state.mosh?.plans || {}).flatMap(([id, plan]) => {
    if (plan.card !== "DUET" || !plan.target) return [];
    const a = players.value.findIndex((p) => p.id === id),
      b = players.value.findIndex((p) => p.id === plan.target);
    const key = [id, plan.target].sort().join(":");
    if (a < 0 || b < 0 || seen.has(key)) return [];
    seen.add(key);
    const height =
      players.value.length > 6
        ? 241
        : props.compact
          ? 167
          : choices.value.length
            ? 170
            : 205;
    const point = (index: number, player: string) => {
      const pos = position(index, player);
      return {
        x: parseFloat(pos.left) * 10,
        y: ((height - parseFloat(pos.bottom) - 40) / height) * 200,
      };
    };
    const from = point(a, id),
      to = point(b, plan.target);
    return [
      {
        key,
        path: `M ${from.x} ${from.y} Q ${(from.x + to.x) / 2} ${Math.min(from.y, to.y) - 70} ${to.x} ${to.y}`,
      },
    ];
  });
});
function position(index: number, id: string) {
  const choice = choices.value.findIndex(
    (c) => c.id === (props.state.you.answer || props.selected),
  );
  const selected = id === props.state.you.id && choice >= 0;
  return {
    left: `${selected ? ((choice + 0.5) * 100) / choices.value.length : (((index % 6) + 0.5) * 100) / Math.min(6, players.value.length)}%`,
    bottom: selected ? "4px" : `${42 + Math.floor(index / 6) * 83}px`,
    "--avatar-color": avatarColor(id),
    "--delay": `${index * -0.23}s`,
  };
}
</script>
<template>
  <div
    class="arena"
    :class="{
      encore: state.mosh?.encore,
      'arena-compact': compact,
      'arena-answering': choices.length,
      'arena-revealed': revealed,
    }"
  >
    <div class="arena-sky" aria-hidden="true">
      <i class="beam beam-one"></i><i class="beam beam-two"></i
      ><i class="beam beam-three"></i
      ><span
        v-for="n in 22"
        :key="n"
        class="arena-star"
        :style="{
          left: ((n * 37) % 100) + '%',
          top: ((n * 23) % 64) + '%',
          '--delay': (n % 5) + 's',
        }"
      ></span>
    </div>
    <div class="stage-sign">
      <span class="live-dot"></span
      >{{
        state.mosh?.encore
          ? "B I S · RISCO E BÔNUS ×2"
          : state.phase === "BACKSTAGE"
            ? "BASTIDORES · JOGADAS SECRETAS"
            : state.phase === "FINISHED"
              ? "VALEU PELO SHOW!"
              : "MOSH LIVE"
      }}
    </div>
    <div class="stage-speaker speaker-left" aria-hidden="true">
      <i></i><i></i>
    </div>
    <div class="stage-speaker speaker-right" aria-hidden="true">
      <i></i><i></i>
    </div>
    <div class="arena-floor" aria-hidden="true"></div>
    <div class="arena-cast" :class="{ 'large-cast': players.length > 6 }">
      <svg
        v-if="duets.length"
        class="duet-links"
        viewBox="0 0 1000 200"
        preserveAspectRatio="none"
        aria-hidden="true"
      >
        <path v-for="duet in duets" :key="duet.key" :d="duet.path" />
      </svg>
      <div
        v-for="(player, index) in players"
        :key="player.id"
        class="arena-player"
        :class="{
          'own-avatar': player.id === state.you.id,
          'player-offline': !player.online,
          'player-ready':
            player.answered ||
            (state.phase === 'BACKSTAGE' &&
              state.mosh?.ready.includes(player.id)),
        }"
        :style="position(index, player.id)"
      >
        <span
          v-if="revealed && state.reveal"
          class="floating-score"
          :class="{ loss: (state.reveal.deltas[player.id] || 0) < 0 }"
          >{{ (state.reveal.deltas[player.id] || 0) > 0 ? "+" : ""
          }}{{ state.reveal.deltas[player.id] || 0 }}</span
        >
        <span
          v-else-if="state.phase === 'ROUND' && state.mosh?.plans[player.id]"
          class="tactic-bubble"
          :title="
            cardInfo(state.mosh.plans[player.id].card).name +
            (state.mosh.plans[player.id].target
              ? ' com ' +
                (players.find(
                  (p) => p.id === state.mosh?.plans[player.id].target,
                )?.nickname || 'parceiro')
              : '')
          "
          >{{ cardInfo(state.mosh.plans[player.id].card).mark }}</span
        >
        <MoshAvatar
          :id="player.id"
          :excited="revealed && (state.reveal?.deltas[player.id] || 0) > 0"
          :crown="
            state.phase === 'FINISHED' &&
            state.ranking.find((p) => p.id === player.id)?.rank === 1
          "
        />
        <span class="arena-name"
          >{{ player.id === state.you.id ? "VOCÊ · " : ""
          }}{{ player.nickname }}</span
        >
      </div>
    </div>
    <div
      v-if="choices.length"
      class="answer-platforms"
      aria-label="Plataformas de resposta"
    >
      <button
        v-for="(choice, index) in choices"
        :key="choice.id"
        class="answer-platform"
        :data-choice="choice.id"
        :class="{ selected: (state.you.answer || selected) === choice.id }"
        :style="{
          '--pad-color': ['#b8a0ff', '#ff9c76', '#6fe4dc', '#ff9bd4'][index],
        }"
        :disabled="!canAnswer"
        :aria-pressed="(state.you.answer || selected) === choice.id"
        @click="emit('select', choice.id)"
      >
        <span class="platform-number">{{ index + 1 }}</span
        ><strong>{{ choice.text }}</strong
        ><small>{{
          state.you.answer === choice.id
            ? "TRAVADA ✓"
            : selected === choice.id
              ? "VOCÊ ESTÁ AQUI"
              : "IR PARA CÁ"
        }}</small>
      </button>
    </div>
    <div
      v-if="choices.length && state.you.role === 'PLAYER'"
      class="arena-controller"
    >
      <p>
        {{
          state.you.locked
            ? "Resposta travada. Agora segura a ansiedade…"
            : selected
              ? "Pode trocar de plataforma antes de travar."
              : "Escolha uma plataforma para mover seu personagem."
        }}
      </p>
      <button
        class="primary-button lock-answer"
        :disabled="!selected || !canAnswer"
        @click="emit('confirm')"
      >
        {{ state.you.locked ? "No palco!" : "Travar resposta" }}
        <kbd>Enter</kbd>
      </button>
    </div>
    <div v-if="revealed" class="arena-confetti" aria-hidden="true">
      <i
        v-for="n in 24"
        :key="n"
        :style="{
          left: ((n * 29) % 100) + '%',
          '--delay': (n % 6) * 0.16 + 's',
          '--avatar-color': avatarColor(String(n)),
        }"
      ></i>
    </div>
    <div v-if="!choices.length" class="arena-crowd" aria-hidden="true">
      <i
        v-for="n in 26"
        :key="n"
        :style="{ '--delay': (n % 7) * -0.2 + 's' }"
      ></i>
    </div>
  </div>
</template>
