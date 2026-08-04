/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx,ts,tsx}'],
  theme: {
    extend: {
      colors: {
        forest: {
          DEFAULT: '#1B3B36',
          light: '#2A5049',
        },
        honey: '#F2A93B',
        paper: '#FBF6EC',
        coral: '#E8563F',
      },
      fontFamily: {
        heading: ['GmarketSans', 'Pretendard', 'sans-serif'],
        body: ['Pretendard', 'GmarketSans', 'sans-serif'],
      },
    },
  },
  plugins: [],
}
