package io.github.willdalh.oboecraft.tools.readingclips;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A running VOICEVOX engine, reached over its HTTP API. The engine is started by hand, from the VOICEVOX app or its
 * Docker image, see {@code docs/architecture/reading-clip-tool.md}.
 */
final class Voicevox implements SpokenText.Pronouncer {
    private static final Duration TIMEOUT = Duration.ofMinutes(1);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final URI engine;
    private final int styleId;

    private Voicevox(URI engine, int styleId) {
        this.engine = engine;
        this.styleId = styleId;
    }

    /**
     * Connects to the engine at {@code engine} and finds the voice's style.
     *
     * @throws IllegalStateException if the engine doesn't have the voice
     */
    static Voicevox connect(URI engine, ReadingClipList.Voice voice) throws IOException {
        Voicevox voicevox = new Voicevox(engine, -1);
        for (JsonElement speaker : voicevox.send(voicevox.get("/speakers")).getAsJsonArray()) {
            if (!speaker.getAsJsonObject().get("speaker_uuid").getAsString().equals(voice.speakerUuid())) continue;
            for (JsonElement style : speaker.getAsJsonObject().getAsJsonArray("styles")) {
                if (style.getAsJsonObject().get("name").getAsString().equals(voice.style())) {
                    return new Voicevox(engine, style.getAsJsonObject().get("id").getAsInt());
                }
            }
        }
        throw new IllegalStateException("The VOICEVOX engine at " + engine + " doesn't have the voice "
                + voice.speaker() + " (" + voice.speakerUuid() + ") in the style " + voice.style());
    }

    /** The engine's version, like {@code 0.26.1}. */
    String version() throws IOException {
        return send(get("/version")).getAsString();
    }

    @Override
    public List<Mora> moras(String text) {
        try {
            List<Mora> moras = new ArrayList<>();
            JsonElement phrases = send(post("/accent_phrases?speaker=" + styleId + "&text=" + encode(text),
                    HttpRequest.BodyPublishers.noBody()));
            for (JsonElement phrase : phrases.getAsJsonArray()) {
                for (JsonElement element : phrase.getAsJsonObject().getAsJsonArray("moras")) {
                    var mora = element.getAsJsonObject();
                    JsonElement consonant = mora.get("consonant");
                    moras.add(new Mora(
                            mora.get("text").getAsString(),
                            consonant == null || consonant.isJsonNull()
                                    ? Optional.empty()
                                    : Optional.of(consonant.getAsString()),
                            mora.get("vowel").getAsString()));
                }
            }
            return moras;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Says {@code text} in the voice, as a WAV file. */
    byte[] speak(String text) throws IOException {
        String query = send(post("/audio_query?speaker=" + styleId + "&text=" + encode(text),
                HttpRequest.BodyPublishers.noBody())).toString();
        HttpRequest synthesis = post("/synthesis?speaker=" + styleId, HttpRequest.BodyPublishers.ofString(query))
                .header("Content-Type", "application/json")
                .build();
        return check(synthesis, sendRequest(synthesis, HttpResponse.BodyHandlers.ofByteArray())).body();
    }

    private HttpRequest.Builder get(String path) {
        return HttpRequest.newBuilder(engine.resolve(path)).timeout(TIMEOUT).GET();
    }

    private HttpRequest.Builder post(String path, HttpRequest.BodyPublisher body) {
        return HttpRequest.newBuilder(engine.resolve(path)).timeout(TIMEOUT).POST(body);
    }

    private JsonElement send(HttpRequest.Builder builder) throws IOException {
        HttpRequest request = builder.build();
        return JsonParser.parseString(
                check(request, sendRequest(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)))
                        .body());
    }

    private <T> HttpResponse<T> sendRequest(HttpRequest request, HttpResponse.BodyHandler<T> handler)
            throws IOException {
        try {
            return http.send(request, handler);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for VOICEVOX", e);
        }
    }

    private static <T> HttpResponse<T> check(HttpRequest request, HttpResponse<T> response) throws IOException {
        if (response.statusCode() != 200) {
            throw new IOException("VOICEVOX answered " + response.statusCode() + " to " + request.uri());
        }
        return response;
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }
}
