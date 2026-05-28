<template>
  <div class="heart-copy-card">
    <span class="quote-mark open">「</span>
    <p class="heart-copy-text">{{ heartCopy }}</p>
    <span class="quote-mark close">」</span>
  </div>
</template>

<script setup lang="ts">
/**
 * HeartCopy component — displays the emotional/inspirational copy
 * for the matched drink result in a styled card.
 */
defineProps<{
  /** Emotional/inspirational copy text. */
  heartCopy: string
}>()
</script>

<style scoped>
.heart-copy-card {
  background: rgba(255, 255, 255, 0.9);
  border-radius: 16px;
  padding: 20px;
  position: relative;
}

.quote-mark {
  font-size: 32px;
  color: #FF6B1A;
  opacity: 0.4;
  line-height: 1;
  font-weight: 700;
}

.quote-mark.open {
  display: block;
  margin-bottom: 4px;
}

.quote-mark.close {
  display: block;
  text-align: right;
  margin-top: 4px;
}

.heart-copy-text {
  font-size: 16px;
  line-height: 1.8;
  color: #1A1A1A;
  margin: 0;
  padding: 0 4px;
}
</style>
