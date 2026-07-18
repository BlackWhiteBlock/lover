/**
 * Media edit — mirrors Android MediaEditScreen essentials:
 * media grid preview, caption, date (owner), delete (owner).
 */
import { AppNav, AppDialog, MediaDetailParams } from '../core/nav/AppNav'
import { AppViewModel } from '../core/data/AppViewModel'
import { CoupleMood, SoleilMood, Colors, FontSizes } from '../core/design/Theme'
import type { MoodPalette } from '../core/design/Theme'
import { Fonts } from '../core/design/Fonts'
import { SoftTextField, SoftPrimaryButton, LoverDateField } from '../core/design/SoftInputs'
import { MediaItem, MediaAssetPart, MediaType } from '../core/model/Models'
import { DateUtils } from '../core/util/DateUtils'

class EditCell {
  id: string = ''
  url: string = ''
  isVideo: boolean = false
  partId: string = ''
}

@Entry
@Component
struct MediaEditPage {
  @State mood: MoodPalette = CoupleMood
  @State caption: string = ''
  @State date: string = ''
  @State cells: EditCell[] = []
  @State saving: boolean = false
  @State deleting: boolean = false
  @State errorMsg: string = ''
  @State isOwner: boolean = false
  @State ready: boolean = false
  private vm: AppViewModel = AppViewModel.getInstance()
  private mediaId: string = ''
  private originalCaption: string = ''
  private originalDate: string = ''

  aboutToAppear(): void {
    this.mood = this.vm.data.linked ? CoupleMood : SoleilMood
    const params = AppNav.params<MediaDetailParams>()
    this.mediaId = params?.mediaId ?? ''
    this.bootstrap()
  }

  async bootstrap(): Promise<void> {
    let item = this.vm.data.media.find((m: MediaItem) => m.id === this.mediaId)
    if (!item) {
      this.ready = true
      return
    }
    // Prefer signed originals (same as opening detail)
    try {
      item = await this.vm.openMediaDetail(item)
    } catch (_e) {
      // keep list URLs
    }
    this.caption = item.caption
    this.date = item.mediaDate
    this.originalCaption = item.caption
    this.originalDate = item.mediaDate
    const uid = this.vm.data.user?.id ?? ''
    this.isOwner = uid.length > 0 && !!item.uploaderId && item.uploaderId === uid
    this.cells = this.buildCells(item)
    this.ready = true
  }

  private buildCells(item: MediaItem): EditCell[] {
    const assets = MediaItem.sortedAssets(item)
    const out: EditCell[] = []
    for (let i = 0; i < assets.length; i++) {
      const part = assets[i]
      const c = new EditCell()
      c.id = part.id.length > 0 ? part.id : `a_${i}_${part.assetId}`
      c.partId = part.id
      c.isVideo = part.type === MediaType.VIDEO
      c.url = this.partPreview(part)
      out.push(c)
    }
    return out
  }

  private partPreview(part: MediaAssetPart): string {
    return MediaItem.partUrl(part)
  }

  private dirty(): boolean {
    if (this.caption.trim() !== this.originalCaption.trim()) {
      return true
    }
    if (this.isOwner && this.date !== this.originalDate) {
      return true
    }
    return false
  }

  private removeCell(cellId: string): void {
    if (!this.isOwner || this.cells.length <= 1) {
      return
    }
    const next: EditCell[] = []
    for (let i = 0; i < this.cells.length; i++) {
      if (this.cells[i].id !== cellId) {
        next.push(this.cells[i])
      }
    }
    this.cells = next
  }

  build() {
    Column() {
      Row() {
        Text('‹')
          .fontSize(28)
          .fontColor(this.mood.accent)
          .width(40)
          .onClick(() => AppNav.back())
        Column({ space: 2 }) {
          Text('编辑时光')
            .fontSize(FontSizes.titleLarge)
            .fontFamily(Fonts.GARAMOND)
            .fontColor(this.mood.accent)
          Text(this.isOwner ? '可修改描述与媒体' : '仅可修改文字描述')
            .fontSize(FontSizes.labelSmall)
            .fontFamily(Fonts.LATO)
            .fontColor(this.mood.stone)
        }
        .alignItems(HorizontalAlign.Start)
        .layoutWeight(1)
        .padding({ left: 4 })
      }
      .width('100%')
      .padding({ left: 8, right: 8, top: 6, bottom: 6 })

      if (!this.ready) {
        Column() {
          LoadingProgress().width(36).height(36).color(this.mood.soft)
        }
        .width('100%')
        .layoutWeight(1)
        .justifyContent(FlexAlign.Center)
      } else if (this.mediaId.length === 0) {
        Text('找不到这段时光')
          .fontSize(FontSizes.bodyMedium)
          .fontColor(this.mood.stone)
          .margin({ top: 80 })
      } else {
        Scroll() {
          Column({ space: 18 }) {
            // Media grid — Android ReorderableMediaDraftGrid (preview; remove for owner)
            if (this.cells.length === 0) {
              Text('至少需要保留一张照片或视频')
                .fontSize(FontSizes.bodyMedium)
                .fontFamily(Fonts.LATO)
                .fontColor(Colors.error)
                .width('100%')
            } else {
              Column({ space: 8 }) {
                Grid() {
                  ForEach(this.cells, (cell: EditCell) => {
                    GridItem() {
                      Stack({ alignContent: Alignment.TopEnd }) {
                        if (cell.url.length > 0) {
                          Image(cell.url)
                            .width('100%')
                            .height('100%')
                            .objectFit(ImageFit.Cover)
                            .borderRadius(16)
                        } else {
                          Column()
                            .width('100%')
                            .height('100%')
                            .borderRadius(16)
                            .backgroundColor(this.mood.blush)
                        }
                        if (cell.isVideo) {
                          Text('▶')
                            .fontSize(14)
                            .fontColor(Colors.white)
                            .padding(6)
                            .borderRadius(14)
                            .backgroundColor('rgba(0,0,0,0.35)')
                            .position({ x: '38%', y: '38%' })
                        }
                        if (this.isOwner && this.cells.length > 1) {
                          Text('×')
                            .fontSize(14)
                            .fontColor(Colors.white)
                            .width(24)
                            .height(24)
                            .textAlign(TextAlign.Center)
                            .borderRadius(12)
                            .backgroundColor('rgba(0,0,0,0.45)')
                            .margin({ top: 6, right: 6 })
                            .onClick(() => this.removeCell(cell.id))
                        }
                      }
                      .width('100%')
                      .aspectRatio(1)
                      .clip(true)
                    }
                  }, (cell: EditCell) => cell.id)
                }
                .columnsTemplate('1fr 1fr 1fr')
                .columnsGap(8)
                .rowsGap(8)
                .width('100%')

                if (this.isOwner && this.cells.length > 1) {
                  Text('可移除多余媒体；完整排序/继续添加后续对齐')
                    .fontSize(FontSizes.bodySmall)
                    .fontFamily(Fonts.LATO)
                    .fontColor(this.mood.stone)
                    .width('100%')
                }
              }
              .width('100%')
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

            if (this.isOwner) {
              LoverDateField({
                mood: this.mood,
                label: '记录日期',
                value: $date,
                maxDate: DateUtils.formatDate(new Date()),
              })
              Text('仅创建者可修改记录日期')
                .fontSize(FontSizes.bodySmall)
                .fontFamily(Fonts.LATO)
                .fontColor(this.mood.stone)
                .width('100%')
            } else {
              Row() {
                Text('记录日期')
                  .fontSize(FontSizes.bodyMedium)
                  .fontFamily(Fonts.LATO)
                  .fontColor(this.mood.stone)
                Blank()
                Text(this.date)
                  .fontSize(FontSizes.bodyMedium)
                  .fontFamily(Fonts.LATO)
                  .fontColor(this.mood.accent)
                  .fontWeight(FontWeight.Medium)
              }
              .width('100%')
              .padding({ left: 18, right: 18, top: 16, bottom: 16 })
              .backgroundColor(this.mood.softSurface)
              .borderRadius(22)
            }

            if (this.errorMsg.length > 0) {
              Text(this.errorMsg)
                .fontSize(FontSizes.bodySmall)
                .fontColor(Colors.error)
                .width('100%')
            }

            SoftPrimaryButton({
              mood: this.mood,
              label: this.saving ? '保存中…' : '保存',
              isEnabled: !this.saving && !this.deleting && this.dirty() && this.cells.length > 0,
              onTap: () => {
                this.save()
              },
            })

            if (this.isOwner) {
              Button(this.deleting ? '删除中…' : '删除这段时光')
                .width('100%')
                .height(48)
                .borderRadius(22)
                .backgroundColor(Colors.white)
                .fontColor(Colors.error)
                .border({ width: 1, color: Colors.error })
                .fontFamily(Fonts.LATO)
                .enabled(!this.deleting && !this.saving)
                .onClick(() => this.confirmDelete())
            }
          }
          .width('100%')
          .padding({ left: 20, right: 20, top: 12, bottom: 40 })
        }
        .layoutWeight(1)
        .scrollBar(BarState.Off)
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor(this.mood.background)
  }

  async save(): Promise<void> {
    if (!this.dirty() || this.saving || this.cells.length === 0) {
      return
    }
    this.saving = true
    this.errorMsg = ''
    try {
      const dateToSave = (this.isOwner && this.date !== this.originalDate) ? this.date : null
      // Caption/date only for now; media order sync when grid reorder lands
      await this.vm.updateMedia(this.mediaId, this.caption.trim(), dateToSave, null)
      AppNav.back()
    } catch (e) {
      this.errorMsg = e instanceof Error ? e.message : '保存失败'
    } finally {
      this.saving = false
    }
  }

  async confirmDelete(): Promise<void> {
    if (this.deleting || this.mediaId.length === 0) {
      return
    }
    const ok = await AppDialog.confirm('确认删除', '删除后无法恢复，确定要删除这段时光吗？')
    if (!ok) {
      return
    }
    this.deleting = true
    this.errorMsg = ''
    try {
      await this.vm.deleteMedia(this.mediaId)
      // Android closes edit + detail overlays; pop both pages.
      AppNav.back()
      setTimeout(() => {
        AppNav.back()
      }, 16)
    } catch (e) {
      const msg = e instanceof Error ? e.message : '删除失败'
      this.errorMsg = msg
      AppDialog.toast(msg)
    } finally {
      this.deleting = false
    }
  }
}
