<script setup>
import DefaultTheme from 'vitepress/theme'
import { onMounted, watch, nextTick } from 'vue'
import { useRoute } from 'vitepress'
import mermaid from 'mermaid'

const { Layout } = DefaultTheme
const route = useRoute()

mermaid.initialize({ startOnLoad: false })

async function renderMermaid() {
  await nextTick()
  const blocks = document.querySelectorAll('pre code.language-mermaid')
  blocks.forEach(async (block) => {
    const pre = block.parentElement
    if (!pre) return
    const code = block.textContent.trim()
    const id = 'mmd-' + Math.random().toString(36).slice(2)
    const div = document.createElement('div')
    div.id = id
    div.className = 'mermaid'
    pre.replaceWith(div)
    try {
      const { svg } = await mermaid.render(id, code)
      div.innerHTML = svg
    } catch (e) {
      div.textContent = code
    }
  })
}

onMounted(() => {
  renderMermaid()
  watch(() => route.path, renderMermaid)
})
</script>

<template>
  <Layout><slot /></Layout>
</template>