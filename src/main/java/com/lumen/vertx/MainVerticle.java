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

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;

import java.util.List;
import java.util.Map;

public class MainVerticle extends AbstractVerticle {

  private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);
  private Mutiny.SessionFactory emf;

  private String bindAddress;
  private int bindPort;

  private String dbUri;
  private String dbName;
  private String puName;

  @Override
  public Uni<Void> asyncStart() {
    try {
      readConfigProps();
    } catch (Exception e) {
      // TODO
      // logger.log(Level.WARNING, "Exception thrown in config props", e);
      logger.error("🔥 Exception thrown in config props", e);
      return Uni.createFrom().nullItem();
    }

    Uni<Void> startHibernate = Uni.createFrom().deferred(() -> {
      // var pgName = "mytest_azure";
      // var pgUri = "192.168.57.35:5432";
      // var persistenceUnitName = "atarcDS";

      // var props = Map.of("jakarta.persistence.jdbc.url", "jdbc:postgresql://192.168.57.35:" + 5432 + pgName);
      // var props = Map.of("jakarta.persistence.jdbc.url", "jdbc:postgresql://localhost:" + pgPort + "/postgres");
      var props = Map.of("jakarta.persistence.jdbc.url", "jdbc:postgresql://" + dbUri + "/" + dbName);
      emf = Persistence
        .createEntityManagerFactory(puName, props)
        .unwrap(Mutiny.SessionFactory.class);

      return Uni.createFrom().voidItem();
    });

    startHibernate = vertx.executeBlocking(startHibernate)  // <2>
      .onItem().invoke(() -> logger.info("✅ Hibernate Reactive is ready"));

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

    Uni<HttpServer> startHttpServer = vertx.createHttpServer(options)
      .requestHandler(router)
      .listen(bindPort, bindAddress)
      .onItem().invoke(() -> logger.info("✅ HTTP server listening on port " + bindPort));

    return Uni.combine().all().unis(startHibernate, startHttpServer).discardItems();  // <1>
  }

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
    String loginId = ctx.pathParam("loginid");
    String queryString = "SELECT DISTINCT u FROM Users u WHERE u.loginId = :loginId";

    Uni<List<Users>> uniUsers = emf.withSession(session -> session
      .createQuery(queryString, Users.class)
      .setParameter("loginId", loginId)
      .getResultList());

    return uniUsers.onItem()
      .transform(response -> response.iterator().next().getApiKey())
      .onFailure().recoverWithItem("null");
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

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();

    JsonObject config = new JsonObject();
    // config.put("pgName", "mytest_azure");

    DeploymentOptions options = new DeploymentOptions().setConfig(config);

    vertx.deployVerticle(MainVerticle::new, options).subscribe().with(
      ok -> {
        logger.info("✅ Deployment success");
      },
      err -> logger.error("🔥 Deployment failure", err));
  }

  private void readConfigProps() throws IllegalArgumentException, InterruptedException
  {

    // look at environment variables and system properties
    // to get our required configuration parameters.

    String tmp = getConfigProp(MiddlewareConstants.CONFIG_PROP__APP_BIND_ADDRESS);

    // verify that tmp looks like a real IP.
    IPAddressString bindAddressString = new IPAddressString(tmp);
    IPAddress bindAddressTmp = bindAddressString.getAddress();

    if (bindAddressTmp == null)
    {
        // the address supplied was not a valid IP address
        throw new IllegalArgumentException(
                String.format("The value supplied for %s property was not a valid ip address",
                        MiddlewareConstants.CONFIG_PROP__APP_BIND_ADDRESS));
    }
    bindAddress = tmp;

    tmp = getConfigProp(MiddlewareConstants.CONFIG_PROP__APP_BIND_PORT);
    // check that tmp looks like a valid bind port
    Integer tmpPort;
    try
    {
        tmpPort = Integer.parseInt(tmp);

        if (tmpPort < MiddlewareConstants.TCP_PORT_MIN || tmpPort > MiddlewareConstants.TCP_PORT_MAX)
        {
            // not a valid port
            throw new IllegalArgumentException(String.format("The value for %s property must be a value between %d and %d",
                    MiddlewareConstants.CONFIG_PROP__APP_BIND_PORT,
                    MiddlewareConstants.TCP_PORT_MIN,
                    MiddlewareConstants.TCP_PORT_MAX));
        }
        bindPort = tmpPort;
    }
    catch (NumberFormatException nfe)
    {
        // not a valid number
        throw new IllegalArgumentException(String.format("The value for %s property couldn't be converted to a number",
                MiddlewareConstants.CONFIG_PROP__APP_BIND_PORT), nfe);
    }

    tmp = getConfigProp(MiddlewareConstants.CONFIG_PROP__DATABASE_URI);
    dbUri = tmp;

    tmp = getConfigProp(MiddlewareConstants.CONFIG_PROP__DATABASE_NAME);
    dbName = tmp;

    tmp = getConfigProp(MiddlewareConstants.CONFIG_PROP__PERSISTENCE_UNIT_NAME);
    puName = tmp;
}

    /**
     * Attempt to get named property first from environment variables and if not
     * found then system props. If the property is not found in either location
     * throw an IllegalArgumentException.
     *
     * @param propName Name of environment variable or system prop we want the
     * value for.
     * @return The value of the system prop
     *
     * @throws IllegalArgumentException If the value isn't present.
     */
    private String getConfigProp(String propName) throws IllegalArgumentException
    {
        String ret;

        // first try as environment var
        ret = System.getenv(propName);

        if (ret == null)
        {
            // try as a system property
            ret = System.getProperty(propName);
        }

        if (ret != null)
        {
            // ret could still be blank, so let's trim
            ret = ret.trim();
        }

        if (ret == null || ret.isBlank())
        {
            // we have a null or blank value
            throw new IllegalArgumentException(String.format("No value supplied for config prop %s", propName));
        }

        // we have a non-null value
        return ret;
    }

}
