package systems.bdev.deckscraper.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import systems.bdev.deckscraper.model.Card;

import java.io.IOException;

public class CardSerializer extends JsonSerializer<Card> {
    @Override
    public void serialize(Card value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeObject(value.name());
    }
}
