/**
 * API service layer — all backend endpoints.
 * Mirror of Android's ApiService.kt (Retrofit interface).
 */
import { HttpClient } from './HttpClient'
import type {
  SendSmsRequest, SendSmsResponse, LoginRequest, AuthResponse,
  PnvsSdkInfoResponse, PnvsLoginRequest,
  RefreshRequest, TokenResponse, OkResponse, MeResponse,
  PatchMeRequest, PatchMeResponse, OnboardingRequest, OnboardingResponse,
  UserLookupResponse, CreateBindRequest, BindRequestDto, AcceptBindResponse,
  PendingBindsResponse, UpdateCoupleLinkRequest, CoupleLinkResponse,
  BootstrapResponse, UpdateCoupleSpaceRequest,
  TokenAssetRequest, TokenAssetResponse, AssetRequest, CompleteAssetResponse,
  SignAssetRequest, SignAssetResponse,
  MediaItem, CreateMediaRequest, UpdateMediaRequest,
  Anniversary, CreateAnniversaryRequest, UpdateAnniversaryRequest,
  Letter, CreateLetterRequest,
  CreateUnbindingRequest, UnbindingRequest,
  ItemPage, DailyQuote, PartnerActivityPage, MarkActivityReadRequest,
  MarkActivityReadResponse, CoupleSpace,
  MediaYearsResponse, MediaUnreadSummary, MediaUnreadPage, MarkMediaReadRequest,
} from '../model/Models'

export class ApiService {
  private http: HttpClient

  constructor(http: HttpClient) {
    this.http = http
  }

  // ─── Auth ───────────────────────────────────────────

  async sendSms(request: SendSmsRequest): Promise<SendSmsResponse> {
    return this.http.post<SendSmsResponse>('api/auth/sms/send', request)
  }

  async login(request: LoginRequest): Promise<AuthResponse> {
    return this.http.post<AuthResponse>('api/auth/sms/login', request)
  }

  async pnvsSdkInfo(platform: string): Promise<PnvsSdkInfoResponse> {
    return this.http.get<PnvsSdkInfoResponse>(`api/auth/pnvs/sdk-info?platform=${platform}`)
  }

  async pnvsLogin(request: PnvsLoginRequest): Promise<AuthResponse> {
    return this.http.post<AuthResponse>('api/auth/pnvs/login', request)
  }

  async refresh(request: RefreshRequest): Promise<TokenResponse> {
    return this.http.post<TokenResponse>('api/auth/refresh', request)
  }

  async logout(): Promise<OkResponse> {
    return this.http.post<OkResponse>('api/auth/logout')
  }

  // ─── Me ─────────────────────────────────────────────

  async me(): Promise<MeResponse> {
    return this.http.get<MeResponse>('api/me')
  }

  async patchMe(request: PatchMeRequest): Promise<PatchMeResponse> {
    return this.http.patch<PatchMeResponse>('api/me', request as Object)
  }

  // ─── Onboarding ─────────────────────────────────────

  async onboarding(request: OnboardingRequest): Promise<OnboardingResponse> {
    return this.http.post<OnboardingResponse>('api/onboarding', request as Object)
  }

  // ─── Couple ─────────────────────────────────────────

  async coupleSpace(): Promise<CoupleSpace> {
    return this.http.get<CoupleSpace>('api/couple-space')
  }

  async updateCoupleSpace(request: UpdateCoupleSpaceRequest): Promise<CoupleSpace> {
    return this.http.patch<CoupleSpace>('api/couple-space', request as Object)
  }

  async lookupUser(phone: string): Promise<UserLookupResponse> {
    const params = new Map<string, string>()
    params.set('phone', phone)
    return this.http.get<UserLookupResponse>('api/users/lookup', params)
  }

  async createBind(request: CreateBindRequest): Promise<BindRequestDto> {
    return this.http.post<BindRequestDto>('api/couple-binds', request)
  }

  async pendingBinds(): Promise<PendingBindsResponse> {
    return this.http.get<PendingBindsResponse>('api/couple-binds/pending')
  }

  async acceptBind(id: string): Promise<AcceptBindResponse> {
    return this.http.post<AcceptBindResponse>(`api/couple-binds/${id}/accept`)
  }

  async rejectBind(id: string): Promise<OkResponse> {
    return this.http.post<OkResponse>(`api/couple-binds/${id}/reject`)
  }

  async cancelBind(id: string): Promise<OkResponse> {
    return this.http.post<OkResponse>(`api/couple-binds/${id}/cancel`)
  }

  async updateCoupleLink(request: UpdateCoupleLinkRequest): Promise<CoupleLinkResponse> {
    return this.http.patch<CoupleLinkResponse>('api/couple-link', request as Object)
  }

  // ─── Bootstrap ──────────────────────────────────────

  async bootstrap(): Promise<BootstrapResponse> {
    return this.http.get<BootstrapResponse>('api/bootstrap')
  }

  async dailyQuote(): Promise<DailyQuote> {
    return this.http.get<DailyQuote>('api/daily-quote')
  }

  // ─── Media Assets ───────────────────────────────────

  async assetToken(request: TokenAssetRequest): Promise<TokenAssetResponse> {
    return this.http.post<TokenAssetResponse>('api/media-assets/token', request as Object)
  }

  async completeAsset(request: AssetRequest): Promise<CompleteAssetResponse> {
    return this.http.post<CompleteAssetResponse>('api/media-assets/complete', request)
  }

  async signAsset(assetId: string, request: SignAssetRequest = { variant: 'original' }): Promise<SignAssetResponse> {
    return this.http.post<SignAssetResponse>(`api/media-assets/${assetId}/sign`, request)
  }

  // ─── Media ──────────────────────────────────────────

  async media(
    cursor: string | null = null,
    limit: number = 30,
    year: number | null = null,
  ): Promise<ItemPage<MediaItem>> {
    const params = new Map<string, string>()
    params.set('limit', limit.toString())
    if (cursor) {
      params.set('cursor', cursor)
    }
    if (year !== null && year !== undefined) {
      params.set('year', year.toString())
    }
    return this.http.get<ItemPage<MediaItem>>('api/media', params)
  }

  async mediaYears(): Promise<MediaYearsResponse> {
    return this.http.get<MediaYearsResponse>('api/media/years')
  }

  async mediaUnreadSummary(): Promise<MediaUnreadSummary> {
    return this.http.get<MediaUnreadSummary>('api/media/unread-summary')
  }

  async mediaUnread(
    cursor: string | null = null,
    limit: number = 30,
  ): Promise<MediaUnreadPage> {
    const params = new Map<string, string>()
    params.set('limit', limit.toString())
    if (cursor) {
      params.set('cursor', cursor)
    }
    return this.http.get<MediaUnreadPage>('api/media/unread', params)
  }

  async markMediaRead(id: string): Promise<MediaUnreadSummary> {
    // 显式空对象：避免 `{}` 字面量触发 arkts-no-untyped-obj-literals，
    // 同时保证 POST 带合法 JSON body（避免 Fastify empty JSON body 报错）
    const emptyBody: MarkMediaReadRequest = { ids: null, all: null }
    return this.http.post<MediaUnreadSummary>(`api/media/${id}/read`, emptyBody)
  }

  async markMediaReadBatch(request: MarkMediaReadRequest): Promise<MediaUnreadSummary> {
    return this.http.post<MediaUnreadSummary>('api/media/read', request as Object)
  }

  async createMedia(request: CreateMediaRequest): Promise<MediaItem> {
    return this.http.post<MediaItem>('api/media', request as Object)
  }

  async mediaDetail(id: string): Promise<MediaItem> {
    return this.http.get<MediaItem>(`api/media/${id}`)
  }

  async updateMedia(id: string, request: UpdateMediaRequest): Promise<MediaItem> {
    return this.http.patch<MediaItem>(`api/media/${id}`, request as Object)
  }

  async deleteMedia(id: string): Promise<OkResponse> {
    return this.http.delete<OkResponse>(`api/media/${id}`)
  }

  // ─── Anniversaries ──────────────────────────────────

  async createAnniversary(request: CreateAnniversaryRequest): Promise<Anniversary> {
    return this.http.post<Anniversary>('api/anniversaries', request as Object)
  }

  async anniversaries(): Promise<ItemPage<Anniversary>> {
    return this.http.get<ItemPage<Anniversary>>('api/anniversaries')
  }

  async anniversaryDetail(id: string): Promise<Anniversary> {
    return this.http.get<Anniversary>(`api/anniversaries/${id}`)
  }

  async updateAnniversary(id: string, request: UpdateAnniversaryRequest): Promise<Anniversary> {
    return this.http.patch<Anniversary>(`api/anniversaries/${id}`, request as Object)
  }

  async deleteAnniversary(id: string): Promise<OkResponse> {
    return this.http.delete<OkResponse>(`api/anniversaries/${id}`)
  }

  // ─── Letters ────────────────────────────────────────

  async createLetter(request: CreateLetterRequest): Promise<Letter> {
    return this.http.post<Letter>('api/letters', request as Object)
  }

  async letters(): Promise<ItemPage<Letter>> {
    return this.http.get<ItemPage<Letter>>('api/letters')
  }

  async letterUnreadSummary(): Promise<MediaUnreadSummary> {
    return this.http.get<MediaUnreadSummary>('api/letters/unread-summary')
  }

  async letterDetail(id: string): Promise<Letter> {
    return this.http.get<Letter>(`api/letters/${id}`)
  }

  async openLetter(id: string): Promise<Letter> {
    const emptyBody: MarkMediaReadRequest = { ids: null, all: null }
    return this.http.post<Letter>(`api/letters/${id}/open`, emptyBody)
  }

  async updateLetter(id: string, request: CreateLetterRequest): Promise<Letter> {
    return this.http.put<Letter>(`api/letters/${id}`, request as Object)
  }

  async deleteLetter(id: string): Promise<OkResponse> {
    return this.http.delete<OkResponse>(`api/letters/${id}`)
  }

  // ─── Unbinding ──────────────────────────────────────

  async requestUnbinding(request: CreateUnbindingRequest): Promise<UnbindingRequest> {
    return this.http.post<UnbindingRequest>('api/couple-space/unbinding', request)
  }

  async confirmUnbinding(id: string): Promise<OkResponse> {
    return this.http.post<OkResponse>(`api/couple-space/unbinding/${id}/confirm`)
  }

  async cancelUnbinding(id: string): Promise<OkResponse> {
    return this.http.post<OkResponse>(`api/couple-space/unbinding/${id}/cancel`)
  }

  // ─── Activity ───────────────────────────────────────

  async partnerActivity(unreadOnly: boolean = true, limit: number = 20): Promise<PartnerActivityPage> {
    const params = new Map<string, string>()
    params.set('unreadOnly', unreadOnly.toString())
    params.set('limit', limit.toString())
    return this.http.get<PartnerActivityPage>('api/activity', params)
  }

  async markActivityRead(request: MarkActivityReadRequest): Promise<MarkActivityReadResponse> {
    return this.http.post<MarkActivityReadResponse>('api/activity/read', request as Object)
  }
}
