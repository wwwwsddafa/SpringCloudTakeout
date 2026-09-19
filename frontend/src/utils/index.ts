const DATA_URI_PREFIX = 'data:image/png;base64,'

export function cleanCaptchaImage(raw: string): string {
  if (!raw) return raw

  const cleaned = raw.replaceAll(DATA_URI_PREFIX, '')
  return DATA_URI_PREFIX + cleaned
}