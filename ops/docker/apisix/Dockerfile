FROM apache/apisix:2.15.0-alpine

ARG APISIX_CONFIG=apisix.yaml
ARG APISIX_CONFIG_FILE=config.yaml

COPY ${APISIX_CONFIG_FILE} /usr/local/apisix/conf/config.yaml
COPY ${APISIX_CONFIG} /usr/local/apisix/conf/apisix.yaml

EXPOSE 9080 9443 9090

CMD ["sh", "-c", "/usr/bin/apisix init && /usr/local/openresty/bin/openresty -p /usr/local/apisix -g 'daemon off;'"]
