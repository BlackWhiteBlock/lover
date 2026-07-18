/**
 * Page header — Chinese title + English subtitle (uppercase).
 */
import { Fonts } from './Fonts'
import { FontSizes, LetterSpacing } from './Theme'
import type { MoodPalette } from './Theme'

@Component
export struct PageHeader {
  @Prop mood: MoodPalette
  @Prop title: string = ''
  @Prop subtitle: string = ''

  build() {
    Column({ space: 4 }) {
      Text(this.title)
        .fontSize(FontSizes.headlineMedium)
        .fontFamily(this.mood.solo ? Fonts.GARAMOND : Fonts.LATO)
        .fontColor(this.mood.accent)
        .opacity(0.92)
        .width('100%')
      Text(this.subtitle.toUpperCase())
        .fontSize(FontSizes.labelSmall)
        .fontFamily(Fonts.LATO_LIGHT)
        .fontColor(this.mood.stone)
        .letterSpacing(LetterSpacing.wide)
        .width('100%')
    }
    .width('100%')
    .padding({ left: 22, right: 22, top: 18, bottom: 18 })
    .alignItems(HorizontalAlign.Start)
  }
}
