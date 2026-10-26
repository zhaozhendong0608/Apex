<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

const emit = defineEmits<{
  (e: 'importSuccess', data: { nodes: any[]; edges: any[]; zones: any[] }): void
}>()

const visible = ref(false)
const activeTab = ref<'csv' | 'cisco'>('csv')
const csvContent = ref<string>('')
const ciscoContent = ref<string>('')

// 默认 CSV 演示数据 (干净正规的列头，带#号注释段)
const defaultCsvSample = `所属大区,设备名称,设备类型,IP地址,子网掩码,默认网关,备注说明
IT_ZONE,研发办公PC,HOST,192.168.1.10,255.255.255.0,192.168.1.1,研发办公终端
IT_ZONE,监控大屏服务,SERVER,192.168.1.5,255.255.255.0,192.168.1.1,Web监控大屏
IT_ZONE,IT核心路由器,ROUTER,192.168.1.1,255.255.255.0,,三层骨干汇聚路由
DMZ_ZONE,边界防火墙,FIREWALL,10.0.0.1,255.255.255.0,,跨区安全边界防护
DMZ_ZONE,单向安全网闸,GAP,10.0.0.2,255.255.255.0,,物理单向数据摆渡
OT_ZONE,工业核心交换机,SWITCH,172.16.1.1,255.255.255.0,,生产控制区骨干交换机
OT_ZONE,产线PLC工控机,PLC,172.16.1.50,255.255.255.0,172.16.1.1,西门子S7-1500
OT_ZONE,时序数据库,TDENGINE,172.16.1.100,255.255.255.0,172.16.1.1,IoT时序存储集群

# === 物理网络互联链路 ===
# LINKS
源设备名称,目标设备名称,带宽Mbps,延迟ms
研发办公PC,IT核心路由器,1000,2
监控大屏服务,IT核心路由器,1000,2
IT核心路由器,边界防火墙,1000,2
边界防火墙,工业核心交换机,1000,2
单向安全网闸,工业核心交换机,1000,2
工业核心交换机,产线PLC工控机,100,2
工业核心交换机,时序数据库,1000,2
`

const defaultCiscoSample = `! Cisco/Huawei 工业路由器与防火墙配置逆向示例
hostname Industrial-Core-FW
!
interface GigabitEthernet0/0
 description Link_To_IT_Zone
 ip address 10.0.0.1 255.255.255.0
!
interface GigabitEthernet0/1
 description Link_To_OT_Production
 ip address 172.16.1.1 255.255.255.0
!
ip route 192.168.1.0 255.255.255.0 10.0.0.2
ip route 172.16.1.0 255.255.255.0 172.16.1.1
!
ip access-list extended OT_SECURITY_ACL
 permit tcp 192.168.1.0 0.0.0.255 host 172.16.1.100 eq 6030
 permit tcp any any eq 1883
 deny tcp any any eq 22
 deny tcp any any eq 3389
`

const open = () => {
  visible.value = true
  if (!csvContent.value) {
    csvContent.value = defaultCsvSample
  }
  if (!ciscoContent.value) {
    ciscoContent.value = defaultCiscoSample
  }
}

const close = () => {
  visible.value = false
}

// 一键下载标准双 Sheet 的专业 Excel 模板 (.xls)
const handleDownloadExcel = () => {
  const excelXml = `<?xml version="1.0"?>
<?mso-application progid="Excel.Sheet"?>
<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet"
 xmlns:o="urn:schemas-microsoft-com:office:office"
 xmlns:x="urn:schemas-microsoft-com:office:excel"
 xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet"
 xmlns:html="http://www.w3.org/TR/REC-html40">
 <Styles>
  <Style ss:ID="Header">
   <Font ss:Bold="1" ss:Color="#FFFFFF"/>
   <Interior ss:Color="#1E293B" ss:Pattern="Solid"/>
   <Alignment ss:Horizontal="Center" ss:Vertical="Center"/>
  </Style>
  <Style ss:ID="Data">
   <Alignment ss:Horizontal="Left" ss:Vertical="Center"/>
  </Style>
 </Styles>
 <Worksheet ss:Name="1.设备资产清单">
  <Table ss:DefaultColumnWidth="120" ss:DefaultRowHeight="20">
   <Row ss:StyleID="Header">
    <Cell><Data ss:Type="String">所属大区(必填)</Data></Cell>
    <Cell><Data ss:Type="String">设备名称(必填)</Data></Cell>
    <Cell><Data ss:Type="String">设备类型(必填)</Data></Cell>
    <Cell><Data ss:Type="String">IP地址(必填)</Data></Cell>
    <Cell><Data ss:Type="String">子网掩码</Data></Cell>
    <Cell><Data ss:Type="String">默认网关</Data></Cell>
    <Cell><Data ss:Type="String">备注说明</Data></Cell>
   </Row>
   <Row ss:StyleID="Data">
    <Cell><Data ss:Type="String">IT_ZONE</Data></Cell>
    <Cell><Data ss:Type="String">研发办公PC</Data></Cell>
    <Cell><Data ss:Type="String">HOST</Data></Cell>
    <Cell><Data ss:Type="String">192.168.1.10</Data></Cell>
    <Cell><Data ss:Type="String">255.255.255.0</Data></Cell>
    <Cell><Data ss:Type="String">192.168.1.1</Data></Cell>
    <Cell><Data ss:Type="String">研发办公终端</Data></Cell>
   </Row>
   <Row ss:StyleID="Data">
    <Cell><Data ss:Type="String">IT_ZONE</Data></Cell>
    <Cell><Data ss:Type="String">监控大屏服务</Data></Cell>
    <Cell><Data ss:Type="String">SERVER</Data></Cell>
    <Cell><Data ss:Type="String">192.168.1.5</Data></Cell>
    <Cell><Data ss:Type="String">255.255.255.0</Data></Cell>
    <Cell><Data ss:Type="String">192.168.1.1</Data></Cell>
    <Cell><Data ss:Type="String">Web监控大屏</Data></Cell>
   </Row>
   <Row ss:StyleID="Data">
    <Cell><Data ss:Type="String">IT_ZONE</Data></Cell>
    <Cell><Data ss:Type="String">IT核心路由器</Data></Cell>
    <Cell><Data ss:Type="String">ROUTER</Data></Cell>
    <Cell><Data ss:Type="String">192.168.1.1</Data></Cell>
    <Cell><Data ss:Type="String">255.255.255.0</Data></Cell>
    <Cell><Data ss:Type="String"></Data></Cell>
    <Cell><Data ss:Type="String">三层骨干汇聚路由</Data></Cell>
   </Row>
   <Row ss:StyleID="Data">
    <Cell><Data ss:Type="String">DMZ_ZONE</Data></Cell>
    <Cell><Data ss:Type="String">边界防火墙</Data></Cell>
    <Cell><Data ss:Type="String">FIREWALL</Data></Cell>
    <Cell><Data ss:Type="String">10.0.0.1</Data></Cell>
    <Cell><Data ss:Type="String">255.255.255.0</Data></Cell>
    <Cell><Data ss:Type="String"></Data></Cell>
    <Cell><Data ss:Type="String">跨区安全边界防护</Data></Cell>
   </Row>
   <Row ss:StyleID="Data">
    <Cell><Data ss:Type="String">DMZ_ZONE</Data></Cell>
    <Cell><Data ss:Type="String">单向安全网闸</Data></Cell>
    <Cell><Data ss:Type="String">GAP</Data></Cell>
    <Cell><Data ss:Type="String">10.0.0.2</Data></Cell>
    <Cell><Data ss:Type="String">255.255.255.0</Data></Cell>
    <Cell><Data ss:Type="String"></Data></Cell>
    <Cell><Data ss:Type="String">物理单向数据摆渡</Data></Cell>
   </Row>
   <Row ss:StyleID="Data">
    <Cell><Data ss:Type="String">OT_ZONE</Data></Cell>
    <Cell><Data ss:Type="String">工业核心交换机</Data></Cell>
    <Cell><Data ss:Type="String">SWITCH</Data></Cell>
    <Cell><Data ss:Type="String">172.16.1.1</Data></Cell>
    <Cell><Data ss:Type="String">255.255.255.0</Data></Cell>
    <Cell><Data ss:Type="String"></Data></Cell>
    <Cell><Data ss:Type="String">生产控制区骨干交换机</Data></Cell>
   </Row>
   <Row ss:StyleID="Data">
    <Cell><Data ss:Type="String">OT_ZONE</Data></Cell>
    <Cell><Data ss:Type="String">产线PLC工控机</Data></Cell>
    <Cell><Data ss:Type="String">PLC</Data></Cell>
    <Cell><Data ss:Type="String">172.16.1.50</Data></Cell>
    <Cell><Data ss:Type="String">255.255.255.0</Data></Cell>
    <Cell><Data ss:Type="String">172.16.1.1</Data></Cell>
    <Cell><Data ss:Type="String">西门子S7-1500</Data></Cell>
   </Row>
   <Row ss:StyleID="Data">
    <Cell><Data ss:Type="String">OT_ZONE</Data></Cell>
    <Cell><Data ss:Type="String">时序数据库</Data></Cell>
    <Cell><Data ss:Type="String">TDENGINE</Data></Cell>
    <Cell><Data ss:Type="String">172.16.1.100</Data></Cell>
    <Cell><Data ss:Type="String">255.255.255.0</Data></Cell>
    <Cell><Data ss:Type="String">172.16.1.1</Data></Cell>
    <Cell><Data ss:Type="String">IoT时序存储集群</Data></Cell>
   </Row>
  </Table>
 </Worksheet>
 <Worksheet ss:Name="2.物理网络链路">
  <Table ss:DefaultColumnWidth="140" ss:DefaultRowHeight="20">
   <Row ss:StyleID="Header">
    <Cell><Data ss:Type="String">源设备名称(必填)</Data></Cell>
    <Cell><Data ss:Type="String">目标设备名称(必填)</Data></Cell>
    <Cell><Data ss:Type="String">链路带宽(Mbps)</Data></Cell>
    <Cell><Data ss:Type="String">链路延迟(ms)</Data></Cell>
   </Row>
   <Row ss:StyleID="Data">
    <Cell><Data ss:Type="String">研发办公PC</Data></Cell>
    <Cell><Data ss:Type="String">IT核心路由器</Data></Cell>
    <Cell><Data ss:Type="Number">1000</Data></Cell>
    <Cell><Data ss:Type="Number">2</Data></Cell>
   </Row>
   <Row ss:StyleID="Data">
    <Cell><Data ss:Type="String">监控大屏服务</Data></Cell>
    <Cell><Data ss:Type="String">IT核心路由器</Data></Cell>
    <Cell><Data ss:Type="Number">1000</Data></Cell>
    <Cell><Data ss:Type="Number">2</Data></Cell>
   </Row>
   <Row ss:StyleID="Data">
    <Cell><Data ss:Type="String">IT核心路由器</Data></Cell>
    <Cell><Data ss:Type="String">边界防火墙</Data></Cell>
    <Cell><Data ss:Type="Number">1000</Data></Cell>
    <Cell><Data ss:Type="Number">2</Data></Cell>
   </Row>
   <Row ss:StyleID="Data">
    <Cell><Data ss:Type="String">边界防火墙</Data></Cell>
    <Cell><Data ss:Type="String">工业核心交换机</Data></Cell>
    <Cell><Data ss:Type="Number">1000</Data></Cell>
    <Cell><Data ss:Type="Number">2</Data></Cell>
   </Row>
   <Row ss:StyleID="Data">
    <Cell><Data ss:Type="String">单向安全网闸</Data></Cell>
    <Cell><Data ss:Type="String">工业核心交换机</Data></Cell>
    <Cell><Data ss:Type="Number">1000</Data></Cell>
    <Cell><Data ss:Type="Number">2</Data></Cell>
   </Row>
   <Row ss:StyleID="Data">
    <Cell><Data ss:Type="String">工业核心交换机</Data></Cell>
    <Cell><Data ss:Type="String">产线PLC工控机</Data></Cell>
    <Cell><Data ss:Type="Number">100</Data></Cell>
    <Cell><Data ss:Type="Number">2</Data></Cell>
   </Row>
   <Row ss:StyleID="Data">
    <Cell><Data ss:Type="String">工业核心交换机</Data></Cell>
    <Cell><Data ss:Type="String">时序数据库</Data></Cell>
    <Cell><Data ss:Type="Number">1000</Data></Cell>
    <Cell><Data ss:Type="Number">2</Data></Cell>
   </Row>
  </Table>
 </Worksheet>
</Workbook>`

  const blob = new Blob([excelXml], { type: 'application/vnd.ms-excel;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.setAttribute('href', url)
  link.setAttribute('download', `工业网络资产与拓扑导入模板_${new Date().toISOString().slice(0, 10)}.xls`)
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
  ElMessage.success('已下载标准多Sheet专业 Excel 模板 (.xls)！打开后包含设备与链路双标签页')
}

// 一键下载标准带 BOM UTF-8 的规整 CSV 模板
const handleDownloadCsv = () => {
  // UTF-8 BOM: \uFEFF，确保在任何版本的 Excel/WPS 打开时绝不乱码且分列正常
  const blob = new Blob(['\uFEFF' + defaultCsvSample], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.setAttribute('href', url)
  link.setAttribute('download', `工业网络资产与拓扑导入模板_${new Date().toISOString().slice(0, 10)}.csv`)
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
  ElMessage.success('已成功下载标准 CSV 模板！带 BOM 防乱码标识')
}

// 处理本地文件上传读取
const handleFileUpload = (e: Event) => {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  const reader = new FileReader()
  reader.onload = (evt) => {
    const text = evt.target?.result as string
    if (text) {
      csvContent.value = text
      ElMessage.success(`已载入文件 [${file.name}]，请点击下方“解析并生成拓扑”`)
    }
  }
  reader.readAsText(file, 'utf-8')
}

// 执行解析并驱动画布自动生成
const handleExecuteImport = () => {
  if (activeTab.value === 'csv') {
    parseAndEmitCsv()
  } else {
    parseAndEmitCisco()
  }
}

// 解析 CSV 并构建整网结构
const parseAndEmitCsv = () => {
  const allLines = csvContent.value.split('\n').map(l => l.trim()).filter(Boolean)
  if (allLines.length === 0) {
    ElMessage.warning('内容为空，请粘贴有效的 CSV 资产与链路数据')
    return
  }

  const rawNodes: Array<{ zone: string; name: string; type: string; ip: string; subnet: string; gateway: string; desc: string }> = []
  const rawEdges: Array<{ srcName: string; dstName: string; bw: string; lat: number }> = []

  let isParsingLinks = false

  for (const line of allLines) {
    // 识别链路段标记 (支持 # LINKS, LINKS, 或含链路关键字)
    if (line.toUpperCase().includes('LINKS') || line.includes('物理网络互联链路') || line.includes('互联链路')) {
      isParsingLinks = true
      continue
    }

    // 普通注释行跳过
    if (line.startsWith('#')) {
      continue
    }

    // 表头行跳过
    if (line.includes('所属大区') || line.includes('设备名称') || line.includes('源设备名称')) {
      continue
    }

    const parts = line.split(',').map(p => p.trim())
    if (!isParsingLinks) {
      // 设备资产清单：大区,设备名称,类型,IP地址,子网掩码,默认网关,备注
      if (parts.length >= 4) {
        rawNodes.push({
          zone: parts[0] || 'IT_ZONE',
          name: parts[1],
          type: (parts[2] || 'HOST').toUpperCase(),
          ip: parts[3],
          subnet: parts[4] || '255.255.255.0',
          gateway: parts[5] || '',
          desc: parts[6] || ''
        })
      }
    } else {
      // 物理互联链路：源设备名称,目标设备名称,带宽Mbps,延迟ms
      if (parts.length >= 2 && parts[0] && parts[1]) {
        rawEdges.push({
          srcName: parts[0],
          dstName: parts[1],
          bw: parts[2] || '1000',
          lat: parts[3] ? Number(parts[3]) : 2
        })
      }
    }
  }

  if (rawNodes.length === 0) {
    ElMessage.error('未能识别到有效的设备节点，请检查第一部分数据格式')
    return
  }

  emit('importSuccess', { nodes: rawNodes, edges: rawEdges, zones: [] })
  ElMessage.success(`🎉 逆向导入成功！已识别 ${rawNodes.length} 台设备、${rawEdges.length} 条互联物理链路！`)
  close()
}

// 解析 Cisco 配置文本并自动逆向出防火墙/路由器与其 ACL
const parseAndEmitCisco = () => {
  const text = ciscoContent.value
  const lines = text.split('\n')

  let hostname = 'Cisco-Gateway'
  const interfaces: Array<{ name: string; ip: string; mask: string }> = []
  const acls: any[] = []
  const routes: any[] = []

  let curIf: { name: string; ip: string; mask: string } | null = null

  lines.forEach(line => {
    line = line.trim()
    if (line.startsWith('hostname ')) {
      hostname = line.split(' ')[1]
    } else if (line.startsWith('interface ')) {
      curIf = { name: line.split(' ')[1], ip: '', mask: '' }
      interfaces.push(curIf)
    } else if (curIf && line.startsWith('ip address ')) {
      const parts = line.split(' ')
      if (parts.length >= 4) {
        curIf.ip = parts[2]
        curIf.mask = parts[3]
      }
    } else if (line.startsWith('ip route ')) {
      const parts = line.split(' ')
      if (parts.length >= 5) {
        routes.push({
          id: 'rt_' + Math.random().toString(36).substring(2, 6),
          destSubnet: parts[2],
          mask: parts[3],
          nextHopIp: parts[4],
          description: 'Cisco逆向静态路由'
        })
      }
    } else if (line.startsWith('permit ') || line.startsWith('deny ')) {
      const isPermit = line.startsWith('permit')
      let port = 0
      let desc = '配置ACL规则'
      if (line.includes('eq 6030')) { port = 6030; desc = '放行时序数据' }
      else if (line.includes('eq 1883')) { port = 1883; desc = '放行MQTT采集' }
      else if (line.includes('eq 22')) { port = 22; desc = '阻断SSH远程' }
      else if (line.includes('eq 3389')) { port = 3389; desc = '阻断RDP远程桌面' }

      acls.push({
        id: 'cisco_acl_' + Math.random().toString(36).substring(2, 6),
        priority: acls.length + 1,
        action: isPermit ? 'PERMIT' : 'DENY',
        destPort: port || 6030,
        protocol: 'TCP',
        description: desc
      })
    }
  })

  const mainIp = interfaces.find(i => i.ip)?.ip || '10.0.0.1'
  const createdDevice = {
    zone: 'DMZ_ZONE',
    name: hostname,
    type: 'FIREWALL',
    ip: mainIp,
    subnet: '255.255.255.0',
    gateway: '',
    desc: `由 Cisco 配置逆向生成，解析出 ${interfaces.length} 个接口、${routes.length} 条静态路由、${acls.length} 条 ACL 规则`,
    acls,
    routes
  }

  emit('importSuccess', { nodes: [createdDevice], edges: [], zones: [] })
  ElMessage.success(`🎉 Cisco 配置逆向成功！提取出主设备 [${hostname}]、${routes.length} 条路由与 ${acls.length} 条 ACL 规则！`)
  close()
}

defineExpose({ open, close })
</script>

<template>
  <el-dialog
    v-model="visible"
    title="📥 工业网络资产与拓扑逆向一键导入"
    width="780px"
    class="import-topology-dialog"
    destroy-on-close
    append-to-body
  >
    <div class="import-modal-body">
      <!-- 顶部功能介绍与模板下载条 -->
      <div class="template-download-banner">
        <div class="banner-left">
          <span class="banner-icon">📊</span>
          <div class="banner-info">
            <span class="banner-title">支持标准 CSV / Excel 表格批量导入资产</span>
            <span class="banner-desc">无需手动拖拽！填好设备与链路，一键在画布自动生成分区与连线。</span>
          </div>
        </div>
        <div class="banner-right">
          <button class="btn-download-tpl excel" title="在 WPS / Excel 中打开包含双工作表(设备清单+链路表)" @click="handleDownloadExcel">
            📊 下载标准 Excel 模板 (.xls)
          </button>
          <button class="btn-download-tpl csv" title="下载带 UTF-8 BOM 防乱码的标准纯净 CSV 格式" @click="handleDownloadCsv">
            📋 下载标准 CSV 模板 (.csv)
          </button>
          <label class="btn-upload-file">
            📁 上传文件
            <input type="file" accept=".csv,.txt" style="display: none;" @change="handleFileUpload" />
          </label>
        </div>
      </div>

      <!-- 标签页选择：CSV 表格导入 vs Cisco/Huawei 配置逆向 -->
      <div class="tab-header">
        <button
          class="tab-btn"
          :class="{ active: activeTab === 'csv' }"
          @click="activeTab = 'csv'"
        >
          📋 资产与互联链路表格 (CSV 批量导入)
        </button>
        <button
          class="tab-btn"
          :class="{ active: activeTab === 'cisco' }"
          @click="activeTab = 'cisco'"
        >
          ⚙️ 真实设备配置逆向 (Cisco / Huawei 配置文本)
        </button>
      </div>

      <!-- CSV 输入区 -->
      <div v-if="activeTab === 'csv'" class="tab-content">
        <div class="content-tips">
          💡 包含两段数据：上方为 <b>设备资产清单</b>，下方通过 <b># LINKS</b> 分隔指定 <b>物理连线</b>（可直接在下方编辑或直接粘贴 Excel 导出的 CSV 内容）：
        </div>
        <textarea
          v-model="csvContent"
          class="code-textarea"
          rows="13"
          placeholder="粘贴您的资产与拓扑链路数据..."
        ></textarea>
      </div>

      <!-- Cisco 配置输入区 -->
      <div v-else class="tab-content">
        <div class="content-tips">
          💡 直接粘贴 Cisco / Huawei 交换机导出的 <code>show running-config</code> 文本，系统自动逆向出设备 IP、接口、静态路由与 ACL 访问控制规则：
        </div>
        <textarea
          v-model="ciscoContent"
          class="code-textarea font-mono"
          rows="13"
          placeholder="粘贴 Cisco / Huawei 配置文本..."
        ></textarea>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="close">取消</el-button>
        <el-button type="primary" class="btn-confirm-import" @click="handleExecuteImport">
          🚀 确认解析并一键生成拓扑
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.import-modal-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.template-download-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(30, 41, 59, 0.7);
  border: 1px solid #334155;
  border-radius: 8px;
  padding: 10px 14px;
}

.banner-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.banner-icon {
  font-size: 24px;
}

.banner-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.banner-title {
  font-size: 13px;
  font-weight: bold;
  color: #38bdf8;
}

.banner-desc {
  font-size: 11px;
  color: #94a3b8;
}

.banner-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.btn-download-tpl {
  color: #ffffff;
  border: none;
  border-radius: 6px;
  padding: 6px 10px;
  font-size: 11px;
  font-weight: bold;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  gap: 4px;
}

.btn-download-tpl.excel {
  background: #166534;
  border: 1px solid #22c55e;
  box-shadow: 0 2px 6px rgba(34, 197, 94, 0.2);
}

.btn-download-tpl.excel:hover {
  background: #15803d;
  box-shadow: 0 2px 8px rgba(34, 197, 94, 0.4);
}

.btn-download-tpl.csv {
  background: #0284c7;
  border: 1px solid #38bdf8;
  box-shadow: 0 2px 6px rgba(2, 132, 199, 0.2);
}

.btn-download-tpl.csv:hover {
  background: #0369a1;
  box-shadow: 0 2px 8px rgba(2, 132, 199, 0.4);
}

.btn-upload-file {
  background: #334155;
  color: #e2e8f0;
  border: 1px solid #475569;
  border-radius: 6px;
  padding: 6px 12px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-upload-file:hover {
  background: #475569;
  color: #ffffff;
}

.tab-header {
  display: flex;
  gap: 8px;
  border-bottom: 1px solid #334155;
  padding-bottom: 8px;
}

.tab-btn {
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  padding: 6px 14px;
  color: #94a3b8;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.tab-btn:hover {
  color: #e2e8f0;
  background: rgba(51, 65, 85, 0.3);
}

.tab-btn.active {
  background: #1e293b;
  border-color: #38bdf8;
  color: #38bdf8;
  font-weight: bold;
}

.tab-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.content-tips {
  font-size: 11px;
  color: #94a3b8;
  line-height: 1.5;
}

.code-textarea {
  width: 100%;
  background: #0f172a;
  border: 1px solid #334155;
  border-radius: 6px;
  color: #38bdf8;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 11px;
  padding: 10px;
  line-height: 1.6;
  outline: none;
  box-sizing: border-box;
  resize: vertical;
}

.code-textarea:focus {
  border-color: #38bdf8;
  box-shadow: 0 0 8px rgba(56, 189, 248, 0.3);
}

.btn-confirm-import {
  background: #059669 !important;
  border-color: #059669 !important;
  font-weight: bold;
}

.btn-confirm-import:hover {
  background: #047857 !important;
}
</style>
