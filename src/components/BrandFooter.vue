<template>
  <div class="brand-footer">
    <!-- Brand name -->
    <p class="brand-name">浪险橙 · 前行饮品</p>

    <!-- Address -->
    <p class="domain"><svg class="addr-icon" viewBox="0 0 24 24" fill="currentColor" width="12" height="12"><path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5S10.62 6.5 12 6.5s2.5 1.12 2.5 2.5S13.38 11.5 12 11.5z"/></svg>五角场街道大学路一层135号</p>
  </div>
</template>

<script setup lang="ts">
/**
 * BrandFooter — minimal footer with brand name and domain.
 */
</script>

<style scoped>
.brand-footer {
  text-align: center;
  padding: 20px 20px 32px;
}

.brand-name {
  font-size: 13px;
  color: #999;
  margin: 0 0 4px;
}

.domain {
  font-size: 12px;
  color: #FF6B1A;
  opacity: 0.7;
  margin: 0;
  word-break: break-all;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.addr-icon {
  flex-shrink: 0;
}
</style>
