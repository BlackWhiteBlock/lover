/**
 * Media compose — mirrors Android MediaComposeScreen.
 * FAB opens this page first; picker runs inside (empty-state / continue add).
 */
import { AppNav } from '../core/nav/AppNav'
import { AppViewModel } from '../core/data/AppViewModel'
import { CoupleMood, SoleilMood, Colors, FontSizes, Radii } from '../core/design/Theme'
import type { MoodPalette } from '../core/design/Theme'
import { Fonts } from '../core/design/Fonts'
import { SoftTextField, SoftPrimaryButton, LoverDateField } from '../core/design/SoftInputs'
import { MediaFileInput } from '../core/model/Models'
import { DateUtils } from '../core/util/DateUtils'
import { MediaTakenDateReader } from '../core/media/MediaTakenDateReader'

const MAX_MEDIA_PICK: number = 20

@Entry
@Component
struct MediaComposePage {
  @State mood: MoodPalette = CoupleMood
  @State caption: string = ''
  @State date: string = ''
  @State saving: boolean = false
  @State picking: boolean = false
  @State errorMsg: string = ''
  @State drafts: MediaFileInput[] = []
  private vm: AppViewModel = AppViewModel.getInstance()

  aboutToAppear(): void {
    this.mood = this.vm.data.linked ? CoupleMood : SoleilMood
    this.date = this.vm.todayIso()
    this.syncDrafts()
    // If drafts already present (rare), still try first-asset date once
    if (this.drafts.length > 0) {
      this.prefillDateFromFirst()
    }
  }

  private syncDrafts(): void {
    this.drafts = [...this.vm.pendingComposeFiles]
  }

  /**
   * Android: only when list was empty before pick — use first asset taken date.
   * User can still change LoverDateField manually.
   */
  private async prefillDateFromFirst(): Promise<void> {
    const first = this.vm.pendingComposeFiles.length > 0
      ? this.vm.pendingComposeFiles[0]
      : null
    if (!first || first.cachePath.length === 0) {
      return
    }
    try {
      const iso = await MediaTakenDateReader.readIsoDate(first.cachePath, first.isVideo)
      if (iso && iso.length >= 8) {
        this.date = iso
      }
    } catch (_e) {
      // keep current date (usually today)
    }
  }

  private remaining(): number {
    return Math.max(0, MAX_MEDIA_PICK - this.drafts.length)
  }

  private subtitle(): string {
    if (this.drafts.length === 0) {
      return '添加照片或视频'
    }
    return `已选 ${this.drafts.length} 项`
  }

  private title(): string {
    return this.mood.solo ? '存下这一刻的时光' : '存下这些属于我们的时光'
  }

  build() {
    Column() {
      // Top bar — ComposerScaffold style
      Column() {
        Row() {
          Text('‹')
            .fontSize(28)
            .fontColor(this.mood.accent)
            .width(40)
            .onClick(() => this.close())
          Column({ space: 2 }) {
            Text(this.title())
              .fontSize(FontSizes.titleLarge)
              .fontFamily(Fonts.GARAMOND)
              .fontColor(this.mood.accent)
              .maxLines(1)
              .textOverflow({ overflow: TextOverflow.Ellipsis })
            Text(this.subtitle())
              .fontSize(FontSizes.labelSmall)
              .fontFamily(Fonts.LATO)
              .fontColor(this.mood.stone)
          }
          .alignItems(HorizontalAlign.Start)
          .layoutWeight(1)
          .padding({ left: 4, right: 4 })
        }
        .width('100%')
        // Match AnniversaryComposePage title bar inset from status bar / top edge
        .padding({ left: 12, right: 16, top: 12, bottom: 8 })
      }
      .width('100%')
      .linearGradient({
        angle: 180,
        colors: [[this.withAlpha(this.mood.blush, 0.85), 0], [this.mood.background, 1]],
      })

      Scroll() {
        Column({ space: 18 }) {
          if (this.drafts.length === 0) {
            // Empty pick surface
            Column({ space: 12 }) {
              Stack({ alignContent: Alignment.Center }) {
                Text('+')
                  .fontSize(28)
                  .fontColor(this.mood.soft)
              }
              .width(56)
              .height(56)
              .borderRadius(28)
              .backgroundColor(this.mood.blush)

              Text('添加照片或视频')
                .fontSize(FontSizes.bodyLarge)
                .fontFamily(Fonts.LATO)
                .fontColor(this.mood.accent)
                .fontWeight(FontWeight.Medium)
              Text(`最多 ${MAX_MEDIA_PICK} 项，支持混选`)
                .fontSize(FontSizes.bodySmall)
                .fontFamily(Fonts.LATO)
                .fontColor(this.mood.stone)
            }
            .width('100%')
            .height(180)
            .justifyContent(FlexAlign.Center)
            .alignItems(HorizontalAlign.Center)
            .borderRadius(28)
            .backgroundColor(this.mood.softSurface)
            .border({ width: 1, color: this.mood.softOutline })
            .onClick(() => this.pickMore())
          } else {
            Grid() {
              ForEach(this.drafts, (item: MediaFileInput) => {
                GridItem() {
                  Stack({ alignContent: Alignment.TopEnd }) {
                    if (this.draftPreview(item).length > 0) {
                      Image(this.previewSrc(this.draftPreview(item)))
                        .width('100%')
                        .height('100%')
                        .objectFit(ImageFit.Cover)
                        .borderRadius(16)
                    } else {
                      Column() {
                        Text(item.isVideo ? '视频' : '')
                          .fontSize(FontSizes.labelSmall)
                          .fontColor(this.mood.stone)
                      }
                      .width('100%')
                      .height('100%')
                      .borderRadius(16)
                      .backgroundColor(this.mood.blush)
                      .justifyContent(FlexAlign.Center)
                    }
                    if (item.isVideo) {
                      Text('▶')
                        .fontSize(14)
                        .fontColor(Colors.white)
                        .padding(6)
                        .borderRadius(14)
                        .backgroundColor('rgba(0,0,0,0.35)')
                        .position({ x: '38%', y: '38%' })
                    }
                    Text('×')
                      .fontSize(14)
                      .fontColor(Colors.white)
                      .width(24)
                      .height(24)
                      .textAlign(TextAlign.Center)
                      .borderRadius(12)
                      .backgroundColor('rgba(0,0,0,0.45)')
                      .margin({ top: 6, right: 6 })
                      .onClick(() => this.removeById(item.draftId))
                  }
                  .width('100%')
                  .aspectRatio(1)
                  .clip(true)
                }
              }, (item: MediaFileInput) => item.draftId.length > 0 ? item.draftId : item.fileName)
            }
            .columnsTemplate('1fr 1fr 1fr')
            .columnsGap(8)
            .rowsGap(8)
            .width('100%')

            Text('点击右上角 × 可移除')
              .fontSize(FontSizes.bodySmall)
              .fontFamily(Fonts.LATO)
              .fontColor(this.mood.stone)
              .width('100%')

            if (this.remaining() > 0) {
              Text(this.picking ? '选择中…' : `继续添加（还可 ${this.remaining()} 项）`)
                .fontSize(FontSizes.bodyMedium)
                .fontFamily(Fonts.LATO)
                .fontColor(this.mood.soft)
                .padding({ top: 4, bottom: 4 })
                .onClick(() => this.pickMore())
            }
          }

          SoftTextField({
            mood: this.mood,
            label: '这一刻想说…',
            value: $caption,
            placeholder: '写给这段时光的一句话',
            maxLength: 200,
            multiline: true,
            areaHeight: 110,
          })

          LoverDateField({
            mood: this.mood,
            label: '记录日期',
            value: $date,
            maxDate: DateUtils.formatDate(new Date()),
          })

          Text('优先使用第一张照片的拍摄日期，可手动修改')
            .fontSize(FontSizes.bodySmall)
            .fontFamily(Fonts.LATO)
            .fontColor(this.mood.stone)
            .width('100%')

          if (this.errorMsg.length > 0) {
            Text(this.errorMsg)
              .fontSize(FontSizes.bodySmall)
              .fontFamily(Fonts.LATO)
              .fontColor(Colors.error)
              .width('100%')
          }
        }
        .width('100%')
        .padding({ left: 20, right: 20, bottom: 20, top: 8 })
      }
      .layoutWeight(1)
      .scrollBar(BarState.Off)

      Column() {
        SoftPrimaryButton({
          mood: this.mood,
          label: this.saving ? '上传中…' : '保存',
          isEnabled: !this.saving && !this.picking && this.drafts.length > 0,
          onTap: () => {
            this.save()
          },
        })
      }
      .width('100%')
      .padding({ left: 20, right: 20, bottom: 24, top: 8 })
      .backgroundColor(this.withAlpha(this.mood.background, 0.96))
    }
    .width('100%')
    .height('100%')
    .backgroundColor(this.mood.background)
  }

  private close(): void {
    this.vm.clearComposePending()
    AppNav.back()
  }

  private removeById(draftId: string): void {
    const list = this.vm.pendingComposeFiles
    let idx = -1
    for (let i = 0; i < list.length; i++) {
      if (list[i].draftId === draftId) {
        idx = i
        break
      }
    }
    if (idx >= 0) {
      this.vm.removeComposeFileAt(idx)
      this.syncDrafts()
    }
  }

  async pickMore(): Promise<void> {
    if (this.picking || this.remaining() <= 0) {
      return
    }
    this.picking = true
    this.errorMsg = ''
    const wasEmpty = this.drafts.length === 0
    try {
      const n = await this.vm.pickForCompose(MAX_MEDIA_PICK, true)
      this.syncDrafts()
      if (n === 0 && this.vm.errorMessage) {
        this.errorMsg = this.vm.errorMessage
        this.vm.errorMessage = null
      }
      // Align Android MediaComposeScreen.mergePicked: prefill only on first batch
      if (wasEmpty && n > 0) {
        await this.prefillDateFromFirst()
      }
    } catch (e) {
      this.errorMsg = e instanceof Error ? e.message : '选择媒体失败'
    } finally {
      this.picking = false
    }
  }

  save(): void {
    if (this.drafts.length === 0 || this.saving) {
      return
    }
    // Mirror Android: enqueue background upload, leave page immediately.
    const files = [...this.vm.pendingComposeFiles]
    const caption = this.caption
    const date = this.date.length > 0 ? this.date : this.vm.todayIso()
    this.saving = true
    this.vm.addMedia(files, caption, date)
    AppNav.back()
  }

  private draftPreview(item: MediaFileInput): string {
    if (item.previewPath.length > 0) {
      return item.previewPath
    }
    return item.cachePath
  }

  private previewSrc(path: string): string {
    if (path.startsWith('file://') || path.startsWith('data:')) {
      return path
    }
    return `file://${path}`
  }

  private withAlpha(hex: string, alpha: number): string {
    if (hex.length === 7 && hex.startsWith('#')) {
      const r = parseInt(hex.substring(1, 3), 16)
      const g = parseInt(hex.substring(3, 5), 16)
      const b = parseInt(hex.substring(5, 7), 16)
      return `rgba(${r},${g},${b},${alpha})`
    }
    return hex
  }
}
