/**
 * Top notice strip — mirror of Android LoverNotice.
 */
import { Fonts } from './Fonts'
import { Colors, FontSizes, Radii } from './Theme'
import type { MoodPalette } from './Theme'

export type NoticeKind = 'info' | 'success' | 'error'

@Component
export struct LoverNoticeBar {
  @Prop mood: MoodPalette
  @Prop message: string = ''
  @Prop kind: NoticeKind = 'info'
  onDismiss: () => void = () => {}

  private bg(): string {
    if (this.kind === 'error') return Colors.error
    if (this.kind === 'success') return '#5A9E6F'
    return this.mood.accent
  }

  build() {
    if (!this.message || this.message.length === 0) {
      Column().width(0).height(0)
    } else {
      Row() {
        Text(this.message)
          .fontSize(FontSizes.bodySmall)
          .fontFamily(Fonts.LATO)
          .fontColor(Colors.white)
          .layoutWeight(1)
          .maxLines(3)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        Text('×')
          .fontSize(18)
          .fontColor(Colors.white)
          .opacity(0.85)
          .margin({ left: 10 })
          .onClick(() => this.onDismiss())
      }
      .width('100%')
      .padding({ left: 16, right: 16, top: 12, bottom: 12 })
      .backgroundColor(this.bg())
      .borderRadius(Radii.small)
      .shadow({ radius: 10, color: 'rgba(0,0,0,0.12)', offsetY: 4 })
      .onClick(() => this.onDismiss())
    }
  }
}
