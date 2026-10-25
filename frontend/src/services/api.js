import axios from 'axios';

const getBaseUrl = () => {
  if (process.env.REACT_APP_API_URL !== undefined && process.env.REACT_APP_API_URL !== '') {
    return process.env.REACT_APP_API_URL.endsWith('/api')
      ? process.env.REACT_APP_API_URL
      : `${process.env.REACT_APP_API_URL}/api`;
  }
  return 'http://localhost:8080/api';
};

const api = axios.create({
  baseURL: getBaseUrl(),
});

api.interceptors.request.use(config => {
  const user = JSON.parse(localStorage.getItem('medchain_user') || '{}');
  if (user.token) {
    config.headers.Authorization = `Bearer ${user.token}`;
  }
  return config;
});

api.interceptors.response.use(
  res => res,
  err => {
    if (err.response?.status === 401) {
      localStorage.removeItem('medchain_user');
      if (window.location.pathname !== '/login' && window.location.pathname !== '/register') {
        window.location.href = '/login';
      }
    }
    return Promise.reject(err);
  }
);

// ─── Authentication Service ───────────────────────────────────────────────────
export const authService = {
  login: (data) => api.post('/auth/login', data),
  register: (data) => api.post('/auth/register', data),
  verifyOtp: (data) => api.post('/auth/verify-otp', data),
  resendOtp: (email) => api.post(`/auth/resend-otp?email=${encodeURIComponent(email)}`),
  forgotPassword: (email) => api.post('/auth/forgot-password', { email }),
  resetPassword: (data) => api.post('/auth/reset-password', data),
  changePassword: (data) => api.post('/auth/change-password', data),
  getMe: () => api.get('/auth/me'),
};

// ─── Record Management Service ────────────────────────────────────────────────
export const recordService = {
  upload: (data) => api.post('/records/upload', data),
  uploadFile: (formData) => api.post('/records/upload-file', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  }),
  getPatientRecords: (patientId) => api.get(patientId ? `/records/patient/${patientId}` : '/records/my'),
  getDoctorRecords: () => api.get('/records/doctor/authorized'),
  viewRecord: (recordId) => api.get(`/records/${recordId}/view`),
  downloadFile: (recordId) => api.get(`/records/${recordId}/download`, { responseType: 'blob' }),
  getDoctors: () => api.get('/records/doctors'),
  getPatients: () => api.get('/records/patients'),
  grantAccess: (recordId, doctorId) => api.post(`/records/${recordId}/grant/${doctorId}`),
  revokeAccess: (recordId, doctorId) => api.post(`/records/${recordId}/revoke/${doctorId}`),
  getPatientRequests: () => api.get('/access/requests'),
  getPatientSecurityAlerts: () => api.get('/ml/alerts'),
  dismissSecurityAlert: (alertId) => api.post(`/ml/alerts/${alertId}/status?status=RESOLVED`),
  requestAccess: (recordId) => api.post(`/records/${recordId}/request-access`),
};

// ─── Patient-Doctor Access Control Lifecycle ──────────────────────────────────
export const accessService = {
  requestAccess: (data) => api.post('/access/request', data),
  approveAccess: (data) => api.post('/access/approve', data),
  rejectAccess: (data) => api.post('/access/reject', data),
  revokeAccess: (data) => api.post('/access/revoke', data),
  getPatientRequests: () => api.get('/access/requests'),
  getDoctorRequests: () => api.get('/access/doctor/requests'),
};

// ─── Sentinel AI Microservice & Dashboard ──────────────────────────────────────
export const sentinelService = {
  getStats: () => api.get('/ml/stats'),
  getAlerts: (status) => api.get(status ? `/ml/alerts?status=${status}` : '/ml/alerts'),
  getPredictions: (limit = 50) => api.get(`/ml/predictions?limit=${limit}`),
  updateAlertStatus: (alertId, status) => api.post(`/ml/alerts/${alertId}/status?status=${status}`),
  evaluateAccess: (data) => api.post('/ml/evaluate', data),
};

// ─── Administrator Service ────────────────────────────────────────────────────
export const adminService = {
  getDashboard: () => api.get('/admin/dashboard'),
  getUsers: () => api.get('/admin/users'),
  toggleUserActive: (userId) => api.post(`/admin/users/${userId}/toggle-active`),
  updateUserRole: (userId, role) => api.post(`/admin/users/${userId}/role?role=${encodeURIComponent(role)}`),
  getAuditLogs: () => api.get('/admin/audit-logs'),
  getSecurityAlerts: () => api.get('/admin/security-alerts'),
  getLogs: () => api.get('/admin/logs'),
  getFlaggedLogs: () => api.get('/admin/logs/flagged'),
  blockUser: (userId) => api.post(`/admin/users/${userId}/block`),
  unblockUser: (userId) => api.post(`/admin/users/${userId}/unblock`),
};

// ─── Blockchain Explorer Service ──────────────────────────────────────────────
export const blockchainService = {
  getTransactions: (limit = 50) => api.get(`/blockchain/transactions?limit=${limit}`),
  verifyRecord: (recordId) => api.get(`/blockchain/verify/${recordId}`),
  getStats: () => api.get('/blockchain/stats'),
  search: (query) => api.get(`/blockchain/search?query=${encodeURIComponent(query)}`),
};

// ─── System Performance Metrics Service ───────────────────────────────────────
export const metricsService = {
  getMetrics: () => api.get('/metrics'),
};

export default api;
