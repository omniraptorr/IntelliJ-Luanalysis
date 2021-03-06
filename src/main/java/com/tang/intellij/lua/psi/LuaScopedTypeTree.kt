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
package com.tang.intellij.lua.psi

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.tang.intellij.lua.comment.LuaCommentUtil
import com.tang.intellij.lua.comment.psi.*
import com.tang.intellij.lua.comment.psi.api.LuaComment
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.index.LuaClassIndex
import com.tang.intellij.lua.ty.ITyClass
import com.tang.intellij.lua.ty.TyPsiDocClass
import com.tang.intellij.lua.ty.TySerializedClass
import com.tang.intellij.lua.ty.isAnonymous
import java.util.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

interface LuaScopedTypeTree {
    companion object {
        private val treeKey = Key.create<ScopedTypeTree>("lua.object.tree.types")

        fun get(file: PsiFile): LuaScopedTypeTree {
            val currentTree = file.getUserData(treeKey)

            if (currentTree?.shouldRebuild() != false) {
                if (file is LuaPsiFile && !file.isContentsLoaded) {
                    try {
                        return ScopedTypeStubTree(file).apply {
                            buildTree(file)
                            file.putUserData(treeKey, this)
                        }
                    } catch (e: Exception) {
                        // Fallback to PSI tree
                    }
                }

                return ScopedTypePsiTree(file).apply {
                    buildTree(file)
                    file.putUserData(treeKey, this)
                }
            }

            return currentTree
        }
    }

    fun find(context: SearchContext, pin: PsiElement, name: String): LuaScopedType?
}

private class ScopedTypeTreeScope(val psi: PsiElement, val treeBuilder: ScopedTypeTree, val parent: ScopedTypeTreeScope?) {
    private val types = ArrayList<LuaScopedType>(0)
    private val childScopes = LinkedList<ScopedTypeTreeScope>()

    fun addChildScope(scope: ScopedTypeTreeScope) {
        childScopes.add(scope)
    }

    fun add(type: LuaScopedType) {
        types.add(type)
    }

    fun addAll(type: Collection<LuaScopedType>) {
        types.addAll(type)
    }

    inline fun forEach(action: (LuaScopedType) -> Unit) {
        types.forEach(action)
    }

    inline fun forEachIndexed(action: (index: Int, LuaScopedType) -> Unit) {
        types.forEachIndexed(action)
    }

    fun indexOf(type: LuaScopedType): Int? {
        val index = types.indexOf(type)
        return if (index >= 0) index else null
    }

    fun get(name: String, beforeIndex: Int): LuaScopedType? {
        for (i in beforeIndex - 1 downTo 0) {
            val type = types[i]

            if (type.name == name) {
                return type
            }
        }

        return null
    }

    fun get(name: String): LuaScopedType? {
        types.forEach {
            if (it.name == name) {
                return it
            }
        }

        return null
    }

    fun find(context: SearchContext, name: String, beforeIndex: Int? = null): LuaScopedType? {
        val type = if (beforeIndex != null) {
            get(name, beforeIndex)
        } else {
            get(name)
        }

        if (type != null) {
            return type
        }

        val cls: ITyClass? = if (psi is LuaClassMethodDefStat) {
            psi.guessParentType(context) as? ITyClass
        } else if (psi is LuaAssignStat) {
            (psi.varExprList.expressionList.first() as? LuaIndexExpr)?.guessParentType(context) as? ITyClass
        } else {
            null
        }

        if (cls?.isAnonymous == false) {
            val classTag = if (cls is TySerializedClass) {
                LuaClassIndex.find(cls.className, context)
            } else if (cls is TyPsiDocClass) {
                cls.tagClass
            } else null

            val genericDef = PsiTreeUtil.getStubChildrenOfTypeAsList(classTag, LuaDocGenericDef::class.java).firstOrNull {
                it.name == name
            }

            if (genericDef != null) {
                return genericDef
            }
        }

        return parent?.find(context, name)
    }
}

@ExperimentalContracts
private fun isValidTypeScope(element: PsiElement?): Boolean {
    contract {
        returns(true) implies (element is LuaTypeScope)
    }

    if (element !is LuaTypeScope) {
        return false
    }

    return when (element) {
        is LuaLocalDefStat -> {
            if (element.localDefList.size == 1) {
                element.exprList?.expressionList?.let {
                    (it.firstOrNull() as? LuaClosureExpr)?.comment != null
                } ?: false
            } else false
        }
        is LuaAssignStat -> {
            if (element.varExprList.expressionList.size == 1) {
                element.valueExprList?.expressionList?.let {
                    (it.firstOrNull() as? LuaClosureExpr)?.comment != null
                } ?: false
            } else false
        }
        is LuaTableField -> {
            return element.valueExpr is LuaClosureExpr
        }
        else -> true
    }
}

private abstract class ScopedTypeTree(val file: PsiFile) : LuaRecursiveVisitor(), LuaScopedTypeTree {
    companion object {
        val scopeKey = Key.create<ScopedTypeTreeScope>("lua.object.tree.types.scope")
    }

    protected class FoundScope(val scope: ScopedTypeTreeScope, val psiScopedTypeIndex: Int? = null)

    private val modificationStamp: Long = file.modificationStamp

    private val rootScope = ScopedTypeTreeScope(file, this, null)
    private var currentScope = rootScope

    open fun shouldRebuild(): Boolean {
        return modificationStamp != file.modificationStamp
    }

    private fun create(psi: LuaTypeScope): ScopedTypeTreeScope {
        val genericDefs = if (psi is LuaDocFunctionTy) {
            psi.genericDefList
        } else {
            val comment = if (psi is LuaCommentOwner) {
                LuaCommentUtil.findComment(psi)
            } else if (psi is LuaDocPsiElement) {
                LuaCommentUtil.findContainer(psi)
            } else null

            comment?.findGenericDefs()
        }

        val scope = ScopedTypeTreeScope(psi, this, currentScope)

        genericDefs?.let {
            scope.addAll(it)
        }

        psi.putUserData(scopeKey, scope)

        return scope
    }

    private fun push(scope: ScopedTypeTreeScope) {
        currentScope.addChildScope(scope)
        currentScope = scope
    }

    private fun pop() {
        currentScope = currentScope.parent ?: rootScope
    }

    fun buildTree(file: PsiFile) {
        file.accept(this)
    }

    abstract fun findScope(element: PsiElement): FoundScope?

    override fun find(context: SearchContext, pin: PsiElement, name: String): LuaScopedType? {
        return findScope(pin)?.let {
            it.scope.find(context, name, it.psiScopedTypeIndex)
        }
    }

    protected open fun traverseChildren(element: PsiElement) {
        super.visitElement(element)
    }

    override fun visitElement(element: PsiElement) {
        if (isValidTypeScope(element) && currentScope.psi !== element) {
            push(create(element))
            traverseChildren(element)
            pop()
        } else if (element is LuaDocTagOverload) {
            // Typically generic defs (@generic) are scoped to the function owner. However, overloads are a special case
            // where generics defs must be scoped to the overload only i.e. cannot be referenced in the function body.
            val previousScope = currentScope
            currentScope = currentScope.parent ?: rootScope

            traverseChildren(element)

            currentScope = previousScope
        } else {
            val cls = (element as? LuaComment)?.tagClass

            if (cls != null) {
                // If we encountered a comment with a @class then we want all comment children (i.e. @field) to be children of the class' scope.
                push(create(cls))
                traverseChildren(element)
                pop()
            } else {
                traverseChildren(element)
            }
        }
    }
}

private class ScopedTypePsiTree(file: PsiFile) : ScopedTypeTree(file) {
    override fun findScope(element: PsiElement): FoundScope? {
        var psi: PsiElement? = element
        var psiScopedType: LuaScopedType? = null

        while (psi != null) {
            if (psi is LuaScopedType) {
                psiScopedType = psi
            } else {
                val candidatePsi = (psi as? LuaComment)?.tagClass ?: psi

                if (isValidTypeScope(candidatePsi)) {
                    var scope = candidatePsi.getUserData(scopeKey)

                    if (scope == null) {
                        buildTree(element.containingFile)
                        scope = candidatePsi.getUserData(scopeKey)
                    }

                    return if (scope != null) {
                        FoundScope(scope, psiScopedType?.let { scope.indexOf(psiScopedType) })
                    } else null
                }
            }

            psi = psi.parent
        }

        return null
    }
}

private class ScopedTypeStubTree(file: PsiFile) : ScopedTypeTree(file) {
    override fun shouldRebuild(): Boolean {
        return super.shouldRebuild() || (file as? LuaPsiFile)?.isContentsLoaded == true
    }

    override fun traverseChildren(element: PsiElement) {
        var stub: STUB_ELE? = null

        if (element is LuaPsiFile) {
            stub = element.stub
        }

        if (element is STUB_PSI) {
            stub = element.stub
        }

        if (stub != null) {
            for (child in stub.childrenStubs) {
                child.psi.accept(this)
            }
        } else {
            super.traverseChildren(element)
        }
    }

    override fun findScope(element: PsiElement): FoundScope? {
        if (element is STUB_PSI) {
            var stub = element.stub
            var psiScopedType: LuaScopedType? = null

            while (stub != null) {
                val stubPsi = stub.psi

                if (stubPsi is LuaScopedType) {
                    psiScopedType = stubPsi
                }

                if (isValidTypeScope(stubPsi)) {
                    val scope = stubPsi.getUserData(scopeKey)

                    return if (scope != null) {
                        FoundScope(scope, psiScopedType?.let { scope.indexOf(psiScopedType) })
                    } else null
                }

                stub = stub.parentStub
            }
        }

        return null
    }
}
