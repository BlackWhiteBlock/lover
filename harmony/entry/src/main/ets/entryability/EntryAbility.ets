/**
 * Entry Ability — application entry point + deep link invite capture.
 */
import { AbilityConstant, UIAbility, Want } from '@kit.AbilityKit'
import { hilog } from '@kit.PerformanceAnalysisKit'
import { window } from '@kit.ArkUI'
import { AppViewModel } from '../core/data/AppViewModel'
import { InviteLinks } from '../core/util/InviteLinks'
import { ensureBrandFonts, resetBrandFontsFlag } from '../core/design/BrandFonts'

const TAG = 'EntryAbility'
const DOMAIN = 0x0001

export default class EntryAbility extends UIAbility {
  onCreate(want: Want, _launchParam: AbilityConstant.LaunchParam): void {
    hilog.info(DOMAIN, TAG, 'onCreate')
    resetBrandFontsFlag()
    ensureBrandFonts()
    AppViewModel.init(this.context)
    this.captureInvite(want)
  }

  onNewWant(want: Want, _launchParam: AbilityConstant.LaunchParam): void {
    this.captureInvite(want)
  }

  private captureInvite(want: Want): void {
    try {
      const uri = want.uri ?? ''
      let code = InviteLinks.parseCode(uri)
      if (!code && want.parameters) {
        const raw = want.parameters as Record<string, Object>
        const maybe = (raw['code'] as string) ?? (raw['inviteCode'] as string) ?? ''
        if (maybe) code = maybe.trim().toUpperCase()
      }
      if (code) {
        AppViewModel.getInstance().offerInviteCode(code)
        hilog.info(DOMAIN, TAG, 'invite code captured')
      }
    } catch (_e) {
      // ignore
    }
  }

  async onWindowStageCreate(windowStage: window.WindowStage): Promise<void> {
    hilog.info(DOMAIN, TAG, 'onWindowStageCreate')
    // Match system startWindow + Index splash (#FFFCFB) so cold start feels continuous.
    try {
      const win = windowStage.getMainWindowSync()
      win.setWindowBackgroundColor('#FFFCFB')
    } catch (_e) {
      // ignore — older APIs may not support
    }
    // Register fonts before first frame so "lover." uses Pinyon Script.
    ensureBrandFonts()
    windowStage.loadContent('pages/Index', (err) => {
      if (err.code) {
        hilog.error(DOMAIN, TAG, 'Failed to load content: %{public}s', JSON.stringify(err))
        return
      }
      ensureBrandFonts()
      hilog.info(DOMAIN, TAG, 'Content loaded successfully')
    })
  }

  onWindowStageDestroy(): void {
    hilog.info(DOMAIN, TAG, 'onWindowStageDestroy')
  }

  onForeground(): void {
    hilog.info(DOMAIN, TAG, 'onForeground')
    try {
      const vm = AppViewModel.getInstance()
      vm.refreshSessionQuietly()
    } catch (_e) {
      // not initialized
    }
  }

  onBackground(): void {
    hilog.info(DOMAIN, TAG, 'onBackground')
  }
}
