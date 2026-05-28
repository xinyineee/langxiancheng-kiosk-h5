/**
 * Drink type definition for the H5 landing page.
 * Mirrors the Android Drink data model for consistency.
 */

/** Unique drink identifier (e.g., "D1" through "D6"). */
export type DrinkId = 'D1' | 'D2' | 'D3' | 'D4' | 'D5' | 'D6'

/** A special drink with its metadata and copy. */
export interface Drink {
  /** Unique drink identifier. */
  id: DrinkId
  /** Chinese name (e.g., "启程·梨想云"). */
  name: string
  /** English name (e.g., "Departure · Pearfect Cloud"). */
  englishName: string
  /** Short motto/slogan. */
  tagline: string
  /** Emotional/inspirational copy for the result card. */
  heartCopy: string
  /** Brand color hex for this drink's accent. */
  colorHex: string
  /** Decorative emoji. */
  emoji: string
}

/** Query parameters for the result page URL. */
export interface ResultQuery {
  /** Drink ID (e.g., "D1"). */
  d: DrinkId
  /** Score hash for verification. */
  s: string
  /** Unix timestamp. */
  t: string
}
