/**
 * Page header — Chinese title + English subtitle (uppercase).
 * Optional trailing action (e.g. unread badge on Timeline).
 */
import { Fonts } from './Fonts'
import { FontSizes, LetterSpacing } from './Theme'
import type { MoodPalette } from './Theme'

@Component
export struct PageHeader {
  @Prop mood: MoodPalette
  @Prop title: string = ''
  @Prop subtitle: string = ''
  /** 底部内边距；时光页有年份筛选时可收紧 */
  @Prop bottomPadding: number = 18
  @BuilderParam actionBuilder: () => void = this.emptyAction

  @Builder
  emptyAction() {
  }

  build() {
    Column({ space: 4 }) {
      Row() {
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
        .layoutWeight(1)
        .alignItems(HorizontalAlign.Start)

        this.actionBuilder()
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)
    }
    .width('100%')
    .padding({ left: 22, right: 22, top: 18, bottom: this.bottomPadding })
    .alignItems(HorizontalAlign.Start)
  }
}
