<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { getServiceMonitorData } from '~/api/server'

interface SysInfo {
  cpu: { cpuNum: number; used: number; sys: number; free: number }
  mem: { total: number; used: number; free: number }
  jvm: { total: number; free: number }
  sys: { computerName: string; computerIp: string }
  sysFiles: { usage: number }[]
}

const serverInfo = ref<SysInfo>()
let timer: ReturnType<typeof setInterval> | undefined

async function fetchData() {
  const { data } = await getServiceMonitorData()
  serverInfo.value = data
}

onMounted(() => {
  fetchData()
  timer = setInterval(fetchData, 30000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

function memUsage(): string {
  if (!serverInfo.value) return '0'
  const m = serverInfo.value.mem
  return ((m.used / m.total) * 100).toFixed(1)
}

function jvmUsage(): string {
  if (!serverInfo.value) return '0'
  const j = serverInfo.value.jvm
  return (((j.total - j.free) / j.total) * 100).toFixed(1)
}

function diskUsage(): string {
  if (!serverInfo.value?.sysFiles?.length) return '0'
  const max = Math.max(...serverInfo.value.sysFiles.map(f => f.usage))
  return max.toFixed(1)
}
</script>

<template>
  <div class="monitor-card">
    <div class="monitor-title">
      <DashboardOutlined /> 系统状态
    </div>
    <div class="monitor-body">
      <template v-if="serverInfo">
        <div class="monitor-item">
          <div class="item-label">
            <span>CPU</span>
            <span class="item-value">{{ serverInfo.cpu.used }}%</span>
          </div>
          <a-progress :percent="serverInfo.cpu.used" :show-info="false" size="small" stroke-color="#52c41a" />
        </div>
        <div class="monitor-item">
          <div class="item-label">
            <span>内存</span>
            <span class="item-value">{{ memUsage() }}%</span>
          </div>
          <a-progress :percent="Number(memUsage())" :show-info="false" size="small" stroke-color="#faad14" />
        </div>
        <div class="monitor-item">
          <div class="item-label">
            <span>JVM</span>
            <span class="item-value">{{ jvmUsage() }}%</span>
          </div>
          <a-progress :percent="Number(jvmUsage())" :show-info="false" size="small" stroke-color="#1677ff" />
        </div>
        <div class="monitor-item">
          <div class="item-label">
            <span>磁盘</span>
            <span class="item-value">{{ diskUsage() }}%</span>
          </div>
          <a-progress :percent="Number(diskUsage())" :show-info="false" size="small" stroke-color="#ff4d4f" />
        </div>
        <div class="monitor-footer">
          <div class="server-info">服务器: {{ serverInfo.sys.computerName }}</div>
          <div class="server-info">IP: {{ serverInfo.sys.computerIp }}</div>
        </div>
      </template>
      <a-skeleton v-else :paragraph="{ rows: 4 }" />
    </div>
  </div>
</template>

<style scoped lang="less">
.monitor-card {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
  padding: 16px;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.monitor-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 20px;
  flex-shrink: 0;
}

.monitor-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
  flex: 1;
  justify-content: space-evenly;
}

.monitor-item {
  .item-label {
    display: flex;
    justify-content: space-between;
    margin-bottom: 6px;
    font-size: 13px;
    color: #333;
  }
  .item-value {
    font-weight: 600;
    color: #666;
  }
}

.monitor-footer {
  margin-top: 8px;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;

  .server-info {
    font-size: 12px;
    color: #999;
    line-height: 1.8;
  }
}
</style>
