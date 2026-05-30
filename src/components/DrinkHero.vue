<template>
  <div class="drink-hero">
    <!-- Drink card image (contains name, tagline, illustration) -->
    <div class="hero-card-wrapper">
      <img
        class="hero-card-img"
        :src="image"
        :alt="name"
        loading="eager"
        @error="onImageError"
      />
      <!-- Fallback when image fails to load -->
      <div v-if="imageError" class="hero-fallback" :style="{ background: colorHex }">
        <span class="fallback-emoji">{{ emoji }}</span>
        <span class="fallback-name">{{ name }}</span>
        <span class="fallback-tagline">{{ tagline }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

/**
 * DrinkHero component — displays the drink card image.
 * The card image itself contains the drink illustration, name, English name, and tagline.
 * Falls back to a styled card if the image fails to load.
 */
defineProps<{
  /** Path to the drink card image. */
  image: string
  /** Chinese drink name (used as alt text). */
  name: string
  /** English drink name (reserved for future use). */
  englishName: string
  /** Short motto/slogan (reserved for future use). */
  tagline: string
  /** Brand color hex for accent styling. */
  colorHex: string
  /** Decorative emoji (shown in fallback). */
  emoji: string
}>()

const imageError = ref(false)

function onImageError() {
  imageError.value = true
}
</script>

<style scoped>
.drink-hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px 16px;
  background: transparent;
}

.hero-card-wrapper {
  width: 100%;
  max-width: 320px;
  border-radius: 24px;
  overflow: hidden;
  box-shadow: 0 8px 32px rgba(255, 107, 26, 0.12);
  animation: card-pop 0.6s cubic-bezier(0.34, 1.56, 0.64, 1) both;
  position: relative;
}

@keyframes card-pop {
  0% {
    opacity: 0;
    transform: scale(0.85) translateY(20px);
  }
  100% {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

.hero-card-img {
  display: block;
  width: 100%;
  height: auto;
  border-radius: 24px;
}

/* Fallback card when image fails */
.hero-fallback {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 380px;
  padding: 32px 20px;
  border-radius: 24px;
  text-align: center;
}

.fallback-emoji {
  font-size: 64px;
  margin-bottom: 16px;
}

.fallback-name {
  font-size: 24px;
  font-weight: 700;
  color: #FFFFFF;
  margin-bottom: 8px;
  text-shadow: 0 1px 3px rgba(0,0,0,0.2);
}

.fallback-tagline {
  font-size: 14px;
  color: rgba(255,255,255,0.9);
  text-shadow: 0 1px 2px rgba(0,0,0,0.15);
}
</style>
