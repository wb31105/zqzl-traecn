FROM nginx:alpine

COPY .deploy/docker/nginx/nginx-docker.conf /etc/nginx/nginx.conf

EXPOSE 80

ENTRYPOINT ["nginx", "-g", "daemon off;"]
