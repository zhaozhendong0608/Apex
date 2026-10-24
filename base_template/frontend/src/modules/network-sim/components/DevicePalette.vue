<script setup lang="ts">
export interface DeviceItem {
  type: string
  name: string
  icon: string
  color: string
  isZone?: boolean
  zoneStyle?: {
    bgColor: string
    borderColor: string
    isDashed: boolean
    defaultW: number
    defaultH: number
  }
}

export interface DeviceGroup {
  name: string
  items: DeviceItem[]
}

// 结构化设备分组库
const deviceGroups: DeviceGroup[] = [
  {
    name: '网络与边界路由',
    items: [
      { type: 'ROUTER', name: '核心路由器', icon: '🌐', color: '#38bdf8' },
      { type: 'FIREWALL', name: '工业防火墙', icon: '🧱', color: '#ef4444' },
      { type: 'GAP', name: '单向安全网闸', icon: '🛡️', color: '#f97316' },
      { type: 'SWITCH', name: '工业交换机', icon: '🔲', color: '#0ea5e9' }
    ]
  },
  {
    name: '工业控制与边缘',
    items: [
      { type: 'PLC', name: '工业控制器PLC', icon: '🎛️', color: '#8b5cf6' },
      { type: 'GATEWAY', name: '边缘网关', icon: '⚙️', color: '#14b8a6' },
      { type: 'NEURON', name: 'Neuron网关', icon: '🔄', color: '#22c55e' },
      { type: 'SENSOR', name: '工业传感器', icon: '🌡️', color: '#f59e0b' }
    ]
  },
  {
    name: '计算与存储服务',
    items: [
      { type: 'SERVER', name: '应用服务器', icon: '🗄️', color: '#a855f7' },
      { type: 'TDENGINE', name: 'TDengine', icon: '🛢️', color: '#06b6d4' },
      { type: 'HOST', name: '研发办公PC', icon: '💻', color: '#38bdf8' }
    ]
  }
]

// 通用大区/背景底板图元（拖入画布后在右侧检查器自由设定名称、颜色与边框样式）
const defaultZone: DeviceItem = {
  type: 'ZONE',
  name: '区域底板',
  icon: '🔲',
  color: '#38bdf8',
  isZone: true,
  zoneStyle: {
    bgColor: 'rgba(56, 189, 248, 0.1)',
    borderColor: '#38bdf8',
    isDashed: false,
    defaultW: 240,
    defaultH: 220
  }
}

const onDragStart = (e: DragEvent, item: DeviceItem) => {
  if (e.dataTransfer) {
    e.dataTransfer.setData('application/json', JSON.stringify(item))
  }
}
</script>

<template>
  <div class="device-palette">
    <!-- 1. 网络与边缘设备分层展示 -->
    <template v-for="group in deviceGroups" :key="group.name">
      <div class="palette-header">{{ group.name }}</div>
      <div class="device-grid">
        <div
          v-for="item in group.items"
          :key="item.type"
          class="device-tile"
          draggable="true"
          @dragstart="onDragStart($event, item)"
        >
          <div class="tile-icon" :style="{ color: item.color }">{{ item.icon }}</div>
          <div class="tile-name">{{ item.name }}</div>
        </div>
      </div>
    </template>

    <!-- 2. 背景容器大区 (单一通用图元) -->
    <div class="palette-header" style="margin-top: 4px;">容器与背景底板</div>
    <div class="zone-grid">
      <div
        class="zone-tile"
        draggable="true"
        @dragstart="onDragStart($event, defaultZone)"
      >
        <div class="zone-color-bar" :style="{ background: defaultZone.color }"></div>
        <div class="zone-info">
          <div class="zone-name">{{ defaultZone.name }}</div>
          <div class="zone-tag">拖拽上图 · 自由定制属性</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.device-palette {
  width: 175px;
  background: #1b2430;
  border-right: 1px solid #2b394b;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.palette-header {
  padding: 8px 12px;
  font-size: 11px;
  font-weight: 600;
  color: #8e9cae;
  border-bottom: 1px solid #2b394b;
  background: #151b24;
  letter-spacing: 0.5px;
}

.device-grid {
  padding: 8px 6px;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 6px;
}

.device-tile {
  background: #222d3d;
  border: 1px solid #2b394b;
  border-radius: 6px;
  padding: 8px 4px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  cursor: grab;
  transition: all 0.2s;
  text-align: center;
}

.device-tile:hover {
  border-color: #38bdf8;
  transform: translateY(-2px);
  box-shadow: 0 4px 8px rgba(0,0,0,0.3);
}

.tile-icon {
  font-size: 18px;
}

.tile-name {
  font-size: 10px;
  color: #e2e8f0;
  font-weight: 500;
  line-height: 1.2;
}

/* 大区背景条目 */
.zone-grid {
  padding: 8px 6px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.zone-tile {
  background: #222d3d;
  border: 1px solid #2b394b;
  border-radius: 6px;
  padding: 6px 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: grab;
  transition: all 0.2s;
}

.zone-tile:hover {
  border-color: #38bdf8;
  transform: translateY(-2px);
  box-shadow: 0 4px 8px rgba(0,0,0,0.3);
}

.zone-color-bar {
  width: 6px;
  height: 28px;
  border-radius: 3px;
}

.zone-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.zone-name {
  font-size: 11px;
  font-weight: 600;
  color: #fff;
}

.zone-tag {
  font-size: 9px;
  color: #64748b;
}
</style>
