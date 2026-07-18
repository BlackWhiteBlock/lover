/**
 * Space / couple card edit — mirrors Android PersonalCardEditSheet / CoupleCardEditSheet.
 */
import { AppNav } from '../core/nav/AppNav'
import { AppViewModel } from '../core/data/AppViewModel'
import { CoupleMood, SoleilMood, Colors, FontSizes } from '../core/design/Theme'
import type { MoodPalette } from '../core/design/Theme'
import { Fonts } from '../core/design/Fonts'
import { SoftTextField, SoftPrimaryButton, LoverDateField } from '../core/design/SoftInputs'
import { MediaFileInput } from '../core/model/Models'
import { DateUtils } from '../core/util/DateUtils'

@Entry
@Component
struct SpaceEditPage {
  @State mood: MoodPalette = CoupleMood
  @State name: string = ''
  @State @Watch('onTogetherWatch') togetherDate: string = ''
  @State dateTouched: boolean = false
  @State saving: boolean = false
  @State errorMsg: string = ''
  @State previewUri: string = ''
  @State clearCover: boolean = false
  @State linked: boolean = false
  private vm: AppViewModel = AppViewModel.getInstance()
  private pickedFile: MediaFileInput | null = null
  private initialTogetherDate: string = ''

  aboutToAppear(): void {
    this.linked = this.vm.data.linked
    this.mood = this.linked ? CoupleMood : SoleilMood
    if (this.linked) {
      this.name = this.vm.data.couple?.name ?? '我们'
      this.initialTogetherDate = this.vm.data.couple?.togetherDate ?? ''
      this.togetherDate = this.initialTogetherDate
      this.previewUri = this.vm.data.user?.coupleCoverUrl ?? ''
    } else {
      const n = this.vm.data.couple?.name
      this.name = (n && n.trim().length > 0)
        ? n
        : (this.mood.solo ? '我的小宇宙' : '我们的小宇宙')
      this.previewUri = this.vm.data.user?.avatarUrl ?? ''
    }
  }

  onTogetherWatch(): void {
    if (this.togetherDate !== this.initialTogetherDate) {
      this.dateTouched = true
    }
  }

  private defaultSpaceName(): string {
    if (this.linked) {
      return '我们'
    }
    return this.mood.solo ? '我的小宇宙' : '我们的小宇宙'
  }

  private hasCover(): boolean {
    return this.previewUri.length > 0 && !this.clearCover
  }

  build() {
    Column() {
      Row() {
        Text('‹')
          .fontSize(28)
          .fontColor(this.mood.onSurface)
          .width(40)
          .onClick(() => AppNav.back())
        Text(this.linked ? '编辑我们' : '编辑我的空间')
          .fontSize(FontSizes.titleLarge)
          .fontFamily(Fonts.GARAMOND)
          .fontColor(this.mood.onSurface)
          .layoutWeight(1)
      }
      .width('100%')
      .padding({ left: 12, right: 16, top: 12, bottom: 8 })

      Scroll() {
        Column({ space: 16 }) {
          Text(this.linked
            ? '情侣头像仅影响你这边的展示；空间名称与在一起的日子会同步给双方'
            : '可以更换头像和空间名称，绑定后双方还会有共同的空间设置')
            .fontSize(FontSizes.bodyMedium)
            .fontFamily(Fonts.LATO)
            .fontColor(this.mood.stone)
            .width('100%')

          Column({ space: 10 }) {
            Stack({ alignContent: Alignment.Center }) {
              if (this.hasCover()) {
                Image(this.previewUri)
                  .width(104)
                  .height(104)
                  .borderRadius(52)
                  .objectFit(ImageFit.Cover)
              } else {
                Text('+')
                  .fontSize(32)
                  .fontColor(this.mood.soft)
              }
            }
            .width(104)
            .height(104)
            .borderRadius(52)
            .backgroundColor(this.mood.blush)
            .clip(true)
            .onClick(() => this.pickImage())

            Text(this.linked
              ? (this.hasCover() ? '点击更换情侣头像' : '设置一张我们一起的照片')
              : '点击更换头像')
              .fontSize(FontSizes.bodySmall)
              .fontFamily(Fonts.LATO)
              .fontColor(this.mood.stone)
              .onClick(() => this.pickImage())

            if (this.linked && this.hasCover()) {
              Text('取消情侣头像')
                .fontSize(FontSizes.bodyMedium)
                .fontFamily(Fonts.LATO)
                .fontColor(this.mood.stone)
                .onClick(() => {
                  this.pickedFile = null
                  this.clearCover = true
                  this.previewUri = ''
                })
            }
          }
          .width('100%')
          .alignItems(HorizontalAlign.Center)

          SoftTextField({
            mood: this.mood,
            label: '空间名称',
            value: $name,
            placeholder: this.defaultSpaceName(),
            maxLength: 40,
          })

          if (this.linked) {
            LoverDateField({
              mood: this.mood,
              label: '在一起的那天',
              value: $togetherDate,
              maxDate: DateUtils.formatDate(new Date()),
            })
            if (this.initialTogetherDate.length === 0 && !this.dateTouched) {
              Text('尚未设置在一起的日子，选择日期后保存即可同步给双方')
                .fontSize(FontSizes.bodySmall)
                .fontFamily(Fonts.LATO)
                .fontColor(this.mood.stone)
                .width('100%')
            }
          }

          if (this.errorMsg.length > 0) {
            Text(this.errorMsg)
              .fontSize(FontSizes.bodySmall)
              .fontFamily(Fonts.LATO)
              .fontColor(Colors.error)
              .width('100%')
          }

          SoftPrimaryButton({
            mood: this.mood,
            label: this.saving ? '保存中…' : '保存',
            isEnabled: !this.saving && this.name.trim().length > 0,
            onTap: () => this.save(),
          })
        }
        .width('100%')
        .padding({ left: 24, right: 24, bottom: 40, top: 8 })
      }
      .layoutWeight(1)
      .scrollBar(BarState.Off)
    }
    .width('100%')
    .height('100%')
    .backgroundColor(this.mood.background)
  }

  async pickImage(): Promise<void> {
    try {
      const picked = await this.vm.pickAvatarPicked()
      if (!picked) {
        return
      }
      this.pickedFile = MediaPickerInputFrom(picked.data, picked.fileName, picked.mimeType, picked.isVideo)
      this.clearCover = false
      this.previewUri = picked.cachePath.startsWith('file://')
        ? picked.cachePath
        : `file://${picked.cachePath}`
    } catch (e) {
      this.errorMsg = e instanceof Error ? e.message : '选图失败'
    }
  }

  async save(): Promise<void> {
    this.saving = true
    this.errorMsg = ''
    try {
      if (this.linked) {
        let dateToSave: string | null = null
        const trimmed = this.togetherDate.trim()
        if (this.dateTouched) {
          dateToSave = trimmed.length > 0 ? trimmed : null
        } else if (this.initialTogetherDate.length > 0) {
          dateToSave = trimmed.length > 0 ? trimmed : this.initialTogetherDate
        }
        const nameToSave = this.name.trim().length > 0 ? this.name.trim() : '我们'
        await this.vm.updateCoupleCard(
          nameToSave,
          dateToSave,
          this.pickedFile,
          this.clearCover && this.pickedFile === null,
        )
      } else {
        const fallback = this.defaultSpaceName()
        const nameToSave = this.name.trim().length > 0 ? this.name.trim() : fallback
        await this.vm.updatePersonalCard(nameToSave, this.pickedFile)
      }
      AppNav.back()
    } catch (e) {
      this.errorMsg = e instanceof Error ? e.message : '保存失败'
    } finally {
      this.saving = false
    }
  }
}

function MediaPickerInputFrom(
  data: ArrayBuffer,
  fileName: string,
  mimeType: string,
  isVideo: boolean,
): MediaFileInput {
  const input = new MediaFileInput()
  input.data = data
  input.fileName = fileName
  input.mimeType = mimeType
  input.isVideo = isVideo
  return input
}
