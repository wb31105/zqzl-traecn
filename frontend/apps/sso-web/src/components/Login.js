import React, { useState, useEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';
import axios from 'axios';

const SSO_SERVER_URL = process.env.REACT_APP_SSO_SERVER_URL;
const API_BASE_URL = `${SSO_SERVER_URL}/v1/auth`;

/**
 * SSO OAuth2 登录页面（前后端分离）
 * 
 * 流程：
 * 1. 用户被 Spring Security 302 重定向到 /login?continue=/oauth2/authorize?...
 * 2. 前端显示登录表单
 * 3. 提交表单到 /v1/auth/login（Spring Security 处理）
 * 4. 登录成功创建 SSO_SESSION Cookie → 重定向回 /oauth2/authorize
 * 5. Spring Security 生成 code → 回调客户端
 */
const Login = ({ onLoginSuccess }) => {
  const location = useLocation();
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
    
    if (continueParam) {
      setContinueUrl(continueParam);
      setIsOAuth2Flow(true);
    }
    
    if (logoutParam) {
      setError('您已成功退出登录');
    }
  }, [location.search]);

  const fetchCaptcha = async () => {
    try {
      const response = await axios.get(`${API_BASE_URL}/captcha`, { withCredentials: true });
      setCaptchaKey(response.data.captchaKey);
      setCaptchaImage(response.data.captchaImage);
    } catch (err) {
      console.error('获取验证码失败', err);
    }
  };

  const checkCaptchaRequirement = async (user) => {
    if (!user) return;
    try {
      const response = await axios.get(`${API_BASE_URL}/check-captcha`, {
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
      const formData = new FormData();
      formData.append('username', username);
      formData.append('password', password);
      if (showCaptcha) {
        formData.append('captcha', captcha);
        formData.append('captchaKey', captchaKey);
      }

      const response = await axios.post(`${SSO_SERVER_URL}/login`, formData, {
        withCredentials: true,
        maxRedirects: 0,
        validateStatus: (status) => status >= 200 && status < 400
      });

      if (isOAuth2Flow && continueUrl) {
        window.location.href = `${SSO_SERVER_URL}${continueUrl}`;
      } else if (response.data?.success) {
        onLoginSuccess({ username });
      } else {
        setError('登录成功');
        setTimeout(() => {
          window.location.href = '/';
        }, 500);
      }
    } catch (err) {
      if (err.response?.status === 302 || err.response?.status === 200) {
        if (isOAuth2Flow && continueUrl) {
          window.location.href = `${SSO_SERVER_URL}${continueUrl}`;
        } else {
          window.location.href = '/';
        }
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
    <div className="login-container">
      <h1 className="login-title">SSO 单点登录</h1>
      
      {error && <div className="error-message">{error}</div>}
      
      <form onSubmit={handleSubmit}>
        <div className="form-group">
          <label htmlFor="username">用户名</label>
          <input
            type="text"
            id="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            placeholder="请输入用户名"
            required
          />
        </div>
        
        <div className="form-group">
          <label htmlFor="password">密码</label>
          <input
            type="password"
            id="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="请输入密码"
            required
          />
        </div>
        
        {showCaptcha && (
          <div className="form-group">
            <label htmlFor="captcha">验证码</label>
            <div className="captcha-group">
              <input
                type="text"
                id="captcha"
                value={captcha}
                onChange={(e) => setCaptcha(e.target.value)}
                placeholder="请输入验证码"
                required
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
          {loading ? '登录中...' : '登录'}
        </button>
      </form>
      
      <div className="auth-links">
        <a href="/forgot-password">忘记密码？</a>
        <a href="/register">没有账号？立即注册</a>
      </div>
    </div>
  );
};

export default Login;
