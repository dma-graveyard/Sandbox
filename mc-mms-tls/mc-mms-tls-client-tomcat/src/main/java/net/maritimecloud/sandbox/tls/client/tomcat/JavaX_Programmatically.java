package net.maritimecloud.sandbox.tls.client.tomcat;

import net.maritimecloud.sandbox.tls.client.AbstractClient;
import net.maritimecloud.sandbox.tls.client.tomcat.util.JavaXEndpoint;
import org.apache.tomcat.websocket.WsWebSocketContainer;

import javax.net.ssl.SSLContext;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.ContainerProvider;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

public class JavaX_Programmatically extends AbstractClient {

    public static void main(String[] args) throws Exception {
        // Setup SSL context
        ClientEndpointConfig config = ClientEndpointConfig.Builder.create().build();

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, loadTrustStore(), null);
        config.getUserProperties().put("org.apache.tomcat.websocket.SSL_CONTEXT", context);


        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        try {
            JavaXEndpoint es = new JavaXEndpoint();
            try (Session session = container.connectToServer(es, config, WSS)) {
                es.assertDone();
            }
        } finally {
            if (container instanceof WsWebSocketContainer) { // proper shutdown
                ((WsWebSocketContainer) container).destroy();
            }
        }
    }
}
