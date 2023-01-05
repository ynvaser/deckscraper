package systems.bdev.deckscraper.util;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import systems.bdev.deckscraper.model.Card;

import java.io.IOException;

public class CardDeserializer extends JsonDeserializer<Card> {
    @Override
    public Card deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
        String name = ctxt.readValue(p, String.class);
        return new Card(name);
    }
}
