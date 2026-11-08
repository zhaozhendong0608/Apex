/**
 * 工作流工位与四层全景拓扑知识库 (Under the Hood Knowledge Base)
 * 涵盖：SOP 指令轨 + AI Skill 专家轨 + 控制中枢轨 + 确定性物理产物轨
 * 包含输入输出清单及真实工程 Demo 示例，辅助用户深度理解工位行为与契约
 */

export interface WorkflowNodeKnowledge {
  name: string
  category: 'instruction' | 'skill' | 'control' | 'artifact'
  categoryName: string
  role: string
  skill: string
  sop: string
  gate: string
  principle: string
  inputs: string[]
  outputs: string[]
  inputDemo?: string
  outputDemo?: string
}

export const NODE_KNOWLEDGE_BASE: Record<string, WorkflowNodeKnowledge> = {
  'SOP-00': {
    name: '[0] 体系总览与规模分流识别',
    category: 'instruction',
    categoryName: '👤 SOP 指令主轨',
    role: '全员 / 流程中枢',
    skill: '-',
    sop: '.ai/sop/00-sop-overview.md',
    gate: '自适应分流判定完毕',
    principle: '依据需求规模 (P1/P2/P3) 与变更烈度，智能决策走标准研发主轨（需门禁与基线闭环）还是极速微创快轨（单文件≤20行免门禁）。消除传统流程“改一个错别字也要走九个流程”的官僚主义，实现敏捷与受控的黄金平衡。',
    inputs: ['业务原始提报', '需求描述口语'],
    outputs: ['自适应分流图谱', '目标执行轨迹 (STANDARD / FASTTRACK)'],
    inputDemo: `【输入口语示例】：
"客户要求在网络拓扑画布上加一个导出SVG矢量图的按钮，并且点击后自动下载到本地。"`,
    outputDemo: `【分流评估结果 (STANDARD)】：
- 需求等级: P2 (中等特性)
- 影响链路: 前端组件 (Canvas.vue) + 工具类 (svgExport.ts)
- 决策路径: 触发标准主轨 (SOP-01 ➔ SOP-02 ➔ SOP-03 ➔ SOP-06)`
  },
  'SOP-01': {
    name: '[1] 需求规划与白话澄清',
    category: 'instruction',
    categoryName: '👤 SOP 指令主轨',
    role: '产品 / 张工',
    skill: 'requirement-discovery',
    sop: '.ai/sop/01-sop-planning.md',
    gate: '四层清单 (P04) 齐备并冻结',
    principle: 'AI 主导的反向质询 (Grill Protocol)。在代码动工前，先将抽象口语翻译为具备“范围、规则、异常、不做项”的四层清单。同时进行披萨式轻量切片，将大项目分解为单次 0.5~2 天即可验证的独立原子增量，并向测试轨平行分流。',
    inputs: ['SOP-00 分流结果', '客户口语化原话 (留痕防扯皮)'],
    outputs: ['四层清单基线草案 (P04)', 'S1~S3 披萨切片清单', 'TC-GEN 测试用例并行触发信标'],
    inputDemo: `【用户模糊输入】：
"我们要支持免登录签署，客户必须能随时点进去看变更说明并签字。"`,
    outputDemo: `【反向质询提炼后的四层清单 (P04)】：
- [SCOPE] 免登录签署落地页 (支持PC端与移动端自适应)
- [RULE] 采用一次性 HMAC-SHA256 签名链接，有效期限定 72 小时
- [EXCEPTION] 链接过期或被篡改时提示“签署凭证失效”，禁止提交
- [SKIP] 本期不做人脸活体识别认证与短信二次验证码`
  },
  'SOP-02': {
    name: '[2] 研发轨方案设计与契约冻结',
    category: 'instruction',
    categoryName: '👤 SOP 指令主轨',
    role: '研发 / 李工',
    skill: 'design-spec-architect',
    sop: '.ai/sop/02-sop-tech-design.md',
    gate: 'python3 gate.py --sql 校验通过',
    principle: '基于四层清单推演完整分层架构、API 契约标准及 service.sql 结构。坚持“契约先于代码”理念，由 board.py 作为状态机唯一合法入口强制固化表结构与 DLD 文档，严禁研发人员未经设计直接凭空写代码。',
    inputs: ['四层清单基线 (P04)', '静态原型 HTML/Mockup'],
    outputs: ['docs/design/detail.md (DLD 详细设计)', 'docs/db/service.sql (表结构定义)', 'board.py 状态机迁移锁定'],
    inputDemo: `【输入前置约束】：
四层清单条目：HMAC 一次性签署凭证生成与核验。`,
    outputDemo: `【输出 DLD 接口契约 & DDL 定义】：
POST /api/v1/req/sign/verify
Response: { "code": 200, "data": { "valid": true, "signer": "李总" } }

CREATE TABLE t_req_sign_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  token VARCHAR(64) NOT NULL UNIQUE COMMENT '一次性签署Token',
  sign_status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
);`
  },
  'TC-GEN': {
    name: '[TC] 测试用例矩阵先行 (测试解耦轨)',
    category: 'instruction',
    categoryName: '👤 SOP 指令主轨',
    role: '测试 / 王工',
    skill: 'quality-verifier',
    sop: '.ai/sop/01-sop-planning.md',
    gate: '用例矩阵 100% 覆盖关键链路',
    principle: '测试轨与研发轨绝对平行解耦！在李工写业务代码的同时，王工与 quality-verifier 专家基于需求清单同步编写独立的 docs/test-cases 矩阵。研发人员写完代码后必须接受此用例矩阵的碰撞考验，杜绝“既当运动员又当裁判员”的伪测试。',
    inputs: ['四层清单基线 (P04)', 'detail.md 接口契约'],
    outputs: ['docs/test-cases/ 自动化测试用例集', 'E2E 离线穿透验证探针'],
    inputDemo: `【输入测试依据】：
接口契约：POST /api/v1/req/sign/verify (含 token 校验与超时控制)`,
    outputDemo: `【输出测试用例矩阵 (TC-SIGN-01)】：
- 用例1 (正向): 合法 Token 提交 ➔ 返回 200 且 valid=true
- 用例2 (异常): 已过期 Token 提交 ➔ 返回 400 "签署凭证已过期"
- 用例3 (破坏性): 篡改签名参数 ➔ 触发防伪阻断，记录审计日志`
  },
  'SOP-03': {
    name: '[3] 单焦点 TDD 红绿循环编码',
    category: 'instruction',
    categoryName: '👤 SOP 指令主轨',
    role: '研发 / 李工',
    skill: 'coding-standards',
    sop: '.ai/sop/03-sop-coding.md',
    gate: '单元测试全绿 (0 Failure / 0 Error)',
    principle: '严格践行红绿单测循环：先写红灯报错测试 ➔ 编写最小化业务实现 ➔ 单测变绿 ➔ 重构优化。严厉禁止使用假 Mock 伪造成功。遇到第三方 API 必须保留真实沙盒联调日志，杜绝未经联调的代码流入下游。',
    inputs: ['detail.md 契约约束', 'service.sql 数据库结构'],
    outputs: ['src/ 业务模块源码', '第三方 API 真实联调日志', '.ai/tier3_status.md 任务更新'],
    inputDemo: `【输入驱动契约】：
DLD-REQ-02: 签署状态状态机变迁 (PENDING ➔ SIGNED ➔ ARCHIVED)`,
    outputDemo: `【输出红绿单测结果】：
[INFO] Running com.apex.req.service.SignServiceTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time: 0.241 s
[INFO] BUILD SUCCESS (全绿通过，产出真实请求联调存证日志)`
  },
  'SOP-04': {
    name: '[4] 代码审查机器门禁 (gate.py)',
    category: 'instruction',
    categoryName: '👤 SOP 指令主轨',
    role: '看门狗探针',
    skill: 'code-quality-reviewer',
    sop: '.ai/sop/04-sop-code-review.md',
    gate: 'python3 gate.py --staged [LGTM 阻断拦截]',
    principle: '代码合并前的终极防线。由 gate.py 看门狗自动化执行 6 项高压扫描：① 严禁假 Mock / 假写死；② 严禁野公网依赖；③ DDL 与回滚配对检查；④ 密钥硬编码拦截；⑤ 圈复杂度超标拦截；⑥ NPE 空指针潜在隐患。任意一项违规立刻直接阻断！',
    inputs: ['git staged 暂存区改动', 'src/ 业务源码工程'],
    outputs: ['gate.py 扫描报告 (LGTM)', '违规代码拦截警报与微创重构建议'],
    inputDemo: `【输入待审代码 Diff】：
git staged 暂存区 3 个修改文件（新增建表 SQL、SignController.java）`,
    outputDemo: `【gate.py 执行报告】：
[PASS] Check 1: 零假Mock代码扫描通过
[PASS] Check 2: SQL 与回滚脚本 1:1 配对校验通过
[PASS] Check 3: 敏感密钥硬编码扫描通过
>>> STATUS: LGTM (机器门禁核准放行)`
  },
  'SOP-05': {
    name: '[5] 架构与契约人工评审',
    category: 'instruction',
    categoryName: '👤 SOP 指令主轨',
    role: '架构师 / 程工',
    skill: 'code-quality-reviewer',
    sop: '.ai/sop/05-sop-review.md',
    gate: '架构师三方签字核准',
    principle: '机器看门狗放行后的第二重专家把关。架构师基于 5 维清单对代码架构、性能并发安全、反向破坏性测试进行最终复核，确认符合团队长期资产沉淀标准后签署放行。',
    inputs: ['gate.py LGTM 机器报告', 'src/ 业务源码工程'],
    outputs: ['人工评审确认凭证 (APPROVED)', '技术债与优化备忘录'],
    inputDemo: `【输入待审报告】：
gate.py LGTM 机器报告 + Git Commit: feat(sign): 支持免登录签署`,
    outputDemo: `【架构师签署意见】：
- 架构分层对齐规范（Controller / Service / Mapper 清晰）
- 建议高并发场景下引入 Redis 缓存 Token 防重放
- 决策结论：APPROVED（签字核准进入集成测试）`
  },
  'SOP-06': {
    name: '[6] 双轨延迟汇合与集成穿透',
    category: 'instruction',
    categoryName: '👤 SOP 指令主轨',
    role: '测试 / 王工',
    skill: 'quality-verifier',
    sop: '.ai/sop/06-sop-integration.md',
    gate: 'TC 用例 100% 碰撞通过',
    principle: '【关键核心节点 · 双轨延迟汇合】：在此工位，研发轨产出的 src 业务工程与测试轨在 SOP-01 阶段独立编写的 TC 用例矩阵首次进行严苛的闭环碰撞。双方在同一沙盒内跑通全链路 E2E 穿透，确保研发没有偷工减料，测试没有放水。',
    inputs: ['SOP-05 交付的研发源码', 'TC-GEN 阶段冻结的测试用例矩阵'],
    outputs: ['E2E 离线穿透验证报告', '0 Error 构建凭证', '全绿碰撞测试单'],
    inputDemo: `【输入汇合双轨产物】：
- 研发轨：编译后的后端 platform-server.jar + 前端 dist/
- 测试轨：docs/test-cases/TC-SIGN-01 自动化执行集`,
    outputDemo: `【集成碰撞执行单】：
>>> 执行全链路 E2E 测试: 12 项
>>> 正向链路: 8/8 通过
>>> 异常拦截: 4/4 通过
>>> 碰撞结果: 100% 闭环通过 (0 Error / 0 Leak)`
  },
  'SOP-07': {
    name: '[7] 交付双向验收门禁 (唯一归档出口)',
    category: 'instruction',
    categoryName: '👤 SOP 指令主轨',
    role: '看门狗探针',
    skill: 'quality-verifier',
    sop: '.ai/sop/07-sop-acceptance.md',
    gate: 'board.py verify ➔ complete --accept',
    principle: '整个生命周期中需求唯一合法的归档出口！强制执行 board.py verify 防伪校验：验证四层清单、设计图、源码、门禁报告、测试报告全量资产无缺失且哈希对齐，人工核放后打上放行徽标。',
    inputs: ['SOP-06 集成测试报告', '全生命周期各工位归档文件'],
    outputs: ['board.py verify 验收凭据', '4 维终验合格清单', 'tier3_status.md 全标绿'],
    inputDemo: `【输入全生命周期归档包】：
清单 P04 + 详细设计 detail.md + 源码 src + gate.py报告 + TC碰撞单`,
    outputDemo: `【board.py 校验输出】：
$ python3 .ai/scripts/board.py verify REQ-001
[CHECK] 1. 四层清单条目全量打标 (5/5) ...... OK
[CHECK] 2. 数据库迁移脚本配对存在 ......... OK
[CHECK] 3. E2E集成测试碰撞通过 ........... OK
>>> 验证通过！需求状态安全跃迁至 COMPLETED`
  },
  'SOP-08': {
    name: '[8] 容器编排与离线部署',
    category: 'instruction',
    categoryName: '👤 SOP 指令主轨',
    role: '运维 / 李工',
    skill: '-',
    sop: '.ai/sop/08-sop-deploy.md',
    gate: '容器健康检查全通',
    principle: '打包 Docker 离线镜像与 docker-compose.yml 编排清单，生成幂等一键部署脚本 install.sh。提供容器心跳检测与自动拉起自愈机制，确保交付物在内网断网环境下可一键点火启动。',
    inputs: ['编译打包后的 JAR / 前端 Dist', 'docs/db/service.sql'],
    outputs: ['docker-compose.yml 编排配置', 'install.sh 幂等安装脚本', '离线镜像包'],
    inputDemo: `【输入编译发布包】：
- 后端 target/platform-server-1.0.0.jar
- 前端 dist/ 静态包
- 数据库 docs/db/service.sql`,
    outputDemo: `【输出容器点火脚本】：
$ bash install.sh --offline
[+] Building 1.2s (8/8) FINISHED
[+] Running 2/2
 ✔ Container apex-mysql   Started (Health: green)
 ✔ Container apex-server  Started (Port: 8080)`
  },
  'SOP-09': {
    name: '[9] Git 发布与版本打标',
    category: 'instruction',
    categoryName: '👤 SOP 指令主轨',
    role: '交付 / 张工',
    skill: '-',
    sop: '.ai/sop/09-sop-git-release.md',
    gate: 'Release 资产落盘完毕',
    principle: '打上标准语义化 Git Tag (如 v1.2.0)，生成正式 Release 发版说明书，并将数据库迁移脚本 (docs/db/migrations) 与回滚方案 1:1 配对归档，完成生命周期最终归档。',
    inputs: ['SOP-07 验收凭证', 'SOP-08 部署包'],
    outputs: ['Git 语义化 Tag', 'docs/db/migrations 升级回滚对', 'Release 最终发布文档'],
    inputDemo: `【输入发版信标】：
需求 REQ-001 已终验放行，目标发版号 v1.1.0`,
    outputDemo: `【输出正式版本资产】：
- Git Tag: tags/v1.1.0 (Signed commit)
- 发版文档: docs/releases/v1.1.0.md (含变更清单与热回滚指引)
- 迁移配对: docs/db/next_execute_sql/ 已同步归档`
  },
  'SOP-B2': {
    name: '⚡ [B2] 极速微创修补 (免状态机)',
    category: 'instruction',
    categoryName: '👤 旁路快速通道',
    role: '研发 / 李工',
    skill: 'quality-verifier',
    sop: '.ai/sop/B2-sop-fasttrack.md',
    gate: '严格限制单文件≤20行改动',
    principle: '专为紧急 Bug 修复、文案微调、样式修补设计的非状态机极速通道。单文件改动严格控制在 20 行以内，免走繁重状态机流转与多重门禁，改动完成后直通 SOP-09 发布打标，兼顾速度与安全。',
    inputs: ['紧急缺陷报告', '代码行级微创修改点'],
    outputs: ['微创代码补丁', '.ai/tier1_snapshot.md 静默刷新'],
    inputDemo: `【紧急修复诉求】：
页面按钮文字由“提交订单”更改为“确认签署”，修改仅限 1 行代码。`,
    outputDemo: `【微创放行凭证】：
git diff --stat: 1 file changed, 1 insertion(+), 1 deletion(-)
符合 B2 快轨单文件 ≤20 行硬标准，直接放行合并至主干。`
  },
  'SKILL-01': {
    name: '🤖 requirement-discovery 专家',
    category: 'skill',
    categoryName: '🧩 AI 技能专家',
    role: 'AI 产品专家',
    skill: 'requirement-discovery',
    sop: '.agents/skills/requirement-discovery/SKILL.md',
    gate: '反向质询无遗漏',
    principle: '采用反诘式引导 (Grill Protocol)，先在内部推演目标、场景、异常、生命周期与边界，再只向用户抛出具有决策价值的高含金量问题，帮助非专业用户无痛理清业务逻辑。',
    inputs: ['模糊的用户一句话需求'],
    outputs: ['四层确认清单 (范围/规则/异常/不做项)'],
    inputDemo: `【输入用户原话】：
"做一个类似电网监控的大屏，要能报警。"`,
    outputDemo: `【专家反向质询问题】：
1. 报警判定阈值是以 60 秒为准还是实时判定？
2. 报警发生时，是弹窗阻断还是仅顶部红灯常亮？`
  },
  'SKILL-02': {
    name: '🤖 design-spec-architect 专家',
    category: 'skill',
    categoryName: '🧩 AI 技能专家',
    role: 'AI 架构专家',
    skill: 'design-spec-architect',
    sop: '.agents/skills/design-spec-architect/SKILL.md',
    gate: '8 大章节规范对齐',
    principle: '依据需求清单自动推演出完整的概要设计 (HLD) 与详细设计 (DLD)。严格遵守 Apex 架构规范，输出标准表结构 DDL 与 API 契约，支持需求变更时增量更新架构文档。',
    inputs: ['四层需求清单', '静态交互原型'],
    outputs: ['detail.md 详细设计说明书', 'service.sql 数据库脚本'],
    inputDemo: `【输入需求清单条目】：
R-01 节点状态实时推送 + 心跳断开判定`,
    outputDemo: `【推演输出设计规范】：
包含 8 大标准章节：1.架构目标 2.数据模型(DDL) 3.REST接口契约 4.时序图 5.异常流`
  },
  'SKILL-03': {
    name: '🤖 coding-standards 专家',
    category: 'skill',
    categoryName: '🧩 AI 技能专家',
    role: 'AI 编码规范守卫',
    skill: 'coding-standards',
    sop: '.agents/skills/coding-standards/SKILL.md',
    gate: '逐字对齐 DLD 契约',
    principle: '强约束 AI 逐字绑定 DLD 说明书中的表名、字段名与 API 契约。遵循分层开发规约（Controller/Service/DAO），杜绝 AI 编码中常见的臆造字段或逻辑跑偏。',
    inputs: ['DLD 契约文档', '数据库 DDL'],
    outputs: ['高质量 Java / Vue 符合分层规约源码'],
    inputDemo: `【输入契约规约】：
字段必须完全锁定为 node_id, track_mode，严禁自造字段名。`,
    outputDemo: `【输出严谨 Java 分层代码】：
100% 逐字对齐 DLD 实体字段，严禁多余字段，包含标准中文注释。`
  },
  'SKILL-04': {
    name: '🤖 code-quality-reviewer 专家',
    category: 'skill',
    categoryName: '🧩 AI 技能专家',
    role: 'AI 静态质量审查专家',
    skill: 'code-quality-reviewer',
    sop: '.agents/skills/code-quality-reviewer/SKILL.md',
    gate: '静态扫描 0 风险',
    principle: '在代码提交前深度扫描代码隐患：NPE 空指针风险、流或连接未关闭/内存泄漏、并发不安全线程竞争、超标圈复杂度，并提供可直接执行的微创重构落地方案。',
    inputs: ['业务源码差异 (git diff)'],
    outputs: ['代码审查诊断报告', '微创重构补丁'],
    inputDemo: `【待扫描代码片段】：
FileInputStream fis = new FileInputStream(file); // 未在 finally 中关闭`,
    outputDemo: `【扫描诊断与微创补丁】：
[WARN] 发现潜在资源未关闭隐患！
建议重构为 try-with-resources:
try (FileInputStream fis = new FileInputStream(file)) { ... }`
  },
  'SKILL-06': {
    name: '🤖 quality-verifier 专家',
    category: 'skill',
    categoryName: '🧩 AI 技能专家',
    role: 'AI 质量验证与排错专家',
    skill: 'quality-verifier',
    sop: '.agents/skills/quality-verifier/SKILL.md',
    gate: '全量单测全绿',
    principle: '质量验证与小黄鸭排错专家。在编码前根据契约自动生成测试用例；在发生编译/运行时错误时，激活小黄鸭探针（日志抓取 ➔ 根因分析 ➔ 最小化修改验证）精准排错。',
    inputs: ['接口契约定义', '错误日志堆栈'],
    outputs: ['自动化测试脚本', '小黄鸭排错报告'],
    inputDemo: `【错误堆栈输入】：
java.lang.NullPointerException: Cannot invoke getStatus() on null object`,
    outputDemo: `【小黄鸭探针根因分析】：
- 根因：查询数据库返回空时未做 Optional 判空
- 最小化修复方案：使用 Optional.ofNullable(obj).orElseGet(...)`
  },
  'SKILL-00': {
    name: '🏛️ legacy-archaeologist 专家',
    category: 'skill',
    categoryName: '🧩 AI 技能专家',
    role: 'AI 逆向考古专家',
    skill: 'legacy-archaeologist',
    sop: '.agents/skills/legacy-archaeologist/SKILL.md',
    gate: '行为锁死探针就绪',
    principle: '面对无文档的庞杂老项目时，自顶向下扫描路由与链路切片，逆向提取 API 契约与表关系，输出 legacy_hld.md，并编写反向行为锁死探针，防止重构改崩旧业务。',
    inputs: ['存量遗留源码库'],
    outputs: ['legacy_hld.md 逆向设计', '反向行为锁死单测探针'],
    inputDemo: `【遗留代码切片】：
存量遗留的老版 Controller 与复杂嵌套 SQL。`,
    outputDemo: `【逆向锁死探针产出】：
- legacy_hld.md: 逆向提取 14 个隐藏 API 契约
- 自动化行为锁死探针: 锁定重构前后输出参数 100% 一致`
  },
  'CTRL-SNAP': {
    name: '⚡ tier1_snapshot.md 记忆卡',
    category: 'control',
    categoryName: '🧠 控制中枢轨 (.ai/)',
    role: '系统记忆中枢',
    skill: '-',
    sop: '.ai/tier1_snapshot.md',
    gate: '单文件严格控制 ≤30 行',
    principle: 'AI 会话冷启动的“名片卡”。单文件严格约束不超过 30 行，仅记录项目代号、当前活跃需求、当前工位与关键阻塞项，确保大模型在极短上下文内瞬间恢复工作记忆。',
    inputs: ['工作流最新流转事件'],
    outputs: ['极简上下文快照 (30行内)'],
    inputDemo: `事件：需求 REQ-001 推进至 SOP-03 编码阶段`,
    outputDemo: `# 项目: NET-SIM | 活跃: REQ-001 | 工位: SOP-03
- 当前目标: 完成 TDD 单元测试
- 阻塞项: 无`
  },
  'CTRL-BOARD': {
    name: '🛠️ board.py 状态机看板脚本',
    category: 'control',
    categoryName: '🧠 控制中枢轨 (.ai/)',
    role: '状态机看门狗',
    skill: '-',
    sop: '.ai/board.py',
    gate: '状态迁移严格校验',
    principle: '整个系统状态机的唯一合法入口！任何工位迁移、工件校验、归档放行都必须经由 board.py 执行校验并原子性更新数据库，严禁外部脚本或人工手工纂改工位。',
    inputs: ['advance/verify/complete 命令行指令'],
    outputs: ['原子状态跃迁', '防伪校验凭证'],
    inputDemo: `$ python3 .ai/scripts/board.py advance REQ-001 SOP-02`,
    outputDemo: `[SUCCESS] REQ-001 工位已安全推进至 [SOP-02 方案设计]，数据库记录已同步。`
  },
  'CTRL-STATUS': {
    name: '📋 tier3_status.md 任务看板',
    category: 'control',
    categoryName: '🧠 控制中枢轨 (.ai/)',
    role: '任务看板中枢',
    skill: '-',
    sop: '.ai/tier3_status.md',
    gate: '任务清单全量跟踪',
    principle: '任务与切片的物理跟踪清单。以 Markdown 表格形式实时维护 ACTIVE 与 DONE 状态列表，作为开发人员与 AI 协同的实时工作台。',
    inputs: ['开发切片任务分解'],
    outputs: ['任务进行中/已完成看板'],
    inputDemo: `S1 需求切片拆分为 3 个原子开发任务`,
    outputDemo: `| 任务编号 | 任务名称 | 状态 |
| T-01 | 数据库建表 DDL | DONE |
| T-02 | REST 接口实现 | ACTIVE |`
  },
  'CTRL-GATE': {
    name: '🔒 gate.py 机器门禁看门狗',
    category: 'control',
    categoryName: '🧠 控制中枢轨 (.ai/)',
    role: '质量门禁中枢',
    skill: '-',
    sop: 'gate.py',
    gate: '6项刚性扫描全绿',
    principle: '工程合并看门狗，内置 AST 抽象语法树与静态扫描器，包含 6 大高压红线规则，执行 exit code!=0 强行阻断。',
    inputs: ['提交代码 diff'],
    outputs: ['LGTM 扫描放行报告'],
    inputDemo: `$ python3 .ai/scripts/gate.py --staged`,
    outputDemo: `>>> gate.py: 6 项红线扫描全部通过 [EXIT 0 / LGTM]`
  },
  'CTRL-HANDOVER': {
    name: '📦 tier3_handover.md 历史长效黑匣子',
    category: 'control',
    categoryName: '🧠 控制中枢轨 (.ai/)',
    role: '长效交接中枢',
    skill: '-',
    sop: '.ai/tier3_handover.md',
    gate: '版本交接资产固化',
    principle: '记录所有技术债务、设计取舍、遗留 Bug 与后续版本的演进规划，为下一任维护者或未来的 AI 会话提供完整的历史推演依据。',
    inputs: ['版本交付验收结果'],
    outputs: ['长效资产交接文档'],
    inputDemo: `项目阶段性发布，存在 1 个未影响主流程的已知技术债务`,
    outputDemo: `## 技术债务存证
- 债务点: 签名链接缓存采用内存 HashMap，下期需重构为 Redis 集群。`
  },
  'ART-01': {
    name: '📝 四层清单基线草案 (P04)',
    category: 'artifact',
    categoryName: '📄 确定性物理产物',
    role: '产物管理',
    skill: 'requirement-discovery',
    sop: 'docs/requirements/P04.md',
    gate: '签字锁定',
    principle: '从口语对撞中提炼出的结构化清单，包含范围(Scope)、规则(Rule)、异常(Exception)、不做(Non-goal)，是后续所有架构设计与测试用例的唯一源头。',
    inputs: ['用户口语输入'],
    outputs: ['四层决策条目', '基线版本 BL-v1.0'],
    inputDemo: `用户口头要求：必须管住需求变更，不能口头说改就改。`,
    outputDemo: `P04 清单文件：docs/requirements/P04_baseline.md (含签字哈希)`
  },
  'ART-02': {
    name: '🎨 mockup.html / PRD / detail.md',
    category: 'artifact',
    categoryName: '📄 确定性物理产物',
    role: '产物管理',
    skill: 'design-spec-architect',
    sop: 'docs/design/detail.md',
    gate: 'DDL 与 DLD 一致性',
    principle: '包含可交互的纯 HTML 原型快照，以及包含数据库表结构 DDL 与 API 契约的详细设计说明书，作为前后端开发与联调的物理契约。',
    inputs: ['四层清单'],
    outputs: ['mockup.html 原型包', 'detail.md 详细设计', 'service.sql'],
    inputDemo: `冻结的四层清单条目清单与原型交互流`,
    outputDemo: `物理落盘文件: docs/design/detail.md + docs/db/service.sql`
  },
  'ART-03': {
    name: '💻 src/ 业务源码工程与联调日志',
    category: 'artifact',
    categoryName: '📄 确定性物理产物',
    role: '产物管理',
    skill: 'coding-standards',
    sop: 'base_template/backend & frontend',
    gate: '单测与编译通过',
    principle: '经过 TDD 红绿循环洗礼的业务源代码工程，附带第三方 API 的真实请求响应日志，绝不含伪造 Mock 代码。',
    inputs: ['detail.md 契约'],
    outputs: ['后端 Java / 前端 Vue 源码', '真实接口联调日志'],
    inputDemo: `DLD 接口规范与数据库表结构`,
    outputDemo: `源码资产: base_template/backend/src/... (含单测)`
  },
  'ART-04': {
    name: '🔒 gate.py LGTM 报告与 E2E 凭证',
    category: 'artifact',
    categoryName: '📄 确定性物理产物',
    role: '产物管理',
    skill: 'code-quality-reviewer',
    sop: 'target/gate-report.json',
    gate: 'LGTM 防伪签名',
    principle: '由机器门禁 gate.py 与端到端穿透测试共同生成的数字证明，具备不可篡改性，是准许进入终验的必备通行证。',
    inputs: ['业务源码', '测试用例'],
    outputs: ['门禁扫描报告 (LGTM)', 'E2E 穿透验证日志'],
    inputDemo: `全量源码提交暂存区与自动化测试用例`,
    outputDemo: `数字签名凭证: target/gate-lgtm.json (Hash: 9a7b...4f)`
  },
  'ART-05': {
    name: '🏁 board.py verify 凭据与 Git Tag',
    category: 'artifact',
    categoryName: '📄 确定性物理产物',
    role: '产物管理',
    skill: 'quality-verifier',
    sop: 'docs/releases/v1.0.0.md',
    gate: '双向验签锁定',
    principle: '包含 board.py verify 全量资产验证单、Git 语义化 Tag 标签与 Release 正式发布说明书，标志着需求正式生命周期闭环。',
    inputs: ['全部前置产物合集'],
    outputs: ['Git Tag 标签', 'Release 归档交付包'],
    inputDemo: `经过验证的交付包 + 发版说明书`,
    outputDemo: `发布标签: git tag -a v1.0.0 -m "Release v1.0.0"`
  }
}
