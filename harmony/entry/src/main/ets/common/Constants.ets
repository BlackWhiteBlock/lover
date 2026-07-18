/**
 * Application-wide constants.
 * Mirror of Android's BuildConfig fields.
 *
 * API_BASE_URL 对应 Android gradle.properties 的 LOVER_API_BASE_URL。
 * 调试时可改为本地可达地址；发布前确认指向生产 HTTPS。
 */
export class Constants {
  /** Backend API base URL */
  static readonly API_BASE_URL: string = 'https://api.loverlyy.space/'

  /** Invite link base URL */
  static readonly INVITE_BASE_URL: string = Constants.API_BASE_URL

  /**
   * Debug flag for verbose error surfaces.
   * Keep true for DevEco debug HAPs; set false before AppGallery release.
   */
  static readonly DEBUG: boolean = true

  /** Media page size for cursor pagination */
  static readonly MEDIA_PAGE_SIZE: number = 30
}
