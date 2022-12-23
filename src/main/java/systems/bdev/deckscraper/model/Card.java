package systems.bdev.deckscraper.model;

import java.util.Objects;

public record Card(String name) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Card card = (Card) o;
        if (name != null) {
            return name.equalsIgnoreCase(card.name);
        } else {
            return card.name == null;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
