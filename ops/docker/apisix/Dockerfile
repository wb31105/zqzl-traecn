FROM apache/apisix:2.15.0-alpine

COPY apisix.yaml.template /usr/local/apisix/conf/apisix.yaml.template
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh

RUN chmod +x /usr/local/bin/docker-entrypoint.sh

EXPOSE 9080 9443 9090

ENTRYPOINT ["docker-entrypoint.sh"]
CMD ["sh", "-c", "/usr/bin/apisix init && /usr/local/openresty/bin/openresty -p /usr/local/apisix -g 'daemon off;'"]
