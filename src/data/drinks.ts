import type { Drink, DrinkId } from '../types/drink'

/**
 * Six special drinks data configuration.
 * Matches the Android TestDataRepository exactly for cross-platform consistency.
 */
export const drinks: Drink[] = [
  {
    id: 'D1',
    name: '启程·梨想云',
    englishName: 'Departure · Pearfect Cloud',
    tagline: '每天都是新的起跑线',
    heartCopy: '每一个起点，都藏着你最好的可能。今天，就是你的新起跑线。',
    colorHex: '#FFB347',
    emoji: '🍐'
  },
  {
    id: 'D2',
    name: '探路·莓抹思慕雪',
    englishName: 'Explorer · Berry Mousse Smoothie',
    tagline: '创业初期的复杂滋味，但值得',
    heartCopy: '路不是直的，味道也不是简单的。但复杂里，藏着最真实的你。',
    colorHex: '#C06C84',
    emoji: '🫐'
  },
  {
    id: 'D3',
    name: '拼了·开心拿铁',
    englishName: 'Pin Le · Happy Latte',
    tagline: '拼这一次，不问结果',
    heartCopy: '不是每次拼搏都有结果，但每次拼搏都有意义。这一杯，献给此刻的你。',
    colorHex: '#FF6B1A',
    emoji: '💪'
  },
  {
    id: 'D4',
    name: '商米橙·破晓',
    englishName: 'SUNMI Dawn · Daybreak',
    tagline: '从混沌到清晰，创业的第一缕光',
    heartCopy: '混乱之后，清晨终会来临。你已经走过最暗的那段路了。',
    colorHex: '#FF8C42',
    emoji: '🌅'
  },
  {
    id: 'D5',
    name: '桂在路上·冷萃',
    englishName: 'Gui Zai Lu Shang · Cold Brew',
    tagline: '路还长，但已经闻到桂花香',
    heartCopy: '不用急，慢慢来。走得稳的人，最终都能闻到桂花香。',
    colorHex: '#D4A574',
    emoji: '🍂'
  },
  {
    id: 'D6',
    name: '上岸·奶砖拿铁',
    englishName: 'Shore Finish · Milk Brick Latte',
    tagline: '熬过层层，开心上岸，这一杯，值得庆祝',
    heartCopy: '熬过来了。真的熬过来了。这一杯，是你给自己最好的礼物。',
    colorHex: '#E8C07D',
    emoji: '🎉'
  }
]

/**
 * Finds a drink by its ID.
 * @param id The drink identifier (e.g., "D1")
 * @returns The matching Drink, or undefined if not found
 */
export function getDrinkById(id: DrinkId): Drink | undefined {
  return drinks.find((drink) => drink.id === id)
}
