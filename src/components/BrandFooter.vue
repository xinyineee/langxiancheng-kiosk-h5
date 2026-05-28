<template>
  <div class="brand-footer">
    <!-- Brand logo/name -->
    <div class="brand-logo">
      <span class="brand-emoji">🍊</span>
      <span class="brand-name">浪险橙咖啡</span>
    </div>

    <!-- Slogan -->
    <p class="brand-slogan">我们一起浪，一起险，一起成</p>

    <!-- Store address -->
    <div class="store-info">
      <p class="store-address">📍 深圳市南山区科技园浪险橙旗舰店</p>
      <p class="store-hours">🕐 营业时间：09:00 - 22:00</p>
    </div>

    <!-- Copyright -->
    <p class="copyright">© 2026 浪险橙咖啡 · All Rights Reserved</p>
  </div>
</template>

<script setup lang="ts">
/**
 * BrandFooter component — displays brand information and store address.
 */
</script>

<style scoped>
.brand-footer {
  text-align: center;
  padding: 24px 20px;
  border-top: 1px solid rgba(255, 107, 26, 0.1);
}

.brand-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-bottom: 8px;
}

.brand-emoji {
  font-size: 24px;
}

.brand-name {
  font-size: 18px;
  font-weight: 700;
  color: #FF6B1A;
}

.brand-slogan {
  font-size: 14px;
  color: #666666;
  margin: 0 0 16px;
}

.store-info {
  margin-bottom: 12px;
}

.store-address,
.store-hours {
  font-size: 13px;
  color: #999999;
  margin: 4px 0;
}

.copyright {
  font-size: 12px;
  color: #CCCCCC;
  margin: 8px 0 0;
}
</style>
