/*
 * Copyright (c) 2020
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

package com.tang.intellij.lua.stubs

import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.tang.intellij.lua.comment.psi.LuaDocTagNot
import com.tang.intellij.lua.comment.psi.impl.LuaDocTagNotImpl
import com.tang.intellij.lua.psi.LuaElementType

class LuaDocTagNotType : LuaStubElementType<LuaDocTagNotStub, LuaDocTagNot>("DOC_NOT"){
    override fun indexStub(stub: LuaDocTagNotStub, sink: IndexSink) {
    }

    override fun deserialize(inputStream: StubInputStream, stubElement: StubElement<*>?): LuaDocTagNotStub {
        return LuaDocTagNotStubImpl(stubElement)
    }

    override fun createPsi(stub: LuaDocTagNotStub) = LuaDocTagNotImpl(stub, this)

    override fun serialize(stub: LuaDocTagNotStub, stubElement: StubOutputStream) {
    }

    override fun createStub(tagNot: LuaDocTagNot, stubElement: StubElement<*>?): LuaDocTagNotStub {
        return LuaDocTagNotStubImpl(stubElement)
    }
}

interface LuaDocTagNotStub : StubElement<LuaDocTagNot>

class LuaDocTagNotStubImpl(parent: StubElement<*>?)
    : LuaDocStubBase<LuaDocTagNot>(parent, LuaElementType.DOC_NOT), LuaDocTagNotStub
