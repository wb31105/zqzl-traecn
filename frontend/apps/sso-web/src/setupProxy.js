const { createProxyMiddleware } = require('http-proxy-middleware');

module.exports = function(app) {
  app.use(
    '/v1/auth',
    createProxyMiddleware({
      target: process.env.REACT_APP_SSO_SERVER_URL,
      changeOrigin: true,
      pathRewrite: {
        '^/v1/auth': '/sso/v1/auth',
      },
    })
  );
};
