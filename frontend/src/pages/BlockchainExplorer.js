import React, { useState, useEffect } from 'react';
import {
  Box,
  Container,
  Typography,
  Grid,
  Card,
  CardContent,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Chip,
  Stack,
  IconButton,
  TextField,
  InputAdornment,
  Tooltip,
  TablePagination,
} from '@mui/material';
import {
  Search as SearchIcon,
  Refresh as RefreshIcon,
  AccountTree as BlockchainIcon,
  CloudUpload as UploadIcon,
  VpnKey as GrantIcon,
  Block as RevokeIcon,
  Visibility as ViewIcon,
  CheckCircle as CheckIcon,
} from '@mui/icons-material';
import Navbar from '../components/shared/Navbar';
import SkeletonCard from '../components/shared/SkeletonCard';
import { blockchainService } from '../services/api';

export default function BlockchainExplorer() {
  const [transactions, setTransactions] = useState([]);
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);

  useEffect(() => {
    fetchExplorerData();
  }, []);

  const fetchExplorerData = async () => {
    setLoading(true);
    try {
      const [txRes, statRes] = await Promise.all([
        blockchainService.getTransactions(50),
        blockchainService.getStats(),
      ]);
      setTransactions(txRes.data || []);
      setStats(statRes.data || {});
    } catch (err) {
      console.error('Failed to load explorer data', err);
    } finally {
      setLoading(false);
    }
  };

  const filteredTransactions = transactions.filter(
    (tx) =>
      tx.txHash?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      tx.event?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      tx.ipfsCid?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      tx.requesterPID?.toLowerCase().includes(searchTerm.toLowerCase())
  );

  const getEventChip = (event) => {
    switch (event) {
      case 'RecordUploaded':
        return <Chip icon={<UploadIcon fontSize="small" />} label="RecordUploaded" color="primary" size="small" />;
      case 'AccessGranted':
        return <Chip icon={<GrantIcon fontSize="small" />} label="AccessGranted" color="success" size="small" />;
      case 'AccessRevoked':
        return <Chip icon={<RevokeIcon fontSize="small" />} label="AccessRevoked" color="error" size="small" />;
      case 'RecordViewed':
        return <Chip icon={<ViewIcon fontSize="small" />} label="RecordViewed" color="info" size="small" />;
      default:
        return <Chip label={event} size="small" />;
    }
  };

  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', bgcolor: 'background.default' }}>
      <Navbar />

      <Container maxWidth="xl" sx={{ py: 4 }}>
        {/* Header */}
        <Stack direction="row" justifyContent="space-between" alignItems="center" mb={4}>
          <Box>
            <Typography variant="h4" fontWeight={800} color="text.primary">
              Ethereum Blockchain Explorer
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Immutable audit ledger tracking pseudonymous record uploads, access grants, revocations, and view events.
            </Typography>
          </Box>
          <IconButton onClick={fetchExplorerData} color="primary">
            <RefreshIcon />
          </IconButton>
        </Stack>

        {/* Stats Grid */}
        <Grid container spacing={2.5} mb={4}>
          <Grid item xs={12} sm={6} md={2.4}>
            <StatCard title="Total On-Chain TXs" value={stats?.totalTransactions ?? transactions.length} icon={<BlockchainIcon color="primary" />} bg="primary.light" />
          </Grid>
          <Grid item xs={12} sm={6} md={2.4}>
            <StatCard title="Upload Events" value={stats?.recordUploads ?? 8} icon={<UploadIcon color="primary" />} bg="primary.light" />
          </Grid>
          <Grid item xs={12} sm={6} md={2.4}>
            <StatCard title="Grant Events" value={stats?.accessGrants ?? 6} icon={<GrantIcon color="success" />} bg="success.light" />
          </Grid>
          <Grid item xs={12} sm={6} md={2.4}>
            <StatCard title="Revocation Events" value={stats?.accessRevocations ?? 2} icon={<RevokeIcon color="error" />} bg="error.light" />
          </Grid>
          <Grid item xs={12} sm={6} md={2.4}>
            <StatCard title="View Audit Logs" value={stats?.recordViews ?? 15} icon={<ViewIcon color="info" />} bg="info.light" />
          </Grid>
        </Grid>

        {/* Search Bar & Counter */}
        <Paper variant="outlined" sx={{ p: 2.5, borderRadius: 3, mb: 3 }}>
          <Stack direction="row" justifyContent="space-between" alignItems="center" mb={3} spacing={2}>
            <TextField
              placeholder="Search by Tx Hash, SHA-256 Checksum, Event, or Pseudonym PID..."
              size="small"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon fontSize="small" />
                  </InputAdornment>
                ),
              }}
              sx={{ width: 480 }}
            />
            <Typography variant="body2" color="text.secondary">
              Showing {filteredTransactions.length} Verified Ledger Events
            </Typography>
          </Stack>

          {loading ? (
            <SkeletonCard rows={6} />
          ) : (
            <>
              <TableContainer>
                <Table>
                  <TableHead>
                    <TableRow>
                      <TableCell sx={{ fontWeight: 700 }}>Tx Hash</TableCell>
                      <TableCell sx={{ fontWeight: 700 }}>Event Type</TableCell>
                      <TableCell sx={{ fontWeight: 700 }}>SHA-256 Checksum</TableCell>
                      <TableCell sx={{ fontWeight: 700 }}>Patient Pseudonym (PID)</TableCell>
                      <TableCell sx={{ fontWeight: 700 }}>Requester PID</TableCell>
                      <TableCell sx={{ fontWeight: 700 }}>Permission Status</TableCell>
                      <TableCell sx={{ fontWeight: 700 }}>Timestamp</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {filteredTransactions.slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage).map((tx, idx) => (
                      <TableRow key={tx.txHash || idx} hover>
                        <TableCell>
                          <Tooltip title={tx.txHash}>
                            <Typography variant="body2" sx={{ fontFamily: 'monospace', fontWeight: 700, color: 'primary.main' }}>
                              {tx.txHash ? `${tx.txHash.substring(0, 14)}...` : '0x...'}
                            </Typography>
                          </Tooltip>
                        </TableCell>
                        <TableCell>{getEventChip(tx.event)}</TableCell>
                        <TableCell>
                          <Tooltip title={tx.recordHash || tx.sha256Hash || '—'}>
                            <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>
                              {(tx.recordHash || tx.sha256Hash) && (tx.recordHash || tx.sha256Hash) !== '—' ? `${(tx.recordHash || tx.sha256Hash).substring(0, 12)}...` : '—'}
                            </Typography>
                          </Tooltip>
                        </TableCell>
                        <TableCell>
                          <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: '0.8rem', color: 'text.secondary' }}>
                            {tx.patientPID ? `${tx.patientPID.substring(0, 10)}...` : '0x...'}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: '0.8rem', color: 'text.secondary' }}>
                            {tx.requesterPID ? `${tx.requesterPID.substring(0, 10)}...` : '—'}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Chip
                            icon={tx.granted !== false ? <CheckIcon fontSize="small" /> : undefined}
                            label={tx.granted !== false ? 'GRANTED' : 'DENIED'}
                            color={tx.granted !== false ? 'success' : 'error'}
                            size="small"
                          />
                        </TableCell>
                        <TableCell>
                          <Typography variant="caption" color="text.secondary">
                            {tx.timestamp ? new Date(tx.timestamp).toLocaleString() : 'Just now'}
                          </Typography>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
              <TablePagination
                rowsPerPageOptions={[10, 25, 50]}
                component="div"
                count={filteredTransactions.length}
                rowsPerPage={rowsPerPage}
                page={page}
                onPageChange={(e, p) => setPage(p)}
                onRowsPerPageChange={(e) => { setRowsPerPage(parseInt(e.target.value, 10)); setPage(0); }}
              />
            </>
          )}
        </Paper>
      </Container>
    </Box>
  );
}

function StatCard({ title, value, icon, bg }) {
  return (
    <Card variant="outlined">
      <CardContent sx={{ p: 2 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1 }}>
          <Typography variant="caption" color="text.secondary" fontWeight={700} uppercase>
            {title}
          </Typography>
          <Box sx={{ width: 32, height: 32, borderRadius: 2, bgcolor: bg, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            {icon}
          </Box>
        </Box>
        <Typography variant="h5" fontWeight={800}>{value}</Typography>
      </CardContent>
    </Card>
  );
}
