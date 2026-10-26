import React, { useState } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import {
  AppBar,
  Toolbar,
  Typography,
  Button,
  IconButton,
  Box,
  Avatar,
  Menu,
  MenuItem,
  Chip,
  Tooltip,
  Container,
  Drawer,
  List,
  ListItemButton,
  ListItemText,
  ListItemIcon,
} from '@mui/material';
import {
  LocalHospital as HospitalIcon,
  DarkMode as DarkIcon,
  LightMode as LightIcon,
  Dashboard as DashboardIcon,
  MonitorHeart as MonitoringIcon,
  AccountTree as BlockchainIcon,
  Menu as MenuIcon,
  Logout as LogoutIcon,
  Architecture as ArchIcon,
  People as UsersIcon,
} from '@mui/icons-material';
import { useAuth } from '../../context/AuthContext';
import { useAppTheme } from '../../context/ThemeContext';

export default function Navbar() {
  const { user, logout } = useAuth();
  const { mode, toggleTheme } = useAppTheme();
  const navigate = useNavigate();
  const location = useLocation();

  const [anchorEl, setAnchorEl] = useState(null);
  const [drawerOpen, setDrawerOpen] = useState(false);

  const handleOpenMenu = (event) => setAnchorEl(event.currentTarget);
  const handleCloseMenu = () => setAnchorEl(null);

  const handleLogout = () => {
    handleCloseMenu();
    logout();
    navigate('/login');
  };

  const getDashboardPath = () => {
    if (!user) return '/login';
    if (user.role === 'PATIENT') return '/patient';
    if (user.role === 'DOCTOR') return '/doctor';
    if (user.role === 'ADMIN') return '/admin';
    return '/';
  };

  const navLinks = [
    { label: 'Home', path: '/' },
    ...(user ? [{ label: 'Dashboard', path: getDashboardPath(), icon: <DashboardIcon /> }] : []),
    ...(user && user.role === 'ADMIN' ? [{ label: 'Users', path: '/admin/users', icon: <UsersIcon /> }] : []),
    ...(user ? [{ label: 'Monitoring', path: '/monitoring', icon: <MonitoringIcon /> }] : []),
    ...(user ? [{ label: 'Blockchain Explorer', path: '/blockchain-explorer', icon: <BlockchainIcon /> }] : []),
    { label: 'Architecture', path: '/architecture', icon: <ArchIcon /> },
  ];

  const getRoleColor = (role) => {
    switch (role) {
      case 'PATIENT': return 'info';
      case 'DOCTOR': return 'success';
      case 'ADMIN': return 'secondary';
      default: return 'default';
    }
  };

  return (
    <AppBar position="sticky" elevation={1} sx={{ bgcolor: 'background.paper', color: 'text.primary', borderBottom: 1, borderColor: 'divider' }}>
      <Container maxWidth="xl">
        <Toolbar disableGutters>
          {/* Logo & Title */}
          <HospitalIcon sx={{ color: 'primary.main', mr: 1, fontSize: 32 }} />
          <Typography
            variant="h5"
            component={Link}
            to="/"
            sx={{
              fontWeight: 800,
              letterSpacing: '-0.5px',
              color: 'primary.main',
              textDecoration: 'none',
              display: 'flex',
              alignItems: 'center',
              mr: 4,
            }}
          >
            MedChain
          </Typography>

          {/* Desktop Nav Links */}
          <Box sx={{ flexGrow: 1, display: { xs: 'none', md: 'flex' }, gap: 1 }}>
            {navLinks.map((link) => (
              <Button
                key={link.path}
                component={Link}
                to={link.path}
                color={location.pathname === link.path ? 'primary' : 'inherit'}
                variant={location.pathname === link.path ? 'contained' : 'text'}
                sx={{
                  fontWeight: location.pathname === link.path ? 700 : 500,
                  borderRadius: 2,
                  px: 2,
                }}
              >
                {link.label}
              </Button>
            ))}
          </Box>

          {/* Mobile Menu Icon */}
          <Box sx={{ display: { xs: 'flex', md: 'none' }, flexGrow: 1 }}>
            <IconButton onClick={() => setDrawerOpen(true)} color="inherit">
              <MenuIcon />
            </IconButton>
          </Box>

          {/* Right Side Actions */}
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
            <Tooltip title={`Switch to ${mode === 'light' ? 'Dark' : 'Light'} Mode`}>
              <IconButton onClick={toggleTheme} color="inherit">
                {mode === 'light' ? <DarkIcon /> : <LightIcon sx={{ color: 'warning.main' }} />}
              </IconButton>
            </Tooltip>

            {user ? (
              <>
                <Chip
                  label={user.role}
                  color={getRoleColor(user.role)}
                  size="small"
                  sx={{ display: { xs: 'none', sm: 'inline-flex' } }}
                />
                <Tooltip title="Account settings">
                  <IconButton onClick={handleOpenMenu} size="small" sx={{ ml: 0.5 }}>
                    <Avatar sx={{ bgcolor: 'primary.main', width: 36, height: 36, fontWeight: 700 }}>
                      {user.name ? user.name.charAt(0).toUpperCase() : 'U'}
                    </Avatar>
                  </IconButton>
                </Tooltip>
                <Menu
                  anchorEl={anchorEl}
                  open={Boolean(anchorEl)}
                  onClose={handleCloseMenu}
                  transformOrigin={{ horizontal: 'right', vertical: 'top' }}
                  anchorOrigin={{ horizontal: 'right', vertical: 'bottom' }}
                  PaperProps={{ sx: { minWidth: 200, mt: 1 } }}
                >
                  <Box sx={{ px: 2, py: 1.5 }}>
                    <Typography variant="subtitle2" noWrap fontWeight={700}>
                      {user.name}
                    </Typography>
                    <Typography variant="body2" color="text.secondary" noWrap fontSize={12}>
                      {user.email}
                    </Typography>
                  </Box>
                  <MenuItem onClick={() => { handleCloseMenu(); navigate(getDashboardPath()); }}>
                    <ListItemIcon><DashboardIcon fontSize="small" /></ListItemIcon>
                    Dashboard
                  </MenuItem>
                  <MenuItem onClick={handleLogout} sx={{ color: 'error.main' }}>
                    <ListItemIcon><LogoutIcon fontSize="small" color="error" /></ListItemIcon>
                    Logout
                  </MenuItem>
                </Menu>
              </>
            ) : (
              <Box sx={{ display: 'flex', gap: 1 }}>
                <Button variant="outlined" component={Link} to="/login" size="small">
                  Sign In
                </Button>
                <Button variant="contained" component={Link} to="/register" size="small">
                  Register
                </Button>
              </Box>
            )}
          </Box>
        </Toolbar>
      </Container>

      {/* Mobile Drawer */}
      <Drawer anchor="left" open={drawerOpen} onClose={() => setDrawerOpen(false)}>
        <Box sx={{ width: 250, pt: 2 }} role="presentation" onClick={() => setDrawerOpen(false)}>
          <Box sx={{ display: 'flex', alignItems: 'center', px: 2, mb: 2 }}>
            <HospitalIcon sx={{ color: 'primary.main', mr: 1 }} />
            <Typography variant="h6" color="primary.main" fontWeight={800}>
              MedChain
            </Typography>
          </Box>
          <List>
            {navLinks.map((link) => (
              <ListItemButton key={link.path} component={Link} to={link.path}>
                {link.icon && <ListItemIcon>{link.icon}</ListItemIcon>}
                <ListItemText primary={link.label} />
              </ListItemButton>
            ))}
          </List>
        </Box>
      </Drawer>
    </AppBar>
  );
}
