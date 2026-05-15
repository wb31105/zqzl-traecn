import React, { useState, useEffect } from 'react';
import axios from 'axios';

const API_BASE_URL = 'http://localhost:8080/sso/api/auth';

const Login = ({ onLoginSuccess }) => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [captcha, setCaptcha] = useState('');
  const [captchaKey, setCaptchaKey] = useState('');
  const [captchaImage, setCaptchaImage] = useState('');
  const [showCaptcha, setShowCaptcha] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const fetchCaptcha = async () => {
    try {
      const response = await axios.get(`${API_BASE_URL}/captcha`);
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
        params: { username: user }
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
      const loginData = {
        username,
        password,
        captcha: showCaptcha ? captcha : undefined,
        captchaKey: showCaptcha ? captchaKey : undefined
      };

      const response = await axios.post(`${API_BASE_URL}/login`, loginData);
      
      if (response.data.success) {
        localStorage.setItem('token', response.data.token);
        onLoginSuccess({ username: response.data.username });
      } else {
        setError(response.data.message);
        if (response.data.requireCaptcha && !showCaptcha) {
          setShowCaptcha(true);
          fetchCaptcha();
        } else if (showCaptcha) {
          fetchCaptcha();
        }
      }
    } catch (err) {
      setError('登录失败，请稍后重试');
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
    </div>
  );
};

export default Login;
