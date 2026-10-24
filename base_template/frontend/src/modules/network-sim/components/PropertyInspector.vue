<script setup lang="ts">
import { ref, watch } from 'vue'
import { Plus, Delete } from '@element-plus/icons-vue'
import type { AclRuleDTO, RouteEntryDTO, NatRuleDTO } from '../types'

export interface SelectedNodeData {
  id: string
  name: string
  type: string
  zone: string
  ip: string
  subnet: string
  gateway?: string
  disabled?: boolean
  acls: AclRuleDTO[]
  routes?: RouteEntryDTO[]
  natRules?: NatRuleDTO[]
}

export interface SelectedEdgeData {
  id: string
  sourceName?: string
  targetName?: string
  bandwidth: string
  latencyMs: number
  disabled?: boolean
}

export interface SelectedZoneData {
  id: string
  name: string
  borderColor: string
  bgColor: string
  isDashed: boolean
  fontSize: number
  subnetPrefix?: string // 如 192.168.1
  gateway?: string      // 如 192.168.1.1
}

const props = defineProps<{
  selectedNode: SelectedNodeData | null
  selectedEdge: SelectedEdgeData | null
  selectedZone: SelectedZoneData | null
}>()

const emit = defineEmits<{
  (e: 'updateNode', node: SelectedNodeData): void
  (e: 'updateEdge', edge: SelectedEdgeData): void
  (e: 'updateZone', zone: SelectedZoneData): void
  (e: 'simulate'): void
}>()

// 本地可编辑模型
const localNode = ref<SelectedNodeData | null>(null)
const localEdge = ref<SelectedEdgeData | null>(null)
const localZone = ref<SelectedZoneData | null>(null)

// 当前设备拆解出的前三段网段与最后一段主机号
const ipPrefix = ref<string>('192.168.1')
const hostSuffix = ref<number | string>(10)

// 深度监听 props 变化
watch(
  () => props.selectedNode,
  (val) => {
    if (val) {
      localNode.value = JSON.parse(JSON.stringify(val))
      localEdge.value = null
      localZone.value = null

      // 解析当前 IP: 提取前三段与最后一段
      const rawIp = localNode.value?.ip || '192.168.1.10'
      const lastDotIndex = rawIp.lastIndexOf('.')
      if (lastDotIndex > -1) {
        ipPrefix.value = rawIp.substring(0, lastDotIndex)
        hostSuffix.value = rawIp.substring(lastDotIndex + 1)
      } else {
        ipPrefix.value = '192.168.1'
        hostSuffix.value = '10'
      }
    } else {
      localNode.value = null
    }
  },
  { immediate: true, deep: true }
)

// 当用户修改第四段主机号时，自动拼装完整 IP 并触发节点更新
const handleHostSuffixChange = () => {
  if (!localNode.value) return
  // 过滤并规范化数字 (1 ~ 254)
  let val = String(hostSuffix.value).replace(/\D/g, '')
  if (val !== '') {
    let num = parseInt(val, 10)
    if (num > 254) num = 254
    if (num < 1) num = 1
    val = String(num)
  }
  hostSuffix.value = val
  localNode.value.ip = `${ipPrefix.value}.${val || 1}`
  handleNodeChange()
}

watch(
  () => props.selectedEdge,
  (val) => {
    if (val) {
      localEdge.value = JSON.parse(JSON.stringify(val))
      localNode.value = null
      localZone.value = null
    } else {
      localEdge.value = null
    }
  },
  { immediate: true, deep: true }
)

watch(
  () => props.selectedZone,
  (val) => {
    if (val) {
      localZone.value = JSON.parse(JSON.stringify(val))
      localNode.value = null
      localEdge.value = null
    } else {
      localZone.value = null
    }
  },
  { immediate: true, deep: true }
)

// 触发大区底板更新
const handleZoneChange = () => {
  if (localZone.value) {
    emit('updateZone', JSON.parse(JSON.stringify(localZone.value)))
  }
}

// 触发节点更新
const handleNodeChange = () => {
  if (localNode.value) {
    emit('updateNode', JSON.parse(JSON.stringify(localNode.value)))
  }
}

// 触发链路更新
const handleEdgeChange = () => {
  if (localEdge.value) {
    emit('updateEdge', JSON.parse(JSON.stringify(localEdge.value)))
  }
}

// 新增 ACL 规则
const handleAddAcl = () => {
  if (!localNode.value) return
  if (!localNode.value.acls) {
    localNode.value.acls = []
  }
  const nextPriority = localNode.value.acls.length + 1
  const newRule: AclRuleDTO = {
    id: 'acl_' + Math.random().toString(36).substring(2, 6),
    priority: nextPriority,
    action: 'PERMIT',
    srcIp: 'ANY',
    destIp: 'ANY',
    destPort: 80,
    protocol: 'TCP',
    description: '自定义访问规则'
  }
  localNode.value.acls.push(newRule)
  handleNodeChange()
}

// 删除 ACL 规则
const handleDeleteAcl = (index: number) => {
  if (!localNode.value || !localNode.value.acls) return
  localNode.value.acls.splice(index, 1)
  handleNodeChange()
}

// 新增静态路由表项 (Routing Entry)
const handleAddRoute = () => {
  if (!localNode.value) return
  if (!localNode.value.routes) {
    localNode.value.routes = []
  }
  const newRoute: RouteEntryDTO = {
    id: 'rt_' + Math.random().toString(36).substring(2, 6),
    destSubnet: '172.16.1.0',
    mask: '255.255.255.0',
    nextHopIp: '10.0.0.1',
    description: '下发静态路由'
  }
  localNode.value.routes.push(newRoute)
  handleNodeChange()
}

// 删除静态路由表项
const handleDeleteRoute = (index: number) => {
  if (!localNode.value || !localNode.value.routes) return
  localNode.value.routes.splice(index, 1)
  handleNodeChange()
}

// 新增 NAT 规则 (支持 SNAT 源地址伪装 或 DNAT 目的端口映射)
const handleAddNatRule = (type: 'SNAT' | 'DNAT' = 'SNAT') => {
  if (!localNode.value) return
  if (!localNode.value.natRules) {
    localNode.value.natRules = []
  }
  const newRule: NatRuleDTO = type === 'SNAT' ? {
    id: 'nat_' + Math.random().toString(36).substring(2, 6),
    type: 'SNAT',
    origSrc: '192.168.1.0/24',
    transSrc: localNode.value.ip || '10.0.0.1',
    description: '内部私网出境源地址伪装'
  } : {
    id: 'nat_' + Math.random().toString(36).substring(2, 6),
    type: 'DNAT',
    extIp: localNode.value.ip || '10.0.0.1',
    extPort: 8080,
    intIp: '172.16.1.100',
    intPort: 6030,
    protocol: 'TCP',
    description: '公网端口映射入站至内部服务'
  }
  localNode.value.natRules.push(newRule)
  handleNodeChange()
}

// 删除 NAT 规则项
const handleDeleteNatRule = (index: number) => {
  if (!localNode.value || !localNode.value.natRules) return
  localNode.value.natRules.splice(index, 1)
  handleNodeChange()
}
</script>

<template>
  <div class="property-inspector">
    <div class="inspector-header">属性与安全策略检查器</div>

    <div class="inspector-body">
      <!-- 场景 1：未选中图元，展示默认说明 -->
      <div v-if="!localNode && !localEdge && !localZone" class="empty-tip">
        <div class="tip-icon">👈</div>
        <div class="tip-title">未选中图元</div>
        <p class="tip-text">请在画布中点击选中任意设备节点、物理链路或安全大区底板，以实时编辑其属性、安全策略或外观样式。</p>
      </div>

      <!-- 场景 2：选中了设备节点 -->
      <div v-else-if="localNode" class="node-panel">
        <div class="panel-section-title">
          <span>🖥️ 设备属性配置</span>
          <span class="type-tag">{{ localNode.type }}</span>
        </div>

        <div class="form-item">
          <label>设备名称</label>
          <input
            v-model="localNode.name"
            class="prop-input-full"
            placeholder="设备名称"
            @input="handleNodeChange"
          />
        </div>

        <div class="form-item">
          <label>所属大区</label>
          <select
            v-model="localNode.zone"
            class="prop-select-full"
            @change="handleNodeChange"
          >
            <option value="IT_ZONE">管理信息大区 (IT Zone)</option>
            <option value="DMZ_ZONE">核心边界隔离区 (DMZ)</option>
            <option value="OT_ZONE">生产控制大区 (OT Zone)</option>
            <option value="PHY_ZONE">物理设备层 (Phy Zone)</option>
          </select>
        </div>

        <div class="form-item">
          <label>IP 地址规划 (锁定大区网段，仅编辑末位)</label>
          <div class="ip-split-input-group">
            <span class="ip-prefix-badge" title="该网段由当前所属安全大区全局锁定，不可在此修改">
              🔒 {{ ipPrefix }}.
            </span>
            <input
              v-model="hostSuffix"
              type="text"
              class="ip-host-suffix-input"
              maxlength="3"
              placeholder="10"
              title="主机号范围: 1 ~ 254"
              @input="handleHostSuffixChange"
            />
          </div>
          <div class="ip-sub-hint">
            💡 前三段网段与大区底板严格绑定，仅允许修改末位主机号 (1~254)。
          </div>
        </div>

        <div class="form-item">
          <label>子网掩码</label>
          <input
            v-model="localNode.subnet"
            class="prop-input-full"
            placeholder="如 255.255.255.0"
            @input="handleNodeChange"
          />
        </div>

        <div class="form-item">
          <label>默认网关 (Default Gateway)</label>
          <input
            v-model="localNode.gateway"
            class="prop-input-full"
            placeholder="如 192.168.1.1"
            @input="handleNodeChange"
          />
          <div class="ip-sub-hint">
            🌐 跨子网通信的首跳设备 IP，通常指向本大区核心路由器或三层网关。
          </div>
        </div>

        <!-- 工业应急故障演练注入 -->
        <div class="form-item fault-injection-box" :class="{ 'is-offline': localNode.disabled }">
          <div class="fault-header">
            <span class="fault-title">⚠️ 设备状态 / 硬件宕机注入</span>
            <el-switch
              v-model="localNode.disabled"
              active-text="宕机离线"
              inactive-text="正常运行"
              inline-prompt
              active-color="#ef4444"
              inactive-color="#10b981"
              @change="handleNodeChange"
            />
          </div>
          <div class="fault-tip" v-if="localNode.disabled">
            已注入模拟硬件断电/宕机！寻路引擎将尝试自愈切换备用冗余设备。
          </div>
        </div>

        <!-- 防火墙、网关、单向网闸与路由器：ACL/过滤策略规则集 -->
        <div v-if="['FIREWALL', 'GATEWAY', 'GAP', 'ROUTER'].includes(localNode.type)" class="acl-section">
          <div class="divider"></div>
          <div class="acl-head-bar">
            <span class="acl-title">🛡️ ACL 与隔离过滤策略</span>
            <button class="btn-mini-add" @click="handleAddAcl">
              <el-icon><Plus /></el-icon> 新增
            </button>
          </div>

          <div v-if="!localNode.acls || localNode.acls.length === 0" class="no-acl-tip">
            暂未配置规则，系统将执行默认放行 (Default Permit)。
          </div>

          <div v-else class="acl-cards-wrap">
            <div
              v-for="(rule, idx) in localNode.acls"
              :key="rule.id"
              class="acl-edit-card"
            >
              <div class="rule-top-line">
                <span class="rule-idx">#{{ idx + 1 }}</span>
                <select
                  v-model="rule.action"
                  :class="['action-select', rule.action === 'PERMIT' ? 'is-permit' : 'is-deny']"
                  @change="handleNodeChange"
                >
                  <option value="PERMIT">PERMIT (放行)</option>
                  <option value="DENY">DENY (阻断)</option>
                </select>
                <button class="btn-del-rule" title="删除规则" @click="handleDeleteAcl(idx)">
                  <el-icon><Delete /></el-icon>
                </button>
              </div>

              <div class="rule-inputs-row">
                <div class="rule-field">
                  <span class="field-label">端口</span>
                  <input
                    v-model.number="rule.destPort"
                    type="number"
                    class="rule-input-sm"
                    placeholder="如 6030"
                    @input="handleNodeChange"
                  />
                </div>
                <div class="rule-field">
                  <span class="field-label">协议</span>
                  <select v-model="rule.protocol" class="rule-input-sm" @change="handleNodeChange">
                    <option value="TCP">TCP</option>
                    <option value="UDP">UDP</option>
                    <option value="ICMP">ICMP</option>
                    <option value="ANY">ANY</option>
                  </select>
                </div>
              </div>

              <div class="rule-desc-row">
                <input
                  v-model="rule.description"
                  class="rule-input-desc"
                  placeholder="规则白话描述..."
                  @input="handleNodeChange"
                />
              </div>
            </div>
          </div>
        </div>

        <!-- 路由器与核心三层设备：静态路由表 (Routing Table) 配置面板 -->
        <div v-if="['ROUTER', 'SWITCH', 'FIREWALL'].includes(localNode.type)" class="acl-section">
          <div class="divider"></div>
          <div class="acl-head-bar">
            <span class="acl-title">🌐 静态路由表 (Routing Table)</span>
            <button class="btn-mini-add" @click="handleAddRoute">
              <el-icon><Plus /></el-icon> 新增路由
            </button>
          </div>

          <div v-if="!localNode.routes || localNode.routes.length === 0" class="no-acl-tip">
            暂未配置静态路由，默认采用直连网络广播与二层交换。
          </div>

          <div v-else class="acl-cards-wrap">
            <div
              v-for="(rt, idx) in localNode.routes"
              :key="rt.id"
              class="acl-edit-card route-card"
            >
              <div class="rule-top-line">
                <span class="rule-idx">R{{ idx + 1 }}</span>
                <span class="route-tag">STATIC</span>
                <button class="btn-del-rule" title="删除此路由" @click="handleDeleteRoute(idx)">
                  <el-icon><Delete /></el-icon>
                </button>
              </div>

              <div class="rule-inputs-row">
                <div class="rule-field">
                  <span class="field-label">目的网段</span>
                  <input
                    v-model="rt.destSubnet"
                    class="rule-input-sm"
                    placeholder="如 172.16.1.0"
                    @input="handleNodeChange"
                  />
                </div>
                <div class="rule-field">
                  <span class="field-label">子网掩码</span>
                  <input
                    v-model="rt.mask"
                    class="rule-input-sm"
                    placeholder="255.255.255.0"
                    @input="handleNodeChange"
                  />
                </div>
              </div>

              <div class="rule-inputs-row">
                <div class="rule-field" style="width: 100%;">
                  <span class="field-label">下一跳 IP (Next Hop)</span>
                  <input
                    v-model="rt.nextHopIp"
                    class="rule-input-sm"
                    placeholder="如 10.0.0.1 (出口网关)"
                    @input="handleNodeChange"
                  />
                </div>
              </div>

              <div class="rule-desc-row">
                <input
                  v-model="rt.description"
                  class="rule-input-desc"
                  placeholder="路由描述，如：通往OT控制区..."
                  @input="handleNodeChange"
                />
              </div>
            </div>
          </div>
        </div>

        <!-- 防火墙与网关设备：NAT 网络地址转换配置面板 (SNAT / DNAT) -->
        <div v-if="['FIREWALL', 'ROUTER', 'GATEWAY'].includes(localNode.type)" class="acl-section">
          <div class="divider"></div>
          <div class="acl-head-bar">
            <span class="acl-title">🔄 NAT 地址转换 (SNAT / DNAT)</span>
            <div class="nat-btn-group">
              <button class="btn-mini-add" title="新增源地址伪装" @click="handleAddNatRule('SNAT')">
                <el-icon><Plus /></el-icon> SNAT
              </button>
              <button class="btn-mini-add" title="新增目的端口映射" @click="handleAddNatRule('DNAT')">
                <el-icon><Plus /></el-icon> DNAT
              </button>
            </div>
          </div>

          <div v-if="!localNode.natRules || localNode.natRules.length === 0" class="no-acl-tip">
            暂未配置 NAT 规则，数据包透传过境时保持原五元组不变。
          </div>

          <div v-else class="acl-cards-wrap">
            <div
              v-for="(nr, idx) in localNode.natRules"
              :key="nr.id"
              class="acl-edit-card"
              :class="nr.type === 'SNAT' ? 'snat-card' : 'dnat-card'"
            >
              <div class="rule-top-line">
                <span class="rule-idx">N{{ idx + 1 }}</span>
                <span :class="nr.type === 'SNAT' ? 'nat-tag-snat' : 'nat-tag-dnat'">{{ nr.type }}</span>
                <select v-model="nr.type" class="rule-action-select" @change="handleNodeChange">
                  <option value="SNAT">SNAT (源地址伪装)</option>
                  <option value="DNAT">DNAT (端口映射入站)</option>
                </select>
                <button class="btn-del-rule" title="删除此规则" @click="handleDeleteNatRule(idx)">
                  <el-icon><Delete /></el-icon>
                </button>
              </div>

              <!-- SNAT 表单项 -->
              <div v-if="nr.type === 'SNAT'" class="rule-inputs-row">
                <div class="rule-field">
                  <span class="field-label">内部源地址/网段</span>
                  <input
                    v-model="nr.origSrc"
                    class="rule-input-sm"
                    placeholder="如 192.168.1.0/24"
                    @input="handleNodeChange"
                  />
                </div>
                <div class="rule-field">
                  <span class="field-label">转换后伪装 IP</span>
                  <input
                    v-model="nr.transSrc"
                    class="rule-input-sm"
                    placeholder="如 10.0.0.1"
                    @input="handleNodeChange"
                  />
                </div>
              </div>

              <!-- DNAT 表单项 -->
              <div v-else class="rule-inputs-column">
                <div class="rule-inputs-row">
                  <div class="rule-field">
                    <span class="field-label">外部暴露 IP</span>
                    <input
                      v-model="nr.extIp"
                      class="rule-input-sm"
                      placeholder="如 10.0.0.1"
                      @input="handleNodeChange"
                    />
                  </div>
                  <div class="rule-field">
                    <span class="field-label">外部端口</span>
                    <input
                      v-model.number="nr.extPort"
                      type="number"
                      class="rule-input-sm"
                      placeholder="如 8080"
                      @input="handleNodeChange"
                    />
                  </div>
                </div>
                <div class="rule-inputs-row" style="margin-top: 4px;">
                  <div class="rule-field">
                    <span class="field-label">内部目标 IP</span>
                    <input
                      v-model="nr.intIp"
                      class="rule-input-sm"
                      placeholder="如 172.16.1.100"
                      @input="handleNodeChange"
                    />
                  </div>
                  <div class="rule-field">
                    <span class="field-label">内部目标端口</span>
                    <input
                      v-model.number="nr.intPort"
                      type="number"
                      class="rule-input-sm"
                      placeholder="如 6030"
                      @input="handleNodeChange"
                    />
                  </div>
                </div>
              </div>

              <div class="rule-desc-row">
                <input
                  v-model="nr.description"
                  class="rule-input-desc"
                  placeholder="NAT 规则描述，如：出境私网源地址伪装..."
                  @input="handleNodeChange"
                />
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 场景 3：选中了连线链路 -->
      <div v-else-if="localEdge" class="edge-panel">
        <div class="panel-section-title">
          <span>⚡ 链路物理属性</span>
        </div>

        <!-- 源设备与目的设备卡片回显 -->
        <div v-if="localEdge.sourceName || localEdge.targetName" class="edge-nodes-card">
          <div class="edge-node-badge">
            <span class="badge-tag">源端</span>
            <span class="badge-name">{{ localEdge.sourceName || '未指定' }}</span>
          </div>
          <div class="edge-arrow-icon">➔</div>
          <div class="edge-node-badge">
            <span class="badge-tag">宿端</span>
            <span class="badge-name">{{ localEdge.targetName || '未指定' }}</span>
          </div>
        </div>

        <div class="form-item">
          <label>链路带宽</label>
          <div class="input-unit-wrap">
            <input
              v-model="localEdge.bandwidth"
              class="prop-input-sm"
              @input="handleEdgeChange"
            />
            <span class="unit-text">Mbps</span>
          </div>
        </div>

        <div class="form-item">
          <label>单向延迟</label>
          <div class="input-unit-wrap">
            <input
              v-model.number="localEdge.latencyMs"
              type="number"
              class="prop-input-sm"
              @input="handleEdgeChange"
            />
            <span class="unit-text">ms</span>
          </div>
        </div>

        <!-- 工业应急故障演练：链路光纤切断/挖断注入 -->
        <div class="form-item fault-injection-box" :class="{ 'is-offline': localEdge.disabled }">
          <div class="fault-header">
            <span class="fault-title">⚡ 物理链路切断 / 故障注入</span>
            <el-switch
              v-model="localEdge.disabled"
              active-text="物理故障"
              inactive-text="通畅正常"
              inline-prompt
              active-color="#ef4444"
              inactive-color="#10b981"
              @change="handleEdgeChange"
            />
          </div>
          <div class="fault-tip" v-if="localEdge.disabled">
            已模拟光纤挖断/端口断开！寻路引擎将避开此链路并启用备用路由自愈。
          </div>
        </div>
      </div>

      <!-- 场景 4：选中了安全大区底板 -->
      <div v-else-if="localZone" class="zone-panel">
        <div class="panel-section-title">
          <span>🔲 安全大区底板配置</span>
        </div>

        <div class="form-item">
          <label>大区名称 / 标题</label>
          <input
            v-model="localZone.name"
            class="prop-input-full"
            placeholder="例如：IT信息大区 / 生产控制区"
            @input="handleZoneChange"
          />
        </div>

        <div class="form-item">
          <label>大区分配网段 (Subnet Prefix)</label>
          <div class="input-unit-wrap">
            <input
              v-model="localZone.subnetPrefix"
              class="prop-input-full"
              placeholder="如 192.168.1 或 10.0.0"
              @input="handleZoneChange"
            />
            <span class="unit-text">.0/24</span>
          </div>
          <div class="ip-sub-hint">
            🌐 拖入或位于该区域内的设备，将自动继承此 IP 网段前缀。
          </div>
        </div>

        <div class="form-item">
          <label>默认网关 (Gateway)</label>
          <input
            v-model="localZone.gateway"
            class="prop-input-full"
            placeholder="如 192.168.1.1"
            @input="handleZoneChange"
          />
        </div>

        <div class="form-item">
          <label>主题与边框颜色</label>
          <div class="color-picker-row">
            <input
              v-model="localZone.borderColor"
              type="color"
              class="color-input"
              @change="handleZoneChange"
            />
            <input
              v-model="localZone.borderColor"
              class="prop-input-sm"
              placeholder="#60a5fa"
              @input="handleZoneChange"
            />
          </div>
        </div>

        <!-- 常用配色快捷色板 -->
        <div class="form-item">
          <label>快速预设色板</label>
          <div class="preset-colors">
            <span
              v-for="color in ['#60a5fa', '#4ade80', '#f87171', '#facc15', '#c084fc', '#38bdf8']"
              :key="color"
              class="color-dot"
              :style="{ background: color }"
              :title="color"
              @click="localZone.borderColor = color; handleZoneChange()"
            ></span>
          </div>
        </div>

        <div class="form-item">
          <label>背景填充透明度</label>
          <input
            v-model="localZone.bgColor"
            class="prop-input-full"
            placeholder="rgba(96, 165, 250, 0.12)"
            @input="handleZoneChange"
          />
        </div>

        <div class="form-item">
          <label>边框样式</label>
          <select
            v-model="localZone.isDashed"
            class="prop-select-full"
            @change="handleZoneChange"
          >
            <option :value="false">实线边框 (常规安全域)</option>
            <option :value="true">虚线边框 (隔离/防护边界)</option>
          </select>
        </div>

        <div class="form-item">
          <label>标题字号</label>
          <div class="input-unit-wrap">
            <input
              v-model.number="localZone.fontSize"
              type="number"
              min="9"
              max="24"
              class="prop-input-sm"
              @input="handleZoneChange"
            />
            <span class="unit-text">px</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部操作按钮 -->
    <div class="inspector-footer">
      <button class="btn-simulate" @click="emit('simulate')">
        <span>▶</span> 应用并测试连通性
      </button>
    </div>
  </div>
</template>

<style scoped>
.property-inspector {
  width: 270px;
  background: #1b2430;
  border-left: 1px solid #2b394b;
  display: flex;
  flex-direction: column;
}

.inspector-header {
  padding: 10px 14px;
  font-size: 12px;
  font-weight: 600;
  color: #e2e8f0;
  border-bottom: 1px solid #2b394b;
}

.inspector-body {
  padding: 14px;
  flex: 1;
  overflow-y: auto;
}

.empty-tip {
  text-align: center;
  padding: 40px 10px;
  color: #64748b;
}
.tip-icon { font-size: 28px; margin-bottom: 8px; }
.tip-title { font-size: 13px; font-weight: 600; color: #94a3b8; margin-bottom: 6px; }
.tip-text { font-size: 11px; line-height: 1.5; }

.panel-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  font-weight: 600;
  color: #38bdf8;
  margin-bottom: 12px;
}

.type-tag {
  background: #243040;
  color: #94a3b8;
  border: 1px solid #334155;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 10px;
  font-family: monospace;
}

.form-item {
  margin-bottom: 12px;
}

.fault-injection-box {
  background: #1e293b;
  border: 1px solid #334155;
  border-radius: 6px;
  padding: 8px 10px;
  margin-top: 6px;
  margin-bottom: 14px;
  transition: all 0.2s;
}

.fault-injection-box.is-offline {
  background: rgba(239, 68, 68, 0.12);
  border-color: #ef4444;
}

.fault-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.fault-title {
  font-size: 11px;
  font-weight: 600;
  color: #f1f5f9;
}

.fault-tip {
  margin-top: 6px;
  font-size: 10px;
  color: #fca5a5;
  line-height: 1.4;
  background: rgba(0, 0, 0, 0.2);
  padding: 4px 6px;
  border-radius: 4px;
}

.ip-sub-hint {
  margin-top: 4px;
  font-size: 10px;
  color: #64748b;
  line-height: 1.35;
}

.form-item label {
  display: block;
  font-size: 11px;
  color: #8e9cae;
  margin-bottom: 4px;
}

.ip-split-input-group {
  display: flex;
  align-items: center;
  background: #111720;
  border: 1px solid #2b394b;
  border-radius: 4px;
  overflow: hidden;
  transition: border-color 0.2s;
}

.ip-split-input-group:focus-within {
  border-color: #38bdf8;
  box-shadow: 0 0 0 1px rgba(56, 189, 248, 0.2);
}

.ip-prefix-badge {
  background: #1e293b;
  color: #94a3b8;
  font-family: monospace;
  font-size: 11px;
  font-weight: bold;
  padding: 6px 8px;
  border-right: 1px solid #2b394b;
  user-select: none;
  white-space: nowrap;
}

.ip-host-suffix-input {
  flex: 1;
  background: transparent;
  border: none;
  padding: 6px 8px;
  color: #38bdf8;
  font-family: monospace;
  font-size: 12px;
  font-weight: bold;
  outline: none;
  width: 100%;
}

.prop-input-full, .prop-select-full {
  width: 100%;
  background: #111720;
  border: 1px solid #2b394b;
  border-radius: 4px;
  padding: 6px 8px;
  color: #fff;
  font-size: 11px;
  outline: none;
  box-sizing: border-box;
}

.prop-input-full:focus, .prop-select-full:focus {
  border-color: #38bdf8;
}

.input-unit-wrap {
  display: flex;
  align-items: center;
  background: #111720;
  border: 1px solid #2b394b;
  border-radius: 4px;
  padding: 4px 8px;
}

.prop-input-sm {
  background: transparent;
  border: none;
  color: #fff;
  font-size: 11px;
  outline: none;
  width: 100%;
}

.unit-text {
  font-size: 10px;
  color: #64748b;
  margin-left: 4px;
}

.divider {
  height: 1px;
  background: #2b394b;
  margin: 14px 0;
}

.acl-head-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.acl-title {
  font-size: 11px;
  font-weight: 600;
  color: #ef4444;
}

.btn-mini-add {
  background: #0284c7;
  color: #fff;
  border: none;
  border-radius: 4px;
  padding: 2px 6px;
  font-size: 10px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.btn-mini-add:hover { background: #0369a1; }

.no-acl-tip {
  font-size: 10px;
  color: #64748b;
  line-height: 1.4;
  padding: 8px;
  background: #111720;
  border-radius: 4px;
}

.acl-cards-wrap {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.acl-edit-card {
  background: #222d3d;
  border: 1px solid #2b394b;
  border-radius: 6px;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.acl-edit-card.route-card {
  background: #172230;
  border-color: #0284c7;
}

.route-tag {
  background: rgba(56, 189, 248, 0.15);
  border: 1px solid #0284c7;
  color: #38bdf8;
  font-size: 9px;
  font-family: monospace;
  padding: 1px 5px;
  border-radius: 3px;
  font-weight: bold;
}

.rule-top-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.rule-idx {
  font-size: 10px;
  font-weight: bold;
  color: #94a3b8;
}

.action-select {
  background: #111720;
  border: 1px solid #334155;
  border-radius: 4px;
  font-size: 10px;
  font-weight: bold;
  padding: 2px 4px;
  outline: none;
}

.action-select.is-permit { color: #4ade80; border-color: #22c55e; }
.action-select.is-deny { color: #f87171; border-color: #ef4444; }

.btn-del-rule {
  background: transparent;
  border: none;
  color: #64748b;
  cursor: pointer;
  font-size: 12px;
}
.btn-del-rule:hover { color: #ef4444; }

.rule-inputs-row {
  display: flex;
  gap: 8px;
}

.rule-field {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 4px;
  background: #111720;
  border: 1px solid #2b394b;
  border-radius: 4px;
  padding: 2px 4px;
}

.field-label {
  font-size: 9px;
  color: #64748b;
}

.rule-input-sm {
  width: 100%;
  background: transparent;
  border: none;
  color: #fff;
  font-size: 10px;
  outline: none;
}

.rule-desc-row {
  width: 100%;
}

.rule-input-desc {
  width: 100%;
  background: #111720;
  border: 1px solid #2b394b;
  border-radius: 4px;
  padding: 3px 6px;
  color: #cbd5e1;
  font-size: 10px;
  outline: none;
  box-sizing: border-box;
}

.inspector-footer {
  padding: 12px 14px;
  border-top: 1px solid #2b394b;
  background: #141b24;
}

.edge-nodes-card {
  background: #151d28;
  border: 1px solid #2b394b;
  border-radius: 6px;
  padding: 8px 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  gap: 6px;
}

.edge-node-badge {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
}

.badge-tag {
  font-size: 9px;
  color: #64748b;
  text-transform: uppercase;
}

.badge-name {
  font-size: 11px;
  font-weight: 600;
  color: #38bdf8;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.edge-arrow-icon {
  color: #64748b;
  font-size: 12px;
  font-weight: bold;
}

.btn-simulate {
  width: 100%;
  height: 36px;
  background: #0f766e;
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  transition: all 0.2s;
  box-shadow: 0 2px 6px rgba(15, 118, 110, 0.3);
}

.btn-simulate:hover {
  background: #115e59;
}

.color-picker-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.color-input {
  width: 32px;
  height: 28px;
  padding: 0;
  border: 1px solid #2b394b;
  border-radius: 4px;
  background: transparent;
  cursor: pointer;
}

.preset-colors {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
}

.color-dot {
  width: 18px;
  height: 18px;
  border-radius: 4px;
  cursor: pointer;
  border: 1px solid rgba(255, 255, 255, 0.2);
  transition: transform 0.15s, border-color 0.15s;
}

.color-dot:hover {
  transform: scale(1.15);
  border-color: #ffffff;
}

/* NAT 配置面板专属样式 */
.nat-btn-group {
  display: flex;
  gap: 6px;
}

.nat-tag-snat {
  font-size: 9px;
  background: rgba(16, 185, 129, 0.2);
  color: #34d399;
  border: 1px solid #059669;
  padding: 1px 4px;
  border-radius: 3px;
  font-weight: bold;
}

.nat-tag-dnat {
  font-size: 9px;
  background: rgba(139, 92, 246, 0.2);
  color: #a78bfa;
  border: 1px solid #7c3aed;
  padding: 1px 4px;
  border-radius: 3px;
  font-weight: bold;
}

.snat-card {
  border-left: 3px solid #10b981;
}

.dnat-card {
  border-left: 3px solid #8b5cf6;
}

.rule-action-select {
  background: #111720;
  border: 1px solid #334155;
  color: #cbd5e1;
  border-radius: 4px;
  font-size: 10px;
  padding: 1px 4px;
  outline: none;
}
</style>
