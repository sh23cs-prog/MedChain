import React from 'react';
import { Link } from 'react-router-dom';
import {
  Box,
  Container,
  Typography,
  Button,
  Grid,
  Card,
  CardContent,
  Chip,
  Stack,
  Stepper,
  Step,
  StepLabel,
  StepContent,
  Paper,
  Divider,
  Avatar,
} from '@mui/material';
import {
  Security as SecurityIcon,
  Lock as LockIcon,
  VpnKey as KeyIcon,
  Psychology as MLIcon,
  AccountTree as BlockchainIcon,
  Storage as StorageIcon,
  LocalHospital as HospitalIcon,
  ArrowForward as ArrowIcon,
  CheckCircleOutline as CheckIcon,
  VerifiedUser as ShieldIcon,
} from '@mui/icons-material';
import Navbar from '../components/shared/Navbar';
import { useAuth } from '../context/AuthContext';

export default function Landing() {
  const { user } = useAuth();

  const features = [
    {
      title: 'CP-ABE Access Control',
      description: 'Ciphertext-Policy Attribute-Based Encryption enforces fine-grained access control based on doctor attributes.',
      icon: <LockIcon fontSize="large" color="primary" />,
    },
    {
      title: 'Proxy Re-Encryption (PRE)',
      description: 'Delegates decryption rights to authorized doctors without exposing raw encryption keys or plaintext files.',
      icon: <KeyIcon fontSize="large" color="secondary" />,
    },
    {
      title: 'Identity Pseudonymisation',
      description: 'SHA3-256 pseudonyms guarantee patient identity privacy on the public ledger. Real identities stay off-chain.',
      icon: <ShieldIcon fontSize="large" color="info" />,
    },
    {
      title: 'ML Anomaly Detection',
      description: 'Isolation Forest continuously analyzes access patterns and flags unauthorized or suspicious access attempts in real-time.',
      icon: <MLIcon fontSize="large" color="warning" />,
    },
    {
      title: 'IPFS Storage (Kubo)',
      description: 'Decentralized content-addressed storage for AES-256-GCM encrypted medical files, avoiding centralized cloud breaches.',
      icon: <StorageIcon fontSize="large" color="success" />,
    },
    {
      title: 'Ethereum Audit Trail',
      description: 'Immutable smart contract logs permanently record all uploads, access grants, revocations, and view events.',
      icon: <BlockchainIcon fontSize="large" color="error" />,
    },
  ];

  const workflowSteps = [
    {
      label: 'Patient Uploads Record',
      description: 'File is encrypted locally with AES-256-GCM and stored securely off-chain with SHA-256 checksum verification.',
    },
    {
      label: 'CP-ABE Key Encapsulation',
      description: 'The AES key is wrapped with an attribute access policy (e.g. "specialty:CARDIOLOGY AND hospital:GENERAL").',
    },
    {
      label: 'Blockchain Registration',
      description: 'Record ID hash, SHA-256 payload checksum, and patient pseudonym (PID) are permanently committed to Hardhat EVM smart contract.',
    },
    {
      label: 'Doctor Access & ML Scoring',
      description: 'When a doctor requests access, Isolation Forest scores behavioral metrics. If clear, PRE key allows AES key recovery.',
    },
    {
      label: 'Decryption & Immutable Audit',
      description: 'Doctor views record; every view event is logged on-chain with risk level, timestamp, and transaction hash.',
    },
  ];

  const techStack = [
    'React 18', 'Material-UI', 'Spring Boot 3', 'Java 17', 'MongoDB 7',
    'Solidity 0.8', 'Hardhat', 'IPFS (Kubo)', 'Python FastAPI', 'Scikit-learn (Isolation Forest)',
    'AES-256-GCM', 'CP-ABE', 'Proxy Re-Encryption', 'JWT'
  ];

  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', bgcolor: 'background.default' }}>
      <Navbar />

      {/* Hero Section */}
      <Box
        sx={{
          background: (theme) =>
            theme.palette.mode === 'light'
              ? 'linear-gradient(135deg, #0F172A 0%, #1E3A8A 50%, #0284C7 100%)'
              : 'linear-gradient(135deg, #020617 0%, #0F172A 50%, #1E293B 100%)',
          color: '#ffffff',
          pt: { xs: 8, md: 12 },
          pb: { xs: 8, md: 12 },
          position: 'relative',
          overflow: 'hidden',
        }}
      >
        <Container maxWidth="lg">
          <Grid container spacing={4} alignItems="center">
            <Grid item xs={12} md={7}>
              <Chip
                label="IEEE Research Prototype • 2026"
                color="secondary"
                size="small"
                sx={{ mb: 2, fontWeight: 700, px: 1 }}
              />
              <Typography
                variant="h2"
                component="h1"
                sx={{
                  fontWeight: 900,
                  fontSize: { xs: '2.2rem', md: '3.5rem' },
                  lineHeight: 1.15,
                  mb: 2,
                  letterSpacing: '-1px',
                }}
              >
                Privacy-Preserving Medical Data Sharing on Blockchain
              </Typography>
              <Typography
                variant="h6"
                sx={{
                  color: 'rgba(255, 255, 255, 0.85)',
                  fontWeight: 400,
                  mb: 4,
                  lineHeight: 1.6,
                }}
              >
                A decentralized e-healthcare platform combining CP-ABE encryption, Proxy Re-Encryption,
                IPFS storage, and Isolation Forest ML anomaly detection for secure medical record sharing.
              </Typography>

              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
                {user ? (
                  <Button
                    variant="contained"
                    color="secondary"
                    size="large"
                    component={Link}
                    to={user.role === 'PATIENT' ? '/patient' : user.role === 'DOCTOR' ? '/doctor' : '/admin'}
                    endIcon={<ArrowIcon />}
                    sx={{ px: 4, py: 1.5, fontSize: '1rem', fontWeight: 700 }}
                  >
                    Go to Dashboard
                  </Button>
                ) : (
                  <>
                    <Button
                      variant="contained"
                      color="secondary"
                      size="large"
                      component={Link}
                      to="/login"
                      endIcon={<ArrowIcon />}
                      sx={{ px: 4, py: 1.5, fontSize: '1rem', fontWeight: 700 }}
                    >
                      Sign In & Demo
                    </Button>
                    <Button
                      variant="outlined"
                      size="large"
                      component={Link}
                      to="/register"
                      sx={{
                        px: 4,
                        py: 1.5,
                        fontSize: '1rem',
                        color: '#ffffff',
                        borderColor: 'rgba(255,255,255,0.4)',
                        '&:hover': { borderColor: '#ffffff', bgcolor: 'rgba(255,255,255,0.08)' },
                      }}
                    >
                      Register New User
                    </Button>
                  </>
                )}
              </Stack>
            </Grid>

            <Grid item xs={12} md={5} sx={{ textAlign: 'center' }}>
              <Paper
                elevation={6}
                sx={{
                  p: 4,
                  borderRadius: 4,
                  bgcolor: 'background.paper',
                  color: 'text.primary',
                  textAlign: 'left',
                }}
              >
                <Typography variant="h6" fontWeight={800} color="primary" gutterBottom>
                  Security Architecture
                </Typography>
                <Divider sx={{ mb: 2 }} />
                <Stack spacing={2}>
                  <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'center' }}>
                    <CheckIcon color="success" />
                    <Typography variant="body2" fontWeight={600}>AES-256-GCM File Encryption</Typography>
                  </Box>
                  <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'center' }}>
                    <CheckIcon color="success" />
                    <Typography variant="body2" fontWeight={600}>CP-ABE Policy Access Control</Typography>
                  </Box>
                  <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'center' }}>
                    <CheckIcon color="success" />
                    <Typography variant="body2" fontWeight={600}>Proxy Re-Encryption (PRE) Key Swap</Typography>
                  </Box>
                  <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'center' }}>
                    <CheckIcon color="success" />
                    <Typography variant="body2" fontWeight={600}>SHA3-256 Identity Pseudonyms</Typography>
                  </Box>
                  <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'center' }}>
                    <CheckIcon color="success" />
                    <Typography variant="body2" fontWeight={600}>Isolation Forest Anomaly Scoring</Typography>
                  </Box>
                  <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'center' }}>
                    <CheckIcon color="success" />
                    <Typography variant="body2" fontWeight={600}>Ethereum Immutable Audit Trail</Typography>
                  </Box>
                </Stack>
              </Paper>
            </Grid>
          </Grid>
        </Container>
      </Box>

      {/* Features Grid */}
      <Container maxWidth="lg" sx={{ py: 10 }}>
        <Box textAlign="center" mb={6}>
          <Typography variant="subtitle1" color="primary.main" fontWeight={700} uppercase letterSpacing={1}>
            Platform Capabilities
          </Typography>
          <Typography variant="h3" fontWeight={800} mt={1}>
            Comprehensive Security & Privacy Features
          </Typography>
          <Typography variant="body1" color="text.secondary" maxWidth="700px" mx="auto" mt={1}>
            Engineered specifically to solve medical data privacy, access control, and auditability challenges in modern healthcare networks.
          </Typography>
        </Box>

        <Grid container spacing={3}>
          {features.map((feature, index) => (
            <Grid item xs={12} sm={6} md={4} key={index}>
              <Card sx={{ height: '100%', transition: '0.3s', '&:hover': { transform: 'translateY(-4px)', boxShadow: 4 } }}>
                <CardContent sx={{ p: 3 }}>
                  <Box mb={2}>{feature.icon}</Box>
                  <Typography variant="h6" fontWeight={700} gutterBottom>
                    {feature.title}
                  </Typography>
                  <Typography variant="body2" color="text.secondary" lineHeight={1.6}>
                    {feature.description}
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
          ))}
        </Grid>
      </Container>

      {/* Workflow Section */}
      <Box sx={{ bgcolor: 'background.paper', py: 10, borderTop: 1, borderBottom: 1, borderColor: 'divider' }}>
        <Container maxWidth="lg">
          <Grid container spacing={6} alignItems="center">
            <Grid item xs={12} md={5}>
              <Typography variant="subtitle1" color="primary.main" fontWeight={700}>
                HOW IT WORKS
              </Typography>
              <Typography variant="h3" fontWeight={800} mt={1} mb={2}>
                End-to-End Secure Sharing Flow
              </Typography>
              <Typography variant="body1" color="text.secondary" paragraph>
                MedChain enforces privacy at every step — from client-side file encryption to smart contract verification and ML-driven risk assessment.
              </Typography>
              <Button
                variant="outlined"
                color="primary"
                component={Link}
                to="/architecture"
                startIcon={<SecurityIcon />}
                sx={{ mt: 2 }}
              >
                View Full System Architecture
              </Button>
            </Grid>

            <Grid item xs={12} md={7}>
              <Stepper orientation="vertical">
                {workflowSteps.map((step, idx) => (
                  <Step key={idx} active={true}>
                    <StepLabel
                      StepIconComponent={() => (
                        <Avatar sx={{ bgcolor: 'primary.main', width: 28, height: 28, fontSize: 14, fontWeight: 700 }}>
                          {idx + 1}
                        </Avatar>
                      )}
                    >
                      <Typography variant="h6" fontWeight={700}>
                        {step.label}
                      </Typography>
                    </StepLabel>
                    <StepContent>
                      <Typography variant="body2" color="text.secondary" sx={{ pb: 2 }}>
                        {step.description}
                      </Typography>
                    </StepContent>
                  </Step>
                ))}
              </Stepper>
            </Grid>
          </Grid>
        </Container>
      </Box>

      {/* Technology Stack Chips */}
      <Container maxWidth="lg" sx={{ py: 10 }}>
        <Box textAlign="center" mb={4}>
          <Typography variant="h4" fontWeight={800} gutterBottom>
            Built With Industry-Standard Technologies
          </Typography>
          <Typography variant="body1" color="text.secondary">
            Production-grade stack engineered for demonstration and research verification.
          </Typography>
        </Box>

        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1.5, justifyContent: 'center' }}>
          {techStack.map((tech) => (
            <Chip
              key={tech}
              label={tech}
              variant="outlined"
              color="primary"
              sx={{ px: 1, py: 2.5, borderRadius: 3, fontWeight: 600, fontSize: '0.9rem' }}
            />
          ))}
        </Box>
      </Container>

      {/* Footer */}
      <Box sx={{ bgcolor: 'background.paper', py: 4, mt: 'auto', borderTop: 1, borderColor: 'divider' }}>
        <Container maxWidth="lg">
          <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems="center" spacing={2}>
            <Box sx={{ display: 'flex', alignItems: 'center' }}>
              <HospitalIcon sx={{ color: 'primary.main', mr: 1 }} />
              <Typography variant="subtitle1" fontWeight={800} color="primary.main">
                MedChain
              </Typography>
              <Typography variant="caption" color="text.secondary" sx={{ ml: 2 }}>
                IEEE Healthcare Platform Demonstration
              </Typography>
            </Box>

            <Typography variant="body2" color="text.secondary">
              © 2026 MedChain Platform. All rights reserved.
            </Typography>
          </Stack>
        </Container>
      </Box>
    </Box>
  );
}
