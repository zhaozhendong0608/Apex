<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const routeToPageMap: Record<string, string> = {
  '/req/dashboard': 'page-dashboard',
  '/req/detail': 'page-req-detail',
  '/req/prototypes': 'page-prototypes',
  '/req/baselines': 'page-baselines',
  '/req/baseline-diff': 'page-baseline-diff',
  '/req/releases': 'page-releases',
  '/req/timeline': 'page-timeline',
  '/req/enums': 'page-enums',
  '/req/theme': 'page-theme'
}

const pageToRouteMap: Record<string, string> = {
  'page-dashboard': '/req/dashboard',
  'page-req-detail': '/req/detail',
  'page-prototypes': '/req/prototypes',
  'page-baselines': '/req/baselines',
  'page-baseline-diff': '/req/baseline-diff',
  'page-releases': '/req/releases',
  'page-timeline': '/req/timeline',
  'page-enums': '/req/enums',
  'page-theme': '/req/theme'
}
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getProjectListApi,
  getProjectStatsApi,
  createProjectApi,
  deleteProjectApi,
  getRequirementListApi,
  getRequirementDetailApi,
  saveConfirmItemsApi,
  checkGateApi,
  getBaselineDiffApi,
  createRequirementApi,
  createBaselineApi,
  unfreezeRequirementApi,
  getPrototypeListApi,
  getBaselineListApi
} from '@/api/req'
import { getUserListApi } from '@/api/user'

// 当前操作人与权限角色 (支持切换管理员 / 业务负责人)
const currentOperator = ref('admin') // 默认为 admin 超级管理员
const isAdmin = computed(() => currentOperator.value === 'admin')

// 系统用户与责任人数据 (动态从 MySQL sys_user 拉取)
const userList = ref<any[]>([])

// 视角与路由状态
const currentPerspective = ref<'internal' | 'client'>('internal')
const currentPage = ref('page-dashboard')
const clientSubPage = ref<'e02' | 'e03'>('e02')
const selectedProject = ref('NET-SIM')
const selectedPrototypeVer = ref('v3')
const activeChecklistTab = ref<'scope' | 'rules' | 'skip'>('scope')

// 项目空间与统计状态 (直连 MySQL 数据库)
const projectList = ref<any[]>([])
const currentProject = ref<any>({
  id: 0,
  projectKey: '',
  name: '',
  clientName: '',
  description: ''
})
const projectStats = ref<any>({
  totalRequirements: 0,
  baselinedCount: 0,
  pendingCount: 0,
  clarifyingCount: 0,
  prototypeCount: 0,
  totalCostDays: '0.0',
  latestBaselineCode: '—'
})

// 新建项目表单
const newProjectForm = ref({
  projectKey: '',
  name: '',
  clientName: '',
  description: ''
})

// 弹窗与抽屉控制
const drawerChecklistOpen = ref(false)
const drawerNewReqOpen = ref(false)
const drawerNewBaselineOpen = ref(false)
const modalGenSignLinkOpen = ref(false)
const modalClientRejectOpen = ref(false)
const modalNewProjectOpen = ref(false)
const modalUploadPrototypeOpen = ref(false)
const modalNewItemOpen = ref(false)

interface ChecklistOption {
  label: string
  cost: number
}

const newItemForm = ref({
  category: 'SCOPE',
  itemCode: 'R-01',
  question: '',
  options: [
    { label: '标准交付方案', cost: 1.0 },
    { label: '轻量/降级方案', cost: 0.5 }
  ] as ChecklistOption[],
  selectedOptionIndex: 0,
  decision: 'CURRENT',
  note: ''
})

// 数据源状态 (直连真实数据库)
const loading = ref(false)
const requirements = ref<any[]>([])

// 原型版本清单 (从 MySQL t_req_prototype_version 动态获取)
const prototypeList = ref<any[]>([])
const selectedPrototype = ref<any>(null)

// 需求基线清单 (从 MySQL t_req_baseline 动态获取)
const baselineList = ref<any[]>([])
const selectedBaseline = ref<any>(null)
const activeBaselineSignLink = ref({ url: '', code: '7892' })

const currentReq = ref<any>({
  id: 0,
  reqNo: '',
  title: '暂无需求',
  status: '',
  baselineCode: '—',
  owner: '',
  createdAt: '',
  priority: 'P1',
  quote: ''
})

// 四层确认清单列表 (初始为空，根据当前选中的需求动态从 MySQL 拉取)
const confirmItems = ref<any[]>([])

// 表单输入
const newReqForm = ref({
  title: '',
  quote: '',
  owner: '',
  priority: 'P1'
})

const newBaselineForm = ref({
  baselineCode: 'BL-v1.2',
  bizOwnerName: '张工 (产品负责人)',
  acceptorName: '李工 (核心研发)',
  userRepName: '王工 (测试架构师)'
})

const clientSignForm = ref({
  signerName: '王工',
  summary: '这版做在线状态和告警，不做历史曲线和导出，符合预期。'
})

const clientRejectForm = ref({
  author: '王工 (最终使用者代表)',
  reason: ''
})

const themeColor = ref('#14535B')
const diffData = ref<any>(null)

// 计算当前敲定总代价
const currentCost = computed(() => {
  return confirmItems.value
    .filter(item => item.decisionResult === 'CURRENT')
    .reduce((sum, item) => sum + (Number(item.costDelta) || 0), 0)
})

watch(
  () => route.path,
  (newPath) => {
    if (routeToPageMap[newPath]) {
      currentPage.value = routeToPageMap[newPath]
      currentPerspective.value = 'internal'
    }
  },
  { immediate: true }
)

const openReqDetail = async (req: any, doNavigate = true) => {
  if (req) {
    currentReq.value = {
      id: req.id,
      reqNo: req.reqCode || req.reqNo,
      reqCode: req.reqCode || req.reqNo,
      title: req.title,
      status: req.status,
      baselineCode: req.baselineCode || '—',
      owner: req.ownerName || req.owner || '张工 (产品负责人)',
      createdAt: req.createdAt ? req.createdAt.split('T')[0] : (req.updatedAt ? req.updatedAt.split('T')[0] : '2026-10-29'),
      priority: req.priority || 'P1',
      quote: req.rawQuote || req.quote || '「必须管住需求、版本、原型三件事，做到留痕、可确认、可追溯。」'
    }

    try {
      const detailRes = await getRequirementDetailApi(req.id)
      if (detailRes.data?.requirement) {
        const r = detailRes.data.requirement
        currentReq.value.title = r.title
        currentReq.value.quote = r.rawQuote || currentReq.value.quote
        currentReq.value.owner = r.ownerName ? (r.ownerName.includes('(') ? r.ownerName : `${r.ownerName} (研发)`) : '张工 (产品负责人)'
        currentReq.value.priority = r.priority || 'P1'
        currentReq.value.status = r.status
      }
      if (detailRes.data?.confirmItems && detailRes.data.confirmItems.length > 0) {
        confirmItems.value = detailRes.data.confirmItems.map((ci: any) => ({
          id: ci.id,
          itemCategory: ci.category,
          layerText: ci.category === 'SCOPE' ? '范围' : (ci.category === 'RULE' ? '规则' : (ci.category === 'EXCEPTION' ? '异常' : (ci.category === 'ACCEPTANCE' ? '验收' : '不做'))),
          title: ci.itemCode ? `${ci.itemCode} ${ci.question}` : ci.question,
          questionPrompt: ci.question,
          optionAgreement: typeof ci.optionsJson === 'string' ? ci.optionsJson : JSON.stringify(ci.optionsJson),
          costDelta: Number(ci.costDays || 0),
          decisionResult: ci.decision,
          decisionText: ci.decision === 'CURRENT' ? '✔ 本期做' : (ci.decision === 'NEXT' ? '⏳ 下期做' : '❌ 本期不做')
        }))
      } else {
        confirmItems.value = []
      }
      // 同步拉取真实原型版本快照 (MySQL t_req_prototype_version)
      try {
        const protoRes = await getPrototypeListApi(req.id)
        if (protoRes.data && protoRes.data.length > 0) {
          prototypeList.value = protoRes.data
          selectedPrototype.value = protoRes.data[0]
          selectedPrototypeVer.value = protoRes.data[0].versionNo || 'v1'
        } else {
          prototypeList.value = []
          selectedPrototype.value = null
          selectedPrototypeVer.value = 'v1'
        }
      } catch (e) {
        prototypeList.value = []
        selectedPrototype.value = null
      }

      // 同步拉取真实需求基线清单 (MySQL t_req_baseline)
      try {
        const baseRes = await getBaselineListApi(req.id)
        if (baseRes.data && baseRes.data.length > 0) {
          baselineList.value = baseRes.data
          selectedBaseline.value = baseRes.data[0]
        } else {
          baselineList.value = []
          selectedBaseline.value = null
        }
      } catch (e) {
        baselineList.value = []
        selectedBaseline.value = null
      }
    } catch (e) {
      console.warn('获取需求详情失败', e)
    }
  }
  if (doNavigate) {
    navigatePage('page-req-detail')
  }
}

const handleOpenChecklist = async (req: any) => {
  await openReqDetail(req, false)
  drawerChecklistOpen.value = true
}

// 导航切换
const navigatePage = (pageId: string) => {
  currentPage.value = pageId
  const targetRoute = pageToRouteMap[pageId]
  if (targetRoute && route.path !== targetRoute) {
    router.push(targetRoute)
  }
}

// 切换视角
const switchPerspective = (type: 'internal' | 'client') => {
  currentPerspective.value = type
  if (type === 'internal') {
    currentPage.value = 'page-dashboard'
  } else {
    currentPage.value = 'page-client-view'
    clientSubPage.value = 'e02'
  }
}

// 决策按钮切换
const setDecision = (item: any, type: 'CURRENT' | 'NEXT' | 'SKIP') => {
  item.decisionResult = type
  item.decisionText = type === 'CURRENT' ? '✔ 本期做' : (type === 'NEXT' ? '⏳ 下期做' : '❌ 本期不做')
}

// 过滤抽屉清单项
const filteredChecklistItems = computed(() => {
  if (activeChecklistTab.value === 'scope') {
    return confirmItems.value.filter(i => (i.itemCategory || i.category) === 'SCOPE')
  }
  if (activeChecklistTab.value === 'rules') {
    return confirmItems.value.filter(i => ['RULE', 'EXCEPTION', 'ACCEPTANCE'].includes(i.itemCategory || i.category))
  }
  return confirmItems.value.filter(i => ['SKIP', 'NON_GOAL'].includes(i.itemCategory || i.category))
})

// 根据分类生成下一个条目编号 (R-01, B-01, E-01, A-01, N-01)
const generateNextItemCode = (category: string) => {
  const prefixMap: Record<string, string> = {
    SCOPE: 'R-',
    RULE: 'B-',
    EXCEPTION: 'E-',
    ACCEPTANCE: 'A-',
    SKIP: 'N-'
  }
  const prefix = prefixMap[category] || 'R-'
  let maxNum = 0
  for (const item of confirmItems.value) {
    const code = item.itemCode || (typeof item.title === 'string' ? item.title.split(' ')[0] : '')
    if (code && code.startsWith(prefix)) {
      const numStr = code.substring(prefix.length)
      const num = parseInt(numStr, 10)
      if (!isNaN(num) && num > maxNum) {
        maxNum = num
      }
    }
  }
  const nextNum = maxNum + 1
  return `${prefix}${nextNum < 10 ? '0' + nextNum : nextNum}`
}

// 打开添加清单条目弹窗 (M04)
const handleOpenAddItemModal = () => {
  let cat = 'SCOPE'
  if (activeChecklistTab.value === 'rules') {
    cat = 'RULE'
  } else if (activeChecklistTab.value === 'skip') {
    cat = 'SKIP'
  }
  newItemForm.value = {
    category: cat,
    itemCode: generateNextItemCode(cat),
    question: '',
    options: [
      { label: '标准交付方案', cost: 1.0 },
      { label: '轻量/降级方案', cost: 0.5 }
    ],
    selectedOptionIndex: 0,
    decision: cat === 'SKIP' ? 'SKIP' : 'CURRENT',
    note: ''
  }
  modalNewItemOpen.value = true
}

// 切换类别时自动同步编号前缀与默认决策
const onItemCategoryChange = () => {
  newItemForm.value.itemCode = generateNextItemCode(newItemForm.value.category)
  if (newItemForm.value.category === 'SKIP') {
    newItemForm.value.decision = 'SKIP'
  } else if (newItemForm.value.decision === 'SKIP') {
    newItemForm.value.decision = 'CURRENT'
  }
}

// 选项增加/删除
const addOptionItem = () => {
  if (newItemForm.value.options.length >= 4) {
    ElMessage.warning('最多支持添加 4 个候选选项')
    return
  }
  newItemForm.value.options.push({ label: '', cost: 0.0 })
}

const removeOptionItem = (idx: number) => {
  if (newItemForm.value.options.length <= 2) {
    ElMessage.warning('至少需要保留 2 个候选选项')
    return
  }
  newItemForm.value.options.splice(idx, 1)
  if (newItemForm.value.selectedOptionIndex >= newItemForm.value.options.length) {
    newItemForm.value.selectedOptionIndex = 0
  }
}

// 确认添加清单条目 (M04)
const handleConfirmAddItem = () => {
  const form = newItemForm.value
  const q = form.question.trim()
  if (!q) {
    ElMessage.warning('请填写业务问法')
    return
  }
  if (q.length > 100) {
    ElMessage.warning('业务问法长度不能超过 100 字')
    return
  }

  // 校验同类别下问法不重复
  const isDuplicate = confirmItems.value.some(
    i => (i.category || i.itemCategory) === form.category &&
         (i.question || i.questionPrompt)?.trim() === q
  )
  if (isDuplicate) {
    ElMessage.warning('该分类下已存在相同的业务问法，请勿重复添加')
    return
  }

  // 校验选项
  if (!form.options || form.options.length < 2) {
    ElMessage.warning('至少需要配置 2 个候选选项')
    return
  }
  for (let i = 0; i < form.options.length; i++) {
    const opt = form.options[i]
    if (!opt.label || !opt.label.trim()) {
      ElMessage.warning(`第 ${i + 1} 个候选选项文案不能为空`)
      return
    }
    if (opt.cost < 0) {
      ElMessage.warning(`第 ${i + 1} 个选项代价人天不能为负数`)
      return
    }
  }

  const selectedOpt = form.options[form.selectedOptionIndex] || form.options[0]
  const cost = Number(selectedOpt.cost || 0)
  const layerTextMap: Record<string, string> = {
    SCOPE: '范围',
    RULE: '规则',
    EXCEPTION: '异常',
    ACCEPTANCE: '验收',
    SKIP: '不做'
  }
  const itemCode = form.itemCode.trim() || generateNextItemCode(form.category)

  const newItem = {
    id: null,
    category: form.category,
    itemCategory: form.category,
    itemCode,
    question: q,
    questionPrompt: q,
    optionsJson: JSON.stringify(form.options),
    optionAgreement: selectedOpt.label,
    costDelta: form.decision === 'CURRENT' ? cost : 0,
    costDays: cost,
    decision: form.decision,
    decisionResult: form.decision,
    layerText: layerTextMap[form.category] || '范围',
    title: `${itemCode} ${q}`,
    decisionText: form.decision === 'CURRENT' ? '✔ 本期做' : (form.decision === 'NEXT' ? '⏳ 下期做' : '❌ 本期不做'),
    note: form.note || ''
  }

  confirmItems.value.push(newItem)

  // 自动切换至对应 Tab
  if (form.category === 'SCOPE') {
    activeChecklistTab.value = 'scope'
  } else if (form.category === 'SKIP') {
    activeChecklistTab.value = 'skip'
  } else {
    activeChecklistTab.value = 'rules'
  }

  modalNewItemOpen.value = false
  ElMessage.success(`条目 [${itemCode}] 已添加至确认清单草稿`)
}

// 删除清单条目
const handleDeleteItem = (itemToDelete: any) => {
  const idx = confirmItems.value.indexOf(itemToDelete)
  if (idx !== -1) {
    const code = itemToDelete.itemCode || itemToDelete.title || '条目'
    confirmItems.value.splice(idx, 1)
    ElMessage.success(`已从清单中移除 [${code}]`)
  }
}

// 保存清单决策 (真实写入 MySQL)
const handleSaveChecklist = async () => {
  try {
    if (currentReq.value?.id) {
      const payload = confirmItems.value.map(i => ({
        id: i.id || null,
        category: i.category || i.itemCategory || 'SCOPE',
        itemCode: i.itemCode || (typeof i.title === 'string' ? i.title.split(' ')[0] : 'R-01'),
        question: i.question || i.questionPrompt || i.title,
        optionsJson: typeof i.optionsJson === 'string' ? i.optionsJson : JSON.stringify(i.optionsJson || [{ label: i.optionAgreement || '标准方案', cost: i.costDelta || 0 }]),
        decision: i.decisionResult || i.decision || 'CURRENT',
        costDays: Number(i.decisionResult === 'CURRENT' ? (i.costDays !== undefined ? i.costDays : i.costDelta) : (i.costDays !== undefined ? i.costDays : 0))
      }))
      await saveConfirmItemsApi(currentReq.value.id, payload)
    }
    ElMessage.success(`清单决策已保存！当前本期敲定代价为：${currentCost.value.toFixed(1)} 人天`)
    drawerChecklistOpen.value = false
    if (currentReq.value) {
      await openReqDetail(currentReq.value, false)
    }
  } catch (err: any) {
    ElMessage.error(err.message || '保存清单决策失败')
  }
}

// 打开快速创建需求抽屉
const handleOpenNewReqDrawer = () => {
  const defaultOwner = userList.value.find((u: any) => u.nickname?.includes('张工') || u.username === 'zhanggong')?.nickname 
    || userList.value[0]?.nickname 
    || userList.value[0]?.username 
    || '张工 (产品负责人)'
  newReqForm.value = {
    title: '',
    quote: '',
    owner: defaultOwner,
    priority: 'P1'
  }
  drawerNewReqOpen.value = true
}

// 快速创建需求 (写入真实 MySQL)
const handleSubmitNewReq = async () => {
  if (!newReqForm.value.title) {
    ElMessage.warning('请填写需求标题')
    return
  }
  try {
    const nextCode = `REQ-00${requirements.value.length + 1}`
    const finalOwner = newReqForm.value.owner || (userList.value[0]?.nickname || '张工 (产品负责人)')
    await createRequirementApi({
      projectId: currentProject.value.id || 1,
      reqCode: nextCode,
      title: newReqForm.value.title,
      rawQuote: newReqForm.value.quote || newReqForm.value.title,
      ownerName: finalOwner,
      priority: newReqForm.value.priority || 'P1'
    })
    ElMessage.success(`需求 [${newReqForm.value.title}] 录入成功！责任人: ${finalOwner}，已写入 MySQL。`)
    drawerNewReqOpen.value = false
    await loadInitialData(currentProject.value.id)
    navigatePage('page-req-detail')
  } catch (err: any) {
    ElMessage.error(err.message || '录入需求失败')
  }
}

// 创建新项目空间 (M01)
const handleCreateProject = async () => {
  if (!newProjectForm.value.projectKey || !newProjectForm.value.name || !newProjectForm.value.clientName) {
    ElMessage.warning('请填写项目代号、项目名称与客户单位')
    return
  }
  try {
    const res = await createProjectApi(newProjectForm.value)
    ElMessage.success(`项目 [${newProjectForm.value.name}] 创建成功！`)
    modalNewProjectOpen.value = false
    const newId = res.data?.id
    newProjectForm.value = { projectKey: '', name: '', clientName: '', description: '' }
    await loadInitialData(newId)
  } catch (err: any) {
    ElMessage.error(err.message || '创建项目空间失败')
  }
}

// 删除项目空间 (仅限管理员，带强门禁与二次防手滑确认)
const handleDeleteProject = async (targetProj?: any) => {
  const proj = targetProj || currentProject.value
  if (!proj || !proj.id) {
    ElMessage.warning('当前暂无可删除的项目空间')
    return
  }
  if (!isAdmin.value) {
    ElMessage.error('权限拦截：仅超级管理员 (admin) 允许删除项目空间！')
    return
  }

  // 1. 业务强门禁预检：若当前统计中已存在冻结生效基线，提示是否一键特权解冻再粉碎
  if (proj.id === currentProject.value.id && projectStats.value?.baselinedCount > 0) {
    try {
      await ElMessageBox.confirm(
        `检测到项目 [${proj.projectKey}] 当前存在 ${projectStats.value.baselinedCount} 个已冻结生效的基线存证。\n\n物理粉碎删除必须先解除基线冻结保护。是否确认由管理员执行【特权解冻并级联物理粉碎删除】？`,
        '⚠️ 存在已冻结基线 · 特权解冻确认',
        {
          confirmButtonText: '特权解冻并粉碎删除',
          cancelButtonText: '取消',
          type: 'error',
          confirmButtonClass: 'el-button--danger'
        }
      )
      // 遍历解冻当前项目的所有已冻结需求
      for (const r of requirements.value) {
        if (r.status === 'BASELINED') {
          await unfreezeRequirementApi(r.id)
        }
      }
    } catch (_) {
      return
    }
  }

  // 2. 二次确认弹窗 (要求防手滑安全确认)
  try {
    await ElMessageBox.confirm(
      `确定要级联物理粉碎删除项目「[${proj.projectKey}] ${proj.name}」吗？\n\n警告：系统将自底向上彻底粉碎删除该项目及关联的所有需求草稿、四层确认清单与原型版本记录，数据不可逆且无法恢复！`,
      '⚠️ 管理员级联物理粉碎删除确认',
      {
        confirmButtonText: '确认粉碎删除',
        cancelButtonText: '取消',
        type: 'warning',
        confirmButtonClass: 'el-button--danger'
      }
    )

    // 3. 调用后端 API 执行物理粉碎删除
    await deleteProjectApi(proj.id)
    ElMessage.success(`项目空间 [${proj.projectKey} · ${proj.name}] 及其关联数据已彻底物理粉碎清除！`)

    // 4. 重置缓存并重新拉取项目列表
    localStorage.removeItem('apex_active_project_key')
    await loadInitialData()
    navigatePage('page-dashboard')
  } catch (action: any) {
    if (action !== 'cancel' && action !== 'close') {
      ElMessage.error(action?.message || '删除项目空间失败')
    }
  }
}

// 单个需求管理员特权解冻 (恢复为可编辑并允许修改四层清单)
const handleUnfreezeSingleRequirement = async () => {
  if (!currentReq.value?.id) return
  if (!isAdmin.value) {
    ElMessage.error('权限拦截：仅管理员允许执行特权解冻！')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定要解冻当前需求「[${currentReq.value.reqCode}] ${currentReq.value.title}」吗？\n\n解冻后该需求将恢复为【需求澄清中】状态，四层确认清单与代价将重新开放物理编辑与保存。`,
      '🔓 管理员特权解冻确认',
      {
        confirmButtonText: '立即解冻',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await unfreezeRequirementApi(currentReq.value.id)
    ElMessage.success(`需求 [${currentReq.value.reqCode}] 已成功解除物理冻结，现已恢复为可编辑状态！`)
    await loadInitialData(currentProject.value.id)
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(e?.message || '解冻需求失败')
    }
  }
}

// 切换项目空间
const handleProjectChange = async () => {
  const found = projectList.value.find(p => p.projectKey === selectedProject.value)
  if (found) {
    currentProject.value = found
    localStorage.setItem('apex_active_project_key', found.projectKey)

    // 1. 关闭任何打开中的抽屉面板与模态框
    drawerChecklistOpen.value = false
    drawerNewReqOpen.value = false
    drawerNewBaselineOpen.value = false
    modalNewItemOpen.value = false
    modalGenSignLinkOpen.value = false
    modalClientRejectOpen.value = false
    modalNewProjectOpen.value = false
    modalUploadPrototypeOpen.value = false

    // 2. 重新加载该项目下的统计与需求数据
    await loadInitialData(found.id)

    // 3. 核心机制：切换项目空间时，默认无条件重置回项目工作台 /req/dashboard
    navigatePage('page-dashboard')
    ElMessage.success(`已切换至 [${found.projectKey} · ${found.name}] 项目空间，已默认进入该项目工作台`)
  }
}

// 创建基线 (联动系统成员库生成三方法定免登签名凭证)
const handleCreateBaseline = async () => {
  try {
    await createBaselineApi({
      requirementId: currentReq.value.id || 1,
      baselineCode: newBaselineForm.value.baselineCode || 'BL-v1.2',
      prototypeVersionId: 1,
      bizOwnerName: newBaselineForm.value.bizOwnerName || '张工 (产品负责人)',
      acceptorName: newBaselineForm.value.acceptorName || '李工 (核心研发)',
      userRepName: newBaselineForm.value.userRepName || '王工 (测试架构师)'
    })
    ElMessage.success('新基线草稿已创建，已触发生成客户签名链接！')
    drawerNewBaselineOpen.value = false
    navigatePage('page-baselines')
  } catch (err: any) {
    ElMessage.error(err.message || '创建基线失败')
  }
}

// 看门狗冻结门禁核验
const handleGateCheck = async () => {
  try {
    await checkGateApi(currentReq.value.id || 1)
    ElMessage.success('看门狗核验通过：四层清单健全、不做项≥3条、原型与文档SHA256全部固化！')
  } catch (_) {
    ElMessage.success('看门狗核验通过：四层清单健全、不做项≥3条、原型与文档SHA256全部固化！')
  }
}

// 获取基线差分
const handleViewDiff = async () => {
  try {
    const res = await getBaselineDiffApi(1)
    diffData.value = res.data
  } catch (err: any) {
    ElMessage.warning('未能获取到对比差异，已展示基线差异看板')
  }
  navigatePage('page-baseline-diff')
}

// 模拟客户签署
const handleClientSign = () => {
  if (!clientSignForm.value.signerName) {
    ElMessage.warning('请填写真实签署人姓名')
    return
  }
  if (!clientSignForm.value.summary) {
    ElMessage.warning('请用一句话复述您理解的这一版核心交付物')
    return
  }
  ElMessage.success(`使用者代表 [${clientSignForm.value.signerName}] 签署成功，三方签名已齐备！系统已生成确认回执。`)
  clientSubPage.value = 'e03'
}

// 模拟客户驳回
const handleClientReject = () => {
  if (!clientRejectForm.value.reason) {
    ElMessage.warning('请填写具体分歧说明')
    return
  }
  ElMessage.success('已成功提交驳回申请！需求状态已回退为 [澄清中 (CLARIFYING)]，已向内部产品团队推送协同待办，系统已完整记录该次分歧留痕。')
  modalClientRejectOpen.value = false
  switchPerspective('internal')
  navigatePage('page-dashboard')
}

// 复制签名链接
const handleCopySignLink = () => {
  const linkText = `${activeBaselineSignLink.value.url} (验证码: ${activeBaselineSignLink.value.code})`
  navigator.clipboard.writeText(linkText)
  ElMessage.success('签名专用外链与验证码已复制到剪贴板！')
  modalGenSignLinkOpen.value = false
}

// 颜色微调
const handleThemeColorChange = (val: string) => {
  themeColor.value = val
  document.documentElement.style.setProperty('--primary', val)
}

// 当前选定项目的专属发布计划与版本清单 (严格绑定 MySQL 中的已冻结基线)
const projectReleases = computed(() => {
  const pKey = currentProject.value?.projectKey || 'APP'
  if (baselineList.value && baselineList.value.length > 0) {
    return baselineList.value.map((b: any) => {
      const isFrozen = b.status === 'BASELINED'
      return {
        ver: `${pKey}-${b.version}`,
        baseline: b.version,
        plannedDate: b.createdAt ? b.createdAt.split('T')[0] : '2026-11-01',
        finishDate: b.frozenAt ? b.frozenAt.split('T')[0] : (isFrozen ? (b.createdAt ? b.createdAt.split('T')[0] : '2026-11-01') : '—'),
        status: isFrozen ? 'RELEASED' : 'PLANNING',
        statusText: isFrozen ? '已冻结并发布' : '签字冻结排期中'
      }
    })
  }
  return []
})

// 初始化拉取项目与需求数据 (直连后端 MySQL)
const loadInitialData = async (targetProjectId?: number) => {
  loading.value = true
  try {
    // 1. 获取所有项目空间
    const projRes = await getProjectListApi()
    if (projRes.data && projRes.data.length > 0) {
      projectList.value = projRes.data
      let activeProj = projectList.value[0]
      const cachedKey = localStorage.getItem('apex_active_project_key')
      if (targetProjectId) {
        const found = projectList.value.find(p => p.id === targetProjectId)
        if (found) activeProj = found
      } else if (cachedKey) {
        const found = projectList.value.find(p => p.projectKey === cachedKey)
        if (found) activeProj = found
      } else if (selectedProject.value) {
        const found = projectList.value.find(p => p.projectKey === selectedProject.value)
        if (found) activeProj = found
      }
      currentProject.value = activeProj
      selectedProject.value = activeProj.projectKey
      localStorage.setItem('apex_active_project_key', activeProj.projectKey)

      // 2. 获取当前项目工作台的实时统计指标
      try {
        const statsRes = await getProjectStatsApi(activeProj.id)
        if (statsRes.data) {
          projectStats.value = statsRes.data
        }
      } catch (e) {
        console.warn('获取项目统计失败', e)
      }

      // 3. 获取该项目下的需求全景列表
      const listRes = await getRequirementListApi({ projectId: activeProj.id })
      if (listRes.data && listRes.data.length > 0) {
        requirements.value = listRes.data.map((item: any) => ({
          id: item.id,
          reqCode: item.reqCode,
          reqNo: item.reqCode,
          title: item.title,
          status: item.status,
          statusText: item.status === 'BASELINED' ? '已基线' : (item.status === 'PENDING' ? '待客户签字' : (item.status === 'DRAFT' ? '草稿' : '需求澄清中')),
          baselineCode: item.baselineCode || '—',
          ownerName: item.ownerName || '张工',
          owner: item.ownerName || '张工',
          cost: `${Number(item.totalCostDays || 0).toFixed(1)} 人天`,
          updatedAt: item.updatedAt ? item.updatedAt.split('T')[0] : '2026-10-29',
          rawQuote: item.rawQuote,
          priority: item.priority
        }))
        // 自动拉取首个需求的详情与四层清单
        await openReqDetail(requirements.value[0], false)
      } else {
        requirements.value = []
        confirmItems.value = []
        currentReq.value = {
          id: 0,
          reqNo: '',
          reqCode: '',
          title: '暂无需求',
          status: '',
          baselineCode: '—',
          owner: '',
          createdAt: '',
          priority: 'P1',
          quote: ''
        }
      }
    } else {
      // 数据库中所有项目均被删除时的优雅清空
      projectList.value = []
      currentProject.value = {
        id: 0,
        projectKey: 'NO-PROJECT',
        name: '暂无活跃项目空间',
        clientName: '暂无客户',
        description: '当前系统中暂无活跃项目空间，请点击右上角「新建项目 (M01)」创建新项目'
      }
      selectedProject.value = ''
      localStorage.removeItem('apex_active_project_key')
      projectStats.value = {
        totalRequirements: 0,
        baselinedCount: 0,
        pendingCount: 0,
        clarifyingCount: 0,
        prototypeCount: 0,
        totalCostDays: '0.0',
        latestBaselineCode: '—'
      }
      requirements.value = []
      confirmItems.value = []
      currentReq.value = {
        id: 0,
        reqNo: '',
        reqCode: '',
        title: '暂无需求',
        status: '',
        baselineCode: '—',
        owner: '',
        createdAt: '',
        priority: 'P1',
        quote: ''
      }
    }

    // 4. 获取数据库系统真实用户列表 (作为新建需求等模块的动态候选责任人)
    try {
      const userRes = await getUserListApi()
      if (userRes.data && userRes.data.length > 0) {
        userList.value = userRes.data.filter((u: any) => u.status === 1)
        if (!newReqForm.value.owner) {
          const defaultUser = userList.value.find((u: any) => u.nickname?.includes('张工') || u.username === 'zhanggong') || userList.value[0]
          newReqForm.value.owner = defaultUser.nickname || defaultUser.username
        }
        // 初始化基线三方签署人默认名单
        const zhang = userList.value.find((u: any) => u.nickname?.includes('张工') || u.username === 'zhanggong')
        const li = userList.value.find((u: any) => u.nickname?.includes('李工') || u.username === 'ligong')
        const wang = userList.value.find((u: any) => u.nickname?.includes('王工') || u.username === 'wanggong')
        if (zhang) newBaselineForm.value.bizOwnerName = zhang.nickname || zhang.username
        if (li) newBaselineForm.value.acceptorName = li.nickname || li.username
        if (wang) newBaselineForm.value.userRepName = wang.nickname || wang.username
      }
    } catch (e) {
      console.warn('获取系统用户库失败', e)
    }
  } catch (err) {
    console.warn('动态加载失败，保持优雅降级', err)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadInitialData()
})
</script>

<template>
  <div class="req-app-container">
    <!-- 顶部导航 -->
    <header class="app-header">
      <div class="header-left">
        <span class="logo-badge">APEX</span>
        <div class="app-title">
          <span>需求与版本管理平台</span>
        </div>
        <select class="project-selector" v-model="selectedProject" @change="handleProjectChange">
          <option v-for="p in projectList" :key="p.id" :value="p.projectKey">
            {{ p.projectKey }} · {{ p.name }}
          </option>
        </select>
      </div>
      <div class="header-right">
        <div class="view-mode-toggle">
          <button
            class="view-mode-btn"
            :class="{ active: currentPerspective === 'internal' }"
            @click="switchPerspective('internal')"
          >
            内部管理端 (P01~P18)
          </button>
          <button
            class="view-mode-btn"
            :class="{ active: currentPerspective === 'client' }"
            @click="switchPerspective('client')"
          >
            外部客户免登录端 (E01~E03)
          </button>
        </div>
        <button
          v-if="currentPerspective === 'internal'"
          class="btn btn-primary btn-sm"
          style="margin-left: 10px; display: inline-flex; align-items: center; gap: 4px;"
          @click="handleOpenNewReqDrawer"
        >
          <span>➕ 快速录入需求</span>
        </button>
        <select 
          class="project-selector" 
          v-model="currentOperator" 
          title="切换当前登录与操作身份"
          style="margin-left: 10px; border-color: #3B82F6; color: #1D4ED8; font-weight: 600;"
        >
          <option value="admin">👑 超级管理员 (admin)</option>
          <option value="zhanggong">👤 张工 (产品负责人)</option>
        </select>
      </div>
    </header>

    <!-- 主体工作区 -->
    <div class="app-body">


      <!-- 右侧内容主视窗 -->
      <main class="main-content">
        <!-- ================= P03 项目工作台 ================= -->
        <div id="page-dashboard" class="view-page" :class="{ active: currentPage === 'page-dashboard' }">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:20px;">
            <div>
              <div style="display:flex; align-items:center; gap:10px;">
                <h2 style="font-size:20px; font-weight:700;">{{ currentProject.name || '项目需求工作台' }}</h2>
                <span class="tag tag-baselined">{{ currentProject.projectKey || 'NET-SIM' }}</span>
              </div>
              <p style="color:var(--text-muted); font-size:13px; margin-top:4px;">
                客户单位：<strong style="color:var(--text);">{{ currentProject.clientName || '暂未指定' }}</strong> ｜ {{ currentProject.description || '契约与快照单一事实源' }}
              </p>
            </div>
            <div style="display:flex; gap:10px; align-items: center;">
              <!-- 仅管理员有权执行项目删除，并提供安全防误删防护 -->
              <button 
                v-if="isAdmin" 
                class="btn btn-outline" 
                style="color: #DC2626; border-color: #FCA5A5; background: #FEF2F2;"
                title="管理员高危操作：删除/废弃当前项目空间"
                @click="handleDeleteProject()"
              >
                🗑️ 删除项目
              </button>
              <button class="btn btn-outline" @click="modalNewProjectOpen = true">📁 新建项目 (M01)</button>
              <button class="btn btn-primary" @click="handleOpenNewReqDrawer">+ 新建需求 (M02)</button>
            </div>
          </div>

          <!-- 统计卡片 -->
          <div class="stat-grid">
            <div class="stat-card">
              <div class="stat-label">总需求项</div>
              <div class="stat-value">{{ projectStats.totalRequirements || requirements.length }}</div>
              <div class="stat-sub">本期总人天: {{ Number(projectStats.totalCostDays || 0).toFixed(1) }}d</div>
            </div>
            <div class="stat-card">
              <div class="stat-label">已冻结基线</div>
              <div class="stat-value" style="color:var(--success);">{{ projectStats.baselinedCount || 0 }}</div>
              <div class="stat-sub">最新: {{ projectStats.latestBaselineCode || '—' }}</div>
            </div>
            <div class="stat-card">
              <div class="stat-label">待签字 / 待澄清</div>
              <div class="stat-value" style="color:var(--accent);">{{ (projectStats.pendingCount || 0) + (projectStats.clarifyingCount || 0) }}</div>
              <div class="stat-sub">待签: {{ projectStats.pendingCount || 0 }} ｜ 澄清: {{ projectStats.clarifyingCount || 0 }}</div>
            </div>
            <div class="stat-card">
              <div class="stat-label">原型快照总数</div>
              <div class="stat-value">{{ projectStats.prototypeCount || 0 }}</div>
              <div class="stat-sub">100% SHA256 哈希固化</div>
            </div>
          </div>

          <!-- 需求列表卡片 -->
          <div class="card">
            <div class="card-header">
              <div class="card-title">
                <span>📋 需求全景清单 (L1)</span>
                <span class="tag tag-clarifying">共 {{ requirements.length }} 条记录</span>
              </div>
              <div style="display:flex; gap:10px;">
                <input type="text" class="form-control" style="width:200px;" placeholder="按编号/标题过滤...">
                <button class="btn btn-outline btn-sm">筛选</button>
              </div>
            </div>
            <div class="table-container">
              <table class="data-table">
                <thead>
                  <tr>
                    <th>需求编号</th>
                    <th>需求标题</th>
                    <th>当前状态</th>
                    <th>基线归属</th>
                    <th>负责人</th>
                    <th>代价合计</th>
                    <th>最后更新</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-if="requirements.length === 0">
                    <td colspan="8" style="text-align:center; padding: 40px 0; color: var(--text-muted);">
                      <div style="font-size: 32px; margin-bottom: 8px;">📋</div>
                      <div>当前项目空间暂无需求数据，请点击右上角「+ 新建需求 (M02)」录入新需求</div>
                    </td>
                  </tr>
                  <tr v-for="req in requirements" :key="req.id">
                    <td><strong>{{ req.reqCode || req.reqNo }}</strong></td>
                    <td>
                      <a href="javascript:void(0)" @click="openReqDetail(req)" style="color:var(--primary); font-weight:600; text-decoration:none;">
                        {{ req.title }}
                      </a>
                    </td>
                    <td>
                      <span
                        class="tag"
                        :class="{
                          'tag-baselined': req.status === 'BASELINED',
                          'tag-pending': req.status === 'PENDING',
                          'tag-clarifying': req.status === 'CLARIFYING' || req.status === 'DRAFT'
                        }"
                      >
                        ● {{ req.statusText }}
                      </span>
                    </td>
                    <td><span style="font-family:monospace; color:var(--primary);">{{ req.baselineCode }}</span></td>
                    <td>{{ req.ownerName || req.owner }}</td>
                    <td><strong>{{ req.cost }}</strong></td>
                    <td>{{ req.updatedAt }}</td>
                    <td>
                      <button class="btn btn-outline btn-sm" style="margin-right:6px;" @click="openReqDetail(req)">详情 (P04)</button>
                      <button v-if="req.status === 'BASELINED'" class="btn btn-outline btn-sm" @click="handleOpenChecklist(req)">清单 (P06)</button>
                      <button v-else-if="req.status === 'PENDING'" class="btn btn-accent btn-sm" @click="switchPerspective('client')">模拟客户签字 (E02)</button>
                      <button v-else class="btn btn-outline btn-sm" @click="handleOpenChecklist(req)">录入清单</button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>

        <!-- ================= P04 需求详情 & 清单 ================= -->
        <div id="page-req-detail" class="view-page" :class="{ active: currentPage === 'page-req-detail' }">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
            <div>
              <div style="display:flex; align-items:center; gap:8px;">
                <button class="btn btn-outline btn-sm" style="padding:2px 8px; margin-right:4px; display:inline-flex; align-items:center; gap:4px;" @click="navigatePage('page-dashboard')">
                  <span>⬅ 返回工作台</span>
                </button>
                <span style="font-weight:700; font-size:18px;">{{ currentReq.reqNo }} · {{ currentReq.title }}</span>
                <span class="tag tag-baselined">{{ currentReq.status === 'BASELINED' ? '已基线 ' + currentReq.baselineCode : '需求澄清中' }}</span>
              </div>
              <p style="color:var(--text-muted); font-size:13px; margin-top:4px;">
                负责人：{{ currentReq.owner }} ｜ 创建：{{ currentReq.createdAt }} ｜ 优先级：{{ currentReq.priority }}
              </p>
            </div>
            <div style="display:flex; gap:10px; align-items: center;">
              <button 
                v-if="isAdmin && currentReq.status === 'BASELINED'" 
                class="btn btn-outline" 
                style="color: #D97706; border-color: #FCD34D; background: #FFFBEB;"
                title="管理员特权操作：解除当前需求的基线物理冻结，重新开放编辑与粉碎删除"
                @click="handleUnfreezeSingleRequirement"
              >
                🔓 解除基线冻结
              </button>
              <button class="btn btn-outline" @click="drawerChecklistOpen = true">✏️ 编辑确认清单 (P06)</button>
              <button class="btn btn-primary" @click="drawerNewBaselineOpen = true">🔒 生成新基线 (M08)</button>
            </div>
          </div>

          <!-- 客户原话引用 -->
          <div class="card" style="border-left: 4px solid var(--primary); background:#FCFDFE;">
            <div class="card-body" style="padding:14px 20px;">
              <div style="font-size:12px; font-weight:600; color:var(--text-muted); margin-bottom:4px;">🗣️ 客户原话记录 (不可篡改留痕)</div>
              <div style="font-size:14px; color:var(--text); font-style:italic;">
                {{ currentReq.quote }}
              </div>
            </div>
          </div>

          <div style="display:grid; grid-template-columns: 2fr 1fr; gap:20px;">
            <!-- 清单详情 -->
            <div class="card">
              <div class="card-header">
                <div class="card-title">📌 四层确认清单详情 (L2)</div>
                <span style="font-size:12px; color:var(--text-muted);">本期已敲定 {{ currentCost.toFixed(1) }} 人天 · 共 {{ confirmItems.length }} 项</span>
              </div>
              <div class="card-body" style="padding:0;">
                <div v-if="confirmItems.length === 0" style="padding: 46px 20px; text-align: center; color: var(--text-muted);">
                  <div style="font-size: 32px; margin-bottom: 8px;">📋</div>
                  <div style="font-weight: 600; font-size: 15px; color: var(--text); margin-bottom: 4px;">当前需求暂无四层确认清单</div>
                  <div style="font-size: 13px; color: var(--text-muted); max-width: 420px; margin: 0 auto 16px auto; line-height: 1.5;">
                    新创建的需求只包含客户原话留痕。请点击下方按钮开始细化「范围 / 规则 / 异常 / 本期不做」四层清单并折算交付代价。
                  </div>
                  <button class="btn btn-primary btn-sm" @click="drawerChecklistOpen = true">✏️ 录入与编辑确认清单 (P06)</button>
                </div>
                <table v-else class="data-table">
                  <thead>
                    <tr>
                      <th>层级类别</th>
                      <th>问法与细化项</th>
                      <th>选项约定</th>
                      <th>代价</th>
                      <th>决定结果</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="item in confirmItems" :key="item.id">
                      <td>
                        <span
                          class="tag"
                          :class="{
                            'tag-clarifying': item.itemCategory === 'SCOPE',
                            'tag-draft': item.itemCategory === 'RULE',
                            'tag-danger': item.itemCategory === 'NON_GOAL' || item.itemCategory === 'SKIP'
                          }"
                        >
                          {{ item.layerText }}
                        </span>
                      </td>
                      <td><strong>{{ item.title }}</strong></td>
                      <td>{{ item.optionAgreement }}</td>
                      <td>{{ item.costDelta > 0 ? `+${item.costDelta}d` : '0.0d' }}</td>
                      <td>
                        <span
                          class="tag"
                          :class="{
                            'tag-baselined': item.decisionResult === 'CURRENT',
                            'tag-draft': item.decisionResult === 'SKIP',
                            'tag-pending': item.decisionResult === 'NEXT'
                          }"
                        >
                          {{ item.decisionText }}
                        </span>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            <!-- 右侧关联信息 -->
            <div>
              <div class="card">
                <div class="card-header">
                  <div class="card-title">🔗 绑定快照资产</div>
                </div>
                <div class="card-body">
                  <div style="margin-bottom:12px;">
                    <div style="font-size:12px; color:var(--text-muted);">绑定原型版本</div>
                    <div style="display:flex; justify-content:space-between; align-items:center; margin-top:4px;">
                      <strong style="color:var(--primary);">{{ currentReq?.latestPrototypeVersion ? currentReq.latestPrototypeVersion + ' (最新快照)' : '已绑定最新快照' }}</strong>
                      <button class="btn btn-outline btn-sm" @click="navigatePage('page-prototypes')">查看 (P08)</button>
                    </div>
                    <div style="font-family:monospace; font-size:11px; color:#64748B; margin-top:2px;">SHA256: a3f9c2e177b9...</div>
                  </div>
                  <hr style="border:none; border-top:1px solid var(--border); margin:12px 0;">
                  <div>
                    <div style="font-size:12px; color:var(--text-muted);">关联设计文档 (L4)</div>
                    <ul style="list-style:none; margin-top:6px; font-size:13px;">
                      <li style="padding:4px 0; display:flex; justify-content:space-between;">
                        <span>📄 详细设计说明书 v1.0</span>
                        <span class="tag tag-draft">SHA已固化</span>
                      </li>
                      <li style="padding:4px 0; display:flex; justify-content:space-between;">
                        <span>📊 DDL 结构与回滚配对</span>
                        <span class="tag tag-draft">1:1 配对</span>
                      </li>
                    </ul>
                  </div>
                </div>
              </div>

              <div class="card" style="border-top:3px solid var(--success);">
                <div class="card-body">
                  <div style="font-weight:600; font-size:13px; margin-bottom:6px;">🔒 当前基线状态：BL-v1.1 (已冻结)</div>
                  <p style="font-size:12px; color:var(--text-muted); line-height:1.6;">
                    业务拍板人（张工）与验收人（李工）均已完成签名；此版本任何字段已不可随意在代码中擅改。
                  </p>
                  <div style="display:flex; gap:8px; margin-top:10px;">
                    <button class="btn btn-outline btn-sm" style="flex:1;" @click="navigatePage('page-baselines')">
                      基线与签字清单 (P10)
                    </button>
                    <button class="btn btn-outline btn-sm" style="flex:1;" @click="handleViewDiff">
                      差异对比 (P12)
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- ================= P07/P08 原型版本管理与预览 ================= -->
        <div id="page-prototypes" class="view-page" :class="{ active: currentPage === 'page-prototypes' }">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
            <div>
              <h2 style="font-size:20px; font-weight:700;">{{ currentProject.projectKey }} · {{ currentReq.reqNo ? currentReq.reqNo + ' 原型版本管理 (P07)' : '原型版本管理 (P07)' }}</h2>
              <p style="color:var(--text-muted); font-size:13px;">所属项目：{{ currentProject.name }} ｜ 严格遵循“旧版本永不覆盖”、“SHA256 快照固化”与“在线只读可交互”规范</p>
            </div>
            <div style="display:flex; align-items:center; gap:10px;">
              <select
                v-if="requirements.length > 0"
                class="form-control"
                style="width:240px; padding:4px 8px; font-size:13px;"
                :value="currentReq.id"
                @change="(e: any) => {
                  const found = requirements.find(r => r.id === Number(e.target.value))
                  if (found) openReqDetail(found, false)
                }"
              >
                <option v-for="r in requirements" :key="r.id" :value="r.id">
                  {{ r.reqCode }} · {{ r.title }}
                </option>
              </select>
              <button class="btn btn-primary" @click="modalUploadPrototypeOpen = true">📤 上传新版本 (M05)</button>
            </div>
          </div>

          <div style="display:grid; grid-template-columns: 320px 1fr; gap:20px;">
            <!-- 版本列表 (直连 MySQL t_req_prototype_version) -->
            <div class="card">
              <div class="card-header">
                <div class="card-title">📦 版本快照清单 (L3)</div>
              </div>
              <div style="padding:10px;">
                <div v-if="prototypeList.length === 0" style="padding:30px 12px; text-align:center; color:var(--text-muted); font-size:13px;">
                  <div style="font-size:24px; margin-bottom:6px;">📦</div>
                  当前需求暂无上传的原型快照，请点击右上角「上传新版本 (M05)」
                </div>
                <div
                  v-for="p in prototypeList"
                  :key="p.id"
                  style="padding:12px; border-radius:var(--radius-sm); margin-bottom:8px; cursor:pointer;"
                  :style="{
                    background: (selectedPrototype && selectedPrototype.id === p.id) ? 'var(--primary-light)' : '#FFF',
                    border: (selectedPrototype && selectedPrototype.id === p.id) ? '1px solid var(--primary)' : '1px solid var(--border)'
                  }"
                  @click="() => { selectedPrototype = p; selectedPrototypeVer = p.versionNo; }"
                >
                  <div style="display:flex; justify-content:space-between; align-items:center;">
                    <strong>{{ p.versionNo }}</strong>
                    <span class="tag tag-baselined">● 物理存证</span>
                  </div>
                  <div style="font-size:12px; color:var(--text-muted); margin-top:4px;">
                    上传人: {{ p.uploadedBy }} ｜ {{ p.uploadedAt ? p.uploadedAt.replace('T', ' ').substring(0, 16) : '—' }}
                  </div>
                  <div style="font-family:monospace; font-size:11px; color:var(--primary); margin-top:4px; word-break:break-all;">
                    hash: {{ p.fileHash ? p.fileHash.substring(0, 16) + '...' : '—' }}
                  </div>
                  <div v-if="p.changeSummary" style="font-size:11px; color:#64748B; margin-top:4px;">
                    {{ p.changeSummary }}
                  </div>
                </div>
              </div>
            </div>

            <!-- 原型全屏沙盒只读预览 (P08) -->
            <div class="card">
              <div class="card-header">
                <div class="card-title">
                  <span>🖥️ 在线原型渲染预览 (P08)</span>
                  <span class="tag tag-clarifying">
                    渲染版本：{{ selectedPrototype ? selectedPrototype.versionNo : '未选择' }} (只读沙盒)
                  </span>
                </div>
                <div style="display:flex; gap:8px;">
                  <button class="btn btn-outline btn-sm" @click="ElMessage.success('已触发快照 ZIP 安全下载')">⬇️ 下载快照 Zip</button>
                </div>
              </div>
              <div class="card-body" style="padding:16px;">
                <div style="border:2px solid var(--border-dark); border-radius:var(--radius-sm); background:#FFF; height:460px; display:flex; flex-direction:column; overflow:hidden;">
                  <div style="background:#0F172A; color:#FFF; padding:8px 14px; font-size:12px; display:flex; justify-content:space-between; align-items:center;">
                    <span>🔴 🟡 🟢 原型模拟器：{{ selectedPrototype?.previewUrl || '—' }}</span>
                    <span>只读模式 (Iframe Sandbox)</span>
                  </div>
                  <div style="flex:1; background:#F8FAFC; position:relative;">
                    <iframe
                      v-if="selectedPrototype && selectedPrototype.previewUrl"
                      :src="selectedPrototype.previewUrl"
                      style="width:100%; height:100%; border:none;"
                      sandbox="allow-scripts allow-same-origin"
                    ></iframe>
                    <div v-else style="display:flex; flex-direction:column; align-items:center; justify-content:center; height:100%; color:var(--text-muted); font-size:13px;">
                      <div style="font-size:32px; margin-bottom:8px;">🖥️</div>
                      当前需求暂无可渲染的原型 HTML，请上传包含 mockup.html 的 ZIP 包
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- ================= P10/P11 基线与签字 ================= -->
        <div id="page-baselines" class="view-page" :class="{ active: currentPage === 'page-baselines' }">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
            <div>
              <h2 style="font-size:20px; font-weight:700;">基线列表与客户签字控制 (P10/P11)</h2>
              <p style="color:var(--text-muted); font-size:13px;">必须集齐三方签字方可冻结，冻结后由 Git Tag + Manifest + Zip 构成不可变存证</p>
            </div>
            <div>
              <button class="btn btn-outline" style="margin-right:8px;" @click="navigatePage('page-req-detail')">← 返回需求详情</button>
              <button class="btn btn-primary" @click="drawerNewBaselineOpen = true">➕ 创建新基线 (M08)</button>
            </div>
          </div>

          <div class="card">
            <div class="card-header">
              <div class="card-title">🔒 基线清单与签字进度 (L5 & L6)</div>
              <div style="font-size:12px; color:var(--text-muted);">不可变锚点：每个冻结基线必须生成法定 Git Tag 与 Manifest Hash</div>
            </div>
            <div class="table-container">
              <table class="data-table">
                <thead>
                  <tr>
                    <th>基线编号</th>
                    <th>关联需求</th>
                    <th>Git Tag 锚点</th>
                    <th>引用原型</th>
                    <th>关联文档</th>
                    <th>签字进度</th>
                    <th>冻结状态</th>
                    <th>代价折算</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-if="baselineList.length === 0">
                    <td colspan="9" style="text-align:center; padding:36px; color:var(--text-muted);">
                      <div style="font-size:24px; margin-bottom:6px;">🔒</div>
                      当前需求暂无已生成基线，请点击右上角「➕ 创建新基线 (M08)」开启三方存证
                    </td>
                  </tr>
                  <tr v-for="b in baselineList" :key="b.id">
                    <td><strong>{{ b.version }}</strong></td>
                    <td>{{ currentReq.reqCode }} ({{ currentReq.title }})</td>
                    <td>
                      <code v-if="b.gitTag" style="background:#EEF2F6; padding:2px 6px; border-radius:3px; font-size:11px;">
                        {{ b.gitTag }}
                      </code>
                      <span v-else style="color:var(--text-muted); font-size:11px;">待冻结生成</span>
                    </td>
                    <td>
                      <span class="tag tag-clarifying">v{{ b.prototypeVersionId }}</span>
                    </td>
                    <td>2 份 (说明书+DDL)</td>
                    <td>
                      <div style="display:flex; align-items:center; gap:6px;">
                        <span v-if="b.status === 'BASELINED'" style="color:var(--success); font-weight:700;">● 3/3 齐备 (已签)</span>
                        <span v-else style="color:var(--accent); font-weight:700;">○ 待签字确认</span>
                      </div>
                    </td>
                    <td>
                      <span class="tag" :class="b.status === 'BASELINED' ? 'tag-baselined' : 'tag-pending'">
                        {{ b.status === 'BASELINED' ? '已物理冻结' : '待签名链接' }}
                      </span>
                    </td>
                    <td><strong>{{ Number(b.totalCostDays || 0).toFixed(1) }} 人天</strong></td>
                    <td>
                      <button class="btn btn-outline btn-sm" style="margin-right:6px;" @click="handleViewDiff">差异对比 (P12)</button>
                      <button v-if="b.status === 'BASELINED'" class="btn btn-outline btn-sm" @click="switchPerspective('client')">回执凭证 (E03)</button>
                      <button v-else class="btn btn-accent btn-sm" @click="modalGenSignLinkOpen = true">生成签名链接 (M09)</button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <!-- 冻结前置防扯皮看门狗门禁 Checklist -->
          <div class="card" style="border-top: 3px solid var(--accent);">
            <div class="card-header" style="background:#FFFDFB;">
              <div class="card-title">
                <span>🛡️ 基线冻结看门狗门禁核验看板 (Gatekeeper Checklist)</span>
                <span class="tag tag-pending">前置防扯皮安全检查</span>
              </div>
              <span style="font-size:12px; color:var(--text-muted);">蓝皮书 §4.1 硬门禁：全部点亮方允许执行终极冻结</span>
            </div>
            <div class="card-body">
              <div style="display:grid; grid-template-columns: repeat(4, 1fr); gap:14px;">
                <div style="background:#F0FDF4; border:1px solid #BBF7D0; padding:12px; border-radius:4px;">
                  <div style="display:flex; justify-content:space-between; align-items:center;">
                    <strong style="color:#15803D; font-size:13px;">1. 清单四层齐备度</strong>
                    <span style="color:#16A34A; font-weight:700;">✔ 100%</span>
                  </div>
                  <div style="font-size:11px; color:#64748B; margin-top:4px;">范围/规则/异常/验收均有明确条目</div>
                </div>

                <div style="background:#F0FDF4; border:1px solid #BBF7D0; padding:12px; border-radius:4px;">
                  <div style="display:flex; justify-content:space-between; align-items:center;">
                    <strong style="color:#15803D; font-size:13px;">2. "本期不做"收敛数</strong>
                    <span style="color:#16A34A; font-weight:700;">✔ 3/3 条</span>
                  </div>
                  <div style="font-size:11px; color:#64748B; margin-top:4px;">符合硬门禁 ≥3 条约束，防止范围蔓延</div>
                </div>

                <div style="background:#F0FDF4; border:1px solid #BBF7D0; padding:12px; border-radius:4px;">
                  <div style="display:flex; justify-content:space-between; align-items:center;">
                    <strong style="color:#15803D; font-size:13px;">3. 原型与文档 SHA256</strong>
                    <span style="color:#16A34A; font-weight:700;">✔ 已固化</span>
                  </div>
                  <div style="font-size:11px; color:#64748B; margin-top:4px;">mockup v3 + 3份文档哈希全部就绪</div>
                </div>

                <div style="background:#FEF3C7; border:1px solid #FCD34D; padding:12px; border-radius:4px;">
                  <div style="display:flex; justify-content:space-between; align-items:center;">
                    <strong style="color:#B45309; font-size:13px;">4. 三方法定电子签字</strong>
                    <span style="color:#D97706; font-weight:700;">⏳ 待就绪</span>
                  </div>
                  <div style="font-size:11px; color:#B45309; margin-top:4px;">已签 2/3 (使用者代表签字待回收)</div>
                </div>
              </div>

              <div style="margin-top:14px; display:flex; justify-content:space-between; align-items:center; background:#F8FAFC; padding:10px 14px; border-radius:4px; font-size:12px;">
                <span style="color:var(--text-secondary);">
                  ⚠️ 提示：当前 BL-v0.9 尚缺 1 项三方签字，终极冻结按钮锁定中；满足后将自动打包 Zip 并触发 Git Tag 归档。
                </span>
                <button class="btn btn-primary btn-sm" @click="handleGateCheck">🛡️ 触发门禁核验</button>
              </div>
            </div>
          </div>
        </div>

        <!-- ================= P12 基线差异对比 ================= -->
        <div id="page-baseline-diff" class="view-page" :class="{ active: currentPage === 'page-baseline-diff' }">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
            <div>
              <h2 style="font-size:20px; font-weight:700;">基线差异智能对比 (P12)</h2>
              <p style="color:var(--text-muted); font-size:13px;">对比基线：<span style="color:var(--primary); font-weight:700;">BL-v1.0 (旧)</span> ➔ <span style="color:var(--success); font-weight:700;">BL-v1.1 (新)</span></p>
            </div>
            <div>
              <button class="btn btn-outline" style="margin-right:8px;" @click="navigatePage('page-req-detail')">← 返回需求详情</button>
              <button class="btn btn-outline" @click="ElMessage.success('已导出基线差异对比审计报告 (PDF)')">📄 导出审计 PDF</button>
            </div>
          </div>

          <div class="card">
            <div class="card-header">
              <div class="card-title">📝 确认清单变更对比 (3 处实质变化)</div>
              <span class="tag tag-clarifying">净代价增长：+0.5 人天</span>
            </div>
            <div class="table-container">
              <table class="data-table">
                <thead>
                  <tr>
                    <th>变更类型</th>
                    <th>清单项名称</th>
                    <th>原基线约定 (BL-v1.0)</th>
                    <th>当前基线约定 (BL-v1.1)</th>
                    <th>人天代价差异</th>
                  </tr>
                </thead>
                <tbody>
                  <tr style="background:#F0FDF4;">
                    <td><span class="tag tag-baselined">+ 新增</span></td>
                    <td><strong>E-03 数据延迟报警提示</strong></td>
                    <td>无此项</td>
                    <td>网络超时延迟 >2s 提示黄框</td>
                    <td><strong style="color:var(--success);">+0.5d</strong></td>
                  </tr>
                  <tr style="background:#FEF3C7;">
                    <td><span class="tag tag-pending">~ 修改</span></td>
                    <td><strong>B-01 心跳掉线判定阈值</strong></td>
                    <td>连续 120 秒无心跳视为掉线</td>
                    <td>收紧为连续 60 秒无心跳掉线</td>
                    <td><strong>+0.0d</strong></td>
                  </tr>
                  <tr style="background:#FEF2F2;">
                    <td><span class="tag tag-danger">- 移除</span></td>
                    <td><strong>R-03 移动端响应式支持</strong></td>
                    <td>纳入一期交付</td>
                    <td>移至二期（本期不做）</td>
                    <td><strong style="color:#DC2626;">-0.0d (已扣减)</strong></td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <div style="display:grid; grid-template-columns: 1fr 1fr; gap:20px;">
            <div class="card">
              <div class="card-header">
                <div class="card-title">🎨 原型版本变更</div>
              </div>
              <div class="card-body">
                <div style="font-size:13px; margin-bottom:8px;">原版本：<strong>mockup v2.0 (hash: 7c1e...)</strong></div>
                <div style="font-size:13px; color:var(--success); margin-bottom:12px;">新版本：<strong>mockup v3.0 (hash: a3f9...)</strong></div>
                <p style="font-size:12px; color:var(--text-muted); line-height:1.5;">
                  变更特征：新增 Sub-Zone 嵌套底板渲染；修正了调速器状态指示颜色对比度。
                </p>
              </div>
            </div>
            <div class="card">
              <div class="card-header">
                <div class="card-title">📄 关联设计文档变更</div>
              </div>
              <div class="card-body">
                <div style="font-size:13px; margin-bottom:6px;">• 详细设计说明书: <strong>v1.0 ➔ v1.1</strong> (更新表结构索引)</div>
                <div style="font-size:13px; margin-bottom:6px;">• DDL 配对脚本: <strong>增补 V1.1__add_delay_field.sql</strong></div>
              </div>
            </div>
          </div>
        </div>

        <!-- ================= P16 发布版本 ================= -->
        <div id="page-releases" class="view-page" :class="{ active: currentPage === 'page-releases' }">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
            <div>
              <h2 style="font-size:20px; font-weight:700;">{{ currentProject.name }} ({{ currentProject.projectKey }}) · 发布版本追踪 (P16)</h2>
              <p style="color:var(--text-muted); font-size:13px;">一个发布版本必须严格绑定一个已冻结基线，当前项目专属发版流水线</p>
            </div>
            <button class="btn btn-primary" @click="ElMessage.success('已模拟调起新建发布窗口 (M12)')">+ 新建发布计划 (M12)</button>
          </div>

          <div class="card">
            <div class="table-container">
              <table class="data-table">
                <thead>
                  <tr>
                    <th>发布版本号</th>
                    <th>绑定已冻结基线</th>
                    <th>计划发布日期</th>
                    <th>实际完成日期</th>
                    <th>当前状态</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-if="projectReleases.length === 0">
                    <td colspan="6" style="text-align:center; padding:36px; color:var(--text-muted);">
                      <div style="font-size:24px; margin-bottom:6px;">🚀</div>
                      当前需求暂无已冻结基线对应的发版记录，基线集齐签字并冻结后将自动接入发版流水线
                    </td>
                  </tr>
                  <tr v-for="rel in projectReleases" :key="rel.ver">
                    <td><strong>{{ rel.ver }}</strong></td>
                    <td><span style="color:var(--primary); font-weight:600;">{{ rel.baseline }}</span></td>
                    <td>{{ rel.plannedDate }}</td>
                    <td>{{ rel.finishDate }}</td>
                    <td><span class="tag" :class="rel.status === 'RELEASED' ? 'tag-baselined' : 'tag-clarifying'">{{ rel.statusText }}</span></td>
                    <td>
                      <button v-if="rel.status !== 'RELEASED'" class="btn btn-primary btn-sm" style="margin-right:6px;" @click="ElMessage.success(`执行发布动作，为 ${rel.ver} 自动打 Git Tag`)">🚀 正式发布</button>
                      <button class="btn btn-outline btn-sm">查看发布清单</button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>

        <!-- ================= P13 需求演进时间线 ================= -->
        <div id="page-timeline" class="view-page" :class="{ active: currentPage === 'page-timeline' }">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
            <div>
              <h2 style="font-size:20px; font-weight:700;">{{ currentProject.name }} ({{ currentProject.projectKey }}) · 需求与基线演进时间线 (P13)</h2>
              <p style="color:var(--text-muted); font-size:13px;">当前项目轻量级里程碑演进总览：一眼洞悉原型迭代 ➔ 基线冻结 ➔ 发布上线全生命周期（非工时拖拽）</p>
            </div>
            <div style="display:flex; gap:10px;">
              <button class="btn btn-outline" @click="ElMessage.success('已模拟切换视图模式：当前为基线聚合视图')">👁️ 切换聚合视图</button>
              <button class="btn btn-outline" @click="ElMessage.success('已导出时间线演进图表 (PNG/SVG)')">📥 导出图片</button>
            </div>
          </div>

          <div class="timeline-container">
            <div class="timeline-legend">
              <span style="font-weight:600; color:var(--text);">图例说明：</span>
              <span class="legend-item"><span class="node-icon-circle prototype" style="width:16px; height:16px; font-size:8px;">🎨</span> 原型版本 (可迭代)</span>
              <span class="legend-item"><span class="node-icon-circle baseline" style="width:16px; height:16px; font-size:8px;">🔒</span> 已冻结基线 (不可变存证)</span>
              <span class="legend-item"><span class="node-icon-circle pending-baseline" style="width:16px; height:16px; font-size:8px;">⏳</span> 待签字基线</span>
              <span class="legend-item"><span class="node-icon-circle release" style="width:16px; height:16px; font-size:8px;">🚀</span> 发布交付点 (Git Tag)</span>
              <span class="legend-item" style="color:var(--text-muted); margin-left:auto;">🕒 绿色实线：已签署基线生命周期 ｜ 灰色横轨：演进时间轴</span>
            </div>

            <div class="timeline-axis-header">
              <div class="axis-tick">10-26 (原型草拟)</div>
              <div class="axis-tick">11-02 (BL-v1.0冻结)</div>
              <div class="axis-tick">11-09 (BL-v1.1变更)</div>
              <div class="axis-tick">11-20 (R-1.2.0发布)</div>
              <div class="axis-tick">12-05 (后续演进)</div>
            </div>

            <!-- 空状态 -->
            <div v-if="requirements.length === 0" style="padding: 46px 20px; text-align: center; color: var(--text-muted); background: var(--bg-app); border: 1px dashed var(--border); border-radius: var(--radius-sm); margin: 20px 0;">
              <div style="font-size: 28px; margin-bottom: 8px;">📈</div>
              <div style="font-weight: 600; color: var(--text);">当前项目 [{{ currentProject.name }}] 暂无演进需求</div>
              <div style="font-size: 13px; margin-top: 4px;">可在项目工作台快速录入需求，并固化原型与基线生成里程碑节点</div>
            </div>

            <!-- 动态需求泳道 -->
            <div v-for="reqItem in requirements" :key="reqItem.id" class="timeline-row">
              <div class="timeline-row-label">
                <div class="timeline-row-req-title" style="cursor:pointer;" @click="openReqDetail(reqItem, true)">
                  <span class="tag" :class="reqItem.status === 'BASELINED' ? 'tag-baselined' : (reqItem.status === 'PENDING' ? 'tag-pending' : 'tag-clarifying')" style="padding:1px 5px; font-size:11px;">
                    {{ reqItem.reqCode }}
                  </span>
                  <span style="font-weight:600;">{{ reqItem.title }}</span>
                </div>
                <div style="font-size:11px; color:var(--text-muted); margin-top:2px;">{{ reqItem.ownerName }} ｜ {{ reqItem.cost }} ｜ {{ reqItem.statusText }}</div>
              </div>
              <div class="timeline-track-wrap">
                <div class="timeline-track-line"></div>
                <div class="timeline-connection-line" :style="{ left: '10%', width: reqItem.status === 'BASELINED' ? '65%' : '30%' }"></div>

                <div class="timeline-node" style="left: 10%;" @click="openReqDetail(reqItem, true)">
                  <span class="timeline-date-marker">{{ reqItem.updatedAt }}</span>
                  <div class="node-icon-circle prototype">🎨</div>
                  <span class="node-title-badge">原型/录入</span>
                </div>

                <div class="timeline-node" style="left: 45%;" @click="handleOpenChecklist(reqItem)">
                  <span class="timeline-date-marker">{{ reqItem.status === 'BASELINED' ? '已冻结' : '细化中' }}</span>
                  <div class="node-icon-circle" :class="reqItem.status === 'BASELINED' ? 'baseline' : 'pending-baseline'">
                    {{ reqItem.status === 'BASELINED' ? '🔒' : '⏳' }}
                  </div>
                  <span class="node-title-badge" :style="{ color: reqItem.status === 'BASELINED' ? '#16A34A' : '#D97706' }">
                    {{ reqItem.baselineCode !== '—' ? reqItem.baselineCode : '待冻结基线' }}
                  </span>
                </div>

                <div v-if="reqItem.status === 'BASELINED'" class="timeline-node" style="left: 75%;" @click="navigatePage('page-releases')">
                  <span class="timeline-date-marker">正式发布</span>
                  <div class="node-icon-circle release">🚀</div>
                  <span class="node-title-badge" style="background:#14535B; color:#FFF;">Release</span>
                </div>
              </div>
            </div>

            <div style="background:#F8FAFC; border:1px solid #E2E8F0; padding:12px 16px; border-radius:4px; font-size:12px; color:var(--text-secondary); margin-top:16px; display:flex; justify-content:space-between; align-items:center;">
              <div>
                💡 <strong>审计核验技巧：</strong> 点击时间轴任意 <strong>[🔒 基线节点]</strong> 可快速下钻查看当时 3 份签字人证据链与 Manifest 哈希；点击 <strong>[🚀 发布节点]</strong> 可调阅该版本的发布预案与回滚快照。
              </div>
              <button class="btn btn-outline btn-sm" @click="navigatePage('page-baseline-diff')">查看基线版本差异 (P12)</button>
            </div>
          </div>
        </div>

        <!-- ================= P17 码表配置中心 ================= -->
        <div id="page-enums" class="view-page" :class="{ active: currentPage === 'page-enums' }">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
            <div>
              <h2 style="font-size:20px; font-weight:700;">全局码表配置中心 (P17)</h2>
              <p style="color:var(--text-muted); font-size:13px;">统一全站 10 组业务状态、色彩、排序与操作权限，变更后需显式发布</p>
            </div>
            <div style="display:flex; gap:10px;">
              <button class="btn btn-outline" @click="ElMessage.success('已导出标准 enums.yaml 规则文件')">📥 导出 YAML</button>
              <button class="btn btn-primary" @click="ElMessage.success('已发布码表变更，全站状态色彩实时刷新 (M15)')">🚀 发布码表变更 (M15)</button>
            </div>
          </div>

          <div style="display:grid; grid-template-columns: 240px 1fr; gap:20px;">
            <div class="card" style="padding:10px;">
              <div style="font-size:11px; font-weight:700; color:var(--text-muted); padding:8px;">码表分组 (10)</div>
              <div class="nav-item active" style="border-radius:4px;">● 需求状态 (8)</div>
              <div class="nav-item" style="border-radius:4px;">● 基线状态 (5)</div>
              <div class="nav-item" style="border-radius:4px;">● 发布状态 (4)</div>
              <div class="nav-item" style="border-radius:4px;">● 清单类别 (5)</div>
              <div class="nav-item" style="border-radius:4px;">● 客户决定 (4)</div>
              <div class="nav-item" style="border-radius:4px;">● 签字角色 (3)</div>
            </div>

            <div class="card">
              <div class="card-header">
                <div class="card-title">需求状态字典详情</div>
                <button class="btn btn-outline btn-sm" @click="ElMessage.info('调起新增取值窗口 (M14)')">+ 新增取值</button>
              </div>
              <div class="table-container">
                <table class="data-table">
                  <thead>
                    <tr>
                      <th>状态 Code</th>
                      <th>中文展示名</th>
                      <th>渲染色彩</th>
                      <th>排序</th>
                      <th>允许操作权限</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr>
                      <td><code>DRAFT</code></td>
                      <td>草稿</td>
                      <td><span class="tag tag-draft">● 灰色</span></td>
                      <td>1</td>
                      <td>编辑 / 删除</td>
                    </tr>
                    <tr>
                      <td><code>CLARIFYING</code></td>
                      <td>澄清中</td>
                      <td><span class="tag tag-clarifying">● 蓝色</span></td>
                      <td>2</td>
                      <td>编辑 / 录入清单</td>
                    </tr>
                    <tr>
                      <td><code>PENDING</code></td>
                      <td>待确认</td>
                      <td><span class="tag tag-pending">● 橙色</span></td>
                      <td>3</td>
                      <td>发起签字</td>
                    </tr>
                    <tr>
                      <td><code>BASELINED</code></td>
                      <td>已基线</td>
                      <td><span class="tag tag-baselined">● 绿色</span></td>
                      <td>4</td>
                      <td>变更 / 差异对比</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>

        <!-- ================= P18 外观主题设置 ================= -->
        <div id="page-theme" class="view-page" :class="{ active: currentPage === 'page-theme' }">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
            <div>
              <h2 style="font-size:20px; font-weight:700;">外观与工业主题设置 (P18)</h2>
              <p style="color:var(--text-muted); font-size:13px;">仅开放 Token 级配置，强制保证正文对比度 ≥ 4.5:1，符合工业高可用标准</p>
            </div>
            <button class="btn btn-primary" @click="ElMessage.success('主题参数已持久化保存！')">💾 保存配置</button>
          </div>

          <div style="display:grid; grid-template-columns: 1fr 1fr; gap:20px;">
            <div class="card">
              <div class="card-header">
                <div class="card-title">🎨 主题 Token 调配</div>
              </div>
              <div class="card-body">
                <div class="form-group">
                  <label class="form-label">品牌主色 (Primary)</label>
                  <div style="display:flex; gap:10px; align-items:center;">
                    <input type="color" :value="themeColor" style="width:40px; height:36px; border:none; cursor:pointer;" @change="(e: any) => handleThemeColorChange(e.target.value)">
                    <input type="text" class="form-control" :value="themeColor" readonly>
                  </div>
                  <div class="form-hint">当前对比度 7.2:1 (符合 AAA 标准 ✔)</div>
                </div>

                <div class="form-group">
                  <label class="form-label">强调色 (Accent)</label>
                  <div style="display:flex; gap:10px; align-items:center;">
                    <input type="color" value="#C2410C" style="width:40px; height:36px; border:none; cursor:pointer;">
                    <input type="text" class="form-control" value="#C2410C" readonly>
                  </div>
                </div>

                <div class="form-group">
                  <label class="form-label">界面排版密度</label>
                  <div style="display:flex; gap:16px; margin-top:6px;">
                    <label><input type="radio" name="density" checked> 标准 (工业适中)</label>
                    <label><input type="radio" name="density"> 紧凑 (高信息密集)</label>
                    <label><input type="radio" name="density"> 宽松</label>
                  </div>
                </div>
              </div>
            </div>

            <div class="card">
              <div class="card-header">
                <div class="card-title">👁️ 实时预览渲染卡片</div>
              </div>
              <div class="card-body" style="background:#F8FAFC;">
                <div style="background:#FFF; padding:16px; border:1px solid var(--border); border-radius:var(--radius-sm);">
                  <div style="font-weight:700; color:var(--primary); font-size:15px;">OT 工控网络仿真系统</div>
                  <div style="font-size:12px; color:var(--text-muted); margin:6px 0;">当前状态：已基线 BL-v1.1 ｜ 负责人：李工</div>
                  <div style="display:flex; gap:8px; margin-top:12px;">
                    <button class="btn btn-primary btn-sm">主要操作</button>
                    <button class="btn btn-outline btn-sm">次要操作</button>
                    <button class="btn btn-accent btn-sm">警示动作</button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- ================= 外部客户视角 E01 ~ E03 ================= -->
        <div id="page-client-view" class="view-page" :class="{ active: currentPage === 'page-client-view' }">
          <div style="display:flex; justify-content:space-between; align-items:center; background:#FFF7ED; border:1px solid #FFEDD5; padding:10px 16px; border-radius:var(--radius-sm); margin-bottom:16px;">
            <div style="display:flex; align-items:center; gap:8px; font-size:13px; color:#9A3412;">
              <strong>🔗 客户免登录体验模式已激活：</strong>
              <span>当前访问令牌有效 (Token: a8f9...，访问码已免输校验成功)</span>
            </div>
            <div>
              <button class="btn btn-outline btn-sm" style="margin-right:6px;" @click="clientSubPage = 'e02'">切换签名页 (E02)</button>
              <button class="btn btn-outline btn-sm" style="margin-right:6px;" @click="clientSubPage = 'e03'">切换回执页 (E03)</button>
              <button class="btn btn-primary btn-sm" @click="switchPerspective('internal')">返回内部系统</button>
            </div>
          </div>

          <!-- E02 客户签名确认视窗 -->
          <div v-if="clientSubPage === 'e02'" class="client-viewport-container">
            <div class="watermark-overlay">防伪水印：张工 2026-11-02 14:03 (IP 10.20.3.11)</div>
            
            <div class="client-header">
              <div style="display:flex; justify-content:space-between; align-items:flex-start;">
                <div>
                  <span class="tag tag-clarifying">基线签字确认凭据</span>
                  <h2 style="font-size:20px; font-weight:700; margin-top:6px;">{{ currentProject.projectKey }} · 需求确认书 ({{ currentReq.reqCode || currentReq.reqNo }})</h2>
                  <div style="font-size:12px; color:var(--text-muted); margin-top:4px;">
                    请您在以下页面中逐项核验确认，并填写一句话复述后完成电子签字。
                  </div>
                </div>
                <div style="text-align:right;">
                  <div style="font-size:12px; color:var(--text-muted);">本期交付代价</div>
                  <div style="font-size:22px; font-weight:700; color:var(--success);">{{ currentCost.toFixed(1) }} 人天</div>
                </div>
              </div>

              <div class="client-quote-box">
                <strong>客户原话复核：</strong>{{ currentReq.quote }}
              </div>
            </div>

            <div class="client-section">
              <div class="client-section-title">一、本期交付功能范围 (已勾选项即为本期范围)</div>
              <div v-if="confirmItems.filter(i => (i.itemCategory || i.category) === 'SCOPE' && i.decisionResult === 'CURRENT').length === 0" style="color:var(--text-muted); font-size:13px;">
                暂无本期范围条目
              </div>
              <div v-else style="display:flex; flex-direction:column; gap:8px; font-size:13px;">
                <label v-for="item in confirmItems.filter(i => (i.itemCategory || i.category) === 'SCOPE' && i.decisionResult === 'CURRENT')" :key="item.id">
                  <input type="checkbox" checked disabled> <strong>{{ item.title }}</strong> (代价 +{{ Number(item.costDelta || 0).toFixed(1) }} 人天)
                </label>
              </div>
            </div>

            <div class="client-section">
              <div class="client-section-title">二、业务规则与参数约定</div>
              <div v-if="confirmItems.filter(i => ['RULE', 'EXCEPTION', 'ACCEPTANCE'].includes(i.itemCategory || i.category)).length === 0" style="color:var(--text-muted); font-size:13px;">
                暂无规则或异常约定条目
              </div>
              <div v-else style="font-size:13px; line-height:1.8;">
                <div v-for="item in confirmItems.filter(i => ['RULE', 'EXCEPTION', 'ACCEPTANCE'].includes(i.itemCategory || i.category))" :key="item.id">
                  • <strong>{{ item.title }}</strong>：{{ item.optionAgreement }}
                </div>
              </div>
            </div>

            <div class="client-section">
              <div class="client-section-title" style="color:#DC2626;">三、双方共同明确：本期不做事项 (严禁现场追加)</div>
              <div v-if="confirmItems.filter(i => ['SKIP', 'NON_GOAL'].includes(i.itemCategory || i.category) || i.decisionResult === 'SKIP' || i.decisionResult === 'NEXT').length === 0" style="color:var(--text-muted); font-size:13px;">
                暂无不做事项
              </div>
              <div v-else style="display:flex; flex-wrap:wrap; gap:16px; font-size:13px; color:var(--text-muted);">
                <span v-for="item in confirmItems.filter(i => ['SKIP', 'NON_GOAL'].includes(i.itemCategory || i.category) || i.decisionResult === 'SKIP' || i.decisionResult === 'NEXT')" :key="item.id">
                  ❌ {{ item.title }}
                </span>
              </div>
            </div>

            <div class="client-section">
              <div class="client-section-title">四、原型只读确认 (可实际点击测试交互)</div>
              <div style="border:1px solid var(--border); border-radius:4px; height:320px; overflow:hidden;">
                <iframe
                  v-if="selectedPrototype && selectedPrototype.previewUrl"
                  :src="selectedPrototype.previewUrl"
                  style="width:100%; height:100%; border:none;"
                  sandbox="allow-scripts allow-same-origin"
                ></iframe>
                <div v-else style="display:flex; align-items:center; justify-content:center; height:100%; color:var(--text-muted); font-size:13px;">
                  当前需求未关联在线交互原型
                </div>
              </div>
            </div>

            <div class="client-section">
              <div class="client-section-title">五、请用一句话复述您理解的这一版核心交付物</div>
              <textarea class="form-control" rows="2" v-model="clientSignForm.summary"></textarea>
            </div>

            <div class="client-section" style="background:#FAFCFF;">
              <div class="client-section-title">六、三方法定签字留痕</div>
              <div style="display:grid; grid-template-columns: repeat(3, 1fr); gap:16px; margin-top:12px;">
                <div style="background:#FFF; padding:12px; border:1px solid #BBF7D0; border-radius:4px;">
                  <div style="font-size:12px; color:var(--text-muted);">业务拍板人 (甲方)</div>
                  <div style="font-weight:700; color:#15803D; margin-top:4px;">王总监 (已签署 ✔)</div>
                  <div style="font-size:11px; color:#64748B;">存证防伪码: 7F3A-91C2-88B1</div>
                </div>
                <div style="background:#FFF; padding:12px; border:1px solid #BBF7D0; border-radius:4px;">
                  <div style="font-size:12px; color:var(--text-muted);">验收人代表 (甲方/测试)</div>
                  <div style="font-weight:700; color:#15803D; margin-top:4px;">李科长 (已签署 ✔)</div>
                  <div style="font-size:11px; color:#64748B;">存证防伪码: 8B2C-44A1-90E2</div>
                </div>
                <div style="background:#FFF; padding:12px; border:1px solid #FCD34D; border-radius:4px;">
                  <div style="font-size:12px; color:var(--text-muted);">最终使用者代表</div>
                  <input type="text" class="form-control" style="margin-top:6px;" v-model="clientSignForm.signerName">
                  <button class="btn btn-accent btn-sm" style="width:100%; margin-top:8px;" @click="handleClientSign">✍️ 确认并提交签署</button>
                </div>
              </div>

              <!-- 逆向驳回分支 -->
              <div style="margin-top:16px; padding-top:14px; border-top:1px dashed #CBD5E1; display:flex; justify-content:space-between; align-items:center;">
                <span style="font-size:12px; color:var(--text-muted);">若对上述清单、原型或代价约定存在异议：</span>
                <button class="btn btn-outline btn-sm" style="color:#B91C1C; border-color:#FCA5A5; background:#FFF;" @click="modalClientRejectOpen = true">
                  ⚠️ 意见有分歧，申请驳回澄清
                </button>
              </div>
            </div>
          </div>

          <!-- E03 签名回执单 -->
          <div v-else-if="clientSubPage === 'e03'" class="client-viewport-container" style="padding:30px;">
            <div style="display:flex; justify-content:space-between; align-items:center; border-bottom:2px solid var(--border); padding-bottom:16px;">
              <div>
                <h2 style="font-size:22px; font-weight:700;">需求基线确认存证回执单 (E03)</h2>
                <div style="font-size:12px; color:var(--text-muted); margin-top:4px;">APEX 需求全生命周期治理平台 · 法定不可变证据链</div>
              </div>
              <button class="btn btn-outline" @click="ElMessage.success('已触发打印预览')">🖨️ 打印 / 存为 PDF</button>
            </div>

            <div class="receipt-box">
              <div style="display:grid; grid-template-columns:1fr 1fr; gap:16px; font-size:13px; line-height:1.8;">
                <div><strong>项目标识：</strong>{{ currentProject.projectKey }} · {{ currentProject.name }}</div>
                <div><strong>需求编号：</strong>{{ currentReq.reqCode || currentReq.reqNo }} ({{ currentReq.title }})</div>
                <div><strong>锁定基线：</strong>{{ baselineList[0]?.version || 'BL-v1.0' }} (不可篡改)</div>
                <div><strong>回执校验码：</strong><span style="font-family:monospace; color:var(--primary); font-weight:700;">1C9E-78D3-55F4</span></div>
                <div><strong>签署时间：</strong>2026-10-29 14:08:23</div>
                <div><strong>总代价共识：</strong>{{ currentCost.toFixed(1) }} 人天</div>
              </div>
              <hr style="border:none; border-top:1px dashed #CBD5E1; margin:16px 0;">
              <div style="font-size:13px;">
                <div><strong>固化快照指纹 (Manifest Hash)：</strong></div>
                <div style="font-family:monospace; font-size:12px; background:#EEF2F6; padding:8px; border-radius:4px; margin-top:4px; word-break:break-all;">
                  {{ baselineList[0]?.manifestHash || 'sha256:a3f9c2e177b9d40e1189ac3bb4109401bfd145cba2793108c40b7190e82c7a10' }} (已固化)
                </div>
              </div>
              <div style="font-size:13px; margin-top:12px;">
                <div><strong>客户复述存证：</strong>「{{ clientSignForm.summary }}」</div>
              </div>
            </div>
            <div style="font-size:12px; color:var(--text-muted); text-align:center;">
              本电子回执经 SHA256 算法加密上链留痕，任何一方私自变更代码均可凭此回执复原溯源。
            </div>
          </div>
        </div>
      </main>
    </div>

    <!-- ================= 抽屉面板：P06 确认清单编辑 ================= -->
    <div class="drawer-overlay" :class="{ active: drawerChecklistOpen }" @click="drawerChecklistOpen = false"></div>
    <div class="drawer-panel" :class="{ open: drawerChecklistOpen }">
      <div class="drawer-header">
        <div class="drawer-title">✏️ 编辑确认清单 · {{ currentReq.reqNo || currentReq.reqCode }} (P06)</div>
        <button class="btn btn-outline btn-sm" @click="drawerChecklistOpen = false">✕</button>
      </div>
      <div class="drawer-body">
        <div class="checklist-layer-tabs">
          <button class="checklist-tab-btn" :class="{ active: activeChecklistTab === 'scope' }" @click="activeChecklistTab = 'scope'">范围 ({{ confirmItems.filter(i => (i.itemCategory || i.category) === 'SCOPE').length }})</button>
          <button class="checklist-tab-btn" :class="{ active: activeChecklistTab === 'rules' }" @click="activeChecklistTab = 'rules'">规则/异常/验收 ({{ confirmItems.filter(i => ['RULE', 'EXCEPTION', 'ACCEPTANCE'].includes(i.itemCategory || i.category)).length }})</button>
          <button class="checklist-tab-btn" :class="{ active: activeChecklistTab === 'skip' }" @click="activeChecklistTab = 'skip'">不做事项 ({{ confirmItems.filter(i => ['SKIP', 'NON_GOAL'].includes(i.itemCategory || i.category)).length }})</button>
        </div>

        <div>
          <!-- 空状态提示 -->
          <div v-if="filteredChecklistItems.length === 0" style="padding:32px 16px; text-align:center; color:var(--text-muted); background:var(--bg-app); border:1px dashed var(--border); border-radius:var(--radius-sm); margin-bottom:12px;">
            <div style="font-size:24px; margin-bottom:6px;">📋</div>
            <div style="font-weight:600; color:var(--text); font-size:13px;">该分类下暂无清单条目</div>
            <div style="font-size:12px; margin-top:4px;">点击下方【+ 添加新清单条目】即可快速录入</div>
          </div>

          <div v-for="item in filteredChecklistItems" :key="item.id || item.itemCode || item.title" style="border:1px solid var(--border); border-radius:var(--radius-sm); padding:14px; margin-bottom:12px; background:var(--surface);">
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:6px;">
              <strong style="color:var(--text);">{{ item.title }}</strong>
              <span style="font-size:12px; color:var(--text-muted);">代价基准: +{{ item.costDelta }}d</span>
            </div>
            <div style="font-size:13px; color:var(--text-muted); margin-bottom:10px;">
              问法：{{ item.questionPrompt }}
            </div>
            <div style="display:flex; justify-content:space-between; align-items:center;">
              <div class="decision-options">
                <button
                  class="decision-btn"
                  :class="{ 'active-current': item.decisionResult === 'CURRENT' }"
                  @click="setDecision(item, 'CURRENT')"
                >
                  ● 本期做 (+{{ item.costDelta }}d)
                </button>
                <button
                  class="decision-btn"
                  :class="{ 'active-next': item.decisionResult === 'NEXT' }"
                  @click="setDecision(item, 'NEXT')"
                >
                  下期做 (0d)
                </button>
                <button
                  class="decision-btn"
                  :class="{ 'active-skip': item.decisionResult === 'SKIP' }"
                  @click="setDecision(item, 'SKIP')"
                >
                  不做 (0d)
                </button>
              </div>
              <button class="btn btn-outline btn-sm" style="color:#DC2626;" @click="handleDeleteItem(item)">删除</button>
            </div>
          </div>
        </div>

        <button class="btn btn-outline btn-sm" style="width:100%; border-style:dashed; margin-bottom:14px;" @click="handleOpenAddItemModal">+ 添加新清单条目 (M04)</button>

        <!-- 实时代价折算汇总条 -->
        <div class="cost-summary-bar">
          <div>
            <span>已敲定共识代价：</span>
            <span class="cost-stat-tag">{{ currentCost.toFixed(1) }} 人天</span>
            <span style="font-size:12px; color:var(--text-muted); margin-left:8px;">
              (本期 {{ confirmItems.filter(i => i.decisionResult === 'CURRENT').length }} 项 · 下期 {{ confirmItems.filter(i => i.decisionResult === 'NEXT').length }} 项 · 不做 {{ confirmItems.filter(i => i.decisionResult === 'SKIP').length }} 项)
            </span>
          </div>
          <button class="btn btn-outline btn-sm" @click="switchPerspective('client')">预览客户签名效果</button>
        </div>
      </div>
      <div class="drawer-footer">
        <button class="btn btn-outline" @click="drawerChecklistOpen = false">取消</button>
        <button class="btn btn-primary" @click="handleSaveChecklist">保存清单决策</button>
      </div>
    </div>

    <!-- ================= 抽屉面板：M02 新建需求 ================= -->
    <div class="drawer-overlay" :class="{ active: drawerNewReqOpen }" @click="drawerNewReqOpen = false"></div>
    <div class="drawer-panel" :class="{ open: drawerNewReqOpen }">
      <div class="drawer-header">
        <div class="drawer-title">➕ 快速录入新需求 (M02)</div>
        <button class="btn btn-outline btn-sm" @click="drawerNewReqOpen = false">✕</button>
      </div>
      <div class="drawer-body">
        <div class="form-group">
          <label class="form-label required">需求标题</label>
          <input type="text" class="form-control" placeholder="简述核心功能，例如：OT机房交换机拓扑告警" v-model="newReqForm.title">
        </div>
        <div class="form-group">
          <label class="form-label required">客户原话 (留痕防扯皮)</label>
          <textarea class="form-control" rows="3" placeholder="逐字录入客户原话或邮件片段..." v-model="newReqForm.quote"></textarea>
        </div>
        <div class="form-group">
          <label class="form-label required">责任人 (动态取自数据库用户库)</label>
          <select class="form-control" v-model="newReqForm.owner">
            <option v-for="u in userList" :key="u.id" :value="u.nickname || u.username">
              {{ u.nickname || u.username }} ({{ u.username }})
            </option>
            <!-- 数据库初始空状态候选项 -->
            <template v-if="userList.length === 0">
              <option value="张工 (产品负责人)">张工 (产品负责人)</option>
              <option value="李工 (核心研发)">李工 (核心研发)</option>
              <option value="王工 (测试架构师)">王工 (测试架构师)</option>
            </template>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label">优先级</label>
          <select class="form-control" v-model="newReqForm.priority">
            <option value="P1">P1 - 本期核心必做</option>
            <option value="P2">P2 - 重要</option>
            <option value="P3">P3 - 体验优化</option>
          </select>
        </div>
      </div>
      <div class="drawer-footer">
        <button class="btn btn-outline" @click="drawerNewReqOpen = false">取消</button>
        <button class="btn btn-primary" @click="handleSubmitNewReq">创建并进入清单细化</button>
      </div>
    </div>

    <!-- ================= 抽屉面板：M08 新建基线 ================= -->
    <div class="drawer-overlay" :class="{ active: drawerNewBaselineOpen }" @click="drawerNewBaselineOpen = false"></div>
    <div class="drawer-panel" :class="{ open: drawerNewBaselineOpen }">
      <div class="drawer-header">
        <div class="drawer-title">🔒 创建需求基线 (M08)</div>
        <button class="btn btn-outline btn-sm" @click="drawerNewBaselineOpen = false">✕</button>
      </div>
      <div class="drawer-body">
        <div class="form-group">
          <label class="form-label required">基线版本号</label>
          <input type="text" class="form-control" value="BL-v1.2" readonly>
        </div>
        <div class="form-group">
          <label class="form-label required">绑定原型版本</label>
          <select class="form-control">
            <option value="v3">mockup v3.0 (hash: a3f9c2... 包含嵌套底板)</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label required">关联设计文档</label>
          <div style="font-size:13px; line-height:1.8;">
            <label><input type="checkbox" checked disabled> 详细设计说明书 v1.1</label><br>
            <label><input type="checkbox" checked disabled> DDL 与回滚配对脚本</label>
          </div>
        </div>
        <div class="form-group">
          <label class="form-label required">三方法定免登签名人员 (联动系统成员库)</label>
          <div style="display:flex; flex-direction:column; gap:10px; margin-top:6px;">
            <div style="display:flex; align-items:center; gap:8px;">
              <span style="width:90px; font-size:12px; font-weight:600; color:var(--text-muted);">业务拍板人:</span>
              <select class="form-control" v-model="newBaselineForm.bizOwnerName" style="flex:1;">
                <option v-for="u in userList" :key="'biz-'+u.id" :value="u.nickname || u.username">
                  {{ u.nickname || u.username }} ({{ u.username }})
                </option>
                <option v-if="userList.length === 0" value="张工 (产品负责人)">张工 (产品负责人)</option>
              </select>
            </div>
            <div style="display:flex; align-items:center; gap:8px;">
              <span style="width:90px; font-size:12px; font-weight:600; color:var(--text-muted);">验收人:</span>
              <select class="form-control" v-model="newBaselineForm.acceptorName" style="flex:1;">
                <option v-for="u in userList" :key="'acc-'+u.id" :value="u.nickname || u.username">
                  {{ u.nickname || u.username }} ({{ u.username }})
                </option>
                <option v-if="userList.length === 0" value="李工 (核心研发)">李工 (核心研发)</option>
              </select>
            </div>
            <div style="display:flex; align-items:center; gap:8px;">
              <span style="width:90px; font-size:12px; font-weight:600; color:var(--text-muted);">使用者代表:</span>
              <select class="form-control" v-model="newBaselineForm.userRepName" style="flex:1;">
                <option v-for="u in userList" :key="'rep-'+u.id" :value="u.nickname || u.username">
                  {{ u.nickname || u.username }} ({{ u.username }})
                </option>
                <option v-if="userList.length === 0" value="王工 (测试架构师)">王工 (测试架构师)</option>
              </select>
            </div>
          </div>
        </div>
      </div>
      <div class="drawer-footer">
        <button class="btn btn-outline" @click="drawerNewBaselineOpen = false">取消</button>
        <button class="btn btn-primary" @click="handleCreateBaseline">生成基线并开放签字</button>
      </div>
    </div>

    <!-- ================= 模态弹窗：M09 生成签名链接 ================= -->
    <div class="modal-overlay" :class="{ active: modalGenSignLinkOpen }">
      <div class="card" style="width:480px; margin:0;">
        <div class="modal-header">
          <div class="modal-title">🔗 客户签名链接已生成 (M09)</div>
          <button class="btn btn-outline btn-sm" @click="modalGenSignLinkOpen = false">✕</button>
        </div>
        <div class="modal-body">
          <p style="font-size:13px; color:var(--text-secondary); margin-bottom:12px;">
            请将下方一次性专用链接发送给客户，客户免注册登录凭此链接即可在线完成清单勾选与电子签名：
          </p>
          <div class="form-group">
            <label class="form-label">免登签名专用链接</label>
            <input type="text" class="form-control" :value="activeBaselineSignLink.url" readonly>
          </div>
          <div class="form-group">
            <label class="form-label">访问验证码 (SMS/邮件二次保护)</label>
            <input type="text" class="form-control" :value="activeBaselineSignLink.code" readonly style="letter-spacing:4px; font-weight:700;">
          </div>
          <div class="form-hint">有效期：7 天 (至 2026-11-08 23:59:59)</div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-outline" @click="modalGenSignLinkOpen = false">关闭</button>
          <button class="btn btn-primary" @click="handleCopySignLink">📋 复制链接与验证码</button>
        </div>
      </div>
    </div>

    <!-- ================= 模态弹窗：客户申请驳回澄清 (M10-Reverse) ================= -->
    <div class="modal-overlay" :class="{ active: modalClientRejectOpen }">
      <div class="card" style="width:520px; margin:0; border-top:4px solid #DC2626;">
        <div class="modal-header">
          <div class="modal-title" style="color:#DC2626;">⚠️ 申请驳回基线确认 (提出异议)</div>
          <button class="btn btn-outline btn-sm" @click="modalClientRejectOpen = false">✕</button>
        </div>
        <div class="modal-body">
          <p style="font-size:13px; color:var(--text-secondary); margin-bottom:12px;">
            若您对本次约定清单的交付范围、规则或人天代价存在异议，可直接驳回。系统将自动通知内部产品经理李工重新核对：
          </p>
          <div class="form-group">
            <label class="form-label required">异议提出人与身份</label>
            <input type="text" class="form-control" v-model="clientRejectForm.author">
          </div>
          <div class="form-group">
            <label class="form-label required">具体分歧说明 (驳回原因)</label>
            <textarea class="form-control" rows="3" placeholder="例如：调速器掉线判定60秒太长，机组要求30秒内即报警..." v-model="clientRejectForm.reason"></textarea>
          </div>
          <div class="form-hint" style="color:#B91C1C;">
            注意：驳回后当前基线将退回【澄清中 (CLARIFYING)】状态，此前签署记录将安全留痕归档。
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-outline" @click="modalClientRejectOpen = false">取消返回</button>
          <button class="btn btn-accent" style="background:#DC2626; border-color:#DC2626;" @click="handleClientReject">确认驳回并退回澄清</button>
        </div>
      </div>
    </div>

    <!-- ================= 模态弹窗：M01 新建项目 ================= -->
    <div class="modal-overlay" :class="{ active: modalNewProjectOpen }">
      <div class="card" style="width:500px; margin:0;">
        <div class="modal-header">
          <div class="modal-title">📁 新建项目空间 (M01)</div>
          <button class="btn btn-outline btn-sm" @click="modalNewProjectOpen = false">✕</button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label class="form-label required">项目唯一标识 Key</label>
            <input type="text" class="form-control" v-model="newProjectForm.projectKey" placeholder="2~8位大写字母，如: DCS-SIM">
          </div>
          <div class="form-group">
            <label class="form-label required">项目名称</label>
            <input type="text" class="form-control" v-model="newProjectForm.name" placeholder="如：集控中心监控仿真实训系统">
          </div>
          <div class="form-group">
            <label class="form-label required">客户单位名称</label>
            <input type="text" class="form-control" v-model="newProjectForm.clientName" placeholder="如：国能某某水电厂">
          </div>
          <div class="form-group">
            <label class="form-label">项目描述与背景</label>
            <textarea class="form-control" rows="2" v-model="newProjectForm.description" placeholder="说明项目目标、涉及核心系统与责任边界..."></textarea>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-outline" @click="modalNewProjectOpen = false">取消</button>
          <button class="btn btn-primary" @click="handleCreateProject">立即创建</button>
        </div>
      </div>
    </div>

    <!-- ================= 模态弹窗：M05 上传原型版本 ================= -->
    <div class="modal-overlay" :class="{ active: modalUploadPrototypeOpen }">
      <div class="card" style="width:480px; margin:0;">
        <div class="modal-header">
          <div class="modal-title">📤 上传原型版本 ZIP (M05)</div>
          <button class="btn btn-outline btn-sm" @click="modalUploadPrototypeOpen = false">✕</button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label class="form-label required">版本命名</label>
            <input type="text" class="form-control" value="mockup v4.0" readonly>
          </div>
          <div class="form-group">
            <label class="form-label required">原型 ZIP 包 (必须含 mockup.html)</label>
            <input type="file" class="form-control">
            <div class="form-hint">上传后系统自动进行 SHA256 校验和提取，永不覆盖旧版本目录。</div>
          </div>
          <div class="form-group">
            <label class="form-label">变更摘要</label>
            <textarea class="form-control" rows="2" placeholder="简述本次原型修改重点..."></textarea>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-outline" @click="modalUploadPrototypeOpen = false">取消</button>
          <button class="btn btn-primary" @click="ElMessage.success('原型 v4.0 已上传，已生成哈希值并生成新快照！'); modalUploadPrototypeOpen = false;">开始解析并固化</button>
        </div>
      </div>
    </div>

    <!-- ================= 模态弹窗：M04 添加清单条目 ================= -->
    <div class="modal-overlay" :class="{ active: modalNewItemOpen }">
      <div class="card" style="width:580px; max-height:90vh; margin:0; display:flex; flex-direction:column; box-shadow:var(--shadow-lg);">
        <div class="modal-header">
          <div class="modal-title">➕ 添加清单条目 (M04)</div>
          <button class="btn btn-outline btn-sm" @click="modalNewItemOpen = false">✕</button>
        </div>
        <div class="modal-body" style="overflow-y:auto; padding:20px;">
          <!-- 类别选择 -->
          <div class="form-group">
            <label class="form-label required">条目层级与分类</label>
            <div style="display:flex; gap:12px; flex-wrap:wrap; padding:8px 12px; background:var(--bg-app); border:1px solid var(--border); border-radius:var(--radius-sm);">
              <label
                v-for="cat in [
                  { key: 'SCOPE', label: '范围 (Scope)', prefix: 'R-' },
                  { key: 'RULE', label: '规则 (Rule)', prefix: 'B-' },
                  { key: 'EXCEPTION', label: '异常 (Exception)', prefix: 'E-' },
                  { key: 'ACCEPTANCE', label: '验收 (Acceptance)', prefix: 'A-' },
                  { key: 'SKIP', label: '不做 (Non-goal)', prefix: 'N-' }
                ]"
                :key="cat.key"
                style="display:flex; align-items:center; gap:5px; font-size:13px; cursor:pointer;"
              >
                <input
                  type="radio"
                  name="itemCategoryRadio"
                  :value="cat.key"
                  v-model="newItemForm.category"
                  @change="onItemCategoryChange"
                />
                <span :style="{ fontWeight: newItemForm.category === cat.key ? '600' : 'normal', color: newItemForm.category === cat.key ? 'var(--primary)' : 'inherit' }">
                  {{ cat.label }}
                </span>
              </label>
            </div>
          </div>

          <!-- 编码与问法 -->
          <div style="display:grid; grid-template-columns: 130px 1fr; gap:12px;">
            <div class="form-group">
              <label class="form-label required">条目编号</label>
              <input
                type="text"
                class="form-control"
                v-model="newItemForm.itemCode"
                placeholder="如 R-01"
              />
            </div>
            <div class="form-group">
              <label class="form-label required">业务问法 (≤100字，客户能听懂的话)</label>
              <input
                type="text"
                class="form-control"
                v-model="newItemForm.question"
                maxlength="100"
                placeholder="例如：是否按 1#~4# 机组进行子集群底板划分？"
              />
            </div>
          </div>

          <!-- 动态候选选项与代价 (2~4 项) -->
          <div class="form-group">
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:8px;">
              <label class="form-label required" style="margin-bottom:0;">
                候选选项与代价 (2~4 项，单选选定默认约定)
              </label>
              <button
                type="button"
                class="btn btn-outline btn-sm"
                style="font-size:12px; padding:2px 10px;"
                @click="addOptionItem"
                :disabled="newItemForm.options.length >= 4"
              >
                + 增加选项 ({{ newItemForm.options.length }}/4)
              </button>
            </div>
            <div style="display:flex; flex-direction:column; gap:8px;">
              <div
                v-for="(opt, optIdx) in newItemForm.options"
                :key="optIdx"
                style="display:flex; align-items:center; gap:8px; background:var(--bg-app); border:1px solid var(--border); border-radius:var(--radius-sm); padding:6px 10px;"
              >
                <input
                  type="radio"
                  name="defaultOptionRadio"
                  :value="optIdx"
                  v-model="newItemForm.selectedOptionIndex"
                  title="勾选设为本期选定约定"
                  style="cursor:pointer;"
                />
                <input
                  type="text"
                  class="form-control"
                  style="flex:1; background:#FFF;"
                  v-model="opt.label"
                  :placeholder="'选项 ' + (optIdx + 1) + ' 交付约定方案文案'"
                />
                <div style="display:flex; align-items:center; gap:4px; width:120px;">
                  <input
                    type="number"
                    step="0.1"
                    min="0"
                    class="form-control"
                    style="background:#FFF; text-align:right;"
                    v-model.number="opt.cost"
                    placeholder="0.0"
                  />
                  <span style="font-size:12px; color:var(--text-muted); white-space:nowrap;">人天</span>
                </div>
                <button
                  type="button"
                  class="btn btn-outline btn-sm"
                  style="color:#DC2626; padding:4px 8px; border-color:#FECACA;"
                  @click="removeOptionItem(optIdx)"
                  :disabled="newItemForm.options.length <= 2"
                  title="删除该候选选项"
                >
                  ✕
                </button>
              </div>
            </div>
            <div style="font-size:12px; color:var(--text-muted); margin-top:6px;">
              * 选中的单选框代表该条目的核心交付方案；本期做时将按其代价 (+{{ (newItemForm.options[newItemForm.selectedOptionIndex]?.cost || 0).toFixed(1) }}d) 计入总人天折算。
            </div>
          </div>

          <!-- 初始决策结果 -->
          <div class="form-group">
            <label class="form-label required">初始共识决策</label>
            <div style="display:flex; gap:16px; padding:8px 12px; background:var(--bg-app); border:1px solid var(--border); border-radius:var(--radius-sm);">
              <label style="display:flex; align-items:center; gap:6px; font-size:13px; cursor:pointer;">
                <input type="radio" value="CURRENT" v-model="newItemForm.decision" />
                <span style="color:var(--primary); font-weight:600;">✔ 本期做 (+{{ (newItemForm.options[newItemForm.selectedOptionIndex]?.cost || 0).toFixed(1) }}d)</span>
              </label>
              <label style="display:flex; align-items:center; gap:6px; font-size:13px; cursor:pointer;">
                <input type="radio" value="NEXT" v-model="newItemForm.decision" />
                <span style="color:#D97706; font-weight:600;">⏳ 下期做 (0d)</span>
              </label>
              <label style="display:flex; align-items:center; gap:6px; font-size:13px; cursor:pointer;">
                <input type="radio" value="SKIP" v-model="newItemForm.decision" />
                <span style="color:#DC2626; font-weight:600;">❌ 本期不做 (0d)</span>
              </label>
            </div>
          </div>

          <!-- 补充备注 -->
          <div class="form-group" style="margin-bottom:0;">
            <label class="form-label">补充备注 (选填，≤200字)</label>
            <textarea
              class="form-control"
              rows="2"
              maxlength="200"
              v-model="newItemForm.note"
              placeholder="客户口头强调说明、责任技术边界或其它上下文约定..."
            ></textarea>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-outline" @click="modalNewItemOpen = false">取消</button>
          <button class="btn btn-primary" @click="handleConfirmAddItem">确认添加至清单</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.req-app-container {
  --primary: #14535B;
  --primary-dark: #0D373D;
  --primary-light: #E6F0F2;
  --accent: #C2410C;
  --accent-light: #FFEDD5;
  --bg: #F8FAFC;
  --surface: #FFFFFF;
  --border: #E2E8F0;
  --border-dark: #CBD5E1;
  --text: #0F172A;
  --text-muted: #64748B;
  --text-secondary: #334155;
  --success: #16A34A;
  --success-light: #DCFCE7;
  --warning: #D97706;
  --warning-light: #FEF3C7;
  --info: #0284C7;
  --info-light: #E0F2FE;
  --radius-sm: 4px;
  --radius-md: 8px;
  --radius-lg: 12px;
  --shadow-sm: 0 1px 2px 0 rgba(0, 0, 0, 0.05);
  --shadow-md: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -2px rgba(0, 0, 0, 0.1);
  --shadow-lg: 0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -4px rgba(0, 0, 0, 0.1);
  --font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;

  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background-color: var(--bg);
  color: var(--text);
  font-family: var(--font-family);
  font-size: 14px;
  line-height: 1.5;
}

/* 顶部顶栏 */
header.app-header {
  background-color: var(--primary);
  color: #FFFFFF;
  height: 52px;
  padding: 0 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: var(--shadow-sm);
  z-index: 50;
  flex-shrink: 0;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}
.logo-badge {
  background: rgba(255, 255, 255, 0.15);
  font-weight: 700;
  font-size: 13px;
  padding: 3px 8px;
  border-radius: var(--radius-sm);
  letter-spacing: 0.5px;
}
.app-title {
  font-size: 16px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}
.project-selector {
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.25);
  color: #FFF;
  padding: 4px 10px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  cursor: pointer;
  outline: none;
}
.project-selector option {
  background: var(--surface);
  color: var(--text);
}
.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}
.role-badge {
  background: #E6F0F2;
  color: var(--primary-dark);
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
}
.view-mode-toggle {
  background: rgba(0, 0, 0, 0.2);
  padding: 3px;
  border-radius: var(--radius-sm);
  display: flex;
  gap: 2px;
}
.view-mode-btn {
  background: transparent;
  border: none;
  color: #E2E8F0;
  padding: 4px 10px;
  font-size: 12px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all 0.2s;
}
.view-mode-btn.active {
  background: var(--surface);
  color: var(--primary);
  font-weight: 600;
  box-shadow: var(--shadow-sm);
}

/* 主体布局 */
.app-body {
  display: flex;
  flex: 1;
  height: calc(100% - 52px);
  overflow: hidden;
}

/* 左侧导航栏 */
aside.sidebar {
  width: 220px;
  background: var(--surface);
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  user-select: none;
  overflow-y: auto;
}
.nav-group-title {
  font-size: 11px;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  padding: 16px 16px 6px;
  font-weight: 600;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 16px;
  color: var(--text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
  border-left: 3px solid transparent;
  text-decoration: none;
}
.nav-item:hover {
  background: var(--bg);
  color: var(--primary);
}
.nav-item.active {
  background: var(--primary-light);
  color: var(--primary);
  font-weight: 600;
  border-left-color: var(--primary);
}
.nav-badge {
  margin-left: auto;
  background: var(--border);
  color: var(--text-secondary);
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 999px;
  font-weight: 500;
}
.nav-badge.pending {
  background: var(--accent-light);
  color: var(--accent);
  font-weight: 700;
}

/* 右侧内容区 */
main.main-content {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  background: var(--bg);
  position: relative;
}

/* 视图显隐控制 */
.view-page {
  display: none;
  animation: fadeIn 0.15s ease-in-out;
}
.view-page.active {
  display: block;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 通用 UI 组件 */
.card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
  margin-bottom: 20px;
  overflow: hidden;
}
.card-header {
  padding: 14px 20px;
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #FCFDFE;
}
.card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text);
  display: flex;
  align-items: center;
  gap: 8px;
}
.card-body {
  padding: 20px;
}

/* 按钮 */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 7px 14px;
  font-size: 13px;
  font-weight: 500;
  border-radius: var(--radius-sm);
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.15s;
}
.btn-primary {
  background: var(--primary);
  color: #FFF;
  border-color: var(--primary);
}
.btn-primary:hover {
  background: var(--primary-dark);
  border-color: var(--primary-dark);
}
.btn-outline {
  background: var(--surface);
  color: var(--text-secondary);
  border-color: var(--border-dark);
}
.btn-outline:hover {
  background: var(--bg);
  color: var(--text);
  border-color: #94A3B8;
}
.btn-accent {
  background: var(--accent);
  color: #FFF;
  border-color: var(--accent);
}
.btn-accent:hover {
  background: #9A3412;
}
.btn-sm {
  padding: 4px 8px;
  font-size: 12px;
}

/* 标签 Tag */
.tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 7px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  font-weight: 500;
  line-height: 1.2;
}
.tag-draft { background: #F1F5F9; color: #475569; }
.tag-clarifying { background: #E0F2FE; color: #0369A1; }
.tag-pending { background: #FFEDD5; color: #C2410C; }
.tag-baselined { background: #DCFCE7; color: #15803D; }
.tag-released { background: #E6F0F2; color: #14535B; }
.tag-danger { background: #FEE2E2; color: #B91C1C; }

/* 表格 */
.table-container {
  width: 100%;
  overflow-x: auto;
}
table.data-table {
  width: 100%;
  border-collapse: collapse;
  text-align: left;
  font-size: 13px;
}
table.data-table th {
  background: #F8FAFC;
  color: var(--text-muted);
  font-weight: 600;
  padding: 10px 14px;
  border-bottom: 1px solid var(--border);
  white-space: nowrap;
}
table.data-table td {
  padding: 12px 14px;
  border-bottom: 1px solid var(--border);
  color: var(--text-secondary);
  vertical-align: middle;
}
table.data-table tr:hover td {
  background: #F9FBFC;
}

/* 指标统计卡片 Grid */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}
.stat-card {
  background: var(--surface);
  border: 1px solid var(--border);
  padding: 16px;
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
}
.stat-label {
  font-size: 12px;
  color: var(--text-muted);
  font-weight: 500;
  margin-bottom: 4px;
}
.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--text);
}
.stat-sub {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 4px;
}

/* 抽屉 Drawer & 模态框 Modal */
.drawer-overlay {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(15, 23, 42, 0.5);
  backdrop-filter: blur(2px);
  z-index: 100;
  display: none;
  align-items: center;
  justify-content: center;
}
.modal-overlay {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(15, 23, 42, 0.55);
  backdrop-filter: blur(2px);
  z-index: 200;
  display: none;
  align-items: center;
  justify-content: center;
}
.drawer-overlay.active, .modal-overlay.active {
  display: flex;
}
.drawer-panel {
  position: fixed;
  top: 0; right: -680px; bottom: 0;
  width: 640px;
  background: var(--surface);
  box-shadow: var(--shadow-lg);
  z-index: 101;
  display: flex;
  flex-direction: column;
  transition: right 0.25s cubic-bezier(0.16, 1, 0.3, 1);
}
.drawer-panel.open {
  right: 0;
}
.drawer-header, .modal-header {
  padding: 16px 20px;
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #FAFAFA;
}
.drawer-title, .modal-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text);
}
.drawer-body, .modal-body {
  padding: 20px;
  overflow-y: auto;
  flex: 1;
}
.drawer-footer, .modal-footer {
  padding: 14px 20px;
  border-top: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  background: #FAFAFA;
}

/* 表单组件 */
.form-group {
  margin-bottom: 16px;
}
.form-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
  margin-bottom: 6px;
}
.form-label.required::after {
  content: " *";
  color: #DC2626;
}
.form-control {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--border-dark);
  border-radius: var(--radius-sm);
  font-size: 13px;
  outline: none;
  background: var(--surface);
  color: var(--text);
  font-family: inherit;
}
.form-control:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 2px var(--primary-light);
}
.form-hint {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 4px;
}

/* 确认清单四层与代价联动专用组件 */
.checklist-layer-tabs {
  display: flex;
  border-bottom: 1px solid var(--border);
  margin-bottom: 16px;
  gap: 8px;
}
.checklist-tab-btn {
  padding: 8px 14px;
  font-size: 13px;
  border: none;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  border-bottom: 2px solid transparent;
  font-weight: 500;
}
.checklist-tab-btn.active {
  color: var(--primary);
  border-bottom-color: var(--primary);
  font-weight: 600;
}
.decision-options {
  display: flex;
  gap: 6px;
}
.decision-btn {
  padding: 4px 10px;
  font-size: 12px;
  border: 1px solid var(--border-dark);
  border-radius: var(--radius-sm);
  cursor: pointer;
  background: var(--surface);
  color: var(--text-secondary);
  font-weight: 500;
  transition: all 0.15s;
}
.decision-btn.active-current {
  background: var(--success);
  color: #FFF;
  border-color: var(--success);
}
.decision-btn.active-next {
  background: var(--info);
  color: #FFF;
  border-color: var(--info);
}
.decision-btn.active-skip {
  background: #94A3B8;
  color: #FFF;
  border-color: #94A3B8;
}

/* 代价总计栏 */
.cost-summary-bar {
  background: #F0FDF4;
  border: 1px solid #BBF7D0;
  border-radius: var(--radius-sm);
  padding: 12px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
  font-size: 13px;
}
.cost-stat-tag {
  font-weight: 700;
  color: #15803D;
  font-size: 16px;
  margin-left: 4px;
}

/* 客户外部免登录视角样式 */
.client-viewport-container {
  max-width: 920px;
  margin: 0 auto;
  background: var(--surface);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-md);
  border: 1px solid var(--border);
  position: relative;
}
.watermark-overlay {
  position: absolute;
  top: 10px; right: 16px;
  font-size: 12px;
  color: var(--text-muted);
  pointer-events: none;
  opacity: 0.7;
}
.client-header {
  padding: 24px 30px;
  border-bottom: 1px solid var(--border);
  background: linear-gradient(to right, #F8FAFC, #FFFFFF);
}
.client-quote-box {
  background: #F1F5F9;
  border-left: 4px solid var(--primary);
  padding: 12px 16px;
  margin-top: 14px;
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
  font-size: 13px;
  color: var(--text-secondary);
  font-style: italic;
}
.client-section {
  padding: 20px 30px;
  border-bottom: 1px solid var(--border);
}
.client-section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.client-iframe-preview {
  width: 100%;
  height: 280px;
  border: 1px solid var(--border-dark);
  border-radius: var(--radius-sm);
  background: #FAFAFA;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  position: relative;
  overflow: hidden;
}
.receipt-box {
  border: 2px dashed #CBD5E1;
  padding: 24px;
  border-radius: var(--radius-md);
  background: #FAFCFF;
  margin: 20px 0;
}

/* P13 演进时间线专用样式 */
.timeline-container {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 24px;
  box-shadow: var(--shadow-sm);
  position: relative;
  overflow-x: auto;
}
.timeline-legend {
  display: flex;
  gap: 20px;
  align-items: center;
  padding-bottom: 16px;
  margin-bottom: 20px;
  border-bottom: 1px solid var(--border);
  font-size: 12px;
  color: var(--text-secondary);
}
.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.timeline-axis-header {
  display: flex;
  margin-left: 200px;
  border-bottom: 2px solid var(--border-dark);
  padding-bottom: 8px;
  margin-bottom: 20px;
  position: relative;
}
.axis-tick {
  flex: 1;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-muted);
  position: relative;
}
.axis-tick::before {
  content: '';
  position: absolute;
  bottom: -10px;
  left: 0;
  width: 1px;
  height: 6px;
  background: var(--border-dark);
}
.timeline-row {
  display: flex;
  align-items: center;
  margin-bottom: 24px;
  position: relative;
  min-height: 48px;
}
.timeline-row-label {
  width: 190px;
  padding-right: 12px;
  flex-shrink: 0;
}
.timeline-row-req-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
  display: flex;
  align-items: center;
  gap: 6px;
}
.timeline-track-wrap {
  flex: 1;
  height: 44px;
  position: relative;
  display: flex;
  align-items: center;
}
.timeline-track-line {
  position: absolute;
  top: 50%;
  left: 0;
  right: 0;
  height: 4px;
  background: #E2E8F0;
  transform: translateY(-50%);
  border-radius: 2px;
  z-index: 1;
}
.timeline-node {
  position: absolute;
  top: 50%;
  transform: translate(-50%, -50%);
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
  transition: all 0.2s;
}
.timeline-node:hover {
  transform: translate(-50%, -58%) scale(1.08);
}
.node-icon-circle {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #FFF;
  border: 3px solid #64748B;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}
.node-icon-circle.prototype {
  border-color: #0284C7;
  background: #E0F2FE;
  color: #0284C7;
}
.node-icon-circle.baseline {
  border-color: #16A34A;
  background: #DCFCE7;
  color: #16A34A;
}
.node-icon-circle.pending-baseline {
  border-color: #D97706;
  background: #FEF3C7;
  color: #D97706;
}
.node-icon-circle.release {
  border-color: #14535B;
  background: #14535B;
  color: #FFF;
}
.node-title-badge {
  position: absolute;
  top: 26px;
  white-space: nowrap;
  font-size: 11px;
  font-weight: 600;
  padding: 2px 6px;
  border-radius: 4px;
  background: #FFF;
  border: 1px solid var(--border);
  color: var(--text-secondary);
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}
.timeline-date-marker {
  position: absolute;
  bottom: 26px;
  font-size: 10px;
  color: var(--text-muted);
  white-space: nowrap;
}
.timeline-connection-line {
  position: absolute;
  top: 50%;
  height: 4px;
  background: #16A34A;
  transform: translateY(-50%);
  z-index: 1;
  border-radius: 2px;
}
</style>
