import React from 'react';
import { Box, Typography, CircularProgress, Chip, Stack } from '@mui/material';
import {
  VerifiedUser as SafeIcon,
  Warning as WarningIcon,
  Block as BlockIcon,
} from '@mui/icons-material';

export default function RiskScoreGauge({ score = 15, status = 'SAFE', riskLevel = 'LOW' }) {
  const getColor = () => {
    if (status === 'LOCKDOWN' || status === 'BLOCKED' || score > 65) return 'error';
    if (status === 'ELEVATED' || status === 'SUSPICIOUS' || score > 35) return 'warning';
    return 'success';
  };

  const getStatusIcon = () => {
    if (status === 'LOCKDOWN' || status === 'BLOCKED') return <BlockIcon fontSize="small" />;
    if (status === 'ELEVATED' || status === 'SUSPICIOUS') return <WarningIcon fontSize="small" />;
    return <SafeIcon fontSize="small" />;
  };

  const getDisplayStatus = () => {
    if (status === 'BLOCKED') return 'LOCKDOWN';
    if (status === 'SUSPICIOUS') return 'ELEVATED';
    return status;
  };

  const color = getColor();
  const displayStatus = getDisplayStatus();

  return (
    <Stack spacing={1.5} alignItems="center">
      <Box sx={{ position: 'relative', display: 'inline-flex' }}>
        <CircularProgress
          variant="determinate"
          value={100}
          size={110}
          thickness={5}
          sx={{ color: (theme) => theme.palette.grey[200] }}
        />
        <CircularProgress
          variant="determinate"
          value={Math.min(100, Math.max(0, score))}
          size={110}
          thickness={5}
          color={color}
          sx={{
            position: 'absolute',
            left: 0,
            strokeLinecap: 'round',
          }}
        />
        <Box
          sx={{
            top: 0,
            left: 0,
            bottom: 0,
            right: 0,
            position: 'absolute',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexDirection: 'column',
          }}
        >
          <Typography variant="h4" component="div" fontWeight={800} color={`${color}.main`}>
            {score}%
          </Typography>
          <Typography variant="caption" color="text.secondary" fontWeight={600}>
            Risk Score
          </Typography>
        </Box>
      </Box>

      <Chip
        icon={getStatusIcon()}
        label={`${displayStatus}`}
        color={color}
        variant="filled"
        sx={{ fontWeight: 700, px: 1.5, py: 0.5, fontSize: '0.85rem' }}
      />
    </Stack>
  );
}
