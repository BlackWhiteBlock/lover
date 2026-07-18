/**
 * Thin navigation helper + typed route params (ArkTS forbids untyped object literals).
 */
import { router, promptAction } from '@kit.ArkUI'
import { Colors } from '../design/Theme'

export class MediaDetailParams {
  mediaId: string = ''

  constructor(mediaId: string) {
    this.mediaId = mediaId
  }
}

export class LetterDetailParams {
  letterId: string = ''

  constructor(letterId: string) {
    this.letterId = letterId
  }
}

export class AnniversaryComposeParams {
  id: string = ''

  constructor(id: string = '') {
    this.id = id
  }
}

class RouterJumpOptions {
  url: string = ''
  params?: Object

  constructor(url: string, params?: Object) {
    this.url = url
    this.params = params
  }
}

class DialogButtonOption {
  text: string = ''
  color: string = ''

  constructor(text: string, color: string) {
    this.text = text
    this.color = color
  }
}

class ShowDialogOptions {
  title: string = ''
  message: string = ''
  buttons: DialogButtonOption[] = []

  constructor(title: string, message: string, buttons: DialogButtonOption[]) {
    this.title = title
    this.message = message
    this.buttons = buttons
  }
}

class ToastOption {
  message: string = ''
  duration: number = 2500

  constructor(message: string, duration: number = 2500) {
    this.message = message
    this.duration = duration
  }
}

export class AppNav {
  static replace(url: string, params?: Object): void {
    if (params) {
      router.replaceUrl(new RouterJumpOptions(url, params))
    } else {
      router.replaceUrl(new RouterJumpOptions(url))
    }
  }

  static push(url: string, params?: Object): void {
    if (params) {
      router.pushUrl(new RouterJumpOptions(url, params))
    } else {
      router.pushUrl(new RouterJumpOptions(url))
    }
  }

  static pushMediaDetail(mediaId: string): void {
    AppNav.push('pages/MediaDetailPage', new MediaDetailParams(mediaId))
  }

  static pushMediaEdit(mediaId: string): void {
    AppNav.push('pages/MediaEditPage', new MediaDetailParams(mediaId))
  }

  static pushLetterDetail(letterId: string): void {
    AppNav.push('pages/LetterDetailPage', new LetterDetailParams(letterId))
  }

  static pushAnniversaryCompose(id: string = ''): void {
    if (id.length > 0) {
      AppNav.push('pages/AnniversaryComposePage', new AnniversaryComposeParams(id))
    } else {
      AppNav.push('pages/AnniversaryComposePage')
    }
  }

  static back(): void {
    router.back()
  }

  static params<T extends Object>(): T | undefined {
    return router.getParams() as T | undefined
  }
}

export class AppDialog {
  static async confirm(
    title: string,
    message: string,
    confirmText: string = '确定',
    cancelText: string = '取消',
  ): Promise<boolean> {
    try {
      const cancelBtn = new DialogButtonOption(cancelText, Colors.stone)
      const confirmBtn = new DialogButtonOption(confirmText, Colors.error)
      const buttons: DialogButtonOption[] = [cancelBtn, confirmBtn]
      const options = new ShowDialogOptions(title, message, buttons)
      const result = await promptAction.showDialog(options)
      return result.index === 1
    } catch (_e) {
      return false
    }
  }

  static toast(message: string, duration: number = 2500): void {
    try {
      promptAction.showToast(new ToastOption(message, duration))
    } catch (_e) {
      // ignore
    }
  }
}
