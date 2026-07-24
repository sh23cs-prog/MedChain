import React, { createContext, useContext, useState, useMemo } from 'react';
import { ThemeProvider as MuiThemeProvider } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import { getTheme } from '../theme/theme';

const ThemeContext = createContext({
  mode: 'light',
  toggleTheme: () => {},
});

export const useAppTheme = () => useContext(ThemeContext);

export const ThemeProvider = ({ children }) => {
  const [mode, setMode] = useState(() => {
    return localStorage.getItem('medchain_theme') || 'light';
  });

  const toggleTheme = () => {
    setMode((prevMode) => {
      const nextMode = prevMode === 'light' ? 'dark' : 'light';
      localStorage.setItem('medchain_theme', nextMode);
      return nextMode;
    });
  };

  const theme = useMemo(() => getTheme(mode), [mode]);

  return (
    <ThemeContext.Provider value={{ mode, toggleTheme }}>
      <MuiThemeProvider theme={theme}>
        <CssBaseline />
        {children}
      </MuiThemeProvider>
    </ThemeContext.Provider>
  );
};
