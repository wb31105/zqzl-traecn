import React, { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import axios from 'axios';

const getEnv = (key) => {
  return (window.__ENV__ && window.__ENV__[key]) || process.env[key];
};

const SSO_SERVER_URL = getEnv('REACT_APP_SSO_SERVER_URL');

const SsoCallback = () => {
  const location = useLocation();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const code = params.get('code');
    const errorParam = params.get('error');
    const errorDescription = params.get('error_description');
    const state = params.get('state');

    if (errorParam) {
      const errorMsg = errorDescription 
        ? decodeURIComponent(errorDescription).split('Parameter:').pop()?.trim() || errorParam
        : errorParam;
      
      if (errorParam === 'invalid_scope' || errorParam === 'access_denied') {
        setError('认证配置错误，正在跳转登录页...');
        setTimeout(() => {
          window.location.href = `${SSO_SERVER_URL}/login`;
        }, 2000);
      } else if (errorParam === 'unauthorized') {
        setError('您没有访问权限');
        setTimeout(() => {
          window.location.href = `${SSO_SERVER_URL}/login`;
        }, 2000);
      } else {
        setError(`认证失败: ${errorMsg}`);
        setTimeout(() => {
          window.location.href = `${SSO_SERVER_URL}/login`;
        }, 3000);
      }
      setLoading(false);
      return;
    }

    if (!code) {
      setError('缺少授权码参数');
      setLoading(false);
      return;
    }

    const storedState = localStorage.getItem('oauth_state');
    if (!state || state !== storedState) {
      setError('State参数验证失败，可能是CSRF攻击');
      setLoading(false);
      setTimeout(() => {
        window.location.href = `${SSO_SERVER_URL}/login`;
      }, 2000);
      return;
    }
    localStorage.removeItem('oauth_state');

    exchangeToken(code);
  }, [location.search]);

  const exchangeToken = async (code) => {
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
          window.location.href = `${SSO_SERVER_URL}/login`;
        }, 2000);
      }
    } catch (err) {
      console.error('令牌交换失败', err);
      setError('登录失败，请重新登录');
      setTimeout(() => {
        window.location.href = `${SSO_SERVER_URL}/login`;
      }, 2000);
    } finally {
      setLoading(false);
    }
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
          <p>即将跳转到登录页面...</p>
        </div>
      </div>
    );
  }

  return null;
};

export default SsoCallback;
