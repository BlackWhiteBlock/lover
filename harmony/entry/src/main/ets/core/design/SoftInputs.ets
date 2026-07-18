/**
 * Soft form controls — SoftTextField + LoverDateField (bottom sheet + DatePicker).
 */
import { Fonts } from './Fonts'
import { Colors, FontSizes, Radii } from './Theme'
import type { MoodPalette } from './Theme'
import { DateUtils } from '../util/DateUtils'

class DatePickerResultValue {
  year: number = 1970
  month: number = 0
  day: number = 1
}

@Component
export struct SoftTextField {
  @Prop mood: MoodPalette
  @Prop label: string = ''
  @Link value: string
  @Prop placeholder: string = ''
  @Prop maxLength: number = 200
  @Prop multiline: boolean = false
  @Prop areaHeight: number = 100
  @Prop inputType: InputType = InputType.Normal
  @Prop readOnly: boolean = false

  build() {
    Column({ space: 6 }) {
      if (this.label.length > 0) {
        Text(this.label)
          .fontSize(FontSizes.bodySmall)
          .fontFamily(Fonts.LATO)
          .fontColor(this.mood.accent)
          .alignSelf(ItemAlign.Start)
      }
      if (this.multiline) {
        TextArea({ placeholder: this.placeholder, text: this.value })
          .height(this.areaHeight)
          .maxLength(this.maxLength)
          .fontSize(FontSizes.bodyLarge)
          .fontFamily(Fonts.LATO)
          .fontColor(this.mood.onSurface)
          .caretColor(this.mood.soft)
          .backgroundColor(Colors.white)
          .borderRadius(Radii.small)
          .border({ width: 1, color: this.mood.softOutline })
          .padding(14)
          .width('100%')
          .onChange((v: string) => { this.value = v })
      } else {
        TextInput({ placeholder: this.placeholder, text: this.value })
          .type(this.inputType)
          .maxLength(this.maxLength)
          .height(52)
          .fontSize(FontSizes.bodyLarge)
          .fontFamily(Fonts.LATO)
          .fontColor(this.mood.onSurface)
          .caretColor(this.mood.soft)
          .backgroundColor(Colors.white)
          .borderRadius(Radii.small)
          .border({ width: 1, color: this.mood.softOutline })
          .padding({ left: 14, right: 14 })
          .width('100%')
          .enabled(!this.readOnly)
          .onChange((v: string) => { this.value = v })
      }
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }
}

@Component
export struct SoftChip {
  @Prop mood: MoodPalette
  @Prop label: string = ''
  @Prop selected: boolean = false
  @Prop isEnabled: boolean = true
  onSelect: () => void = () => {}

  build() {
    Text(this.label)
      .fontSize(FontSizes.bodyMedium)
      .fontFamily(Fonts.LATO)
      .fontColor(this.selected ? Colors.white : this.mood.onSurface)
      .backgroundColor(this.selected ? this.mood.accent : this.mood.softSurface)
      .opacity(this.isEnabled ? 1 : 0.4)
      .borderRadius(Radii.small)
      .padding({ left: 14, right: 14, top: 10, bottom: 10 })
      .border({ width: 1, color: this.selected ? this.mood.accent : this.mood.softOutline })
      .onClick(() => {
        if (this.isEnabled) this.onSelect()
      })
  }
}

@Component
export struct SoftPrimaryButton {
  @Prop mood: MoodPalette
  @Prop label: string = ''
  @Prop isEnabled: boolean = true
  onTap: () => void = () => {}

  build() {
    Button(this.label)
      .width('100%')
      .height(52)
      .borderRadius(22)
      .backgroundColor(this.mood.soft)
      .fontColor(Colors.white)
      .fontSize(FontSizes.bodyLarge)
      .fontFamily(Fonts.LATO)
      .enabled(this.isEnabled)
      .onClick(() => {
        const tap = this.onTap
        if (tap !== undefined) {
          tap()
        }
      })
  }
}

@Component
export struct LoverDateField {
  @Prop mood: MoodPalette
  @Prop label: string = '日期'
  @Link value: string
  /** Optional ISO bound YYYY-MM-DD; empty = default 1970-01-01 / 2100-12-31 */
  @Prop minDate: string = ''
  @Prop maxDate: string = ''
  @State showSheet: boolean = false
  @State pickYear: number = 2026
  @State pickMonth: number = 1
  @State pickDay: number = 1

  displayText(): string {
    if (!this.value || this.value.length < 8) {
      return '请选择日期'
    }
    const d = DateUtils.parseDate(this.value)
    return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`
  }

  startBound(): Date {
    if (this.minDate.length >= 8) {
      return DateUtils.parseDate(this.minDate)
    }
    return new Date(1970, 0, 1)
  }

  endBound(): Date {
    if (this.maxDate.length >= 8) {
      return DateUtils.parseDate(this.maxDate)
    }
    return new Date(2100, 11, 31)
  }

  openSheet(): void {
    const d = (this.value && this.value.length >= 8)
      ? DateUtils.parseDate(this.value)
      : new Date()
    this.pickYear = d.getFullYear()
    this.pickMonth = d.getMonth() + 1
    this.pickDay = d.getDate()
    this.showSheet = true
  }

  confirm(): void {
    this.value =
      `${this.pickYear}-${String(this.pickMonth).padStart(2, '0')}-${String(this.pickDay).padStart(2, '0')}`
    this.showSheet = false
  }

  build() {
    Column({ space: 6 }) {
      Text(this.label)
        .fontSize(FontSizes.bodySmall)
        .fontFamily(Fonts.LATO)
        .fontColor(this.mood.accent)
        .alignSelf(ItemAlign.Start)

      Row() {
        Text(this.displayText())
          .fontSize(FontSizes.bodyLarge)
          .fontFamily(Fonts.LATO)
          .fontColor(this.mood.onSurface)
          .layoutWeight(1)
        Image($r('app.media.ic_calendar'))
          .width(22)
          .height(22)
          .objectFit(ImageFit.Contain)
          .fillColor(this.mood.soft)
      }
      .width('100%')
      .height(52)
      .padding({ left: 14, right: 14 })
      .backgroundColor(Colors.white)
      .borderRadius(Radii.small)
      .border({ width: 1, color: this.mood.softOutline })
      .onClick(() => this.openSheet())
      // Half-modal sheet floats above page Scroll — not clipped like position overlays.
      .bindSheet($$this.showSheet, this.DateSheet(), {
        height: SheetSize.FIT_CONTENT,
        dragBar: true,
        showClose: false,
        backgroundColor: Colors.white,
        preferType: SheetType.BOTTOM,
        onDisappear: () => {
          this.showSheet = false
        },
      })
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  DateSheet() {
    Column({ space: 12 }) {
      Text(this.label.length > 0 ? this.label : '选择日期')
        .fontSize(FontSizes.titleLarge)
        .fontFamily(Fonts.GARAMOND)
        .fontColor(this.mood.accent)
        .width('100%')

      DatePicker({
        start: this.startBound(),
        end: this.endBound(),
        selected: new Date(this.pickYear, this.pickMonth - 1, this.pickDay),
      })
        .lunar(false)
        .onChange((value: DatePickerResultValue) => {
          // DatePickerResult.month is 0-based
          this.pickYear = value.year
          this.pickMonth = value.month + 1
          this.pickDay = value.day
        })
        .width('100%')
        .height(200)

      Row({ space: 12 }) {
        Button('取消')
          .layoutWeight(1)
          .height(48)
          .borderRadius(22)
          .backgroundColor(this.mood.softSurface)
          .fontColor(this.mood.stone)
          .fontFamily(Fonts.LATO)
          .onClick(() => {
            this.showSheet = false
          })
        Button('确定')
          .layoutWeight(1)
          .height(48)
          .borderRadius(22)
          .backgroundColor(this.mood.soft)
          .fontColor(Colors.white)
          .fontFamily(Fonts.LATO)
          .onClick(() => this.confirm())
      }
      .width('100%')
    }
    .width('100%')
    .padding({ left: 20, right: 20, top: 8, bottom: 28 })
  }
}
