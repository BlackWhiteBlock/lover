/**
 * 未读时光图标 — 对齐设计说明（鸿蒙端）。
 * 静默：透明底盘 + #fda4af 描边
 * 点亮：粉白渐变底盘 + 四层动画（弹跳摇摆 / 双脉冲 / 飞散光点 / 角标）
 */
import { Colors } from './Theme'
import { Fonts } from './Fonts'

const QUIET_STROKE = '#FDA4AF'
const ACTIVE_ROSE = '#FB7185'
const BADGE_BG = '#F43F5E'
const BOUNCE_CYCLE_MS = 4000
const BOUNCE_ACTIVE_MS = 2800
const PULSE_CYCLE_MS = 2200
const PULSE_PHASE_MS = 550
const SPARKLE_CYCLE_MS = 3500
const SPARKLE_DUR_MS = 1800
const SPARKLE_STAGGER_MS = 700
const SPARKLE_FLY_VP = 11
const SPARKLE_DOT_VP = 3.5

@Component
export struct UnreadMemoryIcon {
  @Prop @Watch('onCountChanged') count: number = 0
  onIconClick: () => void = () => {}

  @State bounceProgress: number = 0
  @State pulse1: number = 0
  @State pulse2: number = 0
  @State sparkleClock: number = 0
  @State badgeScale: number = 0
  @State lit: boolean = false
  @State displayCount: number = 0
  private running: boolean = false
  private pulse2Timer: number = -1

  aboutToAppear(): void {
    this.lit = this.count > 0
    this.displayCount = this.count
    this.badgeScale = this.count > 0 ? 1 : 0
    this.syncAnimation()
  }

  aboutToDisappear(): void {
    this.stopAnimation()
  }

  onCountChanged(): void {
    const nextLit = this.count > 0
    if (this.count > 0) {
      this.displayCount = this.count
    }
    if (nextLit && !this.lit) {
      this.badgeScale = 0
      this.animTo(320, Curve.EaseOut, () => {
        this.badgeScale = 1
      })
    } else if (!nextLit && this.lit) {
      this.animTo(220, Curve.EaseIn, () => {
        this.badgeScale = 0
      })
    }
    this.lit = nextLit
    this.syncAnimation()
  }

  private animTo(
    duration: number,
    curve: Curve,
    update: () => void,
    onFinish?: () => void,
  ): void {
    try {
      this.getUIContext().animateTo({
        duration: duration,
        curve: curve,
        onFinish: onFinish,
      }, update)
    } catch (_e) {
      // 全局 animateTo 兜底
      animateTo({
        duration: duration,
        curve: curve,
        onFinish: onFinish,
      }, update)
    }
  }

  private stopAnimation(): void {
    this.running = false
    if (this.pulse2Timer >= 0) {
      clearTimeout(this.pulse2Timer)
      this.pulse2Timer = -1
    }
    this.bounceProgress = 0
    this.pulse1 = 0
    this.pulse2 = 0
    this.sparkleClock = 0
  }

  private syncAnimation(): void {
    if (this.lit) {
      if (!this.running) {
        this.running = true
        this.loopBounce()
        this.loopPulse1()
        this.pulse2Timer = setTimeout(() => {
          this.pulse2Timer = -1
          if (this.running) {
            this.loopPulse2()
          }
        }, PULSE_PHASE_MS)
        this.loopSparkle()
      }
    } else {
      this.stopAnimation()
    }
  }

  private loopBounce(): void {
    if (!this.running) {
      return
    }
    this.bounceProgress = 0
    this.animTo(BOUNCE_CYCLE_MS, Curve.Linear, () => {
      this.bounceProgress = 1
    }, () => {
      if (this.running) {
        this.loopBounce()
      }
    })
  }

  private loopPulse1(): void {
    if (!this.running) {
      return
    }
    this.pulse1 = 0
    this.animTo(PULSE_CYCLE_MS, Curve.Linear, () => {
      this.pulse1 = 1
    }, () => {
      if (this.running) {
        this.loopPulse1()
      }
    })
  }

  private loopPulse2(): void {
    if (!this.running) {
      return
    }
    this.pulse2 = 0
    this.animTo(PULSE_CYCLE_MS, Curve.Linear, () => {
      this.pulse2 = 1
    }, () => {
      if (this.running) {
        this.loopPulse2()
      }
    })
  }

  private loopSparkle(): void {
    if (!this.running) {
      return
    }
    this.sparkleClock = 0
    this.animTo(SPARKLE_CYCLE_MS, Curve.Linear, () => {
      this.sparkleClock = 1
    }, () => {
      if (this.running) {
        this.loopSparkle()
      }
    })
  }

  private bounceY(): number {
    if (!this.lit) {
      return 0
    }
    const t = this.bounceProgress * BOUNCE_CYCLE_MS
    if (t >= BOUNCE_ACTIVE_MS) {
      return 0
    }
    if (t < 350) {
      return -4 * (t / 350)
    }
    if (t < 700) {
      return -4 + 4 * ((t - 350) / 350)
    }
    if (t < 1050) {
      return -2 * ((t - 700) / 350)
    }
    if (t < 1400) {
      return -2 + 2 * ((t - 1050) / 350)
    }
    return 0
  }

  private bounceRot(): number {
    if (!this.lit) {
      return 0
    }
    const t = this.bounceProgress * BOUNCE_CYCLE_MS
    if (t >= BOUNCE_ACTIVE_MS) {
      return 0
    }
    if (t < 450) {
      return -6 * (t / 450)
    }
    if (t < 1100) {
      return -6 + 12 * ((t - 450) / 650)
    }
    if (t < 1700) {
      return 6 - 9 * ((t - 1100) / 600)
    }
    if (t < 2300) {
      return -3 + 3 * ((t - 1700) / 600)
    }
    return 0
  }

  private ringScale(progress: number, maxScale: number): number {
    return 1 + (maxScale - 1) * progress
  }

  private ringOpacity(progress: number): number {
    return (1 - progress) * 0.45
  }

  private sparkleLocal(index: number): number {
    const tSec = this.sparkleClock * (SPARKLE_CYCLE_MS / 1000)
    const cycleSec = SPARKLE_CYCLE_MS / 1000
    const staggerSec = SPARKLE_STAGGER_MS / 1000
    return (tSec - index * staggerSec + cycleSec) % cycleSec
  }

  private sparkleOpacity(index: number): number {
    if (!this.lit) {
      return 0
    }
    const local = this.sparkleLocal(index)
    const durSec = SPARKLE_DUR_MS / 1000
    if (local > durSec) {
      return 0
    }
    const p = local / durSec
    const appear = Math.min(1, p / 0.15)
    const fade = Math.min(1, (1 - p) / 0.25)
    return appear * fade
  }

  private sparkleDist(index: number): number {
    if (!this.lit) {
      return 0
    }
    const local = this.sparkleLocal(index)
    const durSec = SPARKLE_DUR_MS / 1000
    if (local > durSec) {
      return 0
    }
    return (local / durSec) * SPARKLE_FLY_VP
  }

  private sparkleScale(index: number): number {
    if (!this.lit) {
      return 0.4
    }
    const local = this.sparkleLocal(index)
    const durSec = SPARKLE_DUR_MS / 1000
    if (local > durSec) {
      return 0.4
    }
    const p = local / durSec
    return 0.4 + p * 0.85
  }

  private sparkleX(index: number, angleDeg: number): number {
    return Math.cos(angleDeg * Math.PI / 180) * this.sparkleDist(index)
  }

  private sparkleY(index: number, angleDeg: number): number {
    return Math.sin(angleDeg * Math.PI / 180) * this.sparkleDist(index)
  }

  private badgeText(): string {
    return this.displayCount > 9 ? '9+' : `${this.displayCount}`
  }

  build() {
    Stack({ alignContent: Alignment.Center }) {
      // 层 2：双脉冲光晕
      if (this.lit) {
        Column()
          .width(26)
          .height(26)
          .borderRadius(13)
          .backgroundColor('rgba(251,113,133,0.35)')
          .scale({ x: this.ringScale(this.pulse1, 1.7), y: this.ringScale(this.pulse1, 1.7) })
          .opacity(this.ringOpacity(this.pulse1))
        Column()
          .width(26)
          .height(26)
          .borderRadius(13)
          .backgroundColor('rgba(251,113,133,0.28)')
          .scale({ x: this.ringScale(this.pulse2, 2.1), y: this.ringScale(this.pulse2, 2.1) })
          .opacity(this.ringOpacity(this.pulse2))
      }

      // 层 1 + 3 + 4：主体 / 光点 / 角标
      Stack({ alignContent: Alignment.Center }) {
        if (this.lit) {
          Column()
            .width(26)
            .height(26)
            .borderRadius(13)
            .linearGradient({
              angle: 135,
              colors: [['#FFF0F2', 0], ['#FFE4E8', 1]],
            })
            .border({ width: 0.8, color: 'rgba(251,113,133,0.35)' })
        } else {
          Column()
            .width(26)
            .height(26)
            .borderRadius(13)
            .backgroundColor('transparent')
            .border({ width: 1.2, color: QUIET_STROKE })
        }

        if (this.lit) {
          Column()
            .width(12)
            .height(11)
            .borderRadius(6)
            .backgroundColor('rgba(251,113,133,0.18)')
            .margin({ top: 1 })
        }

        Image($r('app.media.ic_unread_memory'))
          .width(16)
          .height(16)
          .objectFit(ImageFit.Contain)
          .fillColor(this.lit ? ACTIVE_ROSE : QUIET_STROKE)

        if (this.lit) {
          Column()
            .width(SPARKLE_DOT_VP)
            .height(SPARKLE_DOT_VP)
            .borderRadius(SPARKLE_DOT_VP / 2)
            .backgroundColor(ACTIVE_ROSE)
            .opacity(this.sparkleOpacity(0))
            .scale({ x: this.sparkleScale(0), y: this.sparkleScale(0) })
            .translate({ x: this.sparkleX(0, -45), y: this.sparkleY(0, -45) })
          Column()
            .width(SPARKLE_DOT_VP)
            .height(SPARKLE_DOT_VP)
            .borderRadius(SPARKLE_DOT_VP / 2)
            .backgroundColor(ACTIVE_ROSE)
            .opacity(this.sparkleOpacity(1))
            .scale({ x: this.sparkleScale(1), y: this.sparkleScale(1) })
            .translate({ x: this.sparkleX(1, 45), y: this.sparkleY(1, 45) })
          Column()
            .width(SPARKLE_DOT_VP)
            .height(SPARKLE_DOT_VP)
            .borderRadius(SPARKLE_DOT_VP / 2)
            .backgroundColor(ACTIVE_ROSE)
            .opacity(this.sparkleOpacity(2))
            .scale({ x: this.sparkleScale(2), y: this.sparkleScale(2) })
            .translate({ x: this.sparkleX(2, 135), y: this.sparkleY(2, 135) })
        }

        if (this.badgeScale > 0.01 && this.displayCount > 0) {
          Text(this.badgeText())
            .fontSize(9)
            .fontWeight(FontWeight.Bold)
            .fontFamily(Fonts.LATO)
            .fontColor(Colors.white)
            .padding({ left: 4, right: 4, top: 1, bottom: 1 })
            .constraintSize({ minWidth: 16, minHeight: 14 })
            .textAlign(TextAlign.Center)
            .borderRadius(8)
            .backgroundColor(BADGE_BG)
            .shadow({ radius: 3, color: 'rgba(244,63,94,0.4)', offsetY: 2 })
            .scale({ x: this.badgeScale, y: this.badgeScale })
            .position({ x: 22, y: -1 })
        }
      }
      .width(40)
      .height(40)
      .translate({ y: this.bounceY() })
      .rotate({ angle: this.bounceRot() })
      .clip(false)
    }
    .width(48)
    .height(48)
    .clip(false)
    .onClick(() => this.onIconClick())
  }
}
