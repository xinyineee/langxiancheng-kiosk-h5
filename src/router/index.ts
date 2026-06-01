import { createRouter, createWebHistory } from 'vue-router'
import WelcomeView from '../views/WelcomeView.vue'
import QuizView from '../views/QuizView.vue'
import ResultView from '../views/ResultView.vue'

/**
 * 路由配置
 * / → 欢迎页
 * /quiz → 测试页
 * /result/:id → 结果页（路径参数，NFC 友好，避免 query string 编码问题）
 * /result → 结果页（兼容旧 query 参数格式）
 */
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'welcome',
      component: WelcomeView
    },
    {
      path: '/quiz',
      name: 'quiz',
      component: QuizView
    },
    {
      // New format: /result/D1 (path parameter — NFC safe, no ? and & encoding issues)
      path: '/result/:id',
      name: 'result-path',
      component: ResultView,
      props: (route) => ({
        drinkId: route.params.id as string || '',
        scoreHash: '',
        timestamp: ''
      })
    },
    {
      // Legacy format: /result?d=D1 (query parameter — kept for backward compat)
      path: '/result',
      name: 'result',
      component: ResultView,
      props: (route) => ({
        drinkId: route.query.d as string || '',
        scoreHash: route.query.s as string || '',
        timestamp: route.query.t as string || ''
      })
    }
  ]
})

export default router
