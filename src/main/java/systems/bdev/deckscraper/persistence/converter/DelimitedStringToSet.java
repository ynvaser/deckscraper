package systems.bdev.deckscraper.persistence.converter;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.util.Set;

@Converter
public class DelimitedStringToSet implements AttributeConverter<Set<String>, String> {
    @Override
    public String convertToDatabaseColumn(Set<String> attribute) {
        return String.join(";", attribute);
    }

    @Override
    public Set<String> convertToEntityAttribute(String dbData) {
        return Set.of(dbData.split(";"));
    }
}
