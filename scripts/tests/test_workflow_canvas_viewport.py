#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""TC-WFC 回归探针 · FR-REQ-WFC-01 / Task-510

对应 docs/design/req-management/detail-workflow-canvas-viewport-v1.0.md 的冻结契约。
纯标准库、零新依赖；既用于 [3] TDD 的红→绿，也供 [4]/[6] 回归复跑。

用法：python3 scripts/tests/test_workflow_canvas_viewport.py
退出码：0 = 全绿；1 = 存在未满足契约（真红灯）。
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
VIEW = ROOT / "base_template" / "frontend" / "src" / "modules" / "req-management" / "views" / "ReqManagementView.vue"


def css_block(src: str, selector: str) -> str:
    m = re.search(r"(?m)^\s*" + re.escape(selector) + r"\s*\{([^}]*)\}", src, re.S)
    return m.group(1) if m else ""


def graph_config(src: str) -> str:
    # 截到最外层 "  })" 为止；内部嵌套对象以 "}," 或 "}" 收尾，不会被误判
    m = re.search(r"new Graph\(\{(.*?)\n[ \t]*\}\)", src, re.S)
    return m.group(1) if m else ""


def restrict_block(src: str) -> str:
    m = re.search(r"translating:\s*\{\s*restrict:\s*\{([^}]*)\}", src, re.S)
    return m.group(1) if m else ""


def main() -> int:
    if not VIEW.exists():
        print(f"[RED] 找不到被测文件: {VIEW}")
        return 1

    src = VIEW.read_text(encoding="utf-8")
    cfg = graph_config(src)
    viewport_css = css_block(src, ".wf-viewport")
    restrict = restrict_block(src)

    checks = [
        ("C-01 尺寸常量单点声明 WORKFLOW_CANVAS = 1450×520",
         bool(re.search(r"const\s+WORKFLOW_CANVAS\s*=\s*\{[^}]*width:\s*1450[^}]*height:\s*520", src, re.S))),
        ("C-02 autoResize 关闭（切断 ResizeObserver 正反馈）",
         "autoResize: false" in cfg and "autoResize: true" not in cfg),
        ("C-03 Graph 显式宽高绑定常量",
         "width: WORKFLOW_CANVAS.width" in cfg and "height: WORKFLOW_CANVAS.height" in cfg),
        ("C-04 外层可视框 .wf-viewport 存在",
         'class="wf-viewport"' in src),
        ("C-05 内层 X6 挂载点 .wf-graph-host 且绑定常量尺寸",
         "wf-graph-host" in src
         and 'ref="workflowGraphContainer"' in src
         and "WORKFLOW_CANVAS.width" in src
         and "WORKFLOW_CANVAS.height" in src),
        ("C-06 可视框 CSS 固定 360px + overflow:auto",
         bool(re.search(r"height:\s*360px", viewport_css))
         and bool(re.search(r"overflow:\s*auto", viewport_css))),
        ("C-07 模板不再残留 height:210px 旧内联声明",
         "height:210px" not in src and "height: 210px" not in src),
        ("C-08 translating.restrict 边界绑定内容画布常量（不再锁死 210）",
         "height: WORKFLOW_CANVAS.height" in restrict
         and not re.search(r"height:\s*210", restrict)),
        ("C-09 自适应折行函数已落地（按可视框宽度重排节点）",
         "const relayoutWorkflowGraph = () =>" in src
         and bool(re.search(r"\b\w+\.position\(", src))
         and "clientWidth" in src),
        ("C-10 挂载点高度由响应式状态托管（不被 re-render 覆盖）",
         "const workflowHostHeight = ref(WORKFLOW_CANVAS.height)" in src
         and "height: workflowHostHeight + 'px'" in src
         and "workflowHostHeight.value =" in src),
        ("C-11 首屏收敛：ResizeObserver + ensureWorkflowLayout",
         "new ResizeObserver" in src
         and "ensureWorkflowLayout()" in src),
        ("C-12 生命周期清理且不再直接写 style.height",
         "workflowViewportObserver?.disconnect()" in src
         and ".style.height =" not in src),
    ]

    failed = [name for name, ok in checks if not ok]
    for name, ok in checks:
        print(("  [PASS] " if ok else "  [FAIL] ") + name)

    if failed:
        print(f"\n[RED] {len(failed)}/{len(checks)} 条契约未实现（预期：改代码前必红）")
        return 1

    print(f"\n[GREEN] {len(checks)}/{len(checks)} 条冻结契约全部满足")
    return 0


if __name__ == "__main__":
    sys.exit(main())
