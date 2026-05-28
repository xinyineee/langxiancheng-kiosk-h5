<template>
  <div class="drink-hero">
    <!-- Emoji/icon display -->
    <div class="hero-emoji-wrapper" :style="{ backgroundColor: colorHex + '1A' }">
      <span class="hero-emoji">{{ emoji }}</span>
    </div>

    <!-- Drink name -->
    <h1 class="hero-name" :style="{ color: colorHex }">{{ name }}</h1>

    <!-- English name -->
    <p class="hero-english-name">{{ englishName }}</p>

    <!-- Tagline badge -->
    <div class="hero-tagline" :style="{ backgroundColor: colorHex + '1A' }">
      <span :style="{ color: colorHex }">{{ tagline }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * DrinkHero component — displays the drink's emoji, name, English name, and tagline.
 */
defineProps<{
  /** Decorative emoji for the drink. */
  emoji: string
  /** Chinese drink name. */
  name: string
  /** English drink name. */
  englishName: string
  /** Short motto/slogan. */
  tagline: string
  /** Brand color hex for accent styling. */
  colorHex: string
}>()
</script>

<style scoped>
.drink-hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 32px 20px;
  background: #FFFFFF;
  border-radius: 16px;
  text-align: center;
}

.hero-emoji-wrapper {
  width: 80px;
  height: 80px;
  border-radius: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
}

.hero-emoji {
  font-size: 40px;
}

.hero-name {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.3;
  margin: 0 0 4px;
}

.hero-english-name {
  font-size: 14px;
  color: #999999;
  margin: 0 0 16px;
}

.hero-tagline {
  display: inline-block;
  padding: 8px 16px;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 600;
}
</style>
