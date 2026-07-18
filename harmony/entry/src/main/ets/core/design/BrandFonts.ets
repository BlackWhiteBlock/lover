/**
 * Register brand fonts (same TTF files as Android res/font).
 */
import { font } from '@kit.ArkUI'
import { hilog } from '@kit.PerformanceAnalysisKit'
import { Fonts } from './Fonts'

const TAG = 'BrandFonts'
const DOMAIN = 0x0001
let registered: boolean = false

interface FontRegOptions {
  familyName: string
  familySrc: Resource
}

function buildOptions(familyName: string, familySrc: Resource): FontRegOptions {
  const options: FontRegOptions = {
    familyName: familyName,
    familySrc: familySrc,
  }
  return options
}

/** Register once. Call from Ability / page aboutToAppear. */
export function ensureBrandFonts(): void {
  if (registered) {
    return
  }
  try {
    font.registerFont(buildOptions(Fonts.PINYON, $rawfile('fonts/pinyon_script_regular.ttf')))
    font.registerFont(buildOptions(Fonts.GARAMOND, $rawfile('fonts/eb_garamond_regular.ttf')))
    font.registerFont(buildOptions(Fonts.GARAMOND_ITALIC, $rawfile('fonts/eb_garamond_italic.ttf')))
    font.registerFont(buildOptions(Fonts.LATO, $rawfile('fonts/lato_regular.ttf')))
    font.registerFont(buildOptions(Fonts.LATO_LIGHT, $rawfile('fonts/lato_light.ttf')))
    registered = true
    hilog.info(DOMAIN, TAG, 'brand fonts registered: %{public}s', Fonts.PINYON)
  } catch (e) {
    hilog.error(DOMAIN, TAG, 'brand font register failed: %{public}s', JSON.stringify(e))
  }
}

export function resetBrandFontsFlag(): void {
  registered = false
}
