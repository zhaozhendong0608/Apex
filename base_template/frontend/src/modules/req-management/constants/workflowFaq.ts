/**
 * 工作流精选常见问题与权威解答 (Workflow FAQ Knowledge Base)
 * 沉淀体系总览、质量门禁、状态机流转、AI Skills 与老项目/排障相关核心解答
 */

export interface WorkflowFaqItem {
  id: string
  category: 'overview' | 'gate' | 'state' | 'skills' | 'troubleshoot'
  categoryName: string
  question: string
  summary: string
  tags: string[]
  answer: string
  bestPractice?: string
  relatedSop?: string
  commandExample?: string
}

export const WORKFLOW_FAQ_CATEGORIES = [
  { key: 'all', label: '全部问答', icon: '📚' },
  { key: 'overview', label: '体系导航与分流', icon: '🗺️' },
  { key: 'gate', label: '质量门禁与安检', icon: '🛡️' },
  { key: 'state', label: '状态机与看板流转', icon: '🔄' },
  { key: 'skills', label: 'AI Skills 与专家轨', icon: '🤖' },
  { key: 'troubleshoot', label: '线上排障与老项目', icon: '🔍' }
]

export const WORKFLOW_FAQ_DATA: WorkflowFaqItem[] = [
  {
    id: 'FAQ-001',
    category: 'overview',
    categoryName: '体系导航与分流',
    question: '我刚接手或开始一个新需求，该走哪条工作流指令？',
    summary: '根据需求规模 (S/M/L) 与变更烈度，智能选择标准主链、极速快轨或存量旁路。',
    tags: ['SOP-00', '自适应分流', '主轨与快轨'],
    relatedSop: '.ai/sop/00-sop-overview.md',
    answer: `工作流分为 **主链 ([1]~[7])**、**交付链 ([8]~[9])** 与 **旁路 ([B1]~[B6])** 三大轨道：
1. **小修小补/文案样式 (S 级)**：改动 ≤20 行、无数据库变动、无接口协议漂移，直接走 \`[B2]\` 极速快轨 (FastTrack)，免写 DLD，无需状态机打卡审批。
2. **单模块业务 (M 级)**：改动 ≤3 个文件、轻量增字段、无跨模块服务，走简化主链，免全局深度评审。
3. **复杂跨模块/DDL (L 级)**：涉及表结构变更、核心状态机或高风险链路，必须严格遵循 \`[1] 需求规划 ➔ [2] 详细设计 ➔ [3] TDD编码 ➔ [4] 机器门禁 ➔ [5] 评审 ➔ [6] 集成 ➔ [7] 验收\` 全套主链。
4. **人类特权覆盖**：系统若推荐快轨，但您认为影响重大，可一键或指令说明“改走标准流”，安检机会动态恢复严苛拦截。`,
    bestPractice: '在不确定时，在对话中输入 [0] 即可呼出体系全景图；或发 [1] 让 AI 通过反向质询问卷帮您梳理范围。',
    commandExample: 'python3 .ai/scripts/board.py list'
  },
  {
    id: 'FAQ-002',
    category: 'gate',
    categoryName: '质量门禁与安检',
    question: '门禁脚本 gate.py 报 BLOCKING 或 Exit 2 阻断时该如何解决？',
    summary: '区分 LGTM (0)、BLOCKING (1) 与异常 Exit 2 的物理含义与应对方案。',
    tags: ['gate.py', '门禁阻断', 'Exit 2', '代码规范'],
    relatedSop: '.ai/sop/04-sop-code-review.md',
    answer: `\`gate.py\` 是工程的核心质量安检机，常见状态码及应对策略如下：
1. **LGTM (退出码 0)**：全部安全检查通过，方可放行进入评审与归档。
2. **BLOCKING (退出码 1)**：发现硬性违规，如：
   - **硬编码密码/密钥**：触发密钥泄露红线，需将敏感配置挪入 \`.env\`。
   - **SQL 回滚配对缺失**：新增了 \`V*.sql\` 但缺少对应 \`rollback_*.sql\` 脚本，必须 1:1 配齐且保证非空。
   - **单测失败或 NPE/资源泄露风险**：静态审查发现潜在空指针或流未关闭，需先微创重构。
3. **Exit 2 (异常/空变更集)**：表示门禁根本没有执行！通常是因为没有暂存文件。**严禁把 Exit 2 当作通过**，必须先执行 \`git add\` 暂存后再执行 \`gate.py --staged\`。`,
    bestPractice: '编码完成后提交前，手动执行 python3 .ai/scripts/gate.py --staged 验证，修复全部 blocker 项。',
    commandExample: 'python3 .ai/scripts/gate.py --staged'
  },
  {
    id: 'FAQ-003',
    category: 'state',
    categoryName: '状态机与看板流转',
    question: '为什么严禁手动修改 .ai/tier3_status.md 看板文件？',
    summary: '物理锚点与状态机一致性保障，避免状态流转卡死与正则失效。',
    tags: ['board.py', '锚点机制', '单焦点原则', '状态机'],
    relatedSop: '.ai/sop/00-sop-overview.md',
    answer: `\`tier3_status.md\` 是工程状态机的物理事实源。早期版本通过纯文本正则匹配，极易因人类或 AI 误改标题缩进、换行或 Emoji 而导致脚本报错崩溃。
当前已全面重构为 **HTML 注释物理锚点法 (<!-- SECTION:XXX -->)**：
1. **单焦点原则 (Single Focus)**：系统在同一时刻只允许存在一个 \`ACTIVE\` 进行中任务，防止并发打断造成交付半成品。
2. **状态流转原子性**：任务的 \`TODO ➔ ACTIVE ➔ DONE\` 必须通过 \`board.py\` 脚本命令流转，脚本会自动更新耗时、打上时间戳并触发下游任务升级。
3. **自愈机制**：若锚点被意外破坏，可执行 \`board.py sync\` 触发自愈重建，但严禁手动编辑。`,
    bestPractice: '状态机操作必须通过命令行驱动，如 board.py advance、board.py complete 等。',
    commandExample: 'python3 .ai/scripts/board.py advance REQ-001 SOP-02'
  },
  {
    id: 'FAQ-004',
    category: 'skills',
    categoryName: 'AI Skills 与专家轨',
    question: 'AI Skills 专家轨与传统 Prompt 模板有何本质区别？',
    summary: '包含系统级强约束 SKILL.md、输入输出契约、配套脚本与沙盒执行能力。',
    tags: ['AI Skills', '架构规范', '强约束', '反向质询'],
    relatedSop: '.agents/skills/',
    answer: `AI Skills 不是简单的系统提示词，而是具备**确定性工程约束**的模块化专职角色：
1. **强契约绑定**：例如 \`coding-standards\` 强约束 AI 逐字绑定 DLD 契约中的表名、字段名与 HTTP 状态码，禁止 AI 凭想象力创造伪数据或臆造接口。
2. **反向质询规约 (Grill Protocol)**：如 \`requirement-discovery\` 在需求模糊时，先在内部推演边界，只输出高价值 2~3 个场景选择题反逼出客户真实隐性需求。
3. **工具与脚本自驱动**：每个专家自带校验脚本（如 \`quality-verifier\` 配套探针排错），形成独立可复用的能力胶囊。`,
    bestPractice: '在开发具体阶段，AI 会自动按工位挂载对应的 Skill 专家，确保每个步骤有据可循。',
    commandExample: 'ls -la .agents/skills/'
  },
  {
    id: 'FAQ-005',
    category: 'troubleshoot',
    categoryName: '线上排障与老项目',
    question: '遇到老项目缺乏文档、代码庞杂无从下手，如何使用工作流“破局”？',
    summary: '运用 [B1] SOP 与四维元数据切片 (路由/Controller/Service/DAO)，切忌盲目大范围重构。',
    tags: ['B1-sop-legacy', 'arch.py', '老项目破局', '行为锁死'],
    relatedSop: '.ai/sop/B1-sop-legacy.md',
    answer: `针对遗留项目，严禁一上来就通篇通读或大刀阔斧重构：
1. **呼出老项目破局指令**：在对话中发送 \`[B1]\`，激活 \`legacy-archaeologist\` 专家。
2. **三层穿透与四维建图**：运行 \`python3 .ai/scripts/arch.py\`，自顶向下抓取后端路由、接口契约及表关系，生成 \`tier2_legacy_arch.md\` 切片地图。
3. **编写行为锁死探针**：在动手修改前，针对关键存量逻辑编写 Characterization Tests（反向行为锁死单测），确保改动不破坏原系统黑盒行为。
4. **最小改动原则 (Minimum Surgery)**：每次仅针对微小切片改造，切忌“顺手重构无关代码”。`,
    bestPractice: '老项目改造务必坚持“先加测试锁死旧行为 ➔ 再做微创替换 ➔ 门禁验签”三部曲。',
    commandExample: 'python3 .ai/scripts/arch.py analyze'
  },
  {
    id: 'FAQ-006',
    category: 'troubleshoot',
    categoryName: '线上排障与老项目',
    question: '线上环境突发故障 (500/OOM/死锁) 时，[B4] 排障 SOP 的执行铁律是什么？',
    summary: '“未取证不改码”、“先保现场再修复”、“修复必带防御性单测”三大铁律。',
    tags: ['B4-sop-troubleshoot', '线上故障', '排障铁律', '防重放'],
    relatedSop: '.ai/sop/B4-sop-troubleshoot.md',
    answer: `线上突发故障与本地 Debug 不同，必须严格遵守 3 条铁律：
1. **严禁无头苍蝇式盲猜**：未在真实日志或堆栈中定位到精准报错行之前，绝不许修改核心业务代码。
2. **先保现场严禁盲目重启**：遭遇 OOM、高 CPU 或死锁时，先保存 Thread Dump、Heap Dump 与 Tail 日志快照，否则重启后证据丢失无法追查根因。
3. **修复必带防御单测 (防重放)**：必须补一条能 100% 稳定复现此故障的自动化测试用例，经历“红 (复现) ➔ 绿 (修复)”闭环后才准许合入主干。`,
    bestPractice: '按照定级与影响面 ➔ 日志穿透 ➔ 性能深探 ➔ 微创修复四步流水线操作，交付 ≤5 行白话归档卡片。',
    commandExample: 'python3 .ai/scripts/test_runner.py'
  },
  {
    id: 'FAQ-007',
    category: 'gate',
    categoryName: '质量门禁与安检',
    question: '如何处理长对话后 AI 遗忘工作流规则或发生“协议逃逸”？',
    summary: '运用“三层记忆金字塔”与 [B5]/[B6] 机制极速复活，解决长对话 Token 膨胀。',
    tags: ['三层记忆', '断点复活', 'B5归档', 'B6恢复', 'Token优化'],
    relatedSop: '.ai/sop/B6-sop-resume.md',
    answer: `当对话轮次超过 20 轮，大模型注意力机制会发生概率漂移，出现遗忘规则的“协议逃逸”：
1. **三层记忆金字塔架构**：
   - **第一层 (\`.ai/tier1_snapshot.md\`)**：永远 <30 行的高能快照名片，仅耗费 300 Token，0.5 秒极速吃透工程现状与当前唯一焦点。
   - **第二层 (\`tier2_modules.md\` / \`docs/modules/\`)**：宏观架构与四件套模块地图。
   - **第三层 (\`tier3_status.md\` / \`handover.md\`)**：微观原子任务与历史交付黑匣子。
2. **极速轮转与复活**：下班或会话变长时输入 \`[B5]\` 归档；新开窗口或第二天开工第一句话输入 \`[B6]\`，AI 将仅读取 tier1 快照名片在 1 秒内满血复活，彻底规避长上下文漂移！`,
    bestPractice: '保持短平快会话，养成一个 Task 一归档、新开窗口发 [B6] 的良好工程习惯。',
    commandExample: 'head -n 30 .ai/tier1_snapshot.md'
  }
]
