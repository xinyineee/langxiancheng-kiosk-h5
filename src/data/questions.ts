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
  /** 选项文本 */
  optionText: string
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
 * 测试题数据，与 Android TestDataRepository 完全一致。
 */
export const questions: Question[] = [
  {
    id: 'Q1',
    orderIndex: 0,
    questionText: '如果你的人生是一杯咖啡，现在处于哪个阶段？',
    options: [
      { label: 'A', optionText: '刚磨好豆子，满是香气和期待', weights: [{ drinkId: 'D1', weight: 3 }, { drinkId: 'D2', weight: 1 }] },
      { label: 'B', optionText: '正在萃取中，有点苦但很专注', weights: [{ drinkId: 'D2', weight: 3 }, { drinkId: 'D4', weight: 1 }] },
      { label: 'C', optionText: '快好了，闻到了那股味道', weights: [{ drinkId: 'D5', weight: 3 }, { drinkId: 'D4', weight: 1 }] },
      { label: 'D', optionText: '已经端到手里，该好好喝了', weights: [{ drinkId: 'D6', weight: 3 }, { drinkId: 'D3', weight: 1 }] }
    ]
  },
  {
    id: 'Q2',
    orderIndex: 1,
    questionText: '有人问你"你最近怎么样"，你最诚实的回答是？',
    options: [
      { label: 'A', optionText: '还在想，脑子里一堆想法没落地', weights: [{ drinkId: 'D1', weight: 2 }, { drinkId: 'D4', weight: 2 }] },
      { label: 'B', optionText: '乱得很，但好像也挺有意思的', weights: [{ drinkId: 'D2', weight: 3 }, { drinkId: 'D3', weight: 1 }] },
      { label: 'C', optionText: '在熬，但方向越来越清晰了', weights: [{ drinkId: 'D5', weight: 2 }, { drinkId: 'D4', weight: 2 }] },
      { label: 'D', optionText: '挺好的，刚过了一个难关', weights: [{ drinkId: 'D6', weight: 3 }, { drinkId: 'D3', weight: 1 }] }
    ]
  },
  {
    id: 'Q3',
    orderIndex: 2,
    questionText: '面对一个没把握但很诱人的机会，你会？',
    options: [
      { label: 'A', optionText: '直接冲，失败了再说', weights: [{ drinkId: 'D3', weight: 3 }, { drinkId: 'D1', weight: 1 }] },
      { label: 'B', optionText: '先研究研究，再决定', weights: [{ drinkId: 'D4', weight: 2 }, { drinkId: 'D5', weight: 2 }] },
      { label: 'C', optionText: '边走边看，先踏出第一步', weights: [{ drinkId: 'D2', weight: 2 }, { drinkId: 'D1', weight: 2 }] },
      { label: 'D', optionText: '等时机更成熟再出手', weights: [{ drinkId: 'D5', weight: 3 }, { drinkId: 'D6', weight: 1 }] }
    ]
  },
  {
    id: 'Q4',
    orderIndex: 3,
    questionText: '你和朋友约好一起创业，对方突然退出，你？',
    options: [
      { label: 'A', optionText: '自己干，反正我本来就准备单飞', weights: [{ drinkId: 'D3', weight: 3 }, { drinkId: 'D1', weight: 1 }] },
      { label: 'B', optionText: '调整心态，重新找搭档', weights: [{ drinkId: 'D2', weight: 2 }, { drinkId: 'D4', weight: 2 }] },
      { label: 'C', optionText: '沉淀一下，想清楚再出发', weights: [{ drinkId: 'D5', weight: 2 }, { drinkId: 'D4', weight: 2 }] },
      { label: 'D', optionText: '喝杯咖啡，今天先放下', weights: [{ drinkId: 'D6', weight: 2 }, { drinkId: 'D2', weight: 2 }] }
    ]
  },
  {
    id: 'Q5',
    orderIndex: 4,
    questionText: '浪险橙的 Slogan 是"浪、险、成"——你现在最像哪个字？',
    options: [
      { label: 'A', optionText: '浪——随心所欲，探索未知', weights: [{ drinkId: 'D1', weight: 3 }, { drinkId: 'D2', weight: 1 }] },
      { label: 'B', optionText: '险——在悬崖边上，但不退缩', weights: [{ drinkId: 'D3', weight: 3 }, { drinkId: 'D4', weight: 1 }] },
      { label: 'C', optionText: '成——已经看到结果的轮廓了', weights: [{ drinkId: 'D5', weight: 2 }, { drinkId: 'D6', weight: 2 }] },
      { label: 'D', optionText: '还没想好，三个字我都想要', weights: [{ drinkId: 'D2', weight: 2 }, { drinkId: 'D1', weight: 2 }] }
    ]
  }
]
