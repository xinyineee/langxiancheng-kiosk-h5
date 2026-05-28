<template>
  <div class="welcome-page">
    <div class="welcome-content fade-in">
      <!-- 品牌 Logo 区域 -->
      <div class="logo-area">
        <span class="logo-emoji">🍊</span>
        <h1 class="brand-name">浪险橙</h1>
      </div>

      <!-- Slogan -->
      <p class="slogan">我们一起浪，一起险，一起成</p>

      <!-- 副标题 -->
      <p class="subtitle">30秒，发现你的创业人格饮品</p>

      <!-- 开始测试按钮 -->
      <button class="start-btn" @click="startQuiz">
        开始测试 →
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

/** 点击开始测试，跳转到测试页 */
function startQuiz() {
  router.push('/quiz')
}
</script>

<style scoped>
.welcome-page {
  min-height: 100vh;
  max-width: 428px;
  margin: 0 auto;
  background-color: #FF6B1A;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
}

.welcome-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 16px;
}

/* 淡入动画 */
.fade-in {
  animation: fadeIn 0.6s ease-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.logo-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.logo-emoji {
  font-size: 80px;
  line-height: 1;
}

.brand-name {
  font-size: 40px;
  font-weight: 800;
  color: #FFFFFF;
  letter-spacing: 4px;
  margin: 0;
}

.slogan {
  font-size: 18px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.95);
  margin: 0;
  letter-spacing: 2px;
}

.subtitle {
  font-size: 15px;
  color: rgba(255, 255, 255, 0.8);
  margin: 0 0 32px;
}

.start-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 56px;
  padding: 0 48px;
  border-radius: 28px;
  background-color: #FFFFFF;
  color: #FF6B1A;
  font-size: 18px;
  font-weight: 700;
  border: none;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
}

.start-btn:active {
  transform: scale(0.96);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}
</style>
