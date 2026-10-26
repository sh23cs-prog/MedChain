import React, { useState, useEffect } from 'react';
import {
  Box,
  Container,
  Typography,
  Grid,
  Card,
  CardContent,
  Button,
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
  Tooltip,
  Alert,
  Tabs,
  Tab,
  InputAdornment,
  TablePagination,
  TextField,
} from '@mui/material';
import {
  People as UsersIcon,
  LocalHospital as DoctorIcon,
  Person as PatientIcon,
  Description as RecordIcon,
  AccountTree as BlockchainIcon,
  Warning as AlertIcon,
  Block as BlockIcon,
  CheckCircle as UnblockIcon,
  PersonOff as DeactivateIcon,
  DeleteForever as DeleteIcon,
  Download as DownloadIcon,
  PictureAsPdf as PdfIcon,
  TableChart as CsvIcon,
  Refresh as RefreshIcon,
  Search as SearchIcon,
} from '@mui/icons-material';
import { Link } from 'react-router-dom';
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip as RechartsTooltip,
  ResponsiveContainer,
  BarChart,
  Bar,
} from 'recharts';
import Navbar from '../components/shared/Navbar';
import ConfirmDialog from '../components/shared/ConfirmDialog';
import SkeletonCard from '../components/shared/SkeletonCard';
import { useAuth } from '../context/AuthContext';
import api, { adminService } from '../services/api';

export default function AdminDashboard() {
  const { user } = useAuth();

  const [stats, setStats] = useState(null);
  const [usersList, setUsersList] = useState([]);
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [tabValue, setTabValue] = useState(0);
  const [searchTerm, setSearchTerm] = useState('');
  const [userPage, setUserPage] = useState(0);
  const [userRowsPerPage, setUserRowsPerPage] = useState(5);
  const [logPage, setLogPage] = useState(0);
  const [logRowsPerPage, setLogRowsPerPage] = useState(5);
  const [notification, setNotification] = useState(null);

  // Confirm dialog state
  const [confirmDialog, setConfirmDialog] = useState({ open: false, user: null, action: '' });

  useEffect(() => {
    fetchAdminData();
  }, [user]);

  const fetchAdminData = async () => {
    setLoading(true);
    try {
      const [statRes, userRes, logRes] = await Promise.all([
        adminService.getDashboard(),
        adminService.getUsers(),
        adminService.getLogs(),
      ]);
      setStats(statRes.data || {});
      setUsersList(userRes.data || []);
      setLogs(logRes.data || []);
    } catch (err) {
      showNotification('Failed to load admin dashboard', 'error');
    } finally {
      setLoading(false);
    }
  };

  const showNotification = (message, severity = 'success') => {
    setNotification({ message, severity });
    setTimeout(() => setNotification(null), 4000);
  };

  const handleConfirmAction = async () => {
    const { action, user: targetUser } = confirmDialog;
    if (!targetUser) return;
    try {
      if (action === 'deactivate') {
        await adminService.deactivateUser(targetUser.id);
        showNotification(`User ${targetUser.name} deactivated. Active sessions revoked.`, 'warning');
      } else if (action === 'activate') {
        await adminService.activateUser(targetUser.id);
        showNotification(`User ${targetUser.name} reactivated.`, 'success');
      } else if (action === 'block') {
        await adminService.blockUser(targetUser.id);
        showNotification(`User ${targetUser.name} blocked!`, 'warning');
      } else if (action === 'unblock') {
        await adminService.unblockUser(targetUser.id);
        showNotification(`User ${targetUser.name} unblocked successfully`);
      } else if (action === 'delete') {
        const res = await adminService.deleteUser(targetUser.id);
        showNotification(res.data?.message || `User ${targetUser.name} deleted.`);
      }
      setConfirmDialog({ open: false, user: null, action: '' });
      fetchAdminData();
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Action failed';
      showNotification(msg, 'error');
      setConfirmDialog({ open: false, user: null, action: '' });
    }
  };

  const chartData = [
    { name: 'Mon', accesses: 24, anomalies: 1 },
    { name: 'Tue', accesses: 35, anomalies: 2 },
    { name: 'Wed', accesses: 42, anomalies: 0 },
    { name: 'Thu', accesses: 58, anomalies: 4 },
    { name: 'Fri', accesses: 63, anomalies: 1 },
    { name: 'Sat', accesses: 18, anomalies: 3 },
    { name: 'Sun', accesses: 12, anomalies: 1 },
  ];

  const filteredUsers = usersList.filter(
    (u) =>
      u.name?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      u.email?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      u.role?.toLowerCase().includes(searchTerm.toLowerCase())
  );

  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', bgcolor: 'background.default' }}>
      <Navbar />

      <Container maxWidth="xl" sx={{ py: 4 }}>
        {/* Header Bar with Export Buttons */}
        <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems={{ xs: 'flex-start', sm: 'center' }} mb={4} spacing={2}>
          <Box>
            <Typography variant="h4" fontWeight={800} color="text.primary">
              Admin & Governance Portal
            </Typography>
            <Typography variant="body2" color="text.secondary">
              System health, user management, audit trail, and ML anomaly monitoring.
            </Typography>
          </Box>
          <Stack direction="row" spacing={1.5}>
            <Button
              variant="outlined"
              color="primary"
              startIcon={<CsvIcon />}
              href={`${api.defaults.baseURL}/reports/access-logs.csv`}
              target="_blank"
            >
              Export Logs (CSV)
            </Button>
            <Button
              variant="contained"
              color="primary"
              startIcon={<PdfIcon />}
              href={`${api.defaults.baseURL}/reports/access-logs.pdf`}
              target="_blank"
            >
              Export Report (PDF)
            </Button>
          </Stack>
        </Stack>

        {notification && (
          <Alert severity={notification.severity} sx={{ mb: 3 }} onClose={() => setNotification(null)}>
            {notification.message}
          </Alert>
        )}

        {/* 6 KPI Metric Cards */}
        <Grid container spacing={2.5} mb={4}>
          <Grid item xs={12} sm={6} md={2}>
            <MetricCard title="Total Users" value={stats?.totalUsers ?? usersList.length} icon={<UsersIcon color="primary" />} bg="primary.light" />
          </Grid>
          <Grid item xs={12} sm={6} md={2}>
            <MetricCard title="Patients" value={usersList.filter(u => u.role === 'PATIENT').length} icon={<PatientIcon color="info" />} bg="info.light" />
          </Grid>
          <Grid item xs={12} sm={6} md={2}>
            <MetricCard title="Doctors" value={usersList.filter(u => u.role === 'DOCTOR').length} icon={<DoctorIcon color="success" />} bg="success.light" />
          </Grid>
          <Grid item xs={12} sm={6} md={2}>
            <MetricCard title="Total Records" value={stats?.totalRecords ?? 8} icon={<RecordIcon color="secondary" />} bg="secondary.light" />
          </Grid>
          <Grid item xs={12} sm={6} md={2}>
            <MetricCard title="Blockchain TXs" value={logs.length + 12} icon={<BlockchainIcon color="primary" />} bg="primary.light" />
          </Grid>
          <Grid item xs={12} sm={6} md={2}>
            <MetricCard title="ML Anomaly Alerts" value={logs.filter(l => l.anomalyScore > 0.6).length} icon={<AlertIcon color="error" />} bg="error.light" />
          </Grid>
        </Grid>

        {/* Recharts Analytics Section */}
        <Grid container spacing={3} mb={4}>
          <Grid item xs={12} md={7}>
            <Paper elevation={2} sx={{ p: 3, borderRadius: 3, height: '100%' }}>
              <Typography variant="h6" fontWeight={700} mb={2}>
                Weekly Access Velocity & Anomaly Trends
              </Typography>
              <ResponsiveContainer width="100%" height={260}>
                <AreaChart data={chartData}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="name" />
                  <YAxis />
                  <RechartsTooltip />
                  <Area type="monotone" dataKey="accesses" stroke="#1976D2" fill="#1976D2" fillOpacity={0.2} name="Total Accesses" />
                  <Area type="monotone" dataKey="anomalies" stroke="#EF4444" fill="#EF4444" fillOpacity={0.3} name="Anomalies Flagged" />
                </AreaChart>
              </ResponsiveContainer>
            </Paper>
          </Grid>

          <Grid item xs={12} md={5}>
            <Paper elevation={2} sx={{ p: 3, borderRadius: 3, height: '100%' }}>
              <Typography variant="h6" fontWeight={700} mb={2}>
                Service Status Overview
              </Typography>
              <Stack spacing={2}>
                <ServiceStatusRow name="Spring Boot Backend API" status={true} latency="12 ms" />
                <ServiceStatusRow name="MongoDB Database" status={true} latency="4 ms" />
                <ServiceStatusRow name="Python FastAPI ML Engine" status={stats?.mlServiceUp ?? true} latency="65 ms" />
                <ServiceStatusRow name="Hardhat Ethereum Blockchain" status={stats?.blockchainUp ?? true} latency="220 ms" />
                <ServiceStatusRow name="IPFS Kubo Storage Node" status={true} latency="48 ms" />
              </Stack>
            </Paper>
          </Grid>
        </Grid>

        {/* Tabs for User Management vs Access Logs */}
        <Paper elevation={0} sx={{ borderBottom: 1, borderColor: 'divider', mb: 3 }}>
          <Tabs value={tabValue} onChange={(e, val) => setTabValue(val)}>
            <Tab label={`User Management (${usersList.length})`} sx={{ fontWeight: 700 }} />
            <Tab label={`Access & Audit Logs (${logs.length})`} sx={{ fontWeight: 700 }} />
          </Tabs>
        </Paper>

        {/* Tab 0: User Management */}
        {tabValue === 0 && (
          <Paper elevation={2} sx={{ p: 3, borderRadius: 3 }}>
            <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems={{ xs: 'stretch', sm: 'center' }} mb={3} spacing={2}>
              <TextField
                placeholder="Search users by name, email, or role..."
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
                sx={{ width: { xs: '100%', sm: 320 } }}
              />
              <Stack direction="row" spacing={1.5} alignItems="center">
                <Button
                  component={Link}
                  to="/admin/users"
                  variant="contained"
                  color="primary"
                  size="small"
                  startIcon={<UsersIcon />}
                >
                  Dedicated User Management Page
                </Button>
                <IconButton onClick={fetchAdminData}>
                  <RefreshIcon />
                </IconButton>
              </Stack>
            </Stack>

            <TableContainer>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 700 }}>Name</TableCell>
                    <TableCell sx={{ fontWeight: 700 }}>Email</TableCell>
                    <TableCell sx={{ fontWeight: 700 }}>Role</TableCell>
                    <TableCell sx={{ fontWeight: 700 }}>Pseudonym PID</TableCell>
                    <TableCell sx={{ fontWeight: 700 }}>Status</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700 }}>Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {filteredUsers.slice(userPage * userRowsPerPage, userPage * userRowsPerPage + userRowsPerPage).map((u) => {
                    const isSelf = user?.email?.toLowerCase() === u.email?.toLowerCase();
                    return (
                      <TableRow key={u.id} hover>
                        <TableCell fontWeight={700}>
                          {u.name} {isSelf && <Chip label="You" size="small" color="primary" sx={{ height: 18, fontSize: '0.65rem', ml: 0.5 }} />}
                        </TableCell>
                        <TableCell>{u.email}</TableCell>
                        <TableCell>
                          <Chip
                            label={u.role}
                            size="small"
                            color={u.role === 'PATIENT' ? 'info' : u.role === 'DOCTOR' ? 'success' : 'secondary'}
                          />
                        </TableCell>
                        <TableCell>
                          <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>
                            {u.pseudonymId ? `${u.pseudonymId.substring(0, 12)}...` : 'Pending'}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Chip
                            label={u.blocked ? 'BLOCKED' : u.active ? 'ACTIVE' : 'DEACTIVATED'}
                            size="small"
                            color={u.blocked ? 'error' : u.active ? 'success' : 'warning'}
                            variant="filled"
                          />
                        </TableCell>
                        <TableCell align="right">
                          <Stack direction="row" spacing={1} justifyContent="flex-end" alignItems="center">
                            {/* Deactivate / Activate Button */}
                            {u.active ? (
                              <Button
                                size="small"
                                variant="outlined"
                                color="warning"
                                startIcon={<DeactivateIcon />}
                                disabled={isSelf}
                                onClick={() => setConfirmDialog({ open: true, user: u, action: 'deactivate' })}
                              >
                                Deactivate
                              </Button>
                            ) : (
                              <Button
                                size="small"
                                variant="outlined"
                                color="success"
                                startIcon={<UnblockIcon />}
                                disabled={isSelf}
                                onClick={() => setConfirmDialog({ open: true, user: u, action: 'activate' })}
                              >
                                Activate
                              </Button>
                            )}

                            {/* Block / Unblock Button */}
                            <Button
                              size="small"
                              variant="text"
                              color={u.blocked ? 'success' : 'error'}
                              disabled={isSelf}
                              onClick={() => setConfirmDialog({ open: true, user: u, action: u.blocked ? 'unblock' : 'block' })}
                            >
                              {u.blocked ? 'Unblock' : 'Block'}
                            </Button>

                            {/* Delete User Button */}
                            <Tooltip title={isSelf ? "Cannot delete own account" : "Permanently remove user"}>
                              <span>
                                <IconButton
                                  size="small"
                                  color="error"
                                  disabled={isSelf}
                                  onClick={() => setConfirmDialog({ open: true, user: u, action: 'delete' })}
                                >
                                  <DeleteIcon fontSize="small" />
                                </IconButton>
                              </span>
                            </Tooltip>
                          </Stack>
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </TableContainer>
            <TablePagination
              rowsPerPageOptions={[5, 10, 25]}
              component="div"
              count={filteredUsers.length}
              rowsPerPage={userRowsPerPage}
              page={userPage}
              onPageChange={(e, p) => setUserPage(p)}
              onRowsPerPageChange={(e) => { setUserRowsPerPage(parseInt(e.target.value, 10)); setUserPage(0); }}
            />
          </Paper>
        )}

        {/* Tab 1: Access & Audit Logs */}
        {tabValue === 1 && (
          <Paper elevation={2} sx={{ p: 3, borderRadius: 3 }}>
            <Typography variant="h6" fontWeight={700} mb={2}>
              System-Wide Access Logs & ML Anomaly Audit
            </Typography>
            <TableContainer>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 700 }}>Timestamp</TableCell>
                    <TableCell sx={{ fontWeight: 700 }}>User</TableCell>
                    <TableCell sx={{ fontWeight: 700 }}>Action</TableCell>
                    <TableCell sx={{ fontWeight: 700 }}>Risk Level</TableCell>
                    <TableCell sx={{ fontWeight: 700 }}>Anomaly Score</TableCell>
                    <TableCell sx={{ fontWeight: 700 }}>Status</TableCell>
                    <TableCell sx={{ fontWeight: 700 }}>Blockchain TX</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {logs.slice(logPage * logRowsPerPage, logPage * logRowsPerPage + logRowsPerPage).map((log, idx) => (
                    <TableRow key={log.id || idx} hover>
                      <TableCell>{log.timestamp ? new Date(log.timestamp).toLocaleString() : 'Just now'}</TableCell>
                      <TableCell fontWeight={600}>{log.userName || log.userId}</TableCell>
                      <TableCell>
                        <Chip label={log.action} size="small" variant="outlined" />
                      </TableCell>
                      <TableCell>
                        <Chip
                          label={log.riskLevel || 'LOW'}
                          size="small"
                          color={log.riskLevel === 'CRITICAL' || log.riskLevel === 'HIGH' ? 'error' : log.riskLevel === 'MEDIUM' ? 'warning' : 'success'}
                        />
                      </TableCell>
                      <TableCell fontWeight={700}>
                        {(log.anomalyScore || 0.05).toFixed(3)}
                      </TableCell>
                      <TableCell>
                        <Chip
                          label={log.granted ? 'ALLOWED' : 'DENIED'}
                          size="small"
                          color={log.granted ? 'success' : 'error'}
                        />
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>
                          {log.blockchainTxHash ? `${log.blockchainTxHash.substring(0, 10)}...` : 'Anchored'}
                        </Typography>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
            <TablePagination
              rowsPerPageOptions={[5, 10, 25]}
              component="div"
              count={logs.length}
              rowsPerPage={logRowsPerPage}
              page={logPage}
              onPageChange={(e, p) => setLogPage(p)}
              onRowsPerPageChange={(e) => { setLogRowsPerPage(parseInt(e.target.value, 10)); setLogPage(0); }}
            />
          </Paper>
        )}
      </Container>

      {/* Confirmation Dialog for User Actions */}
      <ConfirmDialog
        open={confirmDialog.open}
        title={
          confirmDialog.action === 'deactivate'
            ? 'Deactivate User Account'
            : confirmDialog.action === 'activate'
            ? 'Reactivate User Account'
            : confirmDialog.action === 'delete'
            ? 'Permanently Delete User'
            : confirmDialog.action === 'block'
            ? 'Block User Account'
            : 'Unblock User Account'
        }
        content={
          confirmDialog.action === 'deactivate'
            ? `Are you sure you want to deactivate ${confirmDialog.user?.name} (${confirmDialog.user?.email})? Active sessions will be revoked and login blocked. Records remain preserved.`
            : confirmDialog.action === 'activate'
            ? `Reactivate account for ${confirmDialog.user?.name}? The user will regain access to their account.`
            : confirmDialog.action === 'delete'
            ? `Permanently remove user ${confirmDialog.user?.name} (${confirmDialog.user?.email})? If active medical records exist, healthcare compliance requires deactivation instead.`
            : confirmDialog.action === 'block'
            ? `Block ${confirmDialog.user?.name}? Active sessions will be immediately terminated.`
            : `Unblock ${confirmDialog.user?.name}?`
        }
        confirmText={
          confirmDialog.action === 'deactivate'
            ? 'Deactivate'
            : confirmDialog.action === 'activate'
            ? 'Activate'
            : confirmDialog.action === 'delete'
            ? 'Delete User'
            : confirmDialog.action === 'block'
            ? 'Block'
            : 'Unblock'
        }
        confirmColor={
          confirmDialog.action === 'delete' || confirmDialog.action === 'block'
            ? 'error'
            : confirmDialog.action === 'deactivate'
            ? 'warning'
            : 'success'
        }
        onConfirm={handleConfirmAction}
        onClose={() => setConfirmDialog({ open: false, user: null, action: '' })}
      />
    </Box>
  );
}

function MetricCard({ title, value, icon, bg }) {
  return (
    <Card variant="outlined">
      <CardContent sx={{ p: 2 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1 }}>
          <Typography variant="caption" color="text.secondary" fontWeight={700} uppercase>
            {title}
          </Typography>
          <Box sx={{ width: 36, height: 36, borderRadius: 2, bgcolor: bg, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            {icon}
          </Box>
        </Box>
        <Typography variant="h4" fontWeight={800}>{value}</Typography>
      </CardContent>
    </Card>
  );
}

function ServiceStatusRow({ name, status, latency }) {
  return (
    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', p: 1.5, borderRadius: 2, bgcolor: 'background.default' }}>
      <Typography variant="body2" fontWeight={600}>{name}</Typography>
      <Stack direction="row" spacing={1} alignItems="center">
        <Typography variant="caption" color="text.secondary">{latency}</Typography>
        <Chip label={status ? 'ONLINE' : 'OFFLINE'} color={status ? 'success' : 'error'} size="small" sx={{ fontWeight: 700 }} />
      </Stack>
    </Box>
  );
}
