<template>
  <div class="nf-page">
    <div class="card nf-card sticker-tilt">
      <div class="nf-code">404</div>
      <p>这张布告好像被风吹走了…</p>
      <router-link to="/" class="btn btn--primary">回到交易大厅</router-link>
    </div>
  </div>
</template>

<script setup>
// 404 兜底页
</script>

<style scoped>
.nf-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 20px;
}
.nf-card {
  padding: 56px 72px;
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 16px;
  align-items: center;
}
.nf-code {
  font-family: var(--font-display);
  font-size: 84px;
  line-height: 1;
  color: var(--primary);
}
</style>
