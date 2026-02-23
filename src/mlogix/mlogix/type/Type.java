package mlogix.mlogix.type;

import java.util.*;

public class Type {
    public final String name;
    public final Map<String, Type> fields = new HashMap<>();
    public final Map<String, Type> methods = new HashMap<>();

    public Type(String name) {
        this.name = name;
    }

    public Type addFields(String name, Type type) {
        fields.put(name, type);
        return this;
    }

    public Type addMethods(String name, Type fn) {
        methods.put(name, fn);
        return this;
    }
}