// This is a generated file. Not intended for manual editing.
package com.tang.intellij.lua.comment.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.tang.intellij.lua.ty.ITy;

public interface LuaDocTagReturn extends LuaDocTag {

  @Nullable
  LuaDocCommentString getCommentString();

  @Nullable
  LuaDocFunctionReturnType getFunctionReturnType();

  //WARNING: resolveTypeAt(...) is skipped
  //matching resolveTypeAt(LuaDocTagReturn, ...)
  //methods are not found in LuaDocPsiImplUtilKt

  @NotNull
  ITy getType();

}
