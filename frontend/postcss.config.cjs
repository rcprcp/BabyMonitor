// Mantine ships its own PostCSS preset; Vite picks this file up automatically.
// The breakpoint variables mirror Mantine's defaults so styles can reference
// them (see https://mantine.dev/styles/postcss-preset/).
module.exports = {
  plugins: {
    'postcss-preset-mantine': {},
    'postcss-simple-vars': {
      variables: {
        'mantine-breakpoint-xs': '36em',
        'mantine-breakpoint-sm': '48em',
        'mantine-breakpoint-md': '62em',
        'mantine-breakpoint-lg': '75em',
        'mantine-breakpoint-xl': '88em',
      },
    },
  },
}
