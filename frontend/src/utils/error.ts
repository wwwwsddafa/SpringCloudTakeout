/**
 * 统一提取错误信息。
 *
 * 后端业务错误由响应拦截器 reject 成 `new Error(data.msg)`，
 * 此时 Error 对象上没有 response 属性；
 * 只有 HTTP 层异常（401/404/500）时 axios 才生成带 response 的 Error。
 * 这里两种都兜住，避免在页面里写 err?.response?.data?.msg 取到 undefined。
 */
export function getErrorMsg(err: any, fallback = '操作失败'): string {
  return err?.message || err?.response?.data?.msg || fallback
}
