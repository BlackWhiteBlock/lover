/**
 * Brand wordmark / logo — mirror of Android LoverBrand.kt
 */
import { Fonts } from './Fonts'
import { FontSizes, LetterSpacing } from './Theme'
import type { MoodPalette } from './Theme'

@Component
export struct LoverAppName {
  @Prop mood: MoodPalette
  @Prop fontSize: number = 56
  @Prop fontEpoch: number = 0

  build() {
    Text(this.fontEpoch >= 0 ? 'lover.' : 'lover.')
      .fontSize(this.fontSize)
      .fontFamily(Fonts.PINYON)
      .fontColor(this.mood.appNameColor)
      .fontWeight(FontWeight.Normal)
  }
}

/** Dual-heart mark — Android lover_logo vector as PNG. */
@Component
export struct LoverLogo {
  @Prop mood: MoodPalette
  @Prop logoSide: number = 56
  @Prop usePhoto: boolean = false

  build() {
    Image(this.usePhoto ? $r('app.media.lover_logo_full') : $r('app.media.lover_logo'))
      .width(this.logoSide)
      .height(this.logoSide * 0.96)
      .objectFit(ImageFit.Contain)
      .opacity(this.mood.solo ? 0.85 : 1)
  }
}

@Component
export struct LoverWordmark {
  @Prop mood: MoodPalette
  @Prop showTagline: boolean = true
  @Prop logoSize: number = 72
  @Prop usePhoto: boolean = true
  @Prop fontEpoch: number = 0

  build() {
    Column({ space: 6 }) {
      LoverLogo({ mood: this.mood, logoSide: this.logoSize, usePhoto: this.usePhoto })
      LoverAppName({ mood: this.mood, fontSize: 56, fontEpoch: this.fontEpoch })
      if (this.showTagline) {
        Text(this.mood.tagline)
          .fontSize(FontSizes.labelMedium)
          .fontFamily(Fonts.LATO_LIGHT)
          .fontColor(this.mood.stone)
          .letterSpacing(LetterSpacing.medium)
      }
    }
    .alignItems(HorizontalAlign.Center)
  }
}

@Component
export struct LoverBrandRow {
  @Prop mood: MoodPalette
  @Prop logoSize: number = 40
  @Prop nameSize: number = 42
  @Prop fontEpoch: number = 0

  build() {
    Row({ space: 8 }) {
      LoverLogo({ mood: this.mood, logoSide: this.logoSize, usePhoto: false })
      LoverAppName({ mood: this.mood, fontSize: this.nameSize, fontEpoch: this.fontEpoch })
    }
    .alignItems(VerticalAlign.Center)
  }
}
