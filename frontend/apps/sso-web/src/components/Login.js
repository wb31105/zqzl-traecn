import React, { useState, useEffect, useRef } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import axios from 'axios';

const getEnv = (key) => {
  return (window.__ENV__ && window.__ENV__[key]) || process.env[key];
};

const SSO_SERVER_URL = getEnv('REACT_APP_SSO_SERVER_URL');

const Login = ({ onLoginSuccess }) => {
  const location = useLocation();
  const navigate = useNavigate();
  const formRef = useRef(null);
  
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [captcha, setCaptcha] = useState('');
  const [captchaKey, setCaptchaKey] = useState('');
  const [captchaImage, setCaptchaImage] = useState('');
  const [showCaptcha, setShowCaptcha] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [continueUrl, setContinueUrl] = useState('');
  const [isOAuth2Flow, setIsOAuth2Flow] = useState(false);

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const continueParam = params.get('continue');
    const logoutParam = params.get('logout');
    const errorParam = params.get('error');
    
    if (continueParam) {
      setContinueUrl(continueParam);
      setIsOAuth2Flow(true);
    }
    
    if (logoutParam) {
      setError('您已成功退出登录');
    }

    if (errorParam) {
      setError('登录已过期，请重新登录');
    }
  }, [location.search]);

  const fetchCaptcha = async () => {
    try {
      const response = await axios.get('/v1/auth/captcha', { withCredentials: true });
      setCaptchaKey(response.data.captchaKey);
      setCaptchaImage(response.data.captchaImage);
    } catch (err) {
      console.error('获取验证码失败', err);
    }
  };

  const checkCaptchaRequirement = async (user) => {
    if (!user) return;
    try {
      const response = await axios.get('/v1/auth/check-captcha', {
        params: { username: user },
        withCredentials: true
      });
      if (response.data && !showCaptcha) {
        setShowCaptcha(true);
        fetchCaptcha();
      }
    } catch (err) {
      console.error('检查验证码需求失败', err);
    }
  };

  useEffect(() => {
    if (username) {
      const timer = setTimeout(() => {
        checkCaptchaRequirement(username);
      }, 300);
      return () => clearTimeout(timer);
    }
  }, [username]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const params = new URLSearchParams();
      params.append('username', username);
      params.append('password', password);
      if (showCaptcha) {
        params.append('captcha', captcha);
        params.append('captchaKey', captchaKey);
      }

      const response = await axios.post('/v1/auth/login', params, {
        withCredentials: true,
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
      });

      if (response.data && response.data.success) {
        const redirectUrl = response.data.redirectUrl;
        if (redirectUrl && redirectUrl !== '/') {
          window.location.href = redirectUrl;
        } else {
          navigate('/');
        }
      } else {
        setError(response.data?.message || '登录失败');
      }
    } catch (err) {
      if (err.response?.status === 401) {
        setError(err.response?.data?.message || '用户名或密码错误');
      } else if (err.response?.data?.message) {
        setError(err.response.data.message);
      } else {
        setError('登录失败，请稍后重试');
      }
      
      if (showCaptcha) {
        fetchCaptcha();
      }
    } finally {
      setLoading(false);
      setPassword('');
      setCaptcha('');
    }
  };

  const refreshCaptcha = () => {
    fetchCaptcha();
  };

  return (
    <div className="login-page-wrapper">
      <div className="login-background">
        <div className="login-bg-shape shape-1"></div>
        <div className="login-bg-shape shape-2"></div>
        <div className="login-bg-shape shape-3"></div>
      </div>
      <div className="login-container">
        <div className="login-header">
          <div className="login-logo">
            <span className="logo-icon">🔐</span>
          </div>
          <h1 className="login-title">欢迎登录</h1>
          <p className="login-subtitle">SSO 统一认证中心</p>
        </div>
        
        {error && <div className="error-message">{error}</div>}
        
        <form onSubmit={handleSubmit} className="login-form">
          <div className="form-group">
            <label htmlFor="username" className="form-label">
              <span className="label-icon">👤</span>
              用户名
            </label>
            <input
              type="text"
              id="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="请输入用户名"
              required
              className="form-input"
            />
          </div>
          
          <div className="form-group">
            <label htmlFor="password" className="form-label">
              <span className="label-icon">🔒</span>
              密码
            </label>
            <input
              type="password"
              id="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="请输入密码"
              required
              className="form-input"
            />
          </div>
          
          {showCaptcha && (
            <div className="form-group">
              <label htmlFor="captcha" className="form-label">
                <span className="label-icon">🔤</span>
                验证码
              </label>
              <div className="captcha-group">
                <input
                  type="text"
                  id="captcha"
                  value={captcha}
                  onChange={(e) => setCaptcha(e.target.value)}
                  placeholder="请输入验证码"
                  required
                  className="form-input captcha-input"
                />
                <img
                  src={captchaImage}
                  alt="验证码"
                  className="captcha-image"
                  onClick={refreshCaptcha}
                  title="点击刷新验证码"
                />
              </div>
            </div>
          )}
          
          <button type="submit" className="login-button" disabled={loading}>
            {loading ? (
              <span className="button-content">
                <span className="button-spinner"></span>
                登录中...
              </span>
            ) : '登 录'}
          </button>
        </form>
        
        <div className="auth-links">
          <a href="/forgot-password" className="auth-link">忘记密码？</a>
          <a href="/register" className="auth-link">没有账号？立即注册</a>
        </div>
        
        <div className="login-footer">
          <p>© 2024 SSO 统一认证中心</p>
        </div>
      </div>
    </div>
  );
};

export default Login;
