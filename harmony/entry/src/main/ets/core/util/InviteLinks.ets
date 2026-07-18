/**
 * Invite link utilities.
 * Mirror of Android's InviteLinks.kt
 */
import { Constants } from '../../common/Constants'

export class InviteLinks {
  static buildUrl(code: string): string {
    let base = Constants.INVITE_BASE_URL
    if (base.endsWith('/')) {
      base = base.substring(0, base.length - 1)
    }
    return `${base}/invite/${code.trim().toUpperCase()}`
  }

  static parseCode(uri: string): string | null {
    if (!uri) return null
    try {
      // Manual URL parsing (URL class not available in ArkTS)
      let protocol = ''
      let hostname = ''
      let pathname = ''

      if (uri.startsWith('lover://')) {
        protocol = 'lover:'
        const rest = uri.substring('lover://'.length)
        const slashIdx = rest.indexOf('/')
        if (slashIdx >= 0) {
          hostname = rest.substring(0, slashIdx)
          pathname = rest.substring(slashIdx)
        } else {
          hostname = rest
          pathname = ''
        }
      } else if (uri.startsWith('https://') || uri.startsWith('http://')) {
        const schemeEnd = uri.indexOf('://')
        protocol = uri.substring(0, schemeEnd) + ':'
        const rest = uri.substring(schemeEnd + 3)
        const slashIdx = rest.indexOf('/')
        if (slashIdx >= 0) {
          hostname = rest.substring(0, slashIdx).toLowerCase()
          pathname = rest.substring(slashIdx)
        } else {
          hostname = rest.toLowerCase()
          pathname = ''
        }
      } else {
        return null
      }

      const segments: string[] = pathname.split('/').filter((s: string) => s.length > 0)

      // lover://invite/CODE
      if (protocol === 'lover:' && hostname === 'invite') {
        const code = segments.length > 0 ? segments[0].trim().toUpperCase() : ''
        if (code.length >= 6 && code.length <= 32) return code
      }

      // https://host/invite/CODE
      if (segments.length >= 2 && segments[0].toLowerCase() === 'invite') {
        const code = segments[1].trim().toUpperCase()
        if (code.length >= 6 && code.length <= 32) return code
      }

      // https://invite.host/CODE
      if (segments.length === 1 && hostname === 'invite') {
        const code = segments[0].trim().toUpperCase()
        if (code.length >= 6 && code.length <= 32) return code
      }

      return null
    } catch (_e) {
      return null
    }
  }

  static shareText(code: string, inviteUrl?: string): string {
    const url = inviteUrl ?? InviteLinks.buildUrl(code)
    return `来 Lover 和我组成小宇宙～\n打开链接或输入邀请码：${code}\n${url}`
  }
}
