<template>
  <div class="drink-hero">
    <!-- Drink card image (contains name, tagline, illustration) -->
    <div class="hero-card-wrapper">
      <img
        class="hero-card-img"
        :src="image"
        :alt="name"
        loading="eager"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * DrinkHero component — displays the drink card image.
 * The card image itself contains the drink illustration, name, English name, and tagline.
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
  /** Decorative emoji (reserved, not displayed). */
  emoji: string
}>()
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
</style>
