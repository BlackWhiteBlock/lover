/**
 * Media detail — memory page aligned with Android MediaDetailScreen.
 * Hero + meta + caption card + asset collage + lightbox (not a black full-screen viewer).
 */
import { display } from '@kit.ArkUI'
import { AppNav, MediaDetailParams } from '../core/nav/AppNav'
import { AppViewModel } from '../core/data/AppViewModel'
import { CoupleMood, SoleilMood, Colors, FontSizes } from '../core/design/Theme'
import type { MoodPalette } from '../core/design/Theme'
import { Fonts } from '../core/design/Fonts'
import { MediaItem, MediaAssetPart, MediaType, CoupleMember } from '../core/model/Models'
import { DateUtils } from '../core/util/DateUtils'

const SOFT_ROSE_BORDER = '#FFF1F2'
const SOFT_ROSE_FILL = '#FFF1F2'
const LIGHT_ROSE_LINE = '#FECDD3'

@Entry
@Component
struct MediaDetailPage {
  @State mood: MoodPalette = CoupleMood
  @State mediaItem: MediaItem | null = null
  @State assets: MediaAssetPart[] = []
  @State loading: boolean = true
  @State lightboxIndex: number = -1
  @State heroHeight: number = 480
  private vm: AppViewModel = AppViewModel.getInstance()
  private mediaId: string = ''

  aboutToAppear(): void {
    this.mood = this.vm.data.linked ? CoupleMood : SoleilMood
    try {
      const d = display.getDefaultDisplaySync()
      this.heroHeight = this.getUIContext().px2vp(d.height * 0.62)
    } catch (_e) {
      this.heroHeight = 480
    }
    const params = AppNav.params<MediaDetailParams>()
    this.mediaId = params?.mediaId ?? ''
    if (this.mediaId.length > 0) {
      this.loadMedia()
    } else {
      this.loading = false
    }
  }

  async loadMedia(): Promise<void> {
    const found = this.vm.data.media.find((m: MediaItem) => m.id === this.mediaId)
    if (!found) {
      this.loading = false
      return
    }
    this.mediaItem = found
    this.assets = MediaItem.sortedAssets(found)
    try {
      const signed = await this.vm.openMediaDetail(found)
      this.mediaItem = signed
      this.assets = MediaItem.sortedAssets(signed)
    } catch (_e) {
      // Keep thumbnail/preview URLs — same as Android fallback
    }
    this.loading = false
  }

  private titleText(): string {
    const cap = (this.mediaItem?.caption ?? '').trim()
    if (cap.length === 0) {
      return '我们的时光'
    }
    const nl = cap.indexOf('\n')
    return nl >= 0 ? cap.substring(0, nl) : cap
  }

  private moodLabel(): string {
    const n = this.assets.length
    if (n === 0) {
      return '瞬间'
    }
    if (n > 1) {
      return '相册'
    }
    return this.assets[0].type === MediaType.VIDEO ? '视频' : '照片'
  }

  private displayDate(): string {
    const raw = this.mediaItem?.mediaDate ?? ''
    if (raw.length < 8) {
      return raw
    }
    try {
      const d = DateUtils.parseDate(raw.substring(0, 10))
      return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`
    } catch (_e) {
      return raw.replace(/-/g, '.')
    }
  }

  private uploaderName(): string {
    const uid = this.mediaItem?.uploaderId
    if (!uid || uid.length === 0) {
      return '我们'
    }
    const members: CoupleMember[] = this.vm.data.couple?.members ?? []
    for (let i = 0; i < members.length; i++) {
      if (members[i].id === uid) {
        return members[i].nickname.length > 0 ? members[i].nickname : 'TA'
      }
    }
    return 'TA'
  }

  private coverUrl(): string {
    if (this.assets.length === 0) {
      return ''
    }
    return MediaItem.partUrl(this.assets[0])
  }

  private assetPreview(part: MediaAssetPart): string {
    return MediaItem.partUrl(part)
  }

  private assetFull(part: MediaAssetPart): string {
    if (part.url && part.url.length > 0) {
      return part.url
    }
    return MediaItem.partUrl(part)
  }

  private openLightbox(index: number): void {
    if (index < 0 || index >= this.assets.length) {
      return
    }
    this.lightboxIndex = index
  }

  private closeLightbox(): void {
    this.lightboxIndex = -1
  }

  private openEdit(): void {
    AppNav.pushMediaEdit(this.mediaId)
  }

  build() {
    Stack() {
      Column() {
        if (this.loading) {
          Column() {
            LoadingProgress().width(40).height(40).color(this.mood.soft)
          }
          .width('100%')
          .layoutWeight(1)
          .justifyContent(FlexAlign.Center)
        } else if (!this.mediaItem) {
          Column({ space: 12 }) {
            Text('找不到这段时光')
              .fontSize(FontSizes.bodyLarge)
              .fontFamily(Fonts.LATO)
              .fontColor(this.mood.stone)
            Text('返回')
              .fontSize(FontSizes.bodyMedium)
              .fontColor(this.mood.accent)
              .onClick(() => AppNav.back())
          }
          .width('100%')
          .layoutWeight(1)
          .justifyContent(FlexAlign.Center)
        } else {
          Scroll() {
            Column() {
              // ── Hero ──
              Stack({ alignContent: Alignment.BottomStart }) {
                if (this.coverUrl().length > 0) {
                  Image(this.coverUrl())
                    .width('100%')
                    .height('100%')
                    .objectFit(ImageFit.Cover)
                } else {
                  Column()
                    .width('100%')
                    .height('100%')
                    .backgroundColor(this.mood.blush)
                }

                // Dark protect + bottom blend into cream
                Column()
                  .width('100%')
                  .height('100%')
                  .linearGradient({
                    angle: 180,
                    colors: [
                      ['rgba(0,0,0,0.35)', 0],
                      ['rgba(0,0,0,0)', 0.35],
                      ['rgba(0,0,0,0.25)', 0.7],
                      ['rgba(0,0,0,0.5)', 1],
                    ],
                  })

                Column()
                  .width('100%')
                  .height('100%')
                  .linearGradient({
                    angle: 180,
                    colors: [
                      ['rgba(0,0,0,0)', 0],
                      ['rgba(0,0,0,0)', 0.55],
                      [this.mood.background, 1],
                    ],
                  })

                // Glass nav
                Row() {
                  Text('‹')
                    .fontSize(22)
                    .fontColor(Colors.white)
                    .width(40)
                    .height(40)
                    .textAlign(TextAlign.Center)
                    .borderRadius(20)
                    .backgroundColor('rgba(0,0,0,0.28)')
                    .border({ width: 1, color: 'rgba(255,255,255,0.35)' })
                    .onClick(() => AppNav.back())

                  Blank()

                  // Glass edit — dark glass so white SVG pencil is visible
                  Stack({ alignContent: Alignment.Center }) {
                    Image($r('app.media.ic_edit'))
                      .width(20)
                      .height(20)
                      .objectFit(ImageFit.Contain)
                      .fillColor('#FFFFFF')
                  }
                  .width(40)
                  .height(40)
                  .borderRadius(20)
                  .backgroundColor('rgba(0,0,0,0.28)')
                  .border({ width: 1, color: 'rgba(255,255,255,0.35)' })
                  .onClick(() => this.openEdit())
                }
                .width('100%')
                .padding({ left: 20, right: 20, top: 48, bottom: 12 })
                .alignItems(VerticalAlign.Center)
                .position({ x: 0, y: 0 })

                // Mood + title
                Column({ space: 12 }) {
                  Text(this.moodLabel().toUpperCase())
                    .fontSize(9)
                    .fontFamily(Fonts.LATO_LIGHT)
                    .fontColor(Colors.white)
                    .letterSpacing(2)
                    .padding({ left: 12, right: 12, top: 5, bottom: 5 })
                    .backgroundColor('rgba(255,255,255,0.2)')
                    .border({ width: 1, color: 'rgba(255,255,255,0.2)' })
                    .borderRadius(999)
                  Text(this.titleText())
                    .fontSize(30)
                    .fontFamily(Fonts.GARAMOND)
                    .fontColor(Colors.white)
                    .maxLines(3)
                    .textOverflow({ overflow: TextOverflow.Ellipsis })
                    .width('100%')
                }
                .width('100%')
                .padding({ left: 24, right: 24, bottom: 28 })
              }
              .width('100%')
              .height(this.heroHeight)
              .clip(true)

              // ── Content ──
              Column({ space: 28 }) {
                Row({ space: 16 }) {
                  Row({ space: 6 }) {
                    Image($r('app.media.ic_calendar'))
                      .width(14)
                      .height(14)
                      .fillColor('rgba(133,122,120,0.7)')
                    Text(this.displayDate())
                      .fontSize(12)
                      .fontFamily(Fonts.LATO_LIGHT)
                      .fontColor('rgba(133,122,120,0.75)')
                  }
                  Text(this.uploaderName())
                    .fontSize(12)
                    .fontFamily(Fonts.LATO_LIGHT)
                    .fontColor('rgba(133,122,120,0.55)')
                }
                .width('100%')
                .alignItems(VerticalAlign.Center)

                if ((this.mediaItem.caption ?? '').trim().length > 0) {
                  Column({ space: 20 }) {
                    Text(this.mediaItem.caption)
                      .fontSize(14)
                      .fontFamily(Fonts.LATO_LIGHT)
                      .fontColor('rgba(133,122,120,0.9)')
                      .lineHeight(26)
                      .width('100%')

                    Row({ space: 8 }) {
                      Divider()
                        .vertical(false)
                        .strokeWidth(1)
                        .color(LIGHT_ROSE_LINE)
                        .width(16)
                      Text('只有我们知道')
                        .fontSize(9)
                        .fontFamily(Fonts.LATO_LIGHT)
                        .fontColor('rgba(133,122,120,0.35)')
                        .letterSpacing(3)
                      Divider()
                        .vertical(false)
                        .strokeWidth(1)
                        .color('rgba(255,241,242,0.7)')
                        .layoutWeight(1)
                    }
                    .width('100%')
                    .alignItems(VerticalAlign.Center)
                  }
                  .width('100%')
                  .padding(24)
                  .backgroundColor('rgba(255,255,255,0.72)')
                  .border({ width: 1, color: SOFT_ROSE_BORDER })
                  .borderRadius(32)
                }

                if (this.assets.length > 0) {
                  Column({ space: 12 }) {
                    Text(`${this.assets.length} 个瞬间`)
                      .fontSize(9)
                      .fontFamily(Fonts.LATO_LIGHT)
                      .fontColor('rgba(133,122,120,0.35)')
                      .letterSpacing(3)
                      .alignSelf(ItemAlign.Start)

                    // Hero tile 4:3
                    this.MemoryTile(this.assets[0], 0, 56, 4 / 3, 32)

                    // Pair of squares
                    if (this.assets.length > 1) {
                      Row({ space: 12 }) {
                        Column() {
                          this.MemoryTile(this.assets[1], 1, 40, 1, 24)
                        }
                        .layoutWeight(1)

                        if (this.assets.length > 2) {
                          Column() {
                            this.MemoryTile(this.assets[2], 2, 40, 1, 24)
                          }
                          .layoutWeight(1)
                        } else {
                          Blank().layoutWeight(1)
                        }
                      }
                      .width('100%')
                    }

                    // Remaining alternating ratios
                    ForEach(this.tailAssets(), (entry: TailAsset) => {
                      this.MemoryTile(entry.part, entry.index, 48, entry.ratio, 28)
                    }, (entry: TailAsset) => `tail_${entry.index}_${entry.part.id}`)
                  }
                  .width('100%')
                }

                // Footer
                Column({ space: 12 }) {
                  Divider()
                    .vertical(false)
                    .strokeWidth(1)
                    .color(SOFT_ROSE_FILL)
                    .width(32)
                  Image($r('app.media.ic_nav_favorite'))
                    .width(16)
                    .height(16)
                    .fillColor(this.withAlpha(this.mood.soft, 0.35))
                  Text(`${this.displayDate()} · 永久珍藏`)
                    .fontSize(9)
                    .fontFamily(Fonts.LATO_LIGHT)
                    .fontColor('rgba(133,122,120,0.35)')
                    .letterSpacing(4)
                }
                .width('100%')
                .alignItems(HorizontalAlign.Center)
                .padding({ top: 8, bottom: 8 })
              }
              .width('100%')
              .padding({ left: 20, right: 20, bottom: 40, top: 8 })
            }
            .width('100%')
          }
          .layoutWeight(1)
          .scrollBar(BarState.Off)
          .edgeEffect(EdgeEffect.Spring)
        }
      }
      .width('100%')
      .height('100%')
      .backgroundColor(this.mood.background)

      // Lightbox overlay
      if (this.lightboxIndex >= 0 && this.lightboxIndex < this.assets.length) {
        this.Lightbox(this.assets[this.lightboxIndex])
      }
    }
    .width('100%')
    .height('100%')
  }

  private tailAssets(): TailAsset[] {
    const out: TailAsset[] = []
    for (let i = 3; i < this.assets.length; i++) {
      const t = new TailAsset()
      t.part = this.assets[i]
      t.index = i
      t.ratio = ((i - 3) % 2 === 0) ? (16 / 9) : (4 / 3)
      out.push(t)
    }
    return out
  }

  @Builder
  MemoryTile(
    part: MediaAssetPart,
    index: number,
    playSize: number,
    ratio: number,
    radius: number,
  ) {
    Stack({ alignContent: Alignment.Center }) {
      if (this.assetPreview(part).length > 0) {
        Image(this.assetPreview(part))
          .width('100%')
          .height('100%')
          .objectFit(ImageFit.Cover)
      } else {
        Column()
          .width('100%')
          .height('100%')
          .backgroundColor(this.mood.blush)
      }
      if (part.type === MediaType.VIDEO) {
        Column()
          .width('100%')
          .height('100%')
          .backgroundColor('rgba(0,0,0,0.2)')
        Column() {
          Text('▶')
            .fontSize(playSize * 0.35)
            .fontColor(Colors.white)
        }
        .width(playSize)
        .height(playSize)
        .justifyContent(FlexAlign.Center)
        .borderRadius(playSize / 2)
        .backgroundColor('rgba(255,255,255,0.3)')
        .border({ width: 1, color: 'rgba(255,255,255,0.35)' })
      }
    }
    .width('100%')
    .aspectRatio(ratio)
    .borderRadius(radius)
    .clip(true)
    .backgroundColor(this.mood.blush)
    .onClick(() => this.openLightbox(index))
  }

  @Builder
  Lightbox(part: MediaAssetPart) {
    Stack({ alignContent: Alignment.Center }) {
      Column()
        .width('100%')
        .height('100%')
        .backgroundColor('rgba(0,0,0,0.92)')
        .onClick(() => this.closeLightbox())

      if (part.type === MediaType.VIDEO) {
        Video({
          src: this.assetFull(part),
          previewUri: this.assetPreview(part),
        })
          .width('100%')
          .constraintSize({ maxHeight: '80%' })
          .margin({ left: 16, right: 16 })
          .borderRadius(16)
          .controls(true)
          .autoPlay(true)
          .onClick(() => {
            // absorb — don't close while interacting with controls
          })
      } else {
        Image(this.assetFull(part))
          .width('100%')
          .constraintSize({ maxHeight: '85%' })
          .objectFit(ImageFit.Contain)
          .margin({ left: 16, right: 16 })
          .borderRadius(16)
          .onClick(() => this.closeLightbox())
      }

      Text('‹')
        .fontSize(22)
        .fontColor(Colors.white)
        .width(40)
        .height(40)
        .textAlign(TextAlign.Center)
        .borderRadius(20)
        .backgroundColor('rgba(255,255,255,0.2)')
        .position({ x: 20, y: 48 })
        .onClick(() => this.closeLightbox())
    }
    .width('100%')
    .height('100%')
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

class TailAsset {
  part: MediaAssetPart = new MediaAssetPart()
  index: number = 0
  ratio: number = 16 / 9
}
