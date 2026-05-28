import React, { useState } from 'react';
import axios from 'axios';

function Register() {
  const [formData, setFormData] = useState({
    username: '',
    password: '',
    confirmPassword: '',
    phone: '',
    verificationCode: ''
  });
  const [message, setMessage] = useState('');
  const [isSuccess, setIsSuccess] = useState(false);
  const [countdown, setCountdown] = useState(0);

  const handleChange = (e) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const sendVerificationCode = async () => {
    if (!formData.phone) {
      setMessage('请先输入手机号');
      return;
    }
    if (!/^1[3-9]\d{9}$/.test(formData.phone)) {
      setMessage('手机号格式不正确');
      return;
    }

    try {
      const response = await axios.post('/v1/auth/send-verification-code', {
        phone: formData.phone,
        type: 'register'
      }, {
        withCredentials: true
      });
      
      if (response.data.success) {
        setMessage('验证码已发送到手机，请注意查收！验证码有效期5分钟');
        setCountdown(60);
        
        const timer = setInterval(() => {
          setCountdown(prev => {
            if (prev <= 1) {
              clearInterval(timer);
              return 0;
            }
            return prev - 1;
          });
        }, 1000);
      } else {
        setMessage(response.data.message || '发送失败');
      }
    } catch (error) {
      setMessage(error.response?.data?.message || '发送失败，请重试');
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setMessage('');

    if (formData.password !== formData.confirmPassword) {
      setMessage('两次输入的密码不一致');
      return;
    }

    try {
      const response = await axios.post('/v1/auth/register', formData, {
        withCredentials: true
      });
      if (response.data.success) {
        setIsSuccess(true);
        setMessage('注册成功！');
      } else {
        setMessage(response.data.message || '注册失败');
      }
    } catch (error) {
      setMessage(error.response?.data?.message || '注册失败，请重试');
    }
  };

  if (isSuccess) {
    return (
      <div className="auth-container">
        <div className="auth-card">
          <h2>🎉 注册成功</h2>
          <p className="success-message">{message}</p>
          <button className="btn btn-primary" onClick={() => window.location.href = '/login'}>
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
        {message && <div className={message.includes('成功') ? 'success-message' : 'error-message'}>{message}</div>}
        <form onSubmit={handleSubmit}>
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
          
          <div className="form-group">
            <label>手机号 *</label>
            <input
              type="tel"
              name="phone"
              value={formData.phone}
              onChange={handleChange}
              placeholder="请输入11位手机号"
              required
              pattern="^1[3-9]\d{9}$"
              title="手机号格式不正确，请输入11位中国大陆手机号"
            />
          </div>
          
          <div className="form-group verification-group">
            <label>验证码 *</label>
            <div className="verification-container">
              <input
                type="text"
                name="verificationCode"
                value={formData.verificationCode}
                onChange={handleChange}
                placeholder="请输入6位验证码"
                required
                maxLength={6}
                pattern="\d{6}"
                title="请输入6位数字验证码"
              />
              <button
                type="button"
                className="btn btn-code"
                onClick={sendVerificationCode}
                disabled={countdown > 0}
              >
                {countdown > 0 ? `${countdown}秒后重发` : '发送验证码'}
              </button>
            </div>
          </div>
          
          <button type="submit" className="btn btn-primary btn-block">
            注册
          </button>
        </form>
        <div className="auth-links">
          <a href="/login">已有账号？立即登录</a>
        </div>
      </div>
    </div>
  );
}

export default Register;
