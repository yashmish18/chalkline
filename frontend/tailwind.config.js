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
          DEFAULT: '#5E6AD2',
          hover: '#4F5AC2',
          light: '#EEF0FF',
          foreground: '#FFFFFF',
        },
        secondary: {
          DEFAULT: '#475569',
          hover: '#334155',
          foreground: '#FFFFFF',
        },
        accent: {
          DEFAULT: '#F59E0B',
          subtle: '#FEF3C7',
          foreground: '#FFFFFF',
        },
        linear: {
          bg: '#0F1011',
          surface: '#141516',
          panel: '#18191A',
          hairline: '#23252A',
          hairlineStrong: '#34343A',
          ink: '#F7F8F8',
          inkMuted: '#D0D6E0',
          inkSubtle: '#8A8F98',
          purple: '#5E6AD2',
          purpleHover: '#828FFF',
        },
        surface: {
          DEFAULT: '#FFFFFF',
          hover: '#F8F9FA',
          active: '#F1F3F5',
        },
        border: {
          DEFAULT: '#E5E7EB',
          subtle: '#F3F4F6',
          strong: '#D1D5DB',
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
            bg: '#EEF2FF',
            text: '#3730A3',
            border: '#C7D2FE',
          },
        },
      },
      fontFamily: {
        sans: ['Inter', '-apple-system', 'BlinkMacSystemFont', 'Segoe UI', 'Roboto', 'sans-serif'],
        serif: ['EB Garamond', 'Georgia', 'serif'],
        mono: ['JetBrains Mono', 'SF Mono', 'ui-monospace', 'monospace'],
      },
    },
  },
  plugins: [],
};
