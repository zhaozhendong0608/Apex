<script setup lang="ts">
import { ref, watch } from 'vue'

export interface LogItem {
  id: string
  time: string
  text: string
  type: 'info' | 'trace' | 'success' | 'danger'
}

export interface NodeOption {
  id: string
  name: string
  ip: string
  type: string
}

const props = defineProps<{
  logs: LogItem[]
  nodes: NodeOption[]
  simulating?: boolean
}>()

const emit = defineEmits<{
  (e: 'run', params: { sourceId: string; targetId: string; destPort: number; protocol: string }): void
  (e: 'clearLogs'): void
  (e: 'resetSim'): void
}>()

// 仿真演练表单模型
const sourceId = ref<string>('')
const targetId = ref<string>('')
const destPort = ref<number>(6030)
const protocol = ref<string>('TCP')

// 当 nodes 列表更新时，智能选择默认源端（HOST类型）和宿端（TDENGINE/SERVER类型）
watch(
  () => props.nodes,
  (list) => {
    if (list && list.length > 0) {
      if (!sourceId.value || !list.some(n => n.id === sourceId.value)) {
        const host = list.find(n => n.type === 'HOST') || list[0]
        sourceId.value = host.id
      }
      if (!targetId.value || !list.some(n => n.id === targetId.value)) {
        const target = list.find(n => n.type === 'TDENGINE' || n.type === 'SERVER' || n.type === 'PLC') || list[list.length - 1]
        targetId.value = target.id
      }
    }
  },
  { immediate: true, deep: true }
)

const handleTriggerRun = () => {
  if (!sourceId.value || !targetId.value) return
  emit('run', {
    sourceId: sourceId.value,
    targetId: targetId.value,
    destPort: destPort.value || 6030,
    protocol: protocol.value || 'TCP'
  })
}
</script>

<template>
  <div class="bottom-console">
    <!-- 顶部综合操作与演练参数栏 -->
    <div class="console-bar">
      <div class="sim-controls">
        <span class="control-label">🚀 仿真演练:</span>

        <span class="param-title">源端:</span>
        <select v-model="sourceId" class="sim-select">
          <option v-for="n in nodes" :key="'src_' + n.id" :value="n.id">
            {{ n.name }} ({{ n.ip }})
          </option>
        </select>

        <span class="arrow-sign">➔</span>

        <span class="param-title">宿端:</span>
        <select v-model="targetId" class="sim-select">
          <option v-for="n in nodes" :key="'dest_' + n.id" :value="n.id">
            {{ n.name }} ({{ n.ip }})
          </option>
        </select>

        <span class="param-title">端口:</span>
        <input v-model.number="destPort" type="number" class="sim-input-port" placeholder="6030" />

        <span class="param-title">协议:</span>
        <select v-model="protocol" class="sim-select-proto">
          <option value="TCP">TCP</option>
          <option value="UDP">UDP</option>
          <option value="ICMP">ICMP</option>
        </select>

        <button class="btn-run-sim" :disabled="simulating" @click="handleTriggerRun">
          {{ simulating ? '仿真计算中...' : '▶ 发起仿真寻路' }}
        </button>

        <!-- 常用端口快捷点选 -->
        <div class="quick-ports">
          <span class="quick-tag" @click="destPort = 6030; protocol = 'TCP'" title="TDengine时序写入">6030(放行)</span>
          <span class="quick-tag is-deny" @click="destPort = 22; protocol = 'TCP'" title="SSH远程阻断">22(阻断)</span>
          <span class="quick-tag" @click="destPort = 1883; protocol = 'TCP'" title="MQTT物联协议">1883(MQTT)</span>
        </div>
      </div>

      <div class="bar-right">
        <span class="engine-status">● 引擎：Java 17 BFS + ACL</span>
        <button class="btn-reset" title="复位仿真流光与高亮状态" @click="emit('resetSim')">↺ 复位拓扑</button>
        <button class="btn-clear" title="清空日志" @click="emit('clearLogs')">清屏</button>
      </div>
    </div>

    <!-- 日志流输出区域 -->
    <div class="console-content">
      <div v-if="logs.length === 0" class="no-log-tip">
        > 等待发起网络仿真演练，请在上方选择源/宿节点及端口后点击【▶ 发起仿真寻路】...
      </div>
      <div v-for="item in logs" :key="item.id" class="log-line">
        <span class="log-time">&gt; [{{ item.time }}]</span>
        <span :class="['log-text', 'log-' + item.type]">{{ item.text }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.bottom-console {
  height: 155px;
  background: #0d1219;
  border-top: 1px solid #2b394b;
  display: flex;
  flex-direction: column;
}

.console-bar {
  height: 36px;
  background: #151b24;
  padding: 0 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #2b394b;
  font-size: 11px;
  color: #8e9cae;
  gap: 12px;
  overflow-x: auto;
  white-space: nowrap;
}

.console-bar::-webkit-scrollbar {
  height: 3px;
}
.console-bar::-webkit-scrollbar-thumb {
  background: #334155;
  border-radius: 2px;
}

.sim-controls {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: nowrap;
  flex-shrink: 0;
}

.control-label {
  font-weight: 600;
  color: #38bdf8;
  font-size: 11px;
  white-space: nowrap;
  flex-shrink: 0;
}

.param-title {
  color: #94a3b8;
  font-size: 10px;
  white-space: nowrap;
  flex-shrink: 0;
}

.arrow-sign {
  color: #38bdf8;
  font-weight: bold;
  flex-shrink: 0;
}

.sim-select {
  background: #1e293b;
  border: 1px solid #334155;
  color: #e2e8f0;
  border-radius: 4px;
  padding: 3px 6px;
  font-size: 11px;
  max-width: 140px;
  outline: none;
  flex-shrink: 0;
}

.sim-select-proto {
  background: #1e293b;
  border: 1px solid #334155;
  color: #e2e8f0;
  border-radius: 4px;
  padding: 3px 4px;
  font-size: 11px;
  outline: none;
  flex-shrink: 0;
}

.sim-input-port {
  width: 50px;
  background: #1e293b;
  border: 1px solid #334155;
  color: #38bdf8;
  border-radius: 4px;
  padding: 3px 6px;
  font-size: 11px;
  font-family: monospace;
  outline: none;
  flex-shrink: 0;
}

.btn-run-sim {
  background: #0284c7;
  color: #ffffff;
  border: none;
  border-radius: 4px;
  padding: 4px 10px;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
  box-shadow: 0 2px 4px rgba(2, 132, 199, 0.3);
  white-space: nowrap;
  flex-shrink: 0;
}

.btn-run-sim:hover:not(:disabled) {
  background: #0369a1;
}

.btn-run-sim:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.quick-ports {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: 2px;
  flex-shrink: 0;
}

.quick-tag {
  background: #1e293b;
  border: 1px solid #22c55e;
  color: #4ade80;
  padding: 1px 5px;
  border-radius: 3px;
  font-size: 9px;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
  flex-shrink: 0;
}

.quick-tag:hover {
  background: #166534;
  color: #fff;
}

.quick-tag.is-deny {
  border-color: #ef4444;
  color: #f87171;
}

.quick-tag.is-deny:hover {
  background: #991b1b;
  color: #fff;
}

.bar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.engine-status {
  color: #34d399;
  font-size: 10px;
}

.btn-reset {
  background: #1e293b;
  border: 1px solid #38bdf8;
  color: #38bdf8;
  border-radius: 3px;
  padding: 1px 7px;
  font-size: 10px;
  cursor: pointer;
  transition: all 0.15s;
}

.btn-reset:hover {
  background: #0284c7;
  color: #fff;
}

.btn-clear {
  background: transparent;
  border: 1px solid #334155;
  color: #94a3b8;
  border-radius: 3px;
  padding: 1px 6px;
  font-size: 10px;
  cursor: pointer;
}

.btn-clear:hover {
  color: #e2e8f0;
  border-color: #64748b;
}

.console-content {
  flex: 1;
  padding: 8px 12px;
  overflow-y: auto;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
  line-height: 1.5;
}

.no-log-tip {
  color: #475569;
  font-style: italic;
  padding: 4px 0;
}

.log-line {
  display: flex;
  gap: 8px;
}

.log-time {
  color: #52637a;
}

.log-info { color: #38bdf8; }
.log-trace { color: #818cf8; }
.log-success { color: #34d399; font-weight: 500; }
.log-danger { color: #f87171; font-weight: bold; }
</style>
