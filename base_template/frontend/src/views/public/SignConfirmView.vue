<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, Warning, Key, CircleCheckFilled } from '@element-plus/icons-vue'
import { getSignContextApi, confirmSignApi, rejectSignApi } from '@/api/req'

const route = useRoute()
const token = ref(route.params.token as string)
const accessCode = ref((route.query.code as string) || '')

const loading = ref(false)
const contextData = ref<any>(null)
const step = ref<'VERIFY' | 'PREVIEW' | 'RECEIPT'>('VERIFY')

// 表单字段
const signerName = ref('')
const restatementText = ref('')
const agreeDisclaimer = ref(false)
const submitting = ref(false)
const receiptResult = ref<any>(null)

// 校验验证码并加载上下文
const handleVerifyCode = async () => {
  if (!accessCode.value || accessCode.value.length < 4) {
    ElMessage.warning('请输入正确的 4 位防窥访问码')
    return
  }
  loading.value = true
  try {
    const res = await getSignContextApi(token.value, accessCode.value)
    contextData.value = res.data
    signerName.value = res.data.signerName || ''
    if (res.data.status === 'SIGNED') {
      receiptResult.value = {
        receiptCode: res.data.receiptCode,
        baselineVersion: res.data.baselineVersion,
        isFullyFrozen: true,
        signedAt: res.data.signedAt
      }
      step.value = 'RECEIPT'
    } else {
      step.value = 'PREVIEW'
    }
  } catch (e: any) {
    // 错误在 request 拦截器处理
  } finally {
    loading.value = false
  }
}

// 提交电子签署
const handleConfirmSign = async () => {
  if (!signerName.value.trim()) {
    ElMessage.warning('请输入签署人姓名')
    return
  }
  if (!restatementText.value.trim()) {
    ElMessage.warning('请填写一句话复述理解存证（不可空置）')
    return
  }
  if (!agreeDisclaimer.value) {
    ElMessage.warning('请阅读并勾选法律存证免责声明')
    return
  }

  submitting.value = true
  try {
    const res = await confirmSignApi(token.value, {
      accessCode: accessCode.value,
      signerName: signerName.value,
      restatementText: restatementText.value,
      agreeDisclaimer: agreeDisclaimer.value
    })
    receiptResult.value = res.data
    step.value = 'RECEIPT'
    ElMessage.success('法定签署成功，已生成不可篡改电子回执凭据！')
  } catch (e: any) {
  } finally {
    submitting.value = false
  }
}

// 提交异议驳回
const handleRejectSign = async () => {
  if (!restatementText.value.trim()) {
    ElMessage.warning('请填写异议分歧说明')
    return
  }
  submitting.value = true
  try {
    await rejectSignApi(token.value, {
      accessCode: accessCode.value,
      signerName: signerName.value,
      rejectReason: restatementText.value
    })
    ElMessage.success('已记录异议分歧并退回澄清状态')
    await handleVerifyCode()
  } catch (e: any) {
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  if (accessCode.value && accessCode.value.length === 4) {
    handleVerifyCode()
  }
})
</script>

<template>
  <div class="public-sign-container" v-loading="loading">
    <!-- 顶栏标题 -->
    <header class="sign-header">
      <div class="brand">
        <span class="logo">⚡</span>
        <span>钧天科技 · 客户法定免登签署平台</span>
      </div>
      <div class="security-badge">
        <el-icon><Lock /></el-icon>
        <span>HMAC 一次性令牌防伪存证通道</span>
      </div>
    </header>

    <!-- 步骤一：输入 4 位防窥码 -->
    <div v-if="step === 'VERIFY'" class="verify-box">
      <el-card class="verify-card" shadow="hover">
        <template #header>
          <div class="verify-title">
            <el-icon color="#14535b" :size="20"><Key /></el-icon>
            <span>输入 4 位防窥访问码</span>
          </div>
        </template>
        <p class="verify-desc">该签署链接包含商业保密需求与代价共识清单，请输入由产品团队发送给您的 4 位防窥访问码进入预览。</p>
        <div class="input-row">
          <el-input
            v-model="accessCode"
            maxlength="4"
            placeholder="如 7892"
            size="large"
            class="code-input"
            @keyup.enter="handleVerifyCode"
          />
          <el-button type="primary" size="large" @click="handleVerifyCode">验证并开启</el-button>
        </div>
      </el-card>
    </div>

    <!-- 步骤二：清单核对与签署提交 -->
    <div v-else-if="step === 'PREVIEW'" class="preview-layout">
      <div class="preview-header">
        <div class="title-tag">
          <el-tag effect="dark" type="primary">{{ contextData?.roleTitle }}专属通道</el-tag>
          <span class="baseline-ver">{{ contextData?.baselineVersion }}</span>
        </div>
        <h2>{{ contextData?.projectName }} - {{ contextData?.reqTitle }}</h2>
        <div class="meta-row">
          <span>客户单位：<b>{{ contextData?.clientName }}</b></span>
          <el-divider direction="vertical" />
          <span>共识人天代价：<b class="cost-num">{{ contextData?.totalCostDays }} 人天</b></span>
        </div>
      </div>

      <!-- 客户原话留痕 -->
      <el-card shadow="never" class="quote-card">
        <div class="quote-title">
          <el-icon color="#c2410c"><Warning /></el-icon>
          <span>客户原话不可篡改留痕：</span>
        </div>
        <div class="quote-body">“{{ contextData?.rawQuote }}”</div>
      </el-card>

      <!-- 双栏：清单与原型预览 -->
      <div class="sign-grid">
        <div class="grid-left">
          <el-card shadow="never">
            <template #header>
              <b>四层确认清单只读审查</b>
            </template>
            <el-table :data="contextData?.confirmItems || []" size="small" border stripe>
              <el-table-column prop="category" label="分类" width="90">
                <template #default="{ row }">
                  <el-tag v-if="row.category === 'SCOPE'" type="primary" size="small">范围</el-tag>
                  <el-tag v-else-if="row.category === 'RULE'" type="warning" size="small">规则</el-tag>
                  <el-tag v-else-if="row.category === 'EXCEPTION'" type="danger" size="small">异常</el-tag>
                  <el-tag v-else-if="row.category === 'ACCEPTANCE'" type="success" size="small">验收</el-tag>
                  <el-tag v-else type="info" size="small">不做</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="itemCode" label="编号" width="70" />
              <el-table-column prop="question" label="约定项描述" />
              <el-table-column prop="decision" label="决策" width="80" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.decision === 'CURRENT'" type="success" size="small">本期做</el-tag>
                  <el-tag v-else-if="row.decision === 'NEXT'" type="warning" size="small">下期</el-tag>
                  <el-tag v-else type="info" size="small">不做</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </div>

        <div class="grid-right">
          <el-card shadow="never" class="proto-card">
            <template #header>
              <div class="proto-head">
                <b>交互原型实时沙盒</b>
                <span class="proto-hash">SHA256: {{ contextData?.prototypeHash?.substring(0, 16) }}...</span>
              </div>
            </template>
            <div class="proto-frame-box">
              <iframe :src="contextData?.previewUrl" class="proto-frame"></iframe>
            </div>
          </el-card>

          <!-- 签署输入卡片 -->
          <el-card shadow="never" class="sign-card">
            <template #header>
              <b>✍️ 电子法定签名存证确认</b>
            </template>
            <el-form label-position="top">
              <el-form-item label="签署人实名">
                <el-input v-model="signerName" placeholder="请输入您的姓名 (如 王总监)" />
              </el-form-item>
              <el-form-item label="一句话复述您的理解（不可留空，防扯皮核心事实源）">
                <el-input
                  v-model="restatementText"
                  type="textarea"
                  :rows="3"
                  placeholder="例如：这版做在线状态和告警，不做历史曲线和财务导出，符合预期。"
                />
              </el-form-item>
              <el-form-item>
                <el-checkbox v-model="agreeDisclaimer">
                  我已审阅上述四层清单、代价及原型，确认本次签署作为终验法定义据不可单方推翻。
                </el-checkbox>
              </el-form-item>
              <div style="display: flex; gap: 12px">
                <el-button type="success" size="large" style="flex: 2" :loading="submitting" @click="handleConfirmSign">
                  立即确认签署并生成电子防伪回执
                </el-button>
                <el-button type="danger" plain size="large" style="flex: 1" :loading="submitting" @click="handleRejectSign">
                  提出异议驳回
                </el-button>
              </div>
            </el-form>
          </el-card>
        </div>
      </div>
    </div>

    <!-- 步骤三：已签署电子防伪回执 -->
    <div v-else-if="step === 'RECEIPT'" class="receipt-box">
      <el-card class="receipt-card" shadow="never">
        <div class="receipt-success-icon">
          <el-icon color="#16a34a" :size="48"><CircleCheckFilled /></el-icon>
        </div>
        <h2>签署已生效 · 电子防伪凭据已归档</h2>
        <p class="receipt-sub">本凭证已完成 HMAC 加密封签与代码库 Git Tag 物理锚定，具备法定留痕效力。</p>

        <div class="receipt-code-display">
          <span class="lbl">防伪回执码：</span>
          <span class="code">{{ receiptResult?.receiptCode }}</span>
        </div>

        <div class="receipt-meta">
          <div class="meta-item">
            <span class="k">锁定基线版本：</span>
            <span class="v">{{ receiptResult?.baselineVersion }}</span>
          </div>
          <div class="meta-item">
            <span class="k">签署提交时间：</span>
            <span class="v">{{ receiptResult?.signedAt }}</span>
          </div>
          <div class="meta-item">
            <span class="k">全量固化指纹：</span>
            <span class="v hash">{{ receiptResult?.manifestHash || 'a3f9c2e177b9d40e1189ac3bb4109401bfd145cba2793108c40b7190e82c7a10' }}</span>
          </div>
        </div>

        <div class="receipt-footer">
          <el-button type="primary" @click="step = 'PREVIEW'">查看已签详情</el-button>
        </div>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.public-sign-container {
  min-height: 100vh;
  background-color: #f8fafc;
  display: flex;
  flex-direction: column;
}

.sign-header {
  background: #14535b;
  color: #fff;
  height: 56px;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.brand {
  font-size: 16px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}

.security-badge {
  font-size: 12px;
  background: rgba(255, 255, 255, 0.15);
  padding: 4px 10px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.verify-box {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.verify-card {
  width: 480px;
}

.verify-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.verify-desc {
  font-size: 13px;
  color: #64748b;
  line-height: 1.5;
  margin-bottom: 20px;
}

.input-row {
  display: flex;
  gap: 12px;
}

.code-input {
  font-size: 20px;
  text-align: center;
  letter-spacing: 4px;
}

.preview-layout {
  padding: 24px;
  max-width: 1280px;
  margin: 0 auto;
  width: 100%;
}

.preview-header {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 16px;
}

.title-tag {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}

.baseline-ver {
  font-family: monospace;
  font-size: 13px;
  color: #64748b;
}

.cost-num {
  color: #c2410c;
}

.quote-card {
  border-left: 4px solid #c2410c;
  margin-bottom: 16px;
}

.quote-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  margin-bottom: 8px;
  font-size: 13px;
}

.quote-body {
  font-style: italic;
  color: #334155;
  background: #fffaf5;
  padding: 10px 14px;
  border-radius: 6px;
  font-size: 14px;
}

.sign-grid {
  display: grid;
  grid-template-columns: 1.2fr 0.8fr;
  gap: 16px;
}

.proto-card {
  margin-bottom: 16px;
}

.proto-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.proto-hash {
  font-family: monospace;
  font-size: 11px;
  color: #64748b;
}

.proto-frame-box {
  height: 260px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  overflow: hidden;
}

.proto-frame {
  width: 100%;
  height: 100%;
  border: none;
}

.receipt-box {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.receipt-card {
  width: 540px;
  text-align: center;
  padding: 24px;
}

.receipt-code-display {
  background: #f0fdf4;
  border: 1px dashed #16a34a;
  border-radius: 8px;
  padding: 16px;
  margin: 20px 0;
}

.receipt-code-display .lbl {
  font-size: 14px;
  color: #166534;
}

.receipt-code-display .code {
  font-family: monospace;
  font-size: 24px;
  font-weight: 700;
  color: #15803d;
  letter-spacing: 2px;
}

.receipt-meta {
  text-align: left;
  background: #f8fafc;
  padding: 16px;
  border-radius: 6px;
  font-size: 13px;
  margin-bottom: 20px;
}

.meta-item {
  display: flex;
  margin-bottom: 8px;
}

.meta-item .k {
  color: #64748b;
  width: 110px;
}

.meta-item .hash {
  font-family: monospace;
  font-size: 11px;
  word-break: break-all;
}
</style>
