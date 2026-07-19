/**
 * Edit personal avatar — mirrors Android AvatarEditSheet.
 */
import { AppNav } from '../core/nav/AppNav'
import { AppViewModel } from '../core/data/AppViewModel'
import { CoupleMood, SoleilMood, Colors, FontSizes } from '../core/design/Theme'
import type { MoodPalette } from '../core/design/Theme'
import { Fonts } from '../core/design/Fonts'
import { SoftPrimaryButton } from '../core/design/SoftInputs'
import { MediaFileInput } from '../core/model/Models'
import { MediaPicker } from '../core/media/MediaPicker'

@Entry
@Component
struct AvatarEditPage {
  @State mood: MoodPalette = CoupleMood
  @State nickname: string = '我'
  @State previewUri: string = ''
  @State saving: boolean = false
  @State errorMsg: string = ''
  @State hasPicked: boolean = false
  private vm: AppViewModel = AppViewModel.getInstance()
  private pickedFile: MediaFileInput | null = null

  aboutToAppear(): void {
    this.mood = this.vm.data.linked ? CoupleMood : SoleilMood
    this.nickname = this.vm.data.user?.nickname ?? '我'
    this.previewUri = this.vm.data.user?.avatarUrl ?? ''
  }

  private initial(): string {
    const n = this.nickname.trim()
    if (n.length === 0) {
      return '我'
    }
    return n.substring(0, 1)
  }

  build() {
    Column() {
      Row() {
        Text('‹')
          .fontSize(28)
          .fontColor(this.mood.onSurface)
          .width(40)
          .onClick(() => AppNav.back())
        Text('修改个人头像')
          .fontSize(FontSizes.titleLarge)
          .fontFamily(Fonts.GARAMOND)
          .fontColor(this.mood.onSurface)
          .layoutWeight(1)
      }
      .width('100%')
      .padding({ left: 12, right: 16, top: 12, bottom: 8 })

      Scroll() {
        Column({ space: 16 }) {
          Text('选择一张照片作为你的个人头像，绑定后双方都能看到')
            .fontSize(FontSizes.bodyMedium)
            .fontFamily(Fonts.LATO)
            .fontColor(this.mood.stone)
            .width('100%')

          Column({ space: 10 }) {
            Stack({ alignContent: Alignment.Center }) {
              if (this.previewUri.length > 0) {
                Image(this.previewUri)
                  .width(120)
                  .height(120)
                  .borderRadius(60)
                  .objectFit(ImageFit.Cover)
              } else {
                Text(this.initial())
                  .fontSize(40)
                  .fontFamily(Fonts.GARAMOND)
                  .fontColor(Colors.white)
              }
            }
            .width(120)
            .height(120)
            .borderRadius(60)
            .backgroundColor(this.mood.blush)
            .clip(true)
            .onClick(() => this.pickImage())

            Text('点击选择新头像')
              .fontSize(FontSizes.bodySmall)
              .fontFamily(Fonts.LATO)
              .fontColor(this.mood.stone)
              .onClick(() => this.pickImage())
          }
          .width('100%')
          .alignItems(HorizontalAlign.Center)

          if (this.errorMsg.length > 0) {
            Text(this.errorMsg)
              .fontSize(FontSizes.bodySmall)
              .fontFamily(Fonts.LATO)
              .fontColor(Colors.error)
              .width('100%')
          }

          SoftPrimaryButton({
            mood: this.mood,
            label: this.saving ? '保存中…' : '保存头像',
            isEnabled: !this.saving && this.hasPicked,
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
      const inputs = MediaPicker.toMediaInputs([picked])
      this.pickedFile = inputs.length > 0 ? inputs[0] : null
      if (!this.pickedFile) {
        return
      }
      this.hasPicked = true
      this.previewUri = picked.cachePath.startsWith('file://')
        ? picked.cachePath
        : `file://${picked.cachePath}`
      this.errorMsg = ''
    } catch (e) {
      this.errorMsg = e instanceof Error ? e.message : '选图失败'
    }
  }

  async save(): Promise<void> {
    if (!this.pickedFile) {
      return
    }
    this.saving = true
    this.errorMsg = ''
    try {
      const localPreview = this.previewUri.startsWith('file://') ? this.previewUri : ''
      await this.vm.updateAvatar(this.pickedFile, localPreview)
      AppNav.back()
    } catch (e) {
      this.errorMsg = e instanceof Error ? e.message : '保存失败'
    } finally {
      this.saving = false
    }
  }
}
