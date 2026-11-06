package io.github.willdalh.oboecraft.tools.namelist;

import javax.xml.stream.XMLInputFactory;

/** How the dictionary files from EDRDG are read. They come from outside, so they are only trusted to be XML. */
final class DictionaryXml {
    private DictionaryXml() {
    }

    /**
     * A StAX factory that never loads anything from outside the file. The files declare their entities, like
     * {@code &n;}, in their own DTD. They are left as entity references instead of being replaced, since the code only
     * needs the text of elements without them, and replacing them would hit the JDK's limit on entity expansions in a
     * file as large as JMdict.
     */
    static XMLInputFactory factory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, true);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        return factory;
    }
}
