FROM node:20-alpine

RUN apk add --no-cache openssl

WORKDIR /app

COPY backend/package*.json ./
COPY backend/prisma ./prisma/

RUN npm ci

RUN npx prisma generate

COPY backend/ .

RUN npm run build

EXPOSE 4000

CMD ["sh", "-c", "npx prisma migrate deploy && node dist/index.js"]
