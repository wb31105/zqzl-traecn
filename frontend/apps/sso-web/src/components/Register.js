import React, { useState, useEffect, useRef } from 'react';
import axios from 'axios';

function Register() {
  const [formData, setFormData] = useState({
    username: '',
    password: '',
    confirmPassword: '',
    email: '',
    phone: '',
    nickname: '',
    captcha: '',
    captchaKey: ''
  });
  const [captchaImage, setCaptchaImage] = useState('');
  const [message, setMessage] = useState('');
  const [isSuccess, setIsSuccess] = useState(false);
  const formRef = useRef(null);

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

    if (formData.password !== formData.confirmPassword) {
      alert('两次输入的密码不一致');
      return;
    }

    try {
      const response = await axios.post('http://localhost:8080/sso/api/auth/register', formData);
      if (response.data.success) {
        setIsSuccess(true);
        setMessage('注册成功！');
      } else {
        setMessage(response.data.message || '注册失败');
        loadCaptcha();
      }
    } catch (error) {
      setMessage(error.response?.data?.message || '注册失败，请重试');
      loadCaptcha();
    }
  };

  if (isSuccess) {
    return (
      <div className="auth-container">
        <div className="auth-card">
          <h2>🎉 注册成功</h2>
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
        <h2>用户注册</h2>
        {message && <div className="error-message">{message}</div>}
        <form ref={formRef} onSubmit={handleSubmit}>
          <div className="form-group">
            <label>用户名 *</label>
            <input
              type="text"
              name="username"
              value={formData.username}
              onChange={handleChange}
              placeholder="3-50个字符，字母数字下划线"
              required
              pattern="^[a-zA-Z0-9_]{3,50}$"
              title="用户名长度必须在3-50个字符之间，只能包含字母、数字和下划线"
            />
          </div>
          <div className="form-row">
            <div className="form-group half">
              <label>密码 *</label>
              <input
                type="password"
                name="password"
                value={formData.password}
                onChange={handleChange}
                placeholder="6-100个字符"
                required
                pattern=".{6,100}"
                title="密码长度必须在6-100个字符之间"
              />
            </div>
            <div className="form-group half">
              <label>确认密码 *</label>
              <input
                type="password"
                name="confirmPassword"
                value={formData.confirmPassword}
                onChange={handleChange}
                placeholder="再次输入密码"
                required
                pattern=".{6,100}"
                title="密码长度必须在6-100个字符之间"
              />
            </div>
          </div>
          <div className="form-row">
            <div className="form-group half">
              <label>邮箱</label>
              <input
                type="email"
                name="email"
                value={formData.email}
                onChange={handleChange}
                placeholder="请输入邮箱"
                title="请输入有效的邮箱地址"
              />
            </div>
            <div className="form-group half">
              <label>手机号</label>
              <input
                type="tel"
                name="phone"
                value={formData.phone}
                onChange={handleChange}
                placeholder="中国大陆手机号"
                pattern="^1[3-9]\d{9}$"
                title="手机号格式不正确，请输入11位中国大陆手机号"
              />
            </div>
          </div>
          <div className="form-group">
            <label>昵称</label>
            <input
              type="text"
              name="nickname"
              value={formData.nickname}
              onChange={handleChange}
              placeholder="请输入昵称"
              maxLength={50}
              title="昵称长度不能超过50个字符"
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
                title="请输入验证码"
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
            注册
          </button>
        </form>
        <div className="auth-links">
          <a href="/">已有账号？立即登录</a>
        </div>
      </div>
    </div>
  );
}

export default Register;
