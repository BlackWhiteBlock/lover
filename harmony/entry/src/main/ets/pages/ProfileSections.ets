/**
 * Profile couple cards — mirrors Android MainScreen ProfilePage visuals.
 */
import { Fonts } from '../core/design/Fonts'
import { Colors, FontSizes, Radii } from '../core/design/Theme'
import type { MoodPalette } from '../core/design/Theme'
import { SoftPrimaryButton } from '../core/design/SoftInputs'
import { curves } from '@kit.ArkUI'

const PULSE_DOTS: number[] = [0, 1, 2]

export enum CoupleBondMode {
  Empty = 0,
  Linking = 1,
  Bound = 2,
}

export function displayName(nickname: string): string {
  const t = nickname.trim()
  if (t.length === 0) {
    return '我'
  }
  return t.length > 12 ? t.substring(0, 12) : t
}

export function bindInviterLabel(nickname: string, phone: string): string {
  const name = nickname.trim().length > 0 ? nickname.trim() : '对方'
  let digits = ''
  for (let i = 0; i < phone.length; i++) {
    const c = phone.charAt(i)
    if (c >= '0' && c <= '9') {
      digits += c
    }
  }
  const tail = digits.length >= 4 ? digits.substring(digits.length - 4) : (digits.length > 0 ? digits : '????')
  return `${name}(${tail})`
}

export function togetherDateLabel(togetherDate: string | null, lovingDays: number | null): string {
  if (!togetherDate || togetherDate.trim().length === 0) {
    return '我们一起的时间还未设置'
  }
  const pretty = togetherDate.replace(/-/g, '.')
  if (lovingDays !== null && lovingDays > 0) {
    return `在一起第 ${lovingDays} 天 · ${pretty}`
  }
  return `在一起自 ${pretty}`
}

function withAlpha(hex: string, alpha: number): string {
  if (hex.length === 7 && hex.startsWith('#')) {
    const r = parseInt(hex.substring(1, 3), 16)
    const g = parseInt(hex.substring(3, 5), 16)
    const b = parseInt(hex.substring(5, 7), 16)
    return `rgba(${r},${g},${b},${alpha})`
  }
  return hex
}

@Component
export struct PersonAvatar {
  @Prop mood: MoodPalette
  @Prop nickname: string = '我'
  @Prop avatarUrl: string = ''
  @Prop sizeVp: number = 72
  @Prop placeholderMark: boolean = false

  private initial(): string {
    const n = displayName(this.nickname)
    return n.length > 0 ? n.substring(0, 1) : '我'
  }

  build() {
    Stack({ alignContent: Alignment.Center }) {
      Column()
        .width(this.sizeVp)
        .height(this.sizeVp)
        .borderRadius(this.sizeVp / 2)
        .backgroundColor(Colors.white)
        .border({ width: 2, color: Colors.white })

      Stack({ alignContent: Alignment.Center }) {
        if (this.avatarUrl.length > 0 && !this.placeholderMark) {
          Image(this.avatarUrl)
            .width('100%')
            .height('100%')
            .objectFit(ImageFit.Cover)
            .borderRadius((this.sizeVp - 6) / 2)
        } else if (this.placeholderMark) {
          Text('?')
            .fontSize(FontSizes.headlineMedium)
            .fontFamily(Fonts.GARAMOND)
            .fontColor(this.mood.soft)
        } else {
          Text(this.initial())
            .fontSize(FontSizes.headlineMedium)
            .fontFamily(Fonts.GARAMOND)
            .fontColor(Colors.white)
        }
      }
      .width(this.sizeVp - 6)
      .height(this.sizeVp - 6)
      .borderRadius((this.sizeVp - 6) / 2)
      .backgroundColor(this.placeholderMark ? this.mood.blush : withAlpha(this.mood.soft, 0.9))
      .clip(true)
    }
    .width(this.sizeVp)
    .height(this.sizeVp)
  }
}

@Component
struct PersonAvatarWithName {
  @Prop mood: MoodPalette
  @Prop nickname: string = '我'
  @Prop avatarUrl: string = ''
  @Prop placeholderMark: boolean = false

  build() {
    Column({ space: 8 }) {
      PersonAvatar({
        mood: this.mood,
        nickname: this.nickname,
        avatarUrl: this.avatarUrl,
        sizeVp: 72,
        placeholderMark: this.placeholderMark,
      })
      Text(this.placeholderMark ? '待绑定' : displayName(this.nickname))
        .fontSize(FontSizes.bodySmall)
        .fontFamily(Fonts.LATO)
        .fontColor(this.mood.stone)
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
        .textAlign(TextAlign.Center)
    }
    .constraintSize({ maxWidth: 96 })
    .alignItems(HorizontalAlign.Center)
  }
}

@Component
struct LinkingPulse {
  @Prop mood: MoodPalette
  @State private travel: number = 0
  @State private glow: number = 0.35
  private running: boolean = false

  aboutToAppear(): void {
    this.running = true
    this.loopTravel()
    this.loopGlow()
  }

  aboutToDisappear(): void {
    this.running = false
  }

  private loopTravel(): void {
    if (!this.running) {
      return
    }
    this.travel = 0
    animateTo({
      duration: 1400,
      curve: curves.cubicBezierCurve(0, 0, 1, 1),
      onFinish: () => {
        if (this.running) {
          this.loopTravel()
        }
      },
    }, () => {
      this.travel = 1
    })
  }

  private loopGlow(): void {
    if (!this.running) {
      return
    }
    const next = this.glow > 0.6 ? 0.35 : 1
    animateTo({
      duration: 900,
      curve: curves.cubicBezierCurve(0.33, 0, 0.2, 1),
      onFinish: () => {
        if (this.running) {
          this.loopGlow()
        }
      },
    }, () => {
      this.glow = next
    })
  }

  build() {
    Stack({ alignContent: Alignment.Center }) {
      Row()
        .width('100%')
        .height(2)
        .borderRadius(2)
        .backgroundColor(withAlpha(this.mood.soft, 0.22 * this.glow))

      Row() {
        ForEach(PULSE_DOTS, (index: number) => {
          Column()
            .width(6)
            .height(6)
            .borderRadius(3)
            .backgroundColor(this.mood.soft)
            .opacity(0.35 + 0.45 * this.glow)
        }, (index: number) => `dot_${index}`)
      }
      .width('100%')
      .justifyContent(FlexAlign.SpaceBetween)

      Text('♥')
        .fontSize(12)
        .fontColor(withAlpha(this.mood.soft, 0.55 + 0.35 * this.glow))
        .translate({ x: (this.travel - 0.5) * 36 })
        .scale({ x: 0.85 + 0.2 * this.glow, y: 0.85 + 0.2 * this.glow })
    }
    .width(56)
    .height(20)
  }
}

@Component
export struct CoupleBondVisual {
  @Prop mood: MoodPalette
  @Prop mode: CoupleBondMode = CoupleBondMode.Empty
  @Prop leftNickname: string = '我'
  @Prop leftAvatarUrl: string = ''
  @Prop rightNickname: string = '?'
  @Prop rightAvatarUrl: string = ''
  @Prop coupleCoverUrl: string = ''

  build() {
    if (this.mode === CoupleBondMode.Bound) {
      Column({ space: 10 }) {
        if (this.coupleCoverUrl.length > 0) {
          Stack({ alignContent: Alignment.Center }) {
            Column()
              .width(96)
              .height(96)
              .borderRadius(48)
              .backgroundColor(Colors.white)
              .border({ width: 2, color: Colors.white })
            Image(this.coupleCoverUrl)
              .width(90)
              .height(90)
              .borderRadius(45)
              .objectFit(ImageFit.Cover)
              .backgroundColor(withAlpha(this.mood.soft, 0.9))
          }
        } else {
          Stack() {
            PersonAvatar({
              mood: this.mood,
              nickname: this.leftNickname,
              avatarUrl: this.leftAvatarUrl,
              sizeVp: 72,
            })
              .position({ x: 0, y: 0 })
            PersonAvatar({
              mood: this.mood,
              nickname: this.rightNickname,
              avatarUrl: this.rightAvatarUrl,
              sizeVp: 72,
            })
              .position({ x: 58, y: 0 })
          }
          .width(130)
          .height(72)
        }
        Text(`${displayName(this.leftNickname)} · ${displayName(this.rightNickname)}`)
          .fontSize(FontSizes.bodyMedium)
          .fontFamily(Fonts.LATO)
          .fontColor(this.mood.stone)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .textAlign(TextAlign.Center)
      }
      .width('100%')
      .alignItems(HorizontalAlign.Center)
    } else if (this.mode === CoupleBondMode.Linking) {
      Row() {
        PersonAvatarWithName({
          mood: this.mood,
          nickname: this.leftNickname,
          avatarUrl: this.leftAvatarUrl,
        })
        LinkingPulse({ mood: this.mood })
          .margin({ top: 28, left: 6, right: 6 })
        PersonAvatarWithName({
          mood: this.mood,
          nickname: this.rightNickname,
          avatarUrl: this.rightAvatarUrl,
        })
      }
      .width('100%')
      .justifyContent(FlexAlign.Center)
      .alignItems(VerticalAlign.Top)
    } else {
      Row({ space: 18 }) {
        PersonAvatarWithName({
          mood: this.mood,
          nickname: this.leftNickname,
          avatarUrl: this.leftAvatarUrl,
        })
        PersonAvatarWithName({
          mood: this.mood,
          nickname: this.rightNickname,
          avatarUrl: '',
          placeholderMark: true,
        })
      }
      .width('100%')
      .justifyContent(FlexAlign.Center)
      .alignItems(VerticalAlign.Top)
    }
  }
}

@Component
struct CardEditButton {
  @Prop mood: MoodPalette
  onEdit: () => void = () => {}

  build() {
    // Android IconButton + Icons.Rounded.Edit 20dp, accent @ 0.75, padding 6
    Stack({ alignContent: Alignment.Center }) {
      Image($r('app.media.ic_edit'))
        .width(20)
        .height(20)
        .objectFit(ImageFit.Contain)
        .fillColor(withAlpha(this.mood.accent, 0.75))
    }
    .width(40)
    .height(40)
    .padding(6)
    .onClick(() => this.onEdit())
  }
}

@Component
export struct EmptyCoupleCard {
  @Prop mood: MoodPalette
  @Prop spaceName: string = '我的小宇宙'
  @Prop meNickname: string = '我'
  @Prop meAvatarUrl: string = ''
  @Prop @Watch('onOutgoing') hasOutgoing: boolean = false
  @Prop outgoingNickname: string = ''
  @Prop outgoingAvatarUrl: string = ''
  @Prop outgoingLabel: string = ''
  onEdit: () => void = () => {}
  onBind: () => void = () => {}
  onCancelOutgoing: () => void = () => {}

  onOutgoing(): void {}

  build() {
    Stack({ alignContent: Alignment.TopEnd }) {
      Column({ space: 14 }) {
        if (this.hasOutgoing) {
          CoupleBondVisual({
            mood: this.mood,
            mode: CoupleBondMode.Linking,
            leftNickname: this.meNickname,
            leftAvatarUrl: this.meAvatarUrl,
            rightNickname: this.outgoingNickname.length > 0 ? this.outgoingNickname : (this.outgoingLabel.length > 0 ? this.outgoingLabel : '对方'),
            rightAvatarUrl: this.outgoingAvatarUrl,
          })
        } else {
          CoupleBondVisual({
            mood: this.mood,
            mode: CoupleBondMode.Empty,
            leftNickname: this.meNickname,
            leftAvatarUrl: this.meAvatarUrl,
            rightNickname: '?',
            rightAvatarUrl: '',
          })
        }

        Text(this.spaceName)
          .fontSize(FontSizes.headlineMedium)
          .fontFamily(Fonts.GARAMOND)
          .fontColor(this.mood.onSurface)
          .textAlign(TextAlign.Center)

        if (this.hasOutgoing && this.outgoingLabel.length > 0) {
          Text(`已向 ${this.outgoingLabel} 发送绑定邀请`)
            .fontSize(FontSizes.bodyLarge)
            .fontFamily(Fonts.LATO)
            .fontColor(this.mood.stone)
            .textAlign(TextAlign.Center)
          Text('状态：等待对方确认')
            .fontSize(FontSizes.bodySmall)
            .fontFamily(Fonts.LATO)
            .fontColor(this.mood.soft)
          Text('取消请求')
            .fontSize(FontSizes.bodyMedium)
            .fontFamily(Fonts.LATO)
            .fontColor(this.mood.accent)
            .padding({ top: 4, bottom: 4 })
            .onClick(() => this.onCancelOutgoing())
        } else {
          Text('现在还没有我们喔，快去绑定另一半吧')
            .fontSize(FontSizes.bodyLarge)
            .fontFamily(Fonts.LATO)
            .fontColor(this.mood.stone)
            .textAlign(TextAlign.Center)
          SoftPrimaryButton({
            mood: this.mood,
            label: '绑定另一半',
            onTap: () => this.onBind(),
          })
        }
      }
      .width('100%')
      .padding(28)
      .alignItems(HorizontalAlign.Center)

      CardEditButton({ mood: this.mood, onEdit: () => this.onEdit() })
        .margin({ top: 6, right: 6 })
    }
    .width('100%')
    .borderRadius(32)
    .backgroundColor(withAlpha(this.mood.blush, 0.85))
    .clip(true)
  }
}

@Component
export struct IncomingBindCard {
  @Prop mood: MoodPalette
  @Prop spaceName: string = '我的小宇宙'
  @Prop meNickname: string = '我'
  @Prop meAvatarUrl: string = ''
  @Prop inviterNickname: string = '对方'
  @Prop inviterAvatarUrl: string = ''
  @Prop inviterLabel: string = '对方'
  onEdit: () => void = () => {}
  onAccept: () => void = () => {}
  onReject: () => void = () => {}

  build() {
    Stack({ alignContent: Alignment.TopEnd }) {
      Column({ space: 14 }) {
        CoupleBondVisual({
          mood: this.mood,
          mode: CoupleBondMode.Linking,
          leftNickname: this.inviterNickname.length > 0 ? this.inviterNickname : '对方',
          leftAvatarUrl: this.inviterAvatarUrl,
          rightNickname: this.meNickname,
          rightAvatarUrl: this.meAvatarUrl,
        })
        Text(this.spaceName)
          .fontSize(FontSizes.headlineMedium)
          .fontFamily(Fonts.GARAMOND)
          .fontColor(this.mood.onSurface)
          .textAlign(TextAlign.Center)
        Text(`${this.inviterLabel}邀请您绑定`)
          .fontSize(FontSizes.bodyLarge)
          .fontFamily(Fonts.LATO)
          .fontColor(this.mood.stone)
          .textAlign(TextAlign.Center)
        Row({ space: 12 }) {
          Button('拒绝')
            .layoutWeight(1)
            .height(48)
            .borderRadius(20)
            .backgroundColor(Colors.white)
            .fontColor(this.mood.onSurface)
            .border({ width: 1, color: this.mood.softOutline })
            .fontFamily(Fonts.LATO)
            .onClick(() => this.onReject())
          Button('同意')
            .layoutWeight(1)
            .height(48)
            .borderRadius(20)
            .backgroundColor(this.mood.soft)
            .fontColor(Colors.white)
            .fontFamily(Fonts.LATO)
            .onClick(() => this.onAccept())
        }
        .width('100%')
      }
      .width('100%')
      .padding(28)
      .alignItems(HorizontalAlign.Center)

      CardEditButton({ mood: this.mood, onEdit: () => this.onEdit() })
        .margin({ top: 6, right: 6 })
    }
    .width('100%')
    .borderRadius(32)
    .backgroundColor(withAlpha(this.mood.blush, 0.85))
    .clip(true)
  }
}

@Component
export struct BoundCoupleCard {
  @Prop mood: MoodPalette
  @Prop spaceName: string = '我们'
  @Prop togetherDate: string = ''
  @Prop lovingDays: number = -1
  @Prop meNickname: string = '我'
  @Prop meAvatarUrl: string = ''
  @Prop partnerNickname: string = 'TA'
  @Prop partnerAvatarUrl: string = ''
  @Prop coupleCoverUrl: string = ''
  onEdit: () => void = () => {}

  private daysOrNull(): number | null {
    return this.lovingDays >= 0 ? this.lovingDays : null
  }

  private dateOrNull(): string | null {
    return this.togetherDate.length > 0 ? this.togetherDate : null
  }

  build() {
    Stack({ alignContent: Alignment.TopEnd }) {
      Column({ space: 12 }) {
        CoupleBondVisual({
          mood: this.mood,
          mode: CoupleBondMode.Bound,
          leftNickname: this.meNickname,
          leftAvatarUrl: this.meAvatarUrl,
          rightNickname: this.partnerNickname,
          rightAvatarUrl: this.partnerAvatarUrl,
          coupleCoverUrl: this.coupleCoverUrl,
        })
        Text(this.spaceName)
          .fontSize(FontSizes.headlineMedium)
          .fontFamily(Fonts.GARAMOND)
          .fontColor(this.mood.onSurface)
          .textAlign(TextAlign.Center)
        Text(togetherDateLabel(this.dateOrNull(), this.daysOrNull()))
          .fontSize(FontSizes.bodyMedium)
          .fontFamily(Fonts.LATO)
          .fontColor(this.mood.stone)
          .textAlign(TextAlign.Center)
      }
      .width('100%')
      .padding(28)
      .alignItems(HorizontalAlign.Center)

      CardEditButton({ mood: this.mood, onEdit: () => this.onEdit() })
        .margin({ top: 6, right: 6 })
    }
    .width('100%')
    .borderRadius(32)
    .backgroundColor(withAlpha(this.mood.blush, 0.85))
    .clip(true)
  }
}

@Component
export struct ProfileSettingRow {
  @Prop mood: MoodPalette
  @Prop title: string = ''
  @Prop detail: string = ''
  @Prop iconGlyph: string = '•'

  build() {
    Row() {
      Stack({ alignContent: Alignment.Center }) {
        Text(this.iconGlyph)
          .fontSize(16)
          .fontColor(this.mood.soft)
      }
      .width(40)
      .height(40)
      .borderRadius(20)
      .backgroundColor(this.mood.blush)

      Column({ space: 2 }) {
        Text(this.title)
          .fontSize(FontSizes.bodyLarge)
          .fontFamily(Fonts.LATO)
          .fontColor(this.mood.onSurface)
          .fontWeight(FontWeight.Medium)
        Text(this.detail)
          .fontSize(FontSizes.bodySmall)
          .fontFamily(Fonts.LATO)
          .fontColor(this.mood.stone)
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
      .margin({ left: 14, right: 14 })

      Text('›')
        .fontSize(22)
        .fontColor(withAlpha(this.mood.stone, 0.7))
    }
    .width('100%')
    .padding({ left: 16, right: 16, top: 14, bottom: 14 })
    .borderRadius(22)
    .backgroundColor(this.mood.softSurface)
    .border({ width: 1, color: withAlpha(this.mood.softOutline, 0.65) })
    .margin({ top: 6, bottom: 6 })
  }
}
