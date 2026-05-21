FROM apache/apisix:2.15.0-alpine

COPY config.yaml /usr/local/apisix/conf/config.yaml
COPY apisix.yaml /usr/local/apisix/conf/apisix.yaml

EXPOSE 9080 9443

CMD ["sh", "-c", "/usr/bin/apisix init && /usr/local/openresty/bin/openresty -p /usr/local/apisix -g 'daemon off;'"]
