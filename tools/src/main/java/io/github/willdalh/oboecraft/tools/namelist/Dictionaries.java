package io.github.willdalh.oboecraft.tools.namelist;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The dictionaries the tool reads names with, downloaded into the cache.
 *
 * <ul>
 *   <li>JMdict and KANJIDIC2 come from EDRDG, which updates them every day under the same address. They are downloaded
 *       once and used until they are deleted from the cache.
 *   <li>SudachiDict is a fixed version from PyPI, where it is published as a Python package that holds the dictionary
 *       file, and its checksum is checked.
 * </ul>
 *
 * @param jmdict             JMdict, with English meanings, which the tool doesn't use, but it is the smallest file
 * @param kanjidic           KANJIDIC2
 * @param sudachiDictionary  SudachiDict's {@code system.dic}
 */
record Dictionaries(Jmdict jmdict, Kanjidic kanjidic, Path sudachiDictionary) {
    private static final URI JMDICT = URI.create("https://www.edrdg.org/pub/Nihongo/JMdict_e.gz");
    private static final URI KANJIDIC = URI.create("https://www.edrdg.org/kanjidic/kanjidic2.xml.gz");

    /** The version of SudachiDict, which is the date it was made. */
    static final String SUDACHI_DICTIONARY_VERSION = "20261015";
    private static final URI SUDACHI_DICTIONARY = URI.create("https://files.pythonhosted.org/packages/3d/6a/"
            + "9dddc36e33ff7f86e394315a77b90d0f41bfd161e011d77a8397662d53e0/sudachidict_core-"
            + SUDACHI_DICTIONARY_VERSION + "-py3-none-any.whl");
    private static final Downloads.Checksum SUDACHI_DICTIONARY_CHECKSUM =
            Downloads.Checksum.sha256("6d7774b518e64c88a0578975d569590b3dae3b3c834f166486aa611879ee9d98");
    private static final String SUDACHI_DICTIONARY_FILE = "sudachidict_core/resources/system.dic";

    /**
     * Downloads the dictionaries, or takes them from the cache, and reads JMdict and KANJIDIC2.
     *
     * @throws IOException if a download fails or a file can't be read
     */
    static Dictionaries download(Downloads downloads) throws IOException {
        Path jmdictFile = downloads.get(JMDICT, "edrdg/JMdict_e.gz", null);
        Path kanjidicFile = downloads.get(KANJIDIC, "edrdg/kanjidic2.xml.gz", null);
        Path sudachiPackage = downloads.get(SUDACHI_DICTIONARY,
                "sudachi/sudachidict_core-" + SUDACHI_DICTIONARY_VERSION + ".whl", SUDACHI_DICTIONARY_CHECKSUM);

        Jmdict jmdict;
        try (InputStream in = new GZIPInputStream(Files.newInputStream(jmdictFile))) {
            jmdict = Jmdict.read(in);
        }
        Kanjidic kanjidic;
        try (InputStream in = new GZIPInputStream(Files.newInputStream(kanjidicFile))) {
            kanjidic = Kanjidic.read(in);
        }
        Path systemDictionary = downloads.cacheFolder()
                .resolve("sudachi/system-" + SUDACHI_DICTIONARY_VERSION + ".dic");
        if (!Files.exists(systemDictionary)) {
            extract(sudachiPackage, SUDACHI_DICTIONARY_FILE, systemDictionary);
        }
        return new Dictionaries(jmdict, kanjidic, systemDictionary);
    }

    /** Unpacks one file from a zip, here the dictionary from SudachiDict's Python package. */
    private static void extract(Path zip, String name, Path target) throws IOException {
        try (ZipFile file = new ZipFile(zip.toFile())) {
            ZipEntry entry = file.getEntry(name);
            if (entry == null) {
                throw new IOException(zip + " has no " + name);
            }
            Path partial = target.resolveSibling(target.getFileName() + ".part");
            try (InputStream in = file.getInputStream(entry)) {
                Files.copy(in, partial, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
    }

    /** The versions of the dictionaries, for the review list. */
    String describe() {
        return "JMdict made on " + jmdict.created() + ", KANJIDIC2 version " + kanjidic.version()
                + " and SudachiDict core " + SUDACHI_DICTIONARY_VERSION;
    }
}
