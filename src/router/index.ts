import { createRouter, createWebHistory } from 'vue-router'
import WelcomeView from '../views/WelcomeView.vue'
import QuizView from '../views/QuizView.vue'
import ResultView from '../views/ResultView.vue'

/**
 * 路由配置
 * / → 欢迎页
 * /quiz → 测试页
 * /result → 结果页（支持 URL query 参数和测试流程跳转）
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
