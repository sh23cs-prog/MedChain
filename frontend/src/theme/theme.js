import { createTheme } from '@mui/material/styles';

export const getTheme = (mode = 'light') =>
  createTheme({
    palette: {
      mode,
      primary: {
        main: '#1976D2',
        light: '#42a5f5',
        dark: '#1565c0',
        contrastText: '#ffffff',
      },
      secondary: {
        main: '#26A69A',
        light: '#4db6ac',
        dark: '#00897b',
        contrastText: '#ffffff',
      },
      background: {
        default: mode === 'light' ? '#F5F9FC' : '#0B132B',
        paper: mode === 'light' ? '#FFFFFF' : '#1C2541',
      },
      text: {
        primary: mode === 'light' ? '#1E293B' : '#F8FAFC',
        secondary: mode === 'light' ? '#64748B' : '#94A3B8',
      },
      info: {
        main: '#0284C7',
      },
      success: {
        main: '#10B981',
      },
      warning: {
        main: '#F59E0B',
      },
      error: {
        main: '#EF4444',
      },
    },
    typography: {
      fontFamily: '"Inter", "Roboto", "Helvetica", "Arial", sans-serif',
      h1: { fontWeight: 700, fontSize: '2.5rem' },
      h2: { fontWeight: 700, fontSize: '2rem' },
      h3: { fontWeight: 600, fontSize: '1.75rem' },
      h4: { fontWeight: 600, fontSize: '1.5rem' },
      h5: { fontWeight: 600, fontSize: '1.25rem' },
      h6: { fontWeight: 600, fontSize: '1rem' },
      subtitle1: { fontWeight: 500 },
      button: { textTransform: 'none', fontWeight: 600 },
    },
    shape: {
      borderRadius: 12,
    },
    components: {
      MuiCard: {
        styleOverrides: {
          root: {
            borderRadius: 12,
            boxShadow: mode === 'light' 
              ? '0 4px 20px 0 rgba(0,0,0,0.05)' 
              : '0 4px 20px 0 rgba(0,0,0,0.3)',
            backgroundImage: 'none',
          },
        },
      },
      MuiButton: {
        styleOverrides: {
          root: {
            borderRadius: 8,
            padding: '8px 20px',
            boxShadow: 'none',
            '&:hover': {
              boxShadow: '0 4px 12px rgba(25, 118, 210, 0.2)',
            },
          },
        },
      },
      MuiPaper: {
        styleOverrides: {
          root: {
            backgroundImage: 'none',
          },
        },
      },
      MuiChip: {
        styleOverrides: {
          root: {
            fontWeight: 600,
            borderRadius: 6,
          },
        },
      },
    },
  });
