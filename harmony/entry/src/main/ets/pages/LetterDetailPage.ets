/**
 * Letter detail — 全屏阅读；未拆信进入时播放拆信动效。
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
  @State playOpenAnimation: boolean = false
  @State showContent: boolean = true
  @State flap: number = 0
  @State lift: number = 0
  @State contentAlpha: number = 1
  private vm: AppViewModel = AppViewModel.getInstance()
  private letterId: string = ''

  aboutToAppear(): void {
    this.mood = this.vm.data.linked ? CoupleMood : SoleilMood
    const params = AppNav.params<LetterDetailParams>()
    this.letterId = params?.letterId ?? ''
    this.playOpenAnimation = params?.playOpenAnimation ?? false
    if (this.letterId) {
      void this.loadLetter()
    }
  }

  async loadLetter(): Promise<void> {
    const found = this.vm.data.letters.find((l) => l.id === this.letterId)
    if (found) {
      this.letter = found
    } else {
      try {
        this.letter = await this.vm.fetchLetterDetail(this.letterId)
      } catch (_e) {
        this.letter = null
      }
    }
    this.loading = false
    if (this.playOpenAnimation && this.letter?.isUnlocked) {
      this.showContent = false
      this.contentAlpha = 0
      this.runOpenAnimation()
    } else {
      this.showContent = true
      this.contentAlpha = 1
    }
  }

  private animTo(duration: number, update: () => void, onFinish?: () => void): void {
    try {
      this.getUIContext().animateTo({
        duration: duration,
        curve: Curve.EaseOut,
        onFinish: onFinish,
      }, update)
    } catch (_e) {
      animateTo({
        duration: duration,
        curve: Curve.EaseOut,
        onFinish: onFinish,
      }, update)
    }
  }

  private runOpenAnimation(): void {
    this.lift = 0
    this.flap = 0
    this.animTo(420, () => {
      this.lift = 1
    }, () => {
      this.animTo(650, () => {
        this.flap = 1
      }, () => {
        setTimeout(() => {
          this.showContent = true
          this.animTo(480, () => {
            this.contentAlpha = 1
          })
        }, 180)
      })
    })
  }

  private statusLabel(): string {
    if (!this.letter) {
      return ''
    }
    if (this.letter.deliveryStatus === 'read') {
      return '已阅'
    }
    if (this.letter.deliveryStatus === 'sent') {
      return '已寄'
    }
    return ''
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
        Stack() {
          // 拆信动效
          if (this.playOpenAnimation && this.letter.isUnlocked && this.contentAlpha < 0.95) {
            Column({ space: 18 }) {
              Column() {
                Column()
                  .width(200)
                  .height(120)
                  .borderRadius({ topLeft: 18, topRight: 18 })
                  .backgroundColor('rgba(251,113,133,0.35)')
                  .translate({ y: -36 * this.flap })
                  .rotate({ angle: -8 * this.flap })
                  .opacity(1 - 0.85 * this.flap)
                  .scale({ y: 1 - 0.25 * this.flap })
                Column()
                  .width(200)
                  .height(110)
                  .borderRadius({ bottomLeft: 18, bottomRight: 18 })
                  .backgroundColor('rgba(255,255,255,0.96)')
              }
              .translate({ y: -12 * this.lift })
              .scale({ x: 0.96 + 0.04 * this.lift, y: 0.96 + 0.04 * this.lift })

              Text('正在拆开信件…')
                .fontSize(FontSizes.bodyMedium)
                .fontFamily(Fonts.LATO)
                .fontColor(this.mood.stone)
            }
            .width('100%')
            .margin({ top: 48 })
            .alignItems(HorizontalAlign.Center)
          }

          if (this.showContent) {
            Scroll() {
              Column({ space: 16 }) {
                Row({ space: 8 }) {
                  Text(this.letter.type === LetterType.CAPSULE ? 'Time Capsule' : 'Instant')
                    .fontSize(FontSizes.labelMedium)
                    .fontFamily(Fonts.LATO_LIGHT)
                    .fontColor(this.mood.accent)
                    .letterSpacing(1.2)
                  if (this.letter.senderId === this.vm.data.user?.id && this.statusLabel().length > 0) {
                    Text(this.statusLabel())
                      .fontSize(FontSizes.labelSmall)
                      .fontFamily(Fonts.LATO)
                      .fontColor(this.letter.deliveryStatus === 'read' ? this.mood.soft : this.mood.stone)
                  }
                }
                .alignSelf(ItemAlign.Start)

                Text(this.letter.title)
                  .fontSize(FontSizes.headlineMedium)
                  .fontFamily(Fonts.GARAMOND)
                  .fontColor(this.mood.onSurface)
                  .alignSelf(ItemAlign.Start)

                Text(`${this.letter.senderNickname} · ${this.letter.createdAt.substring(0, 10)}`)
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
              .opacity(this.contentAlpha)
            }
            .width('100%')
            .height('100%')
            .scrollBar(BarState.Off)
          }
        }
        .layoutWeight(1)
        .width('100%')
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor(this.mood.background)
  }

  async confirmDelete(): Promise<void> {
    const ok = await AppDialog.confirm('确认删除', '确定要删除这封信吗？删除后无法恢复。')
    if (!ok) {
      return
    }
    try {
      await this.vm.deleteLetter(this.letterId)
      AppNav.back()
    } catch (_e) {
      AppDialog.toast('删除失败')
    }
  }
}
