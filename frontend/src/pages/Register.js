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
  FormControl,
  FormLabel,
  RadioGroup,
  FormControlLabel,
  Radio,
  Grid,
} from '@mui/material';
import { LocalHospital as HospitalIcon } from '@mui/icons-material';
import Navbar from '../components/shared/Navbar';
import { authService } from '../services/api';

export default function Register() {
  const [formData, setFormData] = useState({
    name: '',
    email: '',
    password: '',
    role: 'PATIENT',
    phone: '',
    specialty: 'CARDIOLOGY',
    hospital: 'City General Hospital',
  });
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);
  const [loading, setLoading] = useState(false);

  const navigate = useNavigate();

  const handleChange = (e) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    const payload = {
      name: formData.name,
      email: formData.email,
      password: formData.password,
      role: formData.role,
      phone: formData.phone,
      attributes: formData.role === 'DOCTOR' ? {
        specialty: formData.specialty,
        hospital: formData.hospital,
        license: 'Licensed Medical Doctor',
      } : {
        hospital: formData.hospital,
      },
    };

    try {
      await authService.register(payload);
      setSuccess(true);
      setTimeout(() => navigate('/login'), 1500);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Registration failed.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', bgcolor: 'background.default' }}>
      <Navbar />

      <Container maxWidth="sm" sx={{ py: 6, my: 'auto' }}>
        <Paper elevation={4} sx={{ p: 4, borderRadius: 4 }}>
          <Box textAlign="center" mb={3}>
            <Box
              sx={{
                width: 56,
                height: 56,
                borderRadius: '50%',
                bgcolor: 'secondary.light',
                color: 'secondary.main',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                mx: 'auto',
              }}
            >
              <HospitalIcon fontSize="large" />
            </Box>
            <Typography variant="h5" fontWeight={800} mt={1}>
              Create MedChain Account
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Join the decentralized privacy-preserving network
            </Typography>
          </Box>

          {error && <Alert severity="error" sx={{ mb: 3 }}>{error}</Alert>}
          {success && <Alert severity="success" sx={{ mb: 3 }}>Registration successful! Redirecting to login...</Alert>}

          <form onSubmit={handleSubmit}>
            <Stack spacing={2.5}>
              <FormControl component="fieldset">
                <FormLabel component="legend" sx={{ fontWeight: 700, mb: 1 }}>
                  Select Account Role
                </FormLabel>
                <RadioGroup row name="role" value={formData.role} onChange={handleChange}>
                  <FormControlLabel value="PATIENT" control={<Radio />} label="Patient" />
                  <FormControlLabel value="DOCTOR" control={<Radio />} label="Doctor" />
                </RadioGroup>
              </FormControl>

              <TextField
                label="Full Name"
                name="name"
                required
                fullWidth
                value={formData.name}
                onChange={handleChange}
                placeholder="Dr. Alice Smith or John Doe"
              />

              <TextField
                label="Email Address"
                name="email"
                type="email"
                required
                fullWidth
                value={formData.email}
                onChange={handleChange}
                placeholder="user@medchain.com"
              />

              <Grid container spacing={2}>
                <Grid item xs={12} sm={6}>
                  <TextField
                    label="Password"
                    name="password"
                    type="password"
                    required
                    fullWidth
                    value={formData.password}
                    onChange={handleChange}
                  />
                </Grid>
                <Grid item xs={12} sm={6}>
                  <TextField
                    label="Phone Number"
                    name="phone"
                    fullWidth
                    value={formData.phone}
                    onChange={handleChange}
                    placeholder="+1 555-0199"
                  />
                </Grid>
              </Grid>

              {formData.role === 'DOCTOR' && (
                <Grid container spacing={2}>
                  <Grid item xs={12} sm={6}>
                    <TextField
                      label="Specialty"
                      name="specialty"
                      fullWidth
                      value={formData.specialty}
                      onChange={handleChange}
                      placeholder="CARDIOLOGY"
                    />
                  </Grid>
                  <Grid item xs={12} sm={6}>
                    <TextField
                      label="Hospital / Clinic"
                      name="hospital"
                      fullWidth
                      value={formData.hospital}
                      onChange={handleChange}
                      placeholder="City Hospital"
                    />
                  </Grid>
                </Grid>
              )}

              <Button
                type="submit"
                variant="contained"
                size="large"
                fullWidth
                disabled={loading}
                sx={{ py: 1.5, fontWeight: 700 }}
              >
                {loading ? 'Creating Account...' : 'Register Account'}
              </Button>
            </Stack>
          </form>

          <Box textAlign="center" mt={3}>
            <Typography variant="body2" color="text.secondary">
              Already registered?{' '}
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
