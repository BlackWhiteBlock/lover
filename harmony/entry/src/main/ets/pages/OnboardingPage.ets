/**
 * Onboarding — aligned with Android OnboardingScreen.
 */
import { AppNav } from '../core/nav/AppNav'
import { AppViewModel } from '../core/data/AppViewModel'
import { SoleilMood, Colors, FontSizes, Radii } from '../core/design/Theme'
import type { MoodPalette } from '../core/design/Theme'
import { Fonts } from '../core/design/Fonts'
import { SoftTextField, SoftChip, SoftPrimaryButton, LoverDateField } from '../core/design/SoftInputs'
import { DateUtils } from '../core/util/DateUtils'

@Entry
@Component
struct OnboardingPage {
  @State mood: MoodPalette = SoleilMood
  @State nickname: string = ''
  @State spaceName: string = '我们的小宇宙'
  @State gender: string = ''
  @State birthday: string = ''
  @State loading: boolean = false
  @State errorMsg: string = ''
  private vm: AppViewModel = AppViewModel.getInstance()

  aboutToAppear(): void {
    if (!this.birthday) {
      this.birthday = '2000-01-01'
    }
  }

  async submit(): Promise<void> {
    this.errorMsg = ''
    if (!this.nickname.trim()) {
      this.errorMsg = '请填写昵称'
      return
    }
    if (!this.gender) {
      this.errorMsg = '请选择性别'
      return
    }
    if (!this.birthday) {
      this.errorMsg = '请选择生日'
      return
    }
    if (!this.spaceName.trim()) {
      this.errorMsg = '请填写空间名称'
      return
    }
    this.loading = true
    try {
      await this.vm.completeOnboarding(this.nickname, this.gender, this.birthday, this.spaceName)
      AppNav.replace('pages/MainPage')
    } catch (e) {
      this.errorMsg = e instanceof Error ? e.message : '创建失败'
    } finally {
      this.loading = false
    }
  }

  build() {
    Column() {
      Column()
        .width('100%')
        .height(8)
        .linearGradient({
          angle: 90,
          colors: [[this.mood.blush, 0], [this.mood.background, 1]],
        })

      Scroll() {
        Column({ space: 18 }) {
          Text('创建我们的空间')
            .fontSize(FontSizes.headlineMedium)
            .fontFamily(Fonts.GARAMOND)
            .fontColor(this.mood.onSurface)
            .margin({ top: 40 })
            .alignSelf(ItemAlign.Start)

          Text('开始你们的情侣时光之旅')
            .fontSize(FontSizes.bodyMedium)
            .fontFamily(Fonts.LATO)
            .fontColor(this.mood.stone)
            .alignSelf(ItemAlign.Start)

          SoftTextField({
            mood: this.mood,
            label: '你的昵称',
            value: $nickname,
            placeholder: '你希望被怎么称呼？',
            maxLength: 40,
          })

          SoftTextField({
            mood: this.mood,
            label: '空间名称',
            value: $spaceName,
            placeholder: '给你们的空间起个名字',
            maxLength: 40,
          })

          Column({ space: 8 }) {
            Text('性别')
              .fontSize(FontSizes.bodySmall)
              .fontFamily(Fonts.LATO)
              .fontColor(this.mood.accent)
              .alignSelf(ItemAlign.Start)
            Row({ space: 10 }) {
              SoftChip({
                mood: this.mood, label: '男生', selected: this.gender === 'male',
                onSelect: () => { this.gender = 'male' },
              })
              SoftChip({
                mood: this.mood, label: '女生', selected: this.gender === 'female',
                onSelect: () => { this.gender = 'female' },
              })
              SoftChip({
                mood: this.mood, label: '其他', selected: this.gender === 'other',
                onSelect: () => { this.gender = 'other' },
              })
            }
            .width('100%')
          }
          .width('100%')
          .alignItems(HorizontalAlign.Start)

          LoverDateField({
            mood: this.mood,
            label: '生日',
            value: $birthday,
            maxDate: DateUtils.formatDate(new Date()),
          })

          if (this.errorMsg) {
            Text(this.errorMsg)
              .fontSize(FontSizes.bodySmall)
              .fontColor(Colors.error)
              .alignSelf(ItemAlign.Start)
          }

          SoftPrimaryButton({
            mood: this.mood,
            label: this.loading ? '创建中…' : '完成并进入',
            isEnabled: !this.loading,
            onTap: () => this.submit(),
          })
          Blank().height(40)
        }
        .width('100%')
        .padding({ left: 24, right: 24, bottom: 24 })
        .constraintSize({ maxWidth: 448 })
      }
      .layoutWeight(1)
      .align(Alignment.Top)
      .scrollBar(BarState.Off)
    }
    .width('100%')
    .height('100%')
    .linearGradient({
      angle: 180,
      colors: [[this.mood.blush, 0], [this.mood.background, 0.35], [this.mood.background, 1]],
    })
  }
}
