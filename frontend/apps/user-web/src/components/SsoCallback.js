import React, { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import axios from 'axios';

const SsoCallback = () => {
  const location = useLocation();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const code = params.get('code');
    const state = params.get('state');

    if (!code) {
      setError('缺少授权码参数');
      setLoading(false);
      return;
    }

    exchangeToken(code, state);
  }, [location.search]);

  const exchangeToken = async (code, state) => {
    try {
      const response = await axios.post('/v1/oauth2/token', {
        code: code
      });

      if (response.data.success) {
        const tokenData = response.data.data;
        localStorage.setItem('access_token', tokenData.access_token);
        localStorage.setItem('refresh_token', tokenData.refresh_token);
        localStorage.setItem('token_type', tokenData.token_type);
        localStorage.setItem('expires_in', tokenData.expires_in);
        localStorage.setItem('login_time', Date.now().toString());

        const originalPath = localStorage.getItem('originalPath') || '/users';
        localStorage.removeItem('originalPath');

        window.location.href = originalPath;
      } else {
        setError(response.data.message || '令牌交换失败');
        setTimeout(() => {
          redirectToLogin();
        }, 2000);
      }
    } catch (err) {
      console.error('令牌交换失败', err);
      setError('登录失败，请重新登录');
      setTimeout(() => {
        redirectToLogin();
      }, 2000);
    } finally {
      setLoading(false);
    }
  };

  const redirectToLogin = () => {
    const originalPath = window.location.pathname;
    localStorage.setItem('originalPath', originalPath);
    window.location.href = '/';
  };

  if (loading) {
    return (
      <div className="auth-container">
        <div className="auth-card">
          <h2>登录中...</h2>
          <p>正在完成OAuth2认证，请稍候</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="auth-container">
        <div className="auth-card">
          <h2>登录失败</h2>
          <div className="error-message">{error}</div>
          <p>2秒后跳转到登录页面...</p>
        </div>
      </div>
    );
  }

  return null;
};

export default SsoCallback;
