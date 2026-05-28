import type { Config } from 'tailwindcss'

const config: Config = {
  content: [
    './index.html',
    './src/**/*.{vue,js,ts,jsx,tsx}'
  ],
  theme: {
    extend: {
      colors: {
        'brand-orange': '#FF6B1A',
        'brand-orange-light': '#FF9A5C',
        'brand-orange-dark': '#CC5500',
        'brand-bg': '#FFF8F0',
        'brand-surface': '#FFFFFF',
        'brand-text': '#1A1A1A',
        'brand-text-secondary': '#666666',
        'brand-text-hint': '#999999',
        'brand-success': '#4CAF50',
        'brand-error': '#F44336'
      },
      fontFamily: {
        sans: ['-apple-system', '"PingFang SC"', '"Helvetica Neue"', 'Arial', 'sans-serif']
      },
      borderRadius: {
        'brand': '16px',
        'brand-btn': '24px'
      },
      maxWidth: {
        'brand': '428px'
      },
      spacing: {
        'brand-margin': '20px'
      }
    }
  },
  plugins: []
}

export default config
