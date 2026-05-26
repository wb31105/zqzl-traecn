ARG BUILDER_IMAGE=node:22-alpine
ARG RUNTIME_IMAGE=nginx:alpine

FROM ${BUILDER_IMAGE} AS builder
WORKDIR /app

COPY frontend/apps/user-web/package*.json ./
RUN npm install

COPY frontend/apps/user-web .
RUN npm run build

FROM ${RUNTIME_IMAGE}
WORKDIR /usr/share/nginx/html

COPY --from=builder /app/build .
COPY frontend/apps/user-web/deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY frontend/apps/user-web/deploy/docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
COPY frontend/apps/user-web/deploy/start.sh /usr/local/bin/start.sh

RUN chmod +x /usr/local/bin/docker-entrypoint.sh \
    && chmod +x /usr/local/bin/start.sh

EXPOSE 80

ENTRYPOINT ["start.sh"]
CMD ["docker"]
