/**
 * Theme definitions — colors, typography, mood palettes.
 * Mirror of Android's LoverTheme.kt / LoverBrand.kt
 */

// ─── Color Palette ────────────────────────────────────

export interface ColorDefs {
  rose: string
  deepRose: string
  warmBackground: string
  blush: string
  peach: string
  stone: string
  softOutline: string
  softSurface: string
  onSurface: string
  error: string
  dustPurple: string
  parchment: string
  soleilMist: string
  soleilBlush: string
  soleilPeach: string
  soleilStone: string
  soleilOutline: string
  soleilSurface: string
  soleilOnSurface: string
  white: string
  black: string
  loverAppName: string
  soleilAppName: string
}

export const Colors: ColorDefs = {
  // Couple mood
  rose: '#E88998',
  deepRose: '#B84F66',
  warmBackground: '#FFFCFB',
  blush: '#FFE9E6',
  peach: '#FFDCC8',
  stone: '#857A78',
  softOutline: '#E9DAD6',
  softSurface: '#FFF7F5',
  onSurface: '#332927',
  error: '#C45C5C',

  // Soleil (solo) mood
  dustPurple: '#6B5B80',
  parchment: '#F2EDE6',
  soleilMist: '#B8A9C4',
  soleilBlush: '#E8E0D6',
  soleilPeach: '#D9CFC3',
  soleilStone: '#7A7168',
  soleilOutline: '#D8D0C6',
  soleilSurface: '#F7F3ED',
  soleilOnSurface: '#3A342E',

  // Common
  white: '#FFFFFF',
  black: '#000000',
  loverAppName: 'rgba(159, 18, 57, 0.5)', // rose-900 @ 50%
  soleilAppName: 'rgba(107, 91, 128, 0.72)', // dustPurple @ 72%
}

// ─── Mood Palette ─────────────────────────────────────

export interface MoodPalette {
  solo: boolean
  accent: string
  soft: string
  blush: string
  peach: string
  stone: string
  background: string
  softOutline: string
  softSurface: string
  appName: string
  tagline: string
  appNameColor: string
  logoTint: string
  onSurface: string
}

export const CoupleMood: MoodPalette = {
  solo: false,
  accent: Colors.deepRose,
  soft: Colors.rose,
  blush: Colors.blush,
  peach: Colors.peach,
  stone: Colors.stone,
  background: Colors.warmBackground,
  softOutline: Colors.softOutline,
  softSurface: Colors.softSurface,
  appName: 'lover.',
  tagline: 'TWO HEARTS · ONE WORLD',
  appNameColor: Colors.loverAppName,
  logoTint: 'rgba(251, 113, 133, 0.45)',
  onSurface: Colors.onSurface,
}

export const SoleilMood: MoodPalette = {
  solo: true,
  accent: Colors.dustPurple,
  soft: Colors.soleilMist,
  blush: Colors.soleilBlush,
  peach: Colors.soleilPeach,
  stone: Colors.soleilStone,
  background: Colors.parchment,
  softOutline: Colors.soleilOutline,
  softSurface: Colors.soleilSurface,
  appName: 'lover.',
  tagline: 'One heart · One day · One step',
  appNameColor: Colors.soleilAppName,
  logoTint: 'rgba(107, 91, 128, 0.55)',
  onSurface: Colors.soleilOnSurface,
}

// ─── Typography ───────────────────────────────────────

export interface FontSizeDefs {
  displayLarge: number
  headlineMedium: number
  titleLarge: number
  bodyLarge: number
  bodyMedium: number
  bodySmall: number
  labelMedium: number
  labelSmall: number
}

export const FontSizes: FontSizeDefs = {
  displayLarge: 46,
  headlineMedium: 28,
  titleLarge: 22,
  bodyLarge: 16,
  bodyMedium: 14,
  bodySmall: 13,
  labelMedium: 11,
  labelSmall: 10,
}

export interface LetterSpacingDefs {
  tight: number
  normal: number
  medium: number
  wide: number
}

export const LetterSpacing: LetterSpacingDefs = {
  tight: -0.5,
  normal: 0,
  medium: 1.2,
  wide: 1.4,
}

// ─── Border Radius ────────────────────────────────────

export interface RadiusDefs {
  extraSmall: number
  small: number
  medium: number
  large: number
  extraLarge: number
}

export const Radii: RadiusDefs = {
  extraSmall: 14,
  small: 18,
  medium: 26,
  large: 34,
  extraLarge: 40,
}
