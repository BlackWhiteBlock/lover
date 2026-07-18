/**
 * Timeline gallery — mirrors Android TimelineGallery.kt
 * Featured card + dual-column staggered masonry (SINGLE / DUO / STRIP).
 */
import { Fonts } from '../core/design/Fonts'
import { Colors, FontSizes, Radii } from '../core/design/Theme'
import type { MoodPalette } from '../core/design/Theme'
import { MediaAssetPart, MediaItem, MediaType } from '../core/model/Models'
import { AppNav } from '../core/nav/AppNav'
import { PendingMediaUpload } from '../core/data/AppViewModel'

enum TimelineCardVariant {
  SINGLE = 0,
  DUO = 1,
  STRIP = 2,
}

const HEIGHT_BEAT: number[] = [210, 240, 230, 180, 220, 250, 200, 215, 190]
const STRIP_SLOTS: number[] = [0, 1]

class TimelineCardPlan {
  item: MediaItem = new MediaItem()
  variant: TimelineCardVariant = TimelineCardVariant.SINGLE
  heightDp: number = 210
  key: string = ''
}

function mediaCount(item: MediaItem): number {
  const n = item.assets ? item.assets.length : 0
  if (n > 0) {
    return n
  }
  return MediaItem.coverOf(item) !== null ? 1 : 0
}

function uploadProgress(u: PendingMediaUpload): number {
  if (u.total <= 0) {
    return 0
  }
  const fraction = Math.min(u.fileFraction, 1)
  return Math.min(1, Math.max(0, (u.completed + fraction) / u.total))
}

function preferredVariant(item: MediaItem): TimelineCardVariant {
  const count = mediaCount(item)
  const hasVideo = MediaItem.hasVideoAsset(item)
  if (count <= 1) {
    return TimelineCardVariant.SINGLE
  }
  if (hasVideo) {
    return TimelineCardVariant.DUO
  }
  if (count >= 4) {
    return TimelineCardVariant.STRIP
  }
  return TimelineCardVariant.DUO
}

function eligibleVariants(item: MediaItem): TimelineCardVariant[] {
  const count = mediaCount(item)
  if (count <= 1) {
    return [TimelineCardVariant.SINGLE]
  }
  if (count <= 3) {
    return [TimelineCardVariant.DUO, TimelineCardVariant.SINGLE]
  }
  return [TimelineCardVariant.STRIP, TimelineCardVariant.DUO, TimelineCardVariant.SINGLE]
}

function resolveVariant(item: MediaItem, previous: TimelineCardVariant | null): TimelineCardVariant {
  const eligible = eligibleVariants(item)
  let preferred = preferredVariant(item)
  let found = false
  for (let i = 0; i < eligible.length; i++) {
    if (eligible[i] === preferred) {
      found = true
      break
    }
  }
  if (!found) {
    preferred = eligible[0]
  }
  if (previous === null || preferred !== previous) {
    return preferred
  }
  for (let i = 0; i < eligible.length; i++) {
    if (eligible[i] !== previous) {
      return eligible[i]
    }
  }
  return preferred
}

function planTimelineCards(items: MediaItem[]): TimelineCardPlan[] {
  const plans: TimelineCardPlan[] = []
  let previous: TimelineCardVariant | null = null
  let previousHeight = 0
  for (let index = 0; index < items.length; index++) {
    const item = items[index]
    const variant = resolveVariant(item, previous)
    let height = HEIGHT_BEAT[index % HEIGHT_BEAT.length]
    if (item.caption.trim().length > 50) {
      height = Math.min(height + 20, 270)
    }
    if (height === previousHeight) {
      height = Math.min(270, Math.max(180, height + 20))
      if (height === previousHeight) {
        height = height >= 230 ? height - 30 : height + 30
      }
    }
    const plan = new TimelineCardPlan()
    plan.item = item
    plan.variant = variant
    plan.heightDp = height
    plan.key = `${item.id}_${variant}_${height}`
    plans.push(plan)
    previous = variant
    previousHeight = height
  }
  return plans
}

@Component
export struct TimelineGalleryContent {
  @Prop mood: MoodPalette
  @Prop @Watch('onMediaChange') media: MediaItem[] = []
  @Prop pendingUploads: PendingMediaUpload[] = []
  onLoadMore: () => void = () => {}

  @State private featured: MediaItem | null = null
  @State private leftPlans: TimelineCardPlan[] = []
  @State private rightPlans: TimelineCardPlan[] = []

  aboutToAppear(): void {
    this.rebuild()
  }

  onMediaChange(): void {
    this.rebuild()
  }

  private rebuild(): void {
    if (this.media.length === 0) {
      this.featured = null
      this.leftPlans = []
      this.rightPlans = []
      return
    }
    this.featured = this.media[0]
    const rest = planTimelineCards(this.media.slice(1))
    const left: TimelineCardPlan[] = []
    const right: TimelineCardPlan[] = []
    for (let i = 0; i < rest.length; i++) {
      if (i % 2 === 0) {
        left.push(rest[i])
      } else {
        right.push(rest[i])
      }
    }
    this.leftPlans = left
    this.rightPlans = right
  }

  build() {
    Scroll() {
      Column({ space: 20 }) {
        if (this.pendingUploads.length > 0) {
          // Android LazyRow of UploadingMediaCard — width ~148, aspect 0.8
          Scroll() {
            Row({ space: 12 }) {
              ForEach(this.pendingUploads, (u: PendingMediaUpload) => {
                UploadingMediaCard({ mood: this.mood, pending: u })
              }, (u: PendingMediaUpload) =>
                `${u.id}_${u.completed}_${Math.floor(u.fileFraction * 100)}_${u.phase}`)
            }
            .padding({ left: 2, right: 2 })
          }
          .scrollable(ScrollDirection.Horizontal)
          .scrollBar(BarState.Off)
          .edgeEffect(EdgeEffect.Spring)
          .width('100%')
        }

        if (this.featured !== null) {
          FeaturedTimelineCard({
            mood: this.mood,
            item: this.featured as MediaItem,
          })
        }

        if (this.leftPlans.length > 0 || this.rightPlans.length > 0) {
          Row({ space: 12 }) {
            Column({ space: 12 }) {
              ForEach(this.leftPlans, (plan: TimelineCardPlan) => {
                TimelineVariantCard({ mood: this.mood, plan: plan })
              }, (plan: TimelineCardPlan) => `L_${plan.key}`)
            }
            .layoutWeight(1)

            Column({ space: 12 }) {
              ForEach(this.rightPlans, (plan: TimelineCardPlan) => {
                TimelineVariantCard({ mood: this.mood, plan: plan })
              }, (plan: TimelineCardPlan) => `R_${plan.key}`)
            }
            .layoutWeight(1)
            .padding({ top: 40 })
          }
          .width('100%')
          .alignItems(VerticalAlign.Top)
        }

        if (this.media.length >= 30) {
          LoadingProgress()
            .width(24)
            .height(24)
            .color(this.mood.soft)
            .margin({ top: 8, bottom: 8 })
            .onAppear(() => this.onLoadMore())
        }

        Blank().height(120)
      }
      .width('100%')
      .padding({ left: 16, right: 16, top: 4 })
    }
    .width('100%')
    .layoutWeight(1)
    .scrollBar(BarState.Off)
    .edgeEffect(EdgeEffect.Spring)
  }
}

@Component
struct TimelineVariantCard {
  @Prop mood: MoodPalette
  @Prop plan: TimelineCardPlan = new TimelineCardPlan()

  build() {
    if (this.plan.variant === TimelineCardVariant.DUO) {
      CardDuo({ mood: this.mood, item: this.plan.item, heightDp: this.plan.heightDp })
    } else if (this.plan.variant === TimelineCardVariant.STRIP) {
      CardStrip({ mood: this.mood, item: this.plan.item, heightDp: this.plan.heightDp })
    } else {
      CardSingle({ mood: this.mood, item: this.plan.item, heightDp: this.plan.heightDp })
    }
  }
}

@Component
struct FeaturedTimelineCard {
  @Prop mood: MoodPalette
  @Prop item: MediaItem = new MediaItem()

  private sorted(): MediaAssetPart[] {
    return MediaItem.sortedAssets(this.item)
  }

  private strip(): MediaAssetPart[] {
    return this.sorted().slice(0, 4)
  }

  private overflow(): number {
    return Math.max(0, this.sorted().length - 4)
  }

  private titleText(): string {
    return this.item.caption.length > 0 ? this.item.caption : this.item.mediaDate
  }

  build() {
    Column() {
      Stack({ alignContent: Alignment.BottomStart }) {
        MediaThumb({ mood: this.mood, part: MediaItem.coverOf(this.item) })
        Column()
          .width('100%')
          .height('100%')
          .linearGradient({
            angle: 180,
            colors: [
              ['rgba(0,0,0,0)', 0],
              ['rgba(0,0,0,0)', 0.55],
              ['rgba(0,0,0,0.5)', 1],
            ],
          })
        Column({ space: 4 }) {
          Text('精选')
            .fontSize(FontSizes.labelSmall)
            .fontFamily(Fonts.LATO_LIGHT)
            .fontColor(Colors.white)
            .opacity(0.85)
            .letterSpacing(2)
          Text(this.titleText())
            .fontSize(FontSizes.titleLarge)
            .fontFamily(Fonts.GARAMOND)
            .fontColor(Colors.white)
            .maxLines(2)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
            .width('100%')
        }
        .width('100%')
        .padding({ left: 20, right: 20, top: 12, bottom: 12 })

        if (MediaItem.hasVideoAsset(this.item)) {
          VideoBadge()
            .position({ x: 14, y: 14 })
        }
      }
      .width('100%')
      .layoutWeight(0.65)

      Row({ space: 6 }) {
        ForEach(this.strip(), (part: MediaAssetPart, index: number) => {
          Stack() {
            MediaThumb({ mood: this.mood, part: part })
            if (part.type === MediaType.VIDEO) {
              VideoScrim({ large: false })
            }
          }
          .layoutWeight(index === 0 ? 1.5 : 1)
          .height('100%')
          .borderRadius(12)
          .clip(true)
        }, (part: MediaAssetPart, index: number) => `ft_${this.item.id}_${part.assetId}_${index}`)

        if (this.overflow() > 0) {
          Stack({ alignContent: Alignment.Center }) {
            if (this.sorted().length > 4) {
              MediaThumb({ mood: this.mood, part: this.sorted()[4], alpha: 0.45 })
            } else {
              Column().width('100%').height('100%').backgroundColor(this.mood.blush)
            }
            Column()
              .width('100%')
              .height('100%')
              .backgroundColor('rgba(0,0,0,0.35)')
            Text(`+${this.overflow()}`)
              .fontSize(FontSizes.bodyMedium)
              .fontFamily(Fonts.LATO_LIGHT)
              .fontColor(Colors.white)
          }
          .layoutWeight(1)
          .height('100%')
          .borderRadius(12)
          .clip(true)
        }

        Column({ space: 2 }) {
          Text(this.item.mediaDate)
            .fontSize(9)
            .fontFamily(Fonts.LATO_LIGHT)
            .fontColor(this.mood.stone)
            .opacity(0.75)
            .maxLines(2)
          if (this.item.assets.length > 1) {
            Text(`${this.item.assets.length} 张`)
              .fontSize(9)
              .fontFamily(Fonts.LATO_LIGHT)
              .fontColor(this.mood.stone)
              .opacity(0.45)
          }
        }
        .width(56)
        .justifyContent(FlexAlign.Center)
      }
      .width('100%')
      .layoutWeight(0.35)
      .padding(8)
      .backgroundColor('rgba(255,255,255,0.72)')
    }
    .width('100%')
    .height(300)
    .borderRadius(Radii.extraLarge)
    .clip(true)
    .backgroundColor(this.mood.blush)
    .onClick(() => AppNav.pushMediaDetail(this.item.id))
  }
}

@Component
struct CardSingle {
  @Prop mood: MoodPalette
  @Prop item: MediaItem = new MediaItem()
  @Prop heightDp: number = 210

  private caption(): string {
    return this.item.caption.length > 0 ? this.item.caption : '未命名时光'
  }

  build() {
    Stack({ alignContent: Alignment.BottomStart }) {
      MediaThumb({ mood: this.mood, part: MediaItem.coverOf(this.item) })
      Column()
        .width('100%')
        .height('100%')
        .linearGradient({
          angle: 180,
          colors: [
            ['rgba(0,0,0,0)', 0],
            ['rgba(0,0,0,0)', 0.55],
            ['rgba(0,0,0,0.65)', 1],
          ],
        })
      if (MediaItem.hasVideoAsset(this.item)) {
        VideoBadge()
          .position({ x: 12, y: 12 })
      }
      Column({ space: 2 }) {
        Text(this.caption())
          .fontSize(FontSizes.bodySmall)
          .fontFamily(Fonts.LATO)
          .fontColor(Colors.white)
          .maxLines(2)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .width('100%')
        Text(this.item.mediaDate)
          .fontSize(10)
          .fontFamily(Fonts.LATO_LIGHT)
          .fontColor(Colors.white)
          .opacity(0.55)
      }
      .width('100%')
      .padding(14)
    }
    .width('100%')
    .height(this.heightDp)
    .borderRadius(28)
    .clip(true)
    .backgroundColor(this.mood.blush)
    .onClick(() => AppNav.pushMediaDetail(this.item.id))
  }
}

@Component
struct CardDuo {
  @Prop mood: MoodPalette
  @Prop item: MediaItem = new MediaItem()
  @Prop heightDp: number = 210

  private sorted(): MediaAssetPart[] {
    return MediaItem.sortedAssets(this.item)
  }

  private top(): MediaAssetPart | null {
    const s = this.sorted()
    return s.length > 0 ? s[0] : null
  }

  private bottom(): MediaAssetPart | null {
    const s = this.sorted()
    if (s.length > 1) {
      return s[1]
    }
    return this.top()
  }

  private caption(): string {
    return this.item.caption.length > 0 ? this.item.caption : '未命名时光'
  }

  build() {
    Column({ space: 4 }) {
      Stack({ alignContent: Alignment.BottomStart }) {
        MediaThumb({ mood: this.mood, part: this.top() })
        Column()
          .width('100%')
          .height('100%')
          .linearGradient({
            angle: 180,
            colors: [
              ['rgba(0,0,0,0.18)', 0],
              ['rgba(0,0,0,0)', 1],
            ],
          })
        Text(this.caption())
          .fontSize(FontSizes.bodySmall)
          .fontFamily(Fonts.LATO)
          .fontColor(Colors.white)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .padding({ left: 12, right: 12, top: 8, bottom: 8 })
      }
      .width('100%')
      .layoutWeight(3)
      .borderRadius({ topLeft: 28, topRight: 28 })
      .clip(true)
      .backgroundColor(this.mood.blush)

      Stack({ alignContent: Alignment.BottomEnd }) {
        MediaThumb({ mood: this.mood, part: this.bottom() })
        Column()
          .width('100%')
          .height('100%')
          .linearGradient({
            angle: 180,
            colors: [
              ['rgba(0,0,0,0)', 0],
              ['rgba(0,0,0,0.4)', 1],
            ],
          })
        Text(this.item.mediaDate)
          .fontSize(9)
          .fontFamily(Fonts.LATO_LIGHT)
          .fontColor(Colors.white)
          .opacity(0.65)
          .padding({ left: 12, right: 12, top: 8, bottom: 8 })
        if (this.bottom() !== null && (this.bottom() as MediaAssetPart).type === MediaType.VIDEO) {
          VideoScrim({ large: true })
        }
      }
      .width('100%')
      .layoutWeight(2)
      .borderRadius({ bottomLeft: 28, bottomRight: 28 })
      .clip(true)
      .backgroundColor(this.mood.blush)
    }
    .width('100%')
    .height(this.heightDp)
    .onClick(() => AppNav.pushMediaDetail(this.item.id))
  }
}

@Component
struct CardStrip {
  @Prop mood: MoodPalette
  @Prop item: MediaItem = new MediaItem()
  @Prop heightDp: number = 210

  private sorted(): MediaAssetPart[] {
    return MediaItem.sortedAssets(this.item)
  }

  private thumbs(): MediaAssetPart[] {
    return this.sorted().slice(1, 3)
  }

  private caption(): string {
    return this.item.caption.length > 0 ? this.item.caption : '未命名时光'
  }

  build() {
    Column() {
      Stack({ alignContent: Alignment.TopStart }) {
        MediaThumb({ mood: this.mood, part: MediaItem.coverOf(this.item) })
        Column()
          .width('100%')
          .height('100%')
          .linearGradient({
            angle: 180,
            colors: [
              ['rgba(0,0,0,0)', 0],
              ['rgba(0,0,0,0.28)', 1],
            ],
          })
        if (MediaItem.hasVideoAsset(this.item)) {
          VideoBadge()
            .position({ x: 10, y: 10 })
        }
      }
      .width('100%')
      .layoutWeight(0.62)

      Row({ space: 6 }) {
        ForEach(STRIP_SLOTS, (index: number) => {
          Stack({ alignContent: Alignment.Center }) {
            if (this.thumbs().length > index) {
              MediaThumb({ mood: this.mood, part: this.thumbs()[index] })
              if (this.thumbs()[index].type === MediaType.VIDEO) {
                VideoScrim({ large: false })
              }
            } else {
              Column()
                .width('100%')
                .height('100%')
                .backgroundColor(this.withAlpha(this.mood.soft, 0.12))
              Text('图')
                .fontSize(12)
                .fontColor(this.mood.soft)
                .opacity(0.35)
            }
          }
          .layoutWeight(1)
          .height('100%')
          .borderRadius(12)
          .clip(true)
        }, (index: number) => `strip_${this.item.id}_${index}`)

        Column() {
          Text(this.caption())
            .fontSize(FontSizes.labelMedium)
            .fontFamily(Fonts.LATO)
            .fontColor(this.mood.stone)
            .maxLines(2)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
            .width('100%')
          Text(this.item.mediaDate)
            .fontSize(9)
            .fontFamily(Fonts.LATO_LIGHT)
            .fontColor(this.mood.stone)
            .opacity(0.45)
        }
        .layoutWeight(1.05)
        .height('100%')
        .justifyContent(FlexAlign.End)
        .padding({ left: 2, bottom: 2 })
      }
      .width('100%')
      .layoutWeight(0.38)
      .padding(6)
    }
    .width('100%')
    .height(this.heightDp)
    .borderRadius(28)
    .clip(true)
    .backgroundColor('rgba(255,255,255,0.55)')
    .onClick(() => AppNav.pushMediaDetail(this.item.id))
  }

  private withAlpha(hex: string, alpha: number): string {
    if (hex.length === 7 && hex.startsWith('#')) {
      const r = Number.parseInt(hex.substring(1, 3), 16)
      const g = Number.parseInt(hex.substring(3, 5), 16)
      const b = Number.parseInt(hex.substring(5, 7), 16)
      return `rgba(${r},${g},${b},${alpha})`
    }
    return hex
  }
}

/**
 * Uploading placeholder card — mirrors Android UploadingMediaCard
 * (thumb + pulse dim + ring % + phase + linear bar + footer caption).
 */
@Component
struct UploadingMediaCard {
  @Prop mood: MoodPalette
  @Prop pending: PendingMediaUpload = new PendingMediaUpload()
  @State private glow: number = 0.48
  private pulseTimer: number = -1

  aboutToAppear(): void {
    this.startPulse()
  }

  aboutToDisappear(): void {
    if (this.pulseTimer >= 0) {
      clearInterval(this.pulseTimer)
      this.pulseTimer = -1
    }
  }

  private startPulse(): void {
    if (this.pulseTimer >= 0) {
      return
    }
    // Android: alpha = 0.42 + glow(0.35..0.7) * 0.18 → ~0.48..0.55
    let t = 0
    this.pulseTimer = setInterval(() => {
      t = (t + 1) % 24
      const wave = Math.sin((t / 24) * Math.PI * 2)
      this.glow = 0.515 + wave * 0.035
    }, 90)
  }

  private progress(): number {
    return Math.max(0.04, uploadProgress(this.pending))
  }

  private percent(): number {
    return Math.min(99, Math.max(0, Math.floor(uploadProgress(this.pending) * 100)))
  }

  private previewSrc(): string {
    const p = this.pending.previewUri
    if (p.length === 0) {
      return ''
    }
    if (p.startsWith('file://') || p.startsWith('data:')) {
      return p
    }
    return `file://${p}`
  }

  private footerText(): string {
    const cap = this.pending.caption.trim()
    if (cap.length > 0) {
      return cap
    }
    return this.pending.mediaDate
  }

  private statusLine(): string {
    if (this.pending.total > 1) {
      const done = Math.min(this.pending.completed, this.pending.total)
      return `${done}/${this.pending.total} · 后台上传中`
    }
    return '后台上传中，可继续浏览'
  }

  private phaseText(): string {
    return this.pending.phase.length > 0 ? this.pending.phase : '正在上传'
  }

  build() {
    Stack() {
      if (this.previewSrc().length > 0) {
        Image(this.previewSrc())
          .width('100%')
          .height('100%')
          .objectFit(ImageFit.Cover)
      } else {
        Column()
          .width('100%')
          .height('100%')
          .backgroundColor(this.mood.blush)
      }

      Column()
        .width('100%')
        .height('100%')
        .backgroundColor(`rgba(0,0,0,${this.glow})`)

      Column({ space: 10 }) {
        Stack({ alignContent: Alignment.Center }) {
          Progress({
            value: Math.floor(this.progress() * 100),
            total: 100,
            type: ProgressType.Ring,
          })
            .width(54)
            .height(54)
            .color(Colors.white)
            .backgroundColor('rgba(255,255,255,0.25)')
            .style({ strokeWidth: 3 })
          Text(`${this.percent()}%`)
            .fontSize(FontSizes.labelMedium)
            .fontFamily(Fonts.LATO)
            .fontWeight(FontWeight.Medium)
            .fontColor(Colors.white)
        }
        .width(54)
        .height(54)

        Text(this.phaseText())
          .fontSize(FontSizes.bodyMedium)
          .fontFamily(Fonts.LATO)
          .fontWeight(FontWeight.Medium)
          .fontColor(Colors.white)
          .textAlign(TextAlign.Center)
          .maxLines(2)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .width('100%')

        Progress({
          value: Math.floor(this.progress() * 100),
          total: 100,
          type: ProgressType.Linear,
        })
          .width('100%')
          .height(6)
          .borderRadius(4)
          .color(this.mood.soft)
          .backgroundColor('rgba(255,255,255,0.35)')

        Text(this.statusLine())
          .fontSize(FontSizes.labelSmall)
          .fontFamily(Fonts.LATO)
          .fontColor('rgba(255,255,255,0.85)')
          .textAlign(TextAlign.Center)
          .width('100%')
      }
      .width('100%')
      .padding({ left: 14, right: 14 })
      .alignItems(HorizontalAlign.Center)
      .align(Alignment.Center)

      Column() {
        Text(this.footerText())
          .fontSize(FontSizes.bodySmall)
          .fontFamily(Fonts.LATO)
          .fontColor(Colors.white)
          .maxLines(2)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .width('100%')
      }
      .width('100%')
      .padding(12)
      .linearGradient({
        angle: 180,
        colors: [['rgba(0,0,0,0)', 0], ['rgba(0,0,0,0.5)', 1]],
      })
      .align(Alignment.Bottom)

      if (this.pending.assetCount > 1) {
        Text(`${this.pending.assetCount}`)
          .fontSize(FontSizes.labelMedium)
          .fontFamily(Fonts.LATO)
          .fontColor(Colors.white)
          .padding({ left: 8, right: 8, top: 3, bottom: 3 })
          .borderRadius(10)
          .backgroundColor('rgba(0,0,0,0.45)')
          .margin({ top: 10, right: 10 })
          .align(Alignment.TopEnd)
      }
    }
    .width(148)
    .aspectRatio(0.8)
    .borderRadius(26)
    .clip(true)
    .backgroundColor(this.mood.blush)
  }
}

@Component
struct MediaThumb {
  @Prop mood: MoodPalette
  @Prop part: MediaAssetPart | null = null
  @Prop alpha: number = 1

  build() {
    if (this.part !== null && MediaItem.partUrl(this.part).length > 0) {
      Image(MediaItem.partUrl(this.part))
        .width('100%')
        .height('100%')
        .objectFit(ImageFit.Cover)
        .opacity(this.alpha)
    } else {
      Column()
        .width('100%')
        .height('100%')
        .backgroundColor(this.mood.blush)
        .opacity(this.alpha)
    }
  }
}

@Component
struct VideoBadge {
  build() {
    Stack({ alignContent: Alignment.Center }) {
      Text('▶')
        .fontSize(11)
        .fontColor(Colors.white)
    }
    .width(24)
    .height(24)
    .borderRadius(12)
    .backgroundColor('rgba(255,255,255,0.28)')
  }
}

@Component
struct VideoScrim {
  @Prop large: boolean = false

  build() {
    Stack({ alignContent: Alignment.Center }) {
      Column()
        .width('100%')
        .height('100%')
        .backgroundColor('rgba(0,0,0,0.22)')
      Stack({ alignContent: Alignment.Center }) {
        Text('▶')
          .fontSize(this.large ? 12 : 10)
          .fontColor(Colors.white)
      }
      .width(this.large ? 28 : 22)
      .height(this.large ? 28 : 22)
      .borderRadius(14)
      .backgroundColor('rgba(255,255,255,0.28)')
    }
    .width('100%')
    .height('100%')
  }
}
