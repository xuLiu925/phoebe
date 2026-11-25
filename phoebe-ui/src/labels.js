// 状态命名参考公开资料 A 的状态说明表：未完成 / 审核中 / 审核拒绝 / 有效
export const AUDIT = {
  DRAFT: { text: '未完成', cls: 'st-gray', type: 'info' },
  PENDING: { text: '审核中', cls: 'st-orange', type: 'warning' },
  APPROVED: { text: '有效', cls: 'st-green', type: 'success' },
  REJECTED: { text: '审核拒绝', cls: 'st-red', type: 'danger' },
  CANCELED: { text: '已作废', cls: 'st-gray', type: 'info' }
}
export const RUN = { ACTIVE: { text: '启用', type: 'success' }, PAUSED: { text: '暂停', type: 'info' } }
export const SOURCE = { MANUAL: '手动创建', RECOMMEND: '推荐问题', QUALITY: '优质问答', EXCEL: '批量上传', WIZARD: '智能引导', IMPORT: '导入' }
export const TARGET = { QA: '知识问答', MIDDLE_PAGE: '中间页', CREATIVE: '高级创意' }
export const CHANNEL = { ZHIDAO: '百度知道', SEARCH: '网页搜索', BAOBAO: '宝宝知道', BAOBAO_APP: '宝宝知道app' }
export const MATCH = { EXACT: '精确', PHRASE: '短语包含', SUBSCRIBE: '订阅词', SIMILAR: '相似' }
export const PROVINCES = ['北京', '上海', '天津', '重庆', '河北', '山西', '内蒙古', '辽宁', '吉林', '黑龙江', '江苏', '浙江', '安徽', '福建', '江西', '山东', '河南', '湖北', '湖南', '广东', '广西', '海南', '四川', '贵州', '云南', '西藏', '陕西', '甘肃', '青海', '宁夏', '新疆', '香港', '澳门', '台湾']

/** 中间页列表状态：未完成 / 待提交 / 审核中 / 审核拒绝 / 有效（及“有效-新版xx”） */
export function pageStatus(v) {
  const map = {
    VALID: ['有效', 'st-green'],
    VALID_EDITING: ['有效（新版未提交）', 'st-green'],
    VALID_PENDING: ['有效（新版审核中）', 'st-green'],
    VALID_REJECTED: ['有效（新版被拒）', 'st-orange'],
    PENDING: ['审核中', 'st-orange'],
    REJECTED: ['审核拒绝', 'st-red'],
    DRAFT: v.missing?.length ? ['未完成', 'st-gray'] : ['待提交', 'st-blue']
  }
  const [text, cls] = map[v.status] || [v.statusText, '']
  return { text, cls }
}

/** 计划状态：第一个不可投放原因，或“有效” */
export function planStatus(pv) {
  if (!pv.blockers?.length) return { text: '有效', cls: 'st-green', tip: '' }
  const b = pv.blockers[0]
  const text = b.includes('暂停') ? '暂停推广' : b.includes('预算') ? '预算不足' : b.includes('余额') ? '余额不足' : b.includes('中间页') ? '中间页无效' : b.includes('时段') ? '暂停时段' : b
  return { text, cls: b.includes('暂停') ? 'st-gray' : 'st-orange', tip: pv.blockers.join('；') }
}

/** 汉字计 2 字符，与后端 TextRules 一致 */
export function countChars(s) {
  let n = 0
  for (const ch of s || '') n += /[一-鿿　-〿＀-￯]/.test(ch) ? 2 : 1
  return n
}
export function stripHtml(s) {
  return (s || '').replace(/<[^>]+>/g, '').replace(/&nbsp;/g, ' ').trim()
}
export function fmtDate(d) {
  const p = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}
/** 默认最近一周（截至昨日）[P:A] */
export function defaultRange() {
  const to = new Date(Date.now() - 86400000)
  const from = new Date(Date.now() - 7 * 86400000)
  return [fmtDate(from), fmtDate(to)]
}
