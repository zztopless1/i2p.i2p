package net.i2p.util;

import java.io.IOException;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.i2p.I2PAppContext;

/**
 * Map services to internal or external application ports
 * for this context. Not intended for the router's NTCP or SSU ports.
 *
 * @since 0.8.12
 */
public class PortMapper {
    private final ConcurrentHashMap<String, InetSocketAddress> _dir;

    public static final String SVC_CONSOLE = "console";
    public static final String SVC_HTTPS_CONSOLE = "https_console";
    public static final String SVC_HTTP_PROXY = "HTTP";
    public static final String SVC_HTTPS_PROXY = "HTTPS";
    public static final String SVC_EEPSITE = "eepsite";
    public static final String SVC_IRC = "irc";
    public static final String SVC_SOCKS = "socks";
    public static final String SVC_TAHOE = "tahoe-lafs";
    public static final String SVC_SMTP = "SMTP";
    public static final String SVC_POP = "POP3";
    public static final String SVC_SAM = "SAM";
    /** @since 0.9.24 */
    public static final String SVC_SAM_UDP = "SAM-UDP";
    /** @since 0.9.24 */
    public static final String SVC_SAM_SSL = "SAM-SSL";
    public static final String SVC_BOB = "BOB";
    /** not necessary, already in config? */
    public static final String SVC_I2CP = "I2CP";
    /** @since 0.9.23 */
    public static final String SVC_I2CP_SSL = "I2CP-SSL";
    /** @since 0.9.34 */
    public static final String SVC_HTTP_I2PCONTROL = "http_i2pcontrol";
    /** @since 0.9.34 */
    public static final String SVC_HTTPS_I2PCONTROL = "https_i2pcontrol";

    /**
     *  @param context unused for now
     */
    public PortMapper(I2PAppContext context) {
        _dir = new ConcurrentHashMap<String, InetSocketAddress>(8);
    }

    /**
     *  Add the service
     *  @param port &gt; 0
     *  @return success, false if already registered
     */
    public boolean register(String service, int port) {
        return register(service, "127.0.0.1", port);
    }

    /**
     *  Add the service
     *  @param port &gt; 0
     *  @return success, false if already registered
     *  @since 0.9.21
     */
    public boolean register(String service, String host, int port) {
        if (port <= 0 || port > 65535)
            return false;
        return _dir.putIfAbsent(service, InetSocketAddress.createUnresolved(host, port)) == null;
    }

    /**
     *  Remove the service
     */
    public void unregister(String service) {
        _dir.remove(service);
    }

    /**
     *  Get the registered port for a service
     *  @return -1 if not registered
     */
    public int getPort(String service) {
        int port = getPort(service, -1);
        return port;
    }

    /**
     *  Get the registered port for a service
     *  @param def default
     *  @return def if not registered
     */
    public int getPort(String service, int def) {
        InetSocketAddress ia = _dir.get(service);
        if (ia == null)
            return def;
        return ia.getPort();
    }

    /**
     *  Get the registered host for a service.
     *  Will return "127.0.0.1" if the service was registered without a host.
     *  @param def default
     *  @return def if not registered
     *  @since 0.9.21
     */
    public String getHost(String service, String def) {
        InetSocketAddress ia = _dir.get(service);
        if (ia == null)
            return def;
        return ia.getHostName();
    }

    /**
     *  Get the actual host for a service.
     *  Will return "127.0.0.1" if the service was registered without a host.
     *  If the service was registered with the host "0.0.0.0", "::", or "0:0:0:0:0:0:0:0",
     *  it will return a public IP if we have one,
     *  else a local IP if we have one, else def.
     *  If it was not registered with a wildcard address, it will return the registered host.
     *
     *  @param def default
     *  @return def if not registered
     *  @since 0.9.24
     */
    public String getActualHost(String service, String def) {
        InetSocketAddress ia = _dir.get(service);
        if (ia == null)
            return def;
        return convertWildcard(ia.getHostName(), def);
    }

    /*
     *  See above
     *  @param def default
     *  @return def if no ips
     *  @since 0.9.24
     */
    private static String convertWildcard(String ip, String def) {
        String rv = ip;
        if (rv.equals("0.0.0.0")) {
            // public
            rv = Addresses.getAnyAddress();
            if (rv == null) {
                rv = def;
                // local
                Set<String> addrs = Addresses.getAddresses(true, false);
                for (String addr : addrs) {
                    if (!addr.startsWith("127.") && !addr.equals("0.0.0.0")) {
                        rv = addr;
                        break;
                    }
                }
            }
        } else if (rv.equals("::") || rv.equals("0:0:0:0:0:0:0:0")) {
            rv = def;
            // public
            Set<String> addrs = Addresses.getAddresses(false, true);
            for (String addr : addrs) {
                if (!addr.contains(".")) {
                    return rv;
                }
            }
            // local
            addrs = Addresses.getAddresses(true, true);
            for (String addr : addrs) {
                if (!addr.contains(".") && !addr.equals("::") && !addr.equals("0:0:0:0:0:0:0:0")) {
                    rv = addr;
                    break;
                }
            }
        }
        return rv;
    }

    /*
     *  @return http URL unless console is https only. Default http://127.0.0.1:7657/
     *  @since 0.9.33 consolidated from i2ptunnel and desktopgui
     */
    public String getConsoleURL() {
        String unset = "*unset*";
        String httpHost = getActualHost(SVC_CONSOLE, unset);
        String httpsHost = getActualHost(SVC_HTTPS_CONSOLE, unset);
        int httpPort = getPort(SVC_CONSOLE, 7657);
        int httpsPort = getPort(SVC_HTTPS_CONSOLE, -1);
        boolean httpsOnly = httpsPort > 0 && httpHost.equals(unset) && !httpsHost.equals(unset);
        if (httpsOnly)
            return "https://" + httpsHost + ':' + httpsPort + '/';
        if (httpHost.equals(unset))
            httpHost = "127.0.0.1";
        return "http://" + httpHost + ':' + httpPort + '/';
    }

    /**
     *  For debugging only
     *  @since 0.9.20
     */
    public void renderStatusHTML(Writer out) throws IOException {
        List<String> services = new ArrayList<String>(_dir.keySet());
        out.write("<h2 id=\"debug_portmapper\">Port Mapper</h2><table id=\"portmapper\"><tr><th>Service<th>Host<th>Port\n");
        Collections.sort(services);
        for (String s : services) {
            InetSocketAddress ia = _dir.get(s);
            if (ia == null)
                continue;
            out.write("<tr><td>" + s + "<td>" + convertWildcard(ia.getHostName(), "127.0.0.1") + "<td>" + ia.getPort() + '\n');
        }
        out.write("</table>\n");
    }
}
