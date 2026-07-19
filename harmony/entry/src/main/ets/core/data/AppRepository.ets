/**
 * AppRepository — central data layer wrapping all API calls.
 * Mirror of Android's AppRepository.kt
 */
import { ApiService } from '../network/ApiService'
import { AssetUploader } from '../network/AssetUploader'
import { TokenStore } from './TokenStore'
import { BackendException, toUserFacing } from '../network/NetworkErrors'
import {
  AppState, User, CoupleSpace, MediaItem, MediaAssetPart, Anniversary, Letter,
  MediaType, AnniversaryType, LetterType,
  SendSmsResponse, AuthResponse, MeResponse, OnboardingResponse,
  PnvsSdkInfoResponse, PnvsLoginRequest,
  UserLookupResponse, AcceptBindResponse, BootstrapResponse,
  CreateMediaAssetRequest, MediaAssetOrderItem,
  IncomingBindRequest, OutgoingBindRequest,
  PartnerActivityEvent, JsonMap, DailyQuote, MediaFileInput,
  MediaUnreadPage, MediaUnreadSummary,
} from '../model/Models'
import type {
  SendSmsRequest, LoginRequest, OnboardingRequest,
  CreateBindRequest, UpdateCoupleLinkRequest, UpdateCoupleSpaceRequest,
  TokenAssetRequest, AssetRequest, SignAssetRequest,
  CreateMediaRequest, UpdateMediaRequest,
  CreateAnniversaryRequest, UpdateAnniversaryRequest,
  CreateLetterRequest, CreateUnbindingRequest,
  PatchMeRequest, MarkActivityReadRequest, MarkMediaReadRequest,
} from '../model/Models'

export class AppRepository {
  private api: ApiService
  private uploader: AssetUploader
  private tokenStore: TokenStore
  private mediaCursor: string | null = null
  private mediaYearFilter: number | null = null
  private loadingMore: boolean = false
  mediaHasMore: boolean = false
  mediaYears: number[] = []
  mediaUnreadCount: number = 0
  private secondaryReadyListener: (() => void) | null = null

  constructor(api: ApiService, uploader: AssetUploader, tokenStore: TokenStore) {
    this.api = api
    this.uploader = uploader
    this.tokenStore = tokenStore
  }

  get mediaYearFilterState(): number | null {
    return this.mediaYearFilter
  }

  setSecondaryReadyListener(listener: (() => void) | null): void {
    this.secondaryReadyListener = listener
  }

  get state(): AppState {
    return this.tokenStore.state
  }

  // ─── Auth ───────────────────────────────────────────

  async sendSms(phone: string): Promise<SendSmsResponse> {
    return this.call(() => this.api.sendSms({ phone: phone.trim() } as SendSmsRequest))
  }

  async login(phone: string, code: string): Promise<void> {
    const auth = await this.call(() =>
      this.api.login({ phone: phone.trim(), code: code.trim() } as LoginRequest)
    )
    await this.applyAuthSession(auth)
  }

  async fetchPnvsSdkInfo(): Promise<PnvsSdkInfoResponse> {
    return this.call(() => this.api.pnvsSdkInfo('harmony'))
  }

  async loginWithPnvsToken(loginToken: string): Promise<void> {
    const req: PnvsLoginRequest = { loginToken: loginToken }
    const auth = await this.call(() => this.api.pnvsLogin(req))
    await this.applyAuthSession(auth)
  }

  private async applyAuthSession(auth: AuthResponse): Promise<void> {
    await this.tokenStore.saveTokens(auth.accessToken, auth.refreshToken)
    try {
      const me = await this.call(() => this.api.me())
      this.applyMe(me, true)
      if (this.tokenStore.snapshot.profileCompleted) {
        // 登录首屏：只挡「空间」必需数据；时光/纪念/信封后台补齐
        await this.refreshHomeEssentials(false)
        this.warmSecondaryHomeData()
      } else {
        this.tokenStore.update((s) => { s.sessionReady = true; return s })
      }
    } catch (error) {
      await this.tokenStore.clearSession()
      return Promise.reject(error)
    }
  }

  async restoreSession(): Promise<void> {
    if (!this.tokenStore.snapshot.accessToken) return
    this.tokenStore.update((s) => { s.sessionReady = false; return s })
    try {
      const me = await this.call(() => this.api.me())
      this.applyMe(me, false)
      if (this.tokenStore.snapshot.profileCompleted) {
        await this.refreshHomeEssentials(false)
        this.warmSecondaryHomeData()
      } else {
        this.tokenStore.update((s) => { s.sessionReady = true; return s })
      }
    } catch (error) {
      this.tokenStore.update((s) => { s.sessionReady = true; return s })
      return Promise.reject(error)
    }
  }

  private applyMe(me: MeResponse, clearContent: boolean): void {
    const user = User.fromJson(me.user)
    this.tokenStore.update((s) => {
      s.user = user
      s.activeSpaceId = me.activeSpaceId
      s.personalSpaceId = me.personalSpaceId
      s.loverSpaceId = me.loverSpaceId
      s.profileCompleted = me.profileCompleted || user.profileCompleted
      s.linked = me.linked
      if (clearContent || !s.profileCompleted) {
        s.couple = null
        s.pendingIncomingBinds = []
        s.pendingOutgoingBind = null
        s.lovingDays = null
        s.dailyQuote = null
        s.media = []
        s.anniversaries = []
        s.letters = []
      }
      return s
    })
  }

  // ─── Onboarding ─────────────────────────────────────

  async completeOnboarding(
    nickname: string, gender: string, birthday: string, spaceName: string,
  ): Promise<void> {
    this.tokenStore.update((s) => { s.sessionReady = false; return s })
    const result = await this.call(() =>
      this.api.onboarding({
        nickname: nickname.trim(),
        avatarUrl: null,
        gender,
        birthday,
        spaceName: spaceName.trim(),
      } as OnboardingRequest)
    )
    const user = User.fromJson(result.user)
    this.tokenStore.update((s) => {
      s.user = user
      s.personalSpaceId = result.personalSpaceId
      s.activeSpaceId = result.personalSpaceId
      s.profileCompleted = true
      s.linked = false
      return s
    })
    await this.refreshAll()
  }

  // ─── Couple Binding ─────────────────────────────────

  async requestBind(phone: string): Promise<void> {
    const created = await this.call(() =>
      this.api.createBind({ phone: phone.trim() } as CreateBindRequest)
    )
    const outgoing = new OutgoingBindRequest()
    outgoing.id = created.id
    outgoing.targetUserId = created.targetUserId ?? ''
    outgoing.targetNickname = created.targetNickname ?? ''
    outgoing.targetPhone = created.targetPhone?.trim() || phone.trim()
    outgoing.targetAvatarUrl = created.targetAvatarUrl
    outgoing.status = created.status || 'pending'
    outgoing.expiresAt = created.expiresAt
    outgoing.createdAt = created.createdAt
    this.tokenStore.update((s) => {
      s.pendingOutgoingBind = outgoing
      if (!s.couple) s.couple = new CoupleSpace()
      s.couple!.pendingOutgoingBind = outgoing
      return s
    })
    await this.refreshAll()
  }

  async acceptBind(id: string): Promise<AcceptBindResponse> {
    const result = await this.call(() => this.api.acceptBind(id))
    this.tokenStore.update((s) => {
      s.linked = true
      s.coupleLinkId = result.coupleLinkId
      s.loverSpaceId = result.loverSpaceId
      s.needsTogetherDate = result.needsTogetherDate
      return s
    })
    await this.refreshAll()
    return result
  }

  async rejectBind(id: string): Promise<void> {
    await this.call(() => this.api.rejectBind(id))
    await this.refreshAll()
  }

  async cancelBind(id: string): Promise<void> {
    await this.call(() => this.api.cancelBind(id))
    this.tokenStore.update((s) => {
      s.pendingOutgoingBind = null
      if (s.couple) s.couple.pendingOutgoingBind = null
      return s
    })
    await this.refreshAll()
  }

  async markCoupleThemeRevealShown(linkId: string): Promise<void> {
    if (!linkId) return
    this.tokenStore.update((s) => { s.coupleThemeRevealShownLinkId = linkId; return s })
  }

  async updateTogetherDate(togetherDate: string | null): Promise<void> {
    await this.call(() =>
      this.api.updateCoupleLink({ togetherDate, name: null } as UpdateCoupleLinkRequest)
    )
    await this.refreshAll()
  }

  async updateCoupleCard(
    name: string | null,
    togetherDate: string | null,
    coverFile: MediaFileInput | null = null,
    clearCover: boolean = false,
  ): Promise<void> {
    if (name?.trim()) {
      await this.call(() =>
        this.api.updateCoupleLink({
          name: name.trim(),
          togetherDate,
        } as UpdateCoupleLinkRequest)
      )
    } else if (togetherDate !== undefined) {
      await this.call(() =>
        this.api.updateCoupleLink({
          name: null,
          togetherDate,
        } as UpdateCoupleLinkRequest)
      )
    }
    if (clearCover) {
      await this.call(() => this.api.patchMe({ clearCoupleCover: true } as PatchMeRequest))
    } else if (coverFile) {
      const assetId = await this.uploadAsset(
        coverFile.fileName, coverFile.mimeType, coverFile.data.byteLength, coverFile.data, 'cover',
      )
      await this.call(() => this.api.patchMe({ coupleCoverAssetId: assetId } as PatchMeRequest))
    }
    await this.refreshAll()
  }

  async updatePersonalCard(spaceName: string, avatarFile: MediaFileInput | null = null): Promise<void> {
    const trimmed = spaceName.trim()
    if (!trimmed) throw new BackendException('VALIDATION', '请填写空间名称')
    await this.call(() =>
      this.api.updateCoupleSpace({ name: trimmed } as UpdateCoupleSpaceRequest)
    )
    if (avatarFile) {
      const assetId = await this.uploadAsset(
        avatarFile.fileName, avatarFile.mimeType, avatarFile.data.byteLength, avatarFile.data, 'avatar',
      )
      await this.call(() => this.api.patchMe({ avatarAssetId: assetId } as PatchMeRequest))
    }
    await this.refreshAll()
  }

  async lookupUser(phone: string): Promise<UserLookupResponse> {
    return this.call(() => this.api.lookupUser(phone.trim()))
  }

  // ─── Refresh All ────────────────────────────────────

  /**
   * 完整刷新：先解锁「空间」首屏，再等待时光/纪念/信封补齐（下拉刷新等场景）。
   * 登录/恢复会话请用 refreshHomeEssentials + warmSecondaryHomeData，避免挡在全量签名上。
   */
  async refreshAll(): Promise<void> {
    await this.refreshHomeEssentials(true)
    await this.awaitSecondaryHomeData()
  }

  /**
   * 「空间」首页必需：me（可选）+ bootstrap + coupleSpace。
   * 掠影只用 bootstrap.recentMedia（8 条）并为封面签 thumb，不再拉 media?limit=100。
   */
  async refreshHomeEssentials(fetchMe: boolean = true): Promise<void> {
    const previous = this.tokenStore.snapshot
    let meResult: MeResponse | null = null
    let bootstrapResult: BootstrapResponse | null = null
    let coupleBase: CoupleSpace | null = null

    if (fetchMe) {
      const results = await Promise.allSettled([
        this.api.me(),
        this.api.bootstrap(),
        this.api.coupleSpace(),
      ])
      if (results[0].status === 'fulfilled') {
        meResult = results[0].value as MeResponse
      }
      if (results[1].status === 'fulfilled') {
        bootstrapResult = results[1].value as BootstrapResponse
      }
      if (results[2].status === 'fulfilled') {
        coupleBase = CoupleSpace.fromJson(results[2].value as JsonMap)
      }
    } else {
      const results = await Promise.allSettled([
        this.api.bootstrap(),
        this.api.coupleSpace(),
      ])
      if (results[0].status === 'fulfilled') {
        bootstrapResult = results[0].value as BootstrapResponse
      }
      if (results[1].status === 'fulfilled') {
        coupleBase = CoupleSpace.fromJson(results[1].value as JsonMap)
      }
    }

    if (!meResult && !bootstrapResult && !coupleBase) {
      throw new BackendException('REFRESH_FAILED', '刷新失败，请检查网络后重试')
    }

    let lovingDays = previous.lovingDays
    let needsTogetherDate = previous.needsTogetherDate
    let dailyQuote = previous.dailyQuote
    let coupleLinkId = previous.coupleLinkId
    let linked = previous.linked
    let mediaItems: MediaItem[] = previous.media

    if (bootstrapResult) {
      const journey = bootstrapResult.lovingJourney
      lovingDays = (journey.days as number) ?? lovingDays
      needsTogetherDate = (journey.needsTogetherDate as boolean) ?? needsTogetherDate
      if (bootstrapResult.dailyQuote) {
        dailyQuote = DailyQuote.fromJson(bootstrapResult.dailyQuote)
      }
      coupleLinkId = bootstrapResult.coupleLinkId ?? coupleLinkId
      linked = bootstrapResult.linked || linked
      const recent = bootstrapResult.recentMedia ?? []
      if (recent.length > 0) {
        const raw = recent.map((m) => MediaItem.fromJson(m as JsonMap))
        mediaItems = await this.signMediaCovers(this.sortByDate(raw), previous.media)
      } else if (previous.media.length === 0) {
        mediaItems = []
      }
    }

    this.tokenStore.update((s) => {
      if (meResult) {
        s.user = User.fromJson(meResult.user)
        s.activeSpaceId = meResult.activeSpaceId ?? s.activeSpaceId
        s.personalSpaceId = meResult.personalSpaceId ?? s.personalSpaceId
        s.loverSpaceId = meResult.loverSpaceId ?? s.loverSpaceId
        s.linked = meResult.linked || linked
      }
      if (coupleBase) {
        s.couple = coupleBase
      }
      s.coupleLinkId = coupleLinkId
      s.linked = linked
      s.lovingDays = lovingDays
      s.needsTogetherDate = needsTogetherDate
      s.dailyQuote = dailyQuote
      s.media = mediaItems
      s.sessionReady = true
      return s
    })
  }

  /** 登录后不阻塞进首页：后台补齐时光列表、年份、未读与纪念/信封 */
  private warmSecondaryHomeData(): void {
    this.awaitSecondaryHomeData()
      .then(() => {
        if (this.secondaryReadyListener) {
          this.secondaryReadyListener()
        }
      })
      .catch(() => {
        // best-effort
      })
  }

  private async awaitSecondaryHomeData(): Promise<void> {
    await Promise.allSettled([
      this.refreshMedia(),
      this.refreshMediaYears(),
      this.refreshMediaUnreadSummary(),
      this.refreshAnniversariesAndLetters(),
    ])
  }

  private async refreshAnniversariesAndLetters(): Promise<void> {
    const results = await Promise.allSettled([
      this.api.anniversaries(),
      this.api.letters(),
    ])
    let anniversariesList: Anniversary[] = this.tokenStore.snapshot.anniversaries
    let lettersList: Letter[] = this.tokenStore.snapshot.letters
    if (results[0].status === 'fulfilled') {
      anniversariesList = results[0].value.items.map((a) => Anniversary.fromJson(a as JsonMap))
    }
    if (results[1].status === 'fulfilled') {
      lettersList = results[1].value.items.map((l) => Letter.fromJson(l as JsonMap))
    }
    this.tokenStore.update((s) => {
      s.anniversaries = anniversariesList
      s.letters = lettersList
      return s
    })
  }

  // ─── Media ──────────────────────────────────────────

  async refreshMedia(year: number | null = this.mediaYearFilter): Promise<void> {
    this.mediaYearFilter = year
    const page = await this.call(() => this.api.media(null, 30, year))
    this.mediaCursor = page.nextCursor
    this.mediaHasMore = page.nextCursor !== null && page.nextCursor !== undefined
    const previous = this.tokenStore.snapshot.media
    const items = page.items.map((m) => MediaItem.fromJson(m as JsonMap))
    let signed = previous
    try {
      signed = await this.signMediaCovers(this.sortByDate(items), previous)
    } catch (_e) {
      signed = previous
    }
    this.tokenStore.update((s) => { s.media = signed; return s })
  }

  async setMediaYearFilter(year: number | null): Promise<void> {
    if (this.mediaYearFilter === year) {
      return
    }
    await this.refreshMedia(year)
  }

  async refreshMediaYears(): Promise<void> {
    try {
      const res = await this.call(() => this.api.mediaYears())
      this.mediaYears = res.years ?? []
    } catch (_e) {
      this.mediaYears = []
    }
  }

  async refreshMediaUnreadSummary(): Promise<void> {
    if (!this.tokenStore.snapshot.linked) {
      this.mediaUnreadCount = 0
      return
    }
    try {
      const summary = await this.call(() => this.api.mediaUnreadSummary())
      this.mediaUnreadCount = summary.count ?? 0
    } catch (_e) {
      // keep previous count
    }
  }

  async fetchUnreadMedia(cursor: string | null = null, limit: number = 30): Promise<MediaUnreadPage> {
    const page = await this.call(() => this.api.mediaUnread(cursor, limit))
    const previous = this.tokenStore.snapshot.media
    const rawItems = (page.items ?? []).map((m) => MediaItem.fromJson(m as JsonMap))
    let signed = rawItems
    try {
      signed = await this.signMediaCovers(this.sortByDate(rawItems), previous)
    } catch (_e) {
      signed = rawItems
    }
    this.mediaUnreadCount = page.count ?? 0
    const result: MediaUnreadPage = {
      items: signed,
      nextCursor: page.nextCursor ?? null,
      count: page.count ?? 0,
    }
    return result
  }

  async markMediaRead(id: string): Promise<void> {
    try {
      const summary: MediaUnreadSummary = await this.call(() => this.api.markMediaRead(id))
      this.mediaUnreadCount = summary.count ?? 0
    } catch (_e) {
      // ignore
    }
  }

  async markAllUnreadMediaRead(): Promise<void> {
    try {
      const req: MarkMediaReadRequest = { ids: null, all: true }
      const summary: MediaUnreadSummary = await this.call(() => this.api.markMediaReadBatch(req))
      this.mediaUnreadCount = summary.count ?? 0
    } catch (_e) {
      // ignore
    }
  }

  async fetchMediaDetail(id: string): Promise<MediaItem> {
    const raw = await this.call(() => this.api.mediaDetail(id))
    return MediaItem.fromJson(raw as JsonMap)
  }

  async loadMoreMedia(): Promise<boolean> {
    const cursor = this.mediaCursor
    if (!cursor) {
      this.mediaHasMore = false
      return false
    }
    if (this.loadingMore) {
      return false
    }
    this.loadingMore = true
    try {
      const page = await this.call(() => this.api.media(cursor, 30, this.mediaYearFilter))
      this.mediaCursor = page.nextCursor
      this.mediaHasMore = page.nextCursor !== null && page.nextCursor !== undefined
      if (page.items.length === 0) {
        this.mediaHasMore = false
        return false
      }
      const newItems = page.items.map((m) => MediaItem.fromJson(m as JsonMap))
      const previous = this.tokenStore.snapshot.media
      let signed = newItems
      try {
        signed = await this.signMedia(newItems, previous)
      } catch (_e) {
        signed = newItems
      }
      const merged = [...previous, ...signed]
      this.tokenStore.update((s) => { s.media = this.sortByDate(merged); return s })
      return true
    } finally {
      this.loadingMore = false
    }
  }

  /**
   * 列表缩略图：每条只签封面 cover（首页掠影 / 时光列表只展示封面）。
   * 并行签名，避免登录时上百次串行 /sign。
   */
  private async signMedia(items: MediaItem[], previous: MediaItem[] = []): Promise<MediaItem[]> {
    return this.signMediaCovers(items, previous)
  }

  private async signMediaCovers(items: MediaItem[], previous: MediaItem[] = []): Promise<MediaItem[]> {
    const prevById = new Map<string, MediaItem>()
    for (const p of previous) {
      prevById.set(p.id, p)
    }
    const tasks: Promise<MediaItem>[] = items.map(async (item: MediaItem) => {
      const prev = prevById.get(item.id)
      if (prev && this.canReuseSignedThumbs(item, prev)) {
        return this.mergeSignedThumbs(item, prev)
      }
      const cover = MediaItem.coverOf(item)
      if (!cover) {
        return item
      }
      const signedCover = await this.signAssetForList(cover)
      const assets: MediaAssetPart[] = []
      for (const part of item.assets) {
        if (part.id === signedCover.id || part.assetId === signedCover.assetId) {
          assets.push(signedCover)
        } else {
          assets.push(part)
        }
      }
      return this.cloneMediaItem(item, assets)
    })
    return await Promise.all(tasks)
  }

  private mediaAssetFingerprint(assets: MediaAssetPart[]): string {
    const parts: string[] = []
    const sorted = [...assets].sort((a, b) => a.sortOrder - b.sortOrder)
    for (const part of sorted) {
      parts.push(`${part.id}:${part.assetId}:${part.type}:${part.sortOrder}`)
    }
    return parts.join('|')
  }

  private canReuseSignedThumbs(item: MediaItem, prev: MediaItem): boolean {
    if (this.mediaAssetFingerprint(item.assets) !== this.mediaAssetFingerprint(prev.assets)) {
      return false
    }
    for (const a of prev.assets) {
      if (this.isReusableSignedUrl(a.thumbnailUrl) || this.isReusableSignedUrl(a.url)) {
        return true
      }
    }
    return false
  }

  private mergeSignedThumbs(item: MediaItem, prev: MediaItem): MediaItem {
    const prevByPartId = new Map<string, MediaAssetPart>()
    const prevByAssetId = new Map<string, MediaAssetPart>()
    for (const a of prev.assets) {
      if (a.id) prevByPartId.set(a.id, a)
      prevByAssetId.set(a.assetId, a)
    }
    const merged: MediaAssetPart[] = []
    for (const part of item.assets) {
      const cached = prevByPartId.get(part.id) ?? prevByAssetId.get(part.assetId)
      const thumb = cached?.thumbnailUrl && this.isReusableSignedUrl(cached.thumbnailUrl)
        ? cached.thumbnailUrl : null
      const original = cached?.url && this.isReusableSignedUrl(cached.url) ? cached.url : ''
      if (thumb || original.length > 0) {
        const copy = this.clonePart(part)
        copy.url = original
        copy.thumbnailUrl = thumb
        merged.push(copy)
      } else {
        merged.push(part)
      }
    }
    return this.cloneMediaItem(item, merged)
  }

  private isReusableSignedUrl(url: string | null | undefined): boolean {
    if (!url || url.length === 0) return false
    if (!url.toLowerCase().startsWith('https://')) return false
    const lower = url.toLowerCase()
    return lower.indexOf('clouddn.com') < 0 && lower.indexOf('qiniucdn.com') < 0
  }

  private async signAssetForList(part: MediaAssetPart): Promise<MediaAssetPart> {
    let thumbUrl: string | null = null
    try {
      if (part.type === MediaType.VIDEO && part.thumbnailAssetId && part.thumbnailAssetId.length > 0) {
        const res = await this.call(() =>
          this.api.signAsset(part.thumbnailAssetId as string, { variant: 'original' } as SignAssetRequest)
        )
        thumbUrl = res.url
      } else {
        try {
          const res = await this.call(() =>
            this.api.signAsset(part.assetId, { variant: 'thumb' } as SignAssetRequest)
          )
          thumbUrl = res.url
        } catch (_thumbErr) {
          const res = await this.call(() =>
            this.api.signAsset(part.assetId, { variant: 'original' } as SignAssetRequest)
          )
          thumbUrl = res.url
        }
      }
    } catch (_e) {
      thumbUrl = null
    }
    const copy = this.clonePart(part)
    copy.url = ''
    copy.thumbnailUrl = thumbUrl
    return copy
  }

  private clonePart(part: MediaAssetPart): MediaAssetPart {
    const p = new MediaAssetPart()
    p.id = part.id
    p.type = part.type
    p.assetId = part.assetId
    p.thumbnailAssetId = part.thumbnailAssetId
    p.sortOrder = part.sortOrder
    p.url = part.url
    p.thumbnailUrl = part.thumbnailUrl
    return p
  }

  private cloneMediaItem(item: MediaItem, assets: MediaAssetPart[]): MediaItem {
    const m = new MediaItem()
    m.id = item.id
    m.caption = item.caption
    m.mediaDate = item.mediaDate
    m.uploaderId = item.uploaderId
    m.createdAt = item.createdAt
    m.updatedAt = item.updatedAt
    m.assets = assets
    return m
  }

  async ensureMediaOriginals(item: MediaItem): Promise<MediaItem> {
    const signedAssets = await Promise.all(
      item.assets.map(async (part) => {
        if (part.url) return part
        try {
          const signResult = await this.call(() =>
            this.api.signAsset(part.assetId, { variant: 'original' } as SignAssetRequest)
          )
          const newPart = new MediaAssetPart()
          newPart.id = part.id
          newPart.type = part.type
          newPart.assetId = part.assetId
          newPart.thumbnailAssetId = part.thumbnailAssetId
          newPart.sortOrder = part.sortOrder
          newPart.url = signResult.url
          newPart.thumbnailUrl = part.thumbnailUrl
          return newPart
        } catch (_e) {
          return part
        }
      })
    )
    const result = new MediaItem()
    result.id = item.id
    result.caption = item.caption
    result.mediaDate = item.mediaDate
    result.uploaderId = item.uploaderId
    result.createdAt = item.createdAt
    result.updatedAt = item.updatedAt
    result.assets = signedAssets
    return result
  }

  async signAssetOriginalUrl(assetId: string): Promise<string> {
    const result = await this.call(() =>
      this.api.signAsset(assetId, { variant: 'original' } as SignAssetRequest)
    )
    return result.url
  }

  async addMediaBatch(
    fileDataList: Array<MediaFileInput>,
    caption: string,
    date: string,
    onProgress?: (completed: number, total: number, fraction: number, phase: string) => void,
  ): Promise<void> {
    if (fileDataList.length === 0) {
      throw new BackendException('VALIDATION', '请选择至少一张照片或视频')
    }
    const total = fileDataList.length
    this.notifyProgress(onProgress, 0, total, 0, '准备上传')
    const assets: CreateMediaAssetRequest[] = []

    for (let i = 0; i < total; i++) {
      const file = fileDataList[i]
      this.notifyProgress(onProgress, i, total, 0, '读取文件')

      let thumbnailAssetId: string | null = null
      if (file.isVideo) {
        this.notifyProgress(onProgress, i, total, 0.05, '生成封面')
        thumbnailAssetId = await this.tryUploadVideoThumbnail(file)
      }

      this.notifyProgress(onProgress, i, total, 0.1, '上传媒体')
      const progressCb = onProgress
      const assetId = await this.uploadAsset(
        file.fileName,
        file.mimeType,
        file.data.byteLength,
        file.data,
        'media',
        (fraction: number) => {
          this.notifyProgress(progressCb, i, total, 0.1 + fraction * 0.85, '上传媒体')
        },
      )
      this.notifyProgress(onProgress, i + 1, total, 1, '本项已完成')

      const asset: CreateMediaAssetRequest = {
        type: file.isVideo ? MediaType.VIDEO : MediaType.IMAGE,
        assetId: assetId,
        thumbnailAssetId: thumbnailAssetId,
      }
      assets.push(asset)
    }

    this.notifyProgress(onProgress, total, total, 1, '正在保存')
    const req: CreateMediaRequest = {
      caption: caption.trim(),
      mediaDate: date,
      assets: assets,
    }
    await this.call(() => this.api.createMedia(req))
    await this.refreshMedia()
  }

  private notifyProgress(
    cb: ((completed: number, total: number, fraction: number, phase: string) => void) | undefined,
    completed: number,
    total: number,
    fraction: number,
    phase: string,
  ): void {
    if (cb !== undefined) {
      cb(completed, total, fraction, phase)
    }
  }

  async updateMedia(
    id: string, caption: string, date: string | null,
    assetOrder: MediaAssetOrderItem[] | null = null,
  ): Promise<void> {
    await this.call(() => this.api.updateMedia(id, {
      caption: caption.trim(),
      mediaDate: date,
      addAssets: null,
      removeAssetPartIds: null,
      assetOrder,
    } as UpdateMediaRequest))
    await this.refreshMedia()
  }

  async deleteMedia(id: string): Promise<void> {
    await this.call(() => this.api.deleteMedia(id))
    // Drop local cache immediately even if list refresh fails
    this.tokenStore.update((s) => {
      const next = s.clone()
      next.media = s.media.filter((m) => m.id !== id)
      return next
    })
    try {
      await this.refreshMedia()
    } catch (_e) {
      // delete already succeeded
    }
  }

  // ─── Anniversaries ──────────────────────────────────

  async addAnniversary(title: string, date: string, type: AnniversaryType): Promise<void> {
    await this.call(() => this.api.createAnniversary({
      title: title.trim(), date, type, coverAssetId: null,
    } as CreateAnniversaryRequest))
    await this.refreshAll()
  }

  async updateAnniversary(id: string, title: string, date: string, type: AnniversaryType): Promise<void> {
    await this.call(() => this.api.updateAnniversary(id, {
      title: title.trim(), date, type,
    } as UpdateAnniversaryRequest))
    await this.refreshAll()
  }

  async deleteAnniversary(id: string): Promise<void> {
    await this.call(() => this.api.deleteAnniversary(id))
    await this.refreshAll()
  }

  // ─── Letters ────────────────────────────────────────

  async addLetter(
    title: string, content: string, type: LetterType,
    unlockDate: string | null, unlockOnPartnerBind: boolean = false,
  ): Promise<void> {
    let unlockAt: string | null = null
    if (type === LetterType.CAPSULE && !unlockOnPartnerBind && unlockDate) {
      unlockAt = this.localMidnightWithOffset(unlockDate)
    }
    await this.call(() => this.api.createLetter({
      title: title.trim(), content: content.trim(), type, unlockAt, unlockOnPartnerBind,
    } as CreateLetterRequest))
    await this.refreshAll()
  }

  async deleteLetter(id: string): Promise<void> {
    await this.call(() => this.api.deleteLetter(id))
    await this.refreshAll()
  }

  // ─── Unbinding ──────────────────────────────────────

  async requestUnbinding(reason: string | null): Promise<void> {
    await this.call(() => this.api.requestUnbinding({
      reason: reason?.trim() || null,
    } as CreateUnbindingRequest))
    await this.refreshAll()
  }

  async confirmUnbinding(id: string): Promise<void> {
    await this.call(() => this.api.confirmUnbinding(id))
    await this.refreshAll()
  }

  async cancelUnbinding(id: string): Promise<void> {
    await this.call(() => this.api.cancelUnbinding(id))
    await this.refreshAll()
  }

  async logout(): Promise<void> {
    try { await this.api.logout() } catch (_e) { /* ignore */ }
    this.mediaCursor = null
    this.mediaYearFilter = null
    this.mediaHasMore = false
    this.mediaYears = []
    this.mediaUnreadCount = 0
    await this.tokenStore.clearSession()
  }

  // ─── Activity ───────────────────────────────────────

  async fetchUnreadPartnerActivity(): Promise<PartnerActivityEvent[]> {
    if (!this.tokenStore.snapshot.linked) return []
    try {
      const page = await this.api.partnerActivity(true, 20)
      return page.items
    } catch (_e) {
      return []
    }
  }

  async markPartnerActivityRead(ids: string[] | null = null, all: boolean = false): Promise<void> {
    if (!ids?.length && !all) return
    try {
      await this.api.markActivityRead({ ids, all: all || null } as MarkActivityReadRequest)
    } catch (_e) { /* ignore */ }
  }

  // ─── Private Helpers ────────────────────────────────

  private async uploadAsset(
    fileName: string, mimeType: string, sizeBytes: number,
    fileData: ArrayBuffer, purpose: string = 'media',
    onBytes?: (fraction: number) => void,
  ): Promise<string> {
    const token = await this.call(() =>
      this.api.assetToken({ fileName, mimeType, sizeBytes, purpose } as TokenAssetRequest)
    )
    await this.uploader.upload(token, fileName, fileData, mimeType, onBytes)
    await this.call(() => this.api.completeAsset({ assetId: token.assetId } as AssetRequest))
    return token.assetId
  }

  private sortByDate(items: MediaItem[]): MediaItem[] {
    return items.sort((a, b) => {
      const dateCmp = b.mediaDate.localeCompare(a.mediaDate)
      if (dateCmp !== 0) return dateCmp
      return b.createdAt.localeCompare(a.createdAt)
    })
  }

  private localMidnightWithOffset(date: string): string {
    const d = new Date(`${date}T00:00:00`)
    const offsetMin = -d.getTimezoneOffset()
    const sign = offsetMin >= 0 ? '+' : '-'
    const abs = Math.abs(offsetMin)
    const oh = String(Math.floor(abs / 60)).padStart(2, '0')
    const om = String(abs % 60).padStart(2, '0')
    const y = d.getFullYear()
    const m = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    return `${y}-${m}-${day}T00:00:00${sign}${oh}:${om}`
  }

  /**
   * Upload pre-extracted video cover JPEG when available.
   * Prefer no thumbnail over failing the whole media create.
   */
  private async tryUploadVideoThumbnail(file: MediaFileInput): Promise<string | null> {
    try {
      if (!file.isVideo) {
        return null
      }
      if (!file.thumbData || file.thumbData.byteLength === 0) {
        return null
      }
      const name = file.thumbFileName.length > 0
        ? file.thumbFileName
        : `${file.fileName}-thumbnail.jpg`
      return await this.uploadAsset(
        name,
        'image/jpeg',
        file.thumbData.byteLength,
        file.thumbData,
        'media',
      )
    } catch (_e) {
      return null
    }
  }

  private async call<T>(block: () => Promise<T>): Promise<T> {
    try {
      return await block()
    } catch (error) {
      const err = error instanceof Error ? error : new Error(String(error))
      const facing = toUserFacing(err)
      if (facing.code === 'UNAUTHORIZED' || facing.code === 'HTTP_401') {
        await this.tokenStore.clearSession()
      }
      return Promise.reject(facing)
    }
  }
}
