# 07 · API 接口文档

> 新建实现的接口，**不是**原 Phoebe 的接口。运行后可在 Swagger 查看并在线调试：http://localhost:8080/swagger-ui.html

## 通用约定
- 前缀 `/api`。除登录和占位图外，都需要请求头 `Authorization: Bearer <token>`。
- 管理员可以加请求头 `X-Account-Id: <账户ID>`，代登录某个广告账户。
- 返回体：`{"code": 0, "message": "ok", "data": ...}`。出错时 `code` 与 HTTP 状态码一致：400 参数错误、401 未登录、403 无权限、404 不存在、409 状态冲突、422 超出数量上限、500 服务器错误。
- 枚举：`RunStatus` = ACTIVE / PAUSED；`AuditStatus` = DRAFT / PENDING / APPROVED / REJECTED / CANCELED；`Device` = PC / MOBILE；`Channel` = SEARCH / ZHIDAO / BAOBAO / BAOBAO_APP。

## 认证与账户
| 方法 | 路径 | 说明 | 请求体 / 参数 |
|---|---|---|---|
| POST | `/auth/login` | 登录 | `{username, password}` → `{token, user}` |
| GET | `/auth/me` | 当前用户与账户 | |
| GET | `/account` | 当前广告账户 | |
| GET | `/admin/accounts` | [管理员] 账户列表 | |
| GET | `/admin/users` | [管理员] 用户列表 | |
| POST | `/admin/accounts` | [管理员] 开户（校验行业准入） | `{name, companyName, industry, website, consultUrl, initialBalance, username, password}` |
| POST | `/admin/accounts/{id}/recharge` | [管理员] 模拟充值 | `{amount}` |
| PUT | `/admin/audit-mode` | [管理员] 切换自动审核 | `{autoApprove}` |

## 计划
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/plans` | 列表：`[{plan, unitCount, qaCount, middlePageCount, enabledMiddlePageName, todayCost, blockers[]}]` |
| GET | `/plans/{id}` | 详情（结构同上） |
| POST | `/plans` | 新建 `{name, budget(null=不限), mobileBidRatio, regions[], mobilePhone, schedule(168位0/1，null=不限), excludedIps[]}` |
| PUT | `/plans/{id}` | 编辑（同上） |
| PUT | `/plans/{id}/status` | `{status}` |
| DELETE | `/plans/{id}` | 逻辑删除，级联删除下级物料 |

## 单元
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/plans/{planId}/units` | 列表：`[{unit, planName, qaCount, approvedQaCount, creativeCount, subscribeWords[]}]` |
| GET | `/units` | 当前账户全部单元 |
| GET | `/units/{id}` | 详情 |
| POST | `/plans/{planId}/units` | `{name, bid, subscribeWords[]}` |
| PUT | `/units/{id}` | 同上（`subscribeWords` 为 null 时不修改） |
| PUT | `/units/{id}/status` | `{status}` |
| PUT | `/units/{id}/subscribe-words` | `["词1", "词2"]` |
| DELETE | `/units/{id}` | |

## 问答
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/units/{unitId}/qas?auditStatus=&keyword=` | 列表 |
| GET | `/qas/{id}` | 详情 |
| POST | `/units/{unitId}/qas` | `{question, answer(HTML), bid?, source?}`，创建后自动送审 |
| PUT | `/qas/{id}` | 编辑；问题或答案变化时重新送审 |
| PUT | `/qas/{id}/status` | `{status}` |
| DELETE | `/qas/{id}` | |
| POST | `/units/{unitId}/qas/upload` | multipart `file`（xlsx/xls）→ `{totalRows, success, ignored, errors:[{row, question, reason}]}` |
| GET | `/qas/template` | 下载 Excel 模板 |

## 问答推荐
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/recommend/questions?keyword=` | 按关键词推荐高 PV 问题（一次性）。每项为 `{id, question, answer, pv, quality, channel, used}` |
| GET | `/recommend/questions/by-unit/{unitId}` | 按单元订阅词推荐 |
| GET | `/recommend/quality-qas?keyword=` | 优质问答（带答案） |
| GET | `/recommend/answers?question=` | 答案推荐 |

## 中间页
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/plans/{planId}/middle-pages` | 列表：`[{page, latest, online, status, statusText, completeness, missing[]}]` |
| GET | `/middle-pages/valid` | 本账户所有有效中间页（导入候选） |
| POST | `/plans/{planId}/middle-pages` | 新建 `{name?}`，每个计划最多 2 套 |
| GET | `/middle-pages/{id}` | 详情 |
| PUT | `/middle-pages/{id}/draft` | 全部保存 / 分模块保存 `{name?, pcStyle?, pc?, mobile?}` |
| POST | `/middle-pages/{id}/submit` | 全部保存并提交（请求体可选，同上） |
| POST | `/middle-pages/{id}/copy-pc-to-mobile` | 由 PC 页生成无线页 |
| POST | `/middle-pages/{id}/import?fromId=` | 导入有效中间页（覆盖当前内容） |
| POST | `/middle-pages/{id}/enable` | 启用（自动停用同计划的其他中间页） |
| POST | `/middle-pages/{id}/disable` | 停用 |
| GET | `/middle-pages/{id}/versions` | 版本列表 |
| GET | `/middle-pages/{id}/preview?device=PC|MOBILE&which=latest|online` | 预览数据 |
| DELETE | `/middle-pages/{id}` | 启用中的不能删除 |

`pc` / `mobile` 结构（`PageContent`）：
```json
{
  "companyInfo": {"name": "", "logo": "", "intro": "", "website": "", "consultUrl": "", "phone": "", "qrCode": ""},
  "banners": [{"image": "", "url": ""}],
  "products": [{"image": "", "title": "", "desc": "", "url": ""}],
  "appDownloadUrl": null,
  "videoUrl": null
}
```

## 高级创意
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/units/{unitId}/creatives` | 列表（含 `qaIds`） |
| GET | `/creatives/{id}` | 详情 |
| POST | `/units/{unitId}/creatives` | `{title, description, imageUrls[], targetUrl, qaIds[]}` |
| PUT | `/creatives/{id}` | 同上；内容变化时重新送审 |
| PUT | `/creatives/{id}/qas` | `[qaId...]`，只能关联同单元的问答 |
| PUT | `/creatives/{id}/status` | `{status}` |
| DELETE | `/creatives/{id}` | |

## 资源管理
| 方法 | 路径 | 说明 |
|---|---|---|
| GET / POST | `/knowledge` | 企业知识列表 / 新建 `{title, content}` |
| PUT / DELETE | `/knowledge/{id}` | 编辑 / 删除 |
| GET | `/images` | 图片库 |
| POST | `/images` | multipart `file`，可带 `cropRatio`（1:1 / 4:3 / 16:9 / 3:1） |
| GET | `/images/recommendations` | 系统推荐图片 |
| POST | `/images/recommend` | 把推荐图片加入图片库 `{name, url, width, height, cropRatio}` |
| DELETE | `/images/{id}` | |
| GET | `/images/placeholder?w=&h=&text=` | SVG 占位图（无需登录） |

## 审核（审核员 / 管理员）
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/audit/tasks?status=&type=&page=&size=` | 分页列表 |
| GET | `/audit/tasks/{id}` | 详情（`snapshot` 为 JSON 字符串） |
| POST | `/audit/tasks/{id}/approve` | `{reason?}` |
| POST | `/audit/tasks/{id}/reject` | `{reason}`（必填） |

## 模拟投放
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/simulator/search` | `{query, channel, device, pageType: SEARCH_LIST|QUESTION_PAGE, region?, ip?, time?("yyyy-MM-dd HH:mm:ss")}` → `{requestId, items[], auction[], excluded[], rule}` |
| POST | `/simulator/click/{impressionId}` | 点击问答入口并计费 → `{charged, duplicateClick, bidPrice, balance, middlePage}` |
| POST | `/simulator/inner-click/{impressionId}?target=` | 中间页内点击（不计费） |
| POST | `/simulator/traffic` | 批量造流量 `{searches, clickRate, seed?}` |

## 报表与工具
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/reports?level=ACCOUNT|PLAN|UNIT|QA|MIDDLE_PAGE&from=&to=&device=&planId=&unitId=&flow=ALL|ZHIDAO|BAOBAO|BAOBAO_APP&splitRegion=false` | `{rows[], trend[], summary}`；默认最近一周（截至昨日）；`splitRegion=true` 时每行带 `region` |
| GET | `/budget-limits?from=&to=&page=&size=` | 预算达限记录 |
| GET | `/reports/export?level=&from=&to=&device=&flow=&splitRegion=` | CSV 下载 |
| GET | `/oplogs?from=&to=&targetType=&page=&size=` | 历史操作记录 |
| GET | `/tools/bid-suggestion?qaId=` | 出价助手 |
| POST | `/tools/wizard` | 智能引导 `{keyword, budget, bid, maxQas}` |
| GET | `/rules` | 当前生效的业务规则与模拟参数 |
