/**
 * Aliyun PNVS one-click login (Harmony).
 * Requires `numberauth_standard` har — see entry/libs/README.md.
 */
import { common } from '@kit.AbilityKit'
import { hilog } from '@kit.PerformanceAnalysisKit'
import {
  PhoneNumberAuthHelper,
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
      const code = `${result['_code'] ?? result['code'] ?? ''}`
      const token = `${result['_token'] ?? result['token'] ?? ''}`
      if (code === '600001') {
        return
      }
      if (code === '600000' && token.length > 0) {
        try {
          this.helperRef?.quitLoginPage()
        } catch (_e) {
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
    } catch (_e) {
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
      const noop = new LoginTokenListener((_ok: boolean, _msg: string) => {
      })
      const helper = PhoneNumberAuthHelper.getInstance(context, noop)
      helper.setAuthSDKInfo(secretInfo)
      helper.setAuthConfig(PnvsAuthHelper.buildUiConfig(privacyUrl, termsUrl))
      try {
        helper.checkEnvAvailable(1)
      } catch (_e) {
        // best-effort
      }
      this.prepared = true
      return true
    } catch (e) {
      hilog.error(DOMAIN, TAG, 'prepare failed %{public}s', String(e))
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
      const listener = new LoginTokenListener(onResult)
      const helper = PhoneNumberAuthHelper.getInstance(this.context, listener)
      listener.bindHelper(helper)
      helper.setAuthSDKInfo(this.secretInfo)
      helper.setAuthConfig(PnvsAuthHelper.buildUiConfig(this.privacyUrl, this.termsUrl))
      helper.getLoginToken(timeoutMs)
    } catch (e) {
      onResult(false, e instanceof Error ? e.message : '本机号登录失败')
    }
  }

  private static buildUiConfig(privacyUrl: string, termsUrl: string): AuthUiConfig {
    const config = new AuthUiConfig()
    config.privacySpanOneText = '《隐私政策》'
    config.privacySpanOneUrl = privacyUrl
    config.privacySpanTwoText = '《用户协议》'
    config.privacySpanTwoUrl = termsUrl
    config.vendorPrivacyPrefix = '《'
    config.vendorPrivacySuffix = '》'
    return config
  }
}
