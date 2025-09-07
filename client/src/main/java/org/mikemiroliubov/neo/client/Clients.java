package org.mikemiroliubov.neo.client;

import org.mikemiroliubov.neo.client.impl.NeoClientImpl;

import java.io.IOException;

public final class Clients {
    public static NeoClient client() throws IOException {
        return new NeoClientImpl();
    }
}
