import { createRouter, createWebHistory } from 'vue-router'
import ListView from '../views/ListView.vue'

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/problems' },
    { path: '/problems', component: ListView },
    {
      path: '/problems/:id(\\d+)',
      component: () => import('../views/ProblemView.vue'),
      props: true,
    },
    { path: '/submissions', component: ListView, props: { submissions: true } },
    {
      path: '/submissions/:id(\\d+)',
      component: () => import('../views/SubmissionView.vue'),
      props: true,
    },
    { path: '/login', component: () => import('../views/LoginView.vue') },
    { path: '/:pathMatch(.*)*', redirect: '/problems' },
  ],
})
