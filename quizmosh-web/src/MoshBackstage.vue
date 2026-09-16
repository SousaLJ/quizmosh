<script setup lang="ts">
import { computed, ref } from "vue";
import type { Card, State, Tactic } from "./types";
import { cards, cardInfo } from "./mosh";
import MoshArena from "./MoshArena.vue";
const props = defineProps<{
  state: State;
  seconds: number;
  disabled: boolean;
}>();
const emit = defineEmits<{ commit: [plan: Tactic] }>();
const selected = ref<Card>("STEADY");
const partner = ref("");
const own = computed(() => props.state.mosh?.plans[props.state.you.id]);
const energy = computed(
  () => props.state.mosh?.energy[props.state.you.id] || 0,
);
const partners = computed(() =>
  props.state.players.filter(
    (p) =>
      p.role === "PLAYER" &&
      p.id !== props.state.you.id &&
      p.id in (props.state.mosh?.energy || {}),
  ),
);
const canPlan = computed(
  () => props.state.you.id in (props.state.mosh?.energy || {}),
);
const modeName = computed(
  () =>
    ({
      "classic-trivia": "Na mosca",
      "quick-fire": "Bate-pronto",
      "guess-it": "Qual é a boa?",
      "closest-wins": "Quase lá",
    })[props.state.mosh?.mode || ""] || "Mosh",
);
</script>
<template>
  <section class="backstage">
    <div class="backstage-heading">
      <div>
        <span class="eyebrow"
          >RODADA {{ state.mosh?.number }} · {{ modeName }}</span
        >
        <h1>
          {{
            state.mosh?.encore ? "A plateia pediu BIS." : "Qual é a sua jogada?"
          }}
        </h1>
        <p>
          {{
            state.mosh?.encore
              ? "Bônus das cartas e penalidade da aposta em dobro. Escolha com cuidado."
              : "Leia a turma. Escolha em segredo. Revelem as cartas juntos."
          }}
        </p>
      </div>
      <div
        class="backstage-clock"
        role="timer"
        aria-label="Segundos para escolher a carta"
      >
        {{ seconds }}<small>SEG</small>
      </div>
    </div>
    <MoshArena :state="state" compact />
    <template v-if="canPlan">
      <div class="energy-line">
        <span
          >SUAS BATIDAS <b>{{ energy }}/5</b></span
        ><span class="energy-pips" :aria-label="energy + ' batidas'"
          ><i
            v-for="n in 5"
            :key="n"
            :class="{ filled: n <= energy }"
          ></i></span
        ><small>Acerto repõe 1 · erro repõe 2 · máximo 5</small>
      </div>
      <div class="tactic-cards" aria-label="Escolha uma carta">
        <button
          v-for="card in cards"
          :key="card.id"
          class="tactic-card"
          :class="[
            { selected: (own?.card || selected) === card.id },
            'card-' + card.id.toLowerCase(),
          ]"
          :aria-pressed="(own?.card || selected) === card.id"
          :disabled="!!own || disabled || energy < card.cost"
          @click="selected = card.id"
        >
          <span class="card-top"
            ><span class="card-symbol">{{ card.mark }}</span
            ><small>{{
              card.cost === 0
                ? "GRÁTIS"
                : card.cost + " BATIDA" + (card.cost > 1 ? "S" : "")
            }}</small></span
          ><strong>{{ card.name }}</strong>
          <p>{{ card.description }}</p>
        </button>
      </div>
      <p v-if="state.mosh?.mode === 'closest-wins'" class="tactic-hint">
        No Quase lá, ficar nas duas primeiras posições por proximidade conta
        como acerto para as cartas e a plateia. Empates contam.
      </p>
      <div v-if="selected === 'DUET' && !own" class="duet-picker">
        <label for="duet-partner">Em quem você confia?</label
        ><select id="duet-partner" v-model="partner" :disabled="disabled">
          <option value="" disabled>Escolha seu parceiro</option>
          <option
            v-for="player in partners"
            :key="player.id"
            :value="player.id"
          >
            {{ player.nickname }}
          </option>
        </select>
      </div>
      <div class="backstage-action">
        <p v-if="own" role="status">
          ✓ {{ cardInfo(own.card).name }} confirmada. As cartas ainda estão em
          segredo.
        </p>
        <p v-else>Sem escolha até o fim do tempo? Você joga “Na minha”.</p>
        <button
          v-if="!own"
          class="primary-button"
          :disabled="
            disabled ||
            seconds === 0 ||
            energy < cardInfo(selected).cost ||
            (selected === 'DUET' && !partner)
          "
          @click="
            emit('commit', {
              card: selected,
              target: selected === 'DUET' ? partner : null,
            })
          "
        >
          Confirmar jogada <span>→</span></button
        ><span v-else class="locked-plan">JOGADA TRAVADA</span>
      </div>
    </template>
    <p v-else class="answer-status">
      A turma está escolhendo as cartas. Acompanhe a revelação no palco.
    </p>
    <p class="backstage-ready">
      {{ state.mosh?.ready.length }} /
      {{ Object.keys(state.mosh?.energy || {}).length }} jogadas confirmadas · O
      show começa quando todos estiverem prontos.
    </p>
  </section>
</template>
