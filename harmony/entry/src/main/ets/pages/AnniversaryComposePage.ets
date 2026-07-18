/**
 * Anniversary compose — Soft form shell aligned with Android.
 */
import { AppNav, AppDialog, AnniversaryComposeParams } from '../core/nav/AppNav'
import { AppViewModel } from '../core/data/AppViewModel'
import { CoupleMood, SoleilMood, Colors, FontSizes, Radii } from '../core/design/Theme'
import type { MoodPalette } from '../core/design/Theme'
import { Fonts } from '../core/design/Fonts'
import { SoftTextField, SoftChip, SoftPrimaryButton, LoverDateField } from '../core/design/SoftInputs'
import { AnniversaryType } from '../core/model/Models'
import { DateUtils } from '../core/util/DateUtils'

@Entry
@Component
struct AnniversaryComposePage {
  @State mood: MoodPalette = CoupleMood
  @State title: string = ''
  @State date: string = ''
  @State type: AnniversaryType = AnniversaryType.YEARLY
  @State saving: boolean = false
  @State errorMsg: string = ''
  private vm: AppViewModel = AppViewModel.getInstance()
  private editId: string = ''

  aboutToAppear(): void {
    this.mood = this.vm.data.linked ? CoupleMood : SoleilMood
    const params = AppNav.params<AnniversaryComposeParams>()
    this.editId = params?.id ?? ''
    if (this.editId) {
      const found = this.vm.data.anniversaries.find((a) => a.id === this.editId)
      if (found) {
        this.title = found.title
        this.date = found.date
        this.type = found.type
      }
    } else {
      const d = new Date()
      d.setMonth(d.getMonth() + 1)
      this.date = DateUtils.formatDate(d)
    }
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
          Text(this.editId ? '编辑纪念日' : '新建纪念日')
            .fontSize(FontSizes.titleLarge)
            .fontFamily(Fonts.GARAMOND)
            .fontColor(this.mood.onSurface)
          Text(this.editId ? 'EDIT DATE' : 'NEW DATE')
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
        Column({ space: 16 }) {
          Text(this.mood.solo ? '给自己留一个温柔的日子' : '把重要的日子记在一起')
            .fontSize(FontSizes.bodyMedium)
            .fontFamily(Fonts.LATO)
            .fontColor(this.mood.stone)
            .width('100%')
            .padding(14)
            .backgroundColor(this.mood.blush)
            .borderRadius(Radii.small)

          SoftTextField({
            mood: this.mood,
            label: '名称',
            value: $title,
            placeholder: '例如：第一次见面',
            maxLength: 30,
          })

          LoverDateField({ mood: this.mood, label: '日期', value: $date })

          Column({ space: 8 }) {
            Text('类型')
              .fontSize(FontSizes.bodySmall)
              .fontFamily(Fonts.LATO)
              .fontColor(this.mood.accent)
              .alignSelf(ItemAlign.Start)
            Row({ space: 10 }) {
              SoftChip({
                mood: this.mood, label: '年度纪念',
                selected: this.type === AnniversaryType.YEARLY,
                onSelect: () => { this.type = AnniversaryType.YEARLY },
              })
              SoftChip({
                mood: this.mood, label: '里程碑',
                selected: this.type === AnniversaryType.MILESTONE,
                onSelect: () => { this.type = AnniversaryType.MILESTONE },
              })
            }
          }
          .width('100%')
          .alignItems(HorizontalAlign.Start)

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

      Column({ space: 10 }) {
        SoftPrimaryButton({
          mood: this.mood,
          label: this.saving ? '保存中…' : '保存',
          isEnabled: !this.saving,
          onTap: () => this.save(),
        })
        if (this.editId) {
          Button('删除纪念日')
            .width('100%')
            .height(48)
            .backgroundColor(Colors.white)
            .fontColor(Colors.error)
            .border({ width: 1, color: Colors.error })
            .borderRadius(Radii.small)
            .enabled(!this.saving)
            .onClick(() => this.remove())
        }
      }
      .width('100%')
      .padding({ left: 20, right: 20, bottom: 24, top: 8 })
      .backgroundColor(this.mood.background)
    }
    .width('100%')
    .height('100%')
    .backgroundColor(this.mood.background)
  }

  async save(): Promise<void> {
    this.saving = true
    this.errorMsg = ''
    try {
      if (this.editId) {
        await this.vm.updateAnniversary(this.editId, this.title, this.date, this.type)
      } else {
        await this.vm.addAnniversary(this.title, this.date, this.type)
      }
      AppNav.back()
    } catch (e) {
      this.errorMsg = e instanceof Error ? e.message : '保存失败'
    } finally {
      this.saving = false
    }
  }

  async remove(): Promise<void> {
    const ok = await AppDialog.confirm('删除纪念日', `确定删除「${this.title}」？`)
    if (!ok) return
    this.saving = true
    try {
      await this.vm.deleteAnniversary(this.editId)
      AppNav.back()
    } catch (e) {
      this.errorMsg = e instanceof Error ? e.message : '删除失败'
    } finally {
      this.saving = false
    }
  }
}
