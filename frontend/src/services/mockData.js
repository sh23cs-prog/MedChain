export let MOCK_USERS = [
  {
    id: '1', name: 'Alice Johnson', email: 'patient@medchain.com', password: 'patient123',
    role: 'PATIENT', phone: '+1-555-0101', active: true, blocked: false,
    pseudonymId: '0xa3f2...b91c', attributes: {}
  },
  {
    id: '2', name: 'Dr. Robert Smith', email: 'doctor@medchain.com', password: 'doctor123',
    role: 'DOCTOR', phone: '+1-555-0102', active: true, blocked: false,
    pseudonymId: '0xb4e3...c02d',
    attributes: { specialty: 'Cardiology', hospital: 'CityHospital', license: 'Senior', department: 'Cardiology' }
  },
  {
    id: '3', name: 'Admin User', email: 'admin@medchain.com', password: 'admin123',
    role: 'ADMIN', phone: '+1-555-0103', active: true, blocked: false,
    pseudonymId: '0xc5f4...d13e', attributes: {}
  },
  {
    id: '4', name: 'Dr. Emily Chen', email: 'emily@medchain.com', password: 'doctor123',
    role: 'DOCTOR', phone: '+1-555-0104', active: true, blocked: false,
    pseudonymId: '0xd6a5...e24f',
    attributes: { specialty: 'Radiology', hospital: 'CityHospital', license: 'Senior', department: 'Radiology' }
  },
  {
    id: '5', name: 'Bob Williams', email: 'bob@medchain.com', password: 'patient123',
    role: 'PATIENT', phone: '+1-555-0105', active: true, blocked: false,
    pseudonymId: '0xe7b6...f35a', attributes: {}
  },
  {
    id: '6', name: 'Dr. James Wilson', email: 'james@medchain.com', password: 'doctor123',
    role: 'DOCTOR', phone: '+1-555-0106', active: true, blocked: false,
    pseudonymId: '0xf8c7...a46b',
    attributes: { specialty: 'Neurology', hospital: 'CityHospital', license: 'Senior', department: 'Neurology' }
  },
  {
    id: '7', name: 'Dr. Sarah Patel', email: 'sarah@medchain.com', password: 'doctor123',
    role: 'DOCTOR', phone: '+1-555-0107', active: true, blocked: false,
    pseudonymId: '0xa9d8...b57c',
    attributes: { specialty: 'Orthopedics', hospital: 'MedicalCenter', license: 'Junior', department: 'Orthopedics' }
  },
  {
    id: '8', name: 'Dr. Michael Torres', email: 'michael@medchain.com', password: 'doctor123',
    role: 'DOCTOR', phone: '+1-555-0108', active: true, blocked: false,
    pseudonymId: '0xb0e9...c68d',
    attributes: { specialty: 'Oncology', hospital: 'CancerInstitute', license: 'Senior', department: 'Oncology' }
  },
  {
    id: '9', name: 'Dr. Priya Sharma', email: 'priya@medchain.com', password: 'doctor123',
    role: 'DOCTOR', phone: '+1-555-0109', active: true, blocked: false,
    pseudonymId: '0xc1f0...d79e',
    attributes: { specialty: 'Pediatrics', hospital: 'CityHospital', license: 'Junior', department: 'Pediatrics' }
  },
  {
    id: '10', name: 'Dr. David Kim', email: 'david@medchain.com', password: 'doctor123',
    role: 'DOCTOR', phone: '+1-555-0110', active: true, blocked: false,
    pseudonymId: '0xd2a1...e80f',
    attributes: { specialty: 'Dermatology', hospital: 'SkinClinic', license: 'Junior', department: 'Dermatology' }
  },
  {
    id: '11', name: 'Dr. Lisa Nguyen', email: 'lisa@medchain.com', password: 'doctor123',
    role: 'DOCTOR', phone: '+1-555-0111', active: true, blocked: false,
    pseudonymId: '0xe3b2...f91a',
    attributes: { specialty: 'Psychiatry', hospital: 'MindWellness', license: 'Senior', department: 'Psychiatry' }
  },
];

export let MOCK_RECORDS = [
  {
    id: 'r1', title: 'Blood Test Results', description: 'Complete blood count and metabolic panel',
    recordType: 'LAB_REPORT', fileName: 'blood_test_results.pdf', fileSize: '124 KB',
    fileContent: 'PATIENT: Alice Johnson | DATE: 2025-01-10\nWBC: 6.8 | RBC: 4.7 | Hemoglobin: 13.8 g/dL\nAll values within normal range.',
    ipfsCid: 'QmX7b3Na9f2...', policyHash: '0x4a3f...b12c',
    accessPolicy: '(specialty=Cardiology AND hospital=CityHospital) OR (license=Senior)',
    blockchainRecordId: '0x1a2b...3c4d', blockchainTxHash: '0xabc1...def2',
    patientId: '1', patientPID: '0xa3f2...b91c',
    authorizedDoctorIds: ['2'], authorizedDoctorPIDs: ['0xb4e3...c02d'],
    uploadedAt: '2025-01-10T09:00:00Z'
  },
  {
    id: 'r2', title: 'Chest X-Ray', description: 'Annual chest radiograph - no abnormalities',
    recordType: 'SCAN', fileName: 'chest_xray_report.pdf', fileSize: '2.4 MB',
    fileContent: 'PATIENT: Alice Johnson | DATE: 2025-01-15\nFindings: Clear lungs, normal heart size.\nImpression: Normal chest radiograph.',
    ipfsCid: 'QmY8c4Nb0e3...', policyHash: '0x5b4g...c23d',
    accessPolicy: '(specialty=Radiology AND hospital=CityHospital)',
    blockchainRecordId: '0x2b3c...4d5e', blockchainTxHash: '0xbcd2...ef03',
    patientId: '1', patientPID: '0xa3f2...b91c',
    authorizedDoctorIds: ['4'], authorizedDoctorPIDs: ['0xd6a5...e24f'],
    uploadedAt: '2025-01-15T14:30:00Z'
  },
  {
    id: 'r3', title: 'Cardiology Prescription', description: 'Atorvastatin 20mg daily for 3 months',
    recordType: 'PRESCRIPTION', fileName: 'prescription_atorvastatin.pdf', fileSize: '48 KB',
    fileContent: 'Rx: Atorvastatin 20mg | Sig: Once daily at bedtime | Qty: 90 | Refills: 2',
    ipfsCid: 'QmZ9d5Nc1f4...', policyHash: '0x6c5h...d34e',
    accessPolicy: '(specialty=Cardiology) OR (license=Senior AND hospital=CityHospital)',
    blockchainRecordId: '0x3c4d...5e6f', blockchainTxHash: '0xcde3...f014',
    patientId: '1', patientPID: '0xa3f2...b91c',
    authorizedDoctorIds: ['2', '4'], authorizedDoctorPIDs: ['0xb4e3...c02d', '0xd6a5...e24f'],
    uploadedAt: '2025-01-20T11:00:00Z'
  },
  {
    id: 'r4', title: 'Hospital Discharge Summary', description: 'Post-operative care instructions',
    recordType: 'DISCHARGE', fileName: 'discharge_summary_jan25.pdf', fileSize: '312 KB',
    fileContent: 'Diagnosis: Stable angina | Discharge meds: Atorvastatin, Aspirin, Metoprolol | Follow-up: 4 weeks',
    ipfsCid: 'QmA0e6Nd2g5...', policyHash: '0x7d6i...e45f',
    accessPolicy: '(license=Senior AND hospital=CityHospital)',
    blockchainRecordId: '0x4d5e...6f7a', blockchainTxHash: '0xdef4...0125',
    patientId: '1', patientPID: '0xa3f2...b91c',
    authorizedDoctorIds: ['2'], authorizedDoctorPIDs: ['0xb4e3...c02d'],
    uploadedAt: '2025-01-25T16:00:00Z'
  },
  {
    id: 'r8', title: 'Neurology Consultation', description: 'Migraine evaluation and treatment plan',
    recordType: 'LAB_REPORT', fileName: 'neurology_consult.pdf', fileSize: '156 KB',
    fileContent: 'Diagnosis: Chronic migraine | Treatment: Sumatriptan 50mg PRN + Topiramate 25mg daily',
    ipfsCid: 'QmE4i0Rf6k9...', policyHash: '0x8e7j...f56g',
    accessPolicy: '(specialty=Neurology AND hospital=CityHospital)',
    blockchainRecordId: '0x5e6f...7a8b', blockchainTxHash: '0xef05...1236',
    patientId: '1', patientPID: '0xa3f2...b91c',
    authorizedDoctorIds: ['6'], authorizedDoctorPIDs: ['0xf8c7...a46b'],
    uploadedAt: '2025-03-05T10:00:00Z'
  },
  {
    id: 'r10', title: 'MRI Brain Scan', description: 'MRI without contrast - no acute intracranial abnormality',
    recordType: 'SCAN', fileName: 'mri_brain_mar10.pdf', fileSize: '8.2 MB',
    fileContent: 'Findings: No acute infarct, hemorrhage, or mass. Mild white matter changes consistent with migraines.',
    ipfsCid: 'QmG6k2Th8m1...', policyHash: '0x9f8k...g67h',
    accessPolicy: '(specialty=Neurology OR specialty=Radiology) AND hospital=CityHospital',
    blockchainRecordId: '0x6f7a...8b9c', blockchainTxHash: '0xf016...2347',
    patientId: '1', patientPID: '0xa3f2...b91c',
    authorizedDoctorIds: ['6', '4'], authorizedDoctorPIDs: ['0xf8c7...a46b', '0xd6a5...e24f'],
    uploadedAt: '2025-03-10T14:00:00Z'
  },
  {
    id: 'r11', title: 'Thyroid Function Test', description: 'TSH, T3, T4 levels - subclinical hypothyroidism',
    recordType: 'LAB_REPORT', fileName: 'thyroid_function_apr01.pdf', fileSize: '92 KB',
    fileContent: 'TSH: 6.2 mIU/L (High) | Free T4: 0.9 ng/dL (Normal) | Impression: Subclinical hypothyroidism.',
    ipfsCid: 'QmH7l3Ui9n2...', policyHash: '0xa09l...h78i',
    accessPolicy: '(specialty=Cardiology OR license=Senior)',
    blockchainRecordId: '0x7a8b...9c0d', blockchainTxHash: '0x0127...3458',
    patientId: '1', patientPID: '0xa3f2...b91c',
    authorizedDoctorIds: ['2'], authorizedDoctorPIDs: ['0xb4e3...c02d'],
    uploadedAt: '2025-04-01T10:00:00Z'
  },
  {
    id: 'r12', title: 'Dermatology Report', description: 'Eczema assessment - mild atopic dermatitis',
    recordType: 'DISCHARGE', fileName: 'dermatology_report_apr15.pdf', fileSize: '78 KB',
    fileContent: 'Diagnosis: Mild atopic dermatitis | Treatment: Hydrocortisone 1% cream BID x 2 weeks',
    ipfsCid: 'QmI8m4Vj0o3...', policyHash: '0xb10m...i89j',
    accessPolicy: '(specialty=Dermatology)',
    blockchainRecordId: '0x8b9c...0d1e', blockchainTxHash: '0x1238...4569',
    patientId: '1', patientPID: '0xa3f2...b91c',
    authorizedDoctorIds: ['10'], authorizedDoctorPIDs: ['0xd2a1...e80f'],
    uploadedAt: '2025-04-15T11:00:00Z'
  },
  {
    id: 'r13', title: 'Psychiatry Evaluation', description: 'Anxiety disorder assessment - GAD-7 score 12',
    recordType: 'LAB_REPORT', fileName: 'psychiatry_eval_may02.pdf', fileSize: '64 KB',
    fileContent: 'GAD-7 Score: 12 (Moderate anxiety) | Plan: CBT referral + Sertraline 50mg daily',
    ipfsCid: 'QmJ9n5Wk1p4...', policyHash: '0xc21n...j90k',
    accessPolicy: '(specialty=Psychiatry AND license=Senior)',
    blockchainRecordId: '0x9c0d...1e2f', blockchainTxHash: '0x2349...567a',
    patientId: '1', patientPID: '0xa3f2...b91c',
    authorizedDoctorIds: ['11'], authorizedDoctorPIDs: ['0xe3b2...f91a'],
    uploadedAt: '2025-05-02T10:00:00Z'
  },
  {
    id: 'r5', title: 'Lipid Panel Results', description: 'Cholesterol and triglyceride levels - borderline high LDL',
    recordType: 'LAB_REPORT', fileName: 'lipid_panel_feb03.pdf', fileSize: '89 KB',
    fileContent: 'Total Cholesterol: 218 mg/dL | LDL: 142 mg/dL (Borderline High) | HDL: 48 mg/dL',
    ipfsCid: 'QmB1f7Oe3h6...', policyHash: '0x8e7j...f56g',
    accessPolicy: '(specialty=Cardiology AND hospital=CityHospital)',
    blockchainRecordId: '0x5e6f...7a8b', blockchainTxHash: '0xef05...1236',
    patientId: '5', patientPID: '0xe7b6...f35a',
    authorizedDoctorIds: ['2'], authorizedDoctorPIDs: ['0xb4e3...c02d'],
    uploadedAt: '2025-02-03T08:00:00Z'
  },
  {
    id: 'r6', title: 'Echocardiogram Report', description: 'Mild mitral valve regurgitation detected',
    recordType: 'SCAN', fileName: 'echo_report_feb10.pdf', fileSize: '1.8 MB',
    fileContent: 'EF: 62% (Normal) | Mitral Valve: Mild regurgitation | Impression: Normal LV function, mild MR.',
    ipfsCid: 'QmC2g8Pf4i7...', policyHash: '0x9f8k...g67h',
    accessPolicy: '(specialty=Cardiology OR specialty=Radiology) AND hospital=CityHospital',
    blockchainRecordId: '0x6f7a...8b9c', blockchainTxHash: '0xf016...2347',
    patientId: '5', patientPID: '0xe7b6...f35a',
    authorizedDoctorIds: ['2', '4'], authorizedDoctorPIDs: ['0xb4e3...c02d', '0xd6a5...e24f'],
    uploadedAt: '2025-02-10T11:30:00Z'
  },
  {
    id: 'r7', title: 'Beta Blocker Prescription', description: 'Metoprolol 50mg twice daily - cardiac management',
    recordType: 'PRESCRIPTION', fileName: 'prescription_metoprolol.pdf', fileSize: '52 KB',
    fileContent: 'Rx: Metoprolol Succinate 50mg | Sig: Twice daily with food | Qty: 60 | Refills: 3',
    ipfsCid: 'QmD3h9Qg5j8...', policyHash: '0xa09l...h78i',
    accessPolicy: '(specialty=Cardiology AND license=Senior)',
    blockchainRecordId: '0x7a8b...9c0d', blockchainTxHash: '0x0127...3458',
    patientId: '5', patientPID: '0xe7b6...f35a',
    authorizedDoctorIds: [], authorizedDoctorPIDs: [],
    uploadedAt: '2025-02-15T14:00:00Z'
  },
  {
    id: 'r9', title: 'Orthopedic X-Ray', description: 'Right knee - mild osteoarthritis grade II',
    recordType: 'SCAN', fileName: 'knee_xray_mar12.pdf', fileSize: '3.1 MB',
    fileContent: 'Right Knee AP/Lateral: Mild joint space narrowing medial compartment. Grade II OA.',
    ipfsCid: 'QmF5j1Sg7l0...', policyHash: '0xb10m...i89j',
    accessPolicy: '(specialty=Orthopedics OR specialty=Radiology)',
    blockchainRecordId: '0x8b9c...0d1e', blockchainTxHash: '0x1238...4569',
    patientId: '5', patientPID: '0xe7b6...f35a',
    authorizedDoctorIds: ['7', '4'], authorizedDoctorPIDs: ['0xa9d8...b57c', '0xd6a5...e24f'],
    uploadedAt: '2025-03-12T09:00:00Z'
  },
  {
    id: 'r14', title: 'Oncology Screening', description: 'PSA test and colonoscopy - no malignancy detected',
    recordType: 'LAB_REPORT', fileName: 'oncology_screening_mar20.pdf', fileSize: '210 KB',
    fileContent: 'PSA: 1.8 ng/mL (Normal) | Colonoscopy: 2 small hyperplastic polyps removed, benign',
    ipfsCid: 'QmK0o6Xl2q5...', policyHash: '0xc21n...j90k',
    accessPolicy: '(specialty=Oncology AND license=Senior)',
    blockchainRecordId: '0x9c0d...1e2f', blockchainTxHash: '0x2349...567a',
    patientId: '5', patientPID: '0xe7b6...f35a',
    authorizedDoctorIds: ['8'], authorizedDoctorPIDs: ['0xb0e9...c68d'],
    uploadedAt: '2025-03-20T09:00:00Z'
  },
  {
    id: 'r15', title: 'Physiotherapy Discharge', description: 'Post-knee surgery rehab - full ROM achieved',
    recordType: 'DISCHARGE', fileName: 'physio_discharge_apr10.pdf', fileSize: '145 KB',
    fileContent: 'Post-op knee rehab complete. Full ROM achieved. Strength 5/5. Cleared for normal activity.',
    ipfsCid: 'QmL1p7Ym3r6...', policyHash: '0xd32o...k01l',
    accessPolicy: '(specialty=Orthopedics OR license=Senior)',
    blockchainRecordId: '0x0d1e...2f3a', blockchainTxHash: '0x345a...678b',
    patientId: '5', patientPID: '0xe7b6...f35a',
    authorizedDoctorIds: ['7'], authorizedDoctorPIDs: ['0xa9d8...b57c'],
    uploadedAt: '2025-04-10T10:00:00Z'
  },
  {
    id: 'r16', title: 'Pediatric Vaccination Record', description: 'Annual flu vaccine + Hepatitis B booster',
    recordType: 'PRESCRIPTION', fileName: 'vaccination_record_apr22.pdf', fileSize: '38 KB',
    fileContent: 'Flu vaccine (Quadrivalent) administered IM | Hepatitis B booster (3rd dose) | No adverse reactions.',
    ipfsCid: 'QmM2q8Zn4s7...', policyHash: '0xe43p...l12m',
    accessPolicy: '(specialty=Pediatrics OR specialty=Cardiology)',
    blockchainRecordId: '0x1e2f...3a4b', blockchainTxHash: '0x456b...789c',
    patientId: '5', patientPID: '0xe7b6...f35a',
    authorizedDoctorIds: ['9', '2'], authorizedDoctorPIDs: ['0xc1f0...d79e', '0xb4e3...c02d'],
    uploadedAt: '2025-04-22T09:00:00Z'
  },
  {
    id: 'r17', title: 'Psychiatric Follow-up', description: 'Depression screening - PHQ-9 score 8, mild depression',
    recordType: 'LAB_REPORT', fileName: 'psych_followup_may10.pdf', fileSize: '71 KB',
    fileContent: 'PHQ-9: 8 (Mild depression) | GAD-7: 5 (Mild anxiety) | Plan: Continue Sertraline, CBT ongoing.',
    ipfsCid: 'QmN3r9Ao5t8...', policyHash: '0xf54q...m23n',
    accessPolicy: '(specialty=Psychiatry AND license=Senior)',
    blockchainRecordId: '0x2f3a...4b5c', blockchainTxHash: '0x567c...890d',
    patientId: '5', patientPID: '0xe7b6...f35a',
    authorizedDoctorIds: ['11'], authorizedDoctorPIDs: ['0xe3b2...f91a'],
    uploadedAt: '2025-05-10T10:00:00Z'
  },
];

export const MOCK_LOGS = [
  {
    id: 'l1', userId: 'Dr. Robert Smith', requesterPID: '0xb4e3...c02d', patientPID: '0xa3f2...b91c',
    action: 'VIEW', recordId: 'r1', ipAddress: '192.168.1.10',
    anomalyScore: 0.12, isolationScore: 0.10, autoencoderScore: 0.14,
    riskLevel: 'LOW', granted: true, denyReason: null,
    blockchainTxHash: '0xf1a2...b3c4', timestamp: '2025-01-26T08:30:00Z'
  },
  {
    id: 'l2', userId: 'Alice Johnson', requesterPID: '0xa3f2...b91c', patientPID: '0xa3f2...b91c',
    action: 'UPLOAD', recordId: 'r2', ipAddress: '192.168.1.5',
    anomalyScore: 0.05, isolationScore: 0.04, autoencoderScore: 0.06,
    riskLevel: 'LOW', granted: true, denyReason: null,
    blockchainTxHash: '0xf2b3...c4d5', timestamp: '2025-01-26T09:15:00Z'
  },
  {
    id: 'l3', userId: 'Dr. Emily Chen', requesterPID: '0xd6a5...e24f', patientPID: '0xa3f2...b91c',
    action: 'VIEW', recordId: 'r3', ipAddress: '10.0.0.45',
    anomalyScore: 0.78, isolationScore: 0.82, autoencoderScore: 0.74,
    riskLevel: 'HIGH', granted: false, denyReason: 'ANOMALY_DETECTED',
    blockchainTxHash: '0xf3c4...d5e6', timestamp: '2025-01-26T02:45:00Z'
  },
  {
    id: 'l4', userId: 'Dr. Robert Smith', requesterPID: '0xb4e3...c02d', patientPID: '0xa3f2...b91c',
    action: 'VIEW', recordId: 'r4', ipAddress: '192.168.1.10',
    anomalyScore: 0.21, isolationScore: 0.18, autoencoderScore: 0.24,
    riskLevel: 'LOW', granted: true, denyReason: null,
    blockchainTxHash: '0xf4d5...e6f7', timestamp: '2025-01-26T10:00:00Z'
  },
  {
    id: 'l5', userId: 'Unknown', requesterPID: '0x????...????', patientPID: '0xa3f2...b91c',
    action: 'VIEW', recordId: 'r1', ipAddress: '45.33.32.156',
    anomalyScore: 0.95, isolationScore: 0.97, autoencoderScore: 0.93,
    riskLevel: 'CRITICAL', granted: false, denyReason: 'ANOMALY_DETECTED',
    blockchainTxHash: '0xf5e6...f7a8', timestamp: '2025-01-26T03:12:00Z'
  },
  {
    id: 'l6', userId: 'Alice Johnson', requesterPID: '0xa3f2...b91c', patientPID: '0xa3f2...b91c',
    action: 'GRANT', recordId: 'r3', ipAddress: '192.168.1.5',
    anomalyScore: 0.08, isolationScore: 0.07, autoencoderScore: 0.09,
    riskLevel: 'LOW', granted: true, denyReason: null,
    blockchainTxHash: '0xf6f7...a8b9', timestamp: '2025-01-26T11:30:00Z'
  },
  {
    id: 'l7', userId: 'Dr. Robert Smith', requesterPID: '0xb4e3...c02d', patientPID: '0xe7b6...f35a',
    action: 'VIEW', recordId: 'r5', ipAddress: '192.168.1.10',
    anomalyScore: 0.15, isolationScore: 0.13, autoencoderScore: 0.17,
    riskLevel: 'LOW', granted: true, denyReason: null,
    blockchainTxHash: '0xf7a8...b9c0', timestamp: '2025-02-04T09:00:00Z'
  },
  {
    id: 'l8', userId: 'Dr. Emily Chen', requesterPID: '0xd6a5...e24f', patientPID: '0xe7b6...f35a',
    action: 'VIEW', recordId: 'r6', ipAddress: '192.168.1.22',
    anomalyScore: 0.11, isolationScore: 0.09, autoencoderScore: 0.13,
    riskLevel: 'LOW', granted: true, denyReason: null,
    blockchainTxHash: '0xf8b9...c0d1', timestamp: '2025-02-11T10:15:00Z'
  },
  {
    id: 'l9', userId: 'Unknown', requesterPID: '0x????...????', patientPID: '0xe7b6...f35a',
    action: 'VIEW', recordId: 'r7', ipAddress: '185.220.101.45',
    anomalyScore: 0.91, isolationScore: 0.94, autoencoderScore: 0.88,
    riskLevel: 'CRITICAL', granted: false, denyReason: 'ANOMALY_DETECTED',
    blockchainTxHash: '0xf9c0...d1e2', timestamp: '2025-02-15T01:33:00Z'
  },
];

export const MOCK_STATS = {
  totalUsers: 11,
  totalRecords: 17,
  totalLogs: 9,
  flaggedLogs: 3,
};
