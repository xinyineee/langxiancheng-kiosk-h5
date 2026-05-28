<template>
  <div class="result-page">
    <!-- Error state if no valid drink data -->
    <div v-if="!isValid" class="error-state">
      <div class="error-icon">☕</div>
      <h2 class="error-title">找不到你的测试结果</h2>
      <p class="error-desc">请从浪险橙Kiosk重新测试，或扫描NFC标签查看结果</p>
    </div>

    <!-- Valid result display -->
    <div v-else class="result-content">
      <DrinkHero
        :emoji="drink!.emoji"
        :name="drink!.name"
        :english-name="drink!.englishName"
        :tagline="drink!.tagline"
        :color-hex="drink!.colorHex"
      />

      <HeartCopy :heart-copy="drink!.heartCopy" />

      <ActionButtons
        :drink-name="drink!.name"
        :share-url="shareUrl"
      />

      <BrandFooter />
    </div>
  </div>
</template>

<script setup lang="ts">
import { useDrinkData } from '../composables/useDrinkData'
import DrinkHero from '../components/DrinkHero.vue'
import HeartCopy from '../components/HeartCopy.vue'
import ActionButtons from '../components/ActionButtons.vue'
import BrandFooter from '../components/BrandFooter.vue'

const { drink, isValid, shareUrl } = useDrinkData()
</script>

<style scoped>
.result-page {
  min-height: 100vh;
  max-width: 428px;
  margin: 0 auto;
  padding: 24px 20px;
  background-color: #FFF8F0;
}

.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 80vh;
  text-align: center;
  padding: 0 20px;
}

.error-icon {
  font-size: 64px;
  margin-bottom: 16px;
}

.error-title {
  font-size: 20px;
  font-weight: 600;
  color: #1A1A1A;
  margin-bottom: 8px;
}

.error-desc {
  font-size: 14px;
  color: #666666;
  line-height: 1.6;
}

.result-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
</style>
