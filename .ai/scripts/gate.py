#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Apex 统一质量门禁 (Quality Gate Orchestrator) v2.0 —— 语言无关骨架 + 可插拔 Linter
供 [7/CR] 代码审查、pre-commit 钩子与 [7] 终验机器核验调用。

v2.0 相比 v1 的修复
-------------------
1. **空变更集不再假绿灯**：0 个文件 → `[BLOCKING]` + 退出码 2（真没东西可查 ≠ 全部门禁通过）。
2. **SQL 回滚路径对齐 SOP**：同时扫描 `docs/db/migrations`、`docs/db`、`docs/sql`、`docs/deploy`，
   不再出现"按 SOP 落盘却永远查不到"的静默跳过。
3. **linter 全量遍历**：v1 每种扩展名只 lint 第一个文件（`fgroup[0]`），现已遍历全部文件。
4. **新增密钥泄露扫描**：呼应 quality-verifier 的 secret-guard-checklist，
   拦截硬编码密码 / API Key / 私钥 / 带凭据连接串。
5. **新增工作流元门禁 `--meta`**：编译校验 `.ai/scripts` 与 `.agents/skills/**/scripts` 下所有 Python，
   并校验文档内的本地 Markdown 链接是否真实存在 —— 让"工作流自身"也接受门禁检查。

用法
----
  python3 .ai/scripts/gate.py <文件或目录>...   # 显式指定变更集
  python3 .ai/scripts/gate.py --staged          # 自动取 git 暂存区变更 (pre-commit)
  python3 .ai/scripts/gate.py --sql             # 仅执行 SQL 回滚配对检查
  python3 .ai/scripts/gate.py --meta            # 工作流自检：脚本可编译 + 文档链接 + 能力点基线
  python3 .ai/scripts/gate.py --sop             # 只校验 SOP 能力点基线（防精简丢功能）
  python3 .ai/scripts/gate.py --refs            # 只校验指令引用完整性（编号漂移/幽灵引用/旧号残留）
  python3 .ai/scripts/gate.py --stage 3 [--module <m>]   # 进入 [3] 前的前置检查（跳步检测器）
  python3 .ai/scripts/gate.py --build           # 追加快速预编译检查 (pytest / npm)
  python3 .ai/scripts/gate.py --no-lint         # 跳过可选 linter
  python3 .ai/scripts/gate.py --strict          # linter 失败也视为阻断 (默认仅告警)
  python3 .ai/scripts/gate.py --large           # 豁免"最小改动"文件数告警

退出码
------
  0 = [LGTM] 全部通过
  1 = [BLOCKING] 有阻断项，严禁合入主干
  2 = 用法错误 / 空变更集（门禁未真正执行，不得视为通过）
"""

import sys
import os
import re
import subprocess
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

# 套件植入结构固定: <project_root>/.ai/scripts/gate.py -> 以脚本自身定位 .ai 根，杜绝 CWD 漂移
AI_DIR = Path(__file__).resolve().parent.parent
SCRIPTS_DIR = AI_DIR / "scripts"
PROJECT_BY_SCRIPT = AI_DIR.parent

# ---- 看门狗阈值与规则常量 ----
MIN_SURGERY_FILE_LIMIT = 3          # 单次改动文件数软上限（超过则告警，可用 --large 豁免）
EXCLUDED_DIRS = {".git", ".idea", ".vscode", "node_modules", "dist", "build",
                 "target", "__pycache__", ".venv", "env", ".DS_Store", ".ai", ".agents", "assets", "training"}
EXCLUDED_FILES = {"gate.py", "board.py", "sync_tree.py", "arch.py"}   # 工作流脚本自身不计入看门狗统计

# 假 Mock / 兜底假数据特征（命中即视为阻断候选，测试文件豁免；原型文件如 mockup 除外）
MOCK_PATTERNS = [
    re.compile(r"(?i)\b(mock(?!up)|fake|dummy|stub)"),   # 匹配 mock/fake，但放行 mockup 原型词汇
    re.compile(r"(?i)(假数据|兜底|伪造|保底)"),
    re.compile(r"(?i)return\s+(true|false|null|none|\[\]|\{\})\s*;?\s*//\s*(mock|fake|stub|兜底|假)"),
]
# 公共资产（基类/全局拦截器/中间件/全局配置/公共 Util/通用响应结构）路径特征
PUBLIC_ASSET_PATTERNS = [
    re.compile(r"(?i)(^|/)(base|global|config|util|utils|common|interceptor|middleware|filter|constants?)(/|$)"),
    re.compile(r"(?i)(result|response|dto|vo)\.?(ts|java|py|go|js)$"),
]
# 公网依赖特征（CDN script/link、import from url、@import url(http)）
PUBLIC_NET_PATTERNS = [
    re.compile(r"https?://[^\s\"'`]+\.(js|css|woff2?|ttf|png|jpg|svg|ico)"),
    re.compile(r"(?i)import\s+.+\s+from\s+['\"](https?:|//)"),
    re.compile(r"(?i)@import\s+url\(\s*['\"]?https?://"),
    re.compile(r"(?i)<script[^>]+src\s*=\s*['\"]https?://"),
    re.compile(r"(?i)<link[^>]+href\s*=\s*['\"]https?://"),
]
# 硬编码密钥特征（呼应 secret-guard-checklist；示例/模板文件豁免）
SECRET_PATTERNS = [
    re.compile(r"(?i)(password|passwd|pwd)\s*[:=]\s*[\"'][^\"'\s]{4,}[\"']"),
    # --- 常见平台令牌（原版只认 AKIA，漏了 GitHub / OpenAI / Slack）---
    re.compile(r"\bgh[pousr]_[A-Za-z0-9]{20,}\b"),                 # GitHub PAT / OAuth / App
    re.compile(r"\bgithub_pat_[A-Za-z0-9_]{20,}\b"),                # GitHub fine-grained
    re.compile(r"\bsk-[A-Za-z0-9_\-]{20,}\b"),                     # OpenAI / 通用 sk-
    re.compile(r"\bxox[baprs]-[A-Za-z0-9\-]{10,}\b"),              # Slack
    # --- 通用：任意 *token/*secret/*key = "长串字面量" ---
    re.compile(r"(?i)\b[A-Za-z0-9_]*?(token|secret|passwd|password|api_?key)\s*[:=]\s*[\"'][A-Za-z0-9_\-\.\+/]{16,}[\"']"),
    re.compile(r"(?i)(api[_-]?key|secret[_-]?key|access[_-]?key|auth[_-]?token)\s*[:=]\s*[\"'][^\"'\s]{8,}[\"']"),
    re.compile(r"(?i)^\s*(password|passwd|pwd|secret|token)\s*[:=]\s*(?![\"'$<*])(\S{6,})\s*$", re.MULTILINE),
    re.compile(r"-----BEGIN (RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----"),
    re.compile(r"(?i)(jdbc|mysql|postgresql|mongodb|redis)://[^:\s/]+:[^@\s/]{3,}@"),
    re.compile(r"\bAKIA[0-9A-Z]{16}\b"),
]
SECRET_EXEMPT_NAME = re.compile(r"(?i)(example|sample|template|placeholder|\.env\.example)")
SECRET_PLACEHOLDER = re.compile(r"(?i)(\$\{|\{\{|^<|^your|^xxx|^\*+|^change[_-]?me|placeholder|示例|占位)")
SECRET_SCAN_EXTS = {".py", ".js", ".ts", ".tsx", ".jsx", ".java", ".go", ".vue", ".yml", ".yaml",
                    ".properties", ".env", ".ini", ".conf", ".toml", ".xml", ".sh", ".sql"}
# 依赖清单增量新增行特征（用于「零新依赖」核验）
DEP_MANIFESTS = {
    "requirements.txt": re.compile(r"^\+[A-Za-z0-9_.\-]+[=><!~]"),
    "package.json": re.compile(r"^\+\s*\"[^\"]+\"\s*:\s*\"[^\"]+\""),
    "pom.xml": re.compile(r"^\+\s*<artifactId>"),
    "go.mod": re.compile(r"^\+[A-Za-z0-9_./-]+\s+v[0-9]"),
    "Cargo.toml": re.compile(r"^\+\s*[A-Za-z0-9_\-]+\s*=\s*\""),
    "pyproject.toml": re.compile(r"^\+\s*[A-Za-z0-9_\-]+"),
}
# 测试文件豁免假 Mock 扫描
TEST_FILE_RE = re.compile(r"(?i)(test|spec|__tests__|fixture|mock|conftest)")

# 可插拔 linter 的 CLI 探测表（ext -> (探测命令, 构造参数函数)）
LINTER_CLI = {
    ".py": (["ruff"], lambda f: ["ruff", "check", str(f)]),
    ".js": (["eslint"], lambda f: ["eslint", str(f)]),
    ".ts": (["eslint"], lambda f: ["eslint", str(f)]),
    ".tsx": (["eslint"], lambda f: ["eslint", str(f)]),
    ".jsx": (["eslint"], lambda f: ["eslint", str(f)]),
    ".vue": (["eslint"], lambda f: ["eslint", str(f)]),
    ".go": (["golangci-lint"], lambda f: ["golangci-lint", "run", str(f)]),
}

# 文档一致性自检范围（--meta）
DOC_LINK_ROOTS = [
    AI_DIR / "sop",
    AI_DIR,
    PROJECT_BY_SCRIPT,
]


def resolve_project_root() -> Path:
    """定位目标工程根目录: PROJECT_ROOT 环境变量 > 脚本所在工程 > CWD"""
    env_root = os.environ.get("PROJECT_ROOT")
    if env_root and Path(env_root).exists():
        return Path(env_root).resolve()
    if (PROJECT_BY_SCRIPT / ".ai").exists():
        return PROJECT_BY_SCRIPT.resolve()
    if (Path.cwd() / ".ai").exists():
        return Path.cwd().resolve()
    return Path.cwd().resolve()


def collect_staged_files(project_root: Path):
    """从 git 暂存区获取变更文件清单 (相对 project_root)"""
    try:
        out = subprocess.run(
            ["git", "diff", "--cached", "--name-only", "--diff-filter=ACMR"],
            cwd=str(project_root), capture_output=True, text=True, check=True,
        ).stdout
        return [line.strip() for line in out.splitlines() if line.strip()]
    except Exception as e:
        print(f"❌ 无法读取 git 暂存区 (是否在 git 仓库内执行?): {e}")
        sys.exit(2)


def expand_inputs(inputs, project_root: Path):
    """将文件/目录参数展开为具体文件列表，强制相对 project_root 防漂移"""
    files = []
    for item in inputs:
        p = Path(item)
        if not p.is_absolute():
            if (project_root / p).exists():
                p = project_root / p
            elif (Path.cwd() / p).exists():
                p = Path.cwd() / p
            else:
                p = project_root / p
        if p.is_dir():
            files.extend(f for f in p.rglob("*") if f.is_file())
        elif p.is_file():
            files.append(p)
        else:
            print(f"⚠️ 跳过不存在的路径: {item}")
    return files


def is_excluded(f: Path) -> bool:
    if any(part.lower() in EXCLUDED_DIRS for part in f.parts):
        return True
    if f.name in EXCLUDED_FILES:
        return True
    return False


def read_text(f: Path) -> str:
    try:
        return f.read_text(encoding="utf-8", errors="replace")
    except Exception:
        return ""


# ---------------------------------------------------------------------------
# 看门狗核验项
# ---------------------------------------------------------------------------

def check_min_surgery(files, allow_large: bool):
    """最小改动原则：已根据团队要求移除该上限限制"""
    return True, []


def check_zero_new_deps(files, project_root: Path):
    """零新依赖原则：扫描依赖清单相对 HEAD 的增量新增行"""
    violations = []
    for f in files:
        name = f.name
        if name not in DEP_MANIFESTS:
            continue
        try:
            res = subprocess.run(
                ["git", "diff", "--unified=0", "HEAD", "--", str(f)],
                cwd=str(project_root), capture_output=True, text=True, encoding="utf-8", errors="replace",
            )
            if res.returncode != 0:
                continue
            added = [ln for ln in res.stdout.splitlines() if ln.startswith("+") and not ln.startswith("+++")]
        except Exception:
            continue
        pat = DEP_MANIFESTS[name]
        new_deps = [ln for ln in added if pat.search(ln)]
        if new_deps:
            preview = "; ".join(d.strip("+").strip() for d in new_deps[:3])
            violations.append(
                f"🔴 [{name}] 检测到新增依赖（未经授权）: {preview} ... "
                f"如需引入，请先向用户显式申请授权，不要私自 install。")
    return (len(violations) == 0), violations


def check_no_mock(files):
    """严禁保底 Mock 假数据：扫源码命中特征即阻断（测试文件豁免）"""
    violations = []
    for f in files:
        if TEST_FILE_RE.search(f.name) or any("test" in p.lower() for p in f.parts):
            continue
        if f.suffix.lower() not in {".py", ".js", ".ts", ".tsx", ".jsx", ".java", ".go", ".vue", ".rb", ".cs"}:
            continue
        text = read_text(f)
        for pat in MOCK_PATTERNS:
            for m in pat.finditer(text):
                line_no = text[:m.start()].count("\n") + 1
                snippet = text.splitlines()[line_no - 1].strip()[:80]
                violations.append(f"🔴 {f.name}:{line_no} 疑似保底 Mock/假数据: {snippet}")
                break
    return (len(violations) == 0), violations


def check_no_public_net(files):
    """零公网依赖：扫源码/模板中的外链 CDN / import-from-url"""
    violations = []
    for f in files:
        if f.suffix.lower() not in {".py", ".js", ".ts", ".tsx", ".jsx", ".vue", ".html", ".css", ".scss", ".go", ".java"}:
            continue
        text = read_text(f)
        for pat in PUBLIC_NET_PATTERNS:
            for m in pat.finditer(text):
                line_no = text[:m.start()].count("\n") + 1
                snippet = text.splitlines()[line_no - 1].strip()[:90]
                violations.append(f"🔴 {f.name}:{line_no} 检测到公网依赖: {snippet}")
                break
    return (len(violations) == 0), violations


def check_secrets(files):
    """密钥泄露扫描：拦截硬编码密码/API Key/私钥/带凭据连接串（示例模板豁免）"""
    violations = []
    seen = set()
    for f in files:
        if SECRET_EXEMPT_NAME.search(f.name):
            continue
        if f.suffix.lower() not in SECRET_SCAN_EXTS:
            continue
        text = read_text(f)
        for pat in SECRET_PATTERNS:
            for m in pat.finditer(text):
                line_no = text[:m.start()].count("\n") + 1
                if (f.name, line_no) in seen:      # 同一行被多个正则命中只报一次
                    break
                line = text.splitlines()[line_no - 1].strip()
                if SECRET_PLACEHOLDER.search(line):
                    continue
                seen.add((f.name, line_no))
                violations.append(f"🔴 {f.name}:{line_no} 疑似硬编码密钥/凭据: {line[:70]}")
                break
    return (len(violations) == 0), violations


def check_public_asset_impact(files):
    """公共资产改动影响自判：触碰基类/全局配置/Util 时要求出具影响清单（告警）"""
    warnings = []
    hit = [f for f in files if any(p.search(str(f)) for p in PUBLIC_ASSET_PATTERNS)]
    if hit:
        names = ", ".join(f.name for f in hit[:5])
        warnings.append(
            f"⚠️ 本次改动触及公共资产: {names}。请在交付卡片中列出受影响的上游模块与全量回归 "
            f"Checklist（用 grep 扫描所有调用方），杜绝全局静默破坏。")
    return True, warnings


# ---------------------------------------------------------------------------
# 可插拔 Linter（v2：遍历同扩展名的全部文件）
# ---------------------------------------------------------------------------

def run_pluggable_linter(files, strict: bool, no_lint: bool):
    """优先 .ai/scripts/linter_<ext>.py，否则尝试 PATH 中的 CLI；缺失则软跳过"""
    if no_lint:
        return True, ["⏭️  [LINT] 已通过 --no-lint 跳过"]
    by_ext = {}
    for f in files:
        by_ext.setdefault(f.suffix.lower(), []).append(f)

    outputs = []
    hard_fail = False          # 自家 linter 的错误级问题 → 必须硬阻断
    for ext, fgroup in sorted(by_ext.items()):
        # 1) 工程内自定义 linter 脚本优先（一次传入该扩展名的全部文件）
        #    js 家族（js/ts/tsx/jsx/vue）共用 linter_js.py
        custom = SCRIPTS_DIR / f"linter_{ext.lstrip('.')}.py"
        if not custom.exists() and ext in {".js", ".ts", ".tsx", ".jsx", ".vue"}:
            custom = SCRIPTS_DIR / "linter_js.py"
        if custom.exists():
            out = _run_cmd([sys.executable, str(custom)] + [str(x) for x in fgroup],
                           f"LINT(custom:{ext})")
            outputs.append(out)
            if out.startswith("🔴"):
                hard_fail = True
            continue
        # 2) PATH 中的常见 CLI
        if ext in LINTER_CLI:
            probe_cmd, build_fn = LINTER_CLI[ext]
            if _cmd_exists(probe_cmd[0]):
                for target in fgroup:            # ← v2：逐个文件全量 lint
                    outputs.append(_run_cmd(build_fn(target), f"LINT({ext}:{target.name})"))
            else:
                outputs.append(f"⏭️  [LINT({ext})] 未检测到 linter CLI ({probe_cmd[0]})，跳过（软）")
        else:
            outputs.append(f"⏭️  [LINT({ext})] 无对应 linter 配置，跳过")

    all_passed = not any(line.startswith("🔴") for line in outputs)
    if hard_fail:
        # 工程内 linter（我们自己的规则）报的错误级问题 → 不受 --strict 影响，直接阻断
        return False, outputs
    if not strict and not all_passed:
        # 外部 CLI linter 缺失/不兼容时保持"软"，避免环境差异挡住提交
        return True, [f"⚠️ {o}" if o.startswith("🔴") else o for o in outputs]
    return all_passed, outputs


def _cmd_exists(cmd: str) -> bool:
    from shutil import which
    return which(cmd) is not None


def _run_cmd(cmd, label: str) -> str:
    try:
        res = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    except Exception as e:
        return f"⏭️  [{label}] 执行异常: {e}"
    out = (res.stdout or "") + (res.stderr or "")
    if res.returncode == 0:
        return f"✅ [{label}] 通过" + (f"\n{out.strip()}" if out.strip() else "")
    return f"🔴 [{label}] 未通过\n{out.strip()[:800]}"


# ---------------------------------------------------------------------------
# SQL 回滚配对（v2：目录与 SOP 09/10 对齐）
# ---------------------------------------------------------------------------

def _sql_search_dirs(project_root: Path):
    return [project_root / rel for rel in
            ("docs/db/migrations", "docs/db", "docs/sql", "docs/deploy")]


def check_sql_rollback(project_root: Path, target_sql_files=None):
    dirs = [d for d in _sql_search_dirs(project_root) if d.exists()]
    if not dirs:
        return True, ["⏭️  [SQL] 未检测到 docs/db/migrations、docs/db、docs/sql、docs/deploy 任一目录，跳过（无库工程）"]

    if target_sql_files is not None:
        sql_files = [f for f in target_sql_files if f.suffix.lower() == ".sql"]
        if not sql_files:
            return True, ["⏭️  [SQL] 当前变更集不含 SQL 脚本，跳过"]
    else:
        sql_files = []
        for d in dirs:
            sql_files.extend(d.rglob("*.sql"))
        sql_files = sorted(set(sql_files))
        if not sql_files:
            return True, [f"⏭️  [SQL] 已检查 {len(dirs)} 个目录，未发现任何 SQL 脚本（无迁移变更）"]

    violations, passes = [], []
    for sql_file in sql_files:
        name = sql_file.name
        if name.startswith("rollback_") or name.startswith("init-") or "init" in sql_file.parts:
            continue
        # base.sql 与 service.sql 为全量全景底座脚本，免回滚配对约束
        if name in ("base.sql", "service.sql"):
            passes.append(f"✅ 全量底座: {name}")
            continue
        rollback = sql_file.parent / f"rollback_{name}"
        if not rollback.exists():
            violations.append(f"缺失回滚脚本: {name} -> 期望 rollback_{name}")
        elif rollback.stat().st_size == 0:
            violations.append(f"回滚脚本为空: rollback_{name}")
        else:
            passes.append(f"✅ 已配对: {name}")
    if violations:
        return False, [f"🔴 {v}" for v in violations] + \
               ["🚫 铁律: 所有 DDL/DML 变更必须配套可执行的 Rollback SQL。"]
    return True, (passes[:6] + [f"✅ [SQL] 共 {len(passes)} 个升级脚本均已配对非空回滚脚本"])


# ---------------------------------------------------------------------------
# 预编译 (--build, 可选)
# ---------------------------------------------------------------------------

def check_compilation(project_root: Path, files):
    print("\n───── [BUILD] 快速预编译检查 ─────")
    exts = {f.suffix.lower() for f in files}
    all_passed = True
    msgs = []

    if ".py" in exts:
        if _cmd_exists("pytest"):
            res = subprocess.run(["pytest", "--collect-only", "-q"], cwd=str(project_root),
                                 capture_output=True, text=True, encoding="utf-8", errors="replace")
            if res.returncode == 0:
                msgs.append("✅ [BUILD-PY] pytest 收集通过")
            else:
                all_passed = False
                msgs.append("🔴 [BUILD-PY] pytest 收集失败:\n" + (res.stderr or res.stdout)[-400:])
        else:
            msgs.append("⏭️  [BUILD-PY] 未检测到 pytest，跳过")
    if exts & {".js", ".ts", ".tsx", ".jsx", ".vue"}:
        if (project_root / "package.json").exists():
            res = subprocess.run(["npm", "run", "build"], cwd=str(project_root),
                                 capture_output=True, text=True, encoding="utf-8", errors="replace")
            if res.returncode == 0:
                msgs.append("✅ [BUILD-NPM] 前端打包编译通过")
            else:
                all_passed = False
                msgs.append("🔴 [BUILD-NPM] 前端打包失败:\n" + (res.stdout + res.stderr)[-400:])
        else:
            msgs.append("⏭️  [BUILD-NPM] 无 package.json，跳过")
    if not exts:
        msgs.append("⏭️  [BUILD] 无代码文件变更，跳过")
    return all_passed, msgs


# ---------------------------------------------------------------------------
# 工作流元门禁 (--meta)：脚本可编译 + 文档链接不悬空
# ---------------------------------------------------------------------------

MD_LINK_RE = re.compile(r"\[[^\]]*\]\(([^)\s]+)\)")
FENCE_RE = re.compile(r"```.*?```", re.DOTALL)


def check_meta_scripts():
    """内存编译校验工作流自身脚本（不落 .pyc，避免污染与只读目录问题）"""
    targets = sorted(SCRIPTS_DIR.glob("*.py"))
    skills_dir = PROJECT_BY_SCRIPT / ".agents" / "skills"
    if skills_dir.exists():
        targets += sorted(skills_dir.glob("*/scripts/*.py"))

    msgs, failed = [], []
    for py in targets:
        try:
            compile(read_text(py), str(py), "exec")
            msgs.append(f"✅ 语法通过: {py.relative_to(PROJECT_BY_SCRIPT)}")
        except SyntaxError as e:
            failed.append(f"🔴 语法错误: {py.relative_to(PROJECT_BY_SCRIPT)} 第 {e.lineno} 行: {e.msg}")
    if not targets:
        msgs.append("⏭️  [META] 未发现工作流脚本")
    return (len(failed) == 0), failed + msgs


def check_doc_links():
    """校验文档内的本地 Markdown 链接是否真实存在（防幽灵文件/悬空引用）"""
    docs = []
    for root in DOC_LINK_ROOTS:
        if not root.exists():
            continue
        for md in sorted(root.glob("*.md")):
            docs.append(md)
        for md in sorted(root.glob("sop/*.md")):
            if md not in docs:
                docs.append(md)

    missing, checked = [], 0
    for md in docs:
        text = FENCE_RE.sub("", read_text(md))      # 剔除代码块，避免把示例当链接
        for raw in MD_LINK_RE.findall(text):
            target = raw.split("#")[0].strip()
            if (not target
                    or target.startswith(("http://", "https://", "mailto:", "#", "/"))
                    or any(ch in target for ch in ("<", ">", "*", "{", "}"))):
                continue
            checked += 1
            resolved = (md.parent / target).resolve()
            if not resolved.exists():
                missing.append(f"🔴 {md.relative_to(PROJECT_BY_SCRIPT)} → 链接失效: {target}")

    if missing:
        return False, missing + [f"🚫 共校验 {checked} 条本地链接，{len(missing)} 条悬空。"]
    return True, [f"✅ [DOCS] 共校验 {checked} 条本地链接，全部有效"]


# ---------------------------------------------------------------------------
# SOP 能力点基线校验（--sop）：防止"精简文档"变成"删除功能"
# ---------------------------------------------------------------------------

CAP_FILE = AI_DIR / "sop" / "_capability-baseline.md"
CAP_ID_RE = re.compile(r"C-[0-9A-Za-z]+-\d+")
CAP_BLOCK_RE = re.compile(r"^##\s*🧷\s*能力点.*?$(.*?)(?=^##\s|\Z)", re.MULTILINE | re.DOTALL)


def _cap_desc_from_sop(text, cid):
    """从 SOP 的能力点区块里抽出某个 ID 后面跟着的中文描述。"""
    block = CAP_BLOCK_RE.search(text)
    if not block:
        return "待补充描述"
    m = re.search(re.escape(cid) + r"`?\s*([^；;\n|]*)", block.group(1))
    desc = (m.group(1).strip(" `；;·-。") if m else "").strip()
    return desc or "待补充描述"


def check_sop_capabilities():
    """基线里的每个能力点 ID，必须在「归属 SOP 文件」的能力点区块中被声明。"""
    if not CAP_FILE.exists():
        return False, [f"🔴 缺少能力点基线文件: {CAP_FILE.name}"]

    text = read_text(CAP_FILE)
    baseline = {}          # id -> owner filename
    dup = []
    for line in text.splitlines():
        if not line.strip().startswith("|"):
            continue
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) < 3:
            continue
        cid = CAP_ID_RE.search(cells[0])
        if not cid:
            continue
        cid = cid.group(0)
        owner = cells[2].strip("` ")
        if cid in baseline:
            dup.append(f"🔴 基线重复登记: {cid}")
        baseline[cid] = owner

    if dup:
        return False, dup
    if not baseline:
        return False, ["🔴 基线文件解析不到任何能力点 ID（格式应为 | `C-xx-01` | 描述 | 文件 | 出处 |）"]

    declared = {}          # id -> set(owner files)
    sop_text = {}          # filename -> 正文（用于生成补登建议）
    missing_block = []
    sop_dir = AI_DIR / "sop"
    for md in sorted(sop_dir.glob("*.md")):
        if md.name.startswith("_"):
            continue
        body = read_text(md)
        sop_text[md.name] = body
        block = CAP_BLOCK_RE.search(body)
        if not block:
            missing_block.append(f"🔴 {md.name} 缺少「## 🧷 能力点」区块")
            continue
        for cid in CAP_ID_RE.findall(block.group(1)):
            declared.setdefault(cid, set()).add(md.name)

    problems = list(missing_block)
    orphan_rows, lost_ids = [], []
    for cid, owner in baseline.items():
        if cid not in declared:
            problems.append(f"🔴 能力点丢失: {cid}（基线归属 {owner}，但没有任何 SOP 声明它）")
            lost_ids.append((cid, owner))
        elif owner not in declared[cid]:
            problems.append(f"🔴 归属错位: {cid} 基线属 {owner}，实际声明在 {', '.join(sorted(declared[cid]))}")
    for cid, owners in declared.items():
        if cid not in baseline:
            owner = sorted(owners)[0]
            desc = _cap_desc_from_sop(sop_text.get(owner, ""), cid)
            problems.append(f"🔴 未登记能力点: {cid}（出现在 {', '.join(sorted(owners))}，请补进基线）")
            orphan_rows.append(f"| `{cid}` | {desc} | {owner} | 新增 |")

    if not problems:
        return True, [f"✅ [SOP] 能力点基线 {len(baseline)} 项全部有归属、无丢失、无未登记"]

    # ---- 给出可复制粘贴的修复指引 ----
    hints = []
    if orphan_rows:
        hints.append("")
        hints.append("👉 新增能力点：把下面几行复制到基线「📋 能力点清单」表末尾，并把最后一列改成真实出处：")
        hints.extend(orphan_rows[:10])
    if lost_ids:
        hints.append("")
        hints.append("👉 能力点丢失：二选一（不许只删基线了事）——")
        for cid, owner in lost_ids[:10]:
            hints.append(f"   - 恢复 {cid} 的规则到 {owner}；或")
            hints.append(f"   - 在基线把该行改为 ~~`{cid}`~~ **已废弃**（替代：`C-xx-yy`），保留删除痕迹")
    hints.append("")
    hints.append("📌 基线是「功能不缩水」的合同，**不自动跟随 SOP 变更**——否则它永远与 SOP 一致，等于没有保护作用。")

    return False, problems[:30] + hints


# ---------------------------------------------------------------------------
# 指令引用完整性（--refs）：防止编号漂移 / 幽灵引用 / 半迁移
# ---------------------------------------------------------------------------

SOP_FILE_KEY = re.compile(r"^([0-9]{1,2}|B[0-9])-sop-")
SOP_REF = re.compile(r"(?<![\w\]])\[(1\+|B[0-9]|[0-9]{1,2})\]")
VERBOSE_SKIP = {"docs/plans", "node_modules", ".git", "dist", "build", "target", "__pycache__"}


def _scan_targets(project_root: Path):
    files = []
    files += sorted((AI_DIR / "sop").glob("*.md"))
    files += sorted(AI_DIR.glob("*.md"))
    files += sorted(project_root.glob("*.md"))
    files += sorted((project_root / "docs" / "playbooks").glob("*.md"))
    files += [project_root / ".cursorrules", project_root / ".windsurfrules"]
    files += [project_root / "workflow_guide.html"]
    files += sorted(SCRIPTS_DIR.glob("*.py"))
    files += [project_root / "base_template" / ".cursorrules",
              project_root / "base_template" / ".windsurfrules"]
    out = []
    for f in files:
        if not f or not f.exists():
            continue
        rel = str(f.relative_to(project_root)) if str(f).startswith(str(project_root)) else str(f)
        if any(k in rel for k in VERBOSE_SKIP):
            continue
        out.append(f)
    return out


def check_sop_refs(project_root: Path):
    """校验：① 每个 [N]/[Bn] 都有对应 SOP 文件；② 每个 SOP 都被引用过；③ 旧编号零残留。"""
    sop_keys = {}
    for md in sorted((AI_DIR / "sop").glob("*.md")):
        if md.name.startswith("_"):
            continue
        m = SOP_FILE_KEY.match(md.name)
        if m:
            key = m.group(1)
            if key.isdigit():          # 文件名零填充 00..09 → 指令 0..9
                key = str(int(key))
            sop_keys[key] = md.name
    if not sop_keys:
        return False, ["🔴 未解析到任何 SOP 文件（命名应为 <指令>-sop-<名称>.md）"]

    allowed = set(sop_keys) | {"1+"}      # 1+ 是 1 的扩展别名，无独立文件
    legacy = {"0L", "F", "10", "11", "12", "13", "00"}
    invalid, legacy_hits, referenced = [], [], set()
    scanned = _scan_targets(project_root)

    for f in scanned:
        text = read_text(f)
        rel = f.relative_to(project_root) if str(f).startswith(str(project_root)) else f
        for ln, line in enumerate(text.splitlines(), 1):
            for key in SOP_REF.findall(line):
                if key in legacy:
                    legacy_hits.append(f"🔴 {rel}:{ln} 旧编号 [{key}] 残留（已废弃）: {line.strip()[:60]}")
                elif key not in allowed:
                    invalid.append(f"🔴 {rel}:{ln} 引用了不存在的指令 [{key}]: {line.strip()[:60]}")
                else:
                    referenced.add(key)

    orphans = [f"⚠️ {name}（[{k}]）从未被任何文档引用" for k, name in sop_keys.items() if k not in referenced]
    problems = legacy_hits + invalid
    if problems:
        return False, problems[:30] + [f"🚫 指令引用检查未通过：{len(problems)} 处问题（共校验 {len(scanned)} 个文件）"]
    if orphans:
        return True, [f"✅ [REFS] {len(sop_keys)} 个指令引用有效"] + orphans
    return True, [f"✅ [REFS] {len(sop_keys)} 个指令引用有效、无旧编号残留、无孤儿 SOP（{len(scanned)} 个文件）"]


# ---------------------------------------------------------------------------
# 阶段前置检查（--stage <n>）：跳步检测器
# ---------------------------------------------------------------------------

DOC_MODULE_ROOTS = ["base_template/docs/modules", "docs/modules"]


def _active_task():
    """从看板 ACTIVE 区取当前任务编号。"""
    text = read_text(AI_DIR / "tier3_status.md")
    m = re.search(r"<!--\s*SECTION:ACTIVE:START\s*-->(.*?)<!--\s*SECTION:ACTIVE:END\s*-->", text, re.DOTALL)
    if not m:
        return None, ""
    block = m.group(1)
    t = re.search(r"\[(Task-[A-Za-z0-9\-]+)\]", block)
    files = re.search(r"\*\*涉及文件\*\*[:：]\s*(.+)", block)
    return (t.group(1) if t else None), (files.group(1).strip() if files else "")


def _all_modules():
    mods = []
    for root in DOC_MODULE_ROOTS:
        if os.path.isdir(root):
            for d in sorted(os.listdir(root)):
                if os.path.isdir(os.path.join(root, d)):
                    mods.append((d, Path(root)))
    return mods


def _guess_module(explicit, task_files):
    if explicit:
        return explicit
    # 从任务「涉及文件」里推断 docs/modules/<m>/ 或 src/<m>/
    for pat in (r"docs/modules/([^/\s,]+)/", r"src/modules/([^/\s,]+)/",
                r"modules/([^/\s,]+)/", r"src/([^/\s,]+)/"):
        m = re.search(pat, task_files or "")
        if m:
            return m.group(1)
    mods = _all_modules()
    if len(mods) == 1:
        return mods[0][0]
    return None


def _module_paths(module):
    """返回 (docs_root, module_dir) ；找不到返回 (None, None)。连字符/下划线互认。"""
    if not module:
        return None, None
    cand = {module, module.replace("-", "_"), module.replace("_", "-")}
    for name, root in _all_modules():
        if name in cand:
            return root.parent, root / name
    return None, None


def check_stage(stage, module=None):
    """检查进入某个阶段所需的前置产物，识别"跳步"。"""
    task_id, task_files = _active_task()
    module = _guess_module(module, task_files)
    lines, blockers, warns = [], [], []

    lines.append(f"── 阶段 [{stage}] 前置检查 ──")
    lines.append(f"   当前任务: {task_id or '（看板没有 ACTIVE 任务）'}")
    lines.append(f"   目标模块: {module or '（未能推断，可用 --module <名> 指定）'}")

    docs_root, mod_dir = _module_paths(module) if module else (None, None)

    if stage in ("3", "4", "6", "7") and not task_id:
        blockers.append("看板没有 ACTIVE 任务 —— 跳过了 [1] 规划，先入看板再动手")

    if stage == "3":
        detail = (docs_root / "design" / module / "detail.md") if docs_root and module else None
        dld = sorted(mod_dir.glob("dld-*.md")) if mod_dir else []
        if not module:
            warns.append("无法确定模块，跳过契约检查（建议加 --module）")
        elif not docs_root:
            warns.append(f"模块 [{module}] 没有对应 docs 目录，无法校验契约；若确实跳过了 [2]，请先补")
        elif (detail and detail.exists()) or dld:
            lines.append(f"   ✅ 契约存在: {detail if detail and detail.exists() else dld[0]}")
        else:
            blockers.append("缺少模块施工图 detail.md / dld-*.md —— **跳过了 [2] 技术方案**，没有契约就写码")
        tc = (docs_root / "test-cases" / f"{module}-testcase.md") if docs_root and module else None
        if tc and not tc.exists():
            warns.append("缺少测试用例矩阵 —— 现在不补，[6] 集成时一定返工")

    if stage == "4":
        try:
            dirty = subprocess.run(["git", "status", "--porcelain"], cwd=str(PROJECT_BY_SCRIPT),
                                   capture_output=True, text=True, encoding="utf-8").stdout.strip()
        except Exception:
            dirty = ""
        if not dirty:
            blockers.append("工作区没有任何改动 —— 没有可审的东西（跳过 [3] 编码？）")

    if stage == "6":
        if not module:
            warns.append("无法确定模块，跳过用例矩阵检查（建议加 --module）")
        else:
            tc = (docs_root / "test-cases" / f"{module}-testcase.md") if docs_root else None
            if tc and tc.exists() and tc.stat().st_size > 200:
                lines.append(f"   ✅ 用例矩阵存在: {tc}")
            else:
                blockers.append("缺少用例矩阵 docs/test-cases/<module>-testcase.md —— **跳过了 [1] 的 TC 轨**，集成无据可依")

    if stage == "7":
        lines.append("   ℹ️ 终验请用: python3 .ai/scripts/board.py verify \"<Task-ID>\"")

    for w in warns:
        lines.append("   ⚠️  " + w)
    for b in blockers:
        lines.append("   🔴 " + b)

    lines.append("")
    if blockers:
        lines.append("🚫 [BLOCKING] 前置条件不满足 —— 这就是「跳步」。请先补齐上游产物，或明确接受风险后继续。")
        lines.append("   补救指引见: docs/playbooks/new-module-playbook.md §8 跳步应对手册")
        return False, lines
    lines.append("✅ [PASS] 前置条件满足，可以进入该阶段。")
    if warns:
        lines.append("   （警告项建议补上，否则会在下游阶段返工）")
    return True, lines


# ---------------------------------------------------------------------------
# 主流程
# ---------------------------------------------------------------------------

def main():
    args = sys.argv[1:]
    if not args:
        print(__doc__)
        sys.exit(2)

    project_root = resolve_project_root()
    sql_only = "--sql" in args
    staged = "--staged" in args
    do_build = "--build" in args
    do_meta = "--meta" in args
    do_sop = "--sop" in args or do_meta
    do_refs = "--refs" in args or do_meta
    stage = next((args[i + 1] for i, a in enumerate(args) if a == "--stage" and i + 1 < len(args)), None)
    module_arg = next((args[i + 1] for i, a in enumerate(args) if a == "--module" and i + 1 < len(args)), None)
    no_lint = "--no-lint" in args
    strict = "--strict" in args
    allow_large = "--large" in args
    inputs = [a for a in args if not a.startswith("--")]

    # --stage / --module 的取值不算待检文件
    _consumed = set()
    for _flag in ("--stage", "--module"):
        for _i, _a in enumerate(args):
            if _a == _flag and _i + 1 < len(args):
                _consumed.add(args[_i + 1])
    inputs = [a for a in inputs if a not in _consumed]

    # ---- 跳步检测器优先：只做前置检查，不跑代码门禁 ----
    if stage:
        sp, sm = check_stage(stage, module_arg)
        print()
        for line in sm:
            print(line)
        sys.exit(0 if sp else 1)

    if staged:
        inputs = collect_staged_files(project_root)
        print(f"[*] 门禁启动 (staged 模式): {len(inputs)} 个暂存文件")
    elif inputs:
        print(f"[*] 门禁启动 (显式变更集): {len(inputs)} 个输入项")
    else:
        print("[*] 门禁启动 (仅 SQL 配对 / 元门禁)")

    if not inputs and not sql_only and not do_meta and "--sop" not in args and "--refs" not in args:
        print("\n[BLOCKING] 未收到任何待检文件 —— 门禁并未真正执行，不能视为通过。")
        print("   正确用法：")
        print("     python3 .ai/scripts/gate.py --staged            # 检查暂存区")
        print("     python3 .ai/scripts/gate.py src/ docs/          # 检查指定范围")
        print("     python3 .ai/scripts/gate.py --sql --meta        # 只做 SQL 配对与工作流自检")
        sys.exit(2)

    print(f"[*] 目标工程根: {project_root}")

    results = {}
    expanded = expand_inputs(inputs, project_root) if inputs else []
    code_files = [f for f in expanded if not is_excluded(f)]

    # 1. 看门狗护栏核验
    wd_msgs = []
    r1, m1 = check_min_surgery(code_files, allow_large); wd_msgs += m1
    r2, m2 = check_zero_new_deps(code_files, project_root); wd_msgs += m2
    r3, m3 = check_no_mock(code_files); wd_msgs += m3
    r4, m4 = check_no_public_net(code_files); wd_msgs += m4
    r5, m5 = check_public_asset_impact(code_files); wd_msgs += m5
    r6, m6 = check_secrets(code_files); wd_msgs += m6
    watchdog_pass = all([r1, r2, r3, r4, r5, r6])
    results["WATCHDOG"] = (watchdog_pass, wd_msgs or ["✅ 看门狗 6 项核验全部通过"])

    # 2. 可插拔 Linter（只在真的有待检代码文件时才跑，避免"零文件也报通过"）
    if code_files:
        lp, lm = run_pluggable_linter(code_files, strict, no_lint)
        results["LINT"] = (lp, lm)
    elif inputs:
        results["LINT"] = (True, ["⏭️  本次变更不含可 lint 的代码文件，跳过"])

    # 3. SQL 回滚配对
    target_sql = None if (sql_only or not inputs) else [f for f in expanded if f.suffix.lower() == ".sql"]
    sp, sm = check_sql_rollback(project_root, target_sql)
    results["SQL"] = (sp, sm)

    # 4. 预编译
    if do_build:
        bp, bm = check_compilation(project_root, code_files)
        results["BUILD"] = (bp, bm)

    # 5. 工作流元门禁
    if do_meta:
        mp, mm = check_meta_scripts()
        results["META-SCRIPTS"] = (mp, mm)
        dp, dm = check_doc_links()
        results["META-DOCS"] = (dp, dm)
    if do_sop:
        cp, cm = check_sop_capabilities()
        results["SOP-CAPS"] = (cp, cm)
    if do_refs:
        rp, rm = check_sop_refs(project_root)
        results["SOP-REFS"] = (rp, rm)

    # ---- 汇总裁决 ----
    print("\n" + "=" * 54)
    failed = []
    for k, (ok, msgs) in results.items():
        print(f"\n── {k} {'✅ 通过' if ok else '🔴 未通过'} ──")
        for m in msgs:
            print("   " + m)
        if not ok:
            failed.append(k)
    print("=" * 54)

    if failed:
        print(f"\n[BLOCKING] 门禁未通过: {', '.join(failed)} —— 严禁合入主干，修复后重跑 gate.py。")
        sys.exit(1)
    print("\n[LGTM] 全部门禁通过，准予放行！")
    sys.exit(0)


if __name__ == "__main__":
    main()
