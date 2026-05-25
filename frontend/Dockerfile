FROM node:22-alpine AS build

WORKDIR /workspace
ARG VITE_SENTRY_DSN=
ARG VITE_APP_RELEASE=
ARG VITE_GIT_SHA=
ENV VITE_SENTRY_DSN=$VITE_SENTRY_DSN
ENV VITE_APP_RELEASE=$VITE_APP_RELEASE
ENV VITE_GIT_SHA=$VITE_GIT_SHA
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html

EXPOSE 8080
