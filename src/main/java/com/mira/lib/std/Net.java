package com.mira.lib.std;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.mira.lib.Lib;
import com.mira.runtime.functions.NativeFunction;
import com.mira.runtime.interpreter.Environment;

public class Net implements Lib {

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public void loadLib(Environment environment) {

        environment.define("get", new NativeFunction(1, args -> {
            String url = String.valueOf(args.get(0));
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                return response.body();
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException("GET failed: " + e.getMessage());
            }
        }));

        environment.define("post", new NativeFunction(3, args -> {
            String url = String.valueOf(args.get(0));
            String body = String.valueOf(args.get(1));
            String contentType = String.valueOf(args.get(2));
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", contentType)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                return response.body();
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException("POST failed: " + e.getMessage());
            }
        }));

        environment.define("getStatus", new NativeFunction(1, args -> {
            String url = String.valueOf(args.get(0));
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                return (double) response.statusCode();
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException("getStatus failed: " + e.getMessage());
            }
        }));

        environment.define("getHeader", new NativeFunction(2, args -> {
            String url = String.valueOf(args.get(0));
            String header = String.valueOf(args.get(1));
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                return response.headers().firstValue(header).orElse("");
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException("getHeader failed: " + e.getMessage());
            }
        }));

        environment.define("download", new NativeFunction(2, args -> {
            String url = String.valueOf(args.get(0));
            String path = String.valueOf(args.get(1));
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();
                client.send(request, HttpResponse.BodyHandlers.ofFile(java.nio.file.Path.of(path)));
                return null;
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException("download failed: " + e.getMessage());
            }
        }));
    }
}
