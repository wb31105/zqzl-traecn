import React, { useState, useEffect } from 'react';
import axios from 'axios';

function ForgotPassword() {
  const [formData, setFormData] = useState({
    username: '',
    email: '',
    newPassword: '',
    captcha: '',
    captchaKey: ''
  });
  const [captchaImage, setCaptchaImage] = useState('');
  const [message, setMessage] = useState('');
  const [isSuccess, setIsSuccess] = useState(false);

  useEffect(() => {
    loadCaptcha();
  }, []);

  const loadCaptcha = async () => {
    try {
      const response = await axios.get('http://localhost:8080/sso/api/auth/captcha');
      setCaptchaImage(response.data.captchaImage);
      setFormData(prev => ({ ...prev, captchaKey: response.data.captchaKey }));
    } catch (error) {
      console.error('加载验证码失败', error);
    }
  };

  const handleChange = (e) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setMessage('');

    try {
      const response = await axios.post('http://localhost:8080/sso/api/auth/forgot-password', formData);
      if (response.data.success) {
        setIsSuccess(true);
        setMessage('密码重置成功！');
      } else {
        setMessage(response.data.message || '重置失败');
        loadCaptcha();
      }
    } catch (error) {
      setMessage(error.response?.data?.message || '重置失败，请重试');
      loadCaptcha();
    }
  };

  if (isSuccess) {
    return (
      <div className="auth-container">
        <div className="auth-card">
          <h2>🎉 密码重置成功</h2>
          <p className="success-message">{message}</p>
          <button className="btn btn-primary" onClick={() => window.location.href = '/'}>
            返回登录
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="auth-container">
      <div className="auth-card">
        <h2>重置密码</h2>
        {message && <div className="error-message">{message}</div>}
        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label>用户名 *</label>
            <input
              type="text"
              name="username"
              value={formData.username}
              onChange={handleChange}
              placeholder="请输入用户名"
              required
            />
          </div>
          <div className="form-group">
            <label>邮箱</label>
            <input
              type="email"
              name="email"
              value={formData.email}
              onChange={handleChange}
              placeholder="请输入注册邮箱（可选）"
            />
          </div>
          <div className="form-group">
            <label>新密码 *</label>
            <input
              type="password"
              name="newPassword"
              value={formData.newPassword}
              onChange={handleChange}
              placeholder="请输入新密码（6-100个字符）"
              required
            />
          </div>
          <div className="form-group captcha-group">
            <label>验证码 *</label>
            <div className="captcha-container">
              <input
                type="text"
                name="captcha"
                value={formData.captcha}
                onChange={handleChange}
                placeholder="请输入验证码"
                required
              />
              <img 
                src={captchaImage} 
                alt="验证码" 
                onClick={loadCaptcha}
                title="点击刷新验证码"
                className="captcha-image"
              />
            </div>
          </div>
          <button type="submit" className="btn btn-primary btn-block">
            重置密码
          </button>
        </form>
        <div className="auth-links">
          <a href="/">返回登录</a>
          <a href="/register">没有账号？立即注册</a>
        </div>
      </div>
    </div>
  );
}

export default ForgotPassword;
