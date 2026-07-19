/**
 * Aliyun PNVS one-click login (Harmony).
 * Requires `numberauth_standard` har — see entry/libs/README.md.
 */
import { common } from '@kit.AbilityKit'
import { hilog } from '@kit.PerformanceAnalysisKit'
import PhoneNumberAuthHelper, {
  TokenResultListener,
  AuthUiConfig,
} from 'numberauth_standard'

const TAG = 'PnvsAuth'
const DOMAIN = 0x0002

export type PnvsTokenCallback = (ok: boolean, tokenOrMsg: string) => void

class LoginTokenListener implements TokenResultListener {
  private helperRef: PhoneNumberAuthHelper | undefined = undefined
  private callback: PnvsTokenCallback

  constructor(callback: PnvsTokenCallback) {
    this.callback = callback
  }

  bindHelper(helper: PhoneNumberAuthHelper): void {
    this.helperRef = helper
  }

  onSuccess(msg: string): void {
    try {
      const result: Record<string, Object> = JSON.parse(msg) as Record<string, Object>
      const code: string = `${result['_code'] ?? result['code'] ?? ''}`
      const token: string = `${result['_token'] ?? result['token'] ?? ''}`
      if (code === '600001') {
        return
      }
      if (code === '600000' && token.length > 0) {
        try {
          this.helperRef?.quitLoginPage()
        } catch {
          // ignore
        }
        this.callback(true, token)
        return
      }
      if (code === '700000' || code === '700001') {
        this.callback(false, '已取消本机号登录')
        return
      }
      this.callback(false, '本机号登录失败，请使用短信验证码')
    } catch {
      this.callback(false, '本机号登录解析失败')
    }
  }

  onFailure(ret: string): void {
    hilog.error(DOMAIN, TAG, 'failure %{public}s', ret)
    this.callback(false, '本机号登录失败，请使用短信验证码')
  }
}

export class PnvsAuthHelper {
  private static inst: PnvsAuthHelper = new PnvsAuthHelper()
  private prepared: boolean = false
  private context: common.UIAbilityContext | undefined = undefined
  private secretInfo: string = ''
  private privacyUrl: string = ''
  private termsUrl: string = ''

  static getInstance(): PnvsAuthHelper {
    return PnvsAuthHelper.inst
  }

  isPrepared(): boolean {
    return this.prepared
  }

  prepare(
    context: common.UIAbilityContext,
    secretInfo: string,
    privacyUrl: string,
    termsUrl: string,
  ): boolean {
    this.context = context
    this.secretInfo = secretInfo
    this.privacyUrl = privacyUrl
    this.termsUrl = termsUrl
    if (secretInfo.length === 0) {
      this.prepared = false
      return false
    }
    try {
      const noop: LoginTokenListener = new LoginTokenListener((_ok: boolean, _msg: string) => {
      })
      const helper: PhoneNumberAuthHelper = PhoneNumberAuthHelper.getInstance(context, noop)
      helper.setAuthSDKInfo(secretInfo)
      helper.setAuthConfig(PnvsAuthHelper.buildUiConfig(privacyUrl, termsUrl))
      try {
        helper.checkEnvAvailable(1)
      } catch {
        // best-effort
      }
      this.prepared = true
      return true
    } catch {
      hilog.error(DOMAIN, TAG, 'prepare failed')
      this.prepared = false
      return false
    }
  }

  getLoginToken(timeoutMs: number, onResult: PnvsTokenCallback): void {
    if (!this.context || this.secretInfo.length === 0) {
      onResult(false, '本机号登录不可用，请使用短信验证码')
      return
    }
    try {
      const listener: LoginTokenListener = new LoginTokenListener(onResult)
      const helper: PhoneNumberAuthHelper = PhoneNumberAuthHelper.getInstance(this.context, listener)
      listener.bindHelper(helper)
      helper.setAuthSDKInfo(this.secretInfo)
      helper.setAuthConfig(PnvsAuthHelper.buildUiConfig(this.privacyUrl, this.termsUrl))
      helper.getLoginToken(timeoutMs)
    } catch {
      onResult(false, '本机号登录失败')
    }
  }

  /**
   * 对齐官方 NumberAuthDemo：必须显式配置掩码/按钮/协议栏相对布局，
   * 否则默认偏移为 0，会出现按钮贴顶、掩码不可见、复选框缺失等异常。
   * 自定义协议文案不要自带《》，SDK 会用 vendorPrivacyPrefix/Suffix 再包一层。
   */
  private static buildUiConfig(privacyUrl: string, termsUrl: string): AuthUiConfig {
    const config: AuthUiConfig = new AuthUiConfig()
    const brand: string = '#C45C5C'

    // 手机掩码
    config.numberMargin = { top: 200 }
    config.numberFontSize = 26
    config.numberFontColor = Color.Black
    config.numberFontIsBold = true
    config.numberAlignRuleOption = {
      middle: { anchor: '__container__', align: HorizontalAlign.Center },
      top: { anchor: '__container__', align: VerticalAlign.Top },
    }

    // 登录按钮
    config.loginBtnText = '本机号码一键登录'
    config.loginBtnMargin = { left: 30, top: 280, right: 30 }
    config.loginBtnWidth = '80%'
    config.loginBtnHeight = 48
    config.loginBtnFontSize = 17
    config.loginBtnFontColor = Color.White
    config.loginBtnBackGroundColor = brand
    config.loginBtnDisableFontColor = '#999999'
    config.loginBtnDisableBackGroundColor = '#E8C4C4'
    config.loginBtnBorder = 24
    config.loginBtnAlignRuleOption = {
      middle: { anchor: '__container__', align: HorizontalAlign.Center },
      top: { anchor: '__container__', align: VerticalAlign.Top },
    }

    // 协议复选框
    config.privacyCbWidth = 20
    config.privacyCbHeight = 20
    config.privacyCbMargin = { left: 20, bottom: 36 }
    config.privacyCbIsOn = false
    config.privacyCbTipText = '请先勾选同意协议'
    config.privacyCbAlignRuleOption = {
      left: { anchor: '__container__', align: HorizontalAlign.Start },
      bottom: { anchor: '__container__', align: VerticalAlign.Bottom },
    }

    // 协议栏
    config.privacyMargin = { left: 30, right: 16, bottom: 36 }
    config.privacyAlignRuleOption = {
      middle: { anchor: '__container__', align: HorizontalAlign.Center },
      top: { anchor: 'clause_checkBox', align: VerticalAlign.Top },
    }
    config.privacyFontSize = 12
    config.privacyFontColor = '#666666'
    config.privacySpanBeforeText = '我已阅读并同意'
    config.privacySpanEndText = ''
    config.privacySpanOneText = '隐私政策'
    config.privacySpanOneUrl = privacyUrl
    config.privacySpanOneFontSize = 12
    config.privacySpanOneFontColor = brand
    config.privacySpanTwoText = '用户协议'
    config.privacySpanTwoUrl = termsUrl
    config.privacySpanTwoFontSize = 12
    config.privacySpanTwoFontColor = brand
    config.vendorPrivacyPrefix = '《'
    config.vendorPrivacySuffix = '》'
    config.needShowPrivacyAlert = true

    return config
  }
}
