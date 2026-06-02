/** 测试题权重条目 */
export interface WeightEntry {
  /** 饮品 ID（如 D1, D2...） */
  drinkId: string
  /** 权重值 */
  weight: number
}

/** 答案选项 */
export interface AnswerOption {
  /** 选项标签（A/B/C/D） */
  label: string
  /** 表情符号 */
  emoji?: string
  /** 选项主文本 */
  optionText: string
  /** 选项副文本 */
  subText?: string
  /** 权重列表 */
  weights: WeightEntry[]
}

/** 测试题 */
export interface Question {
  /** 题目 ID */
  id: string
  /** 排序索引 */
  orderIndex: number
  /** 题目文本 */
  questionText: string
  /** 选项列表 */
  options: AnswerOption[]
}

/**
 * 测试题数据，与设备端 kiosk HTML 完全一致。
 * 权重数组 [D1, D2, D3, D4, D5, D6] 已展开为显式 drinkId + weight。
 */
export const questions: Question[] = [
  {
    id: 'Q1',
    orderIndex: 0,
    questionText: '你今天更像哪种状态？',
    options: [
      { label: 'A', emoji: '🌅', optionText: '刚刚苏醒', subText: '想慢慢进入状态', weights: [{ drinkId: 'D1', weight: 3 }, { drinkId: 'D2', weight: 1 }, { drinkId: 'D4', weight: 2 }, { drinkId: 'D6', weight: 1 }] },
      { label: 'B', emoji: '🔥', optionText: '准备狠狠干', subText: '今天想要快速推进', weights: [{ drinkId: 'D2', weight: 1 }, { drinkId: 'D3', weight: 3 }, { drinkId: 'D4', weight: 1 }] },
      { label: 'C', emoji: '😌', optionText: '慢慢来', subText: '有自己的节奏', weights: [{ drinkId: 'D4', weight: 1 }, { drinkId: 'D5', weight: 3 }, { drinkId: 'D6', weight: 1 }] },
      { label: 'D', emoji: '🚀', optionText: '直接开冲', subText: '先做了再说', weights: [{ drinkId: 'D1', weight: 1 }, { drinkId: 'D2', weight: 2 }, { drinkId: 'D3', weight: 3 }] }
    ]
  },
  {
    id: 'Q2',
    orderIndex: 1,
    questionText: '此刻你最想要哪种味道？',
    options: [
      { label: 'A', emoji: '🍊', optionText: '清爽果香', subText: '像刚切开的橙子', weights: [{ drinkId: 'D1', weight: 2 }, { drinkId: 'D4', weight: 3 }] },
      { label: 'B', emoji: '🍫', optionText: '浓郁可可', subText: '微苦但很有层次', weights: [{ drinkId: 'D2', weight: 3 }, { drinkId: 'D3', weight: 1 }] },
      { label: 'C', emoji: '🌾', optionText: '温暖谷物', subText: '像太阳晒过的麦子', weights: [{ drinkId: 'D4', weight: 2 }, { drinkId: 'D5', weight: 2 }, { drinkId: 'D6', weight: 1 }] },
      { label: 'D', emoji: '🍯', optionText: '焦糖甜感', subText: '今天需要一点安慰', weights: [{ drinkId: 'D3', weight: 2 }, { drinkId: 'D5', weight: 1 }, { drinkId: 'D6', weight: 3 }] }
    ]
  },
  {
    id: 'Q3',
    orderIndex: 2,
    questionText: '如果面前有两条路，你会？',
    options: [
      { label: 'A', emoji: '🏃', optionText: '冲进雾里', subText: '看不清才刺激', weights: [{ drinkId: 'D1', weight: 1 }, { drinkId: 'D2', weight: 1 }, { drinkId: 'D3', weight: 3 }] },
      { label: 'B', emoji: '🗺️', optionText: '先看地图', subText: '研究清楚再出发', weights: [{ drinkId: 'D4', weight: 1 }, { drinkId: 'D5', weight: 3 }, { drinkId: 'D6', weight: 1 }] },
      { label: 'C', emoji: '👣', optionText: '边走边看', subText: '先踏出一步', weights: [{ drinkId: 'D1', weight: 3 }, { drinkId: 'D2', weight: 2 }] },
      { label: 'D', emoji: '⏳', optionText: '等雾散', subText: '时机到了再动', weights: [{ drinkId: 'D5', weight: 3 }, { drinkId: 'D6', weight: 2 }] }
    ]
  },
  {
    id: 'Q4',
    orderIndex: 3,
    questionText: '大风刮来，你只留一样东西？',
    options: [
      { label: 'A', emoji: '🔥', optionText: '火把', subText: '自己的光最重要', weights: [{ drinkId: 'D1', weight: 1 }, { drinkId: 'D3', weight: 3 }, { drinkId: 'D4', weight: 1 }] },
      { label: 'B', emoji: '🧭', optionText: '罗盘', subText: '方向比速度重要', weights: [{ drinkId: 'D2', weight: 2 }, { drinkId: 'D4', weight: 2 }, { drinkId: 'D5', weight: 2 }] },
      { label: 'C', emoji: '📖', optionText: '日记', subText: '先沉淀再出发', weights: [{ drinkId: 'D4', weight: 1 }, { drinkId: 'D5', weight: 3 }, { drinkId: 'D6', weight: 1 }] },
      { label: 'D', emoji: '☕', optionText: '咖啡', subText: '先补充一点能量', weights: [{ drinkId: 'D2', weight: 1 }, { drinkId: 'D5', weight: 1 }, { drinkId: 'D6', weight: 3 }] }
    ]
  },
  {
    id: 'Q5',
    orderIndex: 4,
    questionText: '浪、险、成，哪个字在敲你的心？',
    options: [
      { label: 'A', emoji: '🌊', optionText: '浪', subText: '世界很大，我想去看看', weights: [{ drinkId: 'D1', weight: 3 }, { drinkId: 'D2', weight: 1 }] },
      { label: 'B', emoji: '⛰️', optionText: '险', subText: '站在崖边，也不想退', weights: [{ drinkId: 'D2', weight: 3 }, { drinkId: 'D3', weight: 2 }] },
      { label: 'C', emoji: '🏆', optionText: '成', subText: '已经看到终点轮廓', weights: [{ drinkId: 'D5', weight: 2 }, { drinkId: 'D6', weight: 3 }] },
      { label: 'D', emoji: '🎲', optionText: '都在变', subText: '三个字我都想要', weights: [{ drinkId: 'D1', weight: 2 }, { drinkId: 'D2', weight: 2 }, { drinkId: 'D4', weight: 1 }] }
    ]
  }
]
