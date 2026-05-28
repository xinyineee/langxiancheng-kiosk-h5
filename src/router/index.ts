import { createRouter, createWebHistory } from 'vue-router'
import ResultView from '../views/ResultView.vue'

/**
 * Router configuration for the H5 landing page.
 * Only the /result route is needed — the Kiosk app navigates here via NFC URL.
 */
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/result'
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
