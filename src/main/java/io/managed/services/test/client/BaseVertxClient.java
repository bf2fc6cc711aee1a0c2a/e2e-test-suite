package io.managed.services.test.client;

import io.managed.services.test.client.exception.ResponseException;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import java.net.URI;

public abstract class BaseVertxClient {

    protected final Vertx vertx;
    protected final WebClient client;

    public BaseVertxClient(Vertx vertx, String uri) {
        this(vertx, URI.create(uri));
    }

    public BaseVertxClient(Vertx vertx, URI uri) {
        this(vertx, optionsForURI(uri));
    }

    public BaseVertxClient(Vertx vertx, WebClientOptions options) {
        this(vertx, WebClient.create(vertx, options));
    }

    public BaseVertxClient(Vertx vertx, WebClient client) {
        this.vertx = vertx;
        this.client = client;
    }

    protected static WebClientOptions optionsForURI(URI uri) {
        return new WebClientOptions()
            .setDefaultHost(uri.getHost())
            .setDefaultPort(getPort(uri))
            .setSsl(isSsl(uri))
            .setConnectTimeout((int) 120_000L)
            .addEnabledSecureTransportProtocol("TLSv1.3");
    }

    static Integer getPort(URI u) {
        if (u.getPort() == -1) {
            switch (u.getScheme()) {
                case "https":
                    return 443;
                case "http":
                    return 80;
            }
        }
        return u.getPort();
    }

    static Boolean isSsl(URI u) {
        switch (u.getScheme()) {
            case "https":
                return true;
            case "http":
                return false;
        }
        return false;
    }

    public static <T> Future<HttpResponse<T>> assertResponse(HttpResponse<T> response, Integer statusCode) {
        if (response.statusCode() != statusCode) {
            String message = String.format("Expected status code %d but got %s", statusCode, response.statusCode());
            return Future.failedFuture(new ResponseException(message, response));
        }
        return Future.succeededFuture(response);
    }

    public static String getRedirectLocation(HttpResponse<Buffer> response) throws ResponseException {
        var l = response.getHeader("Location");
        if (l == null) {
            throw new ResponseException("Location header not found", response);
        }
        return l;
    }

    public static Future<String> getRedirectLocationAsFeature(HttpResponse<Buffer> response) {
        try {
            return Future.succeededFuture(getRedirectLocation(response));
        } catch (ResponseException e) {
            return Future.failedFuture(e);
        }
    }
}
