<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import {
  api,
  ApiError,
  difficulty,
  messageOf,
  statusLabel,
  type Problem,
  type Submission,
} from '../api'

const props = defineProps<{ submissions?: boolean }>()
const problems = ref<Problem[]>([])
const submissions = ref<Submission[]>([])
const page = ref(1)
const total = ref(0)
const retry = ref(0)
const busy = ref(false)
const error = ref('')
const loginRequired = ref(false)
const pages = computed(() => Math.max(1, Math.ceil(total.value / 20)))

watch(
  [page, retry],
  async (_, __, onCleanup) => {
    const controller = new AbortController()
    onCleanup(() => controller.abort())
    busy.value = true
    error.value = ''
    loginRequired.value = false
    try {
      if (props.submissions) {
        const data = await api.submissions(page.value, controller.signal)
        submissions.value = data.records
        total.value = data.total
      } else {
        const data = await api.problems(page.value, controller.signal)
        problems.value = data.records
        total.value = data.total
      }
    } catch (cause) {
      if (controller.signal.aborted) return
      loginRequired.value = cause instanceof ApiError && [1111, 401, 222].includes(cause.code)
      error.value = messageOf(cause)
    } finally {
      if (!controller.signal.aborted) busy.value = false
    }
  },
  { immediate: true },
)
</script>

<template>
  <div class="page-heading">
    <h1>{{ props.submissions ? '提交记录' : '题库' }}</h1>
    <span v-if="!busy && !error" class="count"
      >{{ total }} {{ props.submissions ? '条提交' : '道题目' }}</span
    >
  </div>
  <section class="panel table-panel">
    <p v-if="busy" class="table-state" role="status">加载中…</p>
    <div v-else-if="error" class="table-state">
      <p class="error" role="alert">{{ error }}</p>
      <RouterLink v-if="loginRequired" to="/login?next=/submissions" class="button"
        >登录</RouterLink
      >
      <button v-else class="quiet" @click="retry++">重试</button>
    </div>
    <p v-else-if="!total" class="table-state">
      {{ props.submissions ? '暂无提交记录。' : '暂无已发布题目。' }}
    </p>
    <template v-else>
      <table v-if="props.submissions">
        <thead>
          <tr>
            <th>提交</th>
            <th>题目</th>
            <th>结果</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in submissions" :key="item.id">
            <td>
              <RouterLink :to="'/submissions/' + item.id">#{{ item.id }}</RouterLink>
            </td>
            <td>
              <RouterLink :to="'/problems/' + item.problemId">#{{ item.problemId }}</RouterLink>
            </td>
            <td>
              <span class="tag" :class="{ accepted: item.status === 'ACCEPTED' }">{{
                statusLabel(item.status)
              }}</span>
            </td>
          </tr>
        </tbody>
      </table>
      <table v-else>
        <thead>
          <tr>
            <th>编号</th>
            <th>题目</th>
            <th>难度</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="problem in problems" :key="problem.id">
            <td class="record-id">{{ problem.id }}</td>
            <td>
              <RouterLink :to="'/problems/' + problem.id">{{ problem.title }}</RouterLink>
            </td>
            <td>
              <span class="tag" :class="'difficulty-' + problem.difficulty">{{
                difficulty(problem.difficulty)
              }}</span>
            </td>
          </tr>
        </tbody>
      </table>
      <footer class="pager">
        <span>第 {{ page }} / {{ pages }} 页</span>
        <button class="quiet" :disabled="page <= 1" @click="page--">上一页</button>
        <button class="quiet" :disabled="page >= pages" @click="page++">下一页</button>
      </footer>
    </template>
  </section>
</template>
