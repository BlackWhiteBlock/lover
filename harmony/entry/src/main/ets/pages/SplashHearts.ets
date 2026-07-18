/**
 * Splash heart stage — mirrors Android SplashScreen.kt.
 * Petals use PNG (SVG Image is unreliable on Harmony).
 */
export const SPLASH_SVG_SIZE: number = 148
export const SPLASH_APPROACH_DELAY_MS: number = 700
export const SPLASH_APPROACH_DUR_MS: number = 2200
export const SPLASH_IMPACT_MS: number = SPLASH_APPROACH_DELAY_MS + SPLASH_APPROACH_DUR_MS
export const SPLASH_TOTAL_MS: number = 6800
export const SPLASH_STAGE_GAP: number = 16
export const SPLASH_SCALE_DUR_MS: number = 900

/** Tip origin 50% / 87.5% of petal box */
const TIP_OX: number = 0.5
const TIP_OY: number = 0.875

function stageW(): number {
  return SPLASH_SVG_SIZE * 2
}

function stageH(): number {
  return SPLASH_SVG_SIZE * 0.96
}

function petalW(): number {
  return SPLASH_SVG_SIZE
}

function petalH(): number {
  return stageH()
}

function tipLocalX(): number {
  return petalW() * TIP_OX
}

function tipLocalY(): number {
  return petalH() * TIP_OY
}

export function glowKeyframe(t: number): number[] {
  if (t <= 0.01) {
    return [0, 0]
  }
  if (t <= 0.22) {
    const u = (t - 0.01) / 0.21
    return [lerp(0, 1.4, u), lerp(0, 0.55, u)]
  }
  if (t <= 0.55) {
    const u = (t - 0.22) / 0.33
    return [lerp(1.4, 1.8, u), lerp(0.55, 0.25, u)]
  }
  const u = (t - 0.55) / 0.45
  return [lerp(1.8, 0, u), lerp(0.25, 0, u)]
}

export function scaleKeyframe(t: number): number {
  const ms = Math.min(1, Math.max(0, t)) * SPLASH_SCALE_DUR_MS
  if (ms <= 0) {
    return 1
  }
  if (ms <= 200) {
    return lerp(1, 1.22, ms / 200)
  }
  if (ms <= 480) {
    return lerp(1.22, 0.87, (ms - 200) / 280)
  }
  if (ms <= 700) {
    return lerp(0.87, 1.06, (ms - 480) / 220)
  }
  return lerp(1.06, 1, (ms - 700) / 200)
}

function lerp(a: number, b: number, u: number): number {
  return a + (b - a) * u
}

function rippleScale(progress: number, maxScale: number): number {
  if (progress <= 0) {
    return 0.01
  }
  if (progress < 0.01) {
    return (progress / 0.01) * 0.01
  }
  const t = Math.min(1, Math.max(0, (progress - 0.01) / 0.99))
  return 0.01 + (maxScale - 0.01) * t
}

function rippleAlpha(progress: number): number {
  if (progress <= 0) {
    return 0
  }
  if (progress < 0.01) {
    return progress / 0.01
  }
  return Math.max(0, 1 - ((progress - 0.01) / 0.99))
}

@Component
export struct SplashHeartStage {
  @Prop leftX: number = -SPLASH_SVG_SIZE * 0.95
  @Prop rightX: number = SPLASH_SVG_SIZE * 0.95
  @Prop leftOpacity: number = 0
  @Prop rightOpacity: number = 0
  @Prop leftRotate: number = 0
  @Prop rightRotate: number = 0
  @Prop leftScaleT: number = 0
  @Prop rightScaleT: number = 0
  @Prop ripple1: number = 0
  @Prop ripple2: number = 0
  @Prop ripple3: number = 0
  @Prop glowT: number = 0

  build() {
    Stack({ alignContent: Alignment.TopStart }) {
      if (this.glowT > 0.001) {
        this.GlowBurst()
      }
      if (this.ripple1 > 0.001) {
        this.RippleRing(this.ripple1, 2.8, 'rgba(251,113,133,0.5)')
      }
      if (this.ripple2 > 0.001) {
        this.RippleRing(this.ripple2, 3.8, 'rgba(251,113,133,0.3)')
      }
      if (this.ripple3 > 0.001) {
        this.RippleRing(this.ripple3, 5.0, 'rgba(251,113,133,0.18)')
      }

      // Left petal — TopCenter + translationX
      SplashHeartPetal({
        offsetX: this.leftX,
        angle: this.leftRotate,
        scaleV: scaleKeyframe(this.leftScaleT),
        alpha: this.leftOpacity,
      })

      // Right petal
      SplashHeartPetal({
        offsetX: this.rightX,
        angle: this.rightRotate,
        scaleV: scaleKeyframe(this.rightScaleT),
        alpha: this.rightOpacity,
      })
    }
    .width(stageW())
    .height(stageH())
    .clip(false)
  }

  @Builder
  GlowBurst() {
    Column()
      .width(100)
      .height(100)
      .borderRadius(50)
      .radialGradient({
        center: [50, 50],
        radius: 50,
        colors: [
          ['rgba(253,164,175,0.7)', 0],
          ['rgba(253,164,175,0.15)', 0.55],
          ['rgba(253,164,175,0)', 1],
        ],
      })
      .opacity(glowKeyframe(this.glowT)[1])
      .scale({
        x: Math.max(0.01, glowKeyframe(this.glowT)[0]),
        y: Math.max(0.01, glowKeyframe(this.glowT)[0]),
      })
      .position({
        x: (stageW() - 100) / 2,
        y: stageH() - 100,
      })
  }

  @Builder
  RippleRing(progress: number, maxScale: number, strokeColor: string) {
    Column()
      .width(56)
      .height(56)
      .borderRadius(28)
      .backgroundColor(Color.Transparent)
      .border({ width: 1.5, color: strokeColor })
      .opacity(rippleAlpha(progress))
      .scale({
        x: rippleScale(progress, maxScale),
        y: rippleScale(progress, maxScale),
      })
      .position({
        x: (stageW() - 56) / 2,
        y: stageH() - 56,
      })
  }
}

@Component
struct SplashHeartPetal {
  @Prop offsetX: number = 0
  @Prop angle: number = 0
  @Prop scaleV: number = 1
  @Prop alpha: number = 1

  build() {
    // Anchored at stage TopCenter; slide via translate (Compose translationX).
    Image($r('app.media.heart_petal'))
      .width(petalW())
      .height(petalH())
      .objectFit(ImageFit.Fill)
      .opacity(this.alpha)
      .rotate({
        angle: this.angle,
        centerX: tipLocalX(),
        centerY: tipLocalY(),
      })
      .scale({
        x: this.scaleV,
        y: this.scaleV,
        centerX: tipLocalX(),
        centerY: tipLocalY(),
      })
      .position({
        x: (stageW() - petalW()) / 2,
        y: 0,
      })
      .translate({ x: this.offsetX, y: 0 })
  }
}
