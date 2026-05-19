FROM node:18-alpine AS builder
WORKDIR /app

COPY frontend/apps/sso-web/package*.json ./
RUN npm install --production=false

COPY frontend/apps/sso-web ./

RUN npm run build

FROM nginx:alpine
WORKDIR /usr/share/nginx/html

COPY --from=builder /app/build .
COPY .deploy/docker/frontend/nginx-spa.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

ENTRYPOINT ["nginx", "-g", "daemon off;"]
