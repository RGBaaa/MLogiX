package mlogix.struct;

import mlogix.mlogix.*;

import java.util.*;

public class StructRegistry {
    private final Map<String, Struct> structs = new HashMap<>();
    
    public StructRegistry() {
        register(BuiltinStruct.Int);
        register(BuiltinStruct.String);
    }
    
    public void register(Struct struct) {
        structs.put(struct.name, struct);
    }
    
    public Struct get(String name) {
        return structs.get(name);
    }
    
    public boolean containsKey(String name) {
        return structs.containsKey(name);
    }
}