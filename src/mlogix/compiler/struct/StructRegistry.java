package mlogix.compiler.struct;

import java.util.*;

import mlogix.logix.Struct;
import mlogix.logix.BuiltinStruct;

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