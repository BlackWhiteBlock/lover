/**
 * Token & app state persistent storage.
 * Mirror of Android's TokenStore.kt (DataStore-based).
 * Uses @ohos.data.preferences for persistence.
 */
import { common } from '@kit.AbilityKit'
import { preferences } from '@kit.ArkData'
import { AppState, User, CoupleSpace, MediaItem, Anniversary, Letter, DailyQuote, IncomingBindRequest, OutgoingBindRequest, MediaAssetPart, JsonMap } from '../model/Models'

const PREF_NAME = 'lover_session'
const STATE_KEY = 'app_state_v2'

export class TokenStore {
  private _state: AppState = new AppState()
  private prefs: preferences.Preferences | null = null
  private context: common.UIAbilityContext | null = null

  get state(): AppState {
    return this._state
  }

  get snapshot(): AppState {
    return this._state
  }

  init(context: common.UIAbilityContext): void {
    this.context = context
    this.prefs = preferences.getPreferencesSync(context, { name: PREF_NAME })
    const raw = this.prefs.getSync(STATE_KEY, '') as string
    if (raw) {
      try {
        const parsed = JSON.parse(raw) as JsonMap
        this._state = this.deserializeState(parsed)
      } catch (_e) {
        this._state = new AppState()
      }
    }
    this._state.sessionLoaded = true
  }

  update(transform: (state: AppState) => AppState): void {
    const next = transform(this._state)
    next.loading = false
    next.sessionLoaded = true
    this._state = next
    this.persist()
  }

  async saveTokens(accessToken: string, refreshToken: string): Promise<void> {
    this.update((s) => {
      s.accessToken = accessToken
      s.refreshToken = refreshToken
      s.sessionReady = false
      return s
    })
  }

  async clearSession(): Promise<void> {
    this._state = new AppState()
    this._state.sessionLoaded = true
    this.persist()
  }

  private persist(): void {
    if (!this.prefs) return
    const toSave = this.withoutEphemeralSignedUrls(this._state)
    const raw = JSON.stringify(this.serializeState(toSave))
    this.prefs.putSync(STATE_KEY, raw)
    this.prefs.flush()
  }

  private withoutEphemeralSignedUrls(state: AppState): AppState {
    const cloned = state.clone()
    cloned.media = cloned.media.map((item) => {
      const newItem = new MediaItem()
      newItem.id = item.id
      newItem.caption = item.caption
      newItem.mediaDate = item.mediaDate
      newItem.uploaderId = item.uploaderId
      newItem.createdAt = item.createdAt
      newItem.updatedAt = item.updatedAt
      newItem.assets = item.assets.map((part) => {
        const np = new MediaAssetPart()
        np.id = part.id
        np.type = part.type
        np.assetId = part.assetId
        np.thumbnailAssetId = part.thumbnailAssetId
        np.sortOrder = part.sortOrder
        np.url = ''
        np.thumbnailUrl = null
        return np
      })
      return newItem
    })
    return cloned
  }

  private serializeState(state: AppState): JsonMap {
    const userJson: JsonMap | null = state.user ? JSON.parse(JSON.stringify(state.user)) as JsonMap : null
    const coupleJson: JsonMap | null = state.couple ? JSON.parse(JSON.stringify(state.couple)) as JsonMap : null
    const incomingBindsJson: JsonMap[] = state.pendingIncomingBinds.map((b) => JSON.parse(JSON.stringify(b)) as JsonMap)
    const outgoingBindJson: JsonMap | null = state.pendingOutgoingBind ? JSON.parse(JSON.stringify(state.pendingOutgoingBind)) as JsonMap : null
    const dailyQuoteJson: JsonMap | null = state.dailyQuote ? JSON.parse(JSON.stringify(state.dailyQuote)) as JsonMap : null
    const mediaJson: JsonMap[] = state.media.map((m) => JSON.parse(JSON.stringify(m)) as JsonMap)
    const annivJson: JsonMap[] = state.anniversaries.map((a) => JSON.parse(JSON.stringify(a)) as JsonMap)
    const lettersJson: JsonMap[] = state.letters.map((l) => JSON.parse(JSON.stringify(l)) as JsonMap)
    const result: JsonMap = {}
    result.accessToken = state.accessToken
    result.refreshToken = state.refreshToken
    result.user = userJson
    result.activeSpaceId = state.activeSpaceId
    result.personalSpaceId = state.personalSpaceId
    result.loverSpaceId = state.loverSpaceId
    result.profileCompleted = state.profileCompleted
    result.linked = state.linked
    result.coupleLinkId = state.coupleLinkId
    result.couple = coupleJson
    result.pendingIncomingBinds = incomingBindsJson
    result.pendingOutgoingBind = outgoingBindJson
    result.lovingDays = state.lovingDays
    result.needsTogetherDate = state.needsTogetherDate
    result.dailyQuote = dailyQuoteJson
    result.media = mediaJson
    result.anniversaries = annivJson
    result.letters = lettersJson
    result.coupleThemeRevealShownLinkId = state.coupleThemeRevealShownLinkId
    return result
  }

  private deserializeState(raw: JsonMap): AppState {
    const s = new AppState()
    s.accessToken = (raw.accessToken as string | null) ?? null
    s.refreshToken = (raw.refreshToken as string | null) ?? null
    if (raw.user) {
      s.user = User.fromJson(raw.user)
    }
    s.activeSpaceId = (raw.activeSpaceId as string | null) ?? null
    s.personalSpaceId = (raw.personalSpaceId as string | null) ?? null
    s.loverSpaceId = (raw.loverSpaceId as string | null) ?? null
    s.profileCompleted = (raw.profileCompleted as boolean) ?? false
    s.linked = (raw.linked as boolean) ?? false
    s.coupleLinkId = (raw.coupleLinkId as string | null) ?? null
    if (raw.couple) {
      s.couple = CoupleSpace.fromJson(raw.couple)
    }
    const incomingArr = (raw.pendingIncomingBinds as JsonMap[]) ?? []
    s.pendingIncomingBinds = incomingArr.map((r: JsonMap) => IncomingBindRequest.fromJson(r))
    if (raw.pendingOutgoingBind) {
      s.pendingOutgoingBind = OutgoingBindRequest.fromJson(raw.pendingOutgoingBind)
    }
    s.lovingDays = (raw.lovingDays as number | null) ?? null
    s.needsTogetherDate = (raw.needsTogetherDate as boolean) ?? false
    if (raw.dailyQuote) {
      s.dailyQuote = DailyQuote.fromJson(raw.dailyQuote)
    }
    const mediaArr = (raw.media as JsonMap[]) ?? []
    s.media = mediaArr.map((m: JsonMap) => MediaItem.fromJson(m))
    const annivArr = (raw.anniversaries as JsonMap[]) ?? []
    s.anniversaries = annivArr.map((a: JsonMap) => Anniversary.fromJson(a))
    const lettersArr = (raw.letters as JsonMap[]) ?? []
    s.letters = lettersArr.map((l: JsonMap) => Letter.fromJson(l))
    s.coupleThemeRevealShownLinkId = (raw.coupleThemeRevealShownLinkId as string | null) ?? null
    return s
  }
}
