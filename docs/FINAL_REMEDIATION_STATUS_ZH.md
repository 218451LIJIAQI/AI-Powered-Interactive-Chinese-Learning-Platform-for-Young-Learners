# 最终整改状态

日期：2026-05-25

这份文件是对原 50 项生产就绪审计的收尾校准。结论按当前仓库源码、配置、CI 和本地验证命令判断；需要云资源、真实密钥、CDN、对象存储或人工翻译审校的事项，不能只靠源码宣称 100% 完成。

## 结论

核心 P0/P1 风险已经完成到可验证状态：AI 外部调用不再持有长数据库事务，Hikari 池显式配置为 30，容器部署、文档、安全头、AI 限流、refresh token、缓存、异步邮件、CORS、PWA、前端测试、E2E、质量门、错误监控入口、结构化日志、FULLTEXT、上传限额和 graceful shutdown 均已落地或有自动化验证。

仍然不应伪装成“源码已 100% 完成”的事项主要是三类：

- 内容工程：全站 i18n 文案提取、翻译、审校。
- 大型重构：超大 Vue/CSS/API service 文件拆分，需要按页面逐步回归。
- 外部生产能力：S3/OSS 真实实现、Redis 共享限流、CDN、K8s、视频转码、备份演练、负载测试和 DAST 目标环境。

## 50 项状态

| #   | 状态               | 当前结论                                                                                            |
| --- | ------------------ | --------------------------------------------------------------------------------------------------- |
| 1   | 已修复并测试       | AI provider 调用已从长事务中拆出，事务边界测试覆盖关键路径。                                        |
| 2   | 已修复             | Hikari `maximum-pool-size` 默认提升到 30，并可由环境变量覆盖。                                      |
| 3   | 已修复             | 已添加 `backend/Dockerfile`、`frontend/Dockerfile`、`.dockerignore` 和 `docker-compose.prod.yml`。  |
| 4   | 已修复             | 已添加 README、CONTRIBUTING、SECURITY、CHANGELOG、运维文档和审计文档。                              |
| 5   | 已修复并测试       | 已新增 `/api/v1/ai/chat/stream` SSE 端点，前端聊天页使用增量渲染。                                  |
| 6   | 已修复             | 已启用异步执行器，邮件投递改为事务提交后异步执行。                                                  |
| 7   | 已修复并测试       | 已实现 access token + refresh token，refresh token 存 DB、旋转、撤销，并通过 HttpOnly cookie 下发。 |
| 8   | 已修复             | Caffeine 缓存已覆盖平台设置、商务设置、学习目录、内容安全等热读。                                   |
| 9   | 需内容工程         | i18n 插件存在，但全站硬编码文案仍需逐页提取、翻译和审校。                                           |
| 10  | 已修复             | Spring Security 和 Nginx 均配置 CSP。                                                               |
| 11  | 已修复             | 已实现用户级 AI 使用限流和配额计数；多实例生产需换 Redis/网关共享计数。                             |
| 12  | 部分已治理         | 多个列表仓库已有 `EntityGraph`，仍建议用 SQL profiling 逐接口确认 N+1。                             |
| 13  | 需渐进重构         | God service/component 仍是维护性问题，需配合回归测试逐域拆分。                                      |
| 14  | 需渐进重构         | CSS 已有页面级 chunk 输出，但 `main.css` 仍偏大，需继续按域拆分。                                   |
| 15  | 已抽象，生产需实现 | 已新增 `StorageProvider` 边界和本地实现；水平扩展前接入 S3/OSS 实现。                               |
| 16  | 已修复             | Flyway 已收敛为当前 V1 baseline，后续生产冻结后再严格递增。                                         |
| 17  | 已修复并测试       | V1 schema 已包含 FULLTEXT 索引，并有迁移配置测试锁定。                                              |
| 18  | 已修复             | 已接入 `vite-plugin-pwa`，构建生成 service worker 和 manifest。                                     |
| 19  | 需渐进重构         | `services.ts` 仍较集中，建议按 auth/ai/content/commerce/admin 等域拆分。                            |
| 20  | 已修复             | Actuator、Prometheus registry、AI provider latency metric 已配置。                                  |
| 21  | 已修复             | 已引入 Vitest、Vue Test Utils，并覆盖 auth store、apiError、routeCompat。                           |
| 22  | 已修复             | 已引入 Playwright smoke E2E，并接入 CI。                                                            |
| 23  | 已修复并测试       | 新增 multipart 请求大小过滤，非视频提前按较小上限拒绝，视频路径保留大文件上限。                     |
| 24  | 已修复并测试       | CORS 改为显式 methods/headers。                                                                     |
| 25  | 已修复             | access token 改为内存保存，refresh token 使用 HttpOnly cookie，旧 storage token 会清理。            |
| 26  | 已修复             | CI 已扩展为前端、后端、依赖/镜像扫描多 job。                                                        |
| 27  | 已修复             | 已添加 Husky pre-commit 和 lint-staged。                                                            |
| 28  | 已修复             | 已添加 ESLint 和 Prettier 配置及脚本。                                                              |
| 29  | 未处理             | Dark mode 是产品增强项，不影响生产可用性评分核心。                                                  |
| 30  | 部分处理           | 构建与 CDN 策略已文档化；实际图片转 WebP/AVIF 需资产审计后执行。                                    |
| 31  | 已修复             | Vite manual chunks 已配置，构建输出 framework/networking/icons 等 chunks。                          |
| 32  | 已修复入口         | 前端 Sentry 和后端 Sentry 配置入口已添加，生产需要配置 DSN。                                        |
| 33  | 已修复             | 已配置 `spring.lifecycle.timeout-per-shutdown-phase`。                                              |
| 34  | 未处理             | 视频转码/HLS 需要队列、FFmpeg 和存储策略，建议作为独立迭代。                                        |
| 35  | 已文档化           | CDN 缓存策略已写入运维文档，真实启用取决于部署平台。                                                |
| 36  | 部分修复           | 关键 AI 端点已补 OpenAPI 注解；全控制器完整注解仍可持续补齐。                                       |
| 37  | 已修复             | 已添加 `logstash-logback-encoder` 与 JSON logback 配置。                                            |
| 38  | 已脚手架           | 已添加 k6 smoke capacity 脚本和运维运行说明，需预生产环境执行留证。                                 |
| 39  | 已修复             | 已添加 Dependabot/Dependency Review/Trivy 扫描。                                                    |
| 40  | 已修复入口         | 已添加 ZAP baseline workflow 和规则文件，需预生产 URL 执行。                                        |
| 41  | 已确认             | 上传目录已被 `.gitignore` 覆盖。                                                                    |
| 42  | 已修复             | 已添加 CHANGELOG 和 SECURITY。                                                                      |
| 43  | 部分修复           | 错误响应和验证测试已增强；字段级错误是否暴露仍是产品/隐私取舍。                                     |
| 44  | 需渐进重构         | Admin activity controller/service 仍偏大，建议随测试覆盖逐域拆分。                                  |
| 45  | 已文档化           | 已添加 API versioning policy，路径保留 `/api/v1`。                                                  |
| 46  | 未处理             | K8s manifests 取决于部署平台；当前提供 Docker/Compose 生产路径。                                    |
| 47  | 已脚手架           | 已添加 backup/restore 脚本和演练 runbook。                                                          |
| 48  | 部分修复           | CI 增加 OpenAPI contract 导出校验入口；前端类型完全自动生成仍可继续做。                             |
| 49  | 已修复并测试       | 登录/公开认证限流阈值已配置化。                                                                     |
| 50  | 已修复并测试       | 限流状态改为有界 Caffeine cache，避免 O(n) 容量驱逐扫描。                                           |

## 验收命令

本轮已通过：

```powershell
cd backend
.\mvnw.cmd clean test
.\mvnw.cmd verify
.\mvnw.cmd spotbugs:check
.\mvnw.cmd -DskipTests -Djacoco.skip=true -DskipOpenApiContract=false verify

cd ..\frontend
npm run format:check
npm run lint
npm run build
npm run test:unit
npm run test:e2e
```
