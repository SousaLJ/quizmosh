<script setup lang="ts">
import { computed } from "vue";
import { avatarColor } from "./mosh";
const props = defineProps<{ id: string; excited?: boolean; crown?: boolean }>();
const variant = computed(
  () => Array.from(props.id).reduce((n, c) => n + c.charCodeAt(0), 0) % 3,
);
</script>
<template>
  <svg
    class="mosh-avatar"
    :class="{ excited }"
    viewBox="0 0 100 112"
    aria-hidden="true"
    :style="{ '--avatar-color': avatarColor(id) }"
  >
    <ellipse
      class="avatar-shadow"
      cx="50"
      cy="103"
      rx="29"
      ry="6"
      fill="#070b19"
      opacity=".4"
    />
    <g class="avatar-body">
      <path
        d="M34 85L30 100M66 85L71 100"
        stroke="#17172c"
        stroke-width="10"
        stroke-linecap="round"
      />
      <path
        class="avatar-arm left-arm"
        d="M26 59Q11 68 17 78"
        fill="none"
        stroke="var(--avatar-color)"
        stroke-width="10"
        stroke-linecap="round"
      />
      <path
        class="avatar-arm right-arm"
        d="M75 59Q90 68 83 78"
        fill="none"
        stroke="var(--avatar-color)"
        stroke-width="10"
        stroke-linecap="round"
      />
      <path
        v-if="variant === 0"
        d="M22 43Q20 23 50 23Q81 23 79 46L76 78Q72 92 50 92Q25 91 24 77Z"
        fill="var(--avatar-color)"
      />
      <path
        v-else-if="variant === 1"
        d="M20 76L27 37Q30 24 50 24Q70 24 73 37L81 76Q80 92 50 92Q20 92 20 76"
        fill="var(--avatar-color)"
      />
      <rect
        v-else
        x="22"
        y="26"
        width="56"
        height="66"
        rx="19"
        fill="var(--avatar-color)"
      />
      <path
        d="M30 42Q33 30 46 32"
        stroke="#fff"
        stroke-width="5"
        stroke-linecap="round"
        opacity=".45"
        fill="none"
      />
      <g class="avatar-eyes" fill="#202139">
        <ellipse cx="39" cy="56" rx="5" ry="8" />
        <ellipse cx="61" cy="56" rx="5" ry="8" />
      </g>
      <path v-if="excited" d="M39 72Q50 88 61 72Z" fill="#202139" />
      <path
        v-else
        d="M43 74Q50 80 57 74"
        fill="none"
        stroke="#202139"
        stroke-width="3"
        stroke-linecap="round"
      />
      <ellipse cx="30" cy="69" rx="6" ry="3" fill="#e57698" opacity=".5" />
      <ellipse cx="70" cy="69" rx="6" ry="3" fill="#e57698" opacity=".5" />
      <path
        v-if="variant === 0"
        d="M33 26L38 13L47 25L58 10L65 28"
        fill="#202139"
      />
      <g
        v-else-if="variant === 1"
        fill="none"
        stroke="#34324d"
        stroke-width="7"
      >
        <path d="M23 52V43A27 27 0 0 1 77 43V52" />
        <path d="M23 47V61M77 47V61" stroke-width="10" stroke-linecap="round" />
      </g>
      <path v-else d="M38 25L48 10L63 25Z" fill="#202139" />
      <path
        v-if="crown"
        d="M33 17L28 1L42 9L50 0L58 9L73 1L68 17Z"
        fill="#ffdc75"
        stroke="#87622a"
        stroke-width="1.5"
      />
    </g>
  </svg>
</template>
