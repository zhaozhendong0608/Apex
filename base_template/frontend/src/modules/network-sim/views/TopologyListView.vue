<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Delete, Edit, Share } from '@element-plus/icons-vue'
import {
  getTopologyPageApi,
  saveTopologyApi,
  deleteTopologyApi,
  type NetTopologyItem
} from '../api/topology'

const router = useRouter()

// 列表查询状态
const loading = ref(false)
const keyword = ref('')
const pageNo = ref(1)
const pageSize = ref(10)
const total = ref(0)
const topologyList = ref<NetTopologyItem[]>([])

// 弹窗状态
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const formData = ref({
  id: undefined as number | undefined,
  name: '',
  description: ''
})

const rules = {
  name: [{ required: true, message: '请输入拓扑方案名称', trigger: 'blur' }]
}

// 获取分页数据
const fetchList = async () => {
  loading.value = true
  try {
    const res = await getTopologyPageApi({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      keyword: keyword.value
    })
    topologyList.value = res.data.records
    total.value = res.data.total
  } catch (error: any) {
    ElMessage.error(error.message || '获取拓扑列表失败')
  } finally {
    loading.value = false
  }
}

// 打开新建弹窗
const handleCreate = () => {
  isEdit.value = false
  formData.value = { id: undefined, name: '', description: '' }
  dialogVisible.value = true
}

// 打开编辑基本信息
const handleEdit = (row: NetTopologyItem) => {
  isEdit.value = true
  formData.value = { id: row.id, name: row.name, description: row.description || '' }
  dialogVisible.value = true
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid: boolean) => {
    if (valid) {
      try {
        await saveTopologyApi({
          id: formData.value.id,
          name: formData.value.name,
          description: formData.value.description
        })
        ElMessage.success(isEdit.value ? '修改成功' : '创建成功')
        dialogVisible.value = false
        fetchList()
      } catch (err: any) {
        ElMessage.error(err.message || '操作失败')
      }
    }
  })
}

// 删除拓扑
const handleDelete = (row: NetTopologyItem) => {
  ElMessageBox.confirm(`确定要彻底删除拓扑方案【${row.name}】吗？`, '删除确认', {
    confirmButtonText: '确定删除',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await deleteTopologyApi(row.id)
      ElMessage.success('拓扑方案已删除')
      fetchList()
    } catch (err: any) {
      ElMessage.error(err.message || '删除失败')
    }
  })
}

// 进入全屏设计画布
const handleOpenCanvas = (row: NetTopologyItem) => {
  router.push({
    path: '/network-sim/designer',
    query: { id: row.id }
  })
}

onMounted(() => {
  fetchList()
})
</script>

<template>
  <div class="topology-list-container">
    <div class="page-header">
      <div class="header-left">
        <h2>🌐 网络拓扑仿真方案管理</h2>
        <span class="sub-desc">管理电力、工业与企业网络拓扑方案，点击即可进入 Flowsint 全屏仿真工作台。</span>
      </div>
      <el-button type="primary" :icon="Plus" @click="handleCreate">新建拓扑方案</el-button>
    </div>

    <!-- 检索与工具条 -->
    <div class="search-bar">
      <el-input
        v-model="keyword"
        placeholder="搜索拓扑方案名称或描述..."
        clearable
        style="width: 320px"
        @keyup.enter="fetchList"
        @clear="fetchList"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-button type="default" @click="fetchList">查询</el-button>
    </div>

    <!-- 方案表格 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="topologyList" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="name" label="拓扑方案名称" min-width="180">
          <template #default="{ row }">
            <span class="topo-name" @click="handleOpenCanvas(row)">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="详细描述" min-width="220" show-overflow-tooltip />
        <el-table-column prop="nodeCount" label="设备节点数" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.nodeCount || 0 }} 节点</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="edgeCount" label="物理链路数" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="success">{{ row.edgeCount || 0 }} 链路</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="最后修改时间" width="170" align="center" />
        <el-table-column label="操作" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" size="small" :icon="Share" @click="handleOpenCanvas(row)">
              设计拓扑
            </el-button>
            <el-button link type="primary" size="small" :icon="Edit" @click="handleEdit(row)">
              编辑
            </el-button>
            <el-button link type="danger" size="small" :icon="Delete" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页组件 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </div>
    </el-card>

    <!-- 新建/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑拓扑方案信息' : '新建网络拓扑方案'"
      width="480px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="formData" :rules="rules" label-position="top">
        <el-form-item label="拓扑方案名称" prop="name">
          <el-input v-model="formData.name" placeholder="如：电力边缘采集系统 (生产网)" />
        </el-form-item>
        <el-form-item label="详细描述" prop="description">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="3"
            placeholder="简述该网络拓扑涉及的大区、设备类型与主要仿真场景..."
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">保存方案</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.topology-list-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-left h2 {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-main);
  margin-bottom: 4px;
}

.sub-desc {
  font-size: 13px;
  color: var(--text-muted);
}

.search-bar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.table-card {
  border: 1px solid var(--border-color);
  background: var(--bg-card);
}

.topo-name {
  color: var(--primary-color);
  font-weight: 600;
  cursor: pointer;
}
.topo-name:hover {
  text-decoration: underline;
}

.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
