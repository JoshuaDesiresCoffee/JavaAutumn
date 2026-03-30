package Autumn.handler;

import java.io.IOException;

@FunctionalInterface
public interface Handler {
    void handle(Exchange exchange) throws IOException;
}