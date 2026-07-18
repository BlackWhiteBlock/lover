/**
 * Date utility functions.
 * Mirror of Android's DateUtils.kt
 */
export class DateUtils {
  /** Calculate loving days: days between togetherDate and today (inclusive) */
  static lovingDays(togetherDate: string, today?: Date): number {
    const together = new Date(togetherDate)
    const now = today ?? new Date()
    together.setHours(0, 0, 0, 0)
    now.setHours(0, 0, 0, 0)
    if (together > now) return 0
    const diffMs = now.getTime() - together.getTime()
    const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24))
    return diffDays + 1
  }

  /** Yearly anniversary countdown: days until next occurrence */
  static yearlyCountdown(date: string, today?: Date): number {
    const d = new Date(date)
    const now = today ?? new Date()
    now.setHours(0, 0, 0, 0)
    const thisYear = new Date(now.getFullYear(), d.getMonth(), d.getDate())
    let next = thisYear
    if (thisYear < now) {
      next = new Date(now.getFullYear() + 1, d.getMonth(), d.getDate())
    }
    const diffMs = next.getTime() - now.getTime()
    return Math.ceil(diffMs / (1000 * 60 * 60 * 24))
  }

  /** Milestone countdown: days until date, or null if past */
  static milestoneCountdown(date: string, today?: Date): number | null {
    const d = new Date(date)
    const now = today ?? new Date()
    d.setHours(0, 0, 0, 0)
    now.setHours(0, 0, 0, 0)
    if (d < now) return null
    const diffMs = d.getTime() - now.getTime()
    return Math.ceil(diffMs / (1000 * 60 * 60 * 24))
  }

  /** Format date as YYYY-MM-DD */
  static formatDate(date: Date): string {
    const y = date.getFullYear()
    const m = String(date.getMonth() + 1).padStart(2, '0')
    const d = String(date.getDate()).padStart(2, '0')
    return `${y}-${m}-${d}`
  }

  /** Parse YYYY-MM-DD to Date */
  static parseDate(dateStr: string): Date {
    return new Date(dateStr + 'T00:00:00')
  }
}
