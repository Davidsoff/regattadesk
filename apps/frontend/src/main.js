import { createApp } from 'vue'
import './styles/tokens.css'
import './styles/print.css'
import App from './App.vue'
import i18n from './i18n'

const app = createApp(App)
app.use(i18n)
app.mount('#app')
