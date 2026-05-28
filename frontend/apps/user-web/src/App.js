import React, { useEffect } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate, Outlet, Link } from 'react-router-dom';
import './App.css';
import SsoCallback from './components/SsoCallback';
import UserManagement from './components/UserManagement';
import axios from 'axios';

const getEnv = (key) => {
  return (window.__ENV__ && window.__ENV__[key]) || process.env[key];
};

axios.interceptors.request.use(
  (config) => {
    const accessToken = localStorage.getItem('access_token');
    if (accessToken) {
      config.headers.Authorization = `Bearer ${accessToken}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

axios.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      localStorage.clear();
      redirectToSSO();
    }
    return Promise.reject(error);
  }
);

const OAUTH2_CONFIG = {
  authorizationUri: getEnv('REACT_APP_OAUTH2_AUTH_URI'),
  clientId: getEnv('REACT_APP_OAUTH2_CLIENT_ID'),
  redirectUri: getEnv('REACT_APP_OAUTH2_REDIRECT_URI'),
  scope: getEnv('REACT_APP_OAUTH2_SCOPE'),
  responseType: 'code'
};

const redirectToSSO = () => {
  const originalPath = window.location.pathname;
  localStorage.setItem('originalPath', originalPath);

  const state = Math.random().toString(36).substring(2, 15);
  localStorage.setItem('oauth_state', state);

  const params = new URLSearchParams({
    response_type: OAUTH2_CONFIG.responseType,
    client_id: OAUTH2_CONFIG.clientId,
    redirect_uri: OAUTH2_CONFIG.redirectUri,
    scope: OAUTH2_CONFIG.scope,
    state: state
  });

  window.location.href = `${OAUTH2_CONFIG.authorizationUri}?${params.toString()}`;
};

const isTokenValid = () => {
  const accessToken = localStorage.getItem('access_token');
  const loginTime = localStorage.getItem('login_time');
  const expiresIn = localStorage.getItem('expires_in');

  if (!accessToken || !loginTime || !expiresIn) {
    return false;
  }

  const elapsed = (Date.now() - parseInt(loginTime)) / 1000;
  return elapsed < parseInt(expiresIn) * 0.9;
};

const ProtectedRoute = () => {
  useEffect(() => {
    if (!isTokenValid()) {
      redirectToSSO();
    }
  }, []);

  if (!isTokenValid()) {
    return null;
  }
  
  return <Outlet />;
};

const Navbar = () => {
  const handleLogout = () => {
    localStorage.clear();
    const logoutUri = (getEnv('REACT_APP_OAUTH2_AUTH_URI') || '').replace('/oauth2/authorize', '');
    window.location.href = `${logoutUri}/logout`;
  };

  return (
    <nav className="navbar">
      <div className="nav-brand">用户中心管理系统</div>
      <div className="nav-links">
        <Link to="/users" className="nav-link">用户管理</Link>
        <button className="logout-button-nav" onClick={handleLogout}>退出登录</button>
      </div>
    </nav>
  );
};

const Layout = () => {
  return (
    <div className="App">
      <Navbar />
      <div className="content">
        <Outlet />
      </div>
    </div>
  );
};

function App() {
  return (
    <Router>
      <Routes>
        <Route path="/oauth2/callback" element={<SsoCallback />} />
        
        <Route element={<ProtectedRoute />}>
          <Route element={<Layout />}>
            <Route path="/users" element={<UserManagement />} />
            <Route path="/" element={<Navigate to="/users" />} />
            <Route path="*" element={<Navigate to="/users" />} />
          </Route>
        </Route>
      </Routes>
    </Router>
  );
}

export default App;
