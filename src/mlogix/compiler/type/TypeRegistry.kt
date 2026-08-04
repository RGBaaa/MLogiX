package mlogix.compiler.type

import arc.struct.ObjectMap

class TypeRegistry {
    private val types: ObjectMap<String, Type> = ObjectMap<String, Type>()

    init {
        register(BuiltinType.Int)
        register(BuiltinType.Str)
    }

    fun register(type: Type) {
        types.put(type.name, type)
    }

    fun get(name: String?): Type? {
        return types.get(name)
    }

    fun containsKey(name: String?): Boolean {
        return types.containsKey(name)
    }
}