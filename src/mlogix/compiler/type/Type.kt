package mlogix.compiler.type

import arc.struct.ObjectMap

open class Type(val name: String) {
    val fields: ObjectMap<String, Type> = ObjectMap<String, Type>()
    val methods: ObjectMap<String, FunctionType> = ObjectMap<String, FunctionType>()

    fun addField(name: String?, type: Type?): Type {
        fields.put(name, type)
        return this
    }

    fun addMethod(name: String?, fn: FunctionType?): Type {
        methods.put(name, fn)
        return this
    }
}