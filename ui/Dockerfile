ARG DOCKER_HUB_MIRROR=docker.m.daocloud.io
FROM ${DOCKER_HUB_MIRROR}/library/node:22-alpine@sha256:c610fcdfb1d5b4740dd70c284ed3cb16bb857e0f7166196e36a5501df7a3aa32 AS build

WORKDIR /workspace/ui
COPY ui/package.json ui/package-lock.json ./
RUN --mount=type=cache,id=vgo-ats-npm,target=/root/.npm,sharing=locked \
    npm ci --prefer-offline
COPY ui/ ./
RUN npm run build

FROM ${DOCKER_HUB_MIRROR}/library/nginx:1.28.3-alpine@sha256:a8b39bd9cf0f83869a2162827a0caf6137ddf759d50a171451b335cecc87d236 AS local
COPY deploy/nginx-local.conf /etc/nginx/conf.d/default.conf
COPY deploy/security-headers.conf /etc/nginx/security-headers.inc
COPY --from=build /workspace/ui/dist/ /usr/share/nginx/html/
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]

FROM ${DOCKER_HUB_MIRROR}/library/nginx:1.28.3-alpine@sha256:a8b39bd9cf0f83869a2162827a0caf6137ddf759d50a171451b335cecc87d236 AS production
COPY deploy/nginx-static.conf /etc/nginx/static.conf.template
COPY deploy/web-static-entrypoint.sh /usr/local/bin/web-static-entrypoint
RUN chmod 755 /usr/local/bin/web-static-entrypoint
COPY --from=build /workspace/ui/dist/ /usr/share/nginx/html/
EXPOSE 80
ENTRYPOINT ["/usr/local/bin/web-static-entrypoint"]
