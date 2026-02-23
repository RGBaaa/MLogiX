package mlogix.mlogix.type;

import java.util.*;

public class TypeRegistry {
    private final Map<String, Type> types = new HashMap<>();
    
    public TypeRegistry() {
        register(BuiltinType.Int);
        register(BuiltinType.Str);
    }
    
    public void register(Type type) {
        types.put(type.name, type);
    }
    
    public Type get(String name) {
        return types.get(name);
    }
    
    public boolean containsKey(String name) {
        return types.containsKey(name);
    }
}