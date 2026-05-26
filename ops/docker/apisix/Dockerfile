ARG BASE_IMAGE=apache/apisix:2.15.0-alpine

FROM ${BASE_IMAGE}

COPY ops/docker/apisix/apisix.yaml.template /usr/local/apisix/conf/apisix.yaml.template
COPY ops/docker/apisix/config.yaml /usr/local/apisix/conf/config.yaml
COPY ops/docker/apisix/docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
COPY ops/docker/apisix/start.sh /usr/local/bin/start.sh

RUN chmod +x /usr/local/bin/docker-entrypoint.sh \
    && chmod +x /usr/local/bin/start.sh

EXPOSE 9080 9443 9090

ENTRYPOINT ["start.sh"]
CMD ["docker"]
