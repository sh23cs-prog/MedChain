import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { ThemeProvider } from './context/ThemeContext';
import Landing from './pages/Landing';
import Login from './pages/Login';
import Register from './pages/Register';
import PatientDashboard from './pages/PatientDashboard';
import DoctorDashboard from './pages/DoctorDashboard';
import AdminDashboard from './pages/AdminDashboard';
import UserManagement from './pages/UserManagement';
import Monitoring from './pages/Monitoring';
import BlockchainExplorer from './pages/BlockchainExplorer';
import Architecture from './pages/Architecture';

import OtpVerification from './pages/OtpVerification';

const PrivateRoute = ({ children, role }) => {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  if (role && user.role !== role) {
    if (user.role === 'PATIENT') return <Navigate to="/patient" replace />;
    if (user.role === 'DOCTOR')  return <Navigate to="/doctor"  replace />;
    if (user.role === 'ADMIN')   return <Navigate to="/admin"   replace />;
    return <Navigate to="/login" replace />;
  }
  return children;
};

export default function App() {
  return (
    <ThemeProvider>
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/" element={<Landing />} />
            <Route path="/login" element={<Login />} />
            <Route path="/register" element={<Register />} />
            <Route path="/verify-otp" element={<OtpVerification />} />
            <Route
              path="/patient"
              element={
                <PrivateRoute role="PATIENT">
                  <PatientDashboard />
                </PrivateRoute>
              }
            />
            <Route
              path="/doctor"
              element={
                <PrivateRoute role="DOCTOR">
                  <DoctorDashboard />
                </PrivateRoute>
              }
            />
            <Route
              path="/admin"
              element={
                <PrivateRoute role="ADMIN">
                  <AdminDashboard />
                </PrivateRoute>
              }
            />
            <Route
              path="/admin/users"
              element={
                <PrivateRoute role="ADMIN">
                  <UserManagement />
                </PrivateRoute>
              }
            />
            <Route
              path="/monitoring"
              element={
                <PrivateRoute>
                  <Monitoring />
                </PrivateRoute>
              }
            />
            <Route
              path="/blockchain-explorer"
              element={
                <PrivateRoute>
                  <BlockchainExplorer />
                </PrivateRoute>
              }
            />
            <Route
              path="/architecture"
              element={<Architecture />}
            />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </ThemeProvider>
  );
}
