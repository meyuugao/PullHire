# frontend SPA image (docs/deployment.md §2.2): node:22-alpine -> nginx:1.27-alpine.
# No package-lock.json exists yet, so npm install is used instead of npm ci.
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json ./
RUN npm install
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /app/out /usr/share/nginx/html
EXPOSE 80
