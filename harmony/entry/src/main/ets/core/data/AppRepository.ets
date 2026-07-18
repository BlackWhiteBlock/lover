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
  UserLookupResponse, AcceptBindResponse, BootstrapResponse,
  CreateMediaAssetRequest, MediaAssetOrderItem,
  IncomingBindRequest, OutgoingBindRequest,
  PartnerActivityEvent, JsonMap, DailyQuote, MediaFileInput,
} from '../model/Models'
import type {
  SendSmsRequest, LoginRequest, OnboardingRequest,
  CreateBindRequest, UpdateCoupleLinkRequest, UpdateCoupleSpaceRequest,
  TokenAssetRequest, AssetRequest, SignAssetRequest,
  CreateMediaRequest, UpdateMediaRequest,
  CreateAnniversaryRequest, UpdateAnniversaryRequest,
  CreateLetterRequest, CreateUnbindingRequest,
  PatchMeRequest, MarkActivityReadRequest,
} from '../model/Models'

export class AppRepository {
  private api: ApiService
  private uploader: AssetUploader
  private tokenStore: TokenStore
  private mediaCursor: string | null = null
  private loadingMore: boolean = false

  constructor(api: ApiService, uploader: AssetUploader, tokenStore: TokenStore) {
    this.api = api
    this.uploader = uploader
    this.tokenStore = tokenStore
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
    await this.tokenStore.saveTokens(auth.accessToken, auth.refreshToken)
    try {
      const me = await this.call(() => this.api.me())
      this.applyMe(me, true)
      if (this.tokenStore.snapshot.profileCompleted) {
        await this.refreshAll()
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
        await this.refreshAll()
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

  async refreshAll(): Promise<void> {
    const previous = this.tokenStore.snapshot
    let meResult: MeResponse | null = null
    let bootstrapResult: BootstrapResponse | null = null
    let coupleBase: CoupleSpace | null = null
    let mediaItems: MediaItem[] = [...previous.media]
    let anniversariesList: Anniversary[] = []
    let lettersList: Letter[] = []

    // Parallel fetch with individual error handling
    const results = await Promise.allSettled([
      this.api.me(),
      this.api.bootstrap(),
      this.api.coupleSpace(),
      this.api.media(undefined, 100),
      this.api.anniversaries(),
      this.api.letters(),
    ])

    if (results[0].status === 'fulfilled') meResult = results[0].value
    if (results[1].status === 'fulfilled') bootstrapResult = results[1].value
    if (results[2].status === 'fulfilled') coupleBase = CoupleSpace.fromJson(results[2].value as JsonMap)
    if (results[3].status === 'fulfilled') {
      const page = results[3].value
      this.mediaCursor = page.nextCursor
      const raw = page.items.map((m) => MediaItem.fromJson(m as JsonMap))
      mediaItems = await this.signMedia(this.sortByDate(raw), previous.media)
    }
    if (results[4].status === 'fulfilled') {
      anniversariesList = results[4].value.items.map((a) => Anniversary.fromJson(a as JsonMap))
    }
    if (results[5].status === 'fulfilled') {
      lettersList = results[5].value.items.map((l) => Letter.fromJson(l as JsonMap))
    }

    if (!meResult && !bootstrapResult && !coupleBase) {
      throw new BackendException('REFRESH_FAILED', '刷新失败，请检查网络后重试')
    }

    let lovingDays = previous.lovingDays
    let needsTogetherDate = previous.needsTogetherDate
    let dailyQuote = previous.dailyQuote
    let coupleLinkId = previous.coupleLinkId
    let linked = previous.linked

    if (bootstrapResult) {
      const journey = bootstrapResult.lovingJourney
      lovingDays = (journey.days as number) ?? lovingDays
      needsTogetherDate = (journey.needsTogetherDate as boolean) ?? needsTogetherDate
      if (bootstrapResult.dailyQuote) {
        dailyQuote = DailyQuote.fromJson(bootstrapResult.dailyQuote)
      }
      coupleLinkId = bootstrapResult.coupleLinkId ?? coupleLinkId
      linked = bootstrapResult.linked || linked
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
      s.anniversaries = anniversariesList
      s.letters = lettersList
      s.sessionReady = true
      return s
    })
  }

  // ─── Media ──────────────────────────────────────────

  async refreshMedia(): Promise<void> {
    const page = await this.call(() => this.api.media(null, 30))
    this.mediaCursor = page.nextCursor
    const previous = this.tokenStore.snapshot.media
    const items = page.items.map((m) => MediaItem.fromJson(m as JsonMap))
    const signed = await this.signMedia(this.sortByDate(items), previous)
    this.tokenStore.update((s) => { s.media = signed; return s })
  }

  async loadMoreMedia(): Promise<boolean> {
    if (!this.mediaCursor || this.loadingMore) return false
    this.loadingMore = true
    try {
      const page = await this.call(() => this.api.media(this.mediaCursor, 30))
      this.mediaCursor = page.nextCursor
      if (page.items.length === 0) return false
      const newItems = page.items.map((m) => MediaItem.fromJson(m as JsonMap))
      const previous = this.tokenStore.snapshot.media
      const signed = await this.signMedia(newItems, previous)
      const merged = [...previous, ...signed]
      this.tokenStore.update((s) => { s.media = this.sortByDate(merged); return s })
      return true
    } finally {
      this.loadingMore = false
    }
  }

  /** List thumbs: sign CDN URLs (API returns assetId only). Mirror Android signMedia. */
  private async signMedia(items: MediaItem[], previous: MediaItem[] = []): Promise<MediaItem[]> {
    const prevById = new Map<string, MediaItem>()
    for (const p of previous) {
      prevById.set(p.id, p)
    }
    const signed: MediaItem[] = []
    for (const item of items) {
      const prev = prevById.get(item.id)
      if (prev && this.canReuseSignedThumbs(item, prev)) {
        signed.push(this.mergeSignedThumbs(item, prev))
        continue
      }
      const assets: MediaAssetPart[] = []
      for (const part of item.assets) {
        assets.push(await this.signAssetForList(part))
      }
      signed.push(this.cloneMediaItem(item, assets))
    }
    return signed
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
