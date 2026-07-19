/**
 * AppViewModel — global state management singleton.
 */
import { AppRepository } from './AppRepository'
import { ApiService } from '../network/ApiService'
import { AssetUploader } from '../network/AssetUploader'
import { HttpClient } from '../network/HttpClient'
import { TokenStore } from './TokenStore'
import { isUnauthorized } from '../network/NetworkErrors'
import { MediaPicker, PickedMediaFile } from '../media/MediaPicker'
import {
  AppState, AnniversaryType, LetterType, MediaItem, MediaFileInput,
  UserLookupResponse, MediaAssetOrderItem,
} from '../model/Models'
import { common } from '@kit.AbilityKit'
import { DateUtils } from '../util/DateUtils'

/** Matches Android MainTab / MainPage capsule nav (5 tabs). */
export enum MainTab {
  HOME = 0,
  TIMELINE = 1,
  ANNIVERSARY = 2,
  LETTERS = 3,
  PROFILE = 4,
}

export class PendingMediaUpload {
  id: string = ''
  caption: string = ''
  mediaDate: string = ''
  /** Local preview path/uri for timeline uploading card (first asset). */
  previewUri: string = ''
  assetCount: number = 0
  completed: number = 0
  total: number = 0
  fileFraction: number = 0
  phase: string = '准备上传'

  get progress(): number {
    if (this.total <= 0) {
      return 0
    }
    return Math.min(1, Math.max(0, (this.completed + Math.min(this.fileFraction, 1)) / this.total))
  }
}

@Observed
export class AppViewModel {
  private static instance: AppViewModel | null = null

  repository: AppRepository
  private tokenStore: TokenStore
  private httpClient: HttpClient
  private context: common.UIAbilityContext | null = null
  private mediaPicker: MediaPicker | null = null

  selectedTab: MainTab = MainTab.HOME
  restoreComplete: boolean = false
  promptTogetherDate: boolean = false
  pendingMediaUploads: PendingMediaUpload[] = []
  errorMessage: string | null = null
  successMessage: string | null = null
  /** Partner activity / info notices (not errors). */
  noticeMessage: string | null = null
  coupleRevealEvent: string | null = null
  /** Bumped after data mutations so UI can @Watch and refresh. */
  uiEpoch: number = 0
  /** Files waiting for MediaComposePage. */
  pendingComposeFiles: MediaFileInput[] = []
  /** Deep-link invite code captured before login. */
  pendingInviteCode: string | null = null
  /** Auto-clear top notices (已保存 / 已删除 / errors) after 2s. */
  private noticeClearTimer: number = -1
  private static readonly NOTICE_DISMISS_MS: number = 2000

  private constructor(
    repository: AppRepository,
    tokenStore: TokenStore,
    httpClient: HttpClient,
  ) {
    this.repository = repository
    this.tokenStore = tokenStore
    this.httpClient = httpClient
  }

  /** Show banner then auto-dismiss after 2 seconds. */
  private armNoticeAutoDismiss(): void {
    if (this.noticeClearTimer >= 0) {
      clearTimeout(this.noticeClearTimer)
      this.noticeClearTimer = -1
    }
    const hasSuccess = this.successMessage !== null && this.successMessage.length > 0
    const hasError = this.errorMessage !== null && this.errorMessage.length > 0
    const hasNotice = this.noticeMessage !== null && this.noticeMessage.length > 0
    if (!hasSuccess && !hasError && !hasNotice) {
      return
    }
    this.noticeClearTimer = setTimeout(() => {
      this.noticeClearTimer = -1
      this.errorMessage = null
      this.successMessage = null
      this.noticeMessage = null
      this.bumpUi()
    }, AppViewModel.NOTICE_DISMISS_MS)
  }

  static init(context: common.UIAbilityContext): AppViewModel {
    if (AppViewModel.instance) {
      AppViewModel.instance.context = context
      AppViewModel.instance.mediaPicker = new MediaPicker(context)
      return AppViewModel.instance
    }

    const tokenStore = new TokenStore()
    tokenStore.init(context)

    const httpClient = new HttpClient(
      () => tokenStore.snapshot.accessToken,
      () => tokenStore.snapshot.refreshToken,
    )

    const api = new ApiService(httpClient)
    const uploader = new AssetUploader()
    const repository = new AppRepository(api, uploader, tokenStore)

    httpClient.setRefreshHandlers(
      async (refreshToken) => api.refresh({ refreshToken }),
      async (accessToken, refreshToken) => tokenStore.saveTokens(accessToken, refreshToken),
      async () => tokenStore.clearSession(),
    )

    AppViewModel.instance = new AppViewModel(repository, tokenStore, httpClient)
    AppViewModel.instance.context = context
    AppViewModel.instance.mediaPicker = new MediaPicker(context)
    return AppViewModel.instance
  }

  static getInstance(): AppViewModel {
    if (!AppViewModel.instance) throw new Error('AppViewModel not initialized. Call init() first.')
    return AppViewModel.instance
  }

  get data(): AppState {
    return this.tokenStore.state
  }

  bumpUi(): void {
    this.uiEpoch = this.uiEpoch + 1
  }

  offerInviteCode(code: string | null): void {
    if (code && code.length >= 6) {
      this.pendingInviteCode = code
    }
  }

  // ─── Session ────────────────────────────────────────

  async restoreSession(): Promise<void> {
    if (!this.tokenStore.snapshot.accessToken) {
      this.restoreComplete = true
      this.bumpUi()
      return
    }
    try {
      await this.repository.restoreSession()
      await this.pullPartnerActivityNotices()
    } catch (error) {
      const err = error instanceof Error ? error : new Error(String(error))
      if (isUnauthorized(err) || !this.tokenStore.snapshot.accessToken) {
        this.clearMessages()
      } else {
        this.errorMessage = err.message ?? '刷新失败，已显示缓存'
        this.armNoticeAutoDismiss()
      }
    }
    this.restoreComplete = true
    this.bumpUi()
  }

  async refreshSessionQuietly(): Promise<void> {
    if (!this.tokenStore.snapshot.accessToken) return
    try {
      await this.repository.refreshAll()
      await this.pullPartnerActivityNotices()
      this.bumpUi()
    } catch (_e) { /* quiet */ }
  }

  private async pullPartnerActivityNotices(): Promise<void> {
    if (!this.tokenStore.snapshot.linked) return
    const items = await this.repository.fetchUnreadPartnerActivity()
    if (items.length === 0) return
    const ids = items.map((i) => i.id)
    await this.repository.markPartnerActivityRead(ids)
    const titles = items.slice(0, 8).map((e) => e.title).filter((t) => t.length > 0)
    if (titles.length > 0) {
      this.noticeMessage = titles.join(' · ')
      this.armNoticeAutoDismiss()
    }
  }

  selectTab(tab: MainTab): void {
    this.selectedTab = tab
    this.bumpUi()
  }

  // ─── Auth ───────────────────────────────────────────

  async sendSms(phone: string): Promise<void> {
    await this.repository.sendSms(phone)
  }

  async login(phone: string, code: string): Promise<void> {
    await this.repository.login(phone, code)
    this.bumpUi()
  }

  async loginWithPnvsToken(loginToken: string): Promise<void> {
    await this.repository.loginWithPnvsToken(loginToken)
    this.bumpUi()
  }

  async logout(): Promise<void> {
    this.clearMessages()
    try {
      await this.repository.logout()
    } catch (error) {
      const err = error instanceof Error ? error : new Error(String(error))
      if (!isUnauthorized(err) && this.tokenStore.snapshot.accessToken) {
        this.errorMessage = err.message ?? '退出失败'
        this.armNoticeAutoDismiss()
      }
    }
    this.bumpUi()
  }

  async completeOnboarding(nickname: string, gender: string, birthday: string, spaceName: string): Promise<void> {
    await this.repository.completeOnboarding(nickname, gender, birthday, spaceName)
    this.bumpUi()
  }

  // ─── Binding ────────────────────────────────────────

  async requestBind(phone: string): Promise<void> {
    await this.launchAction('已发送绑定请求', async () => {
      if (phone.trim().length !== 11) throw new Error('请输入对方手机号')
      await this.repository.requestBind(phone)
    })
  }

  async acceptBind(id: string): Promise<void> {
    await this.launchAction('绑定成功', async () => {
      const result = await this.repository.acceptBind(id)
      if (result.needsTogetherDate) {
        this.promptTogetherDate = true
      }
    })
  }

  async rejectBind(id: string): Promise<void> {
    await this.launchAction('已拒绝', () => this.repository.rejectBind(id))
  }

  async cancelBind(id: string): Promise<void> {
    await this.launchAction('已取消', () => this.repository.cancelBind(id))
  }

  async saveTogetherDate(date: string | null): Promise<void> {
    await this.launchAction(date ? '已保存' : null, async () => {
      await this.repository.updateTogetherDate(date)
      this.promptTogetherDate = false
    })
  }

  dismissTogetherDatePrompt(): void {
    this.promptTogetherDate = false
    this.bumpUi()
  }

  // ─── Media ──────────────────────────────────────────

  /**
   * Start media upload in background (mirrors Android MainViewModel.addMedia).
   * Returns immediately after enqueueing — caller should navigate away.
   */
  addMedia(files: Array<MediaFileInput>, caption: string, date: string): void {
    if (!this.tokenStore.snapshot.profileCompleted) {
      this.errorMessage = '请先完成空间创建'
      this.armNoticeAutoDismiss()
      this.bumpUi()
      return
    }
    if (files.length === 0) {
      this.errorMessage = '请选择至少一张照片或视频'
      this.armNoticeAutoDismiss()
      this.bumpUi()
      return
    }
    const jobId = this.generateId()
    const pending = new PendingMediaUpload()
    pending.id = jobId
    pending.caption = caption.trim()
    pending.mediaDate = date
    pending.assetCount = files.length
    pending.total = files.length
    pending.phase = '准备上传'
    const first = files[0]
    if (first.previewPath.length > 0) {
      pending.previewUri = first.previewPath
    } else if (first.cachePath.length > 0) {
      pending.previewUri = first.cachePath
    }
    this.pendingMediaUploads = [pending, ...this.pendingMediaUploads]
    this.selectedTab = MainTab.TIMELINE
    this.clearComposePending()
    this.bumpUi()

    // Fire-and-forget — do not block compose UI
    this.runAddMediaJob(jobId, files, caption, date)
  }

  private async runAddMediaJob(
    jobId: string,
    files: Array<MediaFileInput>,
    caption: string,
    date: string,
  ): Promise<void> {
    try {
      await this.repository.addMediaBatch(files, caption, date,
        (completed: number, total: number, fraction: number, phase: string) => {
          const next: PendingMediaUpload[] = []
          for (let i = 0; i < this.pendingMediaUploads.length; i++) {
            const u = this.pendingMediaUploads[i]
            if (u.id !== jobId) {
              next.push(u)
              continue
            }
            const updated = new PendingMediaUpload()
            updated.id = u.id
            updated.caption = u.caption
            updated.mediaDate = u.mediaDate
            updated.previewUri = u.previewUri
            updated.assetCount = u.assetCount
            updated.completed = completed
            updated.total = total
            updated.fileFraction = fraction
            updated.phase = phase
            next.push(updated)
          }
          this.pendingMediaUploads = next
          this.bumpUi()
        }
      )
      this.removePendingUpload(jobId)
      this.successMessage = '已保存'
      this.errorMessage = null
      this.armNoticeAutoDismiss()
      this.bumpUi()
    } catch (error) {
      this.removePendingUpload(jobId)
      const err = error instanceof Error ? error : new Error(String(error))
      if (isUnauthorized(err) || !this.tokenStore.snapshot.accessToken) {
        this.clearMessages()
      } else {
        this.errorMessage = err.message ?? '上传失败'
        this.successMessage = null
        this.armNoticeAutoDismiss()
      }
      this.bumpUi()
    }
  }

  private removePendingUpload(jobId: string): void {
    const kept: PendingMediaUpload[] = []
    for (let i = 0; i < this.pendingMediaUploads.length; i++) {
      if (this.pendingMediaUploads[i].id !== jobId) {
        kept.push(this.pendingMediaUploads[i])
      }
    }
    this.pendingMediaUploads = kept
  }

  async updateMedia(id: string, caption: string, date: string | null,
    assetOrder: MediaAssetOrderItem[] | null = null): Promise<void> {
    await this.launchAction('已保存', () => this.repository.updateMedia(id, caption, date, assetOrder))
  }

  async deleteMedia(id: string): Promise<void> {
    await this.launchAction('已删除', () => this.repository.deleteMedia(id))
  }

  async loadMoreMedia(): Promise<void> {
    try {
      await this.repository.loadMoreMedia()
      this.bumpUi()
    } catch (_e) { /* ignore */ }
  }

  async openMediaDetail(item: MediaItem): Promise<MediaItem> {
    try {
      return await this.repository.ensureMediaOriginals(item)
    } catch (error) {
      const err = error instanceof Error ? error : new Error(String(error))
      if (isUnauthorized(err) || !this.tokenStore.snapshot.accessToken) {
        this.clearMessages()
      }
      return item
    }
  }

  async signAssetUrl(assetId: string): Promise<string> {
    return this.repository.signAssetOriginalUrl(assetId)
  }

  clearComposePending(): void {
    this.pendingComposeFiles = []
  }

  /**
   * Append media via PhotoViewPicker into pendingComposeFiles (for MediaComposePage).
   * Returns number newly picked (0 = cancelled).
   */
  async pickForCompose(maxItems: number = 20, allowVideo: boolean = true): Promise<number> {
    if (!this.mediaPicker) {
      this.errorMessage = '媒体选择器未初始化'
      this.armNoticeAutoDismiss()
      this.bumpUi()
      return 0
    }
    const room = maxItems - this.pendingComposeFiles.length
    if (room <= 0) {
      return 0
    }
    try {
      const picked = await this.mediaPicker.pickMedia(room, allowVideo)
      if (picked.length === 0) {
        return 0
      }
      const extras = MediaPicker.toMediaInputs(picked)
      this.pendingComposeFiles = [...this.pendingComposeFiles, ...extras].slice(0, maxItems)
      return extras.length
    } catch (error) {
      const err = error instanceof Error ? error : new Error(String(error))
      this.errorMessage = err.message ?? '选择媒体失败'
      this.armNoticeAutoDismiss()
      this.bumpUi()
      return 0
    }
  }

  removeComposeFileAt(index: number): void {
    if (index < 0 || index >= this.pendingComposeFiles.length) {
      return
    }
    const next: MediaFileInput[] = []
    for (let i = 0; i < this.pendingComposeFiles.length; i++) {
      if (i !== index) {
        next.push(this.pendingComposeFiles[i])
      }
    }
    this.pendingComposeFiles = next
  }

  async pickAvatarFile(): Promise<MediaFileInput | null> {
    const picked = await this.pickAvatarPicked()
    if (!picked) return null
    return MediaPicker.toMediaInputs([picked])[0]
  }

  async pickAvatarPicked(): Promise<PickedMediaFile | null> {
    if (!this.mediaPicker) return null
    const picked = await this.mediaPicker.pickImages(1)
    if (picked.length === 0) return null
    return picked[0]
  }

  // ─── Anniversaries ──────────────────────────────────

  async addAnniversary(title: string, date: string, type: AnniversaryType): Promise<void> {
    if (!title.trim()) throw new Error('请填写纪念日名称')
    await this.launchAction('已保存', () => this.repository.addAnniversary(title, date, type))
  }

  async updateAnniversary(id: string, title: string, date: string, type: AnniversaryType): Promise<void> {
    if (!title.trim()) throw new Error('请填写纪念日名称')
    await this.launchAction('已保存', () => this.repository.updateAnniversary(id, title, date, type))
  }

  async deleteAnniversary(id: string): Promise<void> {
    await this.launchAction('已删除', () => this.repository.deleteAnniversary(id))
  }

  // ─── Letters ────────────────────────────────────────

  async addLetter(
    title: string, content: string, type: LetterType,
    unlockDate: string | null, unlockOnPartnerBind: boolean = false,
  ): Promise<void> {
    await this.launchAction('已保存', async () => {
      AppViewModel.validateLetter(title, content, type, unlockDate, unlockOnPartnerBind, this.tokenStore.snapshot.linked)
      await this.repository.addLetter(title, content, type, unlockDate, unlockOnPartnerBind)
    })
  }

  async deleteLetter(id: string): Promise<void> {
    await this.launchAction('已删除', () => this.repository.deleteLetter(id))
  }

  // ─── Couple ─────────────────────────────────────────

  async updateCoupleCard(
    name: string,
    togetherDate: string | null,
    coverFile: MediaFileInput | null = null,
    clearCover: boolean = false,
  ): Promise<void> {
    await this.launchAction('已保存', async () => {
      if (!name.trim()) throw new Error('请填写空间名称')
      await this.repository.updateCoupleCard(name.trim(), togetherDate, coverFile, clearCover)
    })
  }

  async updatePersonalCard(spaceName: string, avatarFile: MediaFileInput | null = null): Promise<void> {
    await this.launchAction('已保存', async () => {
      if (!spaceName.trim()) throw new Error('请填写空间名称')
      await this.repository.updatePersonalCard(spaceName, avatarFile)
    })
  }

  async lookupUser(phone: string): Promise<UserLookupResponse> {
    return this.repository.lookupUser(phone)
  }

  async requestUnbinding(reason: string | null): Promise<void> {
    await this.launchAction('已提交', () => this.repository.requestUnbinding(reason))
  }

  async confirmUnbinding(id: string): Promise<void> {
    await this.launchAction(null, () => this.repository.confirmUnbinding(id))
  }

  async cancelUnbinding(id: string): Promise<void> {
    await this.launchAction('已取消', () => this.repository.cancelUnbinding(id))
  }

  markCoupleThemeRevealShown(linkId: string): void {
    this.repository.markCoupleThemeRevealShown(linkId)
  }

  clearMessages(): void {
    if (this.noticeClearTimer >= 0) {
      clearTimeout(this.noticeClearTimer)
      this.noticeClearTimer = -1
    }
    this.errorMessage = null
    this.successMessage = null
    this.noticeMessage = null
  }

  clearNotice(): void {
    this.noticeMessage = null
    if (this.successMessage === null && this.errorMessage === null) {
      if (this.noticeClearTimer >= 0) {
        clearTimeout(this.noticeClearTimer)
        this.noticeClearTimer = -1
      }
    }
  }

  todayIso(): string {
    return DateUtils.formatDate(new Date())
  }

  private async launchAction(successMsg: string | null, block: () => Promise<void>): Promise<void> {
    try {
      await block()
      if (successMsg) {
        this.successMessage = successMsg
        this.errorMessage = null
        this.armNoticeAutoDismiss()
      }
      this.bumpUi()
    } catch (error) {
      const err = error instanceof Error ? error : new Error(String(error))
      if (isUnauthorized(err) || !this.tokenStore.snapshot.accessToken) {
        this.clearMessages()
      } else {
        this.errorMessage = err.message ?? '操作失败'
        this.successMessage = null
        this.armNoticeAutoDismiss()
      }
      this.bumpUi()
      throw err
    }
  }

  private generateId(): string {
    return Date.now().toString(36) + Math.random().toString(36).substring(2, 10)
  }

  static validateLetter(
    title: string, content: string, type: LetterType,
    unlockDate: string | null, unlockOnPartnerBind: boolean, linked: boolean,
  ): void {
    if (!title.trim()) throw new Error('请填写标题')
    if (!content.trim()) throw new Error('请写下想说的话')
    if (type === LetterType.INSTANT && !linked) throw new Error('绑定另一半后才能发送即时信')
    if (type === LetterType.CAPSULE && !unlockOnPartnerBind) {
      if (!unlockDate) throw new Error('请选择解锁日期，或勾选绑定后开启')
      const d = new Date(unlockDate)
      const today = new Date()
      today.setHours(0, 0, 0, 0)
      if (d <= today) throw new Error('解锁日期必须晚于今天')
    }
  }
}
