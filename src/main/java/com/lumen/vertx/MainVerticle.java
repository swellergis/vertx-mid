package com.lumen.vertx;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.http.HttpServer;
import io.vertx.mutiny.ext.web.Router;
import io.vertx.mutiny.ext.web.RoutingContext;
import io.vertx.mutiny.ext.web.handler.BodyHandler;
import io.vertx.mutiny.ext.web.handler.CorsHandler;
import org.hibernate.reactive.mutiny.Mutiny;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lumen.vertx.model.Customer;

import javax.persistence.Persistence;
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
      // var pgPort = config().getInteger("pgPort", 5432);
      var pgName = config().getString("pg.name");
      var pgUri = config().getString("pg.uri");
      var persistenceUnitName = config().getString("pu.name");

      // var pgName = "mytest_azure";
      // var pgUri = "192.168.57.35:5432";
      // var persistenceUnitName = "atarcDS";

      // var props = Map.of("javax.persistence.jdbc.url", "jdbc:postgresql://192.168.57.35:" + 5432 + pgName);  // <1>
      // var props = Map.of("javax.persistence.jdbc.url", "jdbc:postgresql://localhost:" + pgPort + "/postgres");
      var props = Map.of("javax.persistence.jdbc.url", "jdbc:postgresql://" + pgUri + "/" + pgName);
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

    router.get("/customers").respond(this::listCustomers);
    router.get("/customers/:id").respond(this::getCustomer);
    router.post("/customers").respond(this::createCustomer);
    // end::routing[]

    // tag::async-start[]
    Uni<HttpServer> startHttpServer = vertx.createHttpServer()
      .requestHandler(router)
      .listen(8080)
      .onItem().invoke(() -> logger.info("✅ HTTP server listening on port 8080"));

    return Uni.combine().all().unis(startHibernate, startHttpServer).discardItems();  // <1>
    // end::async-start[]
  }

  // tag::crud-methods[]
  private Uni<List<Customer>> listCustomers(RoutingContext ctx) {
    return emf.withSession(session -> session
      .createQuery("from Customer", Customer.class)
      .getResultList());
  }

  private Uni<Customer> getCustomer(RoutingContext ctx) {
    long id = Long.parseLong(ctx.pathParam("id"));
    return emf.withSession(session -> session
      .find(Customer.class, id))
      .onItem().ifNull().continueWith(Customer::new);
  }

  private Uni<Customer> createCustomer(RoutingContext ctx) {
    Customer customer = ctx.body().asPojo(Customer.class);
    return emf.withSession(session -> session.
      persist(customer)
      .call(session::flush)
      .replaceWith(customer));
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
