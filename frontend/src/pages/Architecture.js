import React, { useState } from 'react';
import {
  Box,
  Container,
  Typography,
  Grid,
  Card,
  CardContent,
  Chip,
  Paper,
  Divider,
  Stack,
} from '@mui/material';
import {
  Security as SecurityIcon,
  CheckCircle as CheckIcon,
  Warning as WarningIcon,
  Block as BlockIcon,
  ArrowDownward as ArrowDownIcon,
  AccountTree as FlowchartIcon,
} from '@mui/icons-material';
import Navbar from '../components/shared/Navbar';

const FLOWCHART_STEPS = [
  { step: '1', title: 'User Login', desc: 'Patient or Doctor logs in with credentials via JWT authentication.', icon: '👤', color: '#6366f1' },
  { step: '2', title: 'Authentication & Role Check', desc: 'System verifies role (PATIENT/DOCTOR/ADMIN) and issues secure Bearer token.', icon: '🔑', color: '#06b6d4' },
  { step: '3', title: 'Blockchain Identity Verification', desc: 'Verifies SHA3-256 Pseudonym (PID = SHA3-256(identity ∥ nonce)) on EVM smart contract.', icon: '🛡️', color: '#10b981' },
  { step: '4', title: 'Encrypted Medical Record Request', desc: 'Doctor submits access request with CP-ABE attribute parameters (specialty/hospital).', icon: '📄', color: '#a855f7' },
  { step: '5', title: 'Retrieve Encrypted File (Off-chain Storage)', desc: 'Backend fetches AES-256-GCM ciphertext payload from IPFS Kubo node using content CID.', icon: '📦', color: '#f59e0b' },
  { step: '6', title: 'Blockchain Hash Verification', desc: 'Computes SHA256(IPFS_Payload) and verifies matching hash registered on Ethereum ledger.', icon: '⛓️', color: '#ef4444' },
  { step: '7', title: 'Machine Learning Risk Analysis', desc: 'FastAPI Isolation Forest + Autoencoder evaluates 8 behavioral telemetry features in real-time.', icon: '🧠', color: '#ec4899' },
  { step: '8', title: 'Explainable AI Report', desc: 'Generates detailed risk breakdown (isolation outlier probability, autoencoder reconstruction error, time/specialty anomalies).', icon: '📊', color: '#8b5cf6' },
];

const TIERS = [
  {
    name: 'SAFE',
    range: 'Risk Score < 35%',
    action: 'Grant Access',
    desc: 'Proxy Re-Encryption releases AES symmetric key. Doctor decrypts record locally.',
    color: '#10b981',
    icon: <CheckIcon fontSize="large" />,
  },
  {
    name: 'ELEVATED',
    range: 'Risk Score 35% - 65%',
    action: 'Extra Verification',
    desc: 'Triggers step-up multi-factor verification or human-in-the-loop review before key release.',
    color: '#f59e0b',
    icon: <WarningIcon fontSize="large" />,
  },
  {
    name: 'LOCKDOWN',
    range: 'Risk Score > 65%',
    action: 'Deny Access',
    desc: 'Key release immediately blocked. Threat alert logged on blockchain audit ledger.',
    color: '#ef4444',
    icon: <BlockIcon fontSize="large" />,
  },
];

export default function Architecture() {
  const [selectedStep, setSelectedStep] = useState(0);

  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', bgcolor: 'background.default' }}>
      <Navbar />

      <Container maxWidth="lg" sx={{ py: 6 }}>
        {/* Header */}
        <Box textAlign="center" mb={6}>
          <Chip
            icon={<SecurityIcon />}
            label="IEEE Research Architecture Specification"
            color="primary"
            sx={{ mb: 2, fontWeight: 700, px: 1 }}
          />
          <Typography variant="h3" fontWeight={900} letterSpacing="-0.5px" gutterBottom>
            System Workflow & Security Architecture
          </Typography>
          <Typography variant="h6" color="text.secondary" maxWidth="800px" mx="auto" fontWeight={400}>
            Blockchain-Based Secure Medical Data Sharing with Intelligent Risk Analysis and Explainable Anomaly Detection Using Machine Learning
          </Typography>
        </Box>

        {/* Interactive Flowchart Diagram */}
        <Paper elevation={4} sx={{ p: 4, borderRadius: 4, mb: 6, border: 1, borderColor: 'divider' }}>
          <Box display="flex" alignItems="center" mb={3} gap={1}>
            <FlowchartIcon color="primary" fontSize="large" />
            <Typography variant="h5" fontWeight={800}>
              End-to-End System Execution Flowchart
            </Typography>
          </Box>
          <Divider sx={{ mb: 4 }} />

          <Grid container spacing={2} justifyContent="center">
            {FLOWCHART_STEPS.map((s, idx) => (
              <Grid item xs={12} md={6} key={s.step}>
                <Paper
                  elevation={selectedStep === idx ? 6 : 1}
                  onClick={() => setSelectedStep(idx)}
                  sx={{
                    p: 2.5,
                    borderRadius: 3,
                    cursor: 'pointer',
                    transition: '0.3s',
                    borderLeft: 6,
                    borderColor: s.color,
                    bgcolor: selectedStep === idx ? 'action.selected' : 'background.paper',
                    '&:hover': { transform: 'translateY(-2px)' },
                  }}
                >
                  <Stack direction="row" spacing={2} alignItems="center">
                    <Box
                      sx={{
                        width: 44,
                        height: 44,
                        borderRadius: '50%',
                        bgcolor: `${s.color}20`,
                        color: s.color,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        fontSize: 20,
                        fontWeight: 800,
                      }}
                    >
                      {s.step}
                    </Box>
                    <Box flexGrow={1}>
                      <Typography variant="subtitle1" fontWeight={800}>
                        {s.title}
                      </Typography>
                      <Typography variant="body2" color="text.secondary" fontSize={13}>
                        {s.desc}
                      </Typography>
                    </Box>
                  </Stack>
                </Paper>
              </Grid>
            ))}
          </Grid>

          {/* Decision Branch Section */}
          <Box textAlign="center" my={4}>
            <ArrowDownIcon sx={{ fontSize: 40, color: 'text.secondary' }} />
            <Typography variant="h6" fontWeight={800} color="primary" mt={1}>
              Machine Learning Risk Evaluation & Action Tiers
            </Typography>
          </Box>

          <Grid container spacing={3}>
            {TIERS.map((tier) => (
              <Grid item xs={12} md={4} key={tier.name}>
                <Card
                  sx={{
                    borderRadius: 3,
                    borderTop: 6,
                    borderColor: tier.color,
                    height: '100%',
                    boxShadow: 3,
                  }}
                >
                  <CardContent sx={{ textAlign: 'center', p: 3 }}>
                    <Box sx={{ color: tier.color, mb: 1 }}>{tier.icon}</Box>
                    <Typography variant="h5" fontWeight={900} color={tier.color}>
                      {tier.name}
                    </Typography>
                    <Chip label={tier.range} size="small" sx={{ my: 1, fontWeight: 700 }} />
                    <Typography variant="subtitle2" fontWeight={800} mt={1}>
                      Action: {tier.action}
                    </Typography>
                    <Typography variant="body2" color="text.secondary" mt={1} fontSize={13}>
                      {tier.desc}
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
            ))}
          </Grid>

          {/* Terminal Step */}
          <Box textAlign="center" mt={4} pt={3} borderTop={1} borderColor="divider">
            <Typography variant="subtitle1" fontWeight={800} color="text.primary">
              Step 10: Record Audit Log on Blockchain
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Final decision, timestamp, risk score, and user pseudonym are permanently committed to Ethereum Smart Contract (<code style={{ background: '#eee', padding: '2px 6px', borderRadius: 4 }}>MedicalRecords.sol</code>).
            </Typography>
          </Box>
        </Paper>
      </Container>
    </Box>
  );
}
