import React from 'react';

const Dashboard = ({ user, onLogout }) => {
  return (
    <div className="dashboard-container">
      <h1 className="dashboard-title">欢迎使用 SSO 单点登录系统</h1>
      <p className="dashboard-welcome">
        你好，<strong>{user.username}</strong>！你已成功登录。
      </p>
      <button className="logout-button" onClick={onLogout}>
        退出登录
      </button>
    </div>
  );
};

export default Dashboard;
