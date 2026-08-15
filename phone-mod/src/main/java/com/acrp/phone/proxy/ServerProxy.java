package com.acrp.phone.proxy;

/**
 * Dedicated-server proxy. Everything it needs is already in {@link CommonProxy};
 * this type exists so the {@code @SidedProxy} binding has a server target that can
 * never pull client-only classes onto the server classpath.
 */
public class ServerProxy extends CommonProxy {
}
