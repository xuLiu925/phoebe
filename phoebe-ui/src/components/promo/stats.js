import http from '../../api'

const LEVEL = { plan: 'PLAN', unit: 'UNIT', qa: 'QA', page: 'MIDDLE_PAGE' }

/** 拉取指定层级在时间范围内的报表行，返回 Map<targetId, row> 与汇总、趋势。 */
export async function loadStats(level, range, planId, unitId) {
  if (!LEVEL[level]) return { map: new Map(), summary: null, trend: [] }
  const r = await http.get('/reports', {
    params: { level: LEVEL[level], from: range?.[0], to: range?.[1], planId: planId || undefined, unitId: unitId || undefined }
  })
  return { map: new Map(r.rows.map(x => [x.targetId, x])), summary: r.summary, trend: r.trend }
}

/** 批量执行（“编辑操作”下拉） */
export async function batch(rows, fn) {
  for (const r of rows) await fn(r)
}
