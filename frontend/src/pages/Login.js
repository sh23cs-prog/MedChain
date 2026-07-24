import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import {
  Box,
  Container,
  Paper,
  Typography,
  TextField,
  Button,
  Stack,
  Alert,
  IconButton,
  InputAdornment,
  Divider,
} from '@mui/material';
import {
  Visibility,
  VisibilityOff,
  LocalHospital as HospitalIcon,
  Person as PersonIcon,
  MedicalServices as DoctorIcon,
  AdminPanelSettings as AdminIcon,
} from '@mui/icons-material';
import Navbar from '../components/shared/Navbar';
import { useAuth } from '../context/AuthContext';
import { authService } from '../services/api';

export default function Login() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const { login: authLogin } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const res = await authService.login({ email, password });
      const user = res.data;
      authLogin(user);
      if (user.role === 'PATIENT') navigate('/patient');
      else if (user.role === 'DOCTOR') navigate('/doctor');
      else if (user.role === 'ADMIN') navigate('/admin');
      else navigate('/');
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Login failed. Please check your credentials.');
    } finally {
      setLoading(false);
    }
  };

  const handleDemoLogin = async (demoEmail, demoPassword) => {
    setEmail(demoEmail);
    setPassword(demoPassword);
    setError('');
    setLoading(true);

    try {
      const res = await authService.login({ email: demoEmail, password: demoPassword });
      const user = res.data;
      authLogin(user);
      if (user.role === 'PATIENT') navigate('/patient');
      else if (user.role === 'DOCTOR') navigate('/doctor');
      else if (user.role === 'ADMIN') navigate('/admin');
      else navigate('/');
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Demo login failed.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', bgcolor: 'background.default' }}>
      <Navbar />

      <Container maxWidth="xs" sx={{ py: 8, my: 'auto' }}>
        <Paper elevation={4} sx={{ p: 4, borderRadius: 4 }}>
          <Box textAlign="center" mb={3}>
            <AvatarIcon />
            <Typography variant="h5" fontWeight={800} mt={1}>
              Sign In to MedChain
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Access your medical records and portal
            </Typography>
          </Box>

          {error && <Alert severity="error" sx={{ mb: 3 }}>{error}</Alert>}

          <form onSubmit={handleSubmit}>
            <Stack spacing={2.5}>
              <TextField
                label="Email Address"
                type="email"
                fullWidth
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="email@example.com"
              />

              <TextField
                label="Password"
                type={showPassword ? 'text' : 'password'}
                fullWidth
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                InputProps={{
                  endAdornment: (
                    <InputAdornment position="end">
                      <IconButton onClick={() => setShowPassword(!showPassword)} edge="end">
                        {showPassword ? <VisibilityOff /> : <Visibility />}
                      </IconButton>
                    </InputAdornment>
                  ),
                }}
              />

              <Button
                type="submit"
                variant="contained"
                size="large"
                fullWidth
                disabled={loading}
                sx={{ py: 1.5, fontWeight: 700 }}
              >
                {loading ? 'Authenticating...' : 'Sign In'}
              </Button>
            </Stack>
          </form>

          <Divider sx={{ my: 3 }}>
            <Typography variant="caption" color="text.secondary">
              QUICK DEMO CREDENTIALS
            </Typography>
          </Divider>

          <Stack spacing={1}>
            <Button
              variant="outlined"
              size="small"
              startIcon={<PersonIcon color="info" />}
              onClick={() => handleDemoLogin('patient@medchain.com', 'patient123')}
            >
              Patient: patient@medchain.com
            </Button>
            <Button
              variant="outlined"
              size="small"
              startIcon={<DoctorIcon color="success" />}
              onClick={() => handleDemoLogin('doctor@medchain.com', 'doctor123')}
            >
              Doctor: doctor@medchain.com
            </Button>
            <Button
              variant="outlined"
              size="small"
              startIcon={<AdminIcon color="secondary" />}
              onClick={() => handleDemoLogin('admin@medchain.com', 'admin123')}
            >
              Admin: admin@medchain.com
            </Button>
          </Stack>

          <Box textAlign="center" mt={3}>
            <Typography variant="body2" color="text.secondary">
              Don't have an account?{' '}
              <Typography component={Link} to="/register" variant="body2" color="primary" fontWeight={700} underline="hover">
                Register here
              </Typography>
            </Typography>
          </Box>
        </Paper>
      </Container>
    </Box>
  );
}

function AvatarIcon() {
  return (
    <Box
      sx={{
        width: 56,
        height: 56,
        borderRadius: '50%',
        bgcolor: 'primary.light',
        color: 'primary.main',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        mx: 'auto',
      }}
    >
      <HospitalIcon fontSize="large" />
    </Box>
  );
}
