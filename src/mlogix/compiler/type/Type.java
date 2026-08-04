package mlogix.compiler.type;

import arc.struct.ObjectMap;

public class Type {
    public final String name;
    public final ObjectMap<String, Type> fields = new ObjectMap<>();
    public final ObjectMap<String, Type> methods = new ObjectMap<>();

    public Type(String name) {
        this.name = name;
    }

    public Type addField(String name, Type type){
        fields.put(name, type);
        return this;
    }

    public Type addMethod(String name, Type fn){
        methods.put(name, fn);
        return this;
    }
}