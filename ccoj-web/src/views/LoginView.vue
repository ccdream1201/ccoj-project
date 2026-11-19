<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, messageOf } from '../api'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const user = useUserStore()
const account = ref('')
const password = ref('')
const confirmPassword = ref('')
const register = ref(false)
const busy = ref(false)
const error = ref('')
const notice = ref('')

function switchMode() {
  register.value = !register.value
  error.value = ''
  notice.value = ''
  password.value = ''
  confirmPassword.value = ''
}

async function send() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    if (register.value) {
      if (password.value !== confirmPassword.value) throw new Error('两次输入的密码不一致')
      await api.register(account.value.trim(), password.value, confirmPassword.value)
      switchMode()
      notice.value = '注册成功，请登录。'
      return
    }
    await api.login(account.value.trim(), password.value)
    await user.refresh()
    if (!user.user) throw new Error('登录后未能读取会话，请检查 Cookie 和网关代理。')
    const next = route.query.next
    await router.replace(
      typeof next === 'string' && /^\/(problems|submissions)(\/\d+)?$/.test(next)
        ? next
        : '/problems',
    )
  } catch (cause) {
    error.value = messageOf(cause)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="panel auth">
    <h1>{{ register ? '创建账号' : '登录 CCOJ' }}</h1>
    <form class="stack" @submit.prevent="send">
      <label>账号<input v-model="account" required autocomplete="username" /></label>
      <label
        >密码<input
          v-model="password"
          type="password"
          required
          :autocomplete="register ? 'new-password' : 'current-password'"
      /></label>
      <label v-if="register"
        >确认密码<input
          v-model="confirmPassword"
          type="password"
          required
          autocomplete="new-password"
      /></label>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <p v-if="notice" role="status">{{ notice }}</p>
      <button class="button" :disabled="busy">
        {{ busy ? '处理中…' : register ? '注册' : '登录' }}
      </button>
      <button type="button" class="quiet" :disabled="busy" @click="switchMode">
        {{ register ? '返回登录' : '创建新账号' }}
      </button>
    </form>
  </section>
</template>
