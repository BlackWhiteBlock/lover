/**
 * Phone bind page — mirrors Android PhoneBindSheet.
 */
import { AppNav } from '../core/nav/AppNav'
import { AppViewModel } from '../core/data/AppViewModel'
import { CoupleMood, SoleilMood, Colors, FontSizes, Radii } from '../core/design/Theme'
import type { MoodPalette } from '../core/design/Theme'
import { Fonts } from '../core/design/Fonts'
import { SoftTextField, SoftPrimaryButton } from '../core/design/SoftInputs'
import type { UserLookupResponse } from '../core/model/Models'
import { PersonAvatar } from './ProfileSections'

@Entry
@Component
struct CouplePage {
  @State mood: MoodPalette = SoleilMood
  @State @Watch('onPhoneWatch') phone: string = ''
  @State lookingUp: boolean = false
  @State lookupReady: boolean = false
  @State found: boolean = false
  @State isSelf: boolean = false
  @State lookupNickname: string = ''
  @State lookupAvatarUrl: string = ''
  @State lookupFailed: boolean = false
  @State loading: boolean = false
  @State errorMsg: string = ''
  @State uiEpoch: number = 0
  private vm: AppViewModel = AppViewModel.getInstance()
  private lookupToken: number = 0
  private normalizingPhone: boolean = false

  aboutToAppear(): void {
    this.mood = this.vm.data.linked ? CoupleMood : SoleilMood
    this.uiEpoch = this.vm.uiEpoch
    if (this.vm.data.linked) {
      AppNav.back()
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
    this.errorMsg = ''
    if (digits.length !== 11) {
      this.resetLookup()
      return
    }
    this.scheduleLookup(digits)
  }

  private resetLookup(): void {
    this.lookingUp = false
    this.lookupReady = false
    this.found = false
    this.isSelf = false
    this.lookupNickname = ''
    this.lookupAvatarUrl = ''
    this.lookupFailed = false
  }

  private scheduleLookup(digits: string): void {
    this.lookupToken += 1
    const token = this.lookupToken
    this.lookingUp = true
    this.lookupReady = false
    this.lookupFailed = false
    setTimeout(() => {
      this.runLookup(digits, token)
    }, 350)
  }

  private async runLookup(digits: string, token: number): Promise<void> {
    if (token !== this.lookupToken || this.phone !== digits) {
      return
    }
    try {
      const r: UserLookupResponse = await this.vm.lookupUser(digits)
      if (token !== this.lookupToken || this.phone !== digits) {
        return
      }
      this.found = r.found
      this.isSelf = r.self
      this.lookupNickname = r.nickname ?? ''
      this.lookupAvatarUrl = r.avatarUrl ?? ''
      this.lookupFailed = false
      this.lookupReady = true
    } catch (_e) {
      if (token !== this.lookupToken) {
        return
      }
      this.lookupFailed = true
      this.lookupReady = true
      this.found = false
    } finally {
      if (token === this.lookupToken) {
        this.lookingUp = false
      }
    }
  }

  private canSend(): boolean {
    return this.phone.length === 11 && this.lookupReady && this.found && !this.isSelf && !this.lookingUp && !this.loading
  }

  build() {
    Column() {
      Row() {
        Text('‹')
          .fontSize(28)
          .fontColor(this.mood.onSurface)
          .width(40)
          .onClick(() => AppNav.back())
        Text('绑定另一半')
          .fontSize(FontSizes.titleLarge)
          .fontFamily(Fonts.GARAMOND)
          .fontColor(this.mood.onSurface)
          .layoutWeight(1)
      }
      .width('100%')
      .padding({ left: 12, right: 16, top: 12, bottom: 8 })

      Scroll() {
        Column({ space: 14 }) {
          Text('绑定另一半')
            .fontSize(FontSizes.headlineMedium)
            .fontFamily(Fonts.GARAMOND)
            .fontColor(this.mood.onSurface)
            .width('100%')
          Text('输入对方已注册的手机号，等待对方同意后完成绑定')
            .fontSize(FontSizes.bodyMedium)
            .fontFamily(Fonts.LATO)
            .fontColor(this.mood.stone)
            .width('100%')

          SoftTextField({
            mood: this.mood,
            label: '对方手机号',
            value: $phone,
            placeholder: '11 位手机号',
            maxLength: 11,
            inputType: InputType.PhoneNumber,
          })

          if (this.phone.length === 11) {
            if (this.lookingUp) {
              Text('正在查找账号…')
                .fontSize(FontSizes.bodySmall)
                .fontFamily(Fonts.LATO)
                .fontColor(this.mood.stone)
                .width('100%')
            } else if (this.lookupFailed) {
              Text('查找失败，请稍后重试')
                .fontSize(FontSizes.bodySmall)
                .fontFamily(Fonts.LATO)
                .fontColor(this.mood.stone)
                .width('100%')
            } else if (this.lookupReady && !this.found) {
              Text('没有对应账号')
                .fontSize(FontSizes.bodyMedium)
                .fontFamily(Fonts.LATO)
                .fontColor(Colors.error)
                .width('100%')
            } else if (this.lookupReady && this.isSelf) {
              Text('不能绑定自己的手机号')
                .fontSize(FontSizes.bodyMedium)
                .fontFamily(Fonts.LATO)
                .fontColor(Colors.error)
                .width('100%')
            } else if (this.lookupReady && this.found) {
              Row({ space: 12 }) {
                PersonAvatar({
                  mood: this.mood,
                  nickname: this.lookupNickname.length > 0 ? this.lookupNickname : '对方',
                  avatarUrl: this.lookupAvatarUrl,
                  sizeVp: 48,
                })
                Column({ space: 2 }) {
                  Text(this.lookupNickname.length > 0 ? this.lookupNickname : 'Lover 用户')
                    .fontSize(FontSizes.bodyLarge)
                    .fontFamily(Fonts.LATO)
                    .fontColor(this.mood.accent)
                    .fontWeight(FontWeight.Medium)
                  Text('已找到对方账号')
                    .fontSize(FontSizes.bodySmall)
                    .fontFamily(Fonts.LATO)
                    .fontColor(this.mood.stone)
                }
                .layoutWeight(1)
                .alignItems(HorizontalAlign.Start)
              }
              .width('100%')
              .padding(12)
              .borderRadius(20)
              .backgroundColor(this.mood.softSurface)
            }
          }

          SoftPrimaryButton({
            mood: this.mood,
            label: this.loading ? '发送中…' : '发送绑定请求',
            isEnabled: this.canSend(),
            onTap: () => this.sendInvite(),
          })

          if (this.vm.data.pendingOutgoingBind) {
            Column({ space: 8 }) {
              Text(`已向 ${this.vm.data.pendingOutgoingBind.targetNickname || '对方'} 发送绑定邀请`)
                .fontSize(FontSizes.bodyMedium)
                .fontFamily(Fonts.LATO)
                .fontColor(this.mood.stone)
                .width('100%')
              Text('状态：等待对方确认')
                .fontSize(FontSizes.bodySmall)
                .fontFamily(Fonts.LATO)
                .fontColor(this.mood.soft)
                .width('100%')
              Text('取消请求')
                .fontSize(FontSizes.bodyMedium)
                .fontFamily(Fonts.LATO)
                .fontColor(this.mood.accent)
                .onClick(async () => {
                  const id = this.vm.data.pendingOutgoingBind?.id
                  if (id) {
                    await this.vm.cancelBind(id)
                    this.uiEpoch = this.vm.uiEpoch
                    AppNav.back()
                  }
                })
            }
            .width('100%')
            .padding(14)
            .borderRadius(Radii.small)
            .backgroundColor(this.mood.blush)
            .alignItems(HorizontalAlign.Start)
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
        .padding({ left: 24, right: 24, bottom: 40, top: 8 })
      }
      .layoutWeight(1)
      .scrollBar(BarState.Off)
    }
    .width('100%')
    .height('100%')
    .backgroundColor(this.mood.background)
  }

  async sendInvite(): Promise<void> {
    if (!this.canSend()) {
      return
    }
    this.loading = true
    this.errorMsg = ''
    try {
      await this.vm.requestBind(this.phone)
      this.uiEpoch = this.vm.uiEpoch
      AppNav.back()
    } catch (e) {
      this.errorMsg = e instanceof Error ? e.message : '发送失败'
    } finally {
      this.loading = false
    }
  }
}
