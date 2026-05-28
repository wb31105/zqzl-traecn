import React, { useState, useEffect } from 'react';
import axios from 'axios';

function ForgotPassword() {
  const [step, setStep] = useState(1);
  const [formData, setFormData] = useState({
    identifier: '',
    captcha: '',
    captchaKey: '',
    newPassword: '',
    confirmPassword: '',
    verificationCode: ''
  });
  const [captchaImage, setCaptchaImage] = useState('');
  const [message, setMessage] = useState('');
  const [isSuccess, setIsSuccess] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const [verifyToken, setVerifyToken] = useState('');
  const [userEmail, setUserEmail] = useState('');
  const [userPhone, setUserPhone] = useState('');
  const [selectedContact, setSelectedContact] = useState('');

  useEffect(() => {
    if (step === 1) {
      loadCaptcha();
    }
  }, [step]);

  useEffect(() => {
    if (step === 2) {
      if (userPhone) {
        setSelectedContact('phone');
      } else if (userEmail) {
        setSelectedContact('email');
      }
    }
  }, [step, userPhone, userEmail]);

  const loadCaptcha = async () => {
    try {
      const response = await axios.get('/v1/auth/captcha', {
        withCredentials: true
      });
      setCaptchaImage(response.data.captchaImage);
      setFormData(prev => ({ ...prev, captchaKey: response.data.captchaKey }));
    } catch (error) {
      console.error('加载验证码失败', error);
    }
  };

  const handleChange = (e) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleNextStep = async (e) => {
    e.preventDefault();
    setMessage('');

    if (!formData.identifier) {
      setMessage('请输入用户名、手机号或邮箱');
      return;
    }

    try {
      const response = await axios.post('/v1/auth/forgot-password/verify', {
        identifier: formData.identifier,
        captcha: formData.captcha,
        captchaKey: formData.captchaKey
      }, {
        withCredentials: true
      });
      
      if (response.data.success) {
        setVerifyToken(response.data.verifyToken);
        setUserEmail(response.data.email);
        setUserPhone(response.data.phone);
        setStep(2);
        setMessage('');
      } else {
        setMessage(response.data.message || '验证失败');
        loadCaptcha();
      }
    } catch (error) {
      setMessage(error.response?.data?.message || '验证失败，请重试');
      loadCaptcha();
    }
  };

  const sendVerificationCode = async () => {
    try {
      const response = await axios.post('/v1/auth/send-verification-code', {
        email: selectedContact === 'email' ? userEmail : null,
        phone: selectedContact === 'phone' ? userPhone : null,
        type: 'forgot_password'
      }, {
        withCredentials: true
      });
      
      if (response.data.success) {
        setMessage('验证码已发送，请注意查收！验证码有效期5分钟');
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

    if (formData.newPassword !== formData.confirmPassword) {
      setMessage('两次输入的密码不一致');
      return;
    }

    try {
      const response = await axios.post('/v1/auth/forgot-password', {
        newPassword: formData.newPassword,
        confirmPassword: formData.confirmPassword,
        verificationCode: formData.verificationCode,
        verifyToken: verifyToken,
        selectedContact: selectedContact
      }, {
        withCredentials: true
      });
      
      if (response.data.success) {
        setIsSuccess(true);
        setMessage('密码重置成功！');
      } else {
        setMessage(response.data.message || '重置失败');
      }
    } catch (error) {
      setMessage(error.response?.data?.message || '重置失败，请重试');
    }
  };

  const goBack = () => {
    setStep(1);
    setMessage('');
    setVerifyToken('');
    setSelectedContact('');
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
        <h2>{step === 1 ? '验证身份' : '重置密码'}</h2>
        {message && <div className={message.includes('成功') ? 'success-message' : 'error-message'}>{message}</div>}
        
        {step === 1 ? (
          <form onSubmit={handleNextStep}>
            <div className="form-group">
              <label>用户名 / 手机号 / 邮箱 *</label>
              <input
                type="text"
                name="identifier"
                value={formData.identifier}
                onChange={handleChange}
                placeholder="请输入用户名、手机号或邮箱"
                required
              />
            </div>
            
            <div className="form-group captcha-group">
              <label>图形验证码 *</label>
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
              下一步
            </button>
          </form>
        ) : (
          <form onSubmit={handleSubmit}>
            <div className="form-row">
              <div className="form-group half">
                <label>新密码 *</label>
                <input
                  type="password"
                  name="newPassword"
                  value={formData.newPassword}
                  onChange={handleChange}
                  placeholder="请输入新密码（6-100个字符）"
                  required
                  minLength={6}
                />
              </div>
              <div className="form-group half">
                <label>确认密码 *</label>
                <input
                  type="password"
                  name="confirmPassword"
                  value={formData.confirmPassword}
                  onChange={handleChange}
                  placeholder="请再次输入新密码"
                  required
                  minLength={6}
                />
              </div>
            </div>
            
            <div className="form-group">
              <label>选择验证码发送方式 *</label>
              <div className="contact-options">
                {userPhone && (
                  <label className="contact-option">
                    <input
                      type="radio"
                      name="contact"
                      value="phone"
                      checked={selectedContact === 'phone'}
                      onChange={() => setSelectedContact('phone')}
                    />
                    <span>手机号：{userPhone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')}</span>
                  </label>
                )}
                {userEmail && (
                  <label className="contact-option">
                    <input
                      type="radio"
                      name="contact"
                      value="email"
                      checked={selectedContact === 'email'}
                      onChange={() => setSelectedContact('email')}
                    />
                    <span>邮箱：{userEmail}</span>
                  </label>
                )}
              </div>
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
                  disabled={countdown > 0 || !selectedContact}
                >
                  {countdown > 0 ? `${countdown}秒后重发` : '发送验证码'}
                </button>
              </div>
            </div>
            
            <div className="form-actions">
              <button type="button" className="btn btn-secondary" onClick={goBack}>
                返回
              </button>
              <button type="submit" className="btn btn-primary">
                重置密码
              </button>
            </div>
          </form>
        )}
        
        <div className="auth-links">
          <a href="/login">返回登录</a>
          <a href="/register">没有账号？立即注册</a>
        </div>
      </div>
    </div>
  );
}

export default ForgotPassword;
