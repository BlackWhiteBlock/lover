/**
 * Letter compose — Soft form aligned with Android LetterComposeScreen.
 */
import { AppNav } from '../core/nav/AppNav'
import { AppViewModel } from '../core/data/AppViewModel'
import { CoupleMood, SoleilMood, Colors, FontSizes, Radii } from '../core/design/Theme'
import type { MoodPalette } from '../core/design/Theme'
import { Fonts } from '../core/design/Fonts'
import { SoftTextField, SoftChip, SoftPrimaryButton, LoverDateField } from '../core/design/SoftInputs'
import { LetterType } from '../core/model/Models'
import { DateUtils } from '../core/util/DateUtils'

@Entry
@Component
struct LetterComposePage {
  @State mood: MoodPalette = CoupleMood
  @State title: string = ''
  @State content: string = ''
  @State type: LetterType = LetterType.CAPSULE
  @State unlockDate: string = ''
  @State unlockOnPartnerBind: boolean = false
  @State saving: boolean = false
  @State errorMsg: string = ''
  private vm: AppViewModel = AppViewModel.getInstance()

  aboutToAppear(): void {
    const linked = this.vm.data.linked
    this.mood = linked ? CoupleMood : SoleilMood
    this.type = linked ? LetterType.INSTANT : LetterType.CAPSULE
    const d = new Date()
    d.setMonth(d.getMonth() + 1)
    this.unlockDate = DateUtils.formatDate(d)
  }

  build() {
    Column() {
      Row() {
        Text('‹')
          .fontSize(28)
          .fontColor(this.mood.onSurface)
          .width(40)
          .onClick(() => AppNav.back())
        Column({ space: 2 }) {
          Text('写给 TA')
            .fontSize(FontSizes.titleLarge)
            .fontFamily(Fonts.GARAMOND)
            .fontColor(this.mood.onSurface)
          Text('LETTER')
            .fontSize(FontSizes.labelSmall)
            .fontFamily(Fonts.LATO_LIGHT)
            .fontColor(this.mood.stone)
            .letterSpacing(1.2)
        }
        .alignItems(HorizontalAlign.Start)
        .layoutWeight(1)
      }
      .width('100%')
      .padding({ left: 12, right: 16, top: 12, bottom: 8 })
      .linearGradient({
        angle: 180,
        colors: [[this.mood.blush, 0], [this.mood.background, 1]],
      })

      Scroll() {
        Column({ space: 14 }) {
          SoftTextField({
            mood: this.mood, label: '标题', value: $title,
            placeholder: '给 TA 的标题', maxLength: 40,
          })
          SoftTextField({
            mood: this.mood, label: '正文', value: $content,
            placeholder: '写下想说的话…', multiline: true, areaHeight: 160,
          })

          Column({ space: 8 }) {
            Text('类型')
              .fontSize(FontSizes.bodySmall)
              .fontFamily(Fonts.LATO)
              .fontColor(this.mood.accent)
              .alignSelf(ItemAlign.Start)
            Row({ space: 10 }) {
              SoftChip({
                mood: this.mood, label: '即时信',
                selected: this.type === LetterType.INSTANT,
                isEnabled: this.vm.data.linked,
                onSelect: () => { this.type = LetterType.INSTANT },
              })
              SoftChip({
                mood: this.mood, label: '时间胶囊',
                selected: this.type === LetterType.CAPSULE,
                onSelect: () => { this.type = LetterType.CAPSULE },
              })
            }
            if (!this.vm.data.linked) {
              Text('未绑定另一半时，只能写时间胶囊')
                .fontSize(FontSizes.bodySmall)
                .fontFamily(Fonts.LATO_LIGHT)
                .fontColor(this.mood.stone)
            }
          }
          .width('100%')
          .alignItems(HorizontalAlign.Start)

          if (this.type === LetterType.CAPSULE) {
            Row() {
              Checkbox({ name: 'bindUnlock', group: 'letter' })
                .select(this.unlockOnPartnerBind)
                .selectedColor(this.mood.accent)
                .onChange((v: boolean) => { this.unlockOnPartnerBind = v })
              Text('绑定另一半后自动开启')
                .fontSize(FontSizes.bodyMedium)
                .fontFamily(Fonts.LATO)
                .fontColor(this.mood.onSurface)
                .margin({ left: 8 })
            }
            .width('100%')
            .alignItems(VerticalAlign.Center)

            if (!this.unlockOnPartnerBind) {
              LoverDateField({
                mood: this.mood,
                label: '解锁日期（须晚于今天）',
                value: $unlockDate,
                minDate: DateUtils.formatDate(new Date(Date.now() + 24 * 60 * 60 * 1000)),
              })
            }
          }

          if (this.errorMsg) {
            Text(this.errorMsg)
              .fontSize(FontSizes.bodySmall)
              .fontColor(Colors.error)
              .alignSelf(ItemAlign.Start)
          }
        }
        .width('100%')
        .padding({ left: 20, right: 20, bottom: 20 })
      }
      .layoutWeight(1)
      .scrollBar(BarState.Off)

      Column() {
        SoftPrimaryButton({
          mood: this.mood,
          label: this.saving ? '寄出中…' : '寄出',
          isEnabled: !this.saving,
          onTap: () => this.save(),
        })
      }
      .width('100%')
      .padding({ left: 20, right: 20, bottom: 24, top: 8 })
    }
    .width('100%')
    .height('100%')
    .backgroundColor(this.mood.background)
  }

  async save(): Promise<void> {
    this.saving = true
    this.errorMsg = ''
    try {
      await this.vm.addLetter(
        this.title, this.content, this.type,
        this.unlockOnPartnerBind ? null : this.unlockDate,
        this.unlockOnPartnerBind,
      )
      AppNav.back()
    } catch (e) {
      this.errorMsg = e instanceof Error ? e.message : '保存失败'
    } finally {
      this.saving = false
    }
  }
}
