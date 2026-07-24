import axios from 'axios';
import { MOCK_USERS, MOCK_LOGS, MOCK_STATS, MOCK_RECORDS } from './mockData';
import * as mockDataModule from './mockData';

const MOCK_MODE = process.env.REACT_APP_MOCK_MODE === 'true';

let MOCK_REQUESTS = [
  { id: 'req-init-1', recordId: 'r1', doctorId: '2', patientId: '1', status: 'PENDING' }
];

let MOCK_SECURITY_ALERTS = [
  {
    id: 'sec-alert-1',
    patientId: '1',
    recordId: 'r1',
    recordTitle: 'Blood Test Results',
    doctorName: 'Dr. Emily Chen (Radiology)',
    ipAddress: '10.0.0.45',
    riskScore: 88,
    riskLevel: 'HIGH',
    status: 'LOCKDOWN',
    action: 'DENY_ACCESS',
    reason: 'Off-hours access attempt (3:15 AM) + Specialty Mismatch (Radiology -> Cardiology Policy)',
    timestamp: new Date(Date.now() - 3600000).toISOString(),
    txHash: '0xf3c4...d5e6',
    read: false,
  }
];

if (MOCK_MODE) {
  const savedRecords = localStorage.getItem('mock_records');
  if (savedRecords) {
    try {
      const parsed = JSON.parse(savedRecords);
      if (Array.isArray(parsed) && parsed.length > 0) {
        mockDataModule.MOCK_RECORDS.splice(0, mockDataModule.MOCK_RECORDS.length, ...parsed);
      }
    } catch(e) {
      localStorage.removeItem('mock_records');
    }
  }
  const savedRequests = localStorage.getItem('mock_requests');
  if (savedRequests) {
    try {
      const parsedReqs = JSON.parse(savedRequests);
      if (Array.isArray(parsedReqs)) MOCK_REQUESTS = parsedReqs;
    } catch(e) {}
  }
}

const saveFileContent = (recordId, content) => {
  try { sessionStorage.setItem(`file_${recordId}`, content); } catch(e) {}
};
const getFileContent = (recordId) => sessionStorage.getItem(`file_${recordId}`);

const saveRecords = () => {
  try {
    const stripped = mockDataModule.MOCK_RECORDS.map(({ content, ...r }) => r);
    localStorage.setItem('mock_records', JSON.stringify(stripped));
  } catch(e) {}
};
const saveRequests = () => localStorage.setItem('mock_requests', JSON.stringify(MOCK_REQUESTS));

const delay = (ms = 300) => new Promise(r => setTimeout(r, ms));

const mockTxHash = () => '0x' + Array.from({length: 64}, () => Math.floor(Math.random() * 16).toString(16)).join('');
const mockPID = () => '0x' + Array.from({length: 64}, () => Math.floor(Math.random() * 16).toString(16)).join('');

const api = axios.create({
  baseURL: process.env.REACT_APP_API_URL ? `${process.env.REACT_APP_API_URL}/api` : '/api',
});

api.interceptors.request.use(config => {
  const user = JSON.parse(localStorage.getItem('medchain_user') || '{}');
  if (user.token) config.headers.Authorization = `Bearer ${user.token}`;
  return config;
});

api.interceptors.response.use(
  res => res,
  err => {
    if (err.response?.status === 401) {
      localStorage.removeItem('medchain_user');
      window.location.href = '/login';
    }
    return Promise.reject(err);
  }
);

export const authService = {
  login: async (data) => {
    if (MOCK_MODE) {
      await delay();
      const user = MOCK_USERS.find(u => u.email === data.email && u.password === data.password);
      if (!user) throw new Error('Invalid credentials');
      const { password, ...safe } = user;
      return { data: { ...safe, token: 'mock-jwt-token' } };
    }
    return api.post('/auth/login', data);
  },
  register: async (data) => {
    if (MOCK_MODE) {
      await delay();
      const exists = MOCK_USERS.find(u => u.email === data.email);
      if (exists) throw { response: { data: { message: 'Email already registered' } } };
      const newUser = {
        id: String(MOCK_USERS.length + 1),
        ...data,
        role: data.role.toUpperCase(),
        active: true,
        blocked: false,
        pseudonymId: mockPID(),
        attributes: data.attributes || {},
      };
      mockDataModule.MOCK_USERS.push(newUser);
      return { data: { message: 'Registered successfully' } };
    }
    return api.post('/auth/register', data);
  },
};

export const recordService = {
  upload: async (data) => {
    if (MOCK_MODE) {
      await delay(600);
      const user = JSON.parse(localStorage.getItem('medchain_user') || '{}');
      const recordId = 'new-' + Date.now();
      const { content, ...rest } = data;
      const newRecord = {
        id: recordId,
        patientId: data.patientId || user.id || '1',
        patientPID: user.pseudonymId || mockPID(),
        title: data.title,
        description: data.description || '',
        recordType: data.recordType || 'LAB_REPORT',
        accessPolicy: data.accessPolicy || 'specialty:CARDIOLOGY',
        fileName: data.fileName || `${(data.title || 'document').toLowerCase().replace(/\s+/g, '_')}.pdf`,
        fileSize: data.fileSize || '150 KB',
        uploadedAt: new Date().toISOString(),
        authorizedDoctorIds: [],
        authorizedDoctorPIDs: [],
        policyHash: mockTxHash(),
        blockchainRecordId: mockTxHash(),
        blockchainTxHash: mockTxHash(),
        ipfsCid: 'Qm' + Math.random().toString(36).slice(2, 14) + '44b58',
        ...rest,
      };
      if (content) {
        saveFileContent(recordId, content);
        newRecord.content = content;
        newRecord.fileContent = content;
      }
      mockDataModule.MOCK_RECORDS.unshift(newRecord);
      saveRecords();
      return { data: newRecord };
    }
    return api.post('/records/upload', data);
  },

  getPatientRecords: async (id) => {
    if (MOCK_MODE) {
      await delay();
      const user = JSON.parse(localStorage.getItem('medchain_user') || '{}');
      const targetId = id || user.id || '1';
      return { data: mockDataModule.MOCK_RECORDS.filter(r => r.patientId === targetId || !r.patientId || r.patientId === '1') };
    }
    return api.get(`/records/patient/${id}`);
  },

  getDoctorRecords: async () => {
    if (MOCK_MODE) {
      await delay();
      const user = JSON.parse(localStorage.getItem('medchain_user') || '{}');
      return { data: mockDataModule.MOCK_RECORDS.filter(r => r.authorizedDoctorIds.includes(user.id)) };
    }
    return api.get('/records/doctor/authorized');
  },

  grantAccess: async (recordId, doctorId) => {
    if (MOCK_MODE) {
      await delay();
      const r = mockDataModule.MOCK_RECORDS.find(r => r.id === recordId);
      if (r && !r.authorizedDoctorIds.includes(doctorId)) {
        r.authorizedDoctorIds.push(doctorId);
        saveRecords();
      }
      const req = MOCK_REQUESTS.find(rq => rq.recordId === recordId && rq.doctorId === doctorId && rq.status === 'PENDING');
      if (req) {
        req.status = 'GRANTED';
        saveRequests();
      }
      return { data: {} };
    }
    return api.post(`/records/${recordId}/grant/${doctorId}`);
  },

  revokeAccess: async (recordId, doctorId) => {
    if (MOCK_MODE) {
      await delay();
      const r = mockDataModule.MOCK_RECORDS.find(r => r.id === recordId);
      if (r) {
        r.authorizedDoctorIds = r.authorizedDoctorIds.filter(id => id !== doctorId);
        saveRecords();
      }
      return { data: {} };
    }
    return api.post(`/records/${recordId}/revoke/${doctorId}`);
  },

  viewRecord: async (recordId) => {
    if (MOCK_MODE) {
      await delay(500);
      const r = mockDataModule.MOCK_RECORDS.find(r => r.id === recordId);
      if (!r) throw new Error('Record not found');
      const content = getFileContent(recordId) || r.content;
      const decryptedContent = content ||
        (r.fileContent ? `data:text/plain;base64,${btoa(unescape(encodeURIComponent(r.fileContent)))}` : '');
      return {
        data: {
          record: r,
          anomalyScore: 0.12,
          riskScore: 12,
          riskLevel: 'LOW',
          status: 'SAFE',
          decryptionStatus: 'AES_PRE_APPLIED',
          blockchainTxHash: mockTxHash(),
          decryptedContent,
          ipfsGatewayUrl: `http://localhost:8081/ipfs/${r.ipfsCid}`
        }
      };
    }
    return api.get(`/records/${recordId}/view`);
  },

  simulateAnomalyTest: async (recordId) => {
    if (MOCK_MODE) {
      await delay(500);
      const r = mockDataModule.MOCK_RECORDS.find(r => r.id === recordId) || mockDataModule.MOCK_RECORDS[0];
      const alertItem = {
        id: 'sec-alert-' + Date.now(),
        patientId: r.patientId || '1',
        recordId: r.id,
        recordTitle: r.title,
        doctorName: 'Dr. Emily Chen (Radiology)',
        ipAddress: '10.0.0.45',
        riskScore: 88,
        riskLevel: 'HIGH',
        status: 'LOCKDOWN',
        action: 'DENY_ACCESS',
        reason: 'Isolation Forest Anomaly (Off-hours access at 3:15 AM + Specialty Mismatch)',
        timestamp: new Date().toISOString(),
        txHash: mockTxHash(),
        read: false,
      };
      MOCK_SECURITY_ALERTS.unshift(alertItem);
      return {
        data: {
          record: r,
          anomalyScore: 0.88,
          riskScore: 88,
          riskLevel: 'HIGH',
          status: 'LOCKDOWN',
          action: 'DENY_ACCESS',
          isolationScore: 0.88,
          autoencoderScore: 0.85,
          decryptionStatus: 'BLOCKED_BY_ML',
          blockchainTxHash: alertItem.txHash,
          decryptedContent: null,
          explainableReport: {
            isolationForestAnalysis: "Tree decision score: 0.88 (Outlier probability)",
            autoencoderAnalysis: "Neural reconstruction error: 0.85 (Pattern deviation)",
            specialtyMatch: "Specialty Mismatch Flagged (Dermatologist -> Cardiology Record)",
            timeAnomaly: "Off-hours access detected (3:15 AM on Sunday)",
            frequencyCheck: "Elevated request rate (28 rec/hr)",
            decisionRationale: "Status [LOCKDOWN] assigned based on combined risk score of 88/100. Action: DENY_ACCESS."
          }
        }
      };
    }
    return api.get(`/records/${recordId || 'r1'}/view?simulateAnomaly=true`);
  },

  getPatientSecurityAlerts: async () => {
    if (MOCK_MODE) {
      await delay();
      const user = JSON.parse(localStorage.getItem('medchain_user') || '{}');
      const targetPatientId = user.id || '1';
      return { data: MOCK_SECURITY_ALERTS.filter(a => a.patientId === targetPatientId || !a.patientId || a.patientId === '1') };
    }
    return api.get('/records/alerts/patient');
  },

  dismissSecurityAlert: async (alertId) => {
    if (MOCK_MODE) {
      await delay();
      MOCK_SECURITY_ALERTS = MOCK_SECURITY_ALERTS.filter(a => a.id !== alertId);
      return { data: { success: true } };
    }
    return api.post(`/records/alerts/${alertId}/dismiss`);
  },

  getDoctors: async () => {
    if (MOCK_MODE) {
      await delay();
      return { data: mockDataModule.MOCK_USERS.filter(u => u.role === 'DOCTOR') };
    }
    return api.get('/records/doctors');
  },

  getPatients: async () => {
    if (MOCK_MODE) {
      await delay();
      return { data: mockDataModule.MOCK_USERS.filter(u => u.role === 'PATIENT') };
    }
    return api.get('/records/patients');
  },

  requestAccess: async (recordId) => {
    if (MOCK_MODE) {
      await delay();
      const user = JSON.parse(localStorage.getItem('medchain_user') || '{}');
      const r = mockDataModule.MOCK_RECORDS.find(r => r.id === recordId);
      const targetPatientId = r?.patientId || '1';
      const req = {
        id: 'req-' + Date.now(),
        recordId,
        doctorId: user.id || '2',
        patientId: targetPatientId,
        status: 'PENDING'
      };
      MOCK_REQUESTS.unshift(req);
      saveRequests();
      return { data: req };
    }
    return api.post(`/records/${recordId}/request-access`);
  },

  getPatientRequests: async () => {
    if (MOCK_MODE) {
      await delay();
      const user = JSON.parse(localStorage.getItem('medchain_user') || '{}');
      const currentPatientId = user.id || '1';
      return {
        data: MOCK_REQUESTS.filter(
          r => (r.patientId === currentPatientId || !r.patientId || r.patientId === '1') && r.status === 'PENDING'
        )
      };
    }
    return api.get('/records/requests/patient');
  },

  denyRequest: async (requestId) => {
    if (MOCK_MODE) {
      await delay();
      const req = MOCK_REQUESTS.find(r => r.id === requestId);
      if (req) {
        req.status = 'DENIED';
        saveRequests();
      }
      return { data: { success: true } };
    }
    return api.post(`/records/requests/${requestId}/deny`);
  },
};

export const adminService = {
  getDashboard: async () => {
    if (MOCK_MODE) {
      await delay();
      return {
        data: {
          ...MOCK_STATS,
          totalRecords: mockDataModule.MOCK_RECORDS.length,
          totalUsers: mockDataModule.MOCK_USERS.length,
          flaggedLogs: MOCK_LOGS.filter(l => l.anomalyScore > 0.6).length,
        }
      };
    }
    return api.get('/admin/dashboard');
  },

  getUsers: async () => {
    if (MOCK_MODE) { await delay(); return { data: mockDataModule.MOCK_USERS }; }
    return api.get('/admin/users');
  },

  getLogs: async () => {
    if (MOCK_MODE) { await delay(); return { data: MOCK_LOGS }; }
    return api.get('/admin/logs');
  },

  getFlaggedLogs: async () => {
    if (MOCK_MODE) { await delay(); return { data: MOCK_LOGS.filter(l => l.anomalyScore > 0.6) }; }
    return api.get('/admin/logs/flagged');
  },

  getDoctors: async () => {
    if (MOCK_MODE) { await delay(); return { data: mockDataModule.MOCK_USERS.filter(u => u.role === 'DOCTOR') }; }
    return api.get('/admin/doctors');
  },

  getPatients: async () => {
    if (MOCK_MODE) { await delay(); return { data: mockDataModule.MOCK_USERS.filter(u => u.role === 'PATIENT') }; }
    return api.get('/admin/patients');
  },

  blockUser: async (userId) => {
    if (MOCK_MODE) {
      await delay();
      const u = mockDataModule.MOCK_USERS.find(u => u.id === userId);
      if (u) u.blocked = true;
      return { data: { message: 'User blocked' } };
    }
    return api.post(`/admin/users/${userId}/block`);
  },

  unblockUser: async (userId) => {
    if (MOCK_MODE) {
      await delay();
      const u = mockDataModule.MOCK_USERS.find(u => u.id === userId);
      if (u) u.blocked = false;
      return { data: { message: 'User unblocked' } };
    }
    return api.post(`/admin/users/${userId}/unblock`);
  },
};

export const metricsService = {
  getMetrics: async () => {
    if (MOCK_MODE) {
      await delay();
      return {
        data: {
          encryptionTimeMs: 12,
          blockchainLatencyMs: 245,
          apiResponseMs: 35,
          mlResponseMs: 62,
          cpuUsage: 32.4,
          memoryUsage: 58.6,
          heapUsedMB: 240,
          heapMaxMB: 512,
          services: {
            backend: true,
            mongodb: true,
            ml: true,
            blockchain: true,
            ipfs: true
          },
          totalUsers: mockDataModule.MOCK_USERS.length,
          totalRecords: mockDataModule.MOCK_RECORDS.length,
          totalLogs: MOCK_LOGS.length,
          timestamp: Date.now()
        }
      };
    }
    return api.get('/metrics');
  }
};

export const blockchainService = {
  getTransactions: async (limit = 50) => {
    if (MOCK_MODE) {
      await delay();
      return {
        data: MOCK_LOGS.map(log => ({
          txHash: log.blockchainTxHash || mockTxHash(),
          event: log.action === 'UPLOAD' ? 'RecordUploaded' : log.action === 'GRANT' ? 'AccessGranted' : log.action === 'REVOKE' ? 'AccessRevoked' : 'RecordViewed',
          ipfsCid: 'Qm' + Math.random().toString(36).slice(2, 14) + '44b58',
          recordId: log.recordId,
          requesterPID: log.requesterPID,
          patientPID: log.patientPID,
          granted: log.granted,
          riskLevel: log.riskLevel || 'LOW',
          anomalyScore: log.anomalyScore,
          timestamp: log.timestamp,
          flagged: log.flagged
        }))
      };
    }
    return api.get(`/blockchain/transactions?limit=${limit}`);
  },

  getStats: async () => {
    if (MOCK_MODE) {
      await delay();
      return {
        data: {
          totalTransactions: MOCK_LOGS.length + 15,
          recordUploads: MOCK_LOGS.filter(l => l.action === 'UPLOAD').length + 5,
          accessGrants: MOCK_LOGS.filter(l => l.action === 'GRANT').length + 4,
          accessRevocations: MOCK_LOGS.filter(l => l.action === 'REVOKE').length + 2,
          recordViews: MOCK_LOGS.filter(l => l.action === 'VIEW').length + 4,
          flaggedEvents: MOCK_LOGS.filter(l => l.anomalyScore > 0.6).length
        }
      };
    }
    return api.get('/blockchain/stats');
  }
};

export default api;
