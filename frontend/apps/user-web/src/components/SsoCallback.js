import React, { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import axios from 'axios';

const SSO_WEB_URL = `${window.location.protocol}//${window.location.hostname}`;
const USER_SERVER_URL = `${process.env.REACT_APP_API_BASE_URL}`;

const SsoCallback = () => {
  const location = useLocation();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const ticket = params.get('ticket');
    const username = params.get('username');

    console.log('SsoCallback - ticket:', ticket, 'username:', username);

    if (!ticket) {
      setError('缺少票据参数');
      setLoading(false);
      return;
    }

    validateTicket(ticket, username);
  }, [location.search]);

  const validateTicket = async (ticket, username) => {
    try {
      console.log('开始验证票据:', ticket);
      const response = await axios.get(`${USER_SERVER_URL}/sso/api/auth/validate-ticket`, {
        params: { ticket }
      });

      console.log('票据验证响应:', response.data);

      if (response.data.success) {
        localStorage.setItem('sso_token', ticket);
        localStorage.setItem('username', username || response.data.username);
        console.log('验证成功，跳转到 /users');
        window.location.href = '/users';
      } else {
        setError(response.data.message || '票据验证失败');
        console.log('验证失败:', response.data.message);
        setTimeout(() => {
          window.location.href = `${SSO_WEB_URL}?redirect=${encodeURIComponent(window.location.origin)}`;
        }, 2000);
      }
    } catch (err) {
      console.error('票据验证失败', err);
      setError('票据验证失败，请重新登录');
      setTimeout(() => {
        window.location.href = `${SSO_WEB_URL}?redirect=${encodeURIComponent(window.location.origin)}`;
      }, 2000);
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="auth-container">
        <div className="auth-card">
          <h2>验证中...</h2>
          <p>正在验证SSO票据，请稍候</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="auth-container">
        <div className="auth-card">
          <h2>验证失败</h2>
          <div className="error-message">{error}</div>
          <p>2秒后跳转到登录页面...</p>
        </div>
      </div>
    );
  }

  return null;
};

export default SsoCallback;
