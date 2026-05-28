<template>
  <div class="action-buttons">
    <!-- Share button -->
    <button class="btn-share" @click="handleShare">
      <svg class="btn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="18" cy="5" r="3" />
        <circle cx="6" cy="12" r="3" />
        <circle cx="18" cy="19" r="3" />
        <line x1="8.59" y1="13.51" x2="15.42" y2="17.49" />
        <line x1="15.41" y1="6.51" x2="8.59" y2="10.49" />
      </svg>
      分享结果
    </button>

    <!-- CTA button -->
    <button class="btn-cta" @click="handleOrder">
      🍊 立即来杯 {{ drinkName }}
    </button>
  </div>
</template>

<script setup lang="ts">
/**
 * ActionButtons component — provides share and order CTA buttons.
 */
const props = defineProps<{
  /** Drink name for the CTA button text. */
  drinkName: string
  /** URL to share on social media. */
  shareUrl: string
}>()

/** Handles the share button click using the Web Share API or fallback. */
function handleShare() {
  if (navigator.share) {
    navigator.share({
      title: `我的创业咖啡口味是${props.drinkName}！`,
      text: `我在浪险橙测出了专属特调，快来看看你的！`,
      url: props.shareUrl
    }).catch(() => {
      copyToClipboard(props.shareUrl)
    })
  } else {
    copyToClipboard(props.shareUrl)
  }
}

/** Handles the order/CTA button click. */
function handleOrder() {
  window.open('https://cafe.langxiancheng.com', '_blank')
}

/** Copies text to clipboard and shows a brief alert. */
function copyToClipboard(text: string) {
  navigator.clipboard.writeText(text).then(() => {
    alert('链接已复制，快去分享吧！')
  }).catch(() => {
    const input = document.createElement('input')
    input.value = text
    document.body.appendChild(input)
    input.select()
    document.execCommand('copy')
    document.body.removeChild(input)
    alert('链接已复制，快去分享吧！')
  })
}
</script>

<style scoped>
.action-buttons {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 0 4px;
}

.btn-share,
.btn-cta {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 48px;
  border-radius: 24px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  border: none;
  width: 100%;
}

.btn-share {
  background: #FFFFFF;
  color: #FF6B1A;
  border: 2px solid #FF6B1A;
}

.btn-share:active {
  background: #FFF8F0;
  transform: scale(0.97);
}

.btn-cta {
  background: #FF6B1A;
  color: #FFFFFF;
}

.btn-cta:active {
  background: #CC5500;
  transform: scale(0.97);
}

.btn-icon {
  width: 20px;
  height: 20px;
}
</style>
