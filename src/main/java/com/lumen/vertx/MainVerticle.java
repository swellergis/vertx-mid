package com.lumen.vertx;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.http.HttpServer;
import io.vertx.mutiny.ext.web.Router;
import io.vertx.mutiny.ext.web.RoutingContext;
import io.vertx.mutiny.ext.web.handler.BodyHandler;
import io.vertx.mutiny.ext.web.handler.CorsHandler;
import jakarta.persistence.Persistence;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

import org.hibernate.reactive.mutiny.Mutiny;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lumen.vertx.model.Request;
import com.lumen.vertx.model.SsnLookup;
import com.lumen.vertx.model.Users;

import java.util.List;
import java.util.Map;

// tag::preamble[]
public class MainVerticle extends AbstractVerticle {

  private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);
  private Mutiny.SessionFactory emf;  // <1>

  @Override
  public Uni<Void> asyncStart() {
// end::preamble[]

    // tag::hr-start[]
    Uni<Void> startHibernate = Uni.createFrom().deferred(() -> {
      var pgName = config().getString("pg.name");
      var pgUri = config().getString("pg.uri");
      var persistenceUnitName = config().getString("pu.name");

      // var pgName = "mytest_azure";
      // var pgUri = "192.168.57.35:5432";
      // var persistenceUnitName = "atarcDS";

      // var props = Map.of("jakarta.persistence.jdbc.url", "jdbc:postgresql://192.168.57.35:" + 5432 + pgName);
      // var props = Map.of("jakarta.persistence.jdbc.url", "jdbc:postgresql://localhost:" + pgPort + "/postgres");
      var props = Map.of("jakarta.persistence.jdbc.url", "jdbc:postgresql://" + pgUri + "/" + pgName);
      emf = Persistence
        .createEntityManagerFactory(persistenceUnitName, props)
        .unwrap(Mutiny.SessionFactory.class);

      return Uni.createFrom().voidItem();
    });

    startHibernate = vertx.executeBlocking(startHibernate)  // <2>
      .onItem().invoke(() -> logger.info("✅ Hibernate Reactive is ready"));
    // end::hr-start[]

    // tag::routing[]
    Router router = Router.router(vertx);
    router.route().handler(CorsHandler.create()
    // add an allowed origin from config file
    .addOrigin("*")
    .allowedMethod(HttpMethod.GET)
    // .allowedMethod(HttpMethod.POST)
    // .allowedMethod(HttpMethod.OPTIONS)
    // .allowedHeader("Access-Control-Allow-Headers")
    // .allowedHeader("Access-Control-Allow-Method")
    // .allowedHeader("Access-Control-Allow-Origin")
    // .allowCredentials(true)
    // .allowedHeader("Authorization")
    // .allowedHeader("Access-Control-Allow-Credentials")
    .allowedHeader("Content-Type"));

    BodyHandler bodyHandler = BodyHandler.create();
    router.post().handler(bodyHandler::handle);

    router.get("/api/apikeys/:loginid").respond(this::getUserApiKey);

    router.get("/customers").respond(this::listCustomers);
    router.get("/customers/:id").respond(this::getCustomer);
    router.post("/customers").respond(this::createCustomer);

    router.get("/requests").respond(this::listRequests);
    router.get("/requests/:id").respond(this::getRequest);
    router.get("/userrequests/:emp_id").respond(this::listUserRequests);
    router.post("/requests").respond(this::createRequest);
    // end::routing[]

    JksOptions keyOptions = new JksOptions();
    // keyOptions.setPath("/home/toor/certs/selfsigned2.jks");
    // keyOptions.setPath("/home/toor/tmp/certs/.cert/server.jks");

    // atarczts-websvr
    // keyOptions.setPath("atarc-websvr.jks");
    // keyOptions.setPassword("changeit");

    // dev and atarc-apisrv
    // keyOptions.setPath("keystore.p12");
    // keyOptions.setPassword("changeme");

    HttpServerOptions options = new HttpServerOptions()
      .setIdleTimeout(0)
      .setUseAlpn(true);

      // .setSsl(true)
      // .setKeyStoreOptions(keyOptions);

      // .setPemTrustOptions(new PemTrustOptions()
      // .addCertPath("/etc/letsencrypt/live/atarcapi.eastus.cloudapp.azure.com/chain.pem"))
      // .setPemKeyCertOptions(new PemKeyCertOptions()
      //   .addKeyPath("/etc/letsencrypt/live/atarcapi.eastus.cloudapp.azure.com/privkey.pem")
      //   .addCertPath("/etc/letsencrypt/live/atarcapi.eastus.cloudapp.azure.com/fullchain.pem"));

    // int port = 8080;
    int port = config().getInteger("app.port", 8080);

    // tag::async-start[]
    Uni<HttpServer> startHttpServer = vertx.createHttpServer(options)
      .requestHandler(router)
      .listen(port)
      .onItem().invoke(() -> logger.info("✅ HTTP server listening on port " + port));

    return Uni.combine().all().unis(startHibernate, startHttpServer).discardItems();  // <1>
    // end::async-start[]
  }

  // tag::crud-methods[]
  private Uni<List<SsnLookup>> listCustomers(RoutingContext ctx) {
    return emf.withSession(session -> session
      .createQuery("from SsnLookup", SsnLookup.class)
      .getResultList());
  }

  private Uni<SsnLookup> getCustomer(RoutingContext ctx) {
    long id = Long.parseLong(ctx.pathParam("customer_id"));
    return emf.withSession(session -> session
      .find(SsnLookup.class, id))
      .onItem().ifNull().continueWith(SsnLookup::new);
  }

  private Uni<String> getUserApiKey(RoutingContext ctx) {
    String loginid = ctx.pathParam("loginid");
    String queryString = String.format("from Users where loginid = '%s'", loginid);

    Uni<List<Users>> uniUsers = emf.withSession(session -> session
      .createQuery(queryString, Users.class)
      .getResultList());

    return uniUsers.onItem()
      .transform(response -> response.iterator().next().getApiKey())
      .onFailure().recoverWithItem("NotFoundException");
  }

  private Uni<SsnLookup> createCustomer(RoutingContext ctx) {
    SsnLookup customer = ctx.body().asPojo(SsnLookup.class);
    return emf.withSession(session -> session.
      persist(customer)
      .call(session::flush)
      .replaceWith(customer));
  }

  private Uni<Request> createRequest(RoutingContext ctx) {
    Request request = ctx.body().asPojo(Request.class);
    return emf.withSession(session -> session.
      persist(request)
      .call(session::flush)
      .replaceWith(request));
  }

  private Uni<List<Request>> listRequests(RoutingContext ctx) {
    return emf.withSession(session -> session
      .createQuery("from Request", Request.class)
      .getResultList());
  }

  private Uni<List<Request>> listUserRequests(RoutingContext ctx) {
    String empId = ctx.pathParam("emp_id");
    return emf.withSession(session -> session
      .createQuery("FROM Request WHERE emp_id='" + empId + "'", Request.class)
      .getResultList());
  }

  private Uni<Request> getRequest(RoutingContext ctx) {
    long id = Long.parseLong(ctx.pathParam("id"));
    return emf.withSession(session -> session
      .find(Request.class, id))
      .onItem().ifNull().continueWith(Request::new);
  }
  // end::crud-methods[]

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();

    JsonObject config = new JsonObject();
    config.put("pgName", "mytest_azure");
    config.put("pgUri", "192.168.57.35:5432");
    config.put("persistenceUnitName", "atarcDS");

    DeploymentOptions options = new DeploymentOptions().setConfig(config); // <1>

    vertx.deployVerticle(MainVerticle::new, options).subscribe().with(  // <2>
      ok -> {
        logger.info("✅ Deployment success");
      },
      err -> logger.error("🔥 Deployment failure", err));
    // end::vertx-start[]
  }

}
