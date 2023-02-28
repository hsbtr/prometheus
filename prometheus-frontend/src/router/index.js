import Vue from 'vue'
import Router from 'vue-router'
import HelloWorld from "../components/HelloWorld.vue";
import Prometheus from "../views/Prometheus.vue";

Vue.use(Router)

export default new Router({
  routes: [
    {
      path: '/',
      name: 'HelloWorld',
      component: HelloWorld
    },
    {
        path: '/prometheus',
        name: 'prometheus',
        component: Prometheus
    }
  ]
})
