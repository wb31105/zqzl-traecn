FROM nginx:alpine
WORKDIR /usr/share/nginx/html

COPY frontend/apps/sso-web/build .
COPY .deploy/docker/frontend/nginx-spa.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

ENTRYPOINT ["nginx", "-g", "daemon off;"]
