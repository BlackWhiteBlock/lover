/**
 * Splash — two heart petals approach, tips meet, rotate ±15° → logo.
 * Timing mirrors Android SplashScreen.kt (~6.8s).
 */
import { AppNav } from '../core/nav/AppNav'
import { AppViewModel } from '../core/data/AppViewModel'
import { Colors, CoupleMood } from '../core/design/Theme'
import type { MoodPalette } from '../core/design/Theme'
import { LoverAppName } from '../core/design/LoverBrand'
import { Fonts } from '../core/design/Fonts'
import { ensureBrandFonts, resetBrandFontsFlag } from '../core/design/BrandFonts'
import { curves } from '@kit.ArkUI'
import {
  SplashHeartStage,
  SPLASH_SVG_SIZE,
  SPLASH_APPROACH_DELAY_MS,
  SPLASH_APPROACH_DUR_MS,
  SPLASH_IMPACT_MS,
  SPLASH_TOTAL_MS,
  SPLASH_STAGE_GAP,
  SPLASH_SCALE_DUR_MS,
} from './SplashHearts'

@Entry
@Component
struct Index {
  @State mood: MoodPalette = CoupleMood
  @State fontEpoch: number = 0
  @State rootOpacity: number = 1
  @State leftX: number = -SPLASH_SVG_SIZE * 0.95
  @State rightX: number = SPLASH_SVG_SIZE * 0.95
  @State leftOpacity: number = 0
  @State rightOpacity: number = 0
  @State leftRotate: number = 0
  @State rightRotate: number = 0
  @State leftScaleT: number = 0
  @State rightScaleT: number = 0
  @State wordOpacity: number = 0
  @State wordY: number = 14
  @State ripple1: number = 0
  @State ripple2: number = 0
  @State ripple3: number = 0
  @State glowT: number = 0
  @State washPulse: number = 0.94
  private vm: AppViewModel | null = null
  private sessionReady: boolean = false
  private splashDone: boolean = false
  private splashStarted: boolean = false
  private washRunning: boolean = false

  aboutToAppear(): void {
    resetBrandFontsFlag()
    ensureBrandFonts()
    this.fontEpoch += 1
    this.vm = AppViewModel.getInstance()
    this.startSessionRestore()
    // Backup start if onPageShow is delayed
    setTimeout(() => {
      this.startSplashIfNeeded()
    }, 50)
  }

  onPageShow(): void {
    this.startSplashIfNeeded()
  }

  private startSplashIfNeeded(): void {
    if (this.splashStarted) {
      return
    }
    this.splashStarted = true
    this.washRunning = true
    this.loopWash(true)
    this.runSplash()
  }

  aboutToDisappear(): void {
    this.washRunning = false
  }

  async startSessionRestore(): Promise<void> {
    if (!this.vm) {
      return
    }
    await this.vm.restoreSession()
    this.sessionReady = true
    this.tryNavigate()
  }

  private animTo(duration: number, curve: Curve, update: () => void): void {
    try {
      this.getUIContext().animateTo({ duration: duration, curve: curve }, update)
    } catch (_e) {
      update()
    }
  }

  private animBezier(duration: number, x1: number, y1: number, x2: number, y2: number,
    update: () => void): void {
    try {
      this.getUIContext().animateTo({
        duration: duration,
        curve: curves.cubicBezierCurve(x1, y1, x2, y2),
      }, update)
    } catch (_e) {
      update()
    }
  }

  private loopWash(toHigh: boolean): void {
    if (!this.washRunning) {
      return
    }
    const target = toHigh ? 1.1 : 0.94
    this.animBezier(2800, 0.4, 0, 0.2, 1, () => {
      this.washPulse = target
    })
    setTimeout(() => {
      if (this.washRunning) {
        this.loopWash(!toHigh)
      }
    }, 2800)
  }

  runSplash(): void {
    // 1) Fade petals in
    setTimeout(() => {
      this.animTo(400, Curve.EaseOut, () => {
        this.leftOpacity = 1
      })
    }, SPLASH_APPROACH_DELAY_MS - 100)
    setTimeout(() => {
      this.animTo(400, Curve.EaseOut, () => {
        this.rightOpacity = 1
      })
    }, SPLASH_APPROACH_DELAY_MS - 60)

    // 2) Approach from sides → tips meet at center
    setTimeout(() => {
      this.animBezier(SPLASH_APPROACH_DUR_MS, 0.33, 0, 0.2, 1, () => {
        this.leftX = 0
      })
    }, SPLASH_APPROACH_DELAY_MS)
    setTimeout(() => {
      this.animBezier(SPLASH_APPROACH_DUR_MS, 0.33, 0, 0.2, 1, () => {
        this.rightX = 0
      })
    }, SPLASH_APPROACH_DELAY_MS + 40)

    // 3) Impact: rotate ±15° + bounce + glow/ripples
    setTimeout(() => {
      this.animBezier(700, 0.34, 1.56, 0.64, 1, () => {
        this.leftRotate = 15
      })
      this.animBezier(700, 0.34, 1.56, 0.64, 1, () => {
        this.rightRotate = -15
      })
      this.animTo(SPLASH_SCALE_DUR_MS, Curve.Linear, () => {
        this.leftScaleT = 1
      })
      this.animTo(1100, Curve.FastOutSlowIn, () => {
        this.glowT = 1
      })
      this.animBezier(1500, 0.22, 1, 0.36, 1, () => {
        this.ripple1 = 1
      })
    }, SPLASH_IMPACT_MS)

    setTimeout(() => {
      this.animTo(SPLASH_SCALE_DUR_MS, Curve.Linear, () => {
        this.rightScaleT = 1
      })
    }, SPLASH_IMPACT_MS + 40)

    setTimeout(() => {
      this.animBezier(1500, 0.22, 1, 0.36, 1, () => {
        this.ripple2 = 1
      })
    }, SPLASH_IMPACT_MS + 140)
    setTimeout(() => {
      this.animBezier(1500, 0.22, 1, 0.36, 1, () => {
        this.ripple3 = 1
      })
    }, SPLASH_IMPACT_MS + 300)

    // 4) Wordmark
    setTimeout(() => {
      this.animBezier(1000, 0.22, 1, 0.36, 1, () => {
        this.wordOpacity = 1
        this.wordY = 0
      })
    }, SPLASH_IMPACT_MS + 650)

    // 5) Fade out → navigate
    setTimeout(() => {
      this.animTo(Math.floor(SPLASH_TOTAL_MS * 0.18), Curve.FastOutSlowIn, () => {
        this.rootOpacity = 0
      })
    }, Math.floor(SPLASH_TOTAL_MS * 0.82))

    setTimeout(() => {
      this.splashDone = true
      this.tryNavigate()
    }, SPLASH_TOTAL_MS)
  }

  tryNavigate(): void {
    if (!this.splashDone || !this.sessionReady || !this.vm) {
      return
    }
    const state = this.vm.data
    if (!state.accessToken) {
      AppNav.replace('pages/AuthPage')
      return
    }
    if (!state.profileCompleted) {
      AppNav.replace('pages/OnboardingPage')
      return
    }
    AppNav.replace('pages/MainPage')
  }

  build() {
    Stack() {
      Column()
        .width('100%')
        .height('100%')
        .backgroundColor(Colors.warmBackground)

      Column()
        .width(520)
        .height(520)
        .borderRadius(260)
        .radialGradient({
          center: [260, 260],
          radius: 260,
          colors: [
            ['rgba(255,232,236,0.9)', 0],
            ['rgba(255,244,245,0.5)', 0.3],
            ['rgba(255,252,251,0.25)', 0.58],
            ['rgba(255,252,251,0.06)', 0.85],
            ['rgba(255,252,251,0)', 1],
          ],
        })
        .scale({ x: this.washPulse * 0.75, y: this.washPulse * 0.75 })
        .markAnchor({ x: '50%', y: '50%' })
        .position({ x: '50%', y: '50%' })
        .translate({ y: -44 })
        .hitTestBehavior(HitTestMode.None)

      Column({ space: SPLASH_STAGE_GAP }) {
        SplashHeartStage({
          leftX: this.leftX,
          rightX: this.rightX,
          leftOpacity: this.leftOpacity,
          rightOpacity: this.rightOpacity,
          leftRotate: this.leftRotate,
          rightRotate: this.rightRotate,
          leftScaleT: this.leftScaleT,
          rightScaleT: this.rightScaleT,
          ripple1: this.ripple1,
          ripple2: this.ripple2,
          ripple3: this.ripple3,
          glowT: this.glowT,
        })

        Column({ space: 8 }) {
          LoverAppName({ mood: this.mood, fontSize: 60, fontEpoch: this.fontEpoch })
          Text('TWO HEARTS · ONE WORLD')
            .fontSize(9)
            .fontFamily(Fonts.LATO_LIGHT)
            .fontColor('#D6D3D1')
            .letterSpacing(4)
            .fontWeight(FontWeight.Lighter)
        }
        .opacity(this.wordOpacity)
        .translate({ y: this.wordY })
        .alignItems(HorizontalAlign.Center)
      }
      .justifyContent(FlexAlign.Center)
      .alignItems(HorizontalAlign.Center)
      .width('100%')
      .height('100%')
      .clip(false)
    }
    .width('100%')
    .height('100%')
    .opacity(this.rootOpacity)
    .clip(false)
  }
}
