package com.lumen.vertx;

/**
 * Useful place to keep constants used by the application
 *
 * @author sweller
 */
public class MiddlewareConstants 
{

    public static final String CONFIG_PROP__APP_BIND_ADDRESS = "APP_BIND_ADDRESS";
    public static final String CONFIG_PROP__APP_BIND_PORT = "APP_BIND_PORT";

    public static final String CONFIG_PROP__DATABASE_URI = "DATABASE_URI";
    public static final String CONFIG_PROP__DATABASE_NAME = "DATABASE_NAME";
    public static final String CONFIG_PROP__PERSISTENCE_UNIT_NAME = "PERSISTENCE_UNIT_NAME";

    public static final int TCP_PORT_MIN = 1;
    public static final int TCP_PORT_MAX = 65535;

    private MiddlewareConstants()
    {
        // prevent instantiation
    }

}
