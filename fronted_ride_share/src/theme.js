import { createTheme } from '@mui/material/styles'

const baseColor = '#0F172A'
const brandMint = '#E8F8F5'

const theme = createTheme({
  palette: {
    mode: 'light',
    primary: {
      main: '#0F8B8D',
      contrastText: '#ffffff',
    },
    secondary: {
      main: '#F25F4C',
    },
    background: {
      default: brandMint,
      paper: '#ffffff',
    },
    text: {
      primary: baseColor,
      secondary: 'rgba(15, 23, 42, 0.7)',
    },
    success: {
      main: '#3BBA9C',
    },
    warning: {
      main: '#F6BD60',
    },
    error: {
      main: '#D9534F',
    },
  },
  typography: {
    fontFamily: 'Outfit, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
    h1: { fontWeight: 700 },
    h2: { fontWeight: 600 },
    h3: { fontWeight: 600 },
    h4: { fontWeight: 600 },
    h5: { fontWeight: 600 },
    h6: { fontWeight: 600 },
    button: {
      textTransform: 'none',
      fontWeight: 600,
    },
  },
  components: {
    MuiPaper: {
      styleOverrides: {
        root: {
          borderRadius: 16,
          boxShadow: '0 20px 45px rgba(15, 23, 42, 0.08)',
        },
      },
    },
    MuiButton: {
      styleOverrides: {
        root: {
          borderRadius: 999,
          paddingInline: 24,
        },
      },
    },
    MuiAppBar: {
      styleOverrides: {
        colorPrimary: {
          backgroundColor: '#ffffff',
          color: baseColor,
        },
      },
    },
  },
})

export default theme

