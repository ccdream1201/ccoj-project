<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { useUserStore } from './stores/user'
import { messageOf } from './api'

const user = useUserStore()
const router = useRouter()
const iconUrl = import.meta.env.BASE_URL + 'coj-icon.png'
const error = ref('')
onMounted(() =>
  user.refresh().catch((cause) => {
    error.value = messageOf(cause)
  }),
)
async function logout() {
  error.value = ''
  try {
    await user.logout()
    await router.push('/problems')
  } catch (cause) {
    error.value = messageOf(cause)
  }
}
</script>

<template>
  <header class="topbar">
    <div class="bar">
      <RouterLink to="/" class="brand"
        ><img :src="iconUrl" width="32" height="32" alt="" />CCOJ</RouterLink
      >
      <nav aria-label="主导航">
        <RouterLink to="/problems">题库</RouterLink>
        <RouterLink to="/submissions">提交记录</RouterLink>
      </nav>
      <div class="identity">
        <template v-if="user.user">
          <span>{{ user.user.nickname || user.user.account }}</span>
          <button class="quiet" @click="logout">退出</button>
        </template>
        <RouterLink v-else class="button" to="/login">登录</RouterLink>
      </div>
    </div>
  </header>
  <main>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <RouterView :key="$route.path" />
  </main>
</template>
