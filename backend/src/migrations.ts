export function orderedMigrationNames(entries: string[]) {
  return entries
    .filter((name) => /^\d{3}_[A-Za-z0-9_-]+\.sql$/.test(name))
    .sort((left, right) => left.localeCompare(right));
}
