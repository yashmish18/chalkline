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
          DEFAULT: '#16181D',
          hover: '#2A2D33',
          light: '#FAFAF7',
          foreground: '#FFFFFF',
        },
        secondary: {
          DEFAULT: '#334155',
          hover: '#1E293B',
          foreground: '#FFFFFF',
        },
        accent: {
          DEFAULT: '#0F5C5C',
          hover: '#0B4747',
          subtle: '#EAF1F0',
          foreground: '#FFFFFF',
        },
        surface: {
          DEFAULT: '#FFFFFF',
          hover: '#F6F5F1',
          active: '#EFEEE9',
        },
        border: {
          DEFAULT: '#E6E4DD',
          subtle: '#EFEEE9',
          strong: '#D6D3CA',
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
            bg: '#EFF6FF',
            text: '#1D4ED8',
            border: '#BFDBFE',
          },
        },
      },
      fontFamily: {
        sans: ['Inter', '-apple-system', 'BlinkMacSystemFont', 'Segoe UI', 'Roboto', 'sans-serif'],
        serif: ['Newsreader', 'Iowan Old Style', 'Georgia', 'serif'],
        mono: ['JetBrains Mono', 'SF Mono', 'ui-monospace', 'monospace'],
      },
    },
  },
  plugins: [],
};
