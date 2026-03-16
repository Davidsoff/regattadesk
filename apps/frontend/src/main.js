import { createApp } from 'vue'
import './styles/tokens.css'
import './styles/print.css'
import App from './App.vue'
import i18n, { initI18n } from './i18n'
import router from './router'

initI18n()

const app = createApp(App)
app.use(i18n)
app.use(router)
app.mount('#app')
