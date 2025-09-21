package mlogix.logix;

import java.util.*;

public class Struct {
    public final String name;
    public final Map<String, Struct> fields = new HashMap<>();
    public final Map<String, Struct> methods = new HashMap<>();

    public Struct(String name) {
        this.name = name;
    }

    public Struct addFields(String name, Struct type) {
        fields.put(name, type);
        return this;
    }

    public Struct addMethods(String name, Struct fn) {
        methods.put(name, fn);
        return this;
    }
}