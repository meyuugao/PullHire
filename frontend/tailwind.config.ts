import type { Config } from "tailwindcss";

// Tailwind CSS 3.4 (docs/stack.md §3).
const config: Config = {
  content: ["./app/**/*.{ts,tsx}", "./components/**/*.{ts,tsx}"],
  theme: {
    extend: {},
  },
  plugins: [],
};

export default config;
