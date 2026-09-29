/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: '#1E3A5F',
          hover: '#162C48',
          foreground: '#FFFFFF',
        },
        secondary: {
          DEFAULT: '#2563EB',
          hover: '#1D4ED8',
          foreground: '#FFFFFF',
        },
        accent: {
          DEFAULT: '#B45309',
          subtle: '#FEF3C7',
          foreground: '#FFFFFF',
        },
        surface: {
          DEFAULT: '#FFFFFF',
          hover: '#F1F5F9',
          active: '#E2E8F0',
        },
        border: {
          DEFAULT: '#E2E8F0',
          subtle: '#F1F5F9',
          strong: '#CBD5E1',
        },
        status: {
          ontrack: {
            bg: '#DCFCE7',
            text: '#166534',
            border: '#86EFAC',
          },
          watch: {
            bg: '#FEF3C7',
            text: '#92400E',
            border: '#FCD34D',
          },
          critical: {
            bg: '#FEE2E2',
            text: '#991B1B',
            border: '#FCA5A5',
          },
          info: {
            bg: '#E0F2FE',
            text: '#075985',
            border: '#7DD3FC',
          },
        },
      },
      fontFamily: {
        sans: ['Inter', '-apple-system', 'BlinkMacSystemFont', 'Segoe UI', 'sans-serif'],
        serif: ['EB Garamond', 'Georgia', 'serif'],
        mono: ['JetBrains Mono', 'SF Mono', 'ui-monospace', 'monospace'],
      },
    },
  },
  plugins: [],
};
