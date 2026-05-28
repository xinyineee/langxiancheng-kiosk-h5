/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

/** spark-md5 类型声明 */
declare module 'spark-md5' {
  const SparkMD5: {
    /** 计算字符串的 MD5 哈希值 */
    hash(str: string, raw?: boolean): string
    /** 增量式 MD5 计算类 */
    new (): {
      append(str: string): void
      end(raw?: boolean): string
      reset(): void
      getState(): { buff: Uint8Array; length: number; hash: number[] }
      setState(state: { buff: Uint8Array; length: number; hash: number[] }): void
      destroy(): void
    }
  }
  export default SparkMD5
}
