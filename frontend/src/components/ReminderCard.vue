<script setup lang="ts">
import { dateText, stageName, statusNames, type Reminder } from '../api'
defineProps<{ item: Reminder; disabled: boolean }>()
defineEmits<{ complete: []; edit: [] }>()
</script>

<template>
  <article class="reminder-card" :class="{ overdue: item.overdue }">
    <div class="reminder-date"><span :class="{ urgent: item.overdue, tomorrow: item.dayLabel === '明天', later: item.dayLabel === '后天' }">{{ item.overdue ? '已逾期' : item.dayLabel }}</span><strong>{{ item.effectiveAt.slice(11, 16) }}</strong><small>{{ item.effectiveAt.slice(0, 10) }}</small></div>
    <div class="reminder-body">
      <div class="reminder-title"><h3>{{ item.companyName }}</h3><el-tag size="small" :type="item.process.status === 'COMPLETED' ? 'success' : 'warning'">{{ statusNames[item.process.status] }}</el-tag><el-tag v-if="item.inProgress" size="small" type="primary">进行中</el-tag></div>
      <p class="reminder-position">{{ item.positionName }} · {{ item.jobLocation || '工作地点待补充' }}</p>
      <p><strong>{{ item.process.roundName || stageName(item.process.stage) }}</strong><span v-if="item.process.roundName && item.process.roundName !== stageName(item.process.stage)" class="muted">（{{ stageName(item.process.stage) }}）</span><span class="mode-label">{{ item.process.timeMode === 'DEADLINE' ? '截止任务' : '定时安排' }}</span></p>
      <p class="reminder-time">{{ item.process.timeMode === 'DEADLINE' ? '截止' : '开始' }}：{{ dateText(item.effectiveAt) }}<span v-if="item.process.endAt">　结束：{{ dateText(item.process.endAt) }}</span><span v-if="item.process.timeMode === 'DEADLINE' && item.process.startAt">　开始：{{ dateText(item.process.startAt) }}</span></p>
      <p v-if="item.process.location" class="reminder-copy">安排地点 / 链接：<a v-if="/^https?:\/\/[^\s]+$/i.test(item.process.location)" :href="item.process.location" target="_blank" rel="noopener noreferrer">{{ item.process.location }}</a><span v-else>{{ item.process.location }}</span></p>
      <p v-if="item.process.notes" class="reminder-copy">{{ item.process.notes }}</p>
      <div class="reminder-actions"><el-button v-if="item.process.status === 'PENDING'" type="primary" size="small" :disabled="disabled" @click="$emit('complete')">标记完成</el-button><el-button size="small" :disabled="disabled" @click="$emit('edit')">编辑 / 改期</el-button><RouterLink :to="{ path: '/applications', query: { applicationId: item.process.applicationId, processId: item.process.id } }">查看投递与流程 →</RouterLink></div>
    </div>
  </article>
</template>

<style scoped>
.reminder-card{display:flex;gap:24px;border:1px solid #e1e9e5;border-radius:12px;padding:22px;background:white}.reminder-card.overdue{border-left:4px solid #ca7868}.reminder-date{display:flex;flex-direction:column;align-items:center;gap:8px;min-width:95px;padding-top:2px}.reminder-date>span{font-size:12px;color:#167d70;background:#edf6f3;border-radius:6px;padding:4px 10px}.reminder-date>span.tomorrow{color:#386ba0;background:#edf3fb}.reminder-date>span.later{color:#805a9e;background:#f5effb}.reminder-date>span.urgent{color:#b35746;background:#fcf0eb}.reminder-date strong{font-size:25px;font-weight:600;letter-spacing:1px}.reminder-date small{color:#8a9791;font-size:11px}.reminder-body{flex:1;min-width:0}.reminder-title{display:flex;gap:12px;align-items:center;flex-wrap:wrap}.reminder-title h3{font-size:16px;margin:0;overflow-wrap:anywhere}.reminder-body p{font-size:13px;line-height:1.8;margin:8px 0}.reminder-position,.reminder-time{color:#788b81}.mode-label{font-size:11px;margin-left:14px;color:#7b8e83;border:1px solid #e1e9e5;padding:3px 6px;border-radius:4px}.reminder-copy{white-space:pre-wrap;overflow-wrap:anywhere}.reminder-actions{display:flex;align-items:center;flex-wrap:wrap;gap:10px;margin-top:16px}.reminder-actions .el-button{margin:0}.reminder-actions a{font-size:12px;margin-left:auto}@media(max-width:600px){.reminder-card{gap:14px;padding:16px 12px}.reminder-date{min-width:72px}.reminder-date strong{font-size:21px}.reminder-actions a{margin-left:0;width:100%}}
</style>
