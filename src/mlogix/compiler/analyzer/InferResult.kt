package mlogix.compiler.analyzer

import arc.struct.Seq
import mlogix.compiler.type.Type

data class InferResult(val type: Type, val constraints: Seq<Constraint>)


