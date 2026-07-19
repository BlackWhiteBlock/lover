/**
 * Data models mirroring the Android Models.kt.
 * All models use plain classes/interfaces compatible with ArkTS.
 * JSON serialization uses ArkTS built-in JSON.parse / JSON.stringify.
 */

// ArkTS-compatible JSON object type for dynamic JSON access via dot notation.
// All fields are optional to accommodate various JSON structures.
export interface JsonObject {
  // User fields
  id?: string | null
  phone?: string | null
  nickname?: string | null
  avatarUrl?: string | null
  coupleCoverUrl?: string | null
  gender?: string | null
  birthday?: string | null
  profileCompleted?: boolean | null
  personalSpaceId?: string | null
  createdAt?: string | null
  
  // CoupleMember fields
  joinedAt?: string | null
  
  // Bind request fields
  requesterId?: string | null
  requesterNickname?: string | null
  requesterPhone?: string | null
  requesterAvatarUrl?: string | null
  targetUserId?: string | null
  targetNickname?: string | null
  targetPhone?: string | null
  targetAvatarUrl?: string | null
  status?: string | number | null
  expiresAt?: string | null
  
  // Anniversary fields
  title?: string | null
  date?: string | null
  type?: string | null
  coverAssetId?: string | null
  createdBy?: string | null
  countdown?: JsonObject | null
  
  // MediaItem fields
  mediaType?: string | null
  assetId?: string | null
  caption?: string | null
  width?: number | null
  height?: number | null
  duration?: number | null
  order?: number | null
  spaceId?: string | null
  parts?: JsonObject[] | null
  
  // Letter fields
  content?: string | null
  letterType?: string | null
  scheduledAt?: string | null
  openedAt?: string | null
  
  // CoupleSpace fields
  code?: string | null
  inviteLink?: string | null
  coupleLink?: JsonObject | null
  coupleSpace?: JsonObject | null
  members?: JsonObject[] | null
  media?: JsonObject[] | null
  anniversaries?: JsonObject[] | null
  letters?: JsonObject[] | null
  dailyQuote?: JsonObject | null
  
  // DailyQuote fields
  quote?: string | null
  author?: string | null
  
  // Unbinding fields
  text?: string | null
  targetId?: string | null
  targetType?: string | null
  
  // Token fields
  refreshToken?: string | null
  accessToken?: string | null
  tokenType?: string | null
  expiresIn?: string | null
  
  // Countdown fields
  days?: number | null
  nextDate?: string | null
  reached?: boolean | null
  
  // CoupleLink fields
  name?: string | null
  togetherDate?: string | null
  needsTogetherDate?: boolean | null
  loverSpaceId?: string | null
  coupleLinkId?: string | null
  
  // UserLookup fields
  found?: boolean | null
  self?: boolean | null
  
  // Auth fields
  isNewUser?: boolean | null
  user?: JsonObject | null
  
  // SMS fields
  ok?: boolean | null
  cooldownSeconds?: number | null
  devCode?: string | null
  
  // Bootstrap fields
  lovingJourney?: JsonObject | null
  recentMedia?: JsonObject[] | null
  linked?: boolean | null
  
  // ItemPage fields
  items?: JsonObject[] | null
  nextCursor?: string | null
  
  // Asset fields
  provider?: string | null
  uploadToken?: string | null
  uploadUrl?: string | null
  objectKey?: string | null
  uploadFields?: JsonObject | null
  sizeBytes?: number | null
  
  // Sign fields
  variant?: string | null
  url?: string | null
  
  // Patch fields
  avatarAssetId?: string | null
  coupleCoverAssetId?: string | null
  clearCoupleCover?: boolean | null
  
  // Media fields
  thumbnailAssetId?: string | null
  mediaDate?: string | null
  assets?: JsonObject[] | null
  addAssets?: JsonObject[] | null
  removeAssetPartIds?: string[] | null
  assetOrder?: JsonObject[] | null
  
  // Letter fields
  unlockAt?: string | null
  unlockOnPartnerBind?: boolean | null
  
  // Unbinding fields
  reason?: string | null
  
  // Error fields (code already declared above)
  
  // Activity fields
  unreadCount?: number | null
  marked?: number | null
  incoming?: JsonObject[] | null
  outgoing?: JsonObject[] | null
  
  // MediaAssetPart fields (url already declared above)
  sortOrder?: number | null
  thumbnailUrl?: string | null
  
  // MediaItem fields
  uploaderId?: string | null
  updatedAt?: string | null
  
  // Letter fields
  senderId?: string | null
  senderNickname?: string | null
  summary?: string | null
  isUnlocked?: boolean | null
  isOpened?: boolean | null
  deliveryStatus?: string | null
  
  // UnbindingRequest fields
  requestedBy?: string | null
  
  // CoupleSpace fields
  kind?: string | null
  pendingUnbinding?: JsonObject | null
  pendingIncomingBinds?: JsonObject[] | null
  pendingOutgoingBind?: JsonObject | null
  
  // PartnerActivityEvent fields
  entityType?: string | null
  entityId?: string | null
  actorNickname?: string | null
  readAt?: string | null

  // Error / HTTP interceptor fields
  response?: JsonObject | null
  config?: JsonObject | null
  data?: JsonObject | null
  headers?: Record<string, string> | null
  error?: JsonObject | null
  message?: string | null

  // Session / serialization fields (only truly new fields not declared above)
  activeSpaceId?: string | null
  coupleThemeRevealShownLinkId?: string | null
  lovingDays?: number | null
  couple?: JsonObject | null
  key?: string | null
  value?: string | null
  mediaId?: string | null
  letterId?: string | null
}

// Alias for JsonObject — used in fromJson signatures and API response types
export type JsonMap = JsonObject

// ─── Enums ──────────────────────────────────────────────

export enum MediaType {
  IMAGE = 'image',
  VIDEO = 'video',
}

export enum AnniversaryType {
  MILESTONE = 'milestone',
  YEARLY = 'yearly',
}

export enum LetterType {
  INSTANT = 'instant',
  CAPSULE = 'capsule',
}

// ─── Core Domain Models ─────────────────────────────────

export class User {
  id: string = ''
  phone: string = ''
  nickname: string = '我'
  avatarUrl: string | null = null
  coupleCoverUrl: string | null = null
  gender: string | null = null
  birthday: string | null = null
  profileCompleted: boolean = false
  personalSpaceId: string | null = null
  createdAt: string | null = null

  static fromJson(json: JsonMap): User {
    const obj = json as JsonObject
    const u = new User()
    u.id = (obj.id as string) ?? ''
    u.phone = (obj.phone as string) ?? ''
    u.nickname = (obj.nickname as string) ?? '我'
    u.avatarUrl = (obj.avatarUrl as string | null) ?? null
    u.coupleCoverUrl = (obj.coupleCoverUrl as string | null) ?? null
    u.gender = (obj.gender as string | null) ?? null
    u.birthday = (obj.birthday as string | null) ?? null
    u.profileCompleted = (obj.profileCompleted as boolean) ?? false
    u.personalSpaceId = (obj.personalSpaceId as string | null) ?? null
    u.createdAt = (obj.createdAt as string | null) ?? null
    return u
  }
}

export class CoupleMember {
  id: string = ''
  nickname: string = ''
  avatarUrl: string | null = null
  joinedAt: string | null = null

  static fromJson(json: JsonMap): CoupleMember {
    const obj = json as JsonObject
    const m = new CoupleMember()
    m.id = (obj.id as string) ?? ''
    m.nickname = (obj.nickname as string) ?? ''
    m.avatarUrl = (obj.avatarUrl as string | null) ?? null
    m.joinedAt = (obj.joinedAt as string | null) ?? null
    return m
  }
}

export class IncomingBindRequest {
  id: string = ''
  requesterId: string = ''
  requesterNickname: string = ''
  requesterPhone: string = ''
  requesterAvatarUrl: string | null = null
  status: string = 'pending'
  expiresAt: string | null = null
  createdAt: string | null = null

  static fromJson(json: JsonMap): IncomingBindRequest {
    const obj = json as JsonObject
    const r = new IncomingBindRequest()
    r.id = (obj.id as string) ?? ''
    r.requesterId = (obj.requesterId as string) ?? ''
    r.requesterNickname = (obj.requesterNickname as string) ?? ''
    r.requesterPhone = (obj.requesterPhone as string) ?? ''
    r.requesterAvatarUrl = (obj.requesterAvatarUrl as string | null) ?? null
    r.status = (obj.status as string) ?? 'pending'
    r.expiresAt = (obj.expiresAt as string | null) ?? null
    r.createdAt = (obj.createdAt as string | null) ?? null
    return r
  }
}

export class OutgoingBindRequest {
  id: string = ''
  targetUserId: string = ''
  targetNickname: string = ''
  targetPhone: string = ''
  targetAvatarUrl: string | null = null
  status: string = 'pending'
  expiresAt: string | null = null
  createdAt: string | null = null

  static fromJson(json: JsonMap): OutgoingBindRequest {
    const obj = json as JsonObject
    const r = new OutgoingBindRequest()
    r.id = (obj.id as string) ?? ''
    r.targetUserId = (obj.targetUserId as string) ?? ''
    r.targetNickname = (obj.targetNickname as string) ?? ''
    r.targetPhone = (obj.targetPhone as string) ?? ''
    r.targetAvatarUrl = (obj.targetAvatarUrl as string | null) ?? null
    r.status = (obj.status as string) ?? 'pending'
    r.expiresAt = (obj.expiresAt as string | null) ?? null
    r.createdAt = (obj.createdAt as string | null) ?? null
    return r
  }
}

export class Countdown {
  days: number = 0
  nextDate: string | null = null
  reached: boolean = false

  static fromJson(json: JsonMap): Countdown {
    const obj = json as JsonObject
    const c = new Countdown()
    c.days = (obj.days as number) ?? 0
    c.nextDate = (obj.nextDate as string | null) ?? null
    c.reached = (obj.reached as boolean) ?? false
    return c
  }
}

export class Anniversary {
  id: string = ''
  title: string = ''
  date: string = ''
  type: AnniversaryType = AnniversaryType.MILESTONE
  coverAssetId: string | null = null
  createdBy: string | null = null
  createdAt: string = ''
  countdown: Countdown | null = null

  static fromJson(json: JsonMap): Anniversary {
    const obj = json as JsonObject
    const a = new Anniversary()
    a.id = (obj.id as string) ?? ''
    a.title = (obj.title as string) ?? ''
    a.date = (obj.date as string) ?? ''
    a.type = (obj.type as string) as AnniversaryType ?? AnniversaryType.MILESTONE
    a.coverAssetId = (obj.coverAssetId as string | null) ?? null
    a.createdBy = (obj.createdBy as string | null) ?? null
    a.createdAt = (obj.createdAt as string) ?? ''
    if (obj.countdown) {
      a.countdown = Countdown.fromJson(obj.countdown as JsonMap)
    }
    return a
  }
}

export class MediaAssetPart {
  id: string = ''
  type: MediaType = MediaType.IMAGE
  assetId: string = ''
  thumbnailAssetId: string | null = null
  sortOrder: number = 0
  url: string = ''
  thumbnailUrl: string | null = null

  get previewUrl(): string {
    return (this.thumbnailUrl && this.thumbnailUrl.length > 0) ? this.thumbnailUrl : this.url
  }

  static fromJson(json: JsonMap): MediaAssetPart {
    const obj = json as JsonObject
    const p = new MediaAssetPart()
    p.id = (obj.id as string) ?? ''
    p.type = ((obj.type as string) as MediaType) ?? MediaType.IMAGE
    p.assetId = (obj.assetId as string) ?? ''
    p.thumbnailAssetId = (obj.thumbnailAssetId as string | null) ?? null
    p.sortOrder = (obj.sortOrder as number) ?? 0
    p.url = (obj.url as string) ?? ''
    p.thumbnailUrl = (obj.thumbnailUrl as string | null) ?? null
    return p
  }
}

export class MediaItem {
  id: string = ''
  caption: string = ''
  mediaDate: string = ''
  uploaderId: string | null = null
  createdAt: string = ''
  updatedAt: string = ''
  assets: MediaAssetPart[] = []

  get cover(): MediaAssetPart | null {
    if (this.assets.length === 0) return null
    let min = this.assets[0]
    for (let i = 1; i < this.assets.length; i++) {
      if (this.assets[i].sortOrder < min.sortOrder) min = this.assets[i]
    }
    return min
  }

  get type(): MediaType {
    return this.cover?.type ?? MediaType.IMAGE
  }

  get url(): string {
    return this.cover?.url ?? ''
  }

  get thumbnailUrl(): string | null {
    const thumb = this.cover?.thumbnailUrl
    if (thumb && thumb.length > 0) return thumb
    const u = this.cover?.url
    if (u && u.length > 0) return u
    return null
  }

  get assetCount(): number {
    return this.assets.length
  }

  /** Safe for @Prop/@State deep-copied plain objects (getters may be lost). */
  static coverOf(item: MediaItem): MediaAssetPart | null {
    if (!item.assets || item.assets.length === 0) {
      return null
    }
    let min = item.assets[0]
    for (let i = 1; i < item.assets.length; i++) {
      if (item.assets[i].sortOrder < min.sortOrder) {
        min = item.assets[i]
      }
    }
    return min
  }

  static thumbUrlOf(item: MediaItem): string {
    const cover = MediaItem.coverOf(item)
    if (!cover) {
      return ''
    }
    if (cover.thumbnailUrl && cover.thumbnailUrl.length > 0) {
      return cover.thumbnailUrl
    }
    if (cover.url && cover.url.length > 0) {
      return cover.url
    }
    return ''
  }

  static isVideoOf(item: MediaItem): boolean {
    return MediaItem.coverOf(item)?.type === MediaType.VIDEO
  }

  static hasVideoAsset(item: MediaItem): boolean {
    if (!item.assets) {
      return false
    }
    for (let i = 0; i < item.assets.length; i++) {
      if (item.assets[i].type === MediaType.VIDEO) {
        return true
      }
    }
    return false
  }

  static sortedAssets(item: MediaItem): MediaAssetPart[] {
    if (!item.assets || item.assets.length === 0) {
      return []
    }
    const copy: MediaAssetPart[] = []
    for (let i = 0; i < item.assets.length; i++) {
      copy.push(item.assets[i])
    }
    copy.sort((a: MediaAssetPart, b: MediaAssetPart) => a.sortOrder - b.sortOrder)
    return copy
  }

  static partUrl(part: MediaAssetPart | null): string {
    if (!part) {
      return ''
    }
    if (part.thumbnailUrl && part.thumbnailUrl.length > 0) {
      return part.thumbnailUrl
    }
    if (part.url && part.url.length > 0) {
      return part.url
    }
    return ''
  }

  static fromJson(json: JsonMap): MediaItem {
    const obj = json as JsonObject
    const m = new MediaItem()
    m.id = (obj.id as string) ?? ''
    m.caption = (obj.caption as string) ?? ''
    m.mediaDate = (obj.mediaDate as string) ?? ''
    m.uploaderId = (obj.uploaderId as string | null) ?? null
    m.createdAt = (obj.createdAt as string) ?? ''
    m.updatedAt = (obj.updatedAt as string) ?? ''
    const assetsArr = (obj.assets as JsonMap[]) ?? []
    m.assets = assetsArr.map((a) => MediaAssetPart.fromJson(a))
    return m
  }
}

export type LetterDeliveryStatus = 'sent' | 'read'

export class Letter {
  id: string = ''
  senderId: string = ''
  senderNickname: string = ''
  title: string = ''
  content: string | null = null
  summary: string | null = null
  type: LetterType = LetterType.INSTANT
  unlockAt: string | null = null
  unlockOnPartnerBind: boolean = false
  isUnlocked: boolean = false
  /** 接收方是否已拆；发送方恒 true */
  isOpened: boolean = true
  /** 发送方：已寄 / 已阅 */
  deliveryStatus: LetterDeliveryStatus | null = null
  createdAt: string = ''

  static isSealedFor(letter: Letter, viewerId: string | null | undefined): boolean {
    if (!viewerId || viewerId.length === 0) {
      return false
    }
    return letter.senderId !== viewerId && letter.isUnlocked && !letter.isOpened
  }

  static fromJson(json: JsonMap): Letter {
    const obj = json as JsonObject
    const l = new Letter()
    l.id = (obj.id as string) ?? ''
    l.senderId = (obj.senderId as string) ?? ''
    l.senderNickname = (obj.senderNickname as string) ?? ''
    l.title = (obj.title as string) ?? ''
    l.content = (obj.content as string | null) ?? null
    l.summary = (obj.summary as string | null) ?? null
    l.type = ((obj.type as string) as LetterType) ?? LetterType.INSTANT
    l.unlockAt = (obj.unlockAt as string | null) ?? null
    l.unlockOnPartnerBind = (obj.unlockOnPartnerBind as boolean) ?? false
    l.isUnlocked = (obj.isUnlocked as boolean) ?? false
    l.isOpened = (obj.isOpened as boolean) ?? true
    const status = obj.deliveryStatus as string | null | undefined
    if (status === 'sent' || status === 'read') {
      l.deliveryStatus = status
    } else {
      l.deliveryStatus = null
    }
    l.createdAt = (obj.createdAt as string) ?? ''
    return l
  }
}

export class UnbindingRequest {
  id: string = ''
  requestedBy: string | null = null
  status: string = ''
  reason: string | null = null
  createdAt: string = ''

  static fromJson(json: JsonMap): UnbindingRequest {
    const obj = json as JsonObject
    const r = new UnbindingRequest()
    r.id = (obj.id as string) ?? ''
    r.requestedBy = (obj.requestedBy as string | null) ?? null
    r.status = (obj.status as string) ?? ''
    r.reason = (obj.reason as string | null) ?? null
    r.createdAt = (obj.createdAt as string) ?? ''
    return r
  }
}

export class CoupleSpace {
  id: string = ''
  name: string = '我们的小宇宙'
  togetherDate: string | null = null
  status: string = 'active'
  createdAt: string = ''
  kind: string | null = null
  linked: boolean = false
  coupleLinkId: string | null = null
  personalSpaceId: string | null = null
  loverSpaceId: string | null = null
  members: CoupleMember[] = []
  pendingUnbinding: UnbindingRequest | null = null
  pendingIncomingBinds: IncomingBindRequest[] = []
  pendingOutgoingBind: OutgoingBindRequest | null = null

  static fromJson(json: JsonMap): CoupleSpace {
    const obj = json as JsonObject
    const s = new CoupleSpace()
    s.id = (obj.id as string) ?? ''
    s.name = (obj.name as string) ?? '我们的小宇宙'
    s.togetherDate = (obj.togetherDate as string | null) ?? null
    s.status = (obj.status as string) ?? 'active'
    s.createdAt = (obj.createdAt as string) ?? ''
    s.kind = (obj.kind as string | null) ?? null
    s.linked = (obj.linked as boolean) ?? false
    s.coupleLinkId = (obj.coupleLinkId as string | null) ?? null
    s.personalSpaceId = (obj.personalSpaceId as string | null) ?? null
    s.loverSpaceId = (obj.loverSpaceId as string | null) ?? null
    const membersArr = (obj.members as JsonMap[]) ?? []
    s.members = membersArr.map((m) => CoupleMember.fromJson(m))
    if (obj.pendingUnbinding) {
      s.pendingUnbinding = UnbindingRequest.fromJson(obj.pendingUnbinding as JsonMap)
    }
    const incomingArr = (obj.pendingIncomingBinds as JsonMap[]) ?? []
    s.pendingIncomingBinds = incomingArr.map((r) => IncomingBindRequest.fromJson(r))
    if (obj.pendingOutgoingBind) {
      s.pendingOutgoingBind = OutgoingBindRequest.fromJson(obj.pendingOutgoingBind as JsonMap)
    }
    return s
  }
}

export class DailyQuote {
  text: string = ''
  author: string | null = null
  date: string | null = null

  static fromJson(json: JsonMap): DailyQuote {
    const obj = json as JsonObject
    const q = new DailyQuote()
    q.text = (obj.text as string) ?? ''
    q.author = (obj.author as string | null) ?? null
    q.date = (obj.date as string | null) ?? null
    return q
  }
}

export class LovingJourney {
  togetherDate: string | null = null
  days: number | null = null
  needsTogetherDate: boolean = false

  static fromJson(json: JsonMap): LovingJourney {
    const obj = json as JsonObject
    const j = new LovingJourney()
    j.togetherDate = (obj.togetherDate as string | null) ?? null
    j.days = (obj.days as number | null) ?? null
    j.needsTogetherDate = (obj.needsTogetherDate as boolean) ?? false
    return j
  }
}

export class PartnerActivityEvent {
  id: string = ''
  type: string = ''
  entityType: string = ''
  entityId: string = ''
  title: string = ''
  actorNickname: string = ''
  createdAt: string = ''
  readAt: string | null = null

  static fromJson(json: JsonMap): PartnerActivityEvent {
    const obj = json as JsonObject
    const e = new PartnerActivityEvent()
    e.id = (obj.id as string) ?? ''
    e.type = (obj.type as string) ?? ''
    e.entityType = (obj.entityType as string) ?? ''
    e.entityId = (obj.entityId as string) ?? ''
    e.title = (obj.title as string) ?? ''
    e.actorNickname = (obj.actorNickname as string) ?? ''
    e.createdAt = (obj.createdAt as string) ?? ''
    e.readAt = (obj.readAt as string | null) ?? null
    return e
  }
}

// ─── App State ──────────────────────────────────────────

@Observed
export class AppState {
  accessToken: string | null = null
  refreshToken: string | null = null
  user: User | null = null
  activeSpaceId: string | null = null
  personalSpaceId: string | null = null
  loverSpaceId: string | null = null
  profileCompleted: boolean = false
  linked: boolean = false
  coupleLinkId: string | null = null
  couple: CoupleSpace | null = null
  pendingIncomingBinds: IncomingBindRequest[] = []
  pendingOutgoingBind: OutgoingBindRequest | null = null
  lovingDays: number | null = null
  needsTogetherDate: boolean = false
  dailyQuote: DailyQuote | null = null
  media: MediaItem[] = []
  anniversaries: Anniversary[] = []
  letters: Letter[] = []
  loading: boolean = false
  sessionReady: boolean = false
  coupleThemeRevealShownLinkId: string | null = null
  sessionLoaded: boolean = false

  clone(): AppState {
    const s = new AppState()
    s.accessToken = this.accessToken
    s.refreshToken = this.refreshToken
    s.user = this.user
    s.activeSpaceId = this.activeSpaceId
    s.personalSpaceId = this.personalSpaceId
    s.loverSpaceId = this.loverSpaceId
    s.profileCompleted = this.profileCompleted
    s.linked = this.linked
    s.coupleLinkId = this.coupleLinkId
    s.couple = this.couple
    s.pendingIncomingBinds = [...this.pendingIncomingBinds]
    s.pendingOutgoingBind = this.pendingOutgoingBind
    s.lovingDays = this.lovingDays
    s.needsTogetherDate = this.needsTogetherDate
    s.dailyQuote = this.dailyQuote
    s.media = [...this.media]
    s.anniversaries = [...this.anniversaries]
    s.letters = [...this.letters]
    s.loading = this.loading
    s.sessionReady = this.sessionReady
    s.coupleThemeRevealShownLinkId = this.coupleThemeRevealShownLinkId
    s.sessionLoaded = this.sessionLoaded
    return s
  }
}

// ─── API Request / Response DTOs ────────────────────────

export interface SendSmsRequest {
  phone: string
}

export interface SendSmsResponse {
  ok: boolean
  cooldownSeconds: number
  devCode?: string | null
}

export interface LoginRequest {
  phone: string
  code: string
  nickname?: string | null
}

export interface PnvsSdkInfoResponse {
  enabled: boolean
  platform: string
  secretInfo: string
  privacyUrl: string
  termsUrl: string
}

export interface PnvsLoginRequest {
  loginToken: string
  nickname?: string | null
}

export interface AuthResponse {
  user: JsonObject
  isNewUser: boolean
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: string
}

export interface RefreshRequest {
  refreshToken: string
}

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: string
}

export interface OkResponse {
  ok: boolean
}

export interface MeResponse {
  user: JsonObject
  personalSpaceId: string | null
  loverSpaceId: string | null
  activeSpaceId: string | null
  profileCompleted: boolean
  linked: boolean
}

export interface OnboardingRequest {
  nickname: string
  avatarUrl: string | null
  gender: string
  birthday: string
  spaceName: string
}

export interface OnboardingResponse {
  user: JsonObject
  personalSpaceId: string
  spaceName: string
}

export interface UserLookupResponse {
  found: boolean
  self: boolean
  nickname: string | null
  avatarUrl: string | null
}

export interface CreateBindRequest {
  phone: string
}

export interface BindRequestDto {
  id: string
  requesterId: string | null
  targetUserId: string | null
  targetNickname: string | null
  targetPhone: string | null
  targetAvatarUrl: string | null
  status: string
  expiresAt: string | null
  createdAt: string | null
}

export interface AcceptBindResponse {
  coupleLinkId: string
  loverSpaceId: string
  togetherDate: string | null
  needsTogetherDate: boolean
}

export interface UpdateCoupleLinkRequest {
  togetherDate: string | null
  name: string | null
}

export interface CoupleLinkResponse {
  id: string | null
  loverSpaceId: string | null
  togetherDate: string | null
  name: string | null
}

export interface UpdateCoupleSpaceRequest {
  name: string | null
  togetherDate: string | null
}

export interface BootstrapResponse {
  space: JsonObject
  lovingJourney: JsonObject
  recentMedia: JsonObject[]
  dailyQuote: JsonObject | null
  linked: boolean
  coupleLinkId: string | null
}

export interface ItemPage<T> {
  items: T[]
  nextCursor: string | null
}

export interface MediaYearsResponse {
  years: number[]
}

export interface MediaUnreadSummary {
  count: number
  hasUnread: boolean
}

export interface MediaUnreadPage {
  items: MediaItem[]
  nextCursor: string | null
  count: number
}

export interface MarkMediaReadRequest {
  ids: string[] | null
  all: boolean | null
}

export interface TokenAssetRequest {
  fileName: string
  mimeType: string
  sizeBytes: number
  purpose: string
}

export interface TokenAssetResponse {
  assetId: string
  provider: string
  uploadToken: string
  uploadUrl: string
  objectKey: string
  /** Qiniu: map like { key: objectKey }; Android uses Map<String,String>. */
  uploadFields: Record<string, string>
  expiresIn: number
}

export interface AssetRequest {
  assetId: string
}

export interface CompleteAssetResponse {
  assetId: string
  status: string
  sizeBytes: number
}

export interface SignAssetRequest {
  variant: string
}

export interface SignAssetResponse {
  url: string
  expiresIn: number
}

export interface PatchMeRequest {
  avatarAssetId?: string | null
  coupleCoverAssetId?: string | null
  clearCoupleCover?: boolean
}

export interface PatchMeResponse {
  user: JsonObject
}

export interface CreateMediaAssetRequest {
  type: MediaType
  assetId: string
  thumbnailAssetId: string | null
}

export interface CreateMediaRequest {
  caption: string
  mediaDate: string
  assets: CreateMediaAssetRequest[]
}

export interface MediaAssetOrderItem {
  partId?: string | null
  type?: MediaType | null
  assetId?: string | null
  thumbnailAssetId?: string | null
}

export interface UpdateMediaRequest {
  caption: string | null
  mediaDate: string | null
  addAssets: CreateMediaAssetRequest[] | null
  removeAssetPartIds: string[] | null
  assetOrder: MediaAssetOrderItem[] | null
}

export interface CreateAnniversaryRequest {
  title: string
  date: string
  type: AnniversaryType
  coverAssetId: string | null
}

export interface UpdateAnniversaryRequest {
  title: string | null
  date: string | null
  type: AnniversaryType | null
  coverAssetId: string | null
}

export interface CreateLetterRequest {
  title: string
  content: string
  type: LetterType
  unlockAt: string | null
  unlockOnPartnerBind: boolean
}

export interface CreateUnbindingRequest {
  reason: string | null
}

export interface ApiErrorEnvelope {
  error: ApiErrorBody
}

export interface ApiErrorBody {
  code: string
  message: string
}

export interface PartnerActivityPage {
  items: PartnerActivityEvent[]
  unreadCount: number
}

export interface MarkActivityReadRequest {
  ids: string[] | null
  all: boolean | null
}

export interface MarkActivityReadResponse {
  ok: boolean
  marked: number
}

export interface PendingBindsResponse {
  incoming: IncomingBindRequest[]
  outgoing: OutgoingBindRequest[]
}

/** A file selected for upload (media picker result). */
export class MediaFileInput {
  data: ArrayBuffer = new ArrayBuffer(0)
  fileName: string = ''
  mimeType: string = ''
  isVideo: boolean = false
  /** Local sandbox path of original media. */
  cachePath: string = ''
  /** Path for Image() preview — JPEG thumb for video, same as cachePath for image. */
  previewPath: string = ''
  /** Pre-extracted video cover JPEG (optional). */
  thumbData: ArrayBuffer | null = null
  thumbFileName: string = ''
  /** Stable key for ForEach. */
  draftId: string = ''
}
