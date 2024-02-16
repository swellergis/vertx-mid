### `vertx-mid:dev container (8080)`

docker build -t vertx-mid:dev .
docker run -itd --rm -p 8080:8080 vertx-mid:dev

### `vertx-mid:dev and bitnami/postgresql:15 containers (8080 and 5432)`

cd ~/checkout/playground/bitnami-postgresql
docker compose up -d

curl http://localhost:8080/customers

### `bitnami/postgresql:15 container`

docker run -itd --restart unless-stopped -e POSTGRES_USER=toor -e POSTGRES_PASSWORD=oicu812 -v /data:/var/lib/postgresql/data --name postgresql bitnami/postgresql

psql -h 172.16.136.129 -p 5432 -d mytest -U toor
psql -h localhost -p 5432 -d mytest -U toor
oicu812

### `nginx proxy to running vertx web service (80 and 443)`

/etc/nginx/conf.d/react-admin.conf
nginx -t
systemctl start nginx

curl -k https://localhost/customers
curl -k https://vertx.local/customers
https://vertx.local/customers

### `nginx-proxy:dev container`

cd ~/checkout/playground/nginx-proxy/
docker build -t nginx-proxy:dev .
docker compose up -d

