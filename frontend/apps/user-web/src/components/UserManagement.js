import React, { useState, useEffect } from 'react';
import axios from 'axios';

const API_BASE_URL = 'http://localhost:8081/user/api/users';

const UserManagement = () => {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(false);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [showEditModal, setShowEditModal] = useState(false);
  const [editingUser, setEditingUser] = useState(null);
  const [editForm, setEditForm] = useState({
    email: '',
    phone: '',
    nickname: '',
    role: ''
  });
  const [showResetPasswordModal, setShowResetPasswordModal] = useState(false);
  const [resetPasswordForm, setResetPasswordForm] = useState({
    newPassword: '',
    confirmPassword: ''
  });

  const fetchUsers = async () => {
    setLoading(true);
    try {
      const response = await axios.get(API_BASE_URL, {
        params: {
          keyword: searchKeyword || undefined,
          page: currentPage,
          size: pageSize
        }
      });
      setUsers(response.data.content || []);
      setTotalPages(response.data.totalPages || 0);
      setTotalElements(response.data.totalElements || 0);
    } catch (err) {
      setError('获取用户列表失败');
      console.error('获取用户列表错误:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchUsers();
  }, [currentPage, pageSize]);

  const handleSearch = (e) => {
    e.preventDefault();
    setCurrentPage(0);
    fetchUsers();
  };

  const handleEdit = (user) => {
    setEditingUser(user);
    setEditForm({
      email: user.email || '',
      phone: user.phone || '',
      nickname: user.nickname || '',
      role: user.role || 'USER'
    });
    setShowEditModal(true);
    setError('');
    setSuccess('');
  };

  const handleEditChange = (e) => {
    const { name, value } = e.target;
    setEditForm(prev => ({
      ...prev,
      [name]: value
    }));
  };

  const handleEditSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setLoading(true);

    try {
      const response = await axios.put(`${API_BASE_URL}/${editingUser.id}`, editForm);
      
      if (response.data.success) {
        setSuccess('用户信息更新成功');
        setTimeout(() => {
          setShowEditModal(false);
          fetchUsers();
        }, 1000);
      } else {
        setError(response.data.message);
      }
    } catch (err) {
      setError('更新用户信息失败');
      console.error('更新用户错误:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleToggleStatus = async (userId, currentStatus) => {
    const action = currentStatus ? '禁用' : '启用';
    if (!window.confirm(`确定要${action}该用户吗？`)) {
      return;
    }

    try {
      const response = await axios.put(`${API_BASE_URL}/${userId}/toggle-status`);
      
      if (response.data.success) {
        setSuccess(response.data.message);
        fetchUsers();
      } else {
        setError(response.data.message);
      }
    } catch (err) {
      setError('操作失败');
      console.error('切换用户状态错误:', err);
    }
  };

  const handleResetPassword = (user) => {
    setEditingUser(user);
    setResetPasswordForm({
      newPassword: '',
      confirmPassword: ''
    });
    setShowResetPasswordModal(true);
    setError('');
    setSuccess('');
  };

  const handleResetPasswordSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    if (resetPasswordForm.newPassword !== resetPasswordForm.confirmPassword) {
      setError('两次输入的密码不一致');
      return;
    }

    if (resetPasswordForm.newPassword.length < 6) {
      setError('密码长度不能少于6位');
      return;
    }

    setLoading(true);

    try {
      const response = await axios.put(`${API_BASE_URL}/${editingUser.id}/reset-password`, {
        newPassword: resetPasswordForm.newPassword
      });
      
      if (response.data.success) {
        setSuccess('密码重置成功');
        setTimeout(() => {
          setShowResetPasswordModal(false);
        }, 1000);
      } else {
        setError(response.data.message);
      }
    } catch (err) {
      setError('重置密码失败');
      console.error('重置密码错误:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (userId) => {
    if (!window.confirm('确定要删除该用户吗？此操作不可恢复！')) {
      return;
    }

    try {
      const response = await axios.delete(`${API_BASE_URL}/${userId}`);
      
      if (response.data.success) {
        setSuccess('用户删除成功');
        fetchUsers();
      } else {
        setError(response.data.message);
      }
    } catch (err) {
      setError('删除用户失败');
      console.error('删除用户错误:', err);
    }
  };

  const formatDate = (dateString) => {
    if (!dateString) return '-';
    return new Date(dateString).toLocaleString('zh-CN');
  };

  return (
    <div className="user-management-container">
      <h1 className="management-title">用户管理</h1>
      
      {error && <div className="error-message">{error}</div>}
      {success && <div className="success-message">{success}</div>}
      
      <div className="search-bar">
        <form onSubmit={handleSearch} className="search-form">
          <input
            type="text"
            value={searchKeyword}
            onChange={(e) => setSearchKeyword(e.target.value)}
            placeholder="搜索用户名、邮箱或昵称..."
            className="search-input"
          />
          <button type="submit" className="search-button">搜索</button>
          <button 
            type="button" 
            className="refresh-button" 
            onClick={() => { setSearchKeyword(''); setCurrentPage(0); fetchUsers(); }}
          >
            刷新
          </button>
        </form>
      </div>

      <div className="table-container">
        <div className="table-wrapper">
          <table className="user-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>用户名</th>
                <th>昵称</th>
                <th>邮箱</th>
                <th>手机号</th>
                <th>角色</th>
                <th>状态</th>
                <th>最后登录</th>
                <th>创建时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan="10" className="loading-cell">加载中...</td>
                </tr>
              ) : users.length === 0 ? (
                <tr>
                  <td colSpan="10" className="empty-cell">暂无数据</td>
                </tr>
              ) : (
                users.map(user => (
                  <tr key={user.id}>
                    <td>{user.id}</td>
                    <td>{user.username}</td>
                    <td>{user.nickname || '-'}</td>
                    <td>{user.email || '-'}</td>
                    <td>{user.phone || '-'}</td>
                    <td>
                      <span className={`role-badge ${user.role}`}>
                        {user.role}
                      </span>
                    </td>
                    <td>
                      <span className={`status-badge ${user.enabled ? 'enabled' : 'disabled'}`}>
                        {user.enabled ? '启用' : '禁用'}
                      </span>
                    </td>
                    <td>{formatDate(user.lastLoginTime)}</td>
                    <td>{formatDate(user.createdAt)}</td>
                    <td>
                      <div className="action-buttons">
                        <button 
                          className="action-button edit-button"
                          onClick={() => handleEdit(user)}
                        >
                          编辑
                        </button>
                        <button 
                          className={`action-button ${user.enabled ? 'disable-button' : 'enable-button'}`}
                          onClick={() => handleToggleStatus(user.id, user.enabled)}
                        >
                          {user.enabled ? '禁用' : '启用'}
                        </button>
                        <button 
                          className="action-button reset-password-button"
                          onClick={() => handleResetPassword(user)}
                        >
                          重置密码
                        </button>
                        <button 
                          className="action-button delete-button"
                          onClick={() => handleDelete(user.id)}
                        >
                          删除
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      <div className="pagination-bar">
        <span className="pagination-info">
          共 {totalElements} 条记录，第 {currentPage + 1} / {totalPages || 1} 页
        </span>
        <div className="pagination-buttons">
          <button
            onClick={() => setCurrentPage(0)}
            disabled={currentPage === 0 || loading}
            className="pagination-button"
          >
            首页
          </button>
          <button
            onClick={() => setCurrentPage(prev => Math.max(0, prev - 1))}
            disabled={currentPage === 0 || loading}
            className="pagination-button"
          >
            上一页
          </button>
          <button
            onClick={() => setCurrentPage(prev => Math.min(totalPages - 1, prev + 1))}
            disabled={currentPage >= totalPages - 1 || loading}
            className="pagination-button"
          >
            下一页
          </button>
          <button
            onClick={() => setCurrentPage(totalPages - 1)}
            disabled={currentPage >= totalPages - 1 || loading}
            className="pagination-button"
          >
            末页
          </button>
          <select
            value={pageSize}
            onChange={(e) => { setPageSize(Number(e.target.value)); setCurrentPage(0); }}
            className="page-size-select"
            disabled={loading}
          >
            <option value="5">5条/页</option>
            <option value="10">10条/页</option>
            <option value="20">20条/页</option>
            <option value="50">50条/页</option>
          </select>
        </div>
      </div>

      {showEditModal && (
        <div className="modal-overlay" onClick={() => setShowEditModal(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <h2 className="modal-title">编辑用户 - {editingUser?.username}</h2>
            
            <form onSubmit={handleEditSubmit} className="edit-form">
              <div className="form-row">
                <div className="form-group half">
                  <label>邮箱</label>
                  <input
                    type="email"
                    name="email"
                    value={editForm.email}
                    onChange={handleEditChange}
                    placeholder="请输入邮箱地址"
                  />
                </div>
                <div className="form-group half">
                  <label>手机号</label>
                  <input
                    type="tel"
                    name="phone"
                    value={editForm.phone}
                    onChange={handleEditChange}
                    placeholder="请输入手机号"
                  />
                </div>
              </div>
              
              <div className="form-row">
                <div className="form-group half">
                  <label>昵称</label>
                  <input
                    type="text"
                    name="nickname"
                    value={editForm.nickname}
                    onChange={handleEditChange}
                    placeholder="请输入昵称"
                  />
                </div>
                <div className="form-group half">
                  <label>角色</label>
                  <select
                    name="role"
                    value={editForm.role}
                    onChange={handleEditChange}
                    className="form-select"
                  >
                    <option value="USER">普通用户</option>
                    <option value="ADMIN">管理员</option>
                  </select>
                </div>
              </div>
              
              <div className="modal-actions">
                <button 
                  type="button" 
                  className="cancel-button"
                  onClick={() => setShowEditModal(false)}
                  disabled={loading}
                >
                  取消
                </button>
                <button 
                  type="submit" 
                  className="save-button"
                  disabled={loading}
                >
                  {loading ? '保存中...' : '保存'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {showResetPasswordModal && (
        <div className="modal-overlay" onClick={() => setShowResetPasswordModal(false)}>
          <div className="modal-content small-modal" onClick={(e) => e.stopPropagation()}>
            <h2 className="modal-title">重置密码 - {editingUser?.username}</h2>
            
            <form onSubmit={handleResetPasswordSubmit} className="edit-form">
              <div className="form-group">
                <label>新密码 *</label>
                <input
                  type="password"
                  name="newPassword"
                  value={resetPasswordForm.newPassword}
                  onChange={(e) => setResetPasswordForm(prev => ({ ...prev, newPassword: e.target.value }))}
                  placeholder="请输入新密码（至少6位）"
                  required
                />
              </div>
              
              <div className="form-group">
                <label>确认新密码 *</label>
                <input
                  type="password"
                  name="confirmPassword"
                  value={resetPasswordForm.confirmPassword}
                  onChange={(e) => setResetPasswordForm(prev => ({ ...prev, confirmPassword: e.target.value }))}
                  placeholder="请再次输入新密码"
                  required
                />
              </div>
              
              <div className="modal-actions">
                <button 
                  type="button" 
                  className="cancel-button"
                  onClick={() => setShowResetPasswordModal(false)}
                  disabled={loading}
                >
                  取消
                </button>
                <button 
                  type="submit" 
                  className="save-button"
                  disabled={loading}
                >
                  {loading ? '重置中...' : '重置密码'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default UserManagement;
