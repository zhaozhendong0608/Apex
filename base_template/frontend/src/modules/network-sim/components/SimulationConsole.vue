<script setup lang="ts">
export interface LogItem {
  id: string
  time: string
  text: string
  type: 'info' | 'trace' | 'success' | 'danger'
}

defineProps<{
  logs: LogItem[]
}>()
</script>

<template>
  <div class="bottom-console">
    <div class="console-bar">
      <span>仿真日志输出 (Simulation Console)</span>
      <span class="engine-status">● 仿真核心引擎：Java 17 BFS + ACL Matcher</span>
    </div>
    <div class="console-content">
      <div v-for="item in logs" :key="item.id" class="log-line">
        <span class="log-time">&gt; [{{ item.time }}]</span>
        <span :class="['log-text', 'log-' + item.type]">{{ item.text }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.bottom-console {
  height: 130px;
  background: #0d1219;
  border-top: 1px solid #2b394b;
  display: flex;
  flex-direction: column;
}

.console-bar {
  height: 24px;
  background: #151b24;
  padding: 0 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #2b394b;
  font-size: 10px;
  color: #8e9cae;
}

.engine-status {
  color: #34d399;
}

.console-content {
  flex: 1;
  padding: 8px 12px;
  overflow-y: auto;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
  line-height: 1.5;
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
