<script setup lang="ts">
defineProps<{
  title: string
  value: number | string
  gradient: string
  icon: string
}>()
</script>

<template>
  <div
    class="stat-card"
    :style="{ background: `linear-gradient(135deg, ${gradient})` }"
  >
    <div class="stat-icon">
      <component :is="icon" />
    </div>
    <div class="stat-info">
      <div class="stat-title">{{ title }}</div>
      <div class="stat-value">{{ value.toLocaleString() }}</div>
    </div>
  </div>
</template>

<style scoped lang="less">
.stat-card {
  border-radius: 8px;
  padding: 20px 24px;
  display: flex;
  align-items: center;
  gap: 16px;
  color: #fff;
  transition: transform 0.3s, box-shadow 0.3s;

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
  }
}

.stat-icon {
  font-size: 32px;
  opacity: 0.9;
}

.stat-info {
  flex: 1;
}

.stat-title {
  font-size: 14px;
  opacity: 0.85;
  margin-bottom: 4px;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
}
</style>
