/**
 * Auth — mirrors Android AuthScreen (一键登录为主 + 短信兜底)
 */
import { common } from '@kit.AbilityKit'
import { AppNav } from '../core/nav/AppNav'
import { AppViewModel } from '../core/data/AppViewModel'
import { CoupleMood, Colors, FontSizes } from '../core/design/Theme'
import type { MoodPalette } from '../core/design/Theme'
import { Fonts } from '../core/design/Fonts'
import { LoverWordmark } from '../core/design/LoverBrand'
import { SoftTextField, SoftPrimaryButton } from '../core/design/SoftInputs'
import { ensureBrandFonts } from '../core/design/BrandFonts'
import { PnvsAuthHelper } from '../core/auth/PnvsAuthHelper'

@Entry
@Component
struct AuthPage {
  @State mood: MoodPalette = CoupleMood
  @State @Watch('onPhoneWatch') phone: string = ''
  @State @Watch('onCodeWatch') code: string = ''
  @State loading: boolean = false
  @State countdown: number = 0
  @State errorMsg: string = ''
  @State fontEpoch: number = 0
  @State pnvsReady: boolean = false
  @State showSms: boolean = false
  @State pnvsBusy: boolean = false
  /** launching=拉授权页；loggingIn=已拿 token，正在服务端取号/建会话 */
  @State pnvsPhase: string = ''
  @State pnvsHint: string = ''
  private vm: AppViewModel = AppViewModel.getInstance()
  private timerId: number = -1
  private normalizingPhone: boolean = false
  private normalizingCode: boolean = false

  aboutToAppear(): void {
    ensureBrandFonts()
    this.fontEpoch += 1
    this.preparePnvs()
  }

  aboutToDisappear(): void {
    if (this.timerId !== -1) {
      clearInterval(this.timerId)
    }
  }

  private markPnvsUnavailable(hint: string): void {
    this.pnvsReady = false
    this.pnvsHint = hint
    this.showSms = true
  }

  private async preparePnvs(): Promise<void> {
    try {
      const info = await this.vm.repository.fetchPnvsSdkInfo()
      if (!info.enabled || info.secretInfo.length === 0) {
        this.markPnvsUnavailable('服务端未开启本机号登录，请检查 PNVS 配置')
        return
      }
      const ctx = getContext(this) as common.UIAbilityContext
      const ok = PnvsAuthHelper.getInstance().prepare(
        ctx,
        info.secretInfo,
        info.privacyUrl,
        info.termsUrl,
      )
      this.pnvsReady = ok
      if (ok) {
        this.pnvsHint = ''
      } else {
        this.markPnvsUnavailable('本机号登录暂不可用（需放入 numberauth_standard.har），请使用验证码')
      }
    } catch {
      this.markPnvsUnavailable('无法获取本机号登录配置，请检查网络与后端')
    }
  }

  onPhoneWatch(): void {
    if (this.normalizingPhone) {
      return
    }
    let digits = ''
    for (let i = 0; i < this.phone.length; i++) {
      const c = this.phone.charAt(i)
      if (c >= '0' && c <= '9') {
        digits += c
      }
    }
    if (digits.length > 11) {
      digits = digits.substring(0, 11)
    }
    if (digits !== this.phone) {
      this.normalizingPhone = true
      this.phone = digits
      this.normalizingPhone = false
    }
  }

  onCodeWatch(): void {
    if (this.normalizingCode) {
      return
    }
    let digits = ''
    for (let i = 0; i < this.code.length; i++) {
      const c = this.code.charAt(i)
      if (c >= '0' && c <= '9') {
        digits += c
      }
    }
    if (digits.length > 6) {
      digits = digits.substring(0, 6)
    }
    if (digits !== this.code) {
      this.normalizingCode = true
      this.code = digits
      this.normalizingCode = false
    }
  }

  private canSend(): boolean {
    return this.phone.length === 11 && this.countdown === 0 && !this.loading
  }

  private canLogin(): boolean {
    return this.phone.length === 11 && this.code.length === 6 && !this.loading
  }

  private navigateAfterLogin(): void {
    if (!this.vm.data.profileCompleted) {
      AppNav.replace('pages/OnboardingPage')
    } else {
      AppNav.replace('pages/MainPage')
    }
  }

  oneClickLogin(): void {
    if (this.pnvsBusy || !this.pnvsReady) {
      return
    }
    this.pnvsBusy = true
    this.pnvsPhase = 'launching'
    this.errorMsg = ''
    PnvsAuthHelper.getInstance().getLoginToken(8000, (ok: boolean, tokenOrMsg: string) => {
      if (!ok) {
        if (tokenOrMsg.indexOf('取消') < 0) {
          this.errorMsg = tokenOrMsg
        }
        this.showSms = true
        this.pnvsBusy = false
        this.pnvsPhase = ''
        return
      }
      // 授权页已关闭；此后耗时主要是服务端 GetMobile + 拉会话/首页数据，不是运营商认证页
      this.pnvsPhase = 'loggingIn'
      this.vm.loginWithPnvsToken(tokenOrMsg).then(() => {
        this.navigateAfterLogin()
      }).catch((e: Error) => {
        this.errorMsg = e.message ?? '登录失败'
        this.showSms = true
      }).finally(() => {
        this.pnvsBusy = false
        this.pnvsPhase = ''
      })
    })
  }

  private pnvsButtonLabel(): string {
    if (this.pnvsPhase === 'launching') {
      return '正在拉起…'
    }
    if (this.pnvsPhase === 'loggingIn') {
      return '正在登录…'
    }
    if (this.pnvsReady) {
      return '本机号码一键登录'
    }
    return '本机号码一键登录（暂不可用）'
  }

  async sendSms(): Promise<void> {
    if (!this.canSend()) {
      if (this.phone.length !== 11) {
        this.errorMsg = '请输入正确的手机号'
      }
      return
    }
    this.loading = true
    this.errorMsg = ''
    try {
      const resp = await this.vm.repository.sendSms(this.phone)
      if (resp.devCode) {
        this.code = resp.devCode
      }
      this.countdown = resp.cooldownSeconds || 60
      if (this.timerId !== -1) {
        clearInterval(this.timerId)
      }
      this.timerId = setInterval(() => {
        this.countdown--
        if (this.countdown <= 0) {
          clearInterval(this.timerId)
          this.timerId = -1
        }
      }, 1000)
    } catch (e) {
      this.errorMsg = e instanceof Error ? e.message : '发送验证码失败'
    } finally {
      this.loading = false
    }
  }

  async login(): Promise<void> {
    if (!this.canLogin()) {
      return
    }
    this.loading = true
    this.errorMsg = ''
    try {
      await this.vm.login(this.phone, this.code)
      this.navigateAfterLogin()
    } catch (e) {
      this.errorMsg = e instanceof Error ? e.message : '登录失败'
    } finally {
      this.loading = false
    }
  }

  build() {
    Stack() {
      Column()
        .width('100%')
        .height('100%')
        .linearGradient({
          angle: 180,
          colors: [
            [Colors.blush, 0],
            [Colors.warmBackground, 1],
          ],
        })

      Column()
        .width(210)
        .height(210)
        .borderRadius(105)
        .backgroundColor(Colors.peach)
        .opacity(0.45)
        .position({ x: 230, y: -40 })
        .hitTestBehavior(HitTestMode.None)

      Column() {
        LoverWordmark({
          mood: this.mood,
          showTagline: true,
          logoSize: 72,
          usePhoto: true,
          fontEpoch: this.fontEpoch,
        })

        Blank().height(36)

        Column({ space: 16 }) {
          Text('欢迎回来')
            .fontSize(FontSizes.headlineMedium)
            .fontFamily(Fonts.GARAMOND)
            .fontColor(this.mood.onSurface)
            .fontWeight(FontWeight.Medium)
            .width('100%')

          Text('用手机号进入两个人的专属世界')
            .fontSize(FontSizes.bodyMedium)
            .fontFamily(Fonts.LATO)
            .fontColor(Colors.stone)
            .width('100%')

          SoftPrimaryButton({
            mood: this.mood,
            label: this.pnvsButtonLabel(),
            isEnabled: this.pnvsReady && !this.pnvsBusy,
            onTap: () => this.oneClickLogin(),
          })
          if (this.pnvsHint.length > 0) {
            Text(this.pnvsHint)
              .fontSize(FontSizes.bodySmall)
              .fontFamily(Fonts.LATO)
              .fontColor(Colors.stone)
              .width('100%')
          }
          if (!this.showSms) {
            Text('其他手机号 / 验证码登录')
              .fontSize(FontSizes.bodyMedium)
              .fontFamily(Fonts.LATO)
              .fontColor(Colors.stone)
              .width('100%')
              .textAlign(TextAlign.Center)
              .padding({ top: 4, bottom: 4 })
              .onClick(() => {
                this.showSms = true
              })
          }

          if (this.showSms) {
            SoftTextField({
              mood: this.mood,
              label: '手机号',
              value: $phone,
              placeholder: '请输入手机号',
              maxLength: 11,
              inputType: InputType.PhoneNumber,
            })

            Row({ space: 10 }) {
              Column() {
                SoftTextField({
                  mood: this.mood,
                  label: '验证码',
                  value: $code,
                  placeholder: '',
                  maxLength: 6,
                  inputType: InputType.Number,
                })
              }
              .layoutWeight(1)

              Button(this.countdown > 0 ? `${this.countdown}s` : '获取')
                .height(56)
                .padding({ left: 18, right: 18 })
                .margin({ top: 8 })
                .borderRadius(20)
                .backgroundColor(Colors.blush)
                .fontColor(Colors.rose)
                .fontSize(FontSizes.bodyMedium)
                .fontFamily(Fonts.LATO)
                .enabled(this.canSend())
                .onClick(() => this.sendSms())
            }
            .width('100%')
            .alignItems(VerticalAlign.Bottom)

            SoftPrimaryButton({
              mood: this.mood,
              label: this.loading ? '登录中…' : '登录 / 注册',
              isEnabled: this.canLogin(),
              onTap: () => this.login(),
            })
          }

          if (this.errorMsg.length > 0) {
            Text(this.errorMsg)
              .fontSize(FontSizes.bodySmall)
              .fontFamily(Fonts.LATO)
              .fontColor(Colors.error)
              .width('100%')
          }
        }
        .width('100%')
        .padding(26)
        .borderRadius(32)
        .backgroundColor('rgba(255,255,255,0.94)')
        .shadow({ radius: 8, color: 'rgba(0,0,0,0.06)', offsetY: 2 })
      }
      .width('100%')
      .constraintSize({ maxWidth: 440 })
      .padding(24)
      .alignItems(HorizontalAlign.Center)
      .justifyContent(FlexAlign.Center)
      .height('100%')
    }
    .width('100%')
    .height('100%')
  }
}
