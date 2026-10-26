<template>
  <div class="tree-node-item">
    <!-- 节点自身行 -->
    <div 
      :class="['tree-node', node.leaf ? 'leaf' : 'parent', { selected: selectedCode === node.code, 'hint-node': node._isHint }]"
      :style="{ paddingLeft: `${node.level * 16 + 10}px` }"
      @click="onRowClick(node)"
    >
      <span v-if="!node.leaf && !node._isHint" class="tree-node-icon">
        {{ node.expanded ? '▼' : '▶' }}
      </span>
      <span v-else-if="node._isHint" class="hint-icon">{{ node._hintAction === 'prev' ? '⬆️' : '⬇️' }}</span>
      <span v-else class="leaf-dot"></span>

      <span class="tree-type-icon">{{ getNodeIcon(node) }}</span>
      <span class="tree-label" :title="node.name">{{ node.name }}</span>
      <span v-if="node.code && node.leaf" class="point-code-mono">({{ node.code }})</span>
      <span v-if="node.childCount && !node.leaf" class="child-count-pill">{{ node.childCount }}</span>
      <span v-if="node.leaf" class="metric-tag" :class="node.tagClass">{{ node.unit || '点' }}</span>
    </div>

    <!-- 子节点递归渲染 (展开时) -->
    <div v-if="node.expanded && node.children && node.children.length > 0" class="tree-node-children">
      <TreeNodeItem
        v-for="cNode in getWindowedChildren(node)"
        :key="cNode.id"
        :node="cNode"
        :selected-code="selectedCode"
        :node-page-map="nodePageMap"
        :page-size="pageSize"
        @select-point="emit('selectPoint', $event)"
      />
    </div>
  </div>
</template>

<script setup>
import { defineProps, defineEmits } from 'vue'
import axios from 'axios'

const props = defineProps({
  node: { type: Object, required: true },
  selectedCode: { type: String, default: '' },
  nodePageMap: { type: Object, required: true },
  pageSize: { type: Number, default: 50 }
})

const emit = defineEmits(['selectPoint'])

function getNodeIcon(node) {
  if (node._isHint) return '📄'
  switch (node.type) {
    case 'station': return '🏭'
    case 'unit': return '⚙️'
    case 'device': return '🎛️'
    case 'component': return '📂'
    case 'point': return '📍'
    default: return '📁'
  }
}

function normalizeNode(raw, level = 0) {
  const isPoint = raw.type === 'point' || raw.leaf === true
  let tagClass = 'temp'
  if (raw.strategyType) {
    tagClass = raw.strategyType.toLowerCase()
  } else if (raw.name && (raw.name.includes('振') || raw.name.includes('位移'))) {
    tagClass = 'vib'
  } else if (raw.name && (raw.name.includes('功率') || raw.name.includes('负荷'))) {
    tagClass = 'power'
  } else if (raw.name && (raw.name.includes('开关') || raw.name.includes('断路器'))) {
    tagClass = 'switch'
  }

  return {
    id: raw.id,
    parentId: raw.parentId,
    name: raw.name,
    code: raw.code || '',
    type: raw.type || (isPoint ? 'point' : 'component'),
    level,
    leaf: isPoint,
    childCount: raw.childCount || 0,
    expanded: false,
    loaded: false,
    children: [],
    tagClass,
    unit: raw.unit || '',
    baseValue: raw.baseValue,
    strategyType: raw.strategyType
  }
}

function getWindowedChildren(node) {
  const all = node.children || []
  if (all.length <= props.pageSize) return all

  const totalPages = Math.ceil(all.length / props.pageSize)
  let curr = props.nodePageMap[node.id] || 1
  if (curr > totalPages) curr = totalPages
  if (curr < 1) curr = 1
  props.nodePageMap[node.id] = curr

  const start = (curr - 1) * props.pageSize
  const end = Math.min(start + props.pageSize, all.length)
  const sliced = all.slice(start, end)

  const res = []
  if (curr > 1) {
    res.push({
      id: `prev-hint-${node.id}`,
      name: `⬆️ [上一页] 查看第 ${curr - 1} 页 (当前第 ${curr}/${totalPages} 页)`,
      level: node.level + 1,
      leaf: true,
      _isHint: true,
      _hintAction: 'prev',
      _parentNode: node,
      _targetPage: curr - 1
    })
  }

  res.push(...sliced)

  if (curr < totalPages) {
    res.push({
      id: `next-hint-${node.id}`,
      name: `⬇️ [下一页] 查看第 ${curr + 1} 页 (共 ${all.length} 项，当前 ${curr}/${totalPages} 页)`,
      level: node.level + 1,
      leaf: true,
      _isHint: true,
      _hintAction: 'next',
      _parentNode: node,
      _targetPage: curr + 1
    })
  }

  return res
}

async function onRowClick(node) {
  if (node._isHint) {
    const pNode = node._parentNode
    if (pNode && node._targetPage !== undefined) {
      props.nodePageMap[pNode.id] = node._targetPage
    }
    return
  }

  if (node.leaf) {
    emit('selectPoint', node)
    return
  }

  node.expanded = !node.expanded

  if (node.expanded && !node.loaded) {
    try {
      const res = await axios.get(`/api/simulator/points?parentId=${node.id}`)
      if (res.data && res.data.data) {
        node.children = res.data.data.map(c => normalizeNode(c, node.level + 1))
        node.loaded = true
      }
    } catch (e) {
      console.warn('按需加载子节点失败:', e)
    }
  }
}
</script>

<style scoped>
.tree-node-item {
  width: 100%;
}

.tree-node {
  display: flex;
  align-items: center;
  padding: 6px 10px;
  border-radius: 6px;
  cursor: pointer;
  user-select: none;
  font-size: 13px;
  color: var(--text-main);
  margin-bottom: 2px;
  transition: background 0.15s;
}

.tree-node:hover {
  background-color: rgba(255, 255, 255, 0.05);
}

.tree-node.selected {
  background-color: rgba(56, 189, 248, 0.2);
  color: var(--accent-cyan);
}

.tree-node-icon {
  font-size: 10px;
  color: var(--text-muted);
  width: 16px;
}

.leaf-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background-color: var(--accent-cyan);
  margin-right: 8px;
  flex-shrink: 0;
}

.tree-type-icon {
  margin-right: 6px;
  font-size: 13px;
  flex-shrink: 0;
}

.tree-label {
  flex: 1;
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.point-code-mono {
  font-family: monospace;
  font-size: 11px;
  color: var(--text-muted);
  margin-left: 4px;
}

.child-count-pill {
  font-size: 10px;
  padding: 0 5px;
  border-radius: 8px;
  background-color: rgba(255, 255, 255, 0.08);
  color: var(--text-muted);
  margin-left: auto;
}

.hint-node {
  color: #38bdf8 !important;
  font-weight: 500;
  background: rgba(56, 189, 248, 0.08);
  border: 1px dashed rgba(56, 189, 248, 0.3);
  margin: 4px 0;
}

.hint-icon {
  margin-right: 6px;
  font-size: 12px;
}

.metric-tag {
  font-size: 10px;
  padding: 1px 5px;
  border-radius: 3px;
  background-color: var(--bg-card);
  border: 1px solid var(--border-color);
}
</style>
