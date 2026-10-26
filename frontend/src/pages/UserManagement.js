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
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  InputAdornment,
  TablePagination,
  TextField,
  CircularProgress,
} from '@mui/material';
import {
  People as UsersIcon,
  LocalHospital as DoctorIcon,
  Person as PatientIcon,
  AdminPanelSettings as AdminIcon,
  Block as BlockIcon,
  CheckCircle as ActivateIcon,
  PersonOff as DeactivateIcon,
  DeleteForever as DeleteIcon,
  Refresh as RefreshIcon,
  Search as SearchIcon,
  ArrowBack as BackIcon,
  WarningAmber as WarningIcon,
} from '@mui/icons-material';
import { Link, useNavigate } from 'react-router-dom';
import Navbar from '../components/shared/Navbar';
import { useAuth } from '../context/AuthContext';
import { adminService } from '../services/api';

export default function UserManagement() {
  const { user } = useAuth();
  const navigate = useNavigate();

  const [usersList, setUsersList] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [roleFilter, setRoleFilter] = useState('ALL');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [notification, setNotification] = useState(null);

  // Action Dialog state: { open: false, type: 'DEACTIVATE'|'ACTIVATE'|'DELETE'|'BLOCK'|'UNBLOCK', user: null, loading: false }
  const [actionDialog, setActionDialog] = useState({ open: false, type: '', user: null, loading: false });

  useEffect(() => {
    fetchUsers();
  }, []);

  const fetchUsers = async () => {
    setLoading(true);
    try {
      const res = await adminService.getUsers();
      setUsersList(res.data || []);
    } catch (err) {
      showNotification('Failed to fetch user directory.', 'error');
    } finally {
      setLoading(false);
    }
  };

  const showNotification = (message, severity = 'success') => {
    setNotification({ message, severity });
    setTimeout(() => setNotification(null), 5000);
  };

  const handleOpenDialog = (type, targetUser) => {
    setActionDialog({ open: true, type, user: targetUser, loading: false });
  };

  const handleCloseDialog = () => {
    if (!actionDialog.loading) {
      setActionDialog({ open: false, type: '', user: null, loading: false });
    }
  };

  const handleConfirmAction = async () => {
    const { type, user: targetUser } = actionDialog;
    if (!targetUser) return;

    setActionDialog((prev) => ({ ...prev, loading: true }));
    try {
      if (type === 'DEACTIVATE') {
        await adminService.deactivateUser(targetUser.id);
        showNotification(`User '${targetUser.name}' was deactivated. All active sessions have been revoked.`, 'warning');
      } else if (type === 'ACTIVATE') {
        await adminService.activateUser(targetUser.id);
        showNotification(`User '${targetUser.name}' account has been reactivated.`, 'success');
      } else if (type === 'BLOCK') {
        await adminService.blockUser(targetUser.id);
        showNotification(`User '${targetUser.name}' was blocked and sessions revoked.`, 'error');
      } else if (type === 'UNBLOCK') {
        await adminService.unblockUser(targetUser.id);
        showNotification(`User '${targetUser.name}' was unblocked.`, 'success');
      } else if (type === 'DELETE') {
        const res = await adminService.deleteUser(targetUser.id);
        showNotification(res.data?.message || `User '${targetUser.name}' was permanently removed.`, 'info');
      }
      handleCloseDialog();
      fetchUsers();
    } catch (err) {
      const errorMsg = err.response?.data?.message || err.message || 'Operation failed.';
      showNotification(errorMsg, 'error');
      setActionDialog((prev) => ({ ...prev, loading: false }));
    }
  };

  const filteredUsers = usersList.filter((u) => {
    const matchesSearch =
      u.name?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      u.email?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      u.pseudonymId?.toLowerCase().includes(searchTerm.toLowerCase());

    const matchesRole = roleFilter === 'ALL' || u.role === roleFilter;

    let matchesStatus = true;
    if (statusFilter === 'ACTIVE') matchesStatus = u.active && !u.blocked;
    else if (statusFilter === 'DEACTIVATED') matchesStatus = !u.active;
    else if (statusFilter === 'BLOCKED') matchesStatus = u.blocked;

    return matchesSearch && matchesRole && matchesStatus;
  });

  const totalPatients = usersList.filter((u) => u.role === 'PATIENT').length;
  const totalDoctors = usersList.filter((u) => u.role === 'DOCTOR').length;
  const totalAdmins = usersList.filter((u) => u.role === 'ADMIN').length;
  const totalActive = usersList.filter((u) => u.active && !u.blocked).length;
  const totalDeactivated = usersList.filter((u) => !u.active).length;
  const totalBlocked = usersList.filter((u) => u.blocked).length;

  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', bgcolor: 'background.default' }}>
      <Navbar />

      <Container maxWidth="xl" sx={{ py: 4, flex: 1 }}>
        {/* Navigation & Header */}
        <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems={{ xs: 'flex-start', sm: 'center' }} mb={3} spacing={2}>
          <Box>
            <Stack direction="row" spacing={1} alignItems="center" mb={0.5}>
              <Button component={Link} to="/admin" startIcon={<BackIcon />} size="small" variant="text" sx={{ textTransform: 'none' }}>
                Admin Portal
              </Button>
              <Typography variant="body2" color="text.secondary">/</Typography>
              <Typography variant="body2" color="text.primary" fontWeight={600}>User Management</Typography>
            </Stack>
            <Typography variant="h4" fontWeight={800} color="text.primary">
              User Directory & Account Governance
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Review registered users, deactivate accounts, revoke active sessions, and safely enforce data retention rules.
            </Typography>
          </Box>
          <Button variant="outlined" startIcon={<RefreshIcon />} onClick={fetchUsers} disabled={loading}>
            Refresh Directory
          </Button>
        </Stack>

        {/* Alerts / Feedback Banner */}
        {notification && (
          <Alert severity={notification.severity} sx={{ mb: 3, borderRadius: 2 }} onClose={() => setNotification(null)}>
            {notification.message}
          </Alert>
        )}

        {/* Metric Cards Summary */}
        <Grid container spacing={2} mb={4}>
          <Grid item xs={12} sm={4} md={2}>
            <Card variant="outlined" sx={{ borderRadius: 2 }}>
              <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
                <Typography variant="caption" color="text.secondary" fontWeight={600}>Total Users</Typography>
                <Typography variant="h5" fontWeight={800}>{usersList.length}</Typography>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={12} sm={4} md={2}>
            <Card variant="outlined" sx={{ borderRadius: 2 }}>
              <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
                <Typography variant="caption" color="success.main" fontWeight={600}>Active Accounts</Typography>
                <Typography variant="h5" fontWeight={800} color="success.main">{totalActive}</Typography>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={12} sm={4} md={2}>
            <Card variant="outlined" sx={{ borderRadius: 2 }}>
              <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
                <Typography variant="caption" color="warning.main" fontWeight={600}>Deactivated</Typography>
                <Typography variant="h5" fontWeight={800} color="warning.main">{totalDeactivated}</Typography>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={12} sm={4} md={2}>
            <Card variant="outlined" sx={{ borderRadius: 2 }}>
              <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
                <Typography variant="caption" color="error.main" fontWeight={600}>Blocked</Typography>
                <Typography variant="h5" fontWeight={800} color="error.main">{totalBlocked}</Typography>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={12} sm={4} md={2}>
            <Card variant="outlined" sx={{ borderRadius: 2 }}>
              <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
                <Typography variant="caption" color="info.main" fontWeight={600}>Patients</Typography>
                <Typography variant="h5" fontWeight={800}>{totalPatients}</Typography>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={12} sm={4} md={2}>
            <Card variant="outlined" sx={{ borderRadius: 2 }}>
              <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
                <Typography variant="caption" color="secondary.main" fontWeight={600}>Doctors / Admins</Typography>
                <Typography variant="h5" fontWeight={800}>{totalDoctors + totalAdmins}</Typography>
              </CardContent>
            </Card>
          </Grid>
        </Grid>

        {/* Table & Controls Container */}
        <Paper elevation={2} sx={{ p: 3, borderRadius: 3 }}>
          {/* Search and Filter Row */}
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} justifyContent="space-between" alignItems={{ xs: 'stretch', md: 'center' }} mb={3}>
            <TextField
              placeholder="Search by name, email, or pseudonym PID..."
              size="small"
              value={searchTerm}
              onChange={(e) => { setSearchTerm(e.target.value); setPage(0); }}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon fontSize="small" />
                  </InputAdornment>
                ),
              }}
              sx={{ width: { xs: '100%', md: 360 } }}
            />

            <Stack direction="row" spacing={1} flexWrap="wrap" alignItems="center">
              <Typography variant="caption" color="text.secondary" fontWeight={700}>Role:</Typography>
              {['ALL', 'PATIENT', 'DOCTOR', 'ADMIN'].map((r) => (
                <Chip
                  key={r}
                  label={r}
                  size="small"
                  clickable
                  color={roleFilter === r ? 'primary' : 'default'}
                  variant={roleFilter === r ? 'filled' : 'outlined'}
                  onClick={() => { setRoleFilter(r); setPage(0); }}
                />
              ))}

              <Box sx={{ width: 12 }} />

              <Typography variant="caption" color="text.secondary" fontWeight={700}>Status:</Typography>
              {['ALL', 'ACTIVE', 'DEACTIVATED', 'BLOCKED'].map((s) => (
                <Chip
                  key={s}
                  label={s}
                  size="small"
                  clickable
                  color={statusFilter === s ? (s === 'ACTIVE' ? 'success' : s === 'DEACTIVATED' ? 'warning' : 'error') : 'default'}
                  variant={statusFilter === s ? 'filled' : 'outlined'}
                  onClick={() => { setStatusFilter(s); setPage(0); }}
                />
              ))}
            </Stack>
          </Stack>

          {/* Users Table */}
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell sx={{ fontWeight: 700 }}>User</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>Role</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>Pseudonym PID</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>Account Status</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>Records</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>Registered</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 700 }}>Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {loading ? (
                  <TableRow>
                    <TableCell colSpan={7} align="center" sx={{ py: 6 }}>
                      <CircularProgress size={32} />
                      <Typography variant="body2" color="text.secondary" mt={1}>Loading registered users...</Typography>
                    </TableCell>
                  </TableRow>
                ) : filteredUsers.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={7} align="center" sx={{ py: 6 }}>
                      <Typography variant="body1" color="text.secondary">No users found matching current filters.</Typography>
                    </TableCell>
                  </TableRow>
                ) : (
                  filteredUsers.slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage).map((u) => {
                    const isSelf = user?.email?.toLowerCase() === u.email?.toLowerCase();
                    return (
                      <TableRow key={u.id} hover>
                        {/* Name & Email */}
                        <TableCell>
                          <Typography variant="subtitle2" fontWeight={700} color="text.primary">
                            {u.name} {isSelf && <Chip label="You" size="small" color="primary" sx={{ height: 18, fontSize: '0.65rem', ml: 0.5 }} />}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            {u.email}
                          </Typography>
                        </TableCell>

                        {/* Role */}
                        <TableCell>
                          <Chip
                            label={u.role}
                            size="small"
                            color={u.role === 'PATIENT' ? 'info' : u.role === 'DOCTOR' ? 'success' : 'secondary'}
                            icon={u.role === 'PATIENT' ? <PatientIcon /> : u.role === 'DOCTOR' ? <DoctorIcon /> : <AdminIcon />}
                          />
                        </TableCell>

                        {/* Pseudonym PID */}
                        <TableCell>
                          <Tooltip title={u.pseudonymId || 'Blockchain Pseudonym ID'}>
                            <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                              {u.pseudonymId ? `${u.pseudonymId.substring(0, 10)}...` : 'Pending'}
                            </Typography>
                          </Tooltip>
                        </TableCell>

                        {/* Status Badges */}
                        <TableCell>
                          <Stack direction="row" spacing={0.5} alignItems="center">
                            {u.blocked ? (
                              <Chip label="BLOCKED" size="small" color="error" variant="filled" />
                            ) : u.active ? (
                              <Chip label="ACTIVE" size="small" color="success" variant="filled" />
                            ) : (
                              <Chip label="DEACTIVATED" size="small" color="warning" variant="filled" />
                            )}
                          </Stack>
                        </TableCell>

                        {/* Record Count */}
                        <TableCell>
                          <Typography variant="body2" fontWeight={600}>
                            {u.recordCount ?? 0}
                          </Typography>
                        </TableCell>

                        {/* Registered Date */}
                        <TableCell>
                          <Typography variant="caption" color="text.secondary">
                            {u.createdAt ? new Date(u.createdAt).toLocaleDateString() : 'N/A'}
                          </Typography>
                        </TableCell>

                        {/* Actions */}
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
                                onClick={() => handleOpenDialog('DEACTIVATE', u)}
                              >
                                Deactivate
                              </Button>
                            ) : (
                              <Button
                                size="small"
                                variant="outlined"
                                color="success"
                                startIcon={<ActivateIcon />}
                                disabled={isSelf}
                                onClick={() => handleOpenDialog('ACTIVATE', u)}
                              >
                                Activate
                              </Button>
                            )}

                            {/* Block / Unblock Button */}
                            {u.blocked ? (
                              <Button
                                size="small"
                                variant="text"
                                color="info"
                                disabled={isSelf}
                                onClick={() => handleOpenDialog('UNBLOCK', u)}
                              >
                                Unblock
                              </Button>
                            ) : (
                              <Button
                                size="small"
                                variant="text"
                                color="error"
                                disabled={isSelf}
                                onClick={() => handleOpenDialog('BLOCK', u)}
                              >
                                Block
                              </Button>
                            )}

                            {/* Delete User Button */}
                            <Tooltip title={isSelf ? "Cannot delete own account" : "Permanently remove user"}>
                              <span>
                                <IconButton
                                  size="small"
                                  color="error"
                                  disabled={isSelf}
                                  onClick={() => handleOpenDialog('DELETE', u)}
                                >
                                  <DeleteIcon fontSize="small" />
                                </IconButton>
                              </span>
                            </Tooltip>
                          </Stack>
                        </TableCell>
                      </TableRow>
                    );
                  })
                )}
              </TableBody>
            </Table>
          </TableContainer>

          <TablePagination
            rowsPerPageOptions={[5, 10, 25, 50]}
            component="div"
            count={filteredUsers.length}
            rowsPerPage={rowsPerPage}
            page={page}
            onPageChange={(e, p) => setPage(p)}
            onRowsPerPageChange={(e) => { setRowsPerPage(parseInt(e.target.value, 10)); setPage(0); }}
          />
        </Paper>
      </Container>

      {/* Confirmation & Action Dialog */}
      <Dialog
        open={actionDialog.open}
        onClose={handleCloseDialog}
        maxWidth="sm"
        fullWidth
        PaperProps={{ sx: { borderRadius: 3, p: 1 } }}
      >
        <DialogTitle sx={{ fontWeight: 800, display: 'flex', alignItems: 'center', gap: 1 }}>
          <WarningIcon color={actionDialog.type === 'DELETE' ? 'error' : actionDialog.type === 'DEACTIVATE' ? 'warning' : 'primary'} />
          {actionDialog.type === 'DEACTIVATE' && 'Confirm Account Deactivation'}
          {actionDialog.type === 'ACTIVATE' && 'Confirm Account Reactivation'}
          {actionDialog.type === 'BLOCK' && 'Confirm Account Block'}
          {actionDialog.type === 'UNBLOCK' && 'Confirm Account Unblock'}
          {actionDialog.type === 'DELETE' && 'Confirm Permanent User Removal'}
        </DialogTitle>

        <DialogContent dividers>
          {actionDialog.type === 'DEACTIVATE' && (
            <DialogContentText>
              Are you sure you want to <strong>deactivate</strong> the account for{' '}
              <strong>{actionDialog.user?.name}</strong> ({actionDialog.user?.email})?
              <br /><br />
              • All active JWT sessions will be <strong>revoked immediately</strong>.<br />
              • The user will be prevented from logging in.<br />
              • <strong>Retention Policy:</strong> All patient medical records, encryption keys, and immutable blockchain audit logs will remain safely preserved.
            </DialogContentText>
          )}

          {actionDialog.type === 'ACTIVATE' && (
            <DialogContentText>
              Reactivate the account for <strong>{actionDialog.user?.name}</strong> ({actionDialog.user?.email})?
              <br /><br />
              The user will regain ability to log in and access their medical records and services.
            </DialogContentText>
          )}

          {actionDialog.type === 'BLOCK' && (
            <DialogContentText>
              Block the account for <strong>{actionDialog.user?.name}</strong> ({actionDialog.user?.email})?
              <br /><br />
              The user's active session will be revoked and they will be barred from authentication.
            </DialogContentText>
          )}

          {actionDialog.type === 'UNBLOCK' && (
            <DialogContentText>
              Unblock <strong>{actionDialog.user?.name}</strong> ({actionDialog.user?.email})?
            </DialogContentText>
          )}

          {actionDialog.type === 'DELETE' && (
            <Box>
              <DialogContentText color="error.main" fontWeight={600} mb={1.5}>
                ⚠️ High-Risk Action: Permanent Deletion
              </DialogContentText>
              <DialogContentText>
                Are you sure you want to permanently delete user{' '}
                <strong>{actionDialog.user?.name}</strong> ({actionDialog.user?.email}, Role: {actionDialog.user?.role})?
                <br /><br />
                <strong>Healthcare Data Retention Policy Notice:</strong><br />
                • If this patient has <strong>active medical records</strong> on file, permanent deletion will be <strong>rejected</strong> by the backend to satisfy HIPAA/medical record retention requirements. Deactivation should be used instead.<br />
                • If deletion proceeds, ephemeral OTPs and access requests will be cleaned up.<br />
                • Immutable blockchain audit logs and security telemetry will be retained for regulatory compliance.<br />
                • This action cannot be undone.
              </DialogContentText>
            </Box>
          )}
        </DialogContent>

        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={handleCloseDialog} disabled={actionDialog.loading} color="inherit">
            Cancel
          </Button>
          <Button
            onClick={handleConfirmAction}
            disabled={actionDialog.loading}
            variant="contained"
            color={actionDialog.type === 'DELETE' ? 'error' : actionDialog.type === 'DEACTIVATE' ? 'warning' : 'primary'}
            startIcon={actionDialog.loading ? <CircularProgress size={16} color="inherit" /> : null}
          >
            {actionDialog.type === 'DEACTIVATE' && 'Deactivate Account'}
            {actionDialog.type === 'ACTIVATE' && 'Reactivate Account'}
            {actionDialog.type === 'BLOCK' && 'Block Account'}
            {actionDialog.type === 'UNBLOCK' && 'Unblock Account'}
            {actionDialog.type === 'DELETE' && 'Permanently Delete'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}