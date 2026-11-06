package io.github.willdalh.oboecraft.tools.namelist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DictionaryXmlTest {
    /**
     * A dictionary file whose DOCTYPE names an external DTD, and one of whose entities is an external parameter entity.
     * Neither may be fetched.
     */
    private static String fileReferring(String address) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE JMdict SYSTEM "%1$s/jmdict.dtd" [
                <!ENTITY %% remote SYSTEM "%1$s/remote.dtd">
                %%remote;
                ]>
                <JMdict><entry><k_ele><keb>原木</keb></k_ele><r_ele><reb>げんぼく</reb></r_ele></entry></JMdict>
                """.formatted(address);
    }

    @Test
    void nothingOutsideTheFileIsFetched() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            byte[] body = "<!ENTITY n \"noun\">".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String address = "http://127.0.0.1:" + server.getAddress().getPort();
            byte[] xml = fileReferring(address).getBytes(StandardCharsets.UTF_8);

            assertThrows(IOException.class, () -> Jmdict.read(new ByteArrayInputStream(xml)));
            assertThrows(IOException.class, () -> Kanjidic.read(new ByteArrayInputStream(xml)));
            assertEquals(0, requests.get(), "requests to the address the file names");
        } finally {
            server.stop(0);
        }
    }
}
