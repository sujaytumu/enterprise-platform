# Used only by docker-compose for local dev. Render deploys the frontend as
# a static site (see render.yaml) and does NOT use this file.
FROM node:20-alpine
WORKDIR /app
COPY package.json .
RUN npm install
COPY . .
EXPOSE 5173
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0"]
