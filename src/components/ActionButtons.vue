<template>
  <div class="action-buttons">
    <!-- Social share buttons row -->
    <div class="share-row">
      <button class="btn-social btn-xhs" @click="handleXiaohongshu">
        <svg class="social-icon" viewBox="0 0 24 24" fill="currentColor">
          <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm4.64 13.2c-.24.36-.72.48-1.08.24l-3.12-1.92-2.16 1.56c-.12.12-.36.12-.48.12-.24 0-.36-.12-.48-.24-.24-.36-.12-.84.24-1.08l2.52-1.8-2.52-1.8c-.36-.24-.48-.72-.24-1.08.24-.36.72-.48 1.08-.24l3.12 1.92 2.16-1.56c.36-.24.84-.12 1.08.24.24.36.12.84-.24 1.08l-2.52 1.8 2.52 1.8c.36.24.48.72.24 1.08z"/>
        </svg>
        小红书
      </button>
      <button class="btn-social btn-wechat" @click="handleWechat">
        <svg class="social-icon" viewBox="0 0 24 24" fill="currentColor">
          <path d="M8.691 2.188C3.891 2.188 0 5.476 0 9.53c0 2.212 1.17 4.203 3.002 5.55a.59.59 0 0 1 .213.665l-.39 1.48c-.019.07-.048.141-.048.213 0 .163.13.295.29.295a.326.326 0 0 0 .167-.054l1.903-1.114a.864.864 0 0 1 .717-.098 10.16 10.16 0 0 0 2.837.403c.276 0 .543-.027.811-.05-.857-2.578.157-4.972 1.932-6.446 1.703-1.415 3.882-1.98 5.853-1.838-.576-3.583-4.196-6.348-8.596-6.348zM5.785 5.991c.642 0 1.162.529 1.162 1.18a1.17 1.17 0 0 1-1.162 1.178A1.17 1.17 0 0 1 4.623 7.17c0-.651.52-1.18 1.162-1.18zm5.813 0c.642 0 1.162.529 1.162 1.18a1.17 1.17 0 0 1-1.162 1.178 1.17 1.17 0 0 1-1.162-1.178c0-.651.52-1.18 1.162-1.18zm3.68 4.025c-3.837 0-6.953 2.708-6.953 6.048 0 3.342 3.116 6.048 6.953 6.048.778 0 1.533-.118 2.245-.338a.72.72 0 0 1 .588.083l1.535.904a.263.263 0 0 0 .136.043.236.236 0 0 0 .233-.237c0-.058-.023-.115-.038-.172l-.316-1.2a.478.478 0 0 1 .174-.544C22.082 19.63 23.107 17.948 23.107 16.064c0-3.34-3.116-6.048-6.829-6.048zm-2.24 3.288c.523 0 .946.431.946.962a.954.954 0 0 1-.946.962.954.954 0 0 1-.946-.962c0-.531.423-.962.946-.962zm4.48 0c.524 0 .947.431.947.962a.954.954 0 0 1-.947.962.954.954 0 0 1-.946-.962c0-.531.423-.962.946-.962z"/>
        </svg>
        微信
      </button>
      <button class="btn-social btn-copy" @click="handleCopyLink">
        <svg class="social-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
          <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
        </svg>
        复制
      </button>
    </div>

    <!-- CTA button -->
    <button class="btn-cta" @click="handleOrder">
      🍊 立即来杯 {{ drinkName }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

/**
 * ActionButtons component — provides social share buttons (小红书, 微信, 复制) and order CTA.
 */
const props = defineProps<{
  /** Drink name for the CTA button text and share text. */
  drinkName: string
  /** URL to share on social media. */
  shareUrl: string
}>()

const showCopied = ref(false)

/** Share text for social media. */
const shareText = `我在浪险橙测出了专属特调「${props.drinkName}」，快来看看你的！`

/** 小红书分享: 复制文案+链接，提示用户打开小红书粘贴 */
function handleXiaohongshu() {
  const text = `${shareText}\n${props.shareUrl}`
  copyText(text)
  showCopied.value = true
  setTimeout(() => { showCopied.value = false }, 2000)
}

/** 微信分享: 使用 Web Share API 或复制链接 */
function handleWechat() {
  if (navigator.share) {
    navigator.share({
      title: `我的创业咖啡口味是${props.drinkName}！`,
      text: shareText,
      url: props.shareUrl
    }).catch(() => {
      copyText(props.shareUrl)
    })
  } else {
    copyText(props.shareUrl)
  }
}

/** 复制链接 */
function handleCopyLink() {
  copyText(props.shareUrl)
  showCopied.value = true
  setTimeout(() => { showCopied.value = false }, 2000)
}

/** Handles the order/CTA button click. */
function handleOrder() {
  window.open('https://xinyineee.github.io/langxiancheng-kiosk-h5/', '_blank')
}

/** Copies text to clipboard. */
function copyText(text: string) {
  navigator.clipboard.writeText(text).then(() => {
    // success
  }).catch(() => {
    const input = document.createElement('input')
    input.value = text
    document.body.appendChild(input)
    input.select()
    document.execCommand('copy')
    document.body.removeChild(input)
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

/* Social share row */
.share-row {
  display: flex;
  gap: 10px;
}

.btn-social {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 12px 8px;
  border-radius: 16px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  border: 1.5px solid transparent;
  background: #FFFFFF;
  color: #333;
}

.btn-social:active {
  transform: scale(0.95);
}

/* 小红书 — red theme */
.btn-xhs {
  border-color: #FF2442;
  color: #FF2442;
  background: #FFF0F2;
}
.btn-xhs:active {
  background: #FFD6DB;
}

/* 微信 — green theme */
.btn-wechat {
  border-color: #07C160;
  color: #07C160;
  background: #F0FFF5;
}
.btn-wechat:active {
  background: #D4F5E0;
}

/* 复制 — neutral */
.btn-copy {
  border-color: #999;
  color: #666;
  background: #F5F5F5;
}
.btn-copy:active {
  background: #E8E8E8;
}

.social-icon {
  width: 22px;
  height: 22px;
}

/* CTA button */
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
  background: #FF6B1A;
  color: #FFFFFF;
}

.btn-cta:active {
  background: #CC5500;
  transform: scale(0.97);
}
</style>
