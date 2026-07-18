/**
 * Main shell — 5-tab capsule nav aligned with Android MainScreen.
 */
import { AppNav } from '../core/nav/AppNav'
import { AppViewModel, MainTab, PendingMediaUpload } from '../core/data/AppViewModel'
import { CoupleMood, SoleilMood, Colors, FontSizes, Radii } from '../core/design/Theme'
import type { MoodPalette } from '../core/design/Theme'
import { Fonts } from '../core/design/Fonts'
import { PageHeader } from '../core/design/PageHeader'
import { LoverNoticeBar } from '../core/design/LoverNotice'
import {
  MediaItem, Anniversary, Letter, AnniversaryType, LetterType, MediaType,
  CoupleMember, IncomingBindRequest, OutgoingBindRequest, UnbindingRequest,
} from '../core/model/Models'
import { DateUtils } from '../core/util/DateUtils'
import { SoftTextField } from '../core/design/SoftInputs'
import { ensureBrandFonts } from '../core/design/BrandFonts'
import {
  HomeTopBar, LovingJourneyCard, DailyQuoteCard, RecentGlimpseSection,
} from './HomeSections'
import { TimelineGalleryContent } from './TimelineGallery'
import {
  EmptyCoupleCard, IncomingBindCard, BoundCoupleCard, ProfileSettingRow, bindInviterLabel,
} from './ProfileSections'

class NavItem {
  tab: MainTab = MainTab.HOME
  label: string = ''
  iconRes: Resource | null = null
}

@Entry
@Component
struct MainPage {
  @State currentIndex: number = 0
  @State mood: MoodPalette = CoupleMood
  @State uiEpoch: number = 0
  @State fontEpoch: number = 0
  @State errorMessage: string | null = null
  @State successMessage: string | null = null
  @State noticeMessage: string | null = null
  @State promptTogetherDate: boolean = false
  @State togetherDraft: string = ''
  private vm: AppViewModel = AppViewModel.getInstance()
  private syncTimer: number = -1

  aboutToAppear(): void {
    ensureBrandFonts()
    this.fontEpoch += 1
    this.syncFromVm()
    if ((this.vm.selectedTab as number) >= 0 && (this.vm.selectedTab as number) <= 4) {
      this.currentIndex = this.vm.selectedTab as number
    }
    this.syncTimer = setInterval(() => {
      if (this.uiEpoch !== this.vm.uiEpoch ||
        this.errorMessage !== this.vm.errorMessage ||
        this.successMessage !== this.vm.successMessage ||
        this.noticeMessage !== this.vm.noticeMessage ||
        this.promptTogetherDate !== this.vm.promptTogetherDate) {
        this.syncFromVm()
      }
    }, 400)
  }

  aboutToDisappear(): void {
    if (this.syncTimer !== -1) {
      clearInterval(this.syncTimer)
      this.syncTimer = -1
    }
  }

  syncFromVm(): void {
    this.uiEpoch = this.vm.uiEpoch
    this.mood = this.vm.data.linked ? CoupleMood : SoleilMood
    this.errorMessage = this.vm.errorMessage
    this.successMessage = this.vm.successMessage
    this.noticeMessage = this.vm.noticeMessage
    this.promptTogetherDate = this.vm.promptTogetherDate
    if (!this.togetherDraft) {
      const d = new Date()
      d.setFullYear(d.getFullYear() - 1)
      this.togetherDraft = DateUtils.formatDate(d)
    }
  }

  navItems(): NavItem[] {
    const linked = this.vm.data.linked
    const items: NavItem[] = []
    const a = new NavItem()
    a.tab = MainTab.HOME
    a.label = '空间'
    a.iconRes = $r('app.media.ic_nav_favorite')
    items.push(a)
    const b = new NavItem()
    b.tab = MainTab.TIMELINE
    b.label = '时光'
    b.iconRes = $r('app.media.ic_nav_photo_library')
    items.push(b)
    const c = new NavItem()
    c.tab = MainTab.ANNIVERSARY
    c.label = '纪念'
    c.iconRes = linked ? $r('app.media.ic_nav_anniversary') : $r('app.media.ic_nav_event')
    items.push(c)
    const d = new NavItem()
    d.tab = MainTab.LETTERS
    d.label = '信封'
    d.iconRes = $r('app.media.ic_nav_mail')
    items.push(d)
    const e = new NavItem()
    e.tab = MainTab.PROFILE
    e.label = linked ? '我们' : '我'
    e.iconRes = linked ? $r('app.media.ic_nav_us') : $r('app.media.ic_nav_person')
    items.push(e)
    return items
  }

  showFab(): boolean {
    return this.currentIndex === (MainTab.TIMELINE as number) ||
      this.currentIndex === (MainTab.ANNIVERSARY as number) ||
      this.currentIndex === (MainTab.LETTERS as number)
  }

  onFab(): void {
    // Align Android: FAB opens compose overlay immediately; picker is inside the page.
    if (this.currentIndex === (MainTab.TIMELINE as number)) {
      this.vm.clearComposePending()
      AppNav.push('pages/MediaComposePage')
    } else if (this.currentIndex === (MainTab.ANNIVERSARY as number)) {
      AppNav.pushAnniversaryCompose()
    } else if (this.currentIndex === (MainTab.LETTERS as number)) {
      AppNav.push('pages/LetterComposePage')
    }
  }

  build() {
    Stack() {
      Column() {
        Column() {
          if (this.currentIndex === (MainTab.HOME as number)) {
            HomeTabContent({
              mood: this.mood,
              uiEpoch: this.uiEpoch,
              fontEpoch: this.fontEpoch,
              onGoTimeline: () => this.selectTab(MainTab.TIMELINE),
              onGoProfile: () => this.selectTab(MainTab.PROFILE),
            })
          } else if (this.currentIndex === (MainTab.TIMELINE as number)) {
            TimelineTabContent({ mood: this.mood, uiEpoch: this.uiEpoch })
          } else if (this.currentIndex === (MainTab.ANNIVERSARY as number)) {
            AnniversaryTabContent({ mood: this.mood, uiEpoch: this.uiEpoch })
          } else if (this.currentIndex === (MainTab.LETTERS as number)) {
            LettersTabContent({ mood: this.mood, uiEpoch: this.uiEpoch })
          } else {
            ProfileTabContent({ mood: this.mood, uiEpoch: this.uiEpoch })
          }
        }
        .width('100%')
        .layoutWeight(1)

        // Capsule navigation
        Row() {
          ForEach(this.navItems(), (item: NavItem) => {
            Column({ space: 2 }) {
              Image(item.iconRes as Resource)
                .width(22)
                .height(22)
                .objectFit(ImageFit.Contain)
                .fillColor(this.currentIndex === (item.tab as number) ? this.mood.soft : this.mood.stone)
                .scale({
                  x: this.currentIndex === (item.tab as number) ? 1.06 : 1,
                  y: this.currentIndex === (item.tab as number) ? 1.06 : 1,
                })
              if (this.currentIndex === (item.tab as number)) {
                Row()
                  .width(12)
                  .height(3)
                  .borderRadius(2)
                  .backgroundColor(this.mood.soft)
              } else {
                Row().width(12).height(3)
              }
              Text(item.label)
                .fontSize(FontSizes.labelSmall)
                .fontFamily(Fonts.LATO)
                .fontColor(this.currentIndex === (item.tab as number) ? this.mood.soft : this.mood.stone)
            }
            .layoutWeight(1)
            .height(56)
            .justifyContent(FlexAlign.Center)
            .alignItems(HorizontalAlign.Center)
            .onClick(() => this.selectTab(item.tab))
          }, (item: NavItem) => `${item.label}_${this.uiEpoch}`)
        }
        .width('100%')
        .padding({ left: 8, right: 8, top: 6, bottom: 6 })
        .margin({ left: 14, right: 14, top: 6, bottom: 12 })
        .backgroundColor(this.mood.solo ? this.mood.softSurface : Colors.white)
        .opacity(0.98)
        .borderRadius(36)
        .border({ width: 1, color: this.mood.softOutline })
        .shadow({ radius: 12, color: 'rgba(0,0,0,0.10)', offsetY: 4 })
      }
      .width('100%')
      .height('100%')
      .backgroundColor(this.mood.background)

      // Notices
      Column({ space: 8 }) {
        if (this.noticeMessage) {
          LoverNoticeBar({
            mood: this.mood,
            message: this.noticeMessage,
            kind: 'info',
            onDismiss: () => {
              this.vm.clearNotice()
              this.syncFromVm()
            },
          })
        }
        if (this.successMessage) {
          LoverNoticeBar({
            mood: this.mood,
            message: this.successMessage,
            kind: 'success',
            onDismiss: () => {
              this.vm.clearMessages()
              this.syncFromVm()
            },
          })
        }
        if (this.errorMessage) {
          LoverNoticeBar({
            mood: this.mood,
            message: this.errorMessage,
            kind: 'error',
            onDismiss: () => {
              this.vm.clearMessages()
              this.syncFromVm()
            },
          })
        }
      }
      .width('88%')
      .position({ x: '6%', y: 48 })

      if (this.showFab()) {
        Button('+')
          .width(56)
          .height(56)
          .borderRadius(22)
          .backgroundColor(this.mood.soft)
          .fontColor(Colors.white)
          .fontSize(28)
          .shadow({ radius: 10, color: 'rgba(0,0,0,0.18)', offsetY: 4 })
          .position({ x: '80%', y: '72%' })
          .onClick(() => this.onFab())
      }

      if (this.promptTogetherDate) {
        Column() {
          Column({ space: 14 }) {
            Text('设置在一起的日子？')
              .fontSize(FontSizes.titleLarge)
              .fontFamily(Fonts.GARAMOND)
              .fontColor(this.mood.onSurface)
            TextInput({ placeholder: 'YYYY-MM-DD', text: this.togetherDraft })
              .height(52)
              .borderRadius(Radii.small)
              .backgroundColor(this.mood.softSurface)
              .onChange((v: string) => { this.togetherDraft = v })
            Row({ space: 12 }) {
              Button('跳过')
                .layoutWeight(1)
                .height(48)
                .backgroundColor(this.mood.softSurface)
                .fontColor(this.mood.stone)
                .borderRadius(Radii.small)
                .onClick(() => {
                  this.vm.dismissTogetherDatePrompt()
                  this.syncFromVm()
                })
              Button('保存')
                .layoutWeight(1)
                .height(48)
                .backgroundColor(this.mood.soft)
                .fontColor(Colors.white)
                .borderRadius(Radii.small)
                .onClick(async () => {
                  await this.vm.saveTogetherDate(this.togetherDraft)
                  this.syncFromVm()
                })
            }
            .width('100%')
          }
          .width('86%')
          .padding(22)
          .backgroundColor(Colors.white)
          .borderRadius(Radii.medium)
        }
        .width('100%')
        .height('100%')
        .justifyContent(FlexAlign.Center)
        .backgroundColor('rgba(0,0,0,0.35)')
      }
    }
    .width('100%')
    .height('100%')
  }

  selectTab(tab: MainTab): void {
    this.currentIndex = tab as number
    this.vm.selectTab(tab)
    this.syncFromVm()
  }
}

// ─── 空间 HOME ────────────────────────────────────────

@Component
struct HomeTabContent {
  @Prop mood: MoodPalette
  @Prop @Watch('onEpoch') uiEpoch: number
  @Prop fontEpoch: number = 0
  onGoTimeline: () => void = () => {}
  onGoProfile: () => void = () => {}
  @State mediaPreview: MediaItem[] = []
  private vm: AppViewModel = AppViewModel.getInstance()

  aboutToAppear(): void { this.reload() }
  onEpoch(): void { this.reload() }

  reload(): void {
    this.mediaPreview = this.vm.data.media.slice(0, 8)
  }

  build() {
    Scroll() {
      Column({ space: 18 }) {
        HomeTopBar({ mood: this.mood, fontEpoch: this.fontEpoch })
        LovingJourneyCard({
          mood: this.mood,
          linked: this.vm.data.linked,
          lovingDays: this.vm.data.lovingDays,
          needsTogetherDate: this.vm.data.needsTogetherDate ||
            (this.vm.data.linked && !this.vm.data.couple?.togetherDate),
          onSetTogetherDate: () => this.onGoProfile(),
        })
        DailyQuoteCard({ mood: this.mood, quote: this.vm.data.dailyQuote })
        RecentGlimpseSection({
          mood: this.mood,
          items: this.mediaPreview,
          onViewAll: () => this.onGoTimeline(),
        })
        Blank().height(24)
      }
      .width('100%')
      .padding({ left: 20, right: 20, bottom: 20 })
    }
    .width('100%')
    .height('100%')
    .scrollBar(BarState.Off)
    .align(Alignment.Top)
  }
}

// ─── 时光 TIMELINE ────────────────────────────────────

@Component
struct TimelineTabContent {
  @Prop mood: MoodPalette
  @Prop @Watch('onEpoch') uiEpoch: number
  @State mediaItems: MediaItem[] = []
  @State pendingUploads: PendingMediaUpload[] = []
  private vm: AppViewModel = AppViewModel.getInstance()

  aboutToAppear(): void { this.reload() }
  onEpoch(): void { this.reload() }

  reload(): void {
    this.mediaItems = [...this.vm.data.media]
    this.pendingUploads = [...this.vm.pendingMediaUploads]
  }

  build() {
    Column() {
      PageHeader({
        mood: this.mood,
        title: this.mood.solo ? '时光' : '相爱时光',
        subtitle: this.mood.solo ? 'Quiet Moments' : 'Visual Memories',
      })
      if (this.mediaItems.length === 0 && this.pendingUploads.length === 0) {
        Column({ space: 12 }) {
          Image($r('app.media.ic_nav_photo_library'))
            .width(58)
            .height(58)
            .fillColor(this.mood.soft)
            .opacity(0.55)
          Text(this.mood.solo ? '选择照片或视频，记录这一刻' : '选择照片或视频，记录共同的故事')
            .fontSize(FontSizes.bodyMedium)
            .fontFamily(Fonts.LATO)
            .fontColor(this.mood.stone)
            .textAlign(TextAlign.Center)
        }
        .width('100%')
        .layoutWeight(1)
        .padding(38)
        .justifyContent(FlexAlign.Center)
        .alignItems(HorizontalAlign.Center)
      } else {
        TimelineGalleryContent({
          mood: this.mood,
          media: this.mediaItems,
          pendingUploads: this.pendingUploads,
          onLoadMore: () => this.vm.loadMoreMedia(),
        })
      }
    }
    .width('100%')
    .height('100%')
  }
}

// ─── 纪念 ─────────────────────────────────────────────

@Component
struct AnniversaryTabContent {
  @Prop mood: MoodPalette
  @Prop @Watch('onEpoch') uiEpoch: number
  @State anniversaries: Anniversary[] = []
  private vm: AppViewModel = AppViewModel.getInstance()

  aboutToAppear(): void { this.reload() }
  onEpoch(): void { this.reload() }
  reload(): void { this.anniversaries = [...this.vm.data.anniversaries] }

  build() {
    Column() {
      PageHeader({
        mood: this.mood,
        title: this.mood.solo ? '纪念日' : '爱的纪念日',
        subtitle: this.mood.solo ? 'Gentle Dates' : 'Eternal Dates',
      })
      List({ space: 16 }) {
        ForEach(this.anniversaries, (item: Anniversary) => {
          ListItem() {
            Column() {
              Text(item.type === AnniversaryType.YEARLY ? '年度纪念' : '里程碑')
                .fontSize(FontSizes.labelSmall)
                .fontFamily(Fonts.LATO_LIGHT)
                .fontColor(Colors.white)
                .backgroundColor(this.mood.accent)
                .borderRadius(Radii.extraSmall)
                .padding({ left: 10, right: 10, top: 4, bottom: 4 })
                .alignSelf(ItemAlign.Start)
              Text(item.title)
                .fontSize(FontSizes.titleLarge)
                .fontFamily(Fonts.GARAMOND)
                .fontColor(Colors.white)
                .margin({ top: 10 })
              Text(item.date)
                .fontSize(FontSizes.bodySmall)
                .fontFamily(Fonts.LATO)
                .fontColor(Colors.white)
                .opacity(0.9)
                .margin({ top: 4 })
              if (item.countdown) {
                Text(item.countdown.reached ? '已到达' : `还有 ${item.countdown.days} 天`)
                  .fontSize(FontSizes.bodyMedium)
                  .fontFamily(Fonts.LATO)
                  .fontColor(Colors.white)
                  .margin({ top: 10 })
              }
            }
            .width('100%')
            .height(168)
            .padding(20)
            .justifyContent(FlexAlign.End)
            .alignItems(HorizontalAlign.Start)
            .borderRadius(Radii.medium)
            .linearGradient({
              angle: 135,
              colors: [[this.mood.accent, 0], [this.mood.soft, 1]],
            })
            .onClick(() => AppNav.pushAnniversaryCompose(item.id))
          }
        }, (item: Anniversary) => `${item.id}_${this.uiEpoch}`)

        ListItem() {
          Text('+  添加新的纪念日')
            .fontSize(FontSizes.bodyMedium)
            .fontFamily(Fonts.LATO)
            .fontColor(this.mood.accent)
            .width('100%')
            .height(56)
            .textAlign(TextAlign.Center)
            .borderRadius(Radii.small)
            .border({ width: 1, color: this.mood.softOutline, style: BorderStyle.Dashed })
            .backgroundColor(this.mood.softSurface)
            .onClick(() => AppNav.pushAnniversaryCompose())
        }
      }
      .width('100%')
      .padding({ left: 16, right: 16, bottom: 24 })
      .layoutWeight(1)
      .scrollBar(BarState.Off)
    }
    .width('100%')
    .height('100%')
  }
}

// ─── 信封 ─────────────────────────────────────────────

@Component
struct LettersTabContent {
  @Prop mood: MoodPalette
  @Prop @Watch('onEpoch') uiEpoch: number
  @State letters: Letter[] = []
  private vm: AppViewModel = AppViewModel.getInstance()

  aboutToAppear(): void { this.reload() }
  onEpoch(): void { this.reload() }
  reload(): void { this.letters = [...this.vm.data.letters] }

  build() {
    Column() {
      PageHeader({
        mood: this.mood,
        title: this.mood.solo ? '为爱信封' : '爱的信封',
        subtitle: this.mood.solo ? 'Letters to Love' : 'Secret Letters',
      })
      if (this.letters.length === 0) {
        Column({ space: 8 }) {
          Text('还没有信件')
            .fontSize(FontSizes.bodyLarge)
            .fontFamily(Fonts.LATO)
            .fontColor(this.mood.stone)
          Text('写一封留给未来的信')
            .fontSize(FontSizes.bodySmall)
            .fontFamily(Fonts.LATO_LIGHT)
            .fontColor(this.mood.stone)
        }
        .width('100%')
        .layoutWeight(1)
        .justifyContent(FlexAlign.Center)
      } else {
        List({ space: 14 }) {
          ForEach(this.letters, (item: Letter) => {
            ListItem() {
              Stack({ alignContent: Alignment.TopEnd }) {
                Column({ space: 6 }) {
                  Text(item.type === LetterType.CAPSULE ? 'Time Capsule' : 'Instant')
                    .fontSize(FontSizes.labelSmall)
                    .fontFamily(Fonts.LATO_LIGHT)
                    .fontColor(this.mood.accent)
                    .letterSpacing(1.2)
                  Text(item.title)
                    .fontSize(FontSizes.titleLarge)
                    .fontFamily(Fonts.GARAMOND)
                    .fontColor(this.mood.onSurface)
                  if (item.isUnlocked && item.summary) {
                    Text(item.summary)
                      .fontSize(FontSizes.bodySmall)
                      .fontFamily(Fonts.LATO)
                      .fontColor(this.mood.stone)
                      .maxLines(2)
                      .textOverflow({ overflow: TextOverflow.Ellipsis })
                  } else if (!item.isUnlocked) {
                    Text(item.unlockOnPartnerBind ? '绑定另一半后解锁' : '尚未解锁')
                      .fontSize(FontSizes.bodySmall)
                      .fontFamily(Fonts.LATO)
                      .fontStyle(FontStyle.Italic)
                      .fontColor(this.mood.stone)
                  }
                  Text(`FROM  ${item.senderNickname}`)
                    .fontSize(FontSizes.labelSmall)
                    .fontFamily(Fonts.LATO_LIGHT)
                    .fontColor(this.mood.stone)
                    .margin({ top: 8 })
                    .letterSpacing(1.2)
                }
                .width('100%')
                .padding(18)
                .alignItems(HorizontalAlign.Start)

                Text('♥')
                  .fontSize(16)
                  .fontColor(this.mood.accent)
                  .width(36)
                  .height(36)
                  .textAlign(TextAlign.Center)
                  .border({ width: 1, color: this.mood.softOutline, style: BorderStyle.Dashed })
                  .borderRadius(4)
                  .margin({ top: 14, right: 14 })
                  .rotate({ angle: 12 })
              }
              .width('100%')
              .backgroundColor(this.mood.softSurface)
              .borderRadius(Radii.medium)
              .border({ width: 1, color: this.mood.softOutline })
              .onClick(() => AppNav.pushLetterDetail(item.id))
            }
          }, (item: Letter) => `${item.id}_${this.uiEpoch}`)
        }
        .width('100%')
        .padding({ left: 16, right: 16, bottom: 24 })
        .layoutWeight(1)
        .scrollBar(BarState.Off)
      }
    }
    .width('100%')
    .height('100%')
  }
}

// ─── 我 / 我们 ────────────────────────────────────────

@Component
struct ProfileTabContent {
  @Prop mood: MoodPalette
  @Prop @Watch('onEpoch') uiEpoch: number
  @State confirmAction: string = ''
  @State unbindReason: string = ''
  private vm: AppViewModel = AppViewModel.getInstance()

  aboutToAppear(): void {}
  onEpoch(): void {}

  private hasPartner(): boolean {
    return this.vm.data.linked && this.partner() !== null
  }

  private partner(): CoupleMember | null {
    const meId = this.vm.data.user?.id ?? ''
    const members = this.vm.data.couple?.members
    if (!members) {
      return null
    }
    for (let i = 0; i < members.length; i++) {
      if (members[i].id !== meId) {
        return members[i]
      }
    }
    return null
  }

  private spaceNameSolo(): string {
    const n = this.vm.data.couple?.name
    if (n && n.trim().length > 0) {
      return n
    }
    return this.mood.solo ? '我的小宇宙' : '我们的小宇宙'
  }

  private incoming(): IncomingBindRequest | null {
    const list = this.vm.data.pendingIncomingBinds
    if (list && list.length > 0 && list[0].id.length > 0) {
      return list[0]
    }
    const fromCouple = this.vm.data.couple?.pendingIncomingBinds
    if (fromCouple && fromCouple.length > 0 && fromCouple[0].id.length > 0) {
      return fromCouple[0]
    }
    return null
  }

  private outgoing(): OutgoingBindRequest | null {
    const o = this.vm.data.pendingOutgoingBind ?? this.vm.data.couple?.pendingOutgoingBind ?? null
    if (o && o.id.length > 0) {
      return o
    }
    return null
  }

  private pendingUnbind(): UnbindingRequest | null {
    return this.vm.data.couple?.pendingUnbinding ?? null
  }

  private meNickname(): string {
    return this.vm.data.user?.nickname ?? '我'
  }

  private meAvatar(): string {
    return this.vm.data.user?.avatarUrl ?? ''
  }

  private coverUrl(): string {
    return this.vm.data.user?.coupleCoverUrl ?? ''
  }

  private lovingDaysValue(): number {
    return this.vm.data.lovingDays !== null ? this.vm.data.lovingDays as number : -1
  }

  build() {
    Stack() {
      Scroll() {
        Column({ space: 0 }) {
          PageHeader({
            mood: this.mood,
            title: this.mood.solo ? '我的小宇宙' : '我们的小宇宙',
            subtitle: this.mood.solo ? 'My Soft World' : 'Our Private World',
          })

          if (!this.hasPartner()) {
            if (this.incoming() !== null) {
              IncomingBindCard({
                mood: this.mood,
                spaceName: this.spaceNameSolo(),
                meNickname: this.meNickname(),
                meAvatarUrl: this.meAvatar(),
                inviterNickname: this.incoming()!.requesterNickname,
                inviterAvatarUrl: this.incoming()!.requesterAvatarUrl ?? '',
                inviterLabel: bindInviterLabel(
                  this.incoming()!.requesterNickname,
                  this.incoming()!.requesterPhone,
                ),
                onEdit: () => AppNav.push('pages/SpaceEditPage'),
                onAccept: () => this.vm.acceptBind(this.incoming()!.id),
                onReject: () => this.vm.rejectBind(this.incoming()!.id),
              })
            } else {
              EmptyCoupleCard({
                mood: this.mood,
                spaceName: this.spaceNameSolo(),
                meNickname: this.meNickname(),
                meAvatarUrl: this.meAvatar(),
                hasOutgoing: this.outgoing() !== null,
                outgoingNickname: this.outgoing()?.targetNickname ?? '',
                outgoingAvatarUrl: this.outgoing()?.targetAvatarUrl ?? '',
                outgoingLabel: this.outgoing() !== null
                  ? bindInviterLabel(this.outgoing()!.targetNickname, this.outgoing()!.targetPhone)
                  : '',
                onEdit: () => AppNav.push('pages/SpaceEditPage'),
                onBind: () => AppNav.push('pages/CouplePage'),
                onCancelOutgoing: () => {
                  const id = this.outgoing()?.id
                  if (id) {
                    this.vm.cancelBind(id)
                  }
                },
              })
            }
          } else {
            BoundCoupleCard({
              mood: this.mood,
              spaceName: this.vm.data.couple?.name ?? '我们',
              togetherDate: this.vm.data.couple?.togetherDate ?? '',
              lovingDays: this.lovingDaysValue(),
              meNickname: this.meNickname(),
              meAvatarUrl: this.meAvatar(),
              partnerNickname: this.partner()!.nickname,
              partnerAvatarUrl: this.partner()!.avatarUrl ?? '',
              coupleCoverUrl: this.coverUrl(),
              onEdit: () => AppNav.push('pages/SpaceEditPage'),
            })
          }

          Text('空间设置')
            .fontSize(FontSizes.titleLarge)
            .fontFamily(Fonts.GARAMOND)
            .fontColor(this.mood.onSurface)
            .width('100%')
            .margin({ top: 20, bottom: 4 })

          ProfileSettingRow({
            mood: this.mood,
            title: '空间装扮',
            detail: '暖玫瑰 · 默认主题',
            iconGlyph: '◇',
          })
          ProfileSettingRow({
            mood: this.mood,
            title: '隐私控制',
            detail: '仅情侣双方可见',
            iconGlyph: '◎',
          })
          ProfileSettingRow({
            mood: this.mood,
            title: '关于 Lover',
            detail: '版本 0.9.0',
            iconGlyph: 'ⓘ',
          })

          Column({ space: 10 }) {
            if (this.hasPartner()) {
              if (this.pendingUnbind() === null) {
                Button('申请解除情侣绑定')
                  .width('100%')
                  .height(48)
                  .borderRadius(22)
                  .backgroundColor(Colors.white)
                  .fontColor(this.mood.onSurface)
                  .border({ width: 1, color: this.mood.softOutline })
                  .fontFamily(Fonts.LATO)
                  .onClick(() => {
                    this.unbindReason = ''
                    this.confirmAction = 'request'
                  })
              } else if (this.pendingUnbind()!.requestedBy === this.vm.data.user?.id) {
                Text('解绑申请等待伴侣确认')
                  .fontSize(FontSizes.bodyMedium)
                  .fontFamily(Fonts.LATO)
                  .fontColor(this.mood.stone)
                  .width('100%')
                Button('取消解绑申请')
                  .width('100%')
                  .height(48)
                  .borderRadius(22)
                  .backgroundColor(Colors.white)
                  .fontColor(this.mood.onSurface)
                  .border({ width: 1, color: this.mood.softOutline })
                  .fontFamily(Fonts.LATO)
                  .onClick(() => { this.confirmAction = 'cancel' })
              } else {
                Text('伴侣发起了解绑申请')
                  .fontSize(FontSizes.bodyMedium)
                  .fontFamily(Fonts.LATO)
                  .fontColor(Colors.error)
                  .width('100%')
                Button('确认解除绑定')
                  .width('100%')
                  .height(48)
                  .borderRadius(22)
                  .backgroundColor(this.mood.soft)
                  .fontColor(Colors.white)
                  .fontFamily(Fonts.LATO)
                  .onClick(() => { this.confirmAction = 'confirm' })
                Button('拒绝并取消申请')
                  .width('100%')
                  .height(44)
                  .backgroundColor(Color.Transparent)
                  .fontColor(this.mood.accent)
                  .fontFamily(Fonts.LATO)
                  .onClick(() => { this.confirmAction = 'cancel' })
              }
            }

            Button('退出登录')
              .width('100%')
              .height(44)
              .backgroundColor(Color.Transparent)
              .fontColor(this.mood.stone)
              .fontFamily(Fonts.LATO)
              .onClick(() => { this.confirmAction = 'logout' })
          }
          .width('100%')
          .margin({ top: 20 })
          .padding({ bottom: 48 })
        }
        .width('100%')
        .padding({ left: 20, right: 20, top: 12, bottom: 12 })
      }
      .width('100%')
      .height('100%')
      .scrollBar(BarState.Off)

      if (this.confirmAction.length > 0) {
        this.ConfirmOverlay()
      }
    }
    .width('100%')
    .height('100%')
  }

  @Builder
  ConfirmOverlay() {
    Column() {
      Column({ space: 14 }) {
        Text(this.confirmTitle())
          .fontSize(FontSizes.titleLarge)
          .fontFamily(Fonts.GARAMOND)
          .fontColor(this.mood.onSurface)
          .width('100%')

        if (this.confirmAction === 'request') {
          Text('申请提交后，需要伴侣确认才会正式解绑。')
            .fontSize(FontSizes.bodyMedium)
            .fontFamily(Fonts.LATO)
            .fontColor(this.mood.stone)
            .width('100%')
          SoftTextField({
            mood: this.mood,
            label: '原因（可选）',
            value: $unbindReason,
            placeholder: '',
            maxLength: 300,
            multiline: true,
            areaHeight: 88,
          })
        } else {
          Text(this.confirmBody())
            .fontSize(FontSizes.bodyMedium)
            .fontFamily(Fonts.LATO)
            .fontColor(this.mood.stone)
            .width('100%')
        }

        Row({ space: 12 }) {
          Button('取消')
            .layoutWeight(1)
            .height(44)
            .backgroundColor(Color.Transparent)
            .fontColor(this.mood.stone)
            .onClick(() => { this.confirmAction = '' })
          Button('确认')
            .layoutWeight(1)
            .height(44)
            .backgroundColor(Color.Transparent)
            .fontColor(this.mood.soft)
            .onClick(() => this.runConfirm())
        }
        .width('100%')
      }
      .width('86%')
      .padding(22)
      .backgroundColor(this.mood.background)
      .borderRadius(28)
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
    .backgroundColor('rgba(0,0,0,0.35)')
  }

  private confirmTitle(): string {
    if (this.confirmAction === 'request') {
      return '申请解绑？'
    }
    if (this.confirmAction === 'confirm') {
      return '确认双方解绑？'
    }
    if (this.confirmAction === 'cancel') {
      return '取消解绑申请？'
    }
    return '确认退出？'
  }

  private confirmBody(): string {
    if (this.confirmAction === 'confirm') {
      return '确认后情侣空间将解散，请谨慎操作。'
    }
    if (this.confirmAction === 'logout') {
      return '退出后可再次使用手机号登录。'
    }
    return '该待处理申请将被取消。'
  }

  private async runConfirm(): Promise<void> {
    const action = this.confirmAction
    this.confirmAction = ''
    if (action === 'request') {
      const reason = this.unbindReason.trim()
      await this.vm.requestUnbinding(reason.length > 0 ? reason : null)
      return
    }
    if (action === 'confirm') {
      const id = this.pendingUnbind()?.id
      if (id) {
        await this.vm.confirmUnbinding(id)
      }
      return
    }
    if (action === 'cancel') {
      const id = this.pendingUnbind()?.id
      if (id) {
        await this.vm.cancelUnbinding(id)
      }
      return
    }
    if (action === 'logout') {
      await this.vm.logout()
      AppNav.replace('pages/AuthPage')
    }
  }
}
