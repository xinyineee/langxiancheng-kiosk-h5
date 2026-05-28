<template>
  <div class="quiz-page">
    <!-- 顶部进度条 -->
    <div class="progress-section">
      <div class="progress-text">{{ currentIndex + 1 }} / {{ totalQuestions }}</div>
      <div class="progress-bar">
        <div
          class="progress-fill"
          :style="{ width: progressPercent + '%' }"
        ></div>
      </div>
    </div>

    <!-- 题目区域 -->
    <div class="question-area" :key="currentQuestion.id">
      <h2 class="question-text">{{ currentQuestion.questionText }}</h2>

      <!-- 选项列表 -->
      <div class="options-list">
        <button
          v-for="option in currentQuestion.options"
          :key="option.label"
          class="option-card"
          :class="{ 'option-selected': selectedLabel === option.label }"
          @click="selectOption(option)"
        >
          <span class="option-label">{{ option.label }}</span>
          <span class="option-text">{{ option.optionText }}</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { getOrderedQuestions, calculateResult } from '../composables/useTestEngine'
import type { AnswerOption } from '../data/questions'

const router = useRouter()

/** 排序后的题目列表 */
const questions = getOrderedQuestions()

/** 题目总数 */
const totalQuestions = questions.length

/** 当前题目索引 */
const currentIndex = ref(0)

/** 当前选中的选项标签 */
const selectedLabel = ref<string | null>(null)

/** 用户的所有答案：Map<题目ID, 选择的选项> */
const answers = ref<Map<string, AnswerOption>>(new Map())

/** 当前题目 */
const currentQuestion = computed(() => questions[currentIndex.value]!)

/** 进度百分比 */
const progressPercent = computed(() => {
  return ((currentIndex.value + 1) / totalQuestions) * 100
})

/**
 * 选择选项并自动跳转下一题。
 * 最后一题选完后自动计算结果并跳转结果页。
 */
function selectOption(option: AnswerOption) {
  if (selectedLabel.value !== null) return // 防止重复点击

  selectedLabel.value = option.label
  answers.value.set(currentQuestion.value.id, option)

  // 最后一题：计算结果并跳转
  if (currentIndex.value === totalQuestions - 1) {
    const result = calculateResult(answers.value)
    const timestamp = Math.floor(Date.now() / 1000).toString()

    setTimeout(() => {
      router.push({
        path: '/result',
        query: {
          d: result.recommendedDrinkId,
          s: result.scoreHash,
          t: timestamp
        }
      })
    }, 300)
    return
  }

  // 非最后一题：延迟跳转下一题
  setTimeout(() => {
    currentIndex.value++
    selectedLabel.value = null
  }, 300)
}
</script>

<style scoped>
.quiz-page {
  min-height: 100vh;
  max-width: 428px;
  margin: 0 auto;
  background-color: #FFF8F0;
  padding: 24px 20px;
  display: flex;
  flex-direction: column;
}

/* 进度条区域 */
.progress-section {
  margin-bottom: 32px;
}

.progress-text {
  font-size: 14px;
  font-weight: 600;
  color: #FF6B1A;
  margin-bottom: 8px;
  text-align: right;
}

.progress-bar {
  height: 6px;
  background-color: #FFE8D6;
  border-radius: 3px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background-color: #FF6B1A;
  border-radius: 3px;
  transition: width 0.3s ease;
}

/* 题目区域 */
.question-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  animation: slideIn 0.3s ease-out;
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateX(20px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.question-text {
  font-size: 22px;
  font-weight: 700;
  color: #1A1A1A;
  line-height: 1.5;
  margin: 0 0 28px;
}

/* 选项列表 */
.options-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.option-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px 18px;
  background: #FFFFFF;
  border: 2px solid #F0E6DC;
  border-radius: 16px;
  cursor: pointer;
  transition: all 0.2s ease;
  text-align: left;
  width: 100%;
}

.option-card:active {
  transform: scale(0.98);
}

.option-card.option-selected {
  border-color: #FF6B1A;
  background-color: #FFF5ED;
}

.option-label {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  height: 28px;
  border-radius: 8px;
  background-color: #FFF0E6;
  color: #FF6B1A;
  font-size: 14px;
  font-weight: 700;
  flex-shrink: 0;
}

.option-selected .option-label {
  background-color: #FF6B1A;
  color: #FFFFFF;
}

.option-text {
  font-size: 15px;
  line-height: 1.6;
  color: #1A1A1A;
  padding-top: 3px;
}

.option-selected .option-text {
  font-weight: 600;
  color: #FF6B1A;
}
</style>
