import React, { useState, useEffect } from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import './App.css';
import Login from './components/Login';
import Register from './components/Register';
import ForgotPassword from './components/ForgotPassword';

function Dashboard({ user, onLogout }) {
  return (
    <div className="dashboard-container">
      <h1 className="dashboard-title">SSO 登录成功</h1>
      <p className="dashboard-welcome">
        你好，<strong>{user.username}</strong>！
      </p>
      <p style={{ color: '#666', marginBottom: '30px' }}>
        票据：{localStorage.getItem('token')}
      </p>
      <button className="logout-button" onClick={onLogout}>
        退出登录
      </button>
    </div>
  );
}

function App() {
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const token = localStorage.getItem('token');
    const savedUser = localStorage.getItem('user');
    if (token && savedUser) {
      setIsLoggedIn(true);
      setUser(JSON.parse(savedUser));
    }
    setLoading(false);
  }, []);

  const handleLoginSuccess = (userData) => {
    setIsLoggedIn(true);
    setUser(userData);
    localStorage.setItem('user', JSON.stringify(userData));
  };

  const handleLogout = () => {
    setIsLoggedIn(false);
    setUser(null);
    localStorage.removeItem('token');
    localStorage.removeItem('user');
  };

  if (loading) {
    return <div style={{ color: 'white', fontSize: '18px', textAlign: 'center', padding: '50px' }}>加载中...</div>;
  }

  return (
    <div className="App">
      <BrowserRouter basename="/">
        <Routes>
          <Route path="/register" element={<Register />} />
          <Route path="/forgot-password" element={<ForgotPassword />} />
          <Route path="/" element={
            isLoggedIn ? (
              <Dashboard user={user} onLogout={handleLogout} />
            ) : (
              <Login onLoginSuccess={handleLoginSuccess} />
            )
          } />
          <Route path="*" element={
            isLoggedIn ? (
              <Dashboard user={user} onLogout={handleLogout} />
            ) : (
              <Login onLoginSuccess={handleLoginSuccess} />
            )
          } />
        </Routes>
      </BrowserRouter>
    </div>
  );
}

export default App;
