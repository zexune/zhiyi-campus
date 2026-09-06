<template>
  <div class="nf-page">
    <div class="card nf-card">
      <div class="nf-code">404</div>
      <p>这张布告好像被风吹走了…</p>
      <router-link to="/" class="btn btn--primary">回到交易大厅</router-link>
    </div>
  </div>
</template>

<script setup lang="ts">
// 404 兜底页
</script>

<style scoped>
.nf-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 20px;
  background: radial-gradient(circle at 18% 18%, rgba(251, 146, 60, 0.14), transparent 20rem), var(--paper);
}
.nf-card {
  padding: 64px 76px;
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 16px;
  align-items: center;
  border-color: #ddd3c2;
  border-radius: var(--r-l);
  box-shadow: var(--shadow-l);
}
.nf-code {
  font-family: var(--font-display);
  font-size: clamp(72px, 12vw, 110px);
  line-height: 1;
  color: var(--primary);
}
@media (max-width: 520px) {
  .nf-card {
    width: min(100%, 360px);
    padding: 48px 24px;
  }
}
</style>
