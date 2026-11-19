<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { api, ApiError, isJudging, messageOf, statusLabel, type Submission } from '../api'

const props = defineProps<{ id: string }>()
const controller = new AbortController()
const submission = ref<Submission>()
const error = ref('')
const loginRequired = ref(false)
let timer: ReturnType<typeof setTimeout> | undefined

async function refresh() {
  error.value = ''
  try {
    submission.value = await api.submission(props.id, controller.signal)
    if (!controller.signal.aborted && isJudging(submission.value.status)) {
      timer = setTimeout(refresh, 1500)
    }
  } catch (cause) {
    if (controller.signal.aborted) return
    loginRequired.value = cause instanceof ApiError && [1111, 401, 222].includes(cause.code)
    error.value = messageOf(cause)
  }
}
onMounted(refresh)
onUnmounted(() => {
  controller.abort()
  clearTimeout(timer)
})
</script>

<template>
  <RouterLink to="/submissions" class="back">← 返回提交记录</RouterLink>
  <section class="panel result-panel">
    <h1>提交 #{{ id }}</h1>
    <template v-if="error">
      <p class="error" role="alert">{{ error }}</p>
      <RouterLink
        v-if="loginRequired"
        :to="{ path: '/login', query: { next: '/submissions/' + id } }"
        class="button"
        >登录</RouterLink
      >
      <button v-else class="quiet" @click="refresh">重新查询</button>
    </template>
    <p v-else-if="!submission" role="status">加载结果…</p>
    <template v-else>
      <p class="verdict" role="status">
        <strong class="tag" :class="{ accepted: submission.status === 'ACCEPTED' }">{{
          statusLabel(submission.status)
        }}</strong>
      </p>
      <p v-if="submission.totalCaseCount != null">
        通过用例 {{ submission.passedCaseCount ?? 0 }} / {{ submission.totalCaseCount }}
      </p>
      <template v-if="submission.compileOutput"
        ><h2>编译输出</h2>
        <pre>{{ submission.compileOutput }}</pre>
      </template>
      <RouterLink :to="'/problems/' + submission.problemId">返回题目</RouterLink>
    </template>
  </section>
</template>
