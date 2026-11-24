package mc.unity.adapter.fabric;
import com.google.gson.*;
import java.util.*;
import net.fabricmc.loader.api.metadata.CustomValue;
class MetadataValue implements CustomValue {
    final JsonElement element;
    MetadataValue(JsonElement element) {
        this.element = element;
    }
    static CustomValue of(JsonElement e) {
        if (e == null)
            return null;
        if (e.isJsonArray())
            return new Array(e.getAsJsonArray());
        if (e.isJsonObject())
            return new ObjectValue(e.getAsJsonObject());
        return new MetadataValue(e);
    }
    public CvType getType() {
        if (element.isJsonNull())
            return CvType.NULL;
        if (element.isJsonObject())
            return CvType.OBJECT;
        if (element.isJsonArray())
            return CvType.ARRAY;
        if (element.getAsJsonPrimitive().isBoolean())
            return CvType.BOOLEAN;
        return element.getAsJsonPrimitive().isNumber() ? CvType.NUMBER : CvType.STRING;
    }
    private void expect(CvType type) {
        if (getType() != type)
            throw new ClassCastException("Expected " + type + ", found " + getType());
    }
    public CvArray getAsArray() {
        expect(CvType.ARRAY);
        return (CvArray)this;
    }
    public CvObject getAsObject() {
        expect(CvType.OBJECT);
        return (CvObject)this;
    }
    public String getAsString() {
        expect(CvType.STRING);
        return element.getAsString();
    }
    public Number getAsNumber() {
        expect(CvType.NUMBER);
        return element.getAsNumber();
    }
    public boolean getAsBoolean() {
        expect(CvType.BOOLEAN);
        return element.getAsBoolean();
    }
    private static final class Array extends MetadataValue implements CvArray {
        final List<CustomValue> values;
        Array(JsonArray a) {
            super(a);
            values = a.asList().stream().map(MetadataValue::of).toList();
        }
        public int size() {
            return values.size();
        }
        public CustomValue get(int i) {
            return values.get(i);
        }
        public Iterator<CustomValue> iterator() {
            return values.iterator();
        }
    }
    private static final class ObjectValue extends MetadataValue implements CvObject {
        final Map<String, CustomValue> values;
        ObjectValue(JsonObject o) {
            super(o);
            var map = new LinkedHashMap<String, CustomValue>();
            o.entrySet().forEach(e -> map.put(e.getKey(), of(e.getValue())));
            values = Collections.unmodifiableMap(map);
        }
        public int size() {
            return values.size();
        }
        public boolean containsKey(String k) {
            return values.containsKey(k);
        }
        public CustomValue get(String k) {
            return values.get(k);
        }
        public Iterator<Map.Entry<String, CustomValue>> iterator() {
            return values.entrySet().iterator();
        }
    }
}
