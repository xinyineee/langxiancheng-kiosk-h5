<template>
  <div class="action-buttons">
    <!-- Share label -->
    <p class="share-label">分享你的前行饮品</p>

    <!-- Social share buttons row -->
    <div class="share-row">
      <!-- 微信 — copy link -->
      <button class="btn-social btn-wechat" @click="handleWechat">
        <div class="icon-circle">
          <svg class="social-icon" viewBox="0 0 24 24" fill="currentColor">
            <path d="M8.691 2.188C3.891 2.188 0 5.476 0 9.53c0 2.212 1.17 4.203 3.002 5.55a.59.59 0 0 1 .213.665l-.39 1.48c-.019.07-.048.141-.048.213 0 .163.13.295.29.295a.326.326 0 0 0 .167-.054l1.903-1.114a.864.864 0 0 1 .717-.098 10.16 10.16 0 0 0 2.837.403c.276 0 .543-.027.811-.05-.857-2.578.157-4.972 1.932-6.446 1.703-1.415 3.882-1.98 5.853-1.838-.576-3.583-4.196-6.348-8.596-6.348zM5.785 5.991c.642 0 1.162.529 1.162 1.18a1.17 1.17 0 0 1-1.162 1.178A1.17 1.17 0 0 1 4.623 7.17c0-.651.52-1.18 1.162-1.18zm5.813 0c.642 0 1.162.529 1.162 1.18a1.17 1.17 0 0 1-1.162 1.178 1.17 1.17 0 0 1-1.162-1.178c0-.651.52-1.18 1.162-1.18zm3.68 4.025c-3.837 0-6.953 2.708-6.953 6.048 0 3.342 3.116 6.048 6.953 6.048.778 0 1.533-.118 2.245-.338a.72.72 0 0 1 .588.083l1.535.904a.263.263 0 0 0 .136.043.236.236 0 0 0 .233-.237c0-.058-.023-.115-.038-.172l-.316-1.2a.478.478 0 0 1 .174-.544C22.082 19.63 23.107 17.948 23.107 16.064c0-3.34-3.116-6.048-6.829-6.048zm-2.24 3.288c.523 0 .946.431.946.962a.954.954 0 0 1-.946.962.954.954 0 0 1-.946-.962c0-.531.423-.962.946-.962zm4.48 0c.524 0 .947.431.947.962a.954.954 0 0 1-.947.962.954.954 0 0 1-.946-.962c0-.531.423-.962.946-.962z"/>
          </svg>
        </div>
        <span class="btn-label">微信</span>
      </button>

      <!-- 保存图片 -->
      <button class="btn-social btn-save" @click="handleSaveImage">
        <div class="icon-circle">
          <svg class="social-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="7 10 12 15 17 10"/>
            <line x1="12" y1="15" x2="12" y2="3"/>
          </svg>
        </div>
        <span class="btn-label">保存图片</span>
      </button>
    </div>

    <!-- Copied toast -->
    <div v-if="showCopied" class="toast">已复制链接，去微信粘贴分享吧！</div>
    <div v-if="showSaved" class="toast">图片已保存到相册！</div>
    <div v-if="showError" class="toast error">保存失败，请重试</div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import html2canvas from 'html2canvas'

/**
 * ActionButtons component — WeChat share (copy link) + Save image.
 */
const props = defineProps<{
  /** Drink name for share text. */
  drinkName: string
  /** URL to share. */
  shareUrl: string
  /** DOM element ref to capture for save-image. */
  captureEl: HTMLElement | null
}>()

const showCopied = ref(false)
const showSaved = ref(false)
const showError = ref(false)

/** 微信分享: 复制链接 */
function handleWechat() {
  const text = `我在浪险橙测出了专属特调「${props.drinkName}」，快来看看你的！\n${props.shareUrl}`
  copyText(text)
  showCopied.value = true
  setTimeout(() => { showCopied.value = false }, 2000)
}

/** 保存图片: 将结果卡转成图片下载 */
async function handleSaveImage() {
  if (!props.captureEl) {
    showError.value = true
    setTimeout(() => { showError.value = false }, 2000)
    return
  }
  try {
    const canvas = await html2canvas(props.captureEl, {
      backgroundColor: '#FFF8F0',
      scale: 2,
      useCORS: true,
      logging: false
    })
    const link = document.createElement('a')
    link.download = `浪险橙-${props.drinkName}.png`
    link.href = canvas.toDataURL('image/png')
    link.click()
    showSaved.value = true
    setTimeout(() => { showSaved.value = false }, 2000)
  } catch {
    showError.value = true
    setTimeout(() => { showError.value = false }, 2000)
  }
}

/** Copies text to clipboard. */
function copyText(text: string) {
  navigator.clipboard.writeText(text).catch(() => {
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
  align-items: center;
  gap: 16px;
  padding: 0 4px;
}

.share-label {
  font-size: 14px;
  color: #999;
  margin: 0;
}

/* Social share row */
.share-row {
  display: flex;
  gap: 32px;
  justify-content: center;
}

.btn-social {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  background: none;
  border: none;
  cursor: pointer;
  padding: 0;
}

.btn-social:active {
  transform: scale(0.92);
}

.icon-circle {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
}

.btn-wechat .icon-circle {
  background: #07C160;
}

.btn-save .icon-circle {
  background: #1A1A1A;
}

.social-icon {
  width: 26px;
  height: 26px;
  color: #FFFFFF;
}

.btn-label {
  font-size: 13px;
  color: #666;
  font-weight: 500;
}

/* Toast */
.toast {
  position: fixed;
  bottom: 80px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(0, 0, 0, 0.75);
  color: #fff;
  padding: 10px 20px;
  border-radius: 20px;
  font-size: 14px;
  z-index: 1000;
  animation: toast-in 0.3s ease;
}

.toast.error {
  background: rgba(200, 50, 50, 0.85);
}

@keyframes toast-in {
  from { opacity: 0; transform: translateX(-50%) translateY(10px); }
  to { opacity: 1; transform: translateX(-50%) translateY(0); }
}
</style>
