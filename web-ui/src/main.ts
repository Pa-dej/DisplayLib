import { createApp } from 'vue';
import App from './App.vue';
import './style.css';

if (window.matchMedia('(prefers-color-scheme: dark)').matches && localStorage.getItem('displaylib.theme') !== 'light') {
  document.documentElement.classList.add('dark');
}
if (localStorage.getItem('displaylib.theme') === 'dark') document.documentElement.classList.add('dark');

import { installSlide } from './ui/slide';

createApp(App).mount('#app');
installSlide();
