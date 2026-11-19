<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { api, ApiError, difficulty, messageOf, type Problem } from '../api'
import { useUserStore } from '../stores/user'

const props = defineProps<{ id: string }>()
const user = useUserStore()
const router = useRouter()
const problem = ref<Problem>()
const controller = new AbortController()
const error = ref('')
const submitError = ref('')
const sending = ref(false)
const code = ref(
  'public class Main {\n    public static void main(String[] args) {\n        \n    }\n}\n',
)

async function load() {
  error.value = ''
  try {
    problem.value = (await api.problem(props.id, controller.signal)).problem
  } catch (cause) {
    if (!controller.signal.aborted) error.value = messageOf(cause)
  }
}
onMounted(load)
onUnmounted(() => controller.abort())

async function submit() {
  if (!problem.value || sending.value || !code.value.trim()) return
  sending.value = true
  submitError.value = ''
  try {
    const id = await api.submit(problem.value.id, code.value)
    if (!controller.signal.aborted) await router.push('/submissions/' + id)
  } catch (cause) {
    if (cause instanceof ApiError && [1111, 401, 222].includes(cause.code)) user.user = null
    submitError.value = messageOf(cause)
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <RouterLink to="/problems" class="back">← 返回题库</RouterLink>
  <div v-if="error">
    <p class="error" role="alert">{{ error }}</p>
    <button class="quiet" @click="load">重试</button>
  </div>
  <p v-else-if="!problem" role="status">加载题目…</p>
  <div v-else class="split">
    <section class="panel statement">
      <h1>{{ problem.title }}</h1>
      <span class="tag" :class="'difficulty-' + problem.difficulty">{{
        difficulty(problem.difficulty)
      }}</span>
      <p class="description">{{ problem.description }}</p>
      <template v-for="(sample, index) in problem.samples" :key="index">
        <h2>样例 {{ index + 1 }}</h2>
        <div class="samples">
          <div>
            输入
            <pre>{{ sample.input }}</pre>
          </div>
          <div>
            输出
            <pre>{{ sample.output }}</pre>
          </div>
        </div>
      </template>
    </section>
    <form class="panel editor" @submit.prevent="submit">
      <div class="editor-toolbar">
        <label for="source-code">源代码</label><span>Java · Main.java</span>
      </div>
      <textarea id="source-code" v-model="code" rows="18" spellcheck="false" maxlength="65536" />
      <div class="editor-actions">
        <p v-if="submitError" class="error" role="alert">{{ submitError }}</p>
        <button v-if="user.user" class="button" :disabled="sending || !code.trim()">
          {{ sending ? '提交中…' : '提交判题' }}
        </button>
        <RouterLink
          v-else
          :to="{ path: '/login', query: { next: '/problems/' + id } }"
          class="button"
          >登录后提交</RouterLink
        >
      </div>
    </form>
  </div>
</template>
