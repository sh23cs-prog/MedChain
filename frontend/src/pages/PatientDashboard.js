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
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  MenuItem,
  Stack,
  IconButton,
  Tooltip,
  Alert,
  Tabs,
  Tab,
  InputAdornment,
  TablePagination,
} from '@mui/material';
import {
  CloudUpload as UploadIcon,
  VpnKey as GrantIcon,
  Block as RevokeIcon,
  AccountBalanceWallet as BlockchainIcon,
  Search as SearchIcon,
  Refresh as RefreshIcon,
  CheckCircle as CheckIcon,
  HourglassEmpty as PendingIcon,
  Description as FileIcon,
  VerifiedUser as ShieldIcon,
  Warning as WarningIcon,
  Psychology as MLIcon,
} from '@mui/icons-material';
import Navbar from '../components/shared/Navbar';
import ConfirmDialog from '../components/shared/ConfirmDialog';
import SkeletonCard from '../components/shared/SkeletonCard';
import { useAuth } from '../context/AuthContext';
import { recordService } from '../services/api';

export default function PatientDashboard() {
  const { user } = useAuth();

  const [records, setRecords] = useState([]);
  const [doctors, setDoctors] = useState([]);
  const [requests, setRequests] = useState([]);
  const [securityAlerts, setSecurityAlerts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [tabValue, setTabValue] = useState(0);
  const [searchTerm, setSearchTerm] = useState('');
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(5);

  // Upload dialog state
  const [uploadOpen, setUploadOpen] = useState(false);
  const [uploadData, setUploadData] = useState({
    title: '',
    description: '',
    recordType: 'LAB_REPORT',
    accessPolicy: '(specialty=Cardiology AND hospital=CityHospital) OR (license=Senior)',
    content: '',
    fileName: '',
    fileSize: '',
  });
  const [fileSelected, setFileSelected] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [notification, setNotification] = useState(null);

  // Grant / Revoke modal
  const [grantOpen, setGrantOpen] = useState(false);
  const [selectedRecord, setSelectedRecord] = useState(null);
  const [selectedDoctorId, setSelectedDoctorId] = useState('');
  const [confirmState, setConfirmState] = useState({ open: false, action: null, doctorId: null });
  const [dashboardError, setDashboardError] = useState(null);

  useEffect(() => {
    fetchDashboardData();
  }, [user]);

  const fetchDashboardData = async () => {
    if (!user) return;
    setLoading(true);
    setDashboardError(null);
    try {
      const [recRes, docRes, reqRes, alertRes] = await Promise.allSettled([
        recordService.getPatientRecords(user.id),
        recordService.getDoctors(),
        recordService.getPatientRequests(),
        recordService.getPatientSecurityAlerts(),
      ]);

      if (recRes.status === 'fulfilled') {
        setRecords(recRes.value.data || []);
      } else {
        const errMsg = recRes.reason?.response?.data?.message || recRes.reason?.message || 'Failed to load medical records';
        setDashboardError(errMsg);
        showNotification(errMsg, 'error');
      }

      if (docRes.status === 'fulfilled') {
        setDoctors(docRes.value.data || []);
      }
      if (reqRes.status === 'fulfilled') {
        setRequests(reqRes.value.data || []);
      }
      if (alertRes.status === 'fulfilled') {
        setSecurityAlerts(alertRes.value.data || []);
      }
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Failed to load dashboard data';
      setDashboardError(msg);
      showNotification(msg, 'error');
    } finally {
      setLoading(false);
    }
  };

  const showNotification = (message, severity = 'success') => {
    setNotification({ message, severity });
    setTimeout(() => setNotification(null), 4000);
  };

  const handleFileChange = (e) => {
    const file = e.target.files[0];
    if (file) {
      setFileSelected(file);
      const reader = new FileReader();
      reader.onload = (uploadEvent) => {
        setUploadData((prev) => ({
          ...prev,
          content: uploadEvent.target.result,
          fileName: file.name,
          fileSize: `${(file.size / 1024).toFixed(1)} KB`,
        }));
      };
      reader.readAsDataURL(file);
    }
  };

  const handleUploadSubmit = async (e) => {
    e.preventDefault();
    setUploading(true);
    try {
      const payload = {
        ...uploadData,
        patientId: user?.id || '1',
        content: uploadData.content || `PATIENT: ${user?.name || 'Patient'} | RECORD: ${uploadData.title}\nDescription: ${uploadData.description || 'No additional notes provided.'}`,
      };
      await recordService.upload(payload);
      showNotification('Record encrypted with AES-256 and uploaded to IPFS + Blockchain!');
      setUploadOpen(false);
      setUploadData({
        title: '',
        description: '',
        recordType: 'LAB_REPORT',
        accessPolicy: '(specialty=Cardiology AND hospital=CityHospital) OR (license=Senior)',
        content: '',
        fileName: '',
        fileSize: '',
      });
      setFileSelected(null);
      fetchDashboardData();
    } catch (err) {
      showNotification(err.response?.data?.message || 'Upload failed', 'error');
    } finally {
      setUploading(false);
    }
  };

  const handleGrantAccess = async (recordId, doctorId) => {
    const targetRecordId = recordId || selectedRecord?.id;
    const targetDoctorId = doctorId || selectedDoctorId;
    if (!targetRecordId || !targetDoctorId) return;
    try {
      await recordService.grantAccess(targetRecordId, targetDoctorId);
      showNotification('Access granted & Proxy Re-Encryption key generated!');
      setGrantOpen(false);
      fetchDashboardData();
    } catch (err) {
      showNotification(err.response?.data?.message || 'Failed to grant access', 'error');
    }
  };

  const handleRevokeAccess = async () => {
    if (!confirmState.doctorId || !selectedRecord) return;
    try {
      await recordService.revokeAccess(selectedRecord.id, confirmState.doctorId);
      showNotification('Doctor access revoked & PRE key invalidated!');
      setConfirmState({ open: false, action: null, doctorId: null });
      fetchDashboardData();
    } catch (err) {
      showNotification(err.response?.data?.message || 'Revocation failed', 'error');
    }
  };

  const handleDismissAlert = async (alertId) => {
    try {
      await recordService.dismissSecurityAlert(alertId);
      fetchDashboardData();
    } catch (err) {
      showNotification('Failed to dismiss alert', 'error');
    }
  };

  const filteredRecords = records.filter(
    (r) =>
      r.title?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      r.recordType?.toLowerCase().includes(searchTerm.toLowerCase())
  );

  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', bgcolor: 'background.default' }}>
      <Navbar />

      <Container maxWidth="xl" sx={{ py: 4 }}>
        {/* Header */}
        <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems={{ xs: 'flex-start', sm: 'center' }} mb={4} spacing={2}>
          <Box>
            <Typography variant="h4" fontWeight={800} color="text.primary">
              Patient Portal
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Manage your confidential health records, CP-ABE policies, and doctor access.
            </Typography>
          </Box>
          <Button
            variant="contained"
            color="primary"
            startIcon={<UploadIcon />}
            onClick={() => setUploadOpen(true)}
            sx={{ px: 3, py: 1.2, fontWeight: 700 }}
          >
            Upload Medical Record
          </Button>
        </Stack>

        {notification && (
          <Alert severity={notification.severity} sx={{ mb: 3 }} onClose={() => setNotification(null)}>
            {notification.message}
          </Alert>
        )}

        {/* Real-time Security Threat Alert Banner */}
        {securityAlerts.length > 0 && (
          <Paper
            elevation={4}
            sx={{
              p: 2.5,
              mb: 4,
              borderRadius: 3,
              bgcolor: 'error.dark',
              color: 'white',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              boxShadow: '0 8px 24px rgba(239, 68, 68, 0.3)',
            }}
          >
            <Stack direction="row" spacing={2} alignItems="center">
              <WarningIcon sx={{ fontSize: 40 }} />
              <Box>
                <Typography variant="h6" fontWeight={900}>
                  🚨 SECURITY THREAT ALERT: Anomalous Access Attempt Blocked!
                </Typography>
                <Typography variant="body2" sx={{ opacity: 0.9 }}>
                  MedChain Machine Learning Engine blocked a suspicious access attempt on record <strong>"{securityAlerts[0]?.recordTitle || securityAlerts[0]?.recordId || 'Medical Record'}"</strong> by {securityAlerts[0]?.doctorName || securityAlerts[0]?.userId || 'Requester'} (Risk Score: {typeof securityAlerts[0]?.riskScore === 'number' ? (securityAlerts[0]?.riskScore <= 1.0 ? securityAlerts[0]?.riskScore * 100 : securityAlerts[0]?.riskScore).toFixed(1) + '%' : 'N/A'} - Risk Level: {securityAlerts[0]?.riskLevel || 'CRITICAL'}).
                </Typography>
              </Box>
            </Stack>
            <Button
              variant="contained"
              color="secondary"
              onClick={() => setTabValue(2)}
              sx={{ fontWeight: 800, px: 2.5, whiteSpace: 'nowrap' }}
            >
              View Threat Details
            </Button>
          </Paper>
        )}

        {/* Stats Grid */}
        <Grid container spacing={3} mb={4}>
          <Grid item xs={12} sm={6} md={3}>
            <Card variant="outlined">
              <CardContent sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                <AvatarBox icon={<FileIcon color="primary" />} bg="primary.light" />
                <Box>
                  <Typography variant="h4" fontWeight={800}>{records.length}</Typography>
                  <Typography variant="caption" color="text.secondary" fontWeight={600}>Total Records</Typography>
                </Box>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <Card variant="outlined">
              <CardContent sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                <AvatarBox icon={<ShieldIcon color="success" />} bg="success.light" />
                <Box>
                  <Typography variant="h4" fontWeight={800}>{doctors.length}</Typography>
                  <Typography variant="caption" color="text.secondary" fontWeight={600}>Registered Doctors</Typography>
                </Box>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <Card variant="outlined">
              <CardContent sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                <AvatarBox icon={<PendingIcon color="warning" />} bg="warning.light" />
                <Box>
                  <Typography variant="h4" fontWeight={800}>{requests.length}</Typography>
                  <Typography variant="caption" color="text.secondary" fontWeight={600}>Pending Access Requests</Typography>
                </Box>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <Card variant="outlined">
              <CardContent sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                <AvatarBox icon={<WarningIcon color="error" />} bg="error.light" />
                <Box>
                  <Typography variant="h4" fontWeight={800} color="error.main">{securityAlerts.length}</Typography>
                  <Typography variant="caption" color="text.secondary" fontWeight={600}>Security Threat Alerts</Typography>
                </Box>
              </CardContent>
            </Card>
          </Grid>
        </Grid>

        {/* Tabs */}
        <Paper elevation={0} sx={{ borderBottom: 1, borderColor: 'divider', mb: 3 }}>
          <Tabs value={tabValue} onChange={(e, val) => setTabValue(val)}>
            <Tab label={`My Records (${records.length})`} sx={{ fontWeight: 700 }} />
            <Tab label={`Access Requests (${requests.length})`} sx={{ fontWeight: 700 }} />
            <Tab
              label={`ML Threat Alerts (${securityAlerts.length})`}
              sx={{ fontWeight: 700, color: securityAlerts.length > 0 ? 'error.main' : 'inherit' }}
            />
          </Tabs>
        </Paper>

        {/* Tab 0: My Records */}
        {tabValue === 0 && (
          <Paper elevation={2} sx={{ p: 3, borderRadius: 3 }}>
            <Stack direction="row" justifyContent="space-between" alignItems="center" mb={3} spacing={2}>
              <TextField
                placeholder="Search records by title or type..."
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
                sx={{ width: 320 }}
              />
              <IconButton onClick={fetchDashboardData}>
                <RefreshIcon />
              </IconButton>
            </Stack>

            {loading ? (
              <SkeletonCard rows={4} />
            ) : dashboardError ? (
              <Box py={4}>
                <Alert
                  severity="error"
                  action={
                    <Button color="inherit" size="small" onClick={fetchDashboardData}>
                      Retry
                    </Button>
                  }
                  sx={{ borderRadius: 2 }}
                >
                  {dashboardError}
                </Alert>
              </Box>
            ) : filteredRecords.length === 0 ? (
              <Box textAlign="center" py={6}>
                <Typography variant="h6" color="text.secondary">No medical records uploaded yet</Typography>
                <Button variant="outlined" startIcon={<UploadIcon />} sx={{ mt: 2 }} onClick={() => setUploadOpen(true)}>
                  Upload First Record
                </Button>
              </Box>
            ) : (
              <>
                <TableContainer>
                  <Table>
                    <TableHead>
                      <TableRow>
                        <TableCell sx={{ fontWeight: 700 }}>Record Title</TableCell>
                        <TableCell sx={{ fontWeight: 700 }}>Type</TableCell>
                        <TableCell sx={{ fontWeight: 700 }}>CP-ABE Policy</TableCell>
                        <TableCell sx={{ fontWeight: 700 }}>SHA-256 Checksum</TableCell>
                        <TableCell sx={{ fontWeight: 700 }}>Authorized Doctors</TableCell>
                        <TableCell align="right" sx={{ fontWeight: 700 }}>Actions</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {filteredRecords.slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage).map((record) => (
                        <TableRow key={record.id} hover>
                          <TableCell>
                            <Typography variant="subtitle2" fontWeight={700}>{record.title}</Typography>
                            <Typography variant="caption" color="text.secondary">{record.fileName || 'file.pdf'}</Typography>
                          </TableCell>
                          <TableCell>
                            <Chip label={record.recordType} color="primary" size="small" />
                          </TableCell>
                          <TableCell>
                            <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                              {record.accessPolicy || 'specialty:CARDIOLOGY'}
                            </Typography>
                          </TableCell>
                          <TableCell>
                            <Tooltip title={record.sha256Hash || 'AES-256-GCM Encrypted Payload Fingerprint'}>
                              <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                                {record.sha256Hash ? `${record.sha256Hash.substring(0, 12)}...` : 'Verified'}
                              </Typography>
                            </Tooltip>
                          </TableCell>
                          <TableCell>
                            <Stack direction="row" spacing={0.5} flexWrap="wrap">
                              {record.authorizedDoctorIds?.map((docId) => {
                                const doc = doctors.find((d) => d.id === docId);
                                return (
                                  <Chip
                                    key={docId}
                                    label={doc ? doc.name : docId}
                                    size="small"
                                    color="success"
                                    onDelete={() => {
                                      setSelectedRecord(record);
                                      setConfirmState({ open: true, action: 'REVOKE', doctorId: docId });
                                    }}
                                  />
                                );
                              })}
                              {(!record.authorizedDoctorIds || record.authorizedDoctorIds.length === 0) && (
                                <Typography variant="caption" color="text.secondary">None</Typography>
                              )}
                            </Stack>
                          </TableCell>
                          <TableCell align="right">
                            <Button
                              size="small"
                              variant="contained"
                              startIcon={<GrantIcon />}
                              onClick={() => {
                                setSelectedRecord(record);
                                setGrantOpen(true);
                              }}
                            >
                              Grant Doctor
                            </Button>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
                <TablePagination
                  rowsPerPageOptions={[5, 10, 25]}
                  component="div"
                  count={filteredRecords.length}
                  rowsPerPage={rowsPerPage}
                  page={page}
                  onPageChange={(e, p) => setPage(p)}
                  onRowsPerPageChange={(e) => { setRowsPerPage(parseInt(e.target.value, 10)); setPage(0); }}
                />
              </>
            )}
          </Paper>
        )}

        {/* Tab 1: Access Requests */}
        {tabValue === 1 && (
          <Paper elevation={2} sx={{ p: 3, borderRadius: 3 }}>
            <Typography variant="h6" fontWeight={700} mb={2}>
              Pending Doctor Access Requests
            </Typography>
            {requests.length === 0 ? (
              <Box textAlign="center" py={6}>
                <Typography color="text.secondary">No pending access requests from doctors</Typography>
              </Box>
            ) : (
              <TableContainer>
                <Table>
                  <TableHead>
                    <TableRow>
                      <TableCell sx={{ fontWeight: 700 }}>Record</TableCell>
                      <TableCell sx={{ fontWeight: 700 }}>Doctor</TableCell>
                      <TableCell sx={{ fontWeight: 700 }}>Status</TableCell>
                      <TableCell align="right" sx={{ fontWeight: 700 }}>Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {requests.map((req) => {
                      const rec = records.find((r) => r.id === req.recordId);
                      const doc = doctors.find((d) => d.id === req.doctorId);
                      return (
                        <TableRow key={req.id}>
                          <TableCell>{rec ? rec.title : req.recordId}</TableCell>
                          <TableCell>{doc ? doc.name : req.doctorId}</TableCell>
                          <TableCell>
                            <Chip label={req.status} color="warning" size="small" />
                          </TableCell>
                          <TableCell align="right">
                            <Stack direction="row" spacing={1} justifyContent="flex-end">
                              <Button
                                size="small"
                                variant="contained"
                                color="success"
                                onClick={() => handleGrantAccess(req.recordId, req.doctorId)}
                              >
                                Approve Access
                              </Button>
                            </Stack>
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </Paper>
        )}

        {/* Tab 2: Security Threat Alerts */}
        {tabValue === 2 && (
          <Paper elevation={2} sx={{ p: 3, borderRadius: 3 }}>
            <Typography variant="h6" fontWeight={700} mb={2} color="error.main" display="flex" alignItems="center" gap={1}>
              <MLIcon color="error" /> Machine Learning Anomaly Security Threat Log
            </Typography>
            {securityAlerts.length === 0 ? (
              <Box textAlign="center" py={6}>
                <Typography color="text.secondary">No security threats detected on your records.</Typography>
              </Box>
            ) : (
              <Stack spacing={2}>
                {securityAlerts.map((alert) => {
                  const rawScore = alert.riskScore;
                  const scorePct = typeof rawScore === 'number'
                    ? (rawScore <= 1.0 ? rawScore * 100 : rawScore).toFixed(1) + '%'
                    : '0.0%';

                  const decimalScore = typeof rawScore === 'number'
                    ? (rawScore <= 1.0 ? rawScore : rawScore / 100)
                    : 0;

                  const computedRiskLevel = decimalScore >= 0.75 ? 'CRITICAL'
                    : decimalScore >= 0.50 ? 'HIGH'
                    : decimalScore >= 0.25 ? 'MEDIUM'
                    : 'LOW';

                  const riskLevel = alert.riskLevel || computedRiskLevel;
                  const rationaleText = alert.rationale || alert.reason || 'No threat rationale detailed.';
                  const txHash = alert.blockchainTxHash || alert.txHash || null;
                  const doctorName = alert.doctorName || alert.userName || alert.userId || 'Unknown Requester';
                  const ipAddr = alert.ipAddress || 'Not available';

                  const getRiskChipColor = (lvl) => {
                    switch (lvl?.toUpperCase()) {
                      case 'CRITICAL': return 'error';
                      case 'HIGH':     return 'warning';
                      case 'MEDIUM':   return 'info';
                      default:         return 'success';
                    }
                  };

                  const getStatusChipColor = (st) => {
                    switch (st?.toUpperCase()) {
                      case 'OPEN':         return 'error';
                      case 'ACKNOWLEDGED': return 'warning';
                      case 'RESOLVED':     return 'success';
                      default:             return 'default';
                    }
                  };

                  return (
                    <Paper key={alert.id} variant="outlined" sx={{ p: 2.5, borderRadius: 3, borderLeft: 6, borderColor: `${getRiskChipColor(riskLevel)}.main`, bgcolor: 'background.paper' }}>
                      <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems={{ xs: 'flex-start', sm: 'center' }} spacing={2}>
                        <Box flexGrow={1}>
                          <Stack direction="row" spacing={1} alignItems="center" mb={1} flexWrap="wrap">
                            <Chip label={`Risk Score: ${scorePct}`} color={getRiskChipColor(riskLevel)} size="small" sx={{ fontWeight: 800 }} />
                            <Chip label={`Risk Level: ${riskLevel}`} color={getRiskChipColor(riskLevel)} size="small" sx={{ fontWeight: 800 }} />
                            <Chip label={`Status: ${alert.status}`} color={getStatusChipColor(alert.status)} variant="outlined" size="small" sx={{ fontWeight: 800 }} />
                            <Typography variant="subtitle1" fontWeight={800} sx={{ ml: 1 }}>
                              Target Record: {alert.recordTitle || alert.recordId || 'Unknown Record'}
                            </Typography>
                          </Stack>
                          <Typography variant="body2" color="text.secondary">
                            <strong>Requester:</strong> {doctorName} • <strong>IP Address:</strong> {ipAddr}
                          </Typography>
                          <Alert severity="error" icon={<WarningIcon />} sx={{ mt: 1.5, py: 0.5 }}>
                            <strong>Threat Rationale:</strong> {rationaleText}
                          </Alert>
                          <Typography variant="caption" color="text.secondary" display="block" mt={1}>
                            Blockchain Audit Transaction: {txHash ? <code style={{ background: '#eee', padding: '2px 6px', borderRadius: 4 }}>{txHash}</code> : <em>Not available</em>} • Timestamp: {new Date(alert.timestamp).toLocaleString()}
                          </Typography>
                        </Box>
                        {alert.status !== 'RESOLVED' && (
                          <Button
                            size="small"
                            variant="outlined"
                            color="error"
                            onClick={() => handleDismissAlert(alert.id)}
                          >
                            Dismiss Alert
                          </Button>
                        )}
                      </Stack>
                    </Paper>
                  );
                })}
              </Stack>
            )}
          </Paper>
        )}
      </Container>

      {/* Upload Dialog */}
      <Dialog open={uploadOpen} onClose={() => setUploadOpen(false)} maxWidth="sm" fullWidth>
        <form onSubmit={handleUploadSubmit}>
          <DialogTitle sx={{ fontWeight: 800 }}>Upload New Medical Record</DialogTitle>
          <DialogContent dividers>
            <Stack spacing={2.5}>
              <TextField
                label="Record Title"
                required
                fullWidth
                value={uploadData.title}
                onChange={(e) => setUploadData({ ...uploadData, title: e.target.value })}
                placeholder="e.g. Annual Blood Panel Report"
              />

              <TextField
                label="Record Type"
                select
                fullWidth
                value={uploadData.recordType}
                onChange={(e) => setUploadData({ ...uploadData, recordType: e.target.value })}
              >
                <MenuItem value="LAB_REPORT">Lab Report</MenuItem>
                <MenuItem value="PRESCRIPTION">Prescription</MenuItem>
                <MenuItem value="SCAN">MRI / X-Ray Scan</MenuItem>
                <MenuItem value="DISCHARGE">Discharge Summary</MenuItem>
                <MenuItem value="CONSULTATION">Consultation Notes</MenuItem>
              </TextField>

              <TextField
                label="Access-Control Policy Selection"
                select
                fullWidth
                value={uploadData.accessPolicy}
                onChange={(e) => setUploadData({ ...uploadData, accessPolicy: e.target.value })}
                helperText="Select Attribute-Based Access-Control Policy for doctor access authorization"
              >
                <MenuItem value="(specialty=Cardiology AND hospital=CityHospital) OR (license=Senior)">
                  Cardiology Dept & Senior Doctors ((specialty=Cardiology AND hospital=CityHospital) OR (license=Senior))
                </MenuItem>
                <MenuItem value="(specialty=Cardiology AND hospital=CityHospital)">
                  Cardiology Department Only (specialty=Cardiology AND hospital=CityHospital)
                </MenuItem>
                <MenuItem value="(specialty=Radiology AND hospital=CityHospital)">
                  Radiology & Imaging Team (specialty=Radiology AND hospital=CityHospital)
                </MenuItem>
                <MenuItem value="(specialty=Neurology AND hospital=CityHospital)">
                  Neurology Department (specialty=Neurology AND hospital=CityHospital)
                </MenuItem>
                <MenuItem value="(specialty=Oncology AND hospital=CancerInstitute)">
                  Oncology Specialists (specialty=Oncology AND hospital=CancerInstitute)
                </MenuItem>
                <MenuItem value="(license=Senior)">
                  Senior Licensed Officers Only (license=Senior)
                </MenuItem>
                <MenuItem value="(department=Pediatrics OR department=Cardiology)">
                  Pediatrics & Cardiology Staff (department=Pediatrics OR department=Cardiology)
                </MenuItem>
                <MenuItem value="specialty:ALL">
                  All Verified Hospital Medical Staff (specialty:ALL)
                </MenuItem>
              </TextField>

              <TextField
                label="Description / Notes"
                multiline
                rows={2}
                fullWidth
                value={uploadData.description}
                onChange={(e) => setUploadData({ ...uploadData, description: e.target.value })}
              />

              <Button variant="outlined" component="label" startIcon={<UploadIcon />}>
                {fileSelected ? fileSelected.name : 'Choose File to Encrypt & Upload'}
                <input type="file" hidden onChange={handleFileChange} />
              </Button>

              {fileSelected && (
                <Alert severity="info" sx={{ py: 0.5 }}>
                  File selected ({uploadData.fileSize}). File will be encrypted using <strong>AES-256-GCM</strong> and pinned to <strong>IPFS</strong>.
                </Alert>
              )}
            </Stack>
          </DialogContent>
          <DialogActions sx={{ px: 3, py: 2 }}>
            <Button onClick={() => setUploadOpen(false)}>Cancel</Button>
            <Button type="submit" variant="contained" disabled={uploading}>
              {uploading ? 'Encrypting & Uploading...' : 'Encrypt & Upload'}
            </Button>
          </DialogActions>
        </form>
      </Dialog>

      {/* Grant Dialog */}
      <Dialog open={grantOpen} onClose={() => setGrantOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle sx={{ fontWeight: 800 }}>Grant Doctor Access</DialogTitle>
        <DialogContent dividers>
          <Typography variant="body2" color="text.secondary" mb={2}>
            Select a licensed doctor to grant access. A Proxy Re-Encryption key will be generated for seamless key delegation.
          </Typography>
          <TextField
            label="Select Doctor"
            select
            fullWidth
            value={selectedDoctorId}
            onChange={(e) => setSelectedDoctorId(e.target.value)}
          >
            {doctors.map((doc) => (
              <MenuItem key={doc.id} value={doc.id}>
                {doc.name} ({doc.attributes?.specialty || 'General'})
              </MenuItem>
            ))}
          </TextField>
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={() => setGrantOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => handleGrantAccess()} disabled={!selectedDoctorId}>
            Generate PRE Key & Grant
          </Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={confirmState.open}
        title="Revoke Access?"
        message="This will invalidate the Proxy Re-Encryption key and revoke doctor access on the blockchain."
        onConfirm={handleRevokeAccess}
        onCancel={() => setConfirmState({ open: false, action: null, doctorId: null })}
      />
    </Box>
  );
}

function AvatarBox({ icon, bg }) {
  return (
    <Box
      sx={{
        width: 48,
        height: 48,
        borderRadius: 3,
        bgcolor: bg,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      {icon}
    </Box>
  );
}
