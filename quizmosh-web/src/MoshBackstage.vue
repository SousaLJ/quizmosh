<script setup lang="ts">
import { t } from "./i18n";
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
      "classic-trivia": t("ui.bullseye"),
      "quick-fire": t("ui.quickfire"),
      "guess-it": t("ui.whatSTheAnswer"),
      "closest-wins": t("ui.closeEnough"),
    })[props.state.mosh?.mode || ""] || "Mosh",
);
</script>
<template>
  <section class="backstage">
    <div class="backstage-heading">
      <div>
        <span class="eyebrow">
          {{ t("ui.round") }} {{ state.mosh?.number }} · {{ modeName }}</span
        >
        <h1>
          {{
            state.mosh?.encore
              ? t("ui.theCrowdWantsAnEncore")
              : t("ui.whatSYourMove")
          }}
        </h1>
        <p>
          {{
            state.mosh?.encore
              ? t("ui.doubleCardBonusesAndBettingPenaltiesChoose")
              : t("ui.readTheRoomChooseInSecretReveal")
          }}
        </p>
      </div>
      <div
        class="backstage-clock"
        role="timer"
        :aria-label="t('ui.secondsToChooseACard')"
      >
        {{ seconds }}<small> {{ t("ui.sec") }} </small>
      </div>
    </div>
    <MoshArena :state="state" compact />
    <template v-if="canPlan">
      <div class="energy-line">
        <span>
          {{ t("ui.yourBeats") }} <b>{{ energy }}/5</b></span
        ><span class="energy-pips" :aria-label="energy + t('ui.beats')"
          ><i
            v-for="n in 5"
            :key="n"
            :class="{ filled: n <= energy }"
          ></i></span
        ><small> {{ t("ui.aHitRestores1AMissRestores") }} </small>
      </div>
      <div class="tactic-cards" :aria-label="t('ui.chooseACard')">
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
                ? t("ui.free")
                : card.cost + t("ui.beat") + (card.cost > 1 ? "S" : "")
            }}</small></span
          ><strong>{{ card.name }}</strong>
          <p>{{ card.description }}</p>
        </button>
      </div>
      <p v-if="state.mosh?.mode === 'closest-wins'" class="tactic-hint">
        {{ t("ui.inCloseEnoughTheTopTwoRanks") }}
      </p>
      <div v-if="selected === 'DUET' && !own" class="duet-picker">
        <label for="duet-partner"> {{ t("ui.whoDoYouTrust") }} </label
        ><select id="duet-partner" v-model="partner" :disabled="disabled">
          <option value="" disabled>{{ t("ui.chooseYourPartner") }}</option>
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
          ✓ {{ cardInfo(own.card).name }}
          {{ t("ui.lockedTheCardsAreStillSecret") }}
        </p>
        <p v-else>{{ t("ui.noChoiceByTheTimeLimitYou") }}</p>
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
          {{ t("ui.lockInMove") }} <span>→</span></button
        ><span v-else class="locked-plan"> {{ t("ui.moveLocked") }} </span>
      </div>
    </template>
    <p v-else class="answer-status">
      {{ t("ui.theCrewIsChoosingCardsWatchThe") }}
    </p>
    <p class="backstage-ready">
      {{ state.mosh?.ready.length }} /
      {{ Object.keys(state.mosh?.energy || {}).length }}
      {{ t("ui.movesLockedTheShowBeginsWhenEveryone") }}
    </p>
  </section>
</template>
