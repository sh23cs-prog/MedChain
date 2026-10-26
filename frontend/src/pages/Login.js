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
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
} from '@mui/material';
import {
  Visibility,
  VisibilityOff,
  LocalHospital as HospitalIcon,
  Person as PersonIcon,
  MedicalServices as DoctorIcon,
  AdminPanelSettings as AdminIcon,
  LockReset as ResetIcon,
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

  // Forgot / Reset Password Dialog State
  const [resetDialogOpen, setResetDialogOpen] = useState(false);
  const [resetStep, setResetStep] = useState(1); // 1: request OTP, 2: verify OTP & set new password
  const [resetEmail, setResetEmail] = useState('');
  const [resetOtp, setResetOtp] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [resetLoading, setResetLoading] = useState(false);
  const [resetError, setResetError] = useState('');
  const [resetSuccess, setResetSuccess] = useState('');

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

  const handleSendResetOtp = async (e) => {
    e.preventDefault();
    setResetError('');
    setResetSuccess('');
    setResetLoading(true);
    try {
      const res = await authService.forgotPassword(resetEmail);
      setResetSuccess(res.data?.message || 'Password reset OTP sent to email. Check your inbox.');
      setResetStep(2);
    } catch (err) {
      setResetError(err.response?.data?.message || err.message || 'Failed to send reset code.');
    } finally {
      setResetLoading(false);
    }
  };

  const handleConfirmReset = async (e) => {
    e.preventDefault();
    setResetError('');
    setResetSuccess('');
    setResetLoading(true);
    try {
      await authService.resetPassword({ email: resetEmail, otp: resetOtp, newPassword });
      setResetSuccess('Password updated successfully! You can now log in with your new password.');
      setTimeout(() => {
        setResetDialogOpen(false);
        setResetStep(1);
        setResetEmail('');
        setResetOtp('');
        setNewPassword('');
      }, 2000);
    } catch (err) {
      setResetError(err.response?.data?.message || err.message || 'Password reset failed.');
    } finally {
      setResetLoading(false);
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

              <Box textAlign="right">
                <Button
                  size="small"
                  color="primary"
                  onClick={() => { setResetDialogOpen(true); setResetStep(1); setResetError(''); setResetSuccess(''); }}
                  sx={{ textTransform: 'none', fontWeight: 600 }}
                >
                  Forgot Password?
                </Button>
              </Box>

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
              onClick={() => handleDemoLogin('patient@medchain.com', 'Password123!')}
            >
              Patient: patient@medchain.com
            </Button>
            <Button
              variant="outlined"
              size="small"
              startIcon={<DoctorIcon color="success" />}
              onClick={() => handleDemoLogin('doctor@medchain.com', 'Password123!')}
            >
              Doctor: doctor@medchain.com
            </Button>
            <Button
              variant="outlined"
              size="small"
              startIcon={<AdminIcon color="secondary" />}
              onClick={() => handleDemoLogin('admin@medchain.com', 'Password123!')}
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

      {/* Forgot / Reset Password Modal */}
      <Dialog open={resetDialogOpen} onClose={() => setResetDialogOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1, fontWeight: 700 }}>
          <ResetIcon color="primary" /> Reset Account Password
        </DialogTitle>
        <DialogContent dividers>
          {resetError && <Alert severity="error" sx={{ mb: 2 }}>{resetError}</Alert>}
          {resetSuccess && <Alert severity="success" sx={{ mb: 2 }}>{resetSuccess}</Alert>}

          {resetStep === 1 ? (
            <form id="forgot-form" onSubmit={handleSendResetOtp}>
              <Stack spacing={2} pt={1}>
                <Typography variant="body2" color="text.secondary">
                  Enter your registered email address to receive a 6-digit password reset OTP code.
                </Typography>
                <TextField
                  label="Registered Email"
                  type="email"
                  required
                  fullWidth
                  value={resetEmail}
                  onChange={(e) => setResetEmail(e.target.value)}
                />
              </Stack>
            </form>
          ) : (
            <form id="reset-form" onSubmit={handleConfirmReset}>
              <Stack spacing={2} pt={1}>
                <Typography variant="body2" color="text.secondary">
                  Enter the 6-digit code sent to <strong>{resetEmail}</strong> and your new password.
                </Typography>
                <TextField
                  label="6-Digit OTP Code"
                  required
                  fullWidth
                  value={resetOtp}
                  onChange={(e) => setResetOtp(e.target.value)}
                  inputProps={{ maxLength: 6, style: { letterSpacing: 4, textAlign: 'center', fontWeight: 700 } }}
                />
                <TextField
                  label="New Password"
                  type="password"
                  required
                  fullWidth
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                />
              </Stack>
            </form>
          )}
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={() => setResetDialogOpen(false)}>Cancel</Button>
          {resetStep === 1 ? (
            <Button type="submit" form="forgot-form" variant="contained" disabled={resetLoading}>
              {resetLoading ? 'Sending...' : 'Send Reset Code'}
            </Button>
          ) : (
            <Button type="submit" form="reset-form" variant="contained" disabled={resetLoading}>
              {resetLoading ? 'Updating...' : 'Set New Password'}
            </Button>
          )}
        </DialogActions>
      </Dialog>
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
