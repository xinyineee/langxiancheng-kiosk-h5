import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { getDrinkById } from '../data/drinks'
import type { Drink, DrinkId } from '../types/drink'

/**
 * Composable for accessing drink data based on the current route query parameters.
 *
 * Reads `d` (drinkId), `s` (scoreHash), and `t` (timestamp) from the URL
 * and provides the matching drink data reactively.
 */
export function useDrinkData() {
  const route = useRoute()

  /** The drink ID from the URL query parameter. */
  const drinkId = computed<DrinkId | null>(() => {
    const d = route.query.d as string | undefined
    if (d && ['D1', 'D2', 'D3', 'D4', 'D5', 'D6'].includes(d)) {
      return d as DrinkId
    }
    return null
  })

  /** The score hash from the URL query parameter. */
  const scoreHash = computed<string>(() => {
    return (route.query.s as string) || ''
  })

  /** The timestamp from the URL query parameter. */
  const timestamp = computed<string>(() => {
    return (route.query.t as string) || ''
  })

  /** The matched drink data, or null if not found. */
  const drink = computed<Drink | null>(() => {
    if (drinkId.value === null) return null
    return getDrinkById(drinkId.value) ?? null
  })

  /** Whether the drink data is valid and loaded. */
  const isValid = computed<boolean>(() => {
    // NFC scan: only `d` param is required (no scoreHash needed)
    return drink.value !== null
  })

  /** The share URL for social media — path-based format (NFC safe, no query string encoding issues). */
  const shareUrl = computed<string>(() => {
    if (!drinkId.value) return ''
    return `https://kiosk-h5.pages.dev/result/${drinkId.value}`
  })

  return {
    drinkId,
    scoreHash,
    timestamp,
    drink,
    isValid,
    shareUrl
  }
}
