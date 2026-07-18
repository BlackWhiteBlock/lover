/**
 * Home dashboard sections — mirror Android HomeSections.kt / MainScreen HomePage.
 */
import { Fonts } from '../core/design/Fonts'
import { LoverBrandRow } from '../core/design/LoverBrand'
import { Colors, FontSizes, LetterSpacing, Radii } from '../core/design/Theme'
import type { MoodPalette } from '../core/design/Theme'
import { DailyQuote, MediaItem } from '../core/model/Models'
import { AppNav } from '../core/nav/AppNav'
import { curves } from '@kit.ArkUI'

const WEEKDAYS: string[] = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
const MONTHS: string[] =
  ['一月', '二月', '三月', '四月', '五月', '六月', '七月', '八月', '九月', '十月', '十一月', '十二月']
const GLIMPSE_INTERVAL_MS = 7000
const GLIMPSE_ANIM_MS = 920

enum GlimpseSlide {
  Featured = 0,
  Vertical = 1,
  Horizontal = 2,
}

@Component
export struct HomeTopBar {
  @Prop mood: MoodPalette
  @Prop fontEpoch: number = 0

  build() {
    Row() {
      Column({ space: 2 }) {
        LoverBrandRow({
          mood: this.mood,
          logoSize: 40,
          nameSize: 42,
          fontEpoch: this.fontEpoch,
        })
        Text(this.mood.tagline)
          .fontSize(FontSizes.labelSmall)
          .fontFamily(Fonts.LATO_LIGHT)
          .fontColor(this.mood.stone)
          .opacity(0.8)
          .letterSpacing(this.mood.solo ? 1.2 : 2)
          .alignSelf(ItemAlign.Start)
      }
      .alignItems(HorizontalAlign.Start)
      .layoutWeight(1)

      Column({ space: 1 }) {
        Text(WEEKDAYS[new Date().getDay()])
          .fontSize(FontSizes.labelSmall)
          .fontFamily(Fonts.LATO_LIGHT)
          .fontColor(this.mood.soft)
          .opacity(0.85)
          .letterSpacing(3)
        Text(`${new Date().getDate()}`)
          .fontSize(30)
          .fontFamily(this.mood.solo ? Fonts.GARAMOND : Fonts.LATO)
          .fontColor(this.mood.accent)
          .opacity(0.88)
        Text(`${MONTHS[new Date().getMonth()]} · ${new Date().getFullYear()}`)
          .fontSize(FontSizes.labelSmall)
          .fontFamily(Fonts.LATO_LIGHT)
          .fontColor(this.mood.stone)
          .opacity(0.65)
          .letterSpacing(1.2)
      }
      .alignItems(HorizontalAlign.End)
    }
    .width('100%')
    .padding({ left: 22, right: 22, top: 16, bottom: 16 })
  }
}

/** Loving journey — blush→peach + LoverLogoPhoto CenterStart @ -14° / 28% (Android). */
@Component
export struct LovingJourneyCard {
  @Prop mood: MoodPalette
  @Prop linked: boolean = false
  @Prop lovingDays: number | null = null
  @Prop needsTogetherDate: boolean = false
  onSetTogetherDate: () => void = () => {}

  private withAlpha(hex: string, alpha: number): string {
    if (hex.length === 7 && hex.startsWith('#')) {
      const r = Number.parseInt(hex.substring(1, 3), 16)
      const g = Number.parseInt(hex.substring(3, 5), 16)
      const b = Number.parseInt(hex.substring(5, 7), 16)
      return `rgba(${r},${g},${b},${alpha})`
    }
    return hex
  }

  private daysValue(): number {
    return this.lovingDays !== null ? this.lovingDays : 0
  }

  build() {
    // Android: Box(padding 30) + Column centered + LoverLogoPhoto(CenterStart, start=18, -14°, α0.28)
    Stack({ alignContent: Alignment.TopStart }) {
      Column({ space: 8 }) {
        if (this.mood.solo || !this.linked) {
          Text('WAITING')
            .fontSize(FontSizes.labelMedium)
            .fontFamily(Fonts.LATO)
            .fontColor(this.mood.accent)
          Text('等待你的出现')
            .fontSize(FontSizes.headlineMedium)
            .fontFamily(Fonts.GARAMOND)
            .fontColor(this.mood.accent)
            .opacity(0.92)
            .textAlign(TextAlign.Center)
          Row() {
            Text(`${this.daysValue()}`)
              .fontSize(FontSizes.displayLarge)
              .fontFamily(Fonts.GARAMOND)
              .fontColor(this.mood.accent)
            Text(' 天')
              .fontSize(FontSizes.titleLarge)
              .fontFamily(Fonts.GARAMOND)
              .fontColor(this.mood.stone)
              .margin({ bottom: 8 })
          }
          .alignItems(VerticalAlign.Bottom)
          Text('每一天，都是给未来的礼物')
            .fontSize(FontSizes.bodyMedium)
            .fontFamily(Fonts.LATO)
            .fontColor(this.mood.stone)
            .textAlign(TextAlign.Center)
        } else if (this.needsTogetherDate || this.lovingDays === null) {
          Text('Loving Journey')
            .fontSize(FontSizes.labelMedium)
            .fontFamily(Fonts.LATO)
            .fontColor(this.mood.accent)
          Text('还未设置在一起的时间')
            .fontSize(FontSizes.headlineMedium)
            .fontFamily(Fonts.GARAMOND)
            .fontColor(this.mood.accent)
            .opacity(0.92)
            .textAlign(TextAlign.Center)
          Text('请到「我们」中设置')
            .fontSize(FontSizes.bodyMedium)
            .fontFamily(Fonts.LATO)
            .fontColor(this.mood.stone)
            .textAlign(TextAlign.Center)
            .onClick(() => this.onSetTogetherDate())
        } else {
          Text('Loving Journey')
            .fontSize(FontSizes.labelMedium)
            .fontFamily(Fonts.LATO)
            .fontColor(this.mood.accent)
          Row() {
            Text(`${this.lovingDays}`)
              .fontSize(FontSizes.displayLarge)
              .fontFamily(Fonts.GARAMOND)
              .fontColor(this.mood.accent)
            Text(' 天')
              .fontSize(FontSizes.titleLarge)
              .fontFamily(Fonts.GARAMOND)
              .fontColor(this.mood.stone)
              .margin({ bottom: 8 })
          }
          .alignItems(VerticalAlign.Bottom)
          Text('每一天，都算数')
            .fontSize(FontSizes.bodyMedium)
            .fontFamily(Fonts.LATO)
            .fontColor(this.mood.stone)
        }
      }
      .width('100%')
      .padding(30)
      .alignItems(HorizontalAlign.Center)

      // Left of day number / vertically centered (Android CenterStart + start 18)
      Image($r('app.media.lover_logo_full'))
        .width(96)
        .height(96)
        .objectFit(ImageFit.Contain)
        .opacity(0.28)
        .rotate({ angle: -14, centerX: '50%', centerY: '50%' })
        .position({ x: 18, y: '50%' })
        .markAnchor({ x: 0, y: '50%' })
    }
    .width('100%')
    .borderRadius(Radii.large)
    .clip(true)
    .linearGradient({
      angle: 135,
      colors: [
        [this.mood.blush, 0],
        [this.withAlpha(this.mood.peach, 0.7), 1],
      ],
    })
  }
}

@Component
export struct DailyQuoteCard {
  @Prop mood: MoodPalette
  @Prop quote: DailyQuote | null = null

  private text(): string {
    if (this.quote && this.quote.text.length > 0) {
      return this.quote.text
    }
    return this.mood.solo
      ? '把今天过成日记里值得留下的一页。'
      : '平凡的日子，因为有你，每一天都值得被记住。'
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

  build() {
    Stack({ alignContent: Alignment.TopStart }) {
      Column({ space: 14 }) {
        Text('每日寄语')
          .fontSize(FontSizes.labelSmall)
          .fontFamily(Fonts.LATO_LIGHT)
          .fontColor(this.mood.soft)
          .opacity(0.8)
          .letterSpacing(4)
          .alignSelf(ItemAlign.Start)

        Row({ space: 6 }) {
          Text('“')
            .fontSize(24)
            .fontFamily(this.mood.solo ? Fonts.GARAMOND : Fonts.LATO_LIGHT)
            .fontColor(this.mood.soft)
            .opacity(0.5)
            .margin({ top: -2 })
          Text(this.text())
            .fontSize(FontSizes.bodyMedium)
            .fontFamily(Fonts.LATO)
            .fontColor(this.mood.stone)
            .opacity(0.78)
            .fontWeight(FontWeight.Lighter)
            .lineHeight(26)
            .layoutWeight(1)
        }
        .width('100%')
        .alignItems(VerticalAlign.Top)
        .padding({ right: 28 })

        if (this.quote && this.quote.author) {
          Text(`— ${this.quote.author}`)
            .fontSize(FontSizes.labelSmall)
            .fontFamily(Fonts.LATO_LIGHT)
            .fontColor(this.mood.stone)
            .opacity(0.45)
            .letterSpacing(2)
            .alignSelf(ItemAlign.Start)
        }
      }
      .width('100%')
      .padding({ left: 26, right: 26, top: 24, bottom: 28 })

      // Absolute bottom-right — align() is unreliable when Stack height = content
      Text('♥')
        .fontSize(18)
        .fontColor(this.mood.solo ? this.mood.accent : this.mood.soft)
        .opacity(this.mood.solo ? 0.35 : 0.28)
        .position({ x: '100%', y: '100%' })
        .markAnchor({ x: '100%', y: '100%' })
        .offset({ x: -22, y: -18 })
    }
    .width('100%')
    .borderRadius(Radii.extraLarge)
    .clip(true)
    .linearGradient({
      angle: 135,
      colors: [
        [this.withAlpha(this.mood.blush, 0.95), 0],
        [this.mood.solo ? this.mood.softSurface : Colors.white, 0.5],
        [this.withAlpha(this.mood.peach, this.mood.solo ? 0.55 : 0.45), 1],
      ],
    })
  }
}

/** Bento recent moments — up to 8 thumbs; featured auto-advances every 7s. */
@Component
export struct RecentGlimpseSection {
  @Prop mood: MoodPalette
  @Prop @Watch('onItems') items: MediaItem[] = []
  onViewAll: () => void = () => {}
  @State featuredIdx: number = 0
  @State snapshots: MediaItem[] = []
  private rotateTimer: number = -1

  aboutToAppear(): void {
    this.syncSnapshots()
    this.startRotate()
  }

  aboutToDisappear(): void {
    if (this.rotateTimer >= 0) {
      clearInterval(this.rotateTimer)
      this.rotateTimer = -1
    }
  }

  onItems(): void {
    this.syncSnapshots()
  }

  private syncSnapshots(): void {
    this.snapshots = this.items.slice(0, 8)
    if (this.featuredIdx >= this.snapshots.length) {
      this.featuredIdx = 0
    }
  }

  private startRotate(): void {
    if (this.rotateTimer >= 0) {
      clearInterval(this.rotateTimer)
    }
    this.rotateTimer = setInterval(() => {
      if (this.snapshots.length <= 1) {
        return
      }
      this.featuredIdx = (this.featuredIdx + 1) % this.snapshots.length
    }, GLIMPSE_INTERVAL_MS)
  }

  /** Ring-rotated item for slot offset (0 = featured large). */
  private at(offset: number): MediaItem | null {
    const n = this.snapshots.length
    if (n === 0 || offset < 0 || offset >= n) {
      return null
    }
    return this.snapshots[(this.featuredIdx + offset) % n]
  }

  private jumpTo(index: number): void {
    if (index === this.featuredIdx || this.snapshots.length === 0) {
      return
    }
    this.featuredIdx = Math.min(Math.max(index, 0), this.snapshots.length - 1)
  }

  private requireAt(offset: number): MediaItem {
    return this.at(offset) as MediaItem
  }

  build() {
    Column({ space: 14 }) {
      if (this.snapshots.length === 0) {
        Text('近期掠影')
          .fontSize(FontSizes.titleLarge)
          .fontFamily(Fonts.GARAMOND)
          .fontColor(this.mood.accent)
          .opacity(0.55)
          .letterSpacing(2)
          .alignSelf(ItemAlign.Start)
          .padding({ left: 4 })
        Column({ space: 12 }) {
          Image($r('app.media.ic_nav_photo_library'))
            .width(40)
            .height(40)
            .fillColor(this.mood.soft)
            .opacity(0.55)
          Text('还没有影像，存下第一个瞬间吧')
            .fontSize(FontSizes.bodyMedium)
            .fontFamily(Fonts.LATO)
            .fontColor(this.mood.stone)
        }
        .width('100%')
        .padding(38)
        .alignItems(HorizontalAlign.Center)
      } else {
        Row() {
          Text('近期掠影')
            .fontSize(FontSizes.titleLarge)
            .fontFamily(Fonts.GARAMOND)
            .fontColor(this.mood.accent)
            .opacity(0.55)
            .letterSpacing(2)
            .layoutWeight(1)
          Text('查看全部 →')
            .fontSize(FontSizes.labelSmall)
            .fontFamily(Fonts.LATO_LIGHT)
            .fontColor(this.mood.stone)
            .opacity(0.55)
            .letterSpacing(1.5)
            .onClick(() => this.onViewAll())
        }
        .width('100%')
        .padding({ left: 4, right: 4 })

        // Fixed slots (no ForEach): instances stay alive so all 8 crossfade together.
        Column({ space: 10 }) {
          Row({ space: 10 }) {
            GlimpseAnimatedSlot({
              mood: this.mood,
              item: this.requireAt(0),
              slide: GlimpseSlide.Featured,
              featured: true,
              showDate: true,
              showDots: this.snapshots.length > 1,
              featuredIdx: this.featuredIdx,
              dotCount: this.snapshots.length,
              onDot: (i: number) => this.jumpTo(i),
            })
              .layoutWeight(this.snapshots.length > 1 ? 2 : 1)
              .height(this.snapshots.length > 1 ? 230 : 210)

            if (this.snapshots.length > 1) {
              Column({ space: 10 }) {
                GlimpseAnimatedSlot({
                  mood: this.mood,
                  item: this.requireAt(1),
                  slide: GlimpseSlide.Vertical,
                  featured: false,
                  showDate: false,
                  showDots: false,
                  featuredIdx: this.featuredIdx,
                  dotCount: 0,
                  onDot: (_i: number) => {},
                })
                  .layoutWeight(1)
                  .width('100%')

                if (this.snapshots.length > 2) {
                  GlimpseAnimatedSlot({
                    mood: this.mood,
                    item: this.requireAt(2),
                    slide: GlimpseSlide.Vertical,
                    featured: false,
                    showDate: false,
                    showDots: false,
                    featuredIdx: this.featuredIdx,
                    dotCount: 0,
                    onDot: (_i: number) => {},
                  })
                    .layoutWeight(1)
                    .width('100%')
                }
              }
              .layoutWeight(1)
              .height(230)
            }
          }
          .width('100%')

          if (this.snapshots.length > 3) {
            Row({ space: 10 }) {
              GlimpseAnimatedSlot({
                mood: this.mood,
                item: this.requireAt(3),
                slide: GlimpseSlide.Horizontal,
                featured: false,
                showDate: false,
                showDots: false,
                featuredIdx: this.featuredIdx,
                dotCount: 0,
                onDot: (_i: number) => {},
              })
                .layoutWeight(1)
                .height(110)

              if (this.snapshots.length > 4) {
                GlimpseAnimatedSlot({
                  mood: this.mood,
                  item: this.requireAt(4),
                  slide: GlimpseSlide.Horizontal,
                  featured: false,
                  showDate: false,
                  showDots: false,
                  featuredIdx: this.featuredIdx,
                  dotCount: 0,
                  onDot: (_i: number) => {},
                })
                  .layoutWeight(1)
                  .height(110)
              }

              if (this.snapshots.length > 5) {
                GlimpseAnimatedSlot({
                  mood: this.mood,
                  item: this.requireAt(5),
                  slide: GlimpseSlide.Horizontal,
                  featured: false,
                  showDate: false,
                  showDots: false,
                  featuredIdx: this.featuredIdx,
                  dotCount: 0,
                  onDot: (_i: number) => {},
                })
                  .layoutWeight(1)
                  .height(110)
              }
            }
            .width('100%')
          }

          if (this.snapshots.length > 6) {
            Row({ space: 10 }) {
              GlimpseAnimatedSlot({
                mood: this.mood,
                item: this.requireAt(6),
                slide: GlimpseSlide.Horizontal,
                featured: false,
                showDate: false,
                showDots: false,
                featuredIdx: this.featuredIdx,
                dotCount: 0,
                onDot: (_i: number) => {},
              })
                .layoutWeight(1)
                .height(100)

              if (this.snapshots.length > 7) {
                GlimpseAnimatedSlot({
                  mood: this.mood,
                  item: this.requireAt(7),
                  slide: GlimpseSlide.Horizontal,
                  featured: false,
                  showDate: false,
                  showDots: false,
                  featuredIdx: this.featuredIdx,
                  dotCount: 0,
                  onDot: (_i: number) => {},
                })
                  .layoutWeight(2)
                  .height(100)
              }
            }
            .width('100%')
          }
        }
        .width('100%')
      }
    }
    .width('100%')
  }
}

/**
 * Cross-fade + slide when item changes — mirrors Android AnimatedContent
 * (fade + slideIn/Out, 920ms, CubicBezier 0.33,0,0.2,1).
 */
@Component
struct GlimpseAnimatedSlot {
  @Prop mood: MoodPalette
  @Prop @Watch('onItemChange') item: MediaItem = new MediaItem()
  @Prop slide: GlimpseSlide = GlimpseSlide.Featured
  @Prop featured: boolean = false
  @Prop showDate: boolean = false
  @Prop showDots: boolean = false
  @Prop featuredIdx: number = 0
  @Prop dotCount: number = 0
  onDot: (index: number) => void = (_i: number) => {}

  @State private curr: MediaItem = new MediaItem()
  @State private prev: MediaItem = new MediaItem()
  @State private showPrev: boolean = false
  @State private currOpacity: number = 1
  @State private prevOpacity: number = 0
  @State private currX: number = 0
  @State private currY: number = 0
  @State private prevX: number = 0
  @State private prevY: number = 0
  private ready: boolean = false
  private animating: boolean = false

  aboutToAppear(): void {
    this.curr = this.item
    this.ready = true
  }

  onItemChange(): void {
    if (!this.ready) {
      this.curr = this.item
      return
    }
    if (this.curr.id === this.item.id) {
      this.curr = this.item
      return
    }
    this.runCrossfade(this.item)
  }

  private enterOffset(): number[] {
    // [x, y] enter from — Android: featured /8, vertical /5, horizontal /6 of size
    if (this.slide === GlimpseSlide.Featured) {
      return [36, 0]
    }
    if (this.slide === GlimpseSlide.Vertical) {
      return [0, 28]
    }
    return [28, 0]
  }

  private exitOffset(): number[] {
    if (this.slide === GlimpseSlide.Featured) {
      return [-28, 0]
    }
    if (this.slide === GlimpseSlide.Vertical) {
      return [0, -28]
    }
    return [-24, 0]
  }

  private runCrossfade(next: MediaItem): void {
    if (this.animating) {
      this.showPrev = false
      this.curr = next
      this.currOpacity = 1
      this.currX = 0
      this.currY = 0
      return
    }
    this.animating = true
    this.prev = this.curr
    this.showPrev = true
    const enter = this.enterOffset()
    const exit = this.exitOffset()
    this.curr = next
    this.currOpacity = 0
    this.currX = enter[0]
    this.currY = enter[1]
    this.prevOpacity = 1
    this.prevX = 0
    this.prevY = 0

    const ease = curves.cubicBezierCurve(0.33, 0, 0.2, 1)
    try {
      this.getUIContext().animateTo({
        duration: GLIMPSE_ANIM_MS,
        curve: ease,
      }, () => {
        this.currOpacity = 1
        this.currX = 0
        this.currY = 0
        this.prevOpacity = 0
        this.prevX = exit[0]
        this.prevY = exit[1]
      })
    } catch (_e) {
      this.currOpacity = 1
      this.currX = 0
      this.currY = 0
      this.showPrev = false
    }

    setTimeout(() => {
      this.showPrev = false
      this.prevOpacity = 0
      this.animating = false
    }, GLIMPSE_ANIM_MS + 40)
  }

  private dots(): number[] {
    const arr: number[] = []
    for (let i = 0; i < this.dotCount; i++) {
      arr.push(i)
    }
    return arr
  }

  build() {
    Stack({ alignContent: Alignment.BottomStart }) {
      if (this.showPrev) {
        GlimpseThumbFace({
          mood: this.mood,
          item: this.prev,
          featured: this.featured,
          showDate: this.showDate,
        })
          .width('100%')
          .height('100%')
          .opacity(this.prevOpacity)
          .translate({ x: this.prevX, y: this.prevY })
      }

      GlimpseThumbFace({
        mood: this.mood,
        item: this.curr,
        featured: this.featured,
        showDate: this.showDate,
      })
        .width('100%')
        .height('100%')
        .opacity(this.currOpacity)
        .translate({ x: this.currX, y: this.currY })

      if (this.showDots && this.dotCount > 1) {
        Row({ space: 4 }) {
          ForEach(this.dots(), (index: number) => {
            Row()
              .width(index === this.featuredIdx ? 14 : 5)
              .height(5)
              .borderRadius(3)
              .backgroundColor(index === this.featuredIdx ?
                'rgba(255,255,255,0.9)' : 'rgba(255,255,255,0.4)')
              .onClick(() => this.onDot(index))
          }, (index: number) => `dot_${index}_${this.featuredIdx}`)
        }
        .align(Alignment.TopEnd)
        .margin(12)
        .zIndex(2)
      }
    }
    .width('100%')
    .height('100%')
    .borderRadius(this.featured ? 32 : 24)
    .clip(true)
    .onClick(() => AppNav.pushMediaDetail(this.curr.id))
  }
}

@Component
struct GlimpseThumbFace {
  @Prop mood: MoodPalette
  @Prop item: MediaItem = new MediaItem()
  @Prop featured: boolean = false
  @Prop showDate: boolean = false

  build() {
    Stack({ alignContent: Alignment.BottomStart }) {
      if (MediaItem.thumbUrlOf(this.item).length > 0) {
        Image(MediaItem.thumbUrlOf(this.item))
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
        .linearGradient({
          angle: 180,
          colors: [
            ['rgba(0,0,0,0)', 0],
            ['rgba(0,0,0,0)', 0.45],
            ['rgba(0,0,0,0.5)', 1],
          ],
        })

      Column({ space: 2 }) {
        Text(this.item.caption && this.item.caption.length > 0 ? this.item.caption : '未命名时光')
          .fontSize(this.featured ? FontSizes.bodySmall : FontSizes.labelSmall)
          .fontFamily(Fonts.LATO_LIGHT)
          .fontColor(Colors.white)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .width('100%')
        if (this.showDate) {
          Text(this.item.mediaDate)
            .fontSize(9)
            .fontFamily(Fonts.LATO_LIGHT)
            .fontColor(Colors.white)
            .opacity(0.55)
        }
      }
      .width('100%')
      .padding({
        left: this.featured ? 14 : 10,
        right: this.featured ? 14 : 10,
        top: this.featured ? 12 : 8,
        bottom: this.featured ? 12 : 8,
      })
    }
    .width('100%')
    .height('100%')
  }
}
