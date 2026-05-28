import SparkMD5 from 'spark-md5'
import { questions } from '../data/questions'
import type { AnswerOption, Question } from '../data/questions'
import type { DrinkId } from '../types/drink'

/** 所有饮品 ID 列表 */
const ALL_DRINK_IDS: DrinkId[] = ['D1', 'D2', 'D3', 'D4', 'D5', 'D6']

/** 测试结果 */
export interface TestResult {
  /** 推荐饮品 ID */
  recommendedDrinkId: DrinkId
  /** 各饮品分数映射 */
  scores: Record<DrinkId, number>
  /** 分数哈希（MD5 前6位） */
  scoreHash: string
}

/**
 * 测试计算引擎，与 Android TestEngine 完全一致。
 *
 * 逻辑：
 * 1. 初始化所有饮品 ID 分数为 0
 * 2. 遍历用户选择的选项，累加权重
 * 3. 找到最高分的饮品（平局时选 ID 更大的）
 * 4. 生成 scoreHash：MD5(scores.toString() + "lxc2026").substring(0, 6)
 */
export function calculateResult(selectedOptions: Map<string, AnswerOption>): TestResult {
  // 步骤1：初始化所有饮品分数为 0
  const scores: Record<DrinkId, number> = {} as Record<DrinkId, number>
  for (const id of ALL_DRINK_IDS) {
    scores[id] = 0
  }

  // 步骤2：遍历用户选择的选项，累加权重
  for (const [_questionId, option] of selectedOptions) {
    for (const weightEntry of option.weights) {
      const drinkId = weightEntry.drinkId as DrinkId
      if (drinkId in scores) {
        scores[drinkId] += weightEntry.weight
      }
    }
  }

  // 步骤3：找到最高分的饮品，平局时选 ID 更大的
  let maxScore = -1
  let recommendedDrinkId: DrinkId = 'D1'
  for (const id of ALL_DRINK_IDS) {
    const score = scores[id]
    if (score > maxScore || (score === maxScore && id > recommendedDrinkId)) {
      maxScore = score
      recommendedDrinkId = id
    }
  }

  // 步骤4：生成 scoreHash
  // scores.toString() 格式：与 Android Map 的 toString() 一致
  // 格式为 {D1=3, D2=1, D3=0, D4=2, D5=0, D6=0}
  const scoresString = `{${ALL_DRINK_IDS.map(id => `${id}=${scores[id]}`).join(', ')}}`
  const rawHash = SparkMD5.hash(scoresString + 'lxc2026')
  const scoreHash = rawHash.substring(0, 6)

  return {
    recommendedDrinkId,
    scores,
    scoreHash
  }
}

/**
 * 获取排序后的题目列表。
 */
export function getOrderedQuestions(): Question[] {
  return [...questions].sort((a, b) => a.orderIndex - b.orderIndex)
}
