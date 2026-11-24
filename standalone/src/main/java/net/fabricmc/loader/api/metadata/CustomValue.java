package net.fabricmc.loader.api.metadata;
import java.util.Map;
public interface CustomValue {
    enum CvType { OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, NULL }
    interface CvArray extends CustomValue, Iterable<CustomValue> {
        int size();
        CustomValue get(int index);
    }
    interface CvObject extends CustomValue, Iterable<Map.Entry<String, CustomValue>> {
        int size();
        boolean containsKey(String key);
        CustomValue get(String key);
    }
    CvType getType();
    CvObject getAsObject();
    CvArray getAsArray();
    String getAsString();
    Number getAsNumber();
    boolean getAsBoolean();
}
