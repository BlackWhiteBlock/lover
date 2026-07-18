/**
 * Letter detail — reading layout aligned with Android LetterDetail dialog feel.
 */
import { AppNav, AppDialog, LetterDetailParams } from '../core/nav/AppNav'
import { AppViewModel } from '../core/data/AppViewModel'
import { CoupleMood, SoleilMood, Colors, FontSizes, Radii } from '../core/design/Theme'
import { Letter, LetterType } from '../core/model/Models'
import type { MoodPalette } from '../core/design/Theme'
import { Fonts } from '../core/design/Fonts'

@Entry
@Component
struct LetterDetailPage {
  @State mood: MoodPalette = CoupleMood
  @State letter: Letter | null = null
  @State loading: boolean = true
  private vm: AppViewModel = AppViewModel.getInstance()
  private letterId: string = ''

  aboutToAppear(): void {
    this.mood = this.vm.data.linked ? CoupleMood : SoleilMood
    const params = AppNav.params<LetterDetailParams>()
    this.letterId = params?.letterId ?? ''
    if (this.letterId) this.loadLetter()
  }

  loadLetter(): void {
    const found = this.vm.data.letters.find((l) => l.id === this.letterId)
    if (found) this.letter = found
    this.loading = false
  }

  build() {
    Column() {
      Row() {
        Text('‹')
          .fontSize(28)
          .fontColor(this.mood.onSurface)
          .onClick(() => AppNav.back())
        Text('信件')
          .fontSize(FontSizes.titleLarge)
          .fontFamily(Fonts.GARAMOND)
          .fontColor(this.mood.onSurface)
          .margin({ left: 12 })
          .layoutWeight(1)
        if (this.letter && this.letter.senderId === this.vm.data.user?.id) {
          Text('删除')
            .fontSize(FontSizes.bodyMedium)
            .fontFamily(Fonts.LATO)
            .fontColor(Colors.error)
            .onClick(() => this.confirmDelete())
        }
      }
      .width('100%')
      .padding({ left: 16, right: 16, top: 12, bottom: 8 })

      if (this.loading) {
        LoadingProgress().width(40).height(40).color(this.mood.accent).margin({ top: 100 })
      } else if (this.letter) {
        Scroll() {
          Column({ space: 16 }) {
            Text(this.letter.type === LetterType.CAPSULE ? 'Time Capsule' : 'Instant')
              .fontSize(FontSizes.labelMedium)
              .fontFamily(Fonts.LATO_LIGHT)
              .fontColor(this.mood.accent)
              .letterSpacing(1.2)
              .alignSelf(ItemAlign.Start)

            Text(this.letter.title)
              .fontSize(FontSizes.headlineMedium)
              .fontFamily(Fonts.GARAMOND)
              .fontColor(this.mood.onSurface)
              .alignSelf(ItemAlign.Start)

            Text(this.letter.createdAt.substring(0, 10))
              .fontSize(FontSizes.bodySmall)
              .fontFamily(Fonts.LATO)
              .fontColor(this.mood.stone)
              .alignSelf(ItemAlign.Start)

            if (!this.letter.isUnlocked) {
              Column({ space: 8 }) {
                Text('这封信还未解锁')
                  .fontSize(FontSizes.bodyLarge)
                  .fontFamily(Fonts.LATO)
                  .fontColor(this.mood.stone)
                if (this.letter.unlockAt) {
                  Text(`解锁日期：${this.letter.unlockAt.substring(0, 10)}`)
                    .fontSize(FontSizes.bodyMedium)
                    .fontColor(this.mood.accent)
                }
                if (this.letter.unlockOnPartnerBind) {
                  Text('绑定另一半后自动解锁')
                    .fontSize(FontSizes.bodyMedium)
                    .fontColor(this.mood.accent)
                }
              }
              .width('100%')
              .padding(32)
              .backgroundColor(this.mood.softSurface)
              .borderRadius(Radii.medium)
              .alignItems(HorizontalAlign.Center)
              .margin({ top: 24 })
            }

            if (this.letter.isUnlocked && this.letter.content) {
              Text(this.letter.content)
                .fontSize(FontSizes.bodyLarge)
                .fontFamily(Fonts.GARAMOND)
                .fontColor(this.mood.onSurface)
                .lineHeight(28)
                .width('100%')
                .padding(20)
                .backgroundColor(this.mood.softSurface)
                .borderRadius(Radii.medium)
                .border({ width: 1, color: this.mood.softOutline })
            }

            Row() {
              Blank()
              Column({ space: 4 }) {
                Text('FROM')
                  .fontSize(FontSizes.labelSmall)
                  .fontFamily(Fonts.LATO_LIGHT)
                  .fontColor(this.mood.stone)
                  .letterSpacing(1.4)
                Text(this.letter.senderNickname)
                  .fontSize(FontSizes.bodyMedium)
                  .fontFamily(Fonts.LATO)
                  .fontColor(this.mood.onSurface)
              }
              .alignItems(HorizontalAlign.End)
            }
            .width('100%')
            .margin({ top: 24 })

            Button('收好')
              .width('100%')
              .height(52)
              .margin({ top: 16 })
              .borderRadius(Radii.small)
              .backgroundColor(this.mood.soft)
              .fontColor(Colors.white)
              .onClick(() => AppNav.back())
          }
          .width('100%')
          .padding({ left: 20, right: 20, bottom: 48 })
        }
        .layoutWeight(1)
        .scrollBar(BarState.Off)
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor(this.mood.background)
  }

  async confirmDelete(): Promise<void> {
    const ok = await AppDialog.confirm('确认删除', '确定要删除这封信吗？删除后无法恢复。')
    if (!ok) return
    try {
      await this.vm.deleteLetter(this.letterId)
      AppNav.back()
    } catch (_e) {
      AppDialog.toast('删除失败')
    }
  }
}
