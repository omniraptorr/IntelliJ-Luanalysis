/*
 * Copyright (c) 2017. tangzx(love.tangzx@qq.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tang.intellij.lua.ty

import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.tang.intellij.lua.Constants
import com.tang.intellij.lua.comment.psi.LuaDocPrimitiveTableTy
import com.tang.intellij.lua.psi.LuaClassMember
import com.tang.intellij.lua.search.SearchContext

interface ITyPrimitive : ITy {
    val primitiveKind: TyPrimitiveKind
}

// number, boolean, nil, void ...
class TyPrimitive(override val primitiveKind: TyPrimitiveKind,
                  override val displayName: String) : Ty(TyKind.Primitive), ITyPrimitive {

    override val booleanType = if (primitiveKind == TyPrimitiveKind.Boolean) Ty.BOOLEAN else Ty.TRUE

    override fun equals(other: Any?): Boolean {
        return other is ITyPrimitive && other.primitiveKind == primitiveKind
    }

    override fun equals(other: ITy, context: SearchContext): Boolean {
        if (this === other) {
            return true
        }

        val resolvedOther = Ty.resolve(other, context)
        return resolvedOther is ITyPrimitive && resolvedOther.primitiveKind == primitiveKind
    }

    override fun hashCode(): Int {
        return primitiveKind.hashCode()
    }

    override fun toString(): String {
        return displayName
    }

    override fun contravariantOf(other: ITy, context: SearchContext, flags: Int): Boolean {
        if (super.contravariantOf(other, context, flags)
                || (other is ITyPrimitive && other.primitiveKind == primitiveKind)) {
            return true
        }

        if (flags and TyVarianceFlags.STRICT_UNKNOWN == 0) {
            if (primitiveKind == TyPrimitiveKind.Function && other.kind == TyKind.Function) {
                return true
            }

            if (primitiveKind == TyPrimitiveKind.Table) {
                val otherBase = if (other is ITyGeneric) other.base else other
                return other.kind == TyKind.Array
                        || otherBase.kind == TyKind.Class
                        || otherBase == Ty.TABLE
            }
        }

        return false
    }

    override fun guessMemberType(name: String, searchContext: SearchContext): ITy? {
        return if (primitiveKind == TyPrimitiveKind.Table) {
            Ty.UNKNOWN
        } else super<Ty>.guessMemberType(name, searchContext)
    }

    override fun guessIndexerType(indexTy: ITy, searchContext: SearchContext, exact: Boolean): ITy? {
        return if (primitiveKind == TyPrimitiveKind.Table) {
            Ty.UNKNOWN
        } else super<Ty>.guessIndexerType(indexTy, searchContext, exact)
    }
}

// string
open class TyPrimitiveClass(override val primitiveKind: TyPrimitiveKind,
                       override val displayName: String) : TyClass(displayName), ITyPrimitive {

    override val kind = TyKind.Primitive

    override fun getSuperClass(context: SearchContext): ITy? = null

    override fun doLazyInit(searchContext: SearchContext) { }

    override fun accept(visitor: ITyVisitor) {
        visitor.visitTy(this)
    }

    override fun equals(other: Any?): Boolean {
        return other is ITyPrimitive && other.primitiveKind == primitiveKind
    }

    override fun equals(other: ITy, context: SearchContext): Boolean {
        if (this === other) {
            return true
        }

        val resolvedOther = Ty.resolve(other, context)
        return resolvedOther is ITyPrimitive && resolvedOther.primitiveKind == primitiveKind
    }

    override fun hashCode(): Int {
        return primitiveKind.hashCode()
    }

    override fun contravariantOf(other: ITy, context: SearchContext, flags: Int): Boolean {
        return (other is ITyPrimitive && other.primitiveKind == primitiveKind) ||
                super.contravariantOf(other, context, flags)
    }
}

object TyPrimitiveSerializer : TySerializer<ITy>() {
    override fun deserializeTy(flags: Int, stream: StubInputStream): ITy {
        val primitiveKind = stream.readByte()
        return when (primitiveKind.toInt()) {
            TyPrimitiveKind.Boolean.ordinal -> Ty.BOOLEAN
            TyPrimitiveKind.String.ordinal -> Ty.STRING
            TyPrimitiveKind.Number.ordinal -> Ty.NUMBER
            TyPrimitiveKind.Table.ordinal -> Ty.TABLE
            TyPrimitiveKind.Function.ordinal -> Ty.FUNCTION
            else -> Ty.UNKNOWN
        }
    }

    override fun serializeTy(ty: ITy, stream: StubOutputStream) {
        when (ty) {
            is ITyPrimitive -> stream.writeByte(ty.primitiveKind.ordinal)
            else -> stream.writeByte(-1)
        }
    }
}

class TyDocPrimitiveTable(val luaDocPrimitiveTableTy: LuaDocPrimitiveTableTy) : TyPrimitiveClass(TyPrimitiveKind.Table, Constants.WORD_TABLE) {
    override fun findMember(name: String, searchContext: SearchContext): LuaClassMember {
        return luaDocPrimitiveTableTy
    }

    override fun findIndexer(indexTy: ITy, searchContext: SearchContext, exact: Boolean): LuaClassMember {
        return luaDocPrimitiveTableTy
    }

    override fun contravariantOf(other: ITy, context: SearchContext, flags: Int): Boolean {
        if (super.contravariantOf(other, context, flags)) {
            return true
        }

        if (flags and TyVarianceFlags.STRICT_UNKNOWN == 0) {
            val otherBase = if (other is ITyGeneric) other.base else other
            return other.kind == TyKind.Array
                    || otherBase.kind == TyKind.Class
                    || otherBase == Ty.TABLE
        }

        return false
    }
}
