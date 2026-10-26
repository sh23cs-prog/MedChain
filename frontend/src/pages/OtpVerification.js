import React, { useState } from 'react';
import { useLocation, useNavigate, Link } from 'react-router-dom';
import {
  Box,
  Container,
  Paper,
  Typography,
  TextField,
  Button,
  Stack,
  Alert,
} from '@mui/material';
import { MarkEmailRead as EmailIcon } from '@mui/icons-material';
import Navbar from '../components/shared/Navbar';
import { authService } from '../services/api';

export default function OtpVerification() {
  const location = useLocation();
  const navigate = useNavigate();

  const initialEmail = location.state?.email || '';
  const initialType = location.state?.type || 'REGISTRATION';

  const [email, setEmail] = useState(initialEmail);
  const [otp, setOtp] = useState('');
  const [loading, setLoading] = useState(false);
  const [resending, setResending] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const handleVerify = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setLoading(true);

    try {
      await authService.verifyOtp({ email, otp, type: initialType });
      setSuccess('OTP verified successfully! Redirecting to sign in...');
      setTimeout(() => navigate('/login'), 1500);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'OTP verification failed.');
    } finally {
      setLoading(false);
    }
  };

  const handleResend = async () => {
    setError('');
    setSuccess('');
    setResending(true);
    try {
      const res = await authService.resendOtp(email);
      setSuccess(res.data?.message || 'A fresh 6-digit OTP code has been dispatched to your email address.');
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to resend OTP.');
    } finally {
      setResending(false);
    }
  };

  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', bgcolor: 'background.default' }}>
      <Navbar />

      <Container maxWidth="xs" sx={{ py: 8, my: 'auto' }}>
        <Paper elevation={4} sx={{ p: 4, borderRadius: 4 }}>
          <Box textAlign="center" mb={3}>
            <Box
              sx={{
                width: 60,
                height: 60,
                borderRadius: '50%',
                bgcolor: 'primary.light',
                color: 'primary.main',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                mx: 'auto',
              }}
            >
              <EmailIcon fontSize="large" />
            </Box>
            <Typography variant="h5" fontWeight={800} mt={2}>
              Verify Email OTP
            </Typography>
            <Typography variant="body2" color="text.secondary" mt={0.5}>
              Enter the 6-digit security code sent to your registered email address.
            </Typography>
          </Box>

          {error && <Alert severity="error" sx={{ mb: 3 }}>{error}</Alert>}
          {success && <Alert severity="success" sx={{ mb: 3 }}>{success}</Alert>}

          <form onSubmit={handleVerify}>
            <Stack spacing={2.5}>
              <TextField
                label="Registered Email"
                type="email"
                required
                fullWidth
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />

              <TextField
                label="6-Digit OTP Code"
                required
                fullWidth
                value={otp}
                onChange={(e) => setOtp(e.target.value.trim())}
                inputProps={{ maxLength: 6, style: { letterSpacing: 6, fontSize: '1.25rem', textAlign: 'center', fontWeight: 700 } }}
                placeholder="123456"
              />

              <Button
                type="submit"
                variant="contained"
                size="large"
                fullWidth
                disabled={loading || otp.length !== 6}
                sx={{ py: 1.5, fontWeight: 700 }}
              >
                {loading ? 'Verifying OTP...' : 'Verify OTP Code'}
              </Button>

              <Button
                variant="outlined"
                color="secondary"
                fullWidth
                disabled={resending || !email}
                onClick={handleResend}
                sx={{ fontWeight: 600 }}
              >
                {resending ? 'Sending Code...' : 'Resend Security OTP'}
              </Button>
            </Stack>
          </form>

          <Box textAlign="center" mt={3}>
            <Typography variant="body2" color="text.secondary">
              Back to{' '}
              <Typography component={Link} to="/login" variant="body2" color="primary" fontWeight={700} underline="hover">
                Sign In
              </Typography>
            </Typography>
          </Box>
        </Paper>
      </Container>
    </Box>
  );
}
