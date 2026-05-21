import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate, Outlet, Link } from 'react-router-dom';
import './App.css';
import SsoCallback from './components/SsoCallback';
import UserManagement from './components/UserManagement';

const SSO_WEB_URL = '/';

const ProtectedRoute = () => {
  const isAuthenticated = !!localStorage.getItem('sso_token');
  
  if (!isAuthenticated) {
    const redirectUrl = encodeURIComponent(window.location.origin + window.location.pathname);
    window.location.href = `${SSO_WEB_URL}?redirect=${redirectUrl}`;
    return null;
  }
  
  return <Outlet />;
};

const Navbar = () => {
  const username = localStorage.getItem('username');
  
  const handleLogout = () => {
    localStorage.removeItem('sso_token');
    localStorage.removeItem('username');
    window.location.href = SSO_WEB_URL;
  };

  return (
    <nav className="navbar">
      <div className="nav-brand">用户中心管理系统</div>
      <div className="nav-links">
        <span className="nav-username">欢迎，{username}</span>
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
    <Router basename="/user">
      <Routes>
        <Route path="/sso/callback" element={<SsoCallback />} />
        
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
