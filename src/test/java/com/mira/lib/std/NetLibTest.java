package com.mira.lib.std;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.PushPromiseHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;
import com.mira.runtime.interpreter.Interpreter;

public class NetLibTest {

    Interpreter interpreter = new Interpreter();

    private HttpClient mockClient(String body, int status, Map<String, List<String>> headers) {
        return new HttpClient() {
            @Override
            public <T> HttpResponse<T> send(HttpRequest request, BodyHandler<T> handler)
                    throws IOException, InterruptedException {
                return (HttpResponse<T>) new HttpResponse<String>() {
                    @Override
                    public int statusCode() {
                        return status;
                    }

                    @Override
                    public String body() {
                        return body;
                    }

                    @Override
                    public HttpHeaders headers() {
                        return HttpHeaders.of(headers, (a, b) -> true);
                    }

                    @Override
                    public HttpRequest request() {
                        return request;
                    }

                    @Override
                    public Optional<HttpResponse<String>> previousResponse() {
                        return Optional.empty();
                    }

                    @Override
                    public Optional<SSLSession> sslSession() {
                        return Optional.empty();
                    }

                    @Override
                    public java.net.URI uri() {
                        return request.uri();
                    }

                    @Override
                    public HttpClient.Version version() {
                        return HttpClient.Version.HTTP_1_1;
                    }
                };
            }

            @Override
            public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r, BodyHandler<T> h) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r, BodyHandler<T> h, PushPromiseHandler<T> p) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<CookieHandler> cookieHandler() {
                return Optional.empty();
            }

            @Override
            public Optional<Duration> connectTimeout() {
                return Optional.empty();
            }

            @Override
            public Redirect followRedirects() {
                return Redirect.NORMAL;
            }

            @Override
            public Optional<ProxySelector> proxy() {
                return Optional.empty();
            }

            @Override
            public SSLContext sslContext() {
                try {
                    return SSLContext.getDefault();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public SSLParameters sslParameters() {
                return new SSLParameters();
            }

            @Override
            public Optional<Authenticator> authenticator() {
                return Optional.empty();
            }

            @Override
            public Version version() {
                return Version.HTTP_1_1;
            }

            @Override
            public Optional<Executor> executor() {
                return Optional.empty();
            }
        };
    }

    private HttpClient mockClient(String body, int status) {
        return mockClient(body, status, Map.of());
    }

    private HttpClient failingClient() {
        return new HttpClient() {
            @Override
            public <T> HttpResponse<T> send(HttpRequest request, BodyHandler<T> handler) throws IOException {
                throw new IOException("connection refused");
            }

            @Override
            public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r, BodyHandler<T> h) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r, BodyHandler<T> h, PushPromiseHandler<T> p) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<CookieHandler> cookieHandler() {
                return Optional.empty();
            }

            @Override
            public Optional<Duration> connectTimeout() {
                return Optional.empty();
            }

            @Override
            public Redirect followRedirects() {
                return Redirect.NORMAL;
            }

            @Override
            public Optional<ProxySelector> proxy() {
                return Optional.empty();
            }

            @Override
            public SSLContext sslContext() {
                try {
                    return SSLContext.getDefault();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public SSLParameters sslParameters() {
                return new SSLParameters();
            }

            @Override
            public Optional<Authenticator> authenticator() {
                return Optional.empty();
            }

            @Override
            public Version version() {
                return Version.HTTP_1_1;
            }

            @Override
            public Optional<Executor> executor() {
                return Optional.empty();
            }
        };
    }

    private Environment setup(HttpClient client) {
        Environment env = new Environment();
        new Net(client).loadLib(env);
        return env;
    }

    private Object call(Environment env, String name, Object... args) {
        NativeFunction fn = (NativeFunction) env.get(name);
        return fn.call(interpreter, List.of(args));
    }

    @Test
    void testGetReturnsBody() {
        Environment env = setup(mockClient("hello world", 200));
        assertEquals("hello world", call(env, "httpGet", "http://example.com"));
    }

    @Test
    void testGetReturnsEmptyBody() {
        Environment env = setup(mockClient("", 200));
        assertEquals("", call(env, "httpGet", "http://example.com"));
    }

    @Test
    void testGetReturnsJsonBody() {
        String json = "{\"key\":\"value\"}";
        Environment env = setup(mockClient(json, 200));
        assertEquals(json, call(env, "httpGet", "http://example.com/api"));
    }

    @Test
    void testGetThrowsOnIOException() {
        Environment env = setup(failingClient());
        assertThrows(RuntimeException.class, () -> call(env, "httpGet", "http://example.com"));
    }

    @Test
    void testGetReturnsString() {
        Environment env = setup(mockClient("response", 200));
        assertInstanceOf(String.class, call(env, "httpGet", "http://example.com"));
    }

    @Test
    void testPostReturnsBody() {
        Environment env = setup(mockClient("{\"ok\":true}", 201));
        assertEquals("{\"ok\":true}", call(env, "httpPost", "http://example.com", "{\"x\":1}", "application/json"));
    }

    @Test
    void testPostWithEmptyBody() {
        Environment env = setup(mockClient("", 204));
        assertEquals("", call(env, "httpPost", "http://example.com", "", "application/json"));
    }

    @Test
    void testPostWithFormContentType() {
        Environment env = setup(mockClient("accepted", 200));
        assertEquals("accepted", call(env, "httpPost", "http://example.com", "a=1&b=2", "application/x-www-form-urlencoded"));
    }

    @Test
    void testPostThrowsOnIOException() {
        Environment env = setup(failingClient());
        assertThrows(RuntimeException.class, () -> call(env, "httpPost", "http://example.com", "{}", "application/json"));
    }

    @Test
    void testGetStatus200() {
        Environment env = setup(mockClient("ok", 200));
        assertEquals(200.0, call(env, "httpStatus", "http://example.com"));
    }

    @Test
    void testGetStatus404() {
        Environment env = setup(mockClient("not found", 404));
        assertEquals(404.0, call(env, "httpStatus", "http://example.com"));
    }

    @Test
    void testGetStatus500() {
        Environment env = setup(mockClient("error", 500));
        assertEquals(500.0, call(env, "httpStatus", "http://example.com"));
    }

    @Test
    void testGetStatusReturnsDouble() {
        Environment env = setup(mockClient("ok", 200));
        assertInstanceOf(Double.class, call(env, "httpStatus", "http://example.com"));
    }

    @Test
    void testGetStatusThrowsOnIOException() {
        Environment env = setup(failingClient());
        assertThrows(RuntimeException.class, () -> call(env, "httpStatus", "http://example.com"));
    }

    @Test
    void testGetHeaderReturnsValue() {
        Environment env = setup(mockClient("ok", 200, Map.of("content-type", List.of("application/json"))));
        assertEquals("application/json", call(env, "httpHeader", "http://example.com", "content-type"));
    }

    @Test
    void testGetHeaderMissingReturnsEmpty() {
        Environment env = setup(mockClient("ok", 200, Map.of()));
        assertEquals("", call(env, "httpHeader", "http://example.com", "x-custom"));
    }

    @Test
    void testGetHeaderMultipleHeaders() {
        Environment env = setup(mockClient("ok", 200, Map.of(
                "content-type", List.of("text/html"),
                "x-powered-by", List.of("mira")
        )));
        assertEquals("text/html", call(env, "httpHeader", "http://example.com", "content-type"));
        assertEquals("mira", call(env, "httpHeader", "http://example.com", "x-powered-by"));
    }

    @Test
    void testGetHeaderThrowsOnIOException() {
        Environment env = setup(failingClient());
        assertThrows(RuntimeException.class, () -> call(env, "httpHeader", "http://example.com", "content-type"));
    }

    @Test
    void testDownloadReturnsNull() throws Exception {
        Path tmp = Files.createTempFile("mira-download-test", ".txt");
        tmp.toFile().deleteOnExit();
        Environment env = setup(mockClient("file content", 200));
        assertNull(call(env, "httpDownload", "http://example.com/file.txt", tmp.toString()));
        Files.deleteIfExists(tmp);
    }

    @Test
    void testDownloadThrowsOnIOException() {
        Environment env = setup(failingClient());
        assertThrows(RuntimeException.class, () -> call(env, "httpDownload", "http://example.com/file.txt", "/tmp/mira-test.txt"));
    }
}
