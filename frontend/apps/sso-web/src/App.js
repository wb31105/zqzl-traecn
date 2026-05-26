import React, { useEffect, useState } from 'react';
import './App.css';

const getEnv = (key) => {
  return (window.__ENV__ && window.__ENV__[key]) || process.env[key];
};

const SSO_SERVER_URL = getEnv('REACT_APP_SSO_SERVER_URL');

const DEMO_CLIENTS = [
  {
    id: 'user-web-client',
    name: '用户管理系统',
    description: '用户中心管理系统，用于管理用户信息',
    url: getEnv('REACT_APP_USER_WEB_URL'),
    icon: '👥'
  }
];

function App() {
  const [clients, setClients] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setClients(DEMO_CLIENTS);
    setLoading(false);
  }, []);

  const handleLogin = () => {
    window.location.href = `${SSO_SERVER_URL}/login`;
  };

  const handleLogout = () => {
    window.location.href = `${SSO_SERVER_URL}/logout`;
  };

  const handleAccessClient = (client) => {
    const params = new URLSearchParams({
      response_type: 'code',
      client_id: client.id,
      redirect_uri: client.url + '/sso/callback',
      scope: 'openid profile read write',
      state: Math.random().toString(36).substring(2, 15)
    });
    window.location.href = `${SSO_SERVER_URL}/oauth2/authorize?${params.toString()}`;
  };

  if (loading) {
    return (
      <div className="loading-container">
        <div className="loading-spinner"></div>
        <p>加载中...</p>
      </div>
    );
  }

  return (
    <div className="App">
      <header className="sso-header">
        <div className="header-content">
          <div className="logo">
            <span className="logo-icon">🔐</span>
            <h1>SSO 统一认证中心</h1>
          </div>
          <div className="header-actions">
            <button className="btn-secondary" onClick={handleLogout}>
              退出登录
            </button>
          </div>
        </div>
      </header>

      <main className="sso-main">
        <div className="welcome-section">
          <h2>欢迎使用 SSO 单点登录服务</h2>
          <p>安全、便捷的统一身份认证解决方案</p>
        </div>

        <div className="features-section">
          <div className="feature-card">
            <div className="feature-icon">🛡️</div>
            <h3>安全可靠</h3>
            <p>基于OAuth2.0授权码模式，保障数据安全</p>
          </div>
          <div className="feature-card">
            <div className="feature-icon">🚀</div>
            <h3>便捷高效</h3>
            <p>一次登录，访问所有授权应用</p>
          </div>
          <div className="feature-card">
            <div className="feature-icon">🔗</div>
            <h3>开放集成</h3>
            <p>支持第三方应用快速接入</p>
          </div>
        </div>

        <div className="apps-section">
          <h2>可用应用</h2>
          <div className="apps-grid">
            {clients.map((client) => (
              <div key={client.id} className="app-card">
                <div className="app-icon">{client.icon}</div>
                <h3>{client.name}</h3>
                <p>{client.description}</p>
                <button 
                  className="btn-primary"
                  onClick={() => handleAccessClient(client)}
                >
                  访问应用
                </button>
              </div>
            ))}
          </div>
        </div>

        <div className="api-section">
          <h2>OAuth2 端点</h2>
          <div className="endpoints-list">
            <div className="endpoint-item">
              <code className="endpoint-method">GET</code>
              <div className="endpoint-info">
                <code className="endpoint-url">/oauth2/authorize</code>
                <span className="endpoint-desc">授权端点</span>
              </div>
            </div>
            <div className="endpoint-item">
              <code className="endpoint-method">POST</code>
              <div className="endpoint-info">
                <code className="endpoint-url">/oauth2/token</code>
                <span className="endpoint-desc">令牌端点</span>
              </div>
            </div>
            <div className="endpoint-item">
              <code className="endpoint-method">GET</code>
              <div className="endpoint-info">
                <code className="endpoint-url">/oauth2/jwks</code>
                <span className="endpoint-desc">公钥端点</span>
              </div>
            </div>
            <div className="endpoint-item">
              <code className="endpoint-method">GET</code>
              <div className="endpoint-info">
                <code className="endpoint-url">/userinfo</code>
                <span className="endpoint-desc">用户信息端点</span>
              </div>
            </div>
            <div className="endpoint-item">
              <code className="endpoint-method">GET</code>
              <div className="endpoint-info">
                <code className="endpoint-url">/.well-known/openid-configuration</code>
                <span className="endpoint-desc">发现文档</span>
              </div>
            </div>
          </div>
        </div>
      </main>

      <footer className="sso-footer">
        <p>© 2024 SSO 统一认证中心 - Powered by OAuth2.0</p>
      </footer>
    </div>
  );
}

export default App;
