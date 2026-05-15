import React from 'react';
import { Link } from 'react-router-dom';

const Dashboard = ({ user, onLogout }) => {
  return (
    <div className="dashboard-container">
      <h1 className="dashboard-title">欢迎使用用户中心</h1>
      <p className="dashboard-welcome">
        你好，<strong>{user.username}</strong>！你已成功登录。
      </p>
      
      <div className="dashboard-cards">
        <Link to="/users" className="dashboard-card">
          <h3>用户管理</h3>
          <p>查看和管理系统中的所有用户</p>
        </Link>
        
        <div className="dashboard-card">
          <h3>账号信息</h3>
          <p>用户名：{user.username}</p>
        </div>
      </div>
    </div>
  );
};

export default Dashboard;
