const { createProxyMiddleware } = require('http-proxy-middleware');

module.exports = function(app) {
  app.use(
    '/v1/auth',
    createProxyMiddleware({
      target: process.env.REACT_APP_SSO_SERVER_URL || 'http://sso.local.bw.com:8080',
      changeOrigin: true,
    })
  );
};
